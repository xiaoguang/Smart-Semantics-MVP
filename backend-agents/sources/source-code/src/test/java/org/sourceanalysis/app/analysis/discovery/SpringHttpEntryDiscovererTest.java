package org.sourceanalysis.app.analysis.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

class SpringHttpEntryDiscovererTest {

  @Test
  void composesStaticClassAndMethodRoutesIntoOneProvenHttpEntry() {
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    VerifiedSourceTextDocument controller =
        text(
            "src/main/java/com/example/DepotHeadController.java",
            """
            package com.example;

            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestMapping;

            @RequestMapping("/depotHead")
            public class DepotHeadController {
              @PostMapping("/batchSetStatus")
              public String batchSetStatus(String status, String ids) {
                return "ok";
              }
            }
            """);
    VerifiedSourceTextReader sourceHandle = reference -> sourceTextSet(controller);

    HttpEntryDiscovery discovery =
        new SpringHttpEntryDiscoverer(sourceHandle)
            .discoverEntries(profile(controller), frozenSource);

    assertThat(discovery.entries())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.kind()).isEqualTo(HttpEntryKind.SPRING_MVC_HTTP);
              assertThat(entry.protocol()).isEqualTo("HTTP");
              assertThat(entry.method()).isEqualTo("POST");
              assertThat(entry.route()).isEqualTo("/depotHead/batchSetStatus");
              assertThat(entry.routeParts()).containsExactly("/depotHead", "/batchSetStatus");
              assertThat(entry.handlerFqn())
                  .isEqualTo("com.example.DepotHeadController#batchSetStatus");
              assertThat(entry.parameterNames()).containsExactly("status", "ids");
              assertThat(entry.routeSourceExcerpts())
                  .extracting(excerpt -> excerpt.locator().path())
                  .containsExactly(
                      "src/main/java/com/example/DepotHeadController.java",
                      "src/main/java/com/example/DepotHeadController.java");
            });
    assertThat(discovery.shardReceipts())
        .singleElement()
        .satisfies(
            receipt -> {
              assertThat(receipt.denominatorSiteIds()).isEqualTo(receipt.dispositionSiteIds());
              assertThat(receipt.denominatorSiteIds()).hasSize(1);
              assertThat(receipt.gapIds()).isEmpty();
              assertThat(receipt.status()).isEqualTo("SUCCEEDED");
            });
  }

  @Test
  void recordsADynamicMethodRouteAsAnExplicitUnresolvedSiteInsteadOfGuessingIt() {
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    VerifiedSourceTextDocument controller =
        text(
            "src/main/java/com/example/DepotHeadController.java",
            """
            package com.example;

            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestMapping;

            @RequestMapping("/depotHead")
            public class DepotHeadController {
              private static final String ACTION = "/batchSetStatus";

              @PostMapping(ACTION)
              public String batchSetStatus(String status, String ids) {
                return "ok";
              }
            }
            """);
    VerifiedSourceTextReader sourceHandle = reference -> sourceTextSet(controller);

    HttpEntryDiscovery discovery =
        new SpringHttpEntryDiscoverer(sourceHandle)
            .discoverEntries(profile(controller), frozenSource);

    assertThat(discovery.entries()).isEmpty();
    assertThat(discovery.sites())
        .singleElement()
        .satisfies(
            site -> {
              assertThat(site.disposition()).isEqualTo(SignalDisposition.UNSUPPORTED);
              assertThat(site.reasonCode()).isEqualTo("DYNAMIC_ROUTE");
              assertThat(site.primaryExcerpt().rawUtf8().copyToByteArray())
                  .isEqualTo("@PostMapping(ACTION)".getBytes(StandardCharsets.UTF_8));
            });
  }

  @Test
  void recordsADynamicClassRouteAsAnExplicitUnresolvedSiteInsteadOfDroppingItsController() {
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    VerifiedSourceTextDocument controller =
        text(
            "src/main/java/com/example/DepotHeadController.java",
            """
            package com.example;

            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestMapping;

            @RequestMapping(DepotHeadRoutes.PREFIX)
            public class DepotHeadController {
              @PostMapping("/batchSetStatus")
              public String batchSetStatus(String status, String ids) {
                return "ok";
              }
            }
            """);
    VerifiedSourceTextReader sourceHandle = reference -> sourceTextSet(controller);

    HttpEntryDiscovery discovery =
        new SpringHttpEntryDiscoverer(sourceHandle)
            .discoverEntries(profile(controller), frozenSource);

    assertThat(discovery.entries()).isEmpty();
    assertThat(discovery.sites())
        .singleElement()
        .satisfies(
            site -> {
              assertThat(site.disposition()).isEqualTo(SignalDisposition.UNSUPPORTED);
              assertThat(site.reasonCode()).isEqualTo("DYNAMIC_ROUTE");
              assertThat(site.gapId()).isNotNull();
              assertThat(site.primaryExcerpt().rawUtf8().copyToByteArray())
                  .isEqualTo(
                      "@RequestMapping(DepotHeadRoutes.PREFIX)".getBytes(StandardCharsets.UTF_8));
            });
    assertThat(discovery.shardReceipts())
        .singleElement()
        .satisfies(
            receipt -> {
              assertThat(receipt.status()).isEqualTo("SUCCEEDED_WITH_GAPS");
              assertThat(receipt.gapIds()).containsExactly(discovery.sites().get(0).gapId());
            });
  }

  @Test
  void discoversAStaticGetMappingWithTheSameRouteEvidenceRulesAsPost() {
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    VerifiedSourceTextDocument controller =
        text(
            "src/main/java/com/example/DepotHeadController.java",
            """
            package com.example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;

            @RequestMapping("/depotHead")
            public class DepotHeadController {
              @GetMapping("/status")
              public String status(String id) {
                return "ok";
              }
            }
            """);
    VerifiedSourceTextReader sourceHandle = reference -> sourceTextSet(controller);

    HttpEntryDiscovery discovery =
        new SpringHttpEntryDiscoverer(sourceHandle)
            .discoverEntries(profile(controller), frozenSource);

    assertThat(discovery.entries())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.method()).isEqualTo("GET");
              assertThat(entry.route()).isEqualTo("/depotHead/status");
              assertThat(entry.handlerFqn()).isEqualTo("com.example.DepotHeadController#status");
              assertThat(entry.routeSourceExcerpts()).hasSize(2);
            });
  }

  @Test
  void discoversAStaticRequestMappingWhenItsHttpMethodIsExplicit() {
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    VerifiedSourceTextDocument controller =
        text(
            "src/main/java/com/example/DepotHeadController.java",
            """
            package com.example;

            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RequestMethod;

            @RequestMapping("/depotHead")
            public class DepotHeadController {
              @RequestMapping(value = "/batchSetStatus", method = RequestMethod.POST)
              public String batchSetStatus(String status, String ids) {
                return "ok";
              }
            }
            """);
    VerifiedSourceTextReader sourceHandle = reference -> sourceTextSet(controller);

    HttpEntryDiscovery discovery =
        new SpringHttpEntryDiscoverer(sourceHandle)
            .discoverEntries(profile(controller), frozenSource);

    assertThat(discovery.entries())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.method()).isEqualTo("POST");
              assertThat(entry.route()).isEqualTo("/depotHead/batchSetStatus");
              assertThat(entry.handlerFqn())
                  .isEqualTo("com.example.DepotHeadController#batchSetStatus");
            });
  }

  @Test
  void ignoresAnAnnotationWithASpringLikeSimpleNameWhenItsImportIsNotSpringMvc() {
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    VerifiedSourceTextDocument controller =
        text(
            "src/main/java/com/example/DepotHeadController.java",
            """
            package com.example;

            import com.example.web.PostMapping;
            import org.springframework.web.bind.annotation.RequestMapping;

            @RequestMapping("/depotHead")
            public class DepotHeadController {
              @PostMapping("/batchSetStatus")
              public String batchSetStatus(String status, String ids) {
                return "ok";
              }
            }
            """);
    VerifiedSourceTextReader sourceHandle = reference -> sourceTextSet(controller);

    HttpEntryDiscovery discovery =
        new SpringHttpEntryDiscoverer(sourceHandle)
            .discoverEntries(profile(controller), frozenSource);

    assertThat(discovery.entries()).isEmpty();
    assertThat(discovery.sites()).isEmpty();
  }

  @Test
  void recordsAMethodMappingWithoutClassRouteEvidenceAsAnExplicitGap() {
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    VerifiedSourceTextDocument controller =
        text(
            "src/main/java/com/example/DepotHeadController.java",
            """
            package com.example;

            import org.springframework.web.bind.annotation.PostMapping;

            public class DepotHeadController {
              @PostMapping("/batchSetStatus")
              public String batchSetStatus(String status, String ids) {
                return "ok";
              }
            }
            """);
    VerifiedSourceTextReader sourceHandle = reference -> sourceTextSet(controller);

    HttpEntryDiscovery discovery =
        new SpringHttpEntryDiscoverer(sourceHandle)
            .discoverEntries(profile(controller), frozenSource);

    assertThat(discovery.entries()).isEmpty();
    assertThat(discovery.sites())
        .singleElement()
        .satisfies(
            site -> {
              assertThat(site.disposition()).isEqualTo(SignalDisposition.UNSUPPORTED);
              assertThat(site.reasonCode()).isEqualTo("MISSING_CLASS_ROUTE");
              assertThat(site.gapId()).isNotNull();
              assertThat(site.primaryExcerpt().rawUtf8().copyToByteArray())
                  .isEqualTo("@PostMapping(\"/batchSetStatus\")".getBytes(StandardCharsets.UTF_8));
            });
  }

  @Test
  void recordsARequestMappingWithoutAnExplicitHttpMethodAsAnExplicitGap() {
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    VerifiedSourceTextDocument controller =
        text(
            "src/main/java/com/example/DepotHeadController.java",
            """
            package com.example;

            import org.springframework.web.bind.annotation.RequestMapping;

            @RequestMapping("/depotHead")
            public class DepotHeadController {
              @RequestMapping("/batchSetStatus")
              public String batchSetStatus(String status, String ids) {
                return "ok";
              }
            }
            """);
    VerifiedSourceTextReader sourceHandle = reference -> sourceTextSet(controller);

    HttpEntryDiscovery discovery =
        new SpringHttpEntryDiscoverer(sourceHandle)
            .discoverEntries(profile(controller), frozenSource);

    assertThat(discovery.entries()).isEmpty();
    assertThat(discovery.sites())
        .singleElement()
        .satisfies(
            site -> {
              assertThat(site.disposition()).isEqualTo(SignalDisposition.UNSUPPORTED);
              assertThat(site.reasonCode()).isEqualTo("UNSPECIFIED_HTTP_METHOD");
              assertThat(site.gapId()).isNotNull();
              assertThat(site.primaryExcerpt().rawUtf8().copyToByteArray())
                  .isEqualTo(
                      "@RequestMapping(\"/batchSetStatus\")".getBytes(StandardCharsets.UTF_8));
            });
  }

  @Test
  void recordsMultipleHttpMappingAnnotationsOnOneMethodAsAnAmbiguousSite() {
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    VerifiedSourceTextDocument controller =
        text(
            "src/main/java/com/example/DepotHeadController.java",
            """
            package com.example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestMapping;

            @RequestMapping("/depotHead")
            public class DepotHeadController {
              @GetMapping("/batchSetStatus")
              @PostMapping("/batchSetStatus")
              public String batchSetStatus(String status, String ids) {
                return "ok";
              }
            }
            """);
    VerifiedSourceTextReader sourceHandle = reference -> sourceTextSet(controller);

    HttpEntryDiscovery discovery =
        new SpringHttpEntryDiscoverer(sourceHandle)
            .discoverEntries(profile(controller), frozenSource);

    assertThat(discovery.entries()).isEmpty();
    assertThat(discovery.sites())
        .singleElement()
        .satisfies(
            site -> {
              assertThat(site.disposition()).isEqualTo(SignalDisposition.AMBIGUOUS);
              assertThat(site.reasonCode()).isEqualTo("MULTIPLE_HTTP_MAPPING_ANNOTATIONS");
              assertThat(site.gapId()).isNotNull();
              assertThat(site.primaryExcerpt().rawUtf8().copyToByteArray())
                  .isEqualTo("@GetMapping(\"/batchSetStatus\")".getBytes(StandardCharsets.UTF_8));
            });
  }

  @Test
  void rejectsOverlappingFileShardsBeforeTheyCanDoubleCountOneControllerSite() {
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    VerifiedSourceTextDocument controller =
        text(
            "src/main/java/com/example/DepotHeadController.java",
            """
            package com.example;

            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestMapping;

            @RequestMapping("/depotHead")
            public class DepotHeadController {
              @PostMapping("/batchSetStatus")
              public String batchSetStatus(String status, String ids) {
                return "ok";
              }
            }
            """);
    VerifiedSourceTextReader sourceHandle = reference -> sourceTextSet(controller);
    List<JavaSourceShard> overlappingShards =
        List.of(
            new JavaSourceShard(
                ArtifactId.parse("entry-shard:" + "e".repeat(64)), List.of(controller.fileId())),
            new JavaSourceShard(
                ArtifactId.parse("entry-shard:" + "f".repeat(64)), List.of(controller.fileId())));

    assertThatThrownBy(
            () ->
                new SpringHttpEntryDiscoverer(sourceHandle)
                    .discoverEntries(profile(controller), frozenSource, overlappingShards))
        .isInstanceOfSatisfying(
            ApplicationDiscoveryException.class,
            failure -> assertThat(failure.code()).isEqualTo("HTTP_ENTRY_SHARD_INVALID"));
  }

  private static ApplicationProfile profile(VerifiedSourceTextDocument document) {
    ArtifactControls controls =
        new ArtifactControls(
            digest('1'),
            digest('2'),
            digest('3'),
            null,
            new ArtifactPolicyRegistryReference(
                ArtifactId.parse("artifact-policy-registry:" + "4".repeat(64)), digest('4')));
    SourceExcerptV1 frameworkExcerpt =
        new SourceExcerptV1(
            new SourceLocatorV1(document.fileId(), document.path(), 0, 7, 1, 1, 1, 8),
            ImmutableBytes.copyOf("package".getBytes(StandardCharsets.UTF_8)),
            Sha256Digest.parse(sha256("package".getBytes(StandardCharsets.UTF_8))));
    return new ApplicationProfile(
        ArtifactId.parse("application-profile:" + "5".repeat(64)),
        "snapshot:" + "6".repeat(64),
        "COMPLETE_CAPTURE",
        true,
        ApplicationLanguage.JAVA,
        8,
        List.of(
            new FrameworkSignal(
                FrameworkSignalKind.SPRING_MVC,
                frameworkExcerpt,
                SignalDisposition.SUPPORTED,
                null)),
        List.of(),
        reference("capability-profile", '7'),
        reference("verified-source-inventory-source-inventory", '8'),
        reference("verified-snapshot", '9'),
        controls);
  }

  private static VerifiedSourceTextSet sourceTextSet(VerifiedSourceTextDocument document) {
    return new VerifiedSourceTextSet(
        "snapshot:" + "6".repeat(64),
        "COMPLETE_CAPTURE",
        true,
        reference("capability-profile", '7'),
        reference("verified-source-inventory-source-inventory", '8'),
        reference("verified-snapshot", '9'),
        new ArtifactControls(
            digest('1'),
            digest('2'),
            digest('3'),
            null,
            new ArtifactPolicyRegistryReference(
                ArtifactId.parse("artifact-policy-registry:" + "4".repeat(64)), digest('4'))),
        List.of(document));
  }

  private static VerifiedSourceInventoryReference frozenSource() {
    return new VerifiedSourceInventoryReference(
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(
                AnalysisRunId.parse("analysis-run:" + "a".repeat(64)),
                AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
            AnalysisStepArtifactRoot.parse("analysis-step-root:" + "b".repeat(64)),
            AnalysisStepReceiptId.parse("analysis-step-receipt:" + "c".repeat(64)),
            digest('d')));
  }

  private static VerifiedSourceTextDocument text(String path, String content) {
    byte[] rawUtf8 = content.getBytes(StandardCharsets.UTF_8);
    String sha256 = sha256(rawUtf8);
    return new VerifiedSourceTextDocument(
        ArtifactId.parse(
            "file:" + sha256((path + "\\n" + sha256).getBytes(StandardCharsets.UTF_8))),
        path,
        "100644",
        "text/plain",
        rawUtf8.length,
        Sha256Digest.parse(sha256),
        ImmutableBytes.copyOf(rawUtf8));
  }

  private static ArtifactReference reference(String prefix, char digit) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + String.valueOf(digit).repeat(64)), digest(digit));
  }

  private static Sha256Digest digest(char digit) {
    return Sha256Digest.parse(String.valueOf(digit).repeat(64));
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
