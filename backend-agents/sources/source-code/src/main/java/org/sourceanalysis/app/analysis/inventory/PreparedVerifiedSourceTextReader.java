package org.sourceanalysis.app.analysis.inventory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.capture.preparation.PreparedSourceArchive;

/** Projects only the verified-text partition of a fresh-reopened v3 prepared source. */
public final class PreparedVerifiedSourceTextReader implements VerifiedSourceTextReader {

  private static final String VERIFIED_TEXT_MEDIA_TYPE = "text/plain; charset=UTF-8";
  private static final Comparator<String> UTF8_ORDER =
      PreparedVerifiedSourceTextReader::compareUtf8;

  private final SourcePreparationReader preparations;
  private final PreparedSourceArchive archive;

  public PreparedVerifiedSourceTextReader(
      SourcePreparationReader preparations, PreparedSourceArchive archive) {
    this.preparations = Objects.requireNonNull(preparations, "source preparation reader");
    this.archive = Objects.requireNonNull(archive, "prepared source archive");
  }

  @Override
  public VerifiedSourceTextSet reopen(VerifiedSourceInventoryReference frozenSource) {
    try {
      requirePreparedSourceReference(frozenSource);
      SavedSourcePreparation saved = preparations.reopen(frozenSource.publication());
      if (!frozenSource.publication().equals(saved.reportReference())
          || saved.sourceVersionReference() == null
          || !frozenSource.publication().equals(saved.sourceVersionReference().publication())) {
        throw invalid();
      }
      String scopeKind;
      boolean repositoryCompletionEligible;
      if (saved.assessment().readiness() == SourcePreparationReadiness.READY) {
        scopeKind = "COMPLETE_CAPTURE";
        repositoryCompletionEligible = true;
      } else if (saved.assessment().readiness()
          == SourcePreparationReadiness.READY_WITH_EXCLUSIONS) {
        scopeKind = "BOUNDED_PATH_SET";
        repositoryCompletionEligible = false;
      } else {
        throw invalid();
      }
      List<VerifiedSourceTextDocument> documents = verifiedTextDocuments(saved);
      PreparedSourcePublicationFacts facts = saved.publicationFacts();
      return new VerifiedSourceTextSet(
          saved.sourceVersionReference().sourceVersionId().value(),
          scopeKind,
          repositoryCompletionEligible,
          facts.capabilityProfileRef(),
          facts.sourceInventoryRef(),
          facts.verifiedSnapshotRef(),
          facts.controls(),
          documents);
    } catch (IllegalArgumentException failure) {
      throw failure;
    } catch (IOException failure) {
      throw new IllegalArgumentException("PREPARED_SOURCE_TEXT_REOPEN_INVALID", failure);
    }
  }

  private List<VerifiedSourceTextDocument> verifiedTextDocuments(SavedSourcePreparation saved)
      throws IOException {
    List<SourceEntry> verified =
        saved.result().entries().stream()
            .filter(entry -> entry.disposition() == SourceEntry.Disposition.VERIFIED_TEXT)
            .sorted(Comparator.comparing(SourceEntry::relativePath, UTF8_ORDER))
            .toList();
    List<VerifiedSourceTextDocument> documents = new ArrayList<>();
    for (SourceEntry entry : verified) {
      ImmutableBytes bytes;
      try (InputStream input =
          archive.open(saved.sourceVersionReference().sourceVersionId(), entry)) {
        bytes = ImmutableBytes.copyOf(input.readAllBytes());
      }
      requireUtf8(bytes);
      documents.add(
          new VerifiedSourceTextDocument(
              entry.fileId(),
              entry.relativePath(),
              gitMode(entry),
              VERIFIED_TEXT_MEDIA_TYPE,
              entry.sizeBytes(),
              entry.sha256(),
              bytes));
    }
    if (documents.isEmpty()) {
      throw invalid();
    }
    return List.copyOf(documents);
  }

  private static void requirePreparedSourceReference(VerifiedSourceInventoryReference reference) {
    if (reference == null
        || reference.publication() == null
        || reference.publication().address().analysisStepKey()
            != AnalysisStepKey.VERIFIED_SOURCE_INVENTORY) {
      throw invalid();
    }
  }

  private static String gitMode(SourceEntry entry) {
    SourceOriginAttributes attributes = entry.originAttributes();
    if (attributes.kind() == SourceOriginAttributes.Kind.DIRECTORY) {
      if (attributes.gitMode() != null) {
        throw invalid();
      }
      return null;
    }
    if (!"100644".equals(attributes.gitMode()) && !"100755".equals(attributes.gitMode())) {
      throw invalid();
    }
    return attributes.gitMode();
  }

  private static void requireUtf8(ImmutableBytes bytes) {
    try {
      CharsetDecoder decoder =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT);
      decoder.decode(ByteBuffer.wrap(bytes.copyToByteArray()));
    } catch (CharacterCodingException invalid) {
      throw new IllegalArgumentException("PREPARED_SOURCE_TEXT_REOPEN_INVALID", invalid);
    }
  }

  private static int compareUtf8(String first, String second) {
    byte[] left = first.getBytes(StandardCharsets.UTF_8);
    byte[] right = second.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(left.length, right.length);
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("PREPARED_SOURCE_TEXT_REOPEN_INVALID");
  }
}
