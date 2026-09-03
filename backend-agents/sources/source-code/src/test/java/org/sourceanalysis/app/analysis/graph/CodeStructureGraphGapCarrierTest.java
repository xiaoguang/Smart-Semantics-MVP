package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** RED contract for carrying a located, shared M1 graph Gap in the structure draft. */
class CodeStructureGraphGapCarrierTest {

  @Test
  void carriesOneLocatedMalformedSourceGapWithClosedCoverageAndStableIdentity() throws Exception {
    CodeStructureSource source = malformedJavaSource();
    CodeStructureDiscovery discovery = malformedDiscovery();
    CodeStructureGraphProfile profile =
        new CodeStructureGraphProfile(reference("graph-profile", "code-structure-v3"));

    CodeStructureGraphDraft draft =
        new CodeStructureGraphBuilder().buildStructure(source, discovery, profile);

    assertThat(draft.schemaVersion())
        .as("M1 must publish the shared Gap carrier in draft v3")
        .isEqualTo("program-graphs-code-structure-draft-v3");
    assertThat(draft.nodes()).isEmpty();
    assertThat(draft.edges()).isEmpty();
    assertThat(draft.coverage().gapDispositions()).hasSize(1);

    List<GraphGapDraft> gapDrafts = gapDrafts(draft);
    assertThat(gapDrafts).hasSize(1);
    GraphGapDraft gap = gapDrafts.get(0);
    assertThat(gap.reasonCode()).isEqualTo("JAVA_PARSE_UNSUPPORTED");
    assertThat(gap.affectedEntryIds()).isEmpty();
    assertThat(gap.candidateElementIds())
        .containsExactly(draft.coverage().gapDispositions().get(0).candidateElementId());
    assertThat(draft.coverage().gapDispositions().get(0).gapId()).isEqualTo(gap.gapId());
    assertThat(draft.coverage().candidateElementIds())
        .containsExactly(draft.coverage().gapDispositions().get(0).candidateElementId());

    assertThat(gap.gapId().value()).startsWith("graph-gap:");
    assertThatCode(() -> GraphGapDraft.requireIdentity(ProgramGraphKind.CODE_STRUCTURE, gap))
        .doesNotThrowAnyException();

    SourceLocatorV1 locator = gap.sourceLocator();
    CodeStructureSourceDocument document = source.documents().get(0);
    assertThat(locator.fileId()).isEqualTo(document.fileId());
    assertThat(locator.path()).isEqualTo(document.path());
    assertThat(locator.startByte()).isGreaterThanOrEqualTo(0);
    assertThat(locator.endByteExclusive()).isLessThanOrEqualTo(document.rawUtf8().size());
    assertThat(locator.startByte()).isLessThan(locator.endByteExclusive());
    assertThat(locator.startLine()).isLessThanOrEqualTo(5);
    assertThat(locator.endLine()).isGreaterThanOrEqualTo(5);

    CodeStructureGraphDraft rebuilt =
        new CodeStructureGraphBuilder().buildStructure(source, discovery, profile);
    assertThat(rebuilt).isEqualTo(draft);
  }

  @SuppressWarnings("unchecked")
  private static List<GraphGapDraft> gapDrafts(CodeStructureGraphDraft draft) {
    try {
      Method accessor = CodeStructureGraphDraft.class.getMethod("gapDrafts");
      return (List<GraphGapDraft>) accessor.invoke(draft);
    } catch (NoSuchMethodException missingAccessor) {
      throw new AssertionError(
          "M1 draft must expose the shared gapDrafts carrier", missingAccessor);
    } catch (IllegalAccessException | InvocationTargetException reflectionFailure) {
      throw new AssertionError("M1 gapDrafts accessor could not be read", reflectionFailure);
    }
  }

  private static CodeStructureSource malformedJavaSource() throws Exception {
    Path fixture =
        Path.of(
            "src/test/resources/analysis/graph/code-structure/src/main/java/com/example/MalformedController.java");
    byte[] bytes = Files.readAllBytes(fixture);
    return new CodeStructureSource(
        "snapshot:" + digest("malformed-source-gap-snapshot"),
        "COMPLETE_CAPTURE",
        true,
        reference("source-inventory", "malformed-source-gap-inventory"),
        reference("verified-snapshot", "malformed-source-gap-snapshot"),
        controls(),
        List.of(
            new CodeStructureSourceDocument(
                id("file", fixture.toString()),
                "src/main/java/com/example/MalformedController.java",
                ImmutableBytes.copyOf(bytes),
                new Sha256Digest(digest(bytes)))));
  }

  private static CodeStructureDiscovery malformedDiscovery() {
    return new CodeStructureDiscovery(
        id("application-profile", "malformed-source-gap-profile"),
        reference("application-profile", "malformed-source-gap-profile"),
        reference("capability-report", "malformed-source-gap-capability"),
        reference("entry-points", "malformed-source-gap-entries"),
        reference("mapper-catalog", "malformed-source-gap-mappers"),
        List.of(id("entry", "malformed-controller-entry")));
  }

  private static ArtifactControls controls() {
    return new ArtifactControls(
        new Sha256Digest(digest("toolchain")),
        new Sha256Digest(digest("profile")),
        new Sha256Digest(digest("schema")),
        null,
        new ArtifactPolicyRegistryReference(
            id("artifact-policy-registry", "structure-gap-policy"),
            new Sha256Digest(digest("structure-gap-policy"))));
  }

  private static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(id(prefix, value), new Sha256Digest(digest(value)));
  }

  private static ArtifactId id(String prefix, String value) {
    return ArtifactId.parse(prefix + ":" + digest(value));
  }

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
