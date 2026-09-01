package com.linguan.codemd.target.stage02.applicationprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linguan.codemd.target.stage02.mappercatalog.MapperCapabilityCataloger;
import com.linguan.codemd.target.stage02.mappercatalog.MapperCatalogDiscovery;
import com.linguan.codemd.target.stage02.mappercatalog.MapperCatalogProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Map;

class Stage02MapperCapabilityCatalogerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void catalogsFrozenMapperJavaAndXmlCandidatesWithoutDeclaringTheirBinding() throws Exception {
        try (Stage02ApplicationProfileDetectorTest.Fixture fixture =
                Stage02ApplicationProfileDetectorTest.Fixture.create(temporaryDirectory)) {
            ApplicationProfileDetection profile = new ApplicationProfileDetector(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .detect(fixture.stage01, DiscoveryProfile.javaSpringMvcMyBatisV2());

            MapperCatalogDiscovery catalog = new MapperCapabilityCataloger(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .catalog(fixture.stage01, profile.draftPublication(), MapperCatalogProfile.mybatisV2());

            assertThat(catalog.entries()).singleElement().satisfies(entry -> {
                assertThat(entry.javaInterfaceFqn()).isEqualTo("example.depot.DepotHeadMapper");
                assertThat(entry.javaMethodCandidates())
                        .containsExactly("updateByExampleSelective(DepotHead,DepotHeadExample)");
                assertThat(entry.xmlResourcePath()).isEqualTo("src/main/resources/mapper/DepotHeadMapper.xml");
                assertThat(entry.xmlNamespace()).isEqualTo("example.depot.DepotHeadMapper");
                assertThat(entry.xmlStatementCandidates()).containsExactly("updateByExampleSelective");
                assertThat(entry.bindingState()).isEqualTo("CANDIDATE_NOT_YET_BOUND");
            });
            assertThat(catalog.sites())
                    .filteredOn(site -> site.kind() == CapabilitySiteKind.MAPPER_RESOURCE_DECLARATION)
                    .singleElement()
                    .satisfies(site -> {
                        assertThat(site.disposition()).isEqualTo(SignalDisposition.SUPPORTED);
                        assertThat(site.primaryLocator().path())
                                .isEqualTo("src/main/resources/mapper/DepotHeadMapper.xml");
                    });
            assertThat(fixture.moduleStore.reopen(catalog.draftPublication()).payloads())
                    .extracting(payload -> payload.descriptor().fileName())
                    .containsExactly("mapper-catalog-draft.json");
        }
    }

    @Test
    void rejectsAnExternalEntityBeforeItCanBecomeAMapperCandidate() throws Exception {
        Map<String, byte[]> files = Stage02ApplicationProfileDetectorTest.Fixture.sourceFiles();
        files.put(
                "src/main/resources/mapper/DepotHeadMapper.xml",
                """
                <!DOCTYPE mapper [
                  <!ENTITY forbidden SYSTEM "file:///must-not-be-read">
                ]>
                <mapper namespace="example.depot.DepotHeadMapper">
                  <update id="updateByExampleSelective">&forbidden;</update>
                </mapper>
                """.getBytes(StandardCharsets.UTF_8));
        try (Stage02ApplicationProfileDetectorTest.Fixture fixture =
                Stage02ApplicationProfileDetectorTest.Fixture.create(temporaryDirectory, files)) {
            ApplicationProfileDetection profile = new ApplicationProfileDetector(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .detect(fixture.stage01, DiscoveryProfile.javaSpringMvcMyBatisV2());

            assertThatThrownBy(() -> new MapperCapabilityCataloger(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .catalog(fixture.stage01, profile.draftPublication(), MapperCatalogProfile.mybatisV2()))
                    .isInstanceOf(ApplicationProfileException.class)
                    .extracting(throwable -> ((ApplicationProfileException) throwable).code())
                    .isEqualTo("XML_EXTERNAL_RESOLUTION_ATTEMPT");
        }
    }

    @Test
    void recordsANamespaceMismatchAsAGapInsteadOfGuessingAJavaMapperBinding() throws Exception {
        Map<String, byte[]> files = Stage02ApplicationProfileDetectorTest.Fixture.sourceFiles();
        files.put(
                "src/main/resources/mapper/DepotHeadMapper.xml",
                """
                <mapper namespace="example.depot.UnrelatedMapper">
                  <update id="updateByExampleSelective">update depot_head set status = #{record.status}</update>
                </mapper>
                """.getBytes(StandardCharsets.UTF_8));
        try (Stage02ApplicationProfileDetectorTest.Fixture fixture =
                Stage02ApplicationProfileDetectorTest.Fixture.create(temporaryDirectory, files)) {
            ApplicationProfileDetection profile = new ApplicationProfileDetector(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .detect(fixture.stage01, DiscoveryProfile.javaSpringMvcMyBatisV2());

            MapperCatalogDiscovery catalog = new MapperCapabilityCataloger(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .catalog(fixture.stage01, profile.draftPublication(), MapperCatalogProfile.mybatisV2());

            assertThat(catalog.entries()).isEmpty();
            assertThat(catalog.sites())
                    .singleElement()
                    .satisfies(site -> {
                        assertThat(site.disposition()).isEqualTo(SignalDisposition.AMBIGUOUS);
                        assertThat(site.reasonCode()).isEqualTo("MAPPER_JAVA_INTERFACE_NOT_CATALOGED");
                        assertThat(site.evidenceRefs()).singleElement();
                    });
            assertThat(catalog.shardReceipts()).singleElement().satisfies(shard -> {
                assertThat(shard.status()).isEqualTo("SUCCEEDED_WITH_GAPS");
                assertThat(shard.gapIds()).hasSize(1);
            });
        }
    }
}
