package com.linguan.codemd.target.stage02.applicationprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguan.codemd.target.artifacts.ReopenedStagePublication;
import com.linguan.codemd.target.artifacts.StagePublicationAddress;
import com.linguan.codemd.target.stage02.mappercatalog.MapperCapabilityCataloger;
import com.linguan.codemd.target.stage02.mappercatalog.MapperCatalogDiscovery;
import com.linguan.codemd.target.stage02.mappercatalog.MapperCatalogProfile;
import com.linguan.codemd.target.stage02.publish.Stage02PublicationSpecificationInput;
import com.linguan.codemd.target.stage02.publish.Stage02PublicationSpecifier;
import com.linguan.codemd.target.stage02.publish.Stage02Reference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Stage02PublicationSpecifierTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir Path temporaryDirectory;

    @Test
    void publishesTheFourStage02SemanticFilesFromFreshlyReopenedM1ToM3Artifacts() throws Exception {
        try (Stage02ApplicationProfileDetectorTest.Fixture fixture =
                Stage02ApplicationProfileDetectorTest.Fixture.create(temporaryDirectory)) {
            ApplicationProfileDetection profile = new ApplicationProfileDetector(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .detect(fixture.stage01, DiscoveryProfile.javaSpringMvcMyBatisV2());
            HttpEntryDiscovery entries = new SpringHttpEntryDiscoverer(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .discover(fixture.stage01, profile.draftPublication(), HttpEntryDiscoveryProfile.springMvcV2());
            MapperCatalogDiscovery catalog = new MapperCapabilityCataloger(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .catalog(fixture.stage01, profile.draftPublication(), MapperCatalogProfile.mybatisV2());

            Stage02Reference stage02 = new Stage02PublicationSpecifier().publish(
                    new Stage02PublicationSpecificationInput(
                            new StagePublicationAddress(
                                    fixture.stage01.stagePublicationReference().address().runId(),
                                    2,
                                    "discover-application-and-entries"),
                            fixture.stage01,
                            profile.draftPublication(),
                            entries.draftPublication(),
                            catalog.draftPublication()),
                    fixture.policies,
                    fixture.moduleStore,
                    fixture.stageStore);

            ReopenedStagePublication reopened = fixture.stageStore.reopen(stage02.stagePublicationReference());
            assertThat(reopened.semanticPayloads())
                    .extracting(payload -> payload.descriptor().fileName())
                    .containsExactly(
                            "application-profile.json",
                            "capability-report.json",
                            "entry-points.jsonl",
                            "mapper-catalog.jsonl");
            assertThat(reopened.receipt().status()).isEqualTo("SUCCEEDED");
            assertThat(reopened.semanticPayloads().stream()
                            .collect(Collectors.toMap(
                                    payload -> payload.descriptor().fileName(),
                                    payload -> payload.canonicalUtf8().copyToByteArray()))
                            .get("entry-points.jsonl"))
                    .asString()
                    .contains("/depotHead/batchSetStatus", "GET", "/health");
            assertThat(JSON.readTree(reopened.semanticPayloads().stream()
                            .filter(payload -> payload.descriptor().fileName().equals("capability-report.json"))
                            .findFirst()
                            .orElseThrow()
                            .canonicalUtf8()
                            .copyToByteArray())
                            .path("repositoryEntryCoverage")
                            .path("closed")
                            .asBoolean())
                    .isTrue();
            assertThat(fixture.moduleStore.reopen(stage02.publisherModuleReference()).payloads())
                    .extracting(payload -> payload.descriptor().fileName())
                    .containsExactly(
                            "application-profile.json",
                            "capability-report.json",
                            "entry-points.jsonl",
                            "mapper-catalog.jsonl");
        }
    }

    @Test
    void publishesAnEmptyEntryInventoryAndANoEntryGapAfterACompleteSearch() throws Exception {
        Map<String, byte[]> files = Stage02ApplicationProfileDetectorTest.Fixture.sourceFiles();
        files.keySet().removeIf(path -> path.endsWith("Controller.java"));
        try (Stage02ApplicationProfileDetectorTest.Fixture fixture =
                Stage02ApplicationProfileDetectorTest.Fixture.create(temporaryDirectory, files)) {
            ApplicationProfileDetection profile = new ApplicationProfileDetector(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .detect(fixture.stage01, DiscoveryProfile.javaSpringMvcMyBatisV2());
            HttpEntryDiscovery entries = new SpringHttpEntryDiscoverer(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .discover(fixture.stage01, profile.draftPublication(), HttpEntryDiscoveryProfile.springMvcV2());
            MapperCatalogDiscovery catalog = new MapperCapabilityCataloger(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .catalog(fixture.stage01, profile.draftPublication(), MapperCatalogProfile.mybatisV2());

            Stage02Reference stage02 = new Stage02PublicationSpecifier().publish(
                    new Stage02PublicationSpecificationInput(
                            new StagePublicationAddress(
                                    fixture.stage01.stagePublicationReference().address().runId(),
                                    2,
                                    "discover-application-and-entries"),
                            fixture.stage01,
                            profile.draftPublication(),
                            entries.draftPublication(),
                            catalog.draftPublication()),
                    fixture.policies,
                    fixture.moduleStore,
                    fixture.stageStore);

            ReopenedStagePublication reopened = fixture.stageStore.reopen(stage02.stagePublicationReference());
            assertThat(reopened.receipt().status()).isEqualTo("SUCCEEDED_WITH_GAPS");
            assertThat(reopened.receipt().gapRefs()).singleElement().satisfies(gap -> assertThat(gap).startsWith("gap:"));
            assertThat(reopened.semanticPayloads().stream()
                            .filter(payload -> payload.descriptor().fileName().equals("entry-points.jsonl"))
                            .findFirst()
                            .orElseThrow()
                            .canonicalUtf8()
                            .copyToByteArray())
                    .asString(StandardCharsets.UTF_8)
                    .isEmpty();
            var report = JSON.readTree(reopened.semanticPayloads().stream()
                            .filter(payload -> payload.descriptor().fileName().equals("capability-report.json"))
                            .findFirst()
                            .orElseThrow()
                            .canonicalUtf8()
                            .copyToByteArray());
            assertThat(report.path("gapReasons").isArray()).isTrue();
            assertThat(report.path("gapReasons").size()).isEqualTo(1);
            assertThat(report.path("gapReasons").get(0).path("reasonCode").asText())
                    .isEqualTo("NO_ENTRY_DISCOVERED");
        }
    }
}
