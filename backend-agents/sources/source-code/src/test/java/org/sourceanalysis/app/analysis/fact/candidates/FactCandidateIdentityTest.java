package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.SoftAssertions;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

/**
 * RED for the M1 candidate-set identity closure.
 *
 * <p>The candidate set identity must change when a candidate's wire-visible atom, Java-local
 * origin, or evidence closure changes. It must remain stable when only input collection order
 * changes. This test uses typed records directly and deliberately does not use raw JSON, graph
 * drafts, source paths, or the legacy POC.
 */
class FactCandidateIdentityTest {

  @Test
  void identityCoversCandidateSemanticsButIgnoresEquivalentInputOrder() {
    FactCandidateSet.FactCandidate approve =
        candidate("approve", "approve", "approve", "approve", "INVOCATION_CALL_ID");
    FactCandidateSet.FactCandidate cancel =
        candidate("cancel", "cancel", "cancel", "cancel", "INVOCATION_CALL_ID");
    List<ArtifactReference> roots = roots();
    List<ArtifactReference> reorderedRoots = new ArrayList<>(roots);
    Collections.reverse(reorderedRoots);

    FactCandidateSet base = FactCandidateSet.create(roots, List.of(approve, cancel), List.of());
    FactCandidateSet reordered =
        FactCandidateSet.create(reorderedRoots, List.of(cancel, approve), List.of());
    SoftAssertions softly = new SoftAssertions();
    softly
        .assertThat(reordered.candidateSetId())
        .as("equivalent root/candidate ordering must not change the identity")
        .isEqualTo(base.candidateSetId());

    FactCandidateSet changedRequiredAtom =
        FactCandidateSet.create(
            roots,
            List.of(
                candidate("approve", "approve", "approve", "approve", "CONTROL_CONTEXT"),
                cancel),
            List.of());
    softly.assertThat(changedRequiredAtom.candidateSetId())
        .as("required atom changes are wire-visible candidate semantics")
        .isNotEqualTo(base.candidateSetId());

    FactCandidateSet changedJavaOrigin =
        FactCandidateSet.create(
            roots,
            List.of(candidate("approve", "approve", "different-origin", "approve", "INVOCATION_CALL_ID"), cancel),
            List.of());
    softly.assertThat(changedJavaOrigin.candidateSetId())
        .as("Java-local argument origin changes are wire-visible candidate semantics")
        .isNotEqualTo(base.candidateSetId());

    FactCandidateSet changedEvidence =
        FactCandidateSet.create(
            roots,
            List.of(candidate("approve", "approve", "approve", "different-evidence", "INVOCATION_CALL_ID"), cancel),
            List.of());
    softly.assertThat(changedEvidence.candidateSetId())
        .as("evidence closure changes are wire-visible candidate semantics")
        .isNotEqualTo(base.candidateSetId());
    softly.assertAll();
  }

  private static FactCandidateSet.FactCandidate candidate(
      String entryToken,
      String boundaryToken,
      String originToken,
      String evidenceToken,
      String atomKey) {
    String entryId = id("entry", entryToken);
    String boundaryId = id("node", boundaryToken + "-boundary");
    String invocationId = id("call", boundaryToken + "-invocation");
    String targetEdgeId = id("edge", boundaryToken + "-target");
    String argumentNodeId = id("node", boundaryToken + "-argument");
    String argumentEdgeId = id("edge", boundaryToken + "-argument");
    String originId = id("node", originToken + "-origin");
    String blockId = id("node", boundaryToken + "-block");
    return new FactCandidateSet.FactCandidate(
        "JAVA_BOUNDARY_INVOCATION",
        entryId,
        "JAVA_BOUNDARY_INVOCATION",
        boundaryId,
        invocationId,
        targetEdgeId,
        "com.example.BoundaryClient",
        "send",
        "com.example.BoundaryClient#send(java.lang.String)",
        List.of(argumentEdgeId),
        List.of(
            new FactCandidateSet.BoundaryArgumentBinding(
                0, argumentNodeId, argumentEdgeId, List.of(originId))),
        blockId,
        null,
        List.of(
            new FactCandidateSet.SubjectEvidenceBinding(
                boundaryId,
                List.of(id("evidence", evidenceToken + "-source")),
                List.of(id("evidence", evidenceToken + "-rule")))),
        List.of(
            new FactCandidateSet.RequiredAtom(
                atomKey, "RELATIONSHIP", "SYMBOL_REF", List.of("SOURCE_EXCERPT", "RULE_APPLICATION"))));
  }

  private static List<ArtifactReference> roots() {
    return List.of(
        reference("structure"),
        reference("call"),
        reference("control"),
        reference("data"),
        reference("evidence"));
  }

  private static ArtifactReference reference(String token) {
    String digest = digest(token);
    return new ArtifactReference(ArtifactId.parse("root:" + digest), new Sha256Digest(digest));
  }

  private static String id(String prefix, String token) {
    return prefix + ":" + digest(token);
  }

  private static String digest(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
