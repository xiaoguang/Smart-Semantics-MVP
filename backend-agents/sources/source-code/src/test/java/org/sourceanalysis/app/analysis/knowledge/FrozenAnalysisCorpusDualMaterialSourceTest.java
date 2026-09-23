package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialMode;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.analysis.interpretation.material.ModelActivityPacket;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialProfile;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.Sha256Digest;

class FrozenAnalysisCorpusDualMaterialSourceTest {

  @Test
  void readsLegacyAndKeepsEqualStep05ShortReferencesIsolatedByPacket() {
    DefaultBusinessProcessDiscovery.FrozenCorpus legacy =
        DefaultBusinessProcessDiscovery.FrozenCorpus.open(legacyActivities(), legacyMaterials());

    assertThat(legacy.activitySourceRefs("activity:legacy")).containsExactly("S1");
    assertThat(legacy.source("S1").snippet()).isEqualTo("class LegacyService {}");

    FrozenProcessSourceCorpus sourceText =
        new FrozenProcessSourceCorpus(
            sourceTextSet(
                List.of(
                    text("src/main/java/example/AlphaService.java", "class AlphaService {}\n"),
                    text("src/main/java/example/BetaService.java", "class BetaService {}\n"))));
    DefaultBusinessProcessDiscovery.FrozenCorpus step05 =
        DefaultBusinessProcessDiscovery.FrozenCorpus.open(
            step05Activities(), step05Materials(), sourceText);

    String alphaRef = step05.activitySourceRefs("activity:alpha").iterator().next();
    String betaRef = step05.activitySourceRefs("activity:beta").iterator().next();
    assertThat(alphaRef).isNotEqualTo(betaRef);
    assertThat(step05.source(alphaRef))
        .extracting("file", "startLine", "endLine", "snippet")
        .containsExactly(
            "src/main/java/example/AlphaService.java", 1, 1, "class AlphaService {}\n");
    assertThat(step05.source(betaRef))
        .extracting("file", "startLine", "endLine", "snippet")
        .containsExactly("src/main/java/example/BetaService.java", 1, 1, "class BetaService {}\n");
    assertThat(step05.activityJson("activity:alpha").path("sourceRefs").get(0).asText())
        .isEqualTo(alphaRef);
    assertThat(step05.activityJson("activity:beta").path("sourceRefs").get(0).asText())
        .isEqualTo(betaRef);
  }

  static ActivityExplanationResult legacyActivities() {
    ReviewedActivity activity = legacyActivity();
    return new ActivityExplanationResult(
        List.of(activity),
        List.of(
            new ActivityEntryCoverage(
                "entry:legacy", "ANALYZED", List.of(activity.activityId()), null)));
  }

  private static ReviewedActivity legacyActivity() {
    return activity(
        "activity:legacy",
        "material:legacy",
        "entry:legacy",
        List.of("S1"),
        "BUSINESS_MATERIALS",
        null,
        Map.of());
  }

  static BusinessMaterialBuildResult legacyMaterials() {
    SourceReference source =
        new SourceReference(
            "S1", "src/main/java/example/LegacyService.java", 1, 1, "class LegacyService {}");
    BusinessMaterial material =
        new BusinessMaterial(
            "material:legacy",
            List.of("entry:legacy"),
            BusinessMaterialMode.FLOW_PREFERRED,
            "legacy context",
            List.of("legacy observation"),
            List.of(source),
            List.of(),
            List.of(),
            List.of(),
            new ModelActivityPacket(
                "legacy context",
                List.of("legacy observation"),
                List.of(new ModelActivityPacket.AllowlistedReference("S1", source.snippet())),
                List.of()));
    return new BusinessMaterialBuildResult(
        new BusinessMaterialSet(
            "business-material-set:" + "a".repeat(64),
            List.of(material),
            List.of(
                new BusinessMaterialEntryCoverage(
                    "entry:legacy", "ANALYZED_MATERIAL", "material:legacy", null))),
        moduleReference());
  }

  static ActivityExplanationResult step05Activities() {
    ReviewedActivity alpha =
        activity(
            "activity:alpha",
            "packet:alpha",
            "entry:alpha",
            List.of("S1"),
            "CODE_READING_MATERIALS",
            null,
            Map.of("S1", "source:1"));
    ReviewedActivity beta =
        activity(
            "activity:beta",
            "packet:beta",
            "entry:beta",
            List.of("S1"),
            "CODE_READING_MATERIALS",
            "slice:beta",
            Map.of("S1", "source:1"));
    return new ActivityExplanationResult(
        List.of(alpha, beta),
        List.of(
            new ActivityEntryCoverage("entry:alpha", "ANALYZED", List.of(alpha.activityId()), null),
            new ActivityEntryCoverage("entry:beta", "ANALYZED", List.of(beta.activityId()), null)));
  }

  private static ReviewedActivity activity(
      String activityId,
      String materialId,
      String entryId,
      List<String> sourceRefs,
      String materialSource,
      String sliceKey,
      Map<String, String> originalSourceRefs) {
    return new ReviewedActivity(
        activityId,
        materialId,
        List.of(entryId),
        "Activity " + activityId,
        "Read saved source",
        List.of("operator"),
        List.of("object"),
        List.of("input"),
        List.of("condition"),
        List.of("step"),
        List.of("result"),
        List.of("rule"),
        List.of(),
        List.of("term"),
        "DIRECT_CODE_BEHAVIOR",
        sourceRefs,
        List.of(),
        List.of(),
        materialSource,
        sliceKey,
        originalSourceRefs);
  }

  static CodeReadingMaterialSet step05Materials() {
    CodeReadingMaterialSet.Packet alpha =
        packet(
            "packet:alpha",
            "src/main/java/example/AlphaService.java",
            "method:alpha",
            "entry:alpha");
    CodeReadingMaterialSet.Packet beta =
        packet(
            "packet:beta", "src/main/java/example/BetaService.java", "method:beta", "entry:beta");
    return new CodeReadingMaterialSet(
        new CodeReadingMaterialSet.Header(
            new VerifiedSourceInventoryReference(
                stepReference(AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, '1')),
            new ProgramGraphsReference(stepReference(AnalysisStepKey.PROGRAM_GRAPHS, '2')),
            stepReference(AnalysisStepKey.PROVEN_CODE_FACTS, '3'),
            "snapshot:" + "a".repeat(64),
            new CodeReadingMaterialProfile(4_096L, 2)),
        List.of(alpha, beta),
        List.of(
            new CodeReadingMaterialSet.EntryCoverage(
                "entry:alpha",
                List.of("packet:alpha"),
                CodeReadingMaterialSet.CoverageStatus.COLLECTED,
                List.of()),
            new CodeReadingMaterialSet.EntryCoverage(
                "entry:beta",
                List.of("packet:beta"),
                CodeReadingMaterialSet.CoverageStatus.COLLECTED,
                List.of())));
  }

  private static CodeReadingMaterialSet.Packet packet(
      String packetId, String path, String methodKey, String entryId) {
    return new CodeReadingMaterialSet.Packet(
        packetId,
        List.of(new EntrySeed(entryId, methodKey, new SourceRange(0, 1, 1, 1), "fixture entry")),
        List.of(),
        List.of(),
        new CodeReadingMaterialSet.PersistenceSelection(
            List.of(), List.of(), List.of(), List.of(), List.of()),
        List.of(
            new CodeReadingMaterialSet.SourceReference(
                "source:1",
                new CodeReadingMaterialSet.UnitLocation("JAVA_METHOD", methodKey, path, 1, 1))),
        List.of(),
        List.of(),
        0L);
  }

  static VerifiedSourceTextSet sourceTextSet(List<VerifiedSourceTextDocument> documents) {
    return new VerifiedSourceTextSet(
        "snapshot:" + "a".repeat(64),
        "COMPLETE_CAPTURE",
        true,
        artifactReference("capability-profile", '4'),
        artifactReference("source-inventory", '5'),
        artifactReference("verified-snapshot", '6'),
        new ArtifactControls(
            Sha256Digest.parse("7".repeat(64)),
            Sha256Digest.parse("8".repeat(64)),
            Sha256Digest.parse("9".repeat(64)),
            Sha256Digest.parse("a".repeat(64)),
            new ArtifactPolicyRegistryReference(
                ArtifactId.parse("artifact-policy-registry:" + "b".repeat(64)),
                Sha256Digest.parse("b".repeat(64)))),
        documents);
  }

  static VerifiedSourceTextDocument text(String path, String source) {
    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
    String sha = sha256(bytes);
    return new VerifiedSourceTextDocument(
        ArtifactId.parse("file:" + sha256((path + sha).getBytes(StandardCharsets.UTF_8))),
        path,
        "100644",
        "text/x-java-source",
        bytes.length,
        Sha256Digest.parse(sha),
        ImmutableBytes.copyOf(bytes));
  }

  private static ArtifactReference artifactReference(String prefix, char digit) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + String.valueOf(digit).repeat(64)),
        Sha256Digest.parse(String.valueOf(digit).repeat(64)));
  }

  private static AnalysisStepPublicationReference stepReference(AnalysisStepKey key, char digit) {
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(
            AnalysisRunId.parse("analysis-run:" + "c".repeat(64)), key),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + String.valueOf(digit).repeat(64)),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + String.valueOf(digit).repeat(64)),
        Sha256Digest.parse(String.valueOf(digit).repeat(64)));
  }

  private static ModulePublicationReference moduleReference() {
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(
            AnalysisRunId.parse("analysis-run:" + "d".repeat(64)),
            AnalysisStepKey.FLOW_INTERPRETATION,
            10,
            "business-material-builder"),
        ModuleArtifactRoot.parse("module-root:" + "d".repeat(64)),
        ModuleReceiptId.parse("module-receipt:" + "d".repeat(64)),
        Sha256Digest.parse("d".repeat(64)));
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
