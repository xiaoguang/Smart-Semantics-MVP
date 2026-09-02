package org.sourceanalysis.app.analysis.inventory;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.localgit.LocalGitSourceRegistry;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceCapture;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceFile;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceSnapshot;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationRegistryException;

/**
 * Reopens a registered capture and verifies every M1-admitted regular file from its sealed bytes.
 *
 * <p>This module deliberately has no source-root {@code Path} input. It can read bytes only from an
 * opaque registered snapshot handle after proving the requested metadata is the exact capture
 * metadata that M1 admitted.
 */
public final class VerifiedSourceIndexer {

  private static final CanonicalJsonCodec CANONICAL_JSON = new CanonicalJsonCodec();
  private static final Comparator<String> UTF8_ORDER = VerifiedSourceIndexer::compareUtf8;

  /**
   * Produces a fully verified source inventory from one M1-admitted request.
   *
   * @throws VerifiedSourceIndexException when capture identity or sealed file bytes do not satisfy
   *     the inventory contract
   */
  public VerifiedSourceIndex index(
      AdmittedSourceRequest request, LocalGitSourceRegistry sourceRegistry) {
    if (request == null || sourceRegistry == null) {
      throw failure("REQUEST_SCHEMA_INVALID");
    }

    try {
      RegisteredSourceSnapshot snapshot =
          sourceRegistry.openSnapshot(request.sourceRegistrationId());
      RegisteredSourceCapture capture = snapshot.capture();
      verifyCaptureIdentity(request, capture);
      List<CapturedRegularFile> admittedFiles = verifiedAdmissionFiles(request, capture);

      List<VerifiedSourceFile> verifiedFiles = new ArrayList<>(admittedFiles.size());
      for (CapturedRegularFile admittedFile : admittedFiles) {
        RegisteredSourceFile manifestFile = matchingManifestFile(capture, admittedFile.path());
        ImmutableBytes sealedBytes = snapshot.read(manifestFile);
        byte[] rawBytes = sealedBytes.copyToByteArray();
        verifyBytes(admittedFile, rawBytes);
        verifiedFiles.add(verifiedFile(admittedFile, rawBytes));
      }

      int textCount =
          (int)
              verifiedFiles.stream()
                  .filter(
                      file ->
                          file.analysisDisposition() == SourceAnalysisDisposition.ANALYZABLE_TEXT)
                  .count();
      return new VerifiedSourceIndex(
          capture.snapshotId(),
          request.requestIdentity(),
          verifiedFiles.size(),
          textCount,
          verifiedFiles.size() - textCount,
          verifiedFiles);
    } catch (VerifiedSourceIndexException failure) {
      throw failure;
    } catch (SourceRegistrationRegistryException failure) {
      throw failure(failure.code());
    } catch (RuntimeException failure) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
  }

  private static void verifyCaptureIdentity(
      AdmittedSourceRequest request, RegisteredSourceCapture capture) {
    if (!request.originRepositoryUrl().equals(capture.declaredRepositoryIdentity())
        || !request.originRevision().equals(capture.commitId())
        || !request.captureReceiptRef().equals(capture.captureReceiptRef())
        || !request.snapshotManifestRef().equals(capture.snapshotManifestRef())
        || request.declaredPathCount() != capture.manifestEntries().size()) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
  }

  private static List<CapturedRegularFile> verifiedAdmissionFiles(
      AdmittedSourceRequest request, RegisteredSourceCapture capture) {
    List<CapturedRegularFile> files = request.files();
    if (files.size() != capture.manifestEntries().size() || !isStrictUtf8Sorted(files)) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }

    Map<String, RegisteredSourceFile> manifestByPath = new HashMap<>();
    for (RegisteredSourceFile entry : capture.manifestEntries()) {
      if (manifestByPath.put(entry.path(), entry) != null) {
        throw failure("DUPLICATE_SOURCE_PATH");
      }
    }
    Set<ArtifactId> textIds = new HashSet<>(request.analyzableTextFileIds());
    Set<ArtifactId> mediaIds = new HashSet<>(request.nonAnalyzableMediaFileIds());
    if (textIds.size() != request.analyzableTextFileIds().size()
        || mediaIds.size() != request.nonAnalyzableMediaFileIds().size()
        || !disjoint(textIds, mediaIds)) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }

    for (CapturedRegularFile file : files) {
      RegisteredSourceFile manifestEntry = manifestByPath.get(file.path());
      if (manifestEntry == null || !sameManifestMetadata(file, manifestEntry)) {
        throw failure("CAPTURE_IDENTITY_INVALID");
      }
      ArtifactId expectedFileId =
          fileId(file.path(), file.gitMode(), file.sizeBytes(), file.sha256());
      if (!file.fileId().equals(expectedFileId)
          || (file.analysisDisposition() == SourceAnalysisDisposition.ANALYZABLE_TEXT
              && !textIds.contains(file.fileId()))
          || (file.analysisDisposition() == SourceAnalysisDisposition.NON_ANALYZABLE_MEDIA
              && !mediaIds.contains(file.fileId()))) {
        throw failure("CAPTURE_IDENTITY_INVALID");
      }
    }
    if (textIds.size() + mediaIds.size() != files.size()) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
    return files;
  }

  private static boolean sameManifestMetadata(
      CapturedRegularFile admitted, RegisteredSourceFile registered) {
    return admitted.gitMode().equals(registered.gitMode())
        && admitted.mediaType().equals(registered.mediaType())
        && admitted.sizeBytes() == registered.sizeBytes()
        && admitted.sha256().equals(registered.sha256())
        && admitted.analysisDisposition().name().equals(registered.analysisDisposition())
        && java.util.Objects.equals(admitted.textEncoding(), registered.textEncoding());
  }

  private static boolean isStrictUtf8Sorted(List<CapturedRegularFile> files) {
    String previous = null;
    Set<String> paths = new HashSet<>();
    for (CapturedRegularFile file : files) {
      if (!paths.add(file.path())
          || (previous != null && UTF8_ORDER.compare(previous, file.path()) >= 0)) {
        return false;
      }
      previous = file.path();
    }
    return true;
  }

  private static boolean disjoint(Set<ArtifactId> first, Set<ArtifactId> second) {
    return first.stream().noneMatch(second::contains);
  }

  private static RegisteredSourceFile matchingManifestFile(
      RegisteredSourceCapture capture, String path) {
    return capture.manifestEntries().stream()
        .filter(entry -> entry.path().equals(path))
        .findFirst()
        .orElseThrow(() -> failure("CAPTURE_IDENTITY_INVALID"));
  }

  private static void verifyBytes(CapturedRegularFile file, byte[] bytes) {
    if (bytes.length != file.sizeBytes()) {
      throw failure("SOURCE_SIZE_MISMATCH");
    }
    if (!sha256(bytes).equals(file.sha256().value())) {
      throw failure("SOURCE_HASH_MISMATCH");
    }
  }

  private static VerifiedSourceFile verifiedFile(CapturedRegularFile file, byte[] rawBytes) {
    if (file.analysisDisposition() == SourceAnalysisDisposition.NON_ANALYZABLE_MEDIA) {
      if (file.textEncoding() != null) {
        throw failure("SOURCE_TEXT_DISPOSITION_INVALID");
      }
      return new VerifiedSourceFile(
          file.fileId(),
          file.path(),
          file.gitMode(),
          file.mediaType(),
          file.sizeBytes(),
          file.sha256(),
          file.analysisDisposition(),
          null,
          null,
          List.of());
    }

    requirePermittedText(rawBytes);
    List<Long> lineStarts = lineStarts(rawBytes);
    return new VerifiedSourceFile(
        file.fileId(),
        file.path(),
        file.gitMode(),
        file.mediaType(),
        file.sizeBytes(),
        file.sha256(),
        file.analysisDisposition(),
        "UTF-8",
        new Sha256Digest(lineIndexDigest(lineStarts)),
        lineStarts);
  }

  private static void requirePermittedText(byte[] rawBytes) {
    try {
      String decoded =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(rawBytes))
              .toString();
      for (int offset = 0; offset < decoded.length(); ) {
        int codePoint = decoded.codePointAt(offset);
        if (isForbiddenControl(codePoint)) {
          throw failure("SOURCE_TEXT_DISPOSITION_INVALID");
        }
        offset += Character.charCount(codePoint);
      }
    } catch (CharacterCodingException failure) {
      throw failure("SOURCE_TEXT_DISPOSITION_INVALID");
    }
  }

  private static boolean isForbiddenControl(int codePoint) {
    return codePoint == 0
        || (codePoint >= 0x01 && codePoint <= 0x08)
        || (codePoint >= 0x0b && codePoint <= 0x0c)
        || (codePoint >= 0x0e && codePoint <= 0x1f);
  }

  private static List<Long> lineStarts(byte[] rawBytes) {
    List<Long> starts = new ArrayList<>();
    starts.add(0L);
    for (int index = 0; index < rawBytes.length; index++) {
      if (rawBytes[index] == '\n' && index + 1 < rawBytes.length) {
        starts.add((long) index + 1L);
      }
    }
    return List.copyOf(starts);
  }

  private static ArtifactId fileId(
      String path, String gitMode, long sizeBytes, Sha256Digest sha256) {
    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("path", path);
    material.put("gitMode", gitMode);
    material.put("sizeBytes", sizeBytes);
    material.put("sha256", sha256.value());
    byte[] materialBytes = CANONICAL_JSON.encodeCanonical(material).copyToByteArray();
    return ArtifactId.parse(
        "file:" + sha256(concatenate(frame("verified-source-file-id-v1"), frame(materialBytes))));
  }

  private static String lineIndexDigest(List<Long> lineStarts) {
    ByteBuffer bytes =
        ByteBuffer.allocate(Long.BYTES * lineStarts.size()).order(ByteOrder.BIG_ENDIAN);
    lineStarts.forEach(bytes::putLong);
    return sha256(concatenate(frame("verified-source-line-index-v1"), frame(bytes.array())));
  }

  private static int compareUtf8(String left, String right) {
    byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
    byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
    int commonLength = Math.min(leftBytes.length, rightBytes.length);
    for (int index = 0; index < commonLength; index++) {
      int comparison =
          Integer.compare(
              Byte.toUnsignedInt(leftBytes[index]), Byte.toUnsignedInt(rightBytes[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(leftBytes.length, rightBytes.length);
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[] first, byte[] second) {
    byte[] result = new byte[first.length + second.length];
    System.arraycopy(first, 0, result, 0, first.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private static VerifiedSourceIndexException failure(String code) {
    return new VerifiedSourceIndexException(code);
  }
}
