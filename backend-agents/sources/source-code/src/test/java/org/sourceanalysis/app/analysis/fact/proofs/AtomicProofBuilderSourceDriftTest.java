package org.sourceanalysis.app.analysis.fact.proofs;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateEnumerator;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateInputs;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSet;
import org.sourceanalysis.app.analysis.fact.candidates.FactRegistry;
import org.sourceanalysis.app.analysis.fact.candidates.PersistedFactCandidateInputReader;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Verifies that an Evidence excerpt cannot silently bind to changed frozen source bytes. */
class AtomicProofBuilderSourceDriftTest {

  @TempDir Path temporaryDirectory;

  @Test
  void rejectsAReopenedSourceWhoseBytesNoLongerMatchItsEvidenceExcerpt() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("source-drift-graphs"))) {
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      FactCandidateSet candidates =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());
      VerifiedSourceTextReader sourceDrift =
          ignored -> replaceEveryDocumentByte(fixture.sourceReader().reopen(ignored));

      assertThatThrownBy(
              () ->
                  new AtomicProofBuilder(sourceDrift)
                      .prove(
                          candidates,
                          inputs,
                          fixture.sourceInventory(),
                          ProofRuleRegistry.standardJavaBoundary()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("PROOF_SOURCE_REOPEN_MISMATCH");
    }
  }

  private static VerifiedSourceTextSet replaceEveryDocumentByte(VerifiedSourceTextSet original) {
    List<VerifiedSourceTextDocument> changed =
        original.documents().stream().map(AtomicProofBuilderSourceDriftTest::changed).toList();
    return new VerifiedSourceTextSet(
        original.snapshotId(),
        original.inventoryScopeKind(),
        original.repositoryCompletionEligible(),
        original.capabilityProfileRef(),
        original.sourceInventoryRef(),
        original.verifiedSnapshotRef(),
        original.controls(),
        changed);
  }

  private static VerifiedSourceTextDocument changed(VerifiedSourceTextDocument document) {
    byte[] bytes = new byte[Math.toIntExact(document.sizeBytes())];
    java.util.Arrays.fill(bytes, (byte) 'x');
    return new VerifiedSourceTextDocument(
        document.fileId(),
        document.path(),
        document.gitMode(),
        document.mediaType(),
        document.sizeBytes(),
        Sha256Digest.parse(sha256(bytes)),
        ImmutableBytes.copyOf(bytes));
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
