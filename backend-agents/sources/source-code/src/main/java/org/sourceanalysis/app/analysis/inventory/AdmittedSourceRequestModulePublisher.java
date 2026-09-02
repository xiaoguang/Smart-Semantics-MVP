package org.sourceanalysis.app.analysis.inventory;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** M1 publisher for the canonical admitted-source-request module artifact. */
final class AdmittedSourceRequestModulePublisher {

  private static final String ARTIFACT_TYPE = "VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST";
  private static final String SCHEMA_VERSION =
      "verified-source-inventory-admitted-source-request-v2";
  private static final String ARTIFACT_PREFIX = "source-request";
  private static final Comparator<String> UTF8_ORDER =
      (first, second) -> compareBytes(utf8(first), utf8(second));

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson;

  AdmittedSourceRequestModulePublisher(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifacts");
    this.canonicalJson = new CanonicalJsonCodec();
  }

  ModulePublicationReference publish(AdmittedSourceRequestPublicationInput input) {
    AnalysisStepModuleAddress address;
    ArtifactControls controls;
    List<ArtifactReference> upstream;
    CanonicalModulePayload payload;
    try {
      if (input == null) {
        throw invalid();
      }
      AdmittedSourceRequest request = input.admittedSourceRequest();
      validateInput(input, request);
      controls = controls(request.controls());
      address =
          new AnalysisStepModuleAddress(
              input.runId(), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 1, "request-admission");
      upstream = expectedUpstream(input, request);
      payload = payload(address, controls, upstream, request);
    } catch (FrozenRequestAdmissionException failure) {
      throw failure;
    } catch (IllegalArgumentException failure) {
      throw new FrozenRequestAdmissionException("REQUEST_SCHEMA_INVALID");
    }
    InstalledModulePublication installed =
        moduleArtifacts.install(
            new ModuleInstallRequest(
                address,
                "v2",
                upstream,
                controls,
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                List.of(payload)));
    return installed.reference();
  }

  private void validateInput(
      AdmittedSourceRequestPublicationInput input, AdmittedSourceRequest request) {
    if (!input.analysisRunRequestRef().artifactId().value().equals(request.requestIdentity())
        || !input.sourceRegistrationRef().artifactId().equals(request.sourceRegistrationId())) {
      throw invalid();
    }
    validateFiles(request);
    List<ArtifactReference> expected = expectedUpstream(input, request);
    if (!expected.equals(input.upstreamArtifacts())) {
      throw invalid();
    }
  }

  private static List<ArtifactReference> expectedUpstream(
      AdmittedSourceRequestPublicationInput input, AdmittedSourceRequest request) {
    List<ArtifactReference> references =
        new ArrayList<>(
            List.of(
                input.analysisRunRequestRef(),
                input.sourceRegistrationRef(),
                request.frozenRepositoryRequestRef(),
                request.captureReceiptRef(),
                request.snapshotManifestRef(),
                input.verificationPolicyRef(),
                input.capabilityProfileRef(),
                request.controls().resourceBudgetRef()));
    references.sort(Comparator.comparing(ArtifactReference::artifactId, artifactIdOrder()));
    ArtifactReference previous = null;
    for (ArtifactReference reference : references) {
      if (reference == null || (previous != null && compareReference(previous, reference) >= 0)) {
        throw invalid();
      }
      previous = reference;
    }
    return List.copyOf(references);
  }

  private static Comparator<ArtifactId> artifactIdOrder() {
    return Comparator.comparing(ArtifactId::value, UTF8_ORDER);
  }

  private static int compareReference(ArtifactReference first, ArtifactReference second) {
    int identity = UTF8_ORDER.compare(first.artifactId().value(), second.artifactId().value());
    return identity != 0
        ? identity
        : UTF8_ORDER.compare(first.sha256().value(), second.sha256().value());
  }

  private static ArtifactControls controls(RunRequestControls requestControls) {
    ArtifactReference policy = requestControls.artifactPolicyRegistryRef();
    return new ArtifactControls(
        requestControls.toolchainRef().sha256(),
        requestControls.profileBundleRef().sha256(),
        requestControls.schemaBundleRef().sha256(),
        requestControls.promptBundleRef().sha256(),
        new ArtifactPolicyRegistryReference(policy.artifactId(), policy.sha256()));
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress address,
      ArtifactControls controls,
      List<ArtifactReference> upstream,
      AdmittedSourceRequest request) {
    ObjectNode withoutArtifactId = JsonNodeFactory.instance.objectNode();
    withoutArtifactId.put("schemaVersion", SCHEMA_VERSION);
    withoutArtifactId.put("artifactType", ARTIFACT_TYPE);
    withoutArtifactId.set("producer", producer(address));
    withoutArtifactId.set("upstreamArtifacts", references(upstream));
    withoutArtifactId.set("controls", controls(controls));
    withoutArtifactId.set("completion", completion());
    withoutArtifactId.set("payload", body(request));
    String artifactId =
        ARTIFACT_PREFIX
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-module-artifact-id-v1"),
                    frame(SCHEMA_VERSION),
                    frame(ARTIFACT_TYPE),
                    frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray())));
    ObjectNode envelope = withoutArtifactId.deepCopy();
    envelope.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        "admitted-source-request.json",
        ARTIFACT_TYPE,
        SCHEMA_VERSION,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(envelope));
  }

  private static ObjectNode producer(AnalysisStepModuleAddress address) {
    ObjectNode producer = JsonNodeFactory.instance.objectNode();
    ObjectNode producerAddress = producer.putObject("address");
    producerAddress.put("kind", "ANALYSIS_STEP");
    producerAddress.put("runId", address.runId().value());
    producerAddress.put("analysisStepKey", address.analysisStepKey().wireValue());
    producerAddress.put("moduleNumber", address.moduleNumber());
    producerAddress.put("moduleKey", address.moduleKey());
    producer.put("moduleVersion", "v2");
    return producer;
  }

  private static ArrayNode references(List<ArtifactReference> references) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    for (ArtifactReference reference : references) {
      values
          .addObject()
          .put("artifactId", reference.artifactId().value())
          .put("sha256", reference.sha256().value());
    }
    return values;
  }

  private static ObjectNode controls(ArtifactControls controls) {
    ObjectNode values = JsonNodeFactory.instance.objectNode();
    values.put("toolchainSha256", controls.toolchainSha256().value());
    values.put("profileSha256", controls.profileSha256().value());
    values.put("schemaBundleSha256", controls.schemaBundleSha256().value());
    if (controls.promptBundleSha256() == null) {
      values.putNull("promptBundleSha256");
    } else {
      values.put("promptBundleSha256", controls.promptBundleSha256().value());
    }
    values
        .putObject("artifactPolicyRegistryRef")
        .put("artifactId", controls.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", controls.artifactPolicyRegistryRef().sha256().value());
    return values;
  }

  private static ObjectNode completion() {
    ObjectNode completion = JsonNodeFactory.instance.objectNode();
    completion.put("status", "SUCCEEDED");
    completion.putArray("gapRefs");
    completion.putNull("failureRef");
    return completion;
  }

  private static ObjectNode body(AdmittedSourceRequest request) {
    ObjectNode values = JsonNodeFactory.instance.objectNode();
    values.put("requestIdentity", request.requestIdentity());
    values.put("sourceRegistrationId", request.sourceRegistrationId().value());
    values.put("originRepositoryUrl", request.originRepositoryUrl());
    values.put("originRevision", request.originRevision());
    ObjectNode scope = values.putObject("inventoryScope");
    scope.put("kind", request.inventoryScope().kind().name());
    if (request.inventoryScope().scopeRoot() == null) {
      scope.putNull("scopeRoot");
    } else {
      scope.put("scopeRoot", request.inventoryScope().scopeRoot());
    }
    values.put("repositoryCompletionEligible", request.repositoryCompletionEligible());
    values.put("declaredPathCount", request.declaredPathCount());
    ArrayNode files = values.putArray("files");
    for (CapturedRegularFile file : request.files()) {
      ObjectNode item = files.addObject();
      item.put("path", file.path());
      item.put("gitMode", file.gitMode());
      item.put("mediaType", file.mediaType());
      item.put("sizeBytes", file.sizeBytes());
      item.put("sha256", file.sha256().value());
      item.put("analysisDisposition", file.analysisDisposition().name());
      if (file.textEncoding() == null) {
        item.putNull("textEncoding");
      } else {
        item.put("textEncoding", file.textEncoding());
      }
    }
    return values;
  }

  private static void validateFiles(AdmittedSourceRequest request) {
    if (request.repositoryCompletionEligible()
        && request.inventoryScope().kind() != InventoryScope.Kind.COMPLETE_CAPTURE) {
      throw invalid();
    }
    if (request.declaredPathCount() != request.files().size() || request.files().isEmpty()) {
      throw invalid();
    }
    Set<ArtifactId> all = new HashSet<>();
    String previousPath = null;
    for (CapturedRegularFile file : request.files()) {
      if (!all.add(file.fileId())
          || !validPath(file.path())
          || (previousPath != null && UTF8_ORDER.compare(previousPath, file.path()) >= 0)) {
        throw invalid();
      }
      previousPath = file.path();
    }
    Set<ArtifactId> text = new HashSet<>(request.analyzableTextFileIds());
    Set<ArtifactId> media = new HashSet<>(request.nonAnalyzableMediaFileIds());
    if (text.size() != request.analyzableTextFileIds().size()
        || media.size() != request.nonAnalyzableMediaFileIds().size()
        || !disjoint(text, media)) {
      throw invalid();
    }
    text.addAll(media);
    if (!text.equals(all)) {
      throw invalid();
    }
  }

  private static boolean disjoint(Set<ArtifactId> first, Set<ArtifactId> second) {
    for (ArtifactId value : first) {
      if (second.contains(value)) {
        return false;
      }
    }
    return true;
  }

  private static boolean validPath(String path) {
    if (path == null || path.isBlank() || path.startsWith("/") || path.indexOf('\\') >= 0) {
      return false;
    }
    for (String segment : path.split("/", -1)) {
      if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
        return false;
      }
    }
    return true;
  }

  private static byte[] frame(String value) {
    return frame(utf8(value));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[]... values) {
    int length = 0;
    for (byte[] value : values) {
      length = Math.addExact(length, value.length);
    }
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private static byte[] utf8(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static int compareBytes(byte[] first, byte[] second) {
    int commonLength = Math.min(first.length, second.length);
    for (int index = 0; index < commonLength; index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(first[index]), Byte.toUnsignedInt(second[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(first.length, second.length);
  }

  private static FrozenRequestAdmissionException invalid() {
    return new FrozenRequestAdmissionException("REQUEST_SCHEMA_INVALID");
  }
}
