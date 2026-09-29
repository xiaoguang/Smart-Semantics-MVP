package org.sourceanalysis.app.capture.preparation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.inventory.DirectorySourceOrigin;
import org.sourceanalysis.app.analysis.inventory.GitCommitSourceOrigin;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.analysis.inventory.SourceEntry;
import org.sourceanalysis.app.analysis.inventory.SourceEntryExclusion;
import org.sourceanalysis.app.analysis.inventory.SourceEntryInheritance;
import org.sourceanalysis.app.analysis.inventory.SourceEntryObservations;
import org.sourceanalysis.app.analysis.inventory.SourceIssue;
import org.sourceanalysis.app.analysis.inventory.SourceObservation;
import org.sourceanalysis.app.analysis.inventory.SourceOrigin;
import org.sourceanalysis.app.analysis.inventory.SourceOriginAttributes;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationAssessment;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationLimits;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationOperation;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationRequest;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationResult;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationTarget;
import org.sourceanalysis.app.analysis.inventory.SourceVersionCalculator;
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
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.Sha256Digest;

/**
 * Private, frozen v2 archive for accepted source bytes and the private preparation facts that
 * public four-file publication deliberately omits.
 */
public final class PreparedSourceArchive {

  private static final String ARCHIVE_SCHEMA = "prepared-source-archive-v2";
  private static final String PUBLICATION_LINKS_SCHEMA = "prepared-source-publication-links-v1";
  private static final int COPY_BUFFER_BYTES = 16 * 1024;
  private static final Comparator<String> UTF8_ORDER = PreparedSourceArchive::compareUtf8;

  private final Path root;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  public PreparedSourceArchive(Path root) {
    this.root =
        Objects.requireNonNull(root, "private prepared-source archive root")
            .toAbsolutePath()
            .normalize();
  }

  /** Derives the saved preparation controls from actual typed preparation facts. */
  public ArtifactControls controlsFor(
      CapturedSourcePreparation capture, ArtifactPolicyRegistryReference policyRegistryReference) {
    Objects.requireNonNull(capture, "captured source preparation");
    Objects.requireNonNull(policyRegistryReference, "artifact policy registry reference");
    ArtifactReference profile =
        reference("source-preparation-profile", profileNode(capture.request()));
    ArtifactReference toolchain =
        reference("source-preparation-toolchain", toolchainNode(capture.toolIdentity()));
    ArtifactReference schemas = reference("source-preparation-schema-bundle", schemaBundleNode());
    return new ArtifactControls(
        toolchain.sha256(), profile.sha256(), schemas.sha256(), null, policyRegistryReference);
  }

  /**
   * Derives the exact v3 run-input references that this archive will persist for a preparation.
   *
   * <p>This operation reads no source bytes and writes no archive state. Composition uses it before
   * queueing a run so the persisted request names the same private controls that {@link #save}
   * later verifies.
   */
  public PreparationInputReferences preparationInputs(
      SourcePreparationRequest request, SourcePreparationToolIdentity toolIdentity) {
    Objects.requireNonNull(request, "source preparation request");
    Objects.requireNonNull(toolIdentity, "source preparation tool identity");
    return new PreparationInputReferences(
        reference("source-preparation-request", requestNode(request)),
        request.policyRef(),
        reference("source-preparation-schema-bundle", schemaBundleNode()),
        reference("source-preparation-resource-budget", budgetNode(request.limits())),
        reference("source-preparation-profile", profileNode(request)),
        reference("source-preparation-toolchain", toolchainNode(toolIdentity)));
  }

  /**
   * Saves each accepted stream exactly once, verifies it, then installs a private archive marker.
   */
  public StoredCapture save(
      CapturedSourcePreparation capture,
      SourcePreparationAssessment assessment,
      ArtifactControls controls)
      throws IOException {
    Objects.requireNonNull(capture, "captured source preparation");
    Objects.requireNonNull(assessment, "source-preparation assessment");
    Objects.requireNonNull(controls, "artifact controls");
    ArtifactId sourceVersionId =
        SourceVersionCalculator.sourceVersionId(capture.request(), capture.result());
    Path archiveDirectory = root.resolve(sourceVersionId.value());
    if (Files.exists(archiveDirectory, LinkOption.NOFOLLOW_LINKS)) {
      return reopen(sourceVersionId);
    }
    Files.createDirectories(root);
    Path staging = Files.createTempDirectory(root, "prepared-source-staging-");
    try {
      Path blobs = staging.resolve("blobs");
      Files.createDirectory(blobs);
      for (SourceEntry entry : capture.result().entries()) {
        if (!accepted(entry)) {
          continue;
        }
        writeVerifiedBlob(
            blobs.resolve(entry.blobRef().sha256().value()), entry, capture.acceptedBytes());
      }
      StoredCapture stored = metadata(capture, assessment, controls, sourceVersionId);
      writePrivateFacts(staging, capture, stored);
      writeCanonical(staging.resolve("prepared-source-archive.json"), archiveNode(stored));
      try {
        Files.move(staging, archiveDirectory, StandardCopyOption.ATOMIC_MOVE);
      } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
        Files.move(staging, archiveDirectory);
      }
      return stored;
    } finally {
      if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
        deleteTree(staging);
      }
    }
  }

  /**
   * Reopens the private marker and verifies the identity before it returns a frozen capture view.
   */
  public StoredCapture reopen(ArtifactId sourceVersionId) throws IOException {
    StoredCapture stored = reopenMetadata(sourceVersionId);
    verifyFrozenBlobs(root.resolve(sourceVersionId.value()), stored.result());
    return stored;
  }

  private StoredCapture reopenMetadata(ArtifactId sourceVersionId) throws IOException {
    if (sourceVersionId == null || !sourceVersionId.value().startsWith("snapshot:")) {
      throw new IOException("prepared source version is invalid");
    }
    Path archiveDirectory = root.resolve(sourceVersionId.value());
    Path manifest = archiveDirectory.resolve("prepared-source-archive.json");
    if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(manifest)) {
      throw new IOException("prepared source archive marker is missing");
    }
    try {
      StoredCapture stored =
          archiveFromNode(
              json.parseCanonical(
                  org.sourceanalysis.app.artifact.ImmutableBytes.copyOf(
                      Files.readAllBytes(manifest))));
      if (!stored.sourceVersionId().equals(sourceVersionId)
          || !SourceVersionCalculator.sourceVersionId(stored.request(), stored.result())
              .equals(sourceVersionId)) {
        throw new IOException("prepared source archive identity does not match its facts");
      }
      verifyPrivateFacts(archiveDirectory, stored);
      return stored;
    } catch (IllegalArgumentException invalid) {
      throw new IOException("prepared source archive is invalid", invalid);
    }
  }

  /**
   * Atomically binds the two private prerequisite module receipts to a frozen source capture. These
   * links are deliberately private: the public v3 M3 payload set remains exactly four files, while
   * a fresh reader can still reopen the complete M1-to-M3 publication chain.
   */
  public void savePublicationLinks(
      ArtifactId sourceVersionId,
      AnalysisStepPublicationAddress reportAddress,
      ModulePublicationReference requestAdmission,
      ModulePublicationReference sourceIndex)
      throws IOException {
    reopen(sourceVersionId);
    PublicationLinks expected = new PublicationLinks(reportAddress, requestAdmission, sourceIndex);
    requirePublicationLinks(expected);
    Path archiveDirectory = root.resolve(sourceVersionId.value());
    Path target = publicationLinksPathForSave(archiveDirectory, reportAddress);
    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
      if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
        throw new IOException("prepared source publication links are invalid");
      }
      if (!reopenPublicationLinks(sourceVersionId, reportAddress).equals(expected)) {
        throw new IOException("prepared source publication links changed");
      }
      return;
    }

    Path staging = Files.createTempFile(archiveDirectory, "publication-links-", ".tmp");
    try {
      writeCanonical(staging, publicationLinksNode(sourceVersionId, expected));
      try {
        Files.createLink(target, staging);
      } catch (java.nio.file.FileAlreadyExistsException raced) {
        if (!reopenPublicationLinks(sourceVersionId, reportAddress).equals(expected)) {
          throw new IOException("prepared source publication links changed", raced);
        }
      } catch (UnsupportedOperationException unsupported) {
        throw new IOException(
            "prepared source publication links require atomic create-new links", unsupported);
      } catch (IOException cannotCreateAtomically) {
        throw new IOException(
            "prepared source publication links could not be installed with atomic create-new semantics",
            cannotCreateAtomically);
      }
    } finally {
      Files.deleteIfExists(staging);
    }
  }

  /** Reopens the private prerequisite links only after verifying the frozen capture itself. */
  public PublicationLinks reopenPublicationLinks(
      ArtifactId sourceVersionId, AnalysisStepPublicationAddress reportAddress) throws IOException {
    reopen(sourceVersionId);
    if (reportAddress == null
        || reportAddress.analysisStepKey() != AnalysisStepKey.VERIFIED_SOURCE_INVENTORY) {
      throw new IOException("prepared source publication address is invalid");
    }
    Path links = publicationLinksPath(root.resolve(sourceVersionId.value()), reportAddress);
    if (!Files.isRegularFile(links, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(links)) {
      throw new IOException("prepared source publication links are missing");
    }
    try {
      PublicationLinks value =
          publicationLinksFromNode(
              sourceVersionId,
              reportAddress,
              json.parseCanonical(
                  org.sourceanalysis.app.artifact.ImmutableBytes.copyOf(
                      Files.readAllBytes(links))));
      requirePublicationLinks(value);
      return value;
    } catch (IllegalArgumentException invalid) {
      throw new IOException("prepared source publication links are invalid", invalid);
    }
  }

  /** Opens one frozen accepted blob after matching its declared private content identity. */
  public InputStream open(ArtifactId sourceVersionId, SourceEntry entry) throws IOException {
    if (!accepted(entry)) {
      throw new IOException("source entry has no accepted bytes");
    }
    StoredCapture stored = reopenMetadata(sourceVersionId);
    SourceEntry archived =
        stored.result().entries().stream()
            .filter(candidate -> candidate.relativePath().equals(entry.relativePath()))
            .findFirst()
            .orElseThrow(() -> new IOException("prepared source entry is missing"));
    if (!archived.equals(entry)) {
      throw new IOException("prepared source entry does not match the archive");
    }
    Path blob =
        root.resolve(sourceVersionId.value())
            .resolve("blobs")
            .resolve(entry.blobRef().sha256().value());
    if (!Files.isRegularFile(blob, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(blob)) {
      throw new IOException("prepared source blob is missing");
    }
    byte[] bytes = Files.readAllBytes(blob);
    verifyBlob(entry, bytes);
    return new java.io.ByteArrayInputStream(bytes);
  }

  private StoredCapture metadata(
      CapturedSourcePreparation capture,
      SourcePreparationAssessment assessment,
      ArtifactControls controls,
      ArtifactId sourceVersionId) {
    List<StoredFact> facts = factsFor(capture, sourceVersionId);
    return new StoredCapture(
        sourceVersionId,
        capture.request(),
        capture.result(),
        assessment,
        factReference(facts, "preparation-request.json"),
        factReference(facts, "source-registration.json"),
        factReference(facts, "source-capture-receipt.json"),
        factReference(facts, "source-snapshot-manifest.json"),
        factReference(facts, "source-preparation-profile.json"),
        factReference(facts, "source-preparation-toolchain.json"),
        factReference(facts, "source-preparation-schema-bundle.json"),
        factReference(facts, "source-preparation-resource-budget.json"),
        factReference(facts, "source-preparation-capability.json"),
        controls);
  }

  private List<StoredFact> factsFor(CapturedSourcePreparation capture, ArtifactId sourceVersionId) {
    return List.of(
        fact(
            "preparation-request.json",
            "source-preparation-request",
            requestNode(capture.request())),
        fact(
            "source-registration.json",
            "source-registration",
            registrationNode(capture, sourceVersionId)),
        fact(
            "source-capture-receipt.json",
            "source-capture-receipt",
            captureReceiptNode(capture, sourceVersionId)),
        fact(
            "source-snapshot-manifest.json",
            "source-snapshot-manifest",
            inventoryNode(capture.result())),
        fact(
            "source-preparation-profile.json",
            "source-preparation-profile",
            profileNode(capture.request())),
        fact(
            "source-preparation-toolchain.json",
            "source-preparation-toolchain",
            toolchainNode(capture.toolIdentity())),
        fact(
            "source-preparation-schema-bundle.json",
            "source-preparation-schema-bundle",
            schemaBundleNode()),
        fact(
            "source-preparation-resource-budget.json",
            "source-preparation-resource-budget",
            budgetNode(capture.request().limits())),
        fact(
            "source-preparation-capability.json",
            "source-preparation-capability",
            capabilityNode()));
  }

  private StoredFact fact(String fileName, String prefix, ObjectNode body) {
    return new StoredFact(fileName, prefix, reference(prefix, body), body);
  }

  private static ArtifactReference factReference(List<StoredFact> facts, String fileName) {
    return facts.stream()
        .filter(fact -> fact.fileName().equals(fileName))
        .map(StoredFact::reference)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("prepared source fact is missing"));
  }

  private void writePrivateFacts(
      Path staging, CapturedSourcePreparation capture, StoredCapture stored) throws IOException {
    Path factsDirectory = staging.resolve("facts");
    Files.createDirectory(factsDirectory);
    for (StoredFact fact : factsFor(capture, stored.sourceVersionId())) {
      if (!factReference(stored, fact.fileName()).equals(fact.reference())) {
        throw new IOException("prepared source fact reference changed before storage");
      }
      writeCanonical(factsDirectory.resolve(fact.fileName()), fact.body());
    }
  }

  private void verifyPrivateFacts(Path archiveDirectory, StoredCapture stored) throws IOException {
    Path factsDirectory = archiveDirectory.resolve("facts");
    if (!Files.isDirectory(factsDirectory, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(factsDirectory)) {
      throw new IOException("prepared source facts are missing");
    }
    verifyPrivateFact(
        factsDirectory,
        "preparation-request.json",
        "source-preparation-request",
        stored.preparationRequestRef());
    verifyPrivateFact(
        factsDirectory,
        "source-registration.json",
        "source-registration",
        stored.sourceRegistrationRef());
    verifyPrivateFact(
        factsDirectory,
        "source-capture-receipt.json",
        "source-capture-receipt",
        stored.captureReceiptRef());
    verifyPrivateFact(
        factsDirectory,
        "source-snapshot-manifest.json",
        "source-snapshot-manifest",
        stored.snapshotManifestRef());
    verifyPrivateFact(
        factsDirectory,
        "source-preparation-profile.json",
        "source-preparation-profile",
        stored.preparationProfileRef());
    verifyPrivateFact(
        factsDirectory,
        "source-preparation-toolchain.json",
        "source-preparation-toolchain",
        stored.preparationToolchainRef());
    verifyPrivateFact(
        factsDirectory,
        "source-preparation-schema-bundle.json",
        "source-preparation-schema-bundle",
        stored.schemaBundleRef());
    verifyPrivateFact(
        factsDirectory,
        "source-preparation-resource-budget.json",
        "source-preparation-resource-budget",
        stored.resourceBudgetRef());
    verifyPrivateFact(
        factsDirectory,
        "source-preparation-capability.json",
        "source-preparation-capability",
        stored.capabilityProfileRef());
    if (!stored.controls().profileSha256().equals(stored.preparationProfileRef().sha256())
        || !stored.controls().toolchainSha256().equals(stored.preparationToolchainRef().sha256())
        || !stored.controls().schemaBundleSha256().equals(stored.schemaBundleRef().sha256())) {
      throw new IOException("prepared source controls do not match frozen facts");
    }
  }

  private void verifyPrivateFact(
      Path factsDirectory, String fileName, String prefix, ArtifactReference expected)
      throws IOException {
    Path fact = factsDirectory.resolve(fileName);
    if (!Files.isRegularFile(fact, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(fact)) {
      throw new IOException("prepared source fact is missing");
    }
    byte[] bytes = Files.readAllBytes(fact);
    json.parseCanonical(org.sourceanalysis.app.artifact.ImmutableBytes.copyOf(bytes));
    ArtifactReference actual =
        new ArtifactReference(
            ArtifactId.parse(prefix + ":" + sha256(bytes)), Sha256Digest.parse(sha256(bytes)));
    if (!expected.equals(actual)) {
      throw new IOException("prepared source fact identity does not match its bytes");
    }
  }

  private static void verifyFrozenBlobs(Path archiveDirectory, SourcePreparationResult result)
      throws IOException {
    for (SourceEntry entry : result.entries()) {
      if (!accepted(entry)) {
        continue;
      }
      Path blob = archiveDirectory.resolve("blobs").resolve(entry.blobRef().sha256().value());
      if (!Files.isRegularFile(blob, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(blob)) {
        throw new IOException("prepared source blob is missing");
      }
      try (InputStream input = Files.newInputStream(blob)) {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        long length = 0L;
        for (int count; (count = input.read(buffer)) >= 0; ) {
          if (count == 0) {
            continue;
          }
          length = Math.addExact(length, count);
          digest.update(buffer, 0, count);
        }
        verifyBlob(entry, length, java.util.HexFormat.of().formatHex(digest.digest()));
      }
    }
  }

  private static ArtifactReference factReference(StoredCapture stored, String fileName) {
    return switch (fileName) {
      case "preparation-request.json" -> stored.preparationRequestRef();
      case "source-registration.json" -> stored.sourceRegistrationRef();
      case "source-capture-receipt.json" -> stored.captureReceiptRef();
      case "source-snapshot-manifest.json" -> stored.snapshotManifestRef();
      case "source-preparation-profile.json" -> stored.preparationProfileRef();
      case "source-preparation-toolchain.json" -> stored.preparationToolchainRef();
      case "source-preparation-schema-bundle.json" -> stored.schemaBundleRef();
      case "source-preparation-resource-budget.json" -> stored.resourceBudgetRef();
      case "source-preparation-capability.json" -> stored.capabilityProfileRef();
      default -> throw new IllegalArgumentException("unknown prepared source fact");
    };
  }

  private void writeVerifiedBlob(
      Path destination, SourceEntry entry, SourcePreparationBlobReader acceptedBytes)
      throws IOException {
    try (InputStream input = acceptedBytes.open(entry);
        OutputStream output = Files.newOutputStream(destination)) {
      if (input == null) {
        throw new IOException("accepted source stream is missing");
      }
      MessageDigest digest = sha256Digest();
      byte[] buffer = new byte[COPY_BUFFER_BYTES];
      long length = 0L;
      for (int count; (count = input.read(buffer)) >= 0; ) {
        if (count == 0) {
          continue;
        }
        length = Math.addExact(length, count);
        output.write(buffer, 0, count);
        digest.update(buffer, 0, count);
      }
      verifyBlob(entry, length, java.util.HexFormat.of().formatHex(digest.digest()));
    }
  }

  private static void verifyBlob(SourceEntry entry, byte[] bytes) throws IOException {
    verifyBlob(entry, bytes.length, sha256(bytes));
  }

  private static void verifyBlob(SourceEntry entry, long length, String digest) throws IOException {
    if (length != entry.sizeBytes()
        || !digest.equals(entry.sha256().value())
        || !entry.blobRef().sha256().value().equals(entry.sha256().value())) {
      throw new IOException("accepted source bytes do not match the entry identity");
    }
  }

  private static boolean accepted(SourceEntry entry) {
    return entry.disposition() == SourceEntry.Disposition.VERIFIED_TEXT
        || entry.disposition() == SourceEntry.Disposition.VERIFIED_MEDIA;
  }

  private void writeCanonical(Path path, ObjectNode node) throws IOException {
    Files.write(path, json.encodeCanonical(node).copyToByteArray());
  }

  private static ObjectNode publicationLinksNode(
      ArtifactId sourceVersionId, PublicationLinks links) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("schemaVersion", PUBLICATION_LINKS_SCHEMA);
    node.put("sourceVersionId", sourceVersionId.value());
    node.set("reportAddress", analysisStepAddressNode(links.reportAddress()));
    node.set("requestAdmission", moduleReferenceNode(links.requestAdmission()));
    node.set("sourceIndex", moduleReferenceNode(links.sourceIndex()));
    return node;
  }

  private static PublicationLinks publicationLinksFromNode(
      ArtifactId sourceVersionId, AnalysisStepPublicationAddress reportAddress, JsonNode value) {
    ObjectNode node = object(value);
    if (!fields(node)
            .equals(
                Set.of(
                    "schemaVersion",
                    "sourceVersionId",
                    "reportAddress",
                    "requestAdmission",
                    "sourceIndex"))
        || !PUBLICATION_LINKS_SCHEMA.equals(text(node, "schemaVersion"))
        || !sourceVersionId.value().equals(text(node, "sourceVersionId"))
        || !reportAddress.equals(analysisStepAddressFromNode(object(node.get("reportAddress"))))) {
      throw new IllegalArgumentException(
          "prepared source publication links do not match the archive");
    }
    return new PublicationLinks(
        reportAddress,
        moduleReferenceFromNode(object(node.get("requestAdmission"))),
        moduleReferenceFromNode(object(node.get("sourceIndex"))));
  }

  private static void requirePublicationLinks(PublicationLinks links) {
    if (links == null
        || links.reportAddress() == null
        || links.reportAddress().analysisStepKey() != AnalysisStepKey.VERIFIED_SOURCE_INVENTORY
        || !(links.requestAdmission().address() instanceof AnalysisStepModuleAddress request)
        || !(links.sourceIndex().address() instanceof AnalysisStepModuleAddress index)
        || request.analysisStepKey() != AnalysisStepKey.VERIFIED_SOURCE_INVENTORY
        || request.moduleNumber() != 1
        || !"request-admission".equals(request.moduleKey())
        || index.analysisStepKey() != AnalysisStepKey.VERIFIED_SOURCE_INVENTORY
        || index.moduleNumber() != 2
        || !"source-index".equals(index.moduleKey())
        || !request.runId().equals(index.runId())
        || !request.runId().equals(links.reportAddress().runId())) {
      throw new IllegalArgumentException("prepared source publication links are not M1 and M2");
    }
  }

  private static Path publicationLinksPath(
      Path archiveDirectory, AnalysisStepPublicationAddress reportAddress) throws IOException {
    Path linksDirectory = archiveDirectory.resolve("publication-links");
    if (Files.exists(linksDirectory, LinkOption.NOFOLLOW_LINKS)) {
      if (!Files.isDirectory(linksDirectory, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(linksDirectory)) {
        throw new IOException("prepared source publication links directory is invalid");
      }
    }
    return linksDirectory.resolve(publicationLinksFileName(reportAddress));
  }

  private static Path publicationLinksPathForSave(
      Path archiveDirectory, AnalysisStepPublicationAddress reportAddress) throws IOException {
    Path linksDirectory = archiveDirectory.resolve("publication-links");
    if (!Files.exists(linksDirectory, LinkOption.NOFOLLOW_LINKS)) {
      try {
        Files.createDirectory(linksDirectory);
      } catch (java.nio.file.FileAlreadyExistsException raced) {
        if (!Files.isDirectory(linksDirectory, LinkOption.NOFOLLOW_LINKS)
            || Files.isSymbolicLink(linksDirectory)) {
          throw new IOException("prepared source publication links directory is invalid", raced);
        }
      }
    }
    return publicationLinksPath(archiveDirectory, reportAddress);
  }

  private static String publicationLinksFileName(AnalysisStepPublicationAddress reportAddress) {
    return reportAddress.runId().value()
        + "-"
        + reportAddress.analysisStepKey().wireValue()
        + ".json";
  }

  private static ObjectNode analysisStepAddressNode(AnalysisStepPublicationAddress address) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("runId", address.runId().value());
    node.put("analysisStepKey", address.analysisStepKey().wireValue());
    return node;
  }

  private static AnalysisStepPublicationAddress analysisStepAddressFromNode(ObjectNode node) {
    if (!fields(node).equals(Set.of("runId", "analysisStepKey"))) {
      throw new IllegalArgumentException("prepared source publication report address is invalid");
    }
    return new AnalysisStepPublicationAddress(
        AnalysisRunId.parse(text(node, "runId")),
        AnalysisStepKey.parse(text(node, "analysisStepKey")));
  }

  private static ObjectNode moduleReferenceNode(ModulePublicationReference reference) {
    if (!(reference.address() instanceof AnalysisStepModuleAddress address)) {
      throw new IllegalArgumentException("prepared source publication link address is invalid");
    }
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    ObjectNode addressNode = node.putObject("address");
    addressNode.put("kind", "ANALYSIS_STEP");
    addressNode.put("runId", address.runId().value());
    addressNode.put("analysisStepKey", address.analysisStepKey().wireValue());
    addressNode.put("moduleNumber", address.moduleNumber());
    addressNode.put("moduleKey", address.moduleKey());
    node.put("moduleArtifactRoot", reference.moduleArtifactRoot().value());
    node.put("moduleReceiptId", reference.moduleReceiptId().value());
    node.put("moduleReceiptSha256", reference.moduleReceiptSha256().value());
    return node;
  }

  private static ModulePublicationReference moduleReferenceFromNode(ObjectNode node) {
    if (!fields(node)
        .equals(
            Set.of("address", "moduleArtifactRoot", "moduleReceiptId", "moduleReceiptSha256"))) {
      throw new IllegalArgumentException("prepared source publication link is invalid");
    }
    return new ModulePublicationReference(
        moduleAddressFromNode(object(node.get("address"))),
        ModuleArtifactRoot.parse(text(node, "moduleArtifactRoot")),
        ModuleReceiptId.parse(text(node, "moduleReceiptId")),
        Sha256Digest.parse(text(node, "moduleReceiptSha256")));
  }

  private static AnalysisStepModuleAddress moduleAddressFromNode(ObjectNode node) {
    if (!fields(node)
            .equals(Set.of("kind", "runId", "analysisStepKey", "moduleNumber", "moduleKey"))
        || !"ANALYSIS_STEP".equals(text(node, "kind"))) {
      throw new IllegalArgumentException("prepared source publication link address is invalid");
    }
    JsonNode number = node.get("moduleNumber");
    if (number == null || !number.isInt()) {
      throw new IllegalArgumentException(
          "prepared source publication link module number is invalid");
    }
    return new AnalysisStepModuleAddress(
        AnalysisRunId.parse(text(node, "runId")),
        AnalysisStepKey.parse(text(node, "analysisStepKey")),
        number.intValue(),
        text(node, "moduleKey"));
  }

  private ObjectNode archiveNode(StoredCapture stored) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("schemaVersion", ARCHIVE_SCHEMA);
    node.put("sourceVersionId", stored.sourceVersionId().value());
    node.set("request", requestNode(stored.request()));
    node.set("result", resultNode(stored.result()));
    node.put("readiness", stored.assessment().readiness().name());
    node.set("preparationRequestRef", referenceNode(stored.preparationRequestRef()));
    node.set("sourceRegistrationRef", referenceNode(stored.sourceRegistrationRef()));
    node.set("captureReceiptRef", referenceNode(stored.captureReceiptRef()));
    node.set("snapshotManifestRef", referenceNode(stored.snapshotManifestRef()));
    node.set("preparationProfileRef", referenceNode(stored.preparationProfileRef()));
    node.set("preparationToolchainRef", referenceNode(stored.preparationToolchainRef()));
    node.set("schemaBundleRef", referenceNode(stored.schemaBundleRef()));
    node.set("resourceBudgetRef", referenceNode(stored.resourceBudgetRef()));
    node.set("capabilityProfileRef", referenceNode(stored.capabilityProfileRef()));
    node.set("controls", controlsNode(stored.controls()));
    return node;
  }

  private StoredCapture archiveFromNode(JsonNode value) {
    ObjectNode node = object(value);
    requireText(node, "schemaVersion", ARCHIVE_SCHEMA);
    ArtifactId sourceVersionId = ArtifactId.parse(text(node, "sourceVersionId"));
    SourcePreparationRequest request = requestFromNode(object(node.get("request")));
    SourcePreparationResult result = resultFromNode(object(node.get("result")));
    SourcePreparationAssessment assessment =
        org.sourceanalysis.app.analysis.inventory.SourcePreparationReadinessEvaluator.assess(
            result);
    if (!assessment.readiness().name().equals(text(node, "readiness"))) {
      throw new IllegalArgumentException("saved preparation readiness changed");
    }
    return new StoredCapture(
        sourceVersionId,
        request,
        result,
        assessment,
        reference(node, "preparationRequestRef"),
        reference(node, "sourceRegistrationRef"),
        reference(node, "captureReceiptRef"),
        reference(node, "snapshotManifestRef"),
        reference(node, "preparationProfileRef"),
        reference(node, "preparationToolchainRef"),
        reference(node, "schemaBundleRef"),
        reference(node, "resourceBudgetRef"),
        reference(node, "capabilityProfileRef"),
        controlsFromNode(object(node.get("controls"))));
  }

  private static ObjectNode requestNode(SourcePreparationRequest request) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("operation", request.operation().name());
    node.set("origin", originNode(request.origin(), true));
    if (request.basePreparation() == null) {
      node.putNull("basePreparation");
    } else {
      node.set("basePreparation", preparedReferenceNode(request.basePreparation()));
    }
    node.set("targets", targetsNode(request.targets()));
    node.set("declaredExclusions", targetsNode(request.declaredExclusions()));
    node.set("effectiveExclusions", targetsNode(request.effectiveExclusions()));
    node.set("limits", limitsNode(request.limits()));
    node.set("policyRef", referenceNode(request.policyRef()));
    return node;
  }

  private static SourcePreparationRequest requestFromNode(ObjectNode node) {
    SourcePreparationOperation operation =
        SourcePreparationOperation.valueOf(text(node, "operation"));
    SourceOrigin origin = originFromNode(object(node.get("origin")), true);
    JsonNode baseNode = node.get("basePreparation");
    PreparedSourceReference base =
        baseNode == null || baseNode.isNull() ? null : preparedReferenceFromNode(object(baseNode));
    return new SourcePreparationRequest(
        operation,
        origin,
        base,
        targetsFromNode(array(node, "targets")),
        targetsFromNode(array(node, "declaredExclusions")),
        targetsFromNode(array(node, "effectiveExclusions")),
        limitsFromNode(object(node.get("limits"))),
        reference(node, "policyRef"));
  }

  private static ObjectNode resultNode(SourcePreparationResult result) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("inspectionStatus", result.inspectionStatus().name());
    node.put("enumerationComplete", result.enumerationComplete());
    ArrayNode entries = node.putArray("entries");
    result.entries().stream()
        .sorted(Comparator.comparing(SourceEntry::relativePath, UTF8_ORDER))
        .forEach(entry -> entries.add(entryNode(entry)));
    ArrayNode issues = node.putArray("issues");
    result.issues().stream()
        .sorted(Comparator.comparing(SourceIssue::issueId, UTF8_ORDER))
        .forEach(issue -> issues.add(issueNode(issue)));
    ArrayNode unknown = node.putArray("unknownSubtrees");
    result.unknownSubtrees().stream().sorted(UTF8_ORDER).forEach(unknown::add);
    node.set("unmatchedExclusions", targetsNode(result.unmatchedExclusions()));
    return node;
  }

  private static SourcePreparationResult resultFromNode(ObjectNode node) {
    List<SourceEntry> entries = new ArrayList<>();
    for (JsonNode entry : array(node, "entries")) {
      entries.add(entryFromNode(object(entry)));
    }
    List<SourceIssue> issues = new ArrayList<>();
    for (JsonNode issue : array(node, "issues")) {
      issues.add(issueFromNode(object(issue)));
    }
    List<String> unknown = new ArrayList<>();
    for (JsonNode value : array(node, "unknownSubtrees")) {
      if (!value.isTextual()) {
        throw new IllegalArgumentException("unknown subtree must be text");
      }
      unknown.add(value.textValue());
    }
    return new SourcePreparationResult(
        SourcePreparationResult.InspectionStatus.valueOf(text(node, "inspectionStatus")),
        bool(node, "enumerationComplete"),
        entries,
        issues,
        unknown,
        targetsFromNode(array(node, "unmatchedExclusions")));
  }

  private static ObjectNode entryNode(SourceEntry entry) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("relativePath", entry.relativePath());
    node.put("entryKind", entry.entryKind().name());
    node.put("disposition", entry.disposition().name());
    nullableLong(node, "sizeBytes", entry.sizeBytes());
    nullableText(node, "sha256", entry.sha256() == null ? null : entry.sha256().value());
    if (entry.blobRef() == null) node.putNull("blobRef");
    else node.set("blobRef", referenceNode(entry.blobRef()));
    nullableText(node, "fileId", entry.fileId() == null ? null : entry.fileId().value());
    nullableText(node, "textEncoding", entry.textEncoding());
    node.set("originAttributes", originAttributesNode(entry.originAttributes()));
    if (entry.observations() == null) node.putNull("observations");
    else node.set("observations", observationsNode(entry.observations()));
    ArrayNode issueIds = node.putArray("issueIds");
    entry.issueIds().forEach(issueIds::add);
    if (entry.inheritedFrom() == null) node.putNull("inheritedFrom");
    else {
      ObjectNode inherited = node.putObject("inheritedFrom");
      inherited.put("baseSourceVersion", entry.inheritedFrom().baseSourceVersion().value());
      nullableText(
          inherited,
          "fileId",
          entry.inheritedFrom().fileId() == null ? null : entry.inheritedFrom().fileId().value());
    }
    if (entry.exclusion() == null) node.putNull("exclusion");
    else {
      ObjectNode exclusion = node.putObject("exclusion");
      exclusion.put("category", entry.exclusion().category());
      exclusion.put("decision", entry.exclusion().decision().name());
      exclusion.put("coveredPath", entry.exclusion().coveredPath());
    }
    return node;
  }

  private static SourceEntry entryFromNode(ObjectNode node) {
    Long sizeBytes = nullableLong(node, "sizeBytes");
    String sha = nullableText(node, "sha256");
    JsonNode blob = node.get("blobRef");
    JsonNode inherited = node.get("inheritedFrom");
    JsonNode exclusion = node.get("exclusion");
    List<String> issueIds = new ArrayList<>();
    for (JsonNode issueId : array(node, "issueIds")) issueIds.add(issueId.textValue());
    return new SourceEntry(
        text(node, "relativePath"),
        SourceEntry.Kind.valueOf(text(node, "entryKind")),
        SourceEntry.Disposition.valueOf(text(node, "disposition")),
        sizeBytes,
        sha == null ? null : Sha256Digest.parse(sha),
        blob == null || blob.isNull() ? null : reference(object(blob)),
        nullableText(node, "fileId") == null
            ? null
            : ArtifactId.parse(nullableText(node, "fileId")),
        nullableText(node, "textEncoding"),
        originAttributesFromNode(object(node.get("originAttributes"))),
        node.get("observations") == null || node.get("observations").isNull()
            ? null
            : observationsFromNode(object(node.get("observations"))),
        issueIds,
        inherited == null || inherited.isNull()
            ? null
            : new SourceEntryInheritance(
                ArtifactId.parse(text(object(inherited), "baseSourceVersion")),
                nullableText(object(inherited), "fileId") == null
                    ? null
                    : ArtifactId.parse(nullableText(object(inherited), "fileId"))),
        exclusion == null || exclusion.isNull()
            ? null
            : new SourceEntryExclusion(
                text(object(exclusion), "category"),
                SourcePreparationOperation.valueOf(text(object(exclusion), "decision")),
                text(object(exclusion), "coveredPath")));
  }

  private static ObjectNode issueNode(SourceIssue issue) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("issueId", issue.issueId());
    node.put("code", issue.code().name());
    node.put("category", issue.category().name());
    node.put("scope", issue.scope().name());
    nullableText(node, "relativePath", issue.relativePath());
    node.put("operation", issue.operation().name());
    node.put("message", issue.message());
    if (issue.expected() == null) node.putNull("expected");
    else node.set("expected", observationNode(issue.expected()));
    if (issue.observed() == null) node.putNull("observed");
    else node.set("observed", observationNode(issue.observed()));
    node.put("resolution", issue.resolution().name());
    ArrayNode actions = node.putArray("allowedActions");
    issue.allowedActions().stream().map(Enum::name).sorted(UTF8_ORDER).forEach(actions::add);
    if (issue.diagnosticRef() == null) node.putNull("diagnosticRef");
    else node.set("diagnosticRef", referenceNode(issue.diagnosticRef()));
    return node;
  }

  private static SourceIssue issueFromNode(ObjectNode node) {
    List<SourceIssue.AllowedAction> actions = new ArrayList<>();
    for (JsonNode action : array(node, "allowedActions"))
      actions.add(SourceIssue.AllowedAction.valueOf(action.textValue()));
    return new SourceIssue(
        text(node, "issueId"),
        SourceIssue.Code.valueOf(text(node, "code")),
        SourceIssue.Category.valueOf(text(node, "category")),
        SourceIssue.Scope.valueOf(text(node, "scope")),
        nullableText(node, "relativePath"),
        SourceIssue.Operation.valueOf(text(node, "operation")),
        text(node, "message"),
        node.get("expected") == null || node.get("expected").isNull()
            ? null
            : observationFromNode(object(node.get("expected"))),
        node.get("observed") == null || node.get("observed").isNull()
            ? null
            : observationFromNode(object(node.get("observed"))),
        SourceIssue.Resolution.valueOf(text(node, "resolution")),
        java.util.Set.copyOf(actions),
        node.get("diagnosticRef") == null || node.get("diagnosticRef").isNull()
            ? null
            : reference(object(node.get("diagnosticRef"))));
  }

  private static ObjectNode originNode(SourceOrigin origin, boolean privateRoot) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("kind", origin.kind().name());
    node.put("logicalIdentity", origin.logicalIdentity());
    if (privateRoot) node.put("canonicalRoot", origin.canonicalRoot().toString());
    if (origin instanceof GitCommitSourceOrigin git) node.put("commitId", git.commitId());
    else node.putNull("commitId");
    return node;
  }

  private static SourceOrigin originFromNode(ObjectNode node, boolean privateRoot) {
    SourceOrigin.Kind kind = SourceOrigin.Kind.valueOf(text(node, "kind"));
    String logicalIdentity = text(node, "logicalIdentity");
    if (!privateRoot) throw new IllegalArgumentException("private origin root is required");
    Path root = Path.of(text(node, "canonicalRoot")).toAbsolutePath().normalize();
    return kind == SourceOrigin.Kind.GIT_COMMIT
        ? new GitCommitSourceOrigin(logicalIdentity, root, text(node, "commitId"))
        : new DirectorySourceOrigin(logicalIdentity, root);
  }

  private static ArrayNode targetsNode(List<SourcePreparationTarget> targets) {
    ArrayNode array = JsonNodeFactory.instance.arrayNode();
    targets.stream()
        .sorted(Comparator.comparing(SourcePreparationTarget::relativePath, UTF8_ORDER))
        .forEach(
            target -> {
              ObjectNode node = array.addObject();
              node.put("relativePath", target.relativePath());
              node.put("kind", target.kind().name());
            });
    return array;
  }

  private static List<SourcePreparationTarget> targetsFromNode(ArrayNode values) {
    List<SourcePreparationTarget> targets = new ArrayList<>();
    for (JsonNode value : values) {
      ObjectNode target = object(value);
      targets.add(
          new SourcePreparationTarget(
              text(target, "relativePath"),
              SourcePreparationTarget.Kind.valueOf(text(target, "kind"))));
    }
    return targets;
  }

  private static ObjectNode limitsNode(SourcePreparationLimits limits) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("maxFiles", limits.maxFiles());
    node.put("maxTotalBytes", limits.maxTotalBytes());
    return node;
  }

  private static SourcePreparationLimits limitsFromNode(ObjectNode node) {
    return new SourcePreparationLimits(
        positiveInt(node, "maxFiles"), nonnegativeLong(node, "maxTotalBytes"));
  }

  private static ObjectNode originAttributesNode(SourceOriginAttributes attributes) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("kind", attributes.kind().name());
    nullableText(node, "gitMode", attributes.gitMode());
    nullableText(node, "gitBlobObjectId", attributes.gitBlobObjectId());
    if (attributes.executable() == null) node.putNull("executable");
    else node.put("executable", attributes.executable());
    return node;
  }

  private static SourceOriginAttributes originAttributesFromNode(ObjectNode node) {
    JsonNode executable = node.get("executable");
    return new SourceOriginAttributes(
        SourceOriginAttributes.Kind.valueOf(text(node, "kind")),
        nullableText(node, "gitMode"),
        nullableText(node, "gitBlobObjectId"),
        executable == null || executable.isNull() ? null : executable.booleanValue());
  }

  private static ObjectNode observationsNode(SourceEntryObservations value) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    if (value.before() == null) node.putNull("before");
    else node.set("before", observationNode(value.before()));
    if (value.after() == null) node.putNull("after");
    else node.set("after", observationNode(value.after()));
    return node;
  }

  private static SourceEntryObservations observationsFromNode(ObjectNode node) {
    JsonNode before = node.get("before");
    JsonNode after = node.get("after");
    return new SourceEntryObservations(
        before == null || before.isNull() ? null : observationFromNode(object(before)),
        after == null || after.isNull() ? null : observationFromNode(object(after)));
  }

  private static ObjectNode observationNode(SourceObservation value) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    nullableLong(node, "sizeBytes", value.sizeBytes());
    nullableText(node, "sha256", value.sha256() == null ? null : value.sha256().value());
    nullableText(
        node,
        "sourceIdentity",
        value.sourceIdentity() == null ? null : value.sourceIdentity().value());
    nullableText(
        node, "modifiedAt", value.modifiedAt() == null ? null : value.modifiedAt().toString());
    nullableText(node, "fileKey", value.fileKey());
    return node;
  }

  private static SourceObservation observationFromNode(ObjectNode node) {
    String modified = nullableText(node, "modifiedAt");
    String sha = nullableText(node, "sha256");
    String identity = nullableText(node, "sourceIdentity");
    return new SourceObservation(
        nullableLong(node, "sizeBytes"),
        sha == null ? null : Sha256Digest.parse(sha),
        identity == null ? null : ArtifactId.parse(identity),
        modified == null ? null : Instant.parse(modified),
        nullableText(node, "fileKey"));
  }

  private static ObjectNode preparedReferenceNode(PreparedSourceReference value) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("sourceVersionId", value.sourceVersionId().value());
    ObjectNode publication = node.putObject("publication");
    publication.put("runId", value.publication().address().runId().value());
    publication.put("analysisStepKey", value.publication().address().analysisStepKey().wireValue());
    publication.put("root", value.publication().analysisStepArtifactRoot().value());
    publication.put("receiptId", value.publication().analysisStepReceiptId().value());
    publication.put("receiptSha256", value.publication().analysisStepReceiptSha256().value());
    node.set("schemaBundleRef", referenceNode(value.schemaBundleRef()));
    node.set("artifactPolicyRegistryRef", policyReferenceNode(value.artifactPolicyRegistryRef()));
    return node;
  }

  private static PreparedSourceReference preparedReferenceFromNode(ObjectNode node) {
    ObjectNode publication = object(node.get("publication"));
    return new PreparedSourceReference(
        ArtifactId.parse(text(node, "sourceVersionId")),
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(
                org.sourceanalysis.app.artifact.AnalysisRunId.parse(text(publication, "runId")),
                AnalysisStepKey.parse(text(publication, "analysisStepKey"))),
            AnalysisStepArtifactRoot.parse(text(publication, "root")),
            AnalysisStepReceiptId.parse(text(publication, "receiptId")),
            Sha256Digest.parse(text(publication, "receiptSha256"))),
        reference(node, "schemaBundleRef"),
        policyReference(object(node.get("artifactPolicyRegistryRef"))));
  }

  private static ObjectNode controlsNode(ArtifactControls controls) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("toolchainSha256", controls.toolchainSha256().value());
    node.put("profileSha256", controls.profileSha256().value());
    node.put("schemaBundleSha256", controls.schemaBundleSha256().value());
    nullableText(
        node,
        "promptBundleSha256",
        controls.promptBundleSha256() == null ? null : controls.promptBundleSha256().value());
    node.set(
        "artifactPolicyRegistryRef", policyReferenceNode(controls.artifactPolicyRegistryRef()));
    return node;
  }

  private static ArtifactControls controlsFromNode(ObjectNode node) {
    String prompt = nullableText(node, "promptBundleSha256");
    return new ArtifactControls(
        Sha256Digest.parse(text(node, "toolchainSha256")),
        Sha256Digest.parse(text(node, "profileSha256")),
        Sha256Digest.parse(text(node, "schemaBundleSha256")),
        prompt == null ? null : Sha256Digest.parse(prompt),
        policyReference(object(node.get("artifactPolicyRegistryRef"))));
  }

  private static ObjectNode referenceNode(ArtifactReference ref) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("artifactId", ref.artifactId().value());
    node.put("sha256", ref.sha256().value());
    return node;
  }

  private static ArtifactReference reference(ObjectNode parent, String field) {
    return reference(object(parent.get(field)));
  }

  private static ArtifactReference reference(ObjectNode node) {
    return new ArtifactReference(
        ArtifactId.parse(text(node, "artifactId")), Sha256Digest.parse(text(node, "sha256")));
  }

  private static ObjectNode policyReferenceNode(ArtifactPolicyRegistryReference ref) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("artifactId", ref.artifactId().value());
    node.put("sha256", ref.sha256().value());
    return node;
  }

  private static ArtifactPolicyRegistryReference policyReference(ObjectNode node) {
    return new ArtifactPolicyRegistryReference(
        ArtifactId.parse(text(node, "artifactId")), Sha256Digest.parse(text(node, "sha256")));
  }

  private ObjectNode registrationNode(CapturedSourcePreparation capture, ArtifactId sourceVersion) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("schemaVersion", "source-registration-v2");
    node.put("sourceVersionId", sourceVersion.value());
    node.set("origin", originNode(capture.request().origin(), false));
    return node;
  }

  private ObjectNode captureReceiptNode(
      CapturedSourcePreparation capture, ArtifactId sourceVersion) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("schemaVersion", "source-capture-receipt-v2");
    node.put("sourceVersionId", sourceVersion.value());
    node.put("inspectionStatus", capture.result().inspectionStatus().name());
    return node;
  }

  private static ObjectNode inventoryNode(SourcePreparationResult result) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("schemaVersion", "source-snapshot-entry-v2");
    node.set("entries", resultNode(result).get("entries"));
    return node;
  }

  private static ObjectNode profileNode(SourcePreparationRequest request) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("schemaVersion", "source-preparation-profile-v1");
    node.set("origin", originNode(request.origin(), false));
    node.set("limits", limitsNode(request.limits()));
    node.set("policyRef", referenceNode(request.policyRef()));
    return node;
  }

  private static ObjectNode toolchainNode(SourcePreparationToolIdentity identity) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("schemaVersion", "source-preparation-toolchain-v1");
    node.put("producerVersion", identity.producerVersion());
    node.put("buildKind", identity.buildKind());
    node.put("buildSha256", identity.buildSha256().value());
    node.put("javaVendor", identity.javaVendor());
    node.put("javaVersion", identity.javaVersion());
    nullableText(node, "trustedGitVersion", identity.trustedGitVersion());
    return node;
  }

  private static ObjectNode schemaBundleNode() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("schemaVersion", "source-preparation-schema-bundle-v1");
    ArrayNode schemas = node.putArray("schemas");
    List.of(
            "source-preparation-input-v1",
            "source-preparation-inventory-v1",
            "source-preparation-issues-v1",
            "source-preparation-result-v1",
            "verified-source-inventory-admitted-source-request-v3",
            "verified-source-inventory-verified-source-index-v3")
        .forEach(schemas::add);
    return node;
  }

  private static ObjectNode budgetNode(SourcePreparationLimits limits) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("schemaVersion", "source-preparation-resource-budget-v1");
    node.set("limits", limitsNode(limits));
    return node;
  }

  private static ObjectNode capabilityNode() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("schemaVersion", "source-preparation-capability-v1");
    node.put("sourceRead", "FROZEN_BYTES_ONLY");
    node.put("textClassification", "STRICT_UTF8_OR_MEDIA");
    return node;
  }

  private ArtifactReference reference(String prefix, ObjectNode content) {
    byte[] bytes = json.encodeCanonical(content).copyToByteArray();
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + sha256(bytes)), Sha256Digest.parse(sha256(bytes)));
  }

  private static void deleteTree(Path path) throws IOException {
    try (var paths = Files.walk(path)) {
      paths
          .sorted(Comparator.reverseOrder())
          .forEach(
              value -> {
                try {
                  Files.deleteIfExists(value);
                } catch (IOException failure) {
                  throw new ArchiveDeleteFailure(failure);
                }
              });
    } catch (ArchiveDeleteFailure failure) {
      throw failure.cause;
    }
  }

  private static Set<String> fields(ObjectNode node) {
    java.util.HashSet<String> names = new java.util.HashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return Set.copyOf(names);
  }

  private static ObjectNode object(JsonNode value) {
    if (!(value instanceof ObjectNode object))
      throw new IllegalArgumentException("expected JSON object");
    return object;
  }

  private static ArrayNode array(ObjectNode node, String field) {
    if (!(node.get(field) instanceof ArrayNode array))
      throw new IllegalArgumentException("expected JSON array");
    return array;
  }

  private static String text(ObjectNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isTextual())
      throw new IllegalArgumentException("expected JSON text");
    return value.textValue();
  }

  private static void requireText(ObjectNode node, String field, String expected) {
    if (!expected.equals(text(node, field)))
      throw new IllegalArgumentException("unexpected JSON text");
  }

  private static boolean bool(ObjectNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isBoolean())
      throw new IllegalArgumentException("expected JSON boolean");
    return value.booleanValue();
  }

  private static Long nullableLong(ObjectNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    if (!value.canConvertToLong()) throw new IllegalArgumentException("expected JSON integer");
    return value.longValue();
  }

  private static void nullableLong(ObjectNode node, String field, Long value) {
    if (value == null) node.putNull(field);
    else node.put(field, value);
  }

  private static String nullableText(ObjectNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    if (!value.isTextual()) throw new IllegalArgumentException("expected optional JSON text");
    return value.textValue();
  }

  private static void nullableText(ObjectNode node, String field, String value) {
    if (value == null) node.putNull(field);
    else node.put(field, value);
  }

  private static int positiveInt(ObjectNode node, String field) {
    long value = nonnegativeLong(node, field);
    if (value > Integer.MAX_VALUE || value == 0L)
      throw new IllegalArgumentException("expected positive JSON integer");
    return (int) value;
  }

  private static long nonnegativeLong(ObjectNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.canConvertToLong() || value.longValue() < 0L)
      throw new IllegalArgumentException("expected nonnegative JSON integer");
    return value.longValue();
  }

  private static MessageDigest sha256Digest() throws IOException {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IOException("SHA-256 unavailable", unavailable);
    }
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private static int compareUtf8(String first, String second) {
    byte[] left = first.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] right = second.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) return comparison;
    }
    return Integer.compare(left.length, right.length);
  }

  private static final class ArchiveDeleteFailure extends RuntimeException {
    private final IOException cause;

    private ArchiveDeleteFailure(IOException cause) {
      this.cause = cause;
    }
  }

  private record StoredFact(
      String fileName, String artifactIdPrefix, ArtifactReference reference, ObjectNode body) {}

  /** The private frozen facts and real references derived from one captured preparation. */
  public record StoredCapture(
      ArtifactId sourceVersionId,
      SourcePreparationRequest request,
      SourcePreparationResult result,
      SourcePreparationAssessment assessment,
      ArtifactReference preparationRequestRef,
      ArtifactReference sourceRegistrationRef,
      ArtifactReference captureReceiptRef,
      ArtifactReference snapshotManifestRef,
      ArtifactReference preparationProfileRef,
      ArtifactReference preparationToolchainRef,
      ArtifactReference schemaBundleRef,
      ArtifactReference resourceBudgetRef,
      ArtifactReference capabilityProfileRef,
      ArtifactControls controls) {}

  /** Private, content-addressed receipt references for the M1/M2 prerequisites of a v3 report. */
  public record PublicationLinks(
      AnalysisStepPublicationAddress reportAddress,
      ModulePublicationReference requestAdmission,
      ModulePublicationReference sourceIndex) {}

  /** Exact pre-queue references for the six-field source-preparation analysis-run request. */
  public record PreparationInputReferences(
      ArtifactReference preparationRequestRef,
      ArtifactReference policyRegistryRef,
      ArtifactReference schemaBundleRef,
      ArtifactReference resourceBudgetRef,
      ArtifactReference preparationProfileRef,
      ArtifactReference preparationToolchainRef) {}
}
