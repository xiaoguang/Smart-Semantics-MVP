package org.sourceanalysis.app.analysis.inventory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.sourceanalysis.app.adapter.cli.SourcePreparationPolicyFixture;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.preparation.CapturedSourcePreparation;
import org.sourceanalysis.app.capture.preparation.PreparedSourceArchive;
import org.sourceanalysis.app.capture.preparation.SourcePreparationToolIdentity;

/** Publishes and fresh-reopens self-owned R0 inventories for source-range reader tests. */
public final class VerifiedSourceFileActivationRangeR0Fixture {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final ArtifactStoreLimits STORE_LIMITS =
      new ArtifactStoreLimits(8, 1_000_000, 2_000_000, 16);
  private static final String LOGICAL_IDENTITY = "fixture.invalid/activation-range";
  private static final byte[] MEDIA_BYTES = new byte[] {0, 2, 4, 6};
  private static final String JAVA_SOURCE_PATH = "src/main/java/fixture/activation/Module.java";
  private static final byte[] JAVA_SOURCE_BYTES =
      "package fixture.activation; final class Module {}\n".getBytes(StandardCharsets.UTF_8);

  private VerifiedSourceFileActivationRangeR0Fixture() {}

  public static PublishedSource publish(
      Path temporaryRoot, String label, String sourceText, String observationFileKey)
      throws IOException {
    return publish(temporaryRoot, label, sourceText, observationFileKey, Map.of());
  }

  public static PublishedSource publish(
      Path temporaryRoot,
      String label,
      String sourceText,
      String observationFileKey,
      Map<String, String> additionalVerifiedTexts)
      throws IOException {
    return publish(
        temporaryRoot,
        label,
        sourceText,
        observationFileKey,
        additionalVerifiedTexts,
        List.of(),
        List.of());
  }

  public static PublishedSource publish(
      Path temporaryRoot,
      String label,
      String sourceText,
      String observationFileKey,
      Map<String, String> additionalVerifiedTexts,
      List<SourcePreparationTarget> declaredExclusions,
      List<String> skippedSymlinks)
      throws IOException {
    Path sourceRoot =
        Files.createDirectories(temporaryRoot.resolve(label + "-source")).toRealPath();
    Path storeRoot = Files.createDirectories(temporaryRoot.resolve(label + "-store"));
    Path archiveRoot = temporaryRoot.resolve(label + "-archive");
    CanonicalArtifactPolicyRegistry policies =
        SourcePreparationPolicyFixture.load(
            Path.of("tools/repository-run/source-preparation-artifact-policy-set-v1.json")
                .toAbsolutePath(),
            JSON);
    ArtifactReference policyRef =
        new ArtifactReference(policies.reference().artifactId(), policies.reference().sha256());
    byte[] textBytes = sourceText.getBytes(StandardCharsets.UTF_8);
    Map<String, byte[]> acceptedBytes = new LinkedHashMap<>();
    acceptedBytes.put("pom.xml", textBytes);
    acceptedBytes.put(JAVA_SOURCE_PATH, JAVA_SOURCE_BYTES);
    acceptedBytes.put("assets/logo.bin", MEDIA_BYTES);
    additionalVerifiedTexts.forEach(
        (path, contents) -> acceptedBytes.put(path, contents.getBytes(StandardCharsets.UTF_8)));
    SourceOrigin origin = new DirectorySourceOrigin(LOGICAL_IDENTITY, sourceRoot);
    SourcePreparationRequest request =
        new SourcePreparationRequest(
            SourcePreparationOperation.NEW,
            origin,
            null,
            List.of(),
            declaredExclusions,
            declaredExclusions,
            new SourcePreparationLimits(16, 1_000_000L),
            policyRef);
    List<SourceEntry> entries =
        new java.util.ArrayList<>(
            List.of(
                verifiedFile(
                    "pom.xml",
                    textBytes,
                    SourceEntry.Disposition.VERIFIED_TEXT,
                    observationFileKey),
                enumeratedDirectory("assets"),
                enumeratedDirectory("src"),
                enumeratedDirectory("src/main"),
                enumeratedDirectory("src/main/java"),
                enumeratedDirectory("src/main/java/fixture"),
                enumeratedDirectory("src/main/java/fixture/activation"),
                verifiedFile(
                    "assets/logo.bin",
                    MEDIA_BYTES,
                    SourceEntry.Disposition.VERIFIED_MEDIA,
                    "fixture-media"),
                verifiedFile(
                    JAVA_SOURCE_PATH,
                    JAVA_SOURCE_BYTES,
                    SourceEntry.Disposition.VERIFIED_TEXT,
                    "fixture-java")));
    java.util.Set<String> directories = new java.util.LinkedHashSet<>();
    for (Map.Entry<String, String> extra : additionalVerifiedTexts.entrySet()) {
      byte[] bytes = extra.getValue().getBytes(StandardCharsets.UTF_8);
      entries.add(
          verifiedFile(
              extra.getKey(),
              bytes,
              SourceEntry.Disposition.VERIFIED_TEXT,
              "fixture:" + extra.getKey()));
      addParentDirectories(directories, extra.getKey());
    }
    for (SourcePreparationTarget exclusion : declaredExclusions) {
      entries.add(excludedEntry(exclusion));
      addParentDirectories(directories, exclusion.relativePath());
    }
    for (String symlinkPath : skippedSymlinks) {
      entries.add(skippedSymlink(symlinkPath));
      addParentDirectories(directories, symlinkPath);
    }
    java.util.Set<String> recordedPaths =
        entries.stream()
            .map(SourceEntry::relativePath)
            .collect(java.util.stream.Collectors.toSet());
    directories.stream()
        .sorted()
        .filter(path -> !recordedPaths.contains(path))
        .map(VerifiedSourceFileActivationRangeR0Fixture::enumeratedDirectory)
        .forEach(entries::add);
    SourcePreparationResult result =
        new SourcePreparationResult(
            SourcePreparationResult.InspectionStatus.COMPLETED,
            true,
            List.copyOf(entries),
            List.of(),
            List.of(),
            List.of());
    CapturedSourcePreparation capture =
        new CapturedSourcePreparation(
            request,
            result,
            entry -> {
              byte[] bytes = acceptedBytes.get(entry.relativePath());
              if (bytes == null) {
                throw new IOException("missing fixture bytes for " + entry.relativePath());
              }
              return new ByteArrayInputStream(bytes);
            },
            toolIdentity());

    try (RunStoreHandle handle = RunStoreBootstrap.open(storeRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, JSON, policies, STORE_LIMITS);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, JSON, policies, STORE_LIMITS);
      SavedSourcePreparation saved =
          new SourcePreparationPublisher(
                  modules, steps, new PreparedSourceArchive(archiveRoot), policies)
              .publish(runId(label), capture);
      if (saved.sourceVersionReference() == null) {
        throw new IllegalStateException("fixture R0 must register a source version");
      }
      return new PublishedSource(
          policies,
          storeRoot,
          archiveRoot,
          saved.reportReference(),
          saved.sourceVersionReference().sourceVersionId().value(),
          saved.publicationFacts().sourceInventoryRef());
    }
  }

  private static SourceEntry verifiedFile(
      String path, byte[] bytes, SourceEntry.Disposition disposition, String observationFileKey) {
    Sha256Digest sha256 = digest(bytes);
    SourceOriginAttributes attributes =
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null);
    ArtifactReference blob =
        new ArtifactReference(ArtifactId.parse("source-blob:" + sha256.value()), sha256);
    ArtifactId fileId = SourceVersionCalculator.fileId(path, bytes.length, sha256, attributes);
    SourceObservation observation =
        new SourceObservation((long) bytes.length, sha256, fileId, null, observationFileKey);
    return new SourceEntry(
        path,
        SourceEntry.Kind.REGULAR_FILE,
        disposition,
        (long) bytes.length,
        sha256,
        blob,
        fileId,
        disposition == SourceEntry.Disposition.VERIFIED_TEXT ? "UTF-8" : null,
        attributes,
        new SourceEntryObservations(observation, observation),
        List.of(),
        null,
        null);
  }

  private static SourceEntry enumeratedDirectory(String path) {
    return new SourceEntry(
        path,
        SourceEntry.Kind.DIRECTORY,
        SourceEntry.Disposition.ENUMERATED_DIRECTORY,
        null,
        null,
        null,
        null,
        null,
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null),
        null,
        List.of(),
        null,
        null);
  }

  private static SourceEntry excludedEntry(SourcePreparationTarget target) {
    SourceEntry.Kind kind =
        target.kind() == SourcePreparationTarget.Kind.FILE
            ? SourceEntry.Kind.REGULAR_FILE
            : SourceEntry.Kind.DIRECTORY;
    return new SourceEntry(
        target.relativePath(),
        kind,
        SourceEntry.Disposition.EXCLUDED_BY_USER,
        null,
        null,
        null,
        null,
        null,
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null),
        null,
        List.of(),
        null,
        new SourceEntryExclusion(
            "USER_REQUEST", SourcePreparationOperation.NEW, target.relativePath()));
  }

  private static SourceEntry skippedSymlink(String path) {
    return new SourceEntry(
        path,
        SourceEntry.Kind.SYMLINK,
        SourceEntry.Disposition.SKIPPED_SYMLINK,
        null,
        null,
        null,
        null,
        null,
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null),
        null,
        List.of(),
        null,
        null);
  }

  private static void addParentDirectories(java.util.Set<String> directories, String path) {
    Path parent = Path.of(path).getParent();
    while (parent != null) {
      directories.add(parent.toString().replace('\\', '/'));
      parent = parent.getParent();
    }
  }

  private static SourcePreparationToolIdentity toolIdentity() {
    return new SourcePreparationToolIdentity(
        "verified-source-inventory/v3",
        "fixture",
        Sha256Digest.parse("d".repeat(64)),
        "fixture-vendor",
        "fixture-java",
        null);
  }

  private static AnalysisRunId runId(String label) {
    return AnalysisRunId.parse("analysis-run:" + HexFormat.of().formatHex(sha256Bytes(label)));
  }

  private static Sha256Digest digest(byte[] bytes) {
    return Sha256Digest.parse(HexFormat.of().formatHex(sha256Bytes(bytes)));
  }

  private static byte[] sha256Bytes(String value) {
    return sha256Bytes(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] sha256Bytes(byte[] bytes) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(bytes);
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  public record PublishedSource(
      CanonicalArtifactPolicyRegistry policies,
      Path storeRoot,
      Path archiveRoot,
      AnalysisStepPublicationReference reportReference,
      String sourceVersionId,
      ArtifactReference sourceInventoryRef) {

    public VerifiedSourceTextSet reopenTexts() {
      try (RunStoreHandle handle = RunStoreBootstrap.open(storeRoot)) {
        SourcePreparationReader preparations = preparations(handle);
        return new PreparedVerifiedSourceTextReader(
                preparations, new PreparedSourceArchive(archiveRoot))
            .reopen(new VerifiedSourceInventoryReference(reportReference));
      }
    }

    public VerifiedSourceFileActivationRange reopenRange(VerifiedSourceTextSet texts) {
      return reopenRange(reportReference, texts);
    }

    public VerifiedSourceFileActivationRange reopenRange(
        AnalysisStepPublicationReference frozenReport, VerifiedSourceTextSet texts) {
      try (RunStoreHandle handle = RunStoreBootstrap.open(storeRoot)) {
        return new PreparedVerifiedSourceFileActivationRangeReader(preparations(handle))
            .reopen(new VerifiedSourceInventoryReference(frozenReport), texts);
      }
    }

    private SourcePreparationReader preparations(RunStoreHandle handle) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, JSON, policies, STORE_LIMITS);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, JSON, policies, STORE_LIMITS);
      return new SourcePreparationReader(modules, steps, new PreparedSourceArchive(archiveRoot));
    }
  }
}
