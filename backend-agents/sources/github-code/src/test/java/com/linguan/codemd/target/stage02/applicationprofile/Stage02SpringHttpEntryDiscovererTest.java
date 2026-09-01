package com.linguan.codemd.target.stage02.applicationprofile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.nio.file.Path;

class Stage02SpringHttpEntryDiscovererTest {
    @TempDir Path temporaryDirectory;

    @Test
    void discoversEveryStaticSpringMvcRouteFromPersistedM1AndFrozenStage01Bytes() throws Exception {
        try (Stage02ApplicationProfileDetectorTest.Fixture fixture =
                Stage02ApplicationProfileDetectorTest.Fixture.create(temporaryDirectory)) {
            ApplicationProfileDetection profile = new ApplicationProfileDetector(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .detect(fixture.stage01, DiscoveryProfile.javaSpringMvcMyBatisV2());

            HttpEntryDiscovery discovery = new SpringHttpEntryDiscoverer(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .discover(fixture.stage01, profile.draftPublication(), HttpEntryDiscoveryProfile.springMvcV2());

            assertThat(discovery.entries())
                    .extracting(entry -> entry.method() + " " + entry.route())
                    .containsExactlyInAnyOrder("GET /health", "POST /depotHead/batchSetStatus");
            assertThat(discovery.entries().stream().map(HttpEntryPoint::entryId).toList())
                    .isSorted();
            HttpEntryPoint depotHead = discovery.entries().stream()
                    .filter(entry -> entry.route().equals("/depotHead/batchSetStatus"))
                    .findFirst()
                    .orElseThrow();
            assertThat(depotHead.handlerFqn()).isEqualTo("example.depot.DepotHeadController#batchSetStatus");
            assertThat(depotHead.parameterNames()).containsExactly("status", "ids");
            assertThat(depotHead.routeSourceExcerpts()).hasSize(2);
            assertThat(fixture.moduleStore.reopen(discovery.draftPublication()).payloads())
                    .extracting(payload -> payload.descriptor().fileName())
                    .containsExactly("http-entry-discovery.json");
        }
    }

    @Test
    void recordsADynamicRouteAsAnEvidenceBackedSiteGapWithoutDiscardingStaticEntries() throws Exception {
        Map<String, byte[]> files = Stage02ApplicationProfileDetectorTest.Fixture.sourceFiles();
        files.put(
                "src/main/java/example/dynamic/DynamicRouteController.java",
                ("package example.dynamic;\n"
                                + "import org.springframework.web.bind.annotation.PostMapping;\n"
                                + "import org.springframework.web.bind.annotation.RestController;\n"
                                + "@RestController\n"
                                + "final class DynamicRouteController {\n"
                                + "  @PostMapping(routeForTenant())\n"
                                + "  String dynamicRoute() { return \"ok\"; }\n"
                                + "  private static String routeForTenant() { return \"/not-static\"; }\n"
                                + "}\n")
                        .getBytes(StandardCharsets.UTF_8));
        try (Stage02ApplicationProfileDetectorTest.Fixture fixture =
                Stage02ApplicationProfileDetectorTest.Fixture.create(temporaryDirectory, files)) {
            ApplicationProfileDetection profile = new ApplicationProfileDetector(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .detect(fixture.stage01, DiscoveryProfile.javaSpringMvcMyBatisV2());

            HttpEntryDiscovery discovery = new SpringHttpEntryDiscoverer(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .discover(fixture.stage01, profile.draftPublication(), HttpEntryDiscoveryProfile.springMvcV2());

            assertThat(discovery.entries())
                    .extracting(entry -> entry.method() + " " + entry.route())
                    .containsExactlyInAnyOrder("GET /health", "POST /depotHead/batchSetStatus");
            assertThat(discovery.sites())
                    .filteredOn(site -> "DYNAMIC_ROUTE_EXPRESSION".equals(site.reasonCode()))
                    .singleElement()
                    .satisfies(site -> {
                        assertThat(site.disposition()).isEqualTo(SignalDisposition.AMBIGUOUS);
                        assertThat(site.affectedEntryIds()).isEmpty();
                        assertThat(site.primaryLocator().path())
                                .isEqualTo("src/main/java/example/dynamic/DynamicRouteController.java");
                        assertThat(site.evidenceRefs()).hasSize(1);
                    });
        }
    }

    @Test
    void keepsDistinctExactEvidenceLocatorsWhenTwoMappingsHaveIdenticalText() throws Exception {
        Map<String, byte[]> files = Stage02ApplicationProfileDetectorTest.Fixture.sourceFiles();
        files.put(
                "src/main/java/example/repeated/RepeatedRouteController.java",
                """
                package example.repeated;

                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                final class RepeatedRouteController {
                  @PostMapping("/same")
                  String first() { return "ok"; }

                  @PostMapping("/same")
                  String second() { return "ok"; }
                }
                """.getBytes(StandardCharsets.UTF_8));
        try (Stage02ApplicationProfileDetectorTest.Fixture fixture =
                Stage02ApplicationProfileDetectorTest.Fixture.create(temporaryDirectory, files)) {
            ApplicationProfileDetection profile = new ApplicationProfileDetector(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .detect(fixture.stage01, DiscoveryProfile.javaSpringMvcMyBatisV2());

            HttpEntryDiscovery discovery = new SpringHttpEntryDiscoverer(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .discover(fixture.stage01, profile.draftPublication(), HttpEntryDiscoveryProfile.springMvcV2());

            assertThat(discovery.entries())
                    .filteredOn(entry -> entry.route().equals("/same"))
                    .hasSize(2)
                    .allSatisfy(entry -> assertThat(entry.routeSourceExcerpts()).singleElement().satisfies(excerpt -> {
                        assertThat(excerpt.rawUtf8()).isEqualTo("@PostMapping(\"/same\")");
                        assertThat(excerpt.locator().path())
                                .isEqualTo("src/main/java/example/repeated/RepeatedRouteController.java");
                    }));
            assertThat(discovery.entries().stream()
                            .filter(entry -> entry.route().equals("/same"))
                            .map(entry -> entry.routeSourceExcerpts().get(0).locator().startByte())
                            .toList())
                    .doesNotHaveDuplicates();
        }
    }
}
