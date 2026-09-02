package org.sourceanalysis.app.analysis.discovery;

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
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;

/** Owns the receipt-last module publication boundary for a detected application profile. */
public final class ApplicationProfileModulePublisher {

  private static final String ARTIFACT_TYPE = "APPLICATION_DISCOVERY_APPLICATION_PROFILE_DRAFT";
  private static final String SCHEMA_VERSION = "application-discovery-application-profile-draft-v2";
  private static final String ARTIFACT_PREFIX = "application-profile";
  private static final Comparator<String> UTF8_ORDER =
      ApplicationProfileModulePublisher::compareUtf8;

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson;

  /** Creates the publisher with the only permitted module-artifact storage dependency. */
  public ApplicationProfileModulePublisher(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.canonicalJson = new CanonicalJsonCodec();
  }

  /** Installs the one canonical M1 profile draft and returns only its typed reopen reference. */
  public ApplicationProfileDraftReference publish(
      AnalysisStepModuleAddress destination, ApplicationProfile profile) {
    try {
      requireDestination(destination);
      Objects.requireNonNull(profile, "application profile");
      List<ArtifactReference> upstream =
          sortedReferences(
              List.of(
                  profile.capabilityProfileRef(),
                  profile.sourceInventoryRef(),
                  profile.verifiedSnapshotRef()));
      InstalledModulePublication installed =
          moduleArtifacts.install(
              new ModuleInstallRequest(
                  destination,
                  "v2",
                  upstream,
                  profile.controls(),
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  List.of(payload(destination, upstream, profile))));
      return new ApplicationProfileDraftReference(installed.reference());
    } catch (ApplicationDiscoveryException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ApplicationDiscoveryException("APPLICATION_PROFILE_PUBLICATION_INVALID");
    }
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress address,
      List<ArtifactReference> upstream,
      ApplicationProfile profile) {
    ObjectNode withoutArtifactId = JsonNodeFactory.instance.objectNode();
    withoutArtifactId.put("schemaVersion", SCHEMA_VERSION);
    withoutArtifactId.put("artifactType", ARTIFACT_TYPE);
    withoutArtifactId.set("producer", producer(address));
    withoutArtifactId.set("upstreamArtifacts", references(upstream));
    withoutArtifactId.set("controls", controls(profile.controls()));
    withoutArtifactId.set("completion", completion());
    withoutArtifactId.set("payload", body(profile));
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
        "application-profile-draft.json",
        ARTIFACT_TYPE,
        SCHEMA_VERSION,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(envelope));
  }

  private static ObjectNode body(ApplicationProfile profile) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("applicationProfileId", profile.applicationProfileId().value());
    body.put("snapshotId", profile.snapshotId());
    body.put("inventoryScopeKind", profile.inventoryScopeKind());
    body.put("repositoryCompletionEligible", profile.repositoryCompletionEligible());
    body.put("language", profile.language().name());
    if (profile.languageVersion() == null) {
      body.putNull("languageVersion");
    } else {
      body.put("languageVersion", profile.languageVersion());
    }
    ArrayNode frameworkSignals = body.putArray("frameworkSignals");
    for (FrameworkSignal signal : profile.frameworkSignals()) {
      ObjectNode item = frameworkSignals.addObject();
      item.put("kind", signal.kind().name());
      item.set("sourceExcerpt", sourceExcerpt(signal.sourceExcerpt()));
      item.put("disposition", signal.disposition().name());
      if (signal.reasonCode() == null) {
        item.putNull("reasonCode");
      } else {
        item.put("reasonCode", signal.reasonCode());
      }
    }
    ArrayNode configSignals = body.putArray("configSignals");
    for (ConfigSignal signal : profile.configSignals()) {
      ObjectNode item = configSignals.addObject();
      item.put("kind", signal.kind().name());
      item.set("sourceExcerpt", sourceExcerpt(signal.sourceExcerpt()));
      if (signal.value() == null) {
        item.putNull("value");
      } else {
        item.put("value", signal.value());
      }
      item.put("disposition", signal.disposition().name());
      if (signal.reasonCode() == null) {
        item.putNull("reasonCode");
      } else {
        item.put("reasonCode", signal.reasonCode());
      }
    }
    body.set("capabilityProfileRef", reference(profile.capabilityProfileRef()));
    return body;
  }

  private static ObjectNode sourceExcerpt(org.sourceanalysis.app.evidence.SourceExcerptV1 excerpt) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    ObjectNode locator = body.putObject("locator");
    locator.put("fileId", excerpt.locator().fileId().value());
    locator.put("path", excerpt.locator().path());
    locator.put("startByte", excerpt.locator().startByte());
    locator.put("endByteExclusive", excerpt.locator().endByteExclusive());
    locator.put("startLine", excerpt.locator().startLine());
    locator.put("startColumn", excerpt.locator().startColumn());
    locator.put("endLine", excerpt.locator().endLine());
    locator.put("endColumn", excerpt.locator().endColumn());
    body.put("rawUtf8", new String(excerpt.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8));
    body.put("rawUtf8Sha256", excerpt.rawUtf8Sha256().value());
    return body;
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

  private static ArrayNode references(List<ArtifactReference> upstream) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    upstream.forEach(reference -> values.add(reference(reference)));
    return values;
  }

  private static ObjectNode reference(ArtifactReference reference) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", reference.artifactId().value())
        .put("sha256", reference.sha256().value());
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

  private static List<ArtifactReference> sortedReferences(List<ArtifactReference> references) {
    List<ArtifactReference> sorted = new ArrayList<>(references);
    sorted.sort(Comparator.comparing(reference -> reference.artifactId().value(), UTF8_ORDER));
    if (sorted.stream().distinct().count() != sorted.size()) {
      throw new ApplicationDiscoveryException("APPLICATION_PROFILE_PUBLICATION_INVALID");
    }
    return List.copyOf(sorted);
  }

  private static void requireDestination(AnalysisStepModuleAddress destination) {
    if (destination == null
        || destination.analysisStepKey() != AnalysisStepKey.APPLICATION_DISCOVERY
        || destination.moduleNumber() != 1
        || !"application-profile".equals(destination.moduleKey())) {
      throw new ApplicationDiscoveryException("APPLICATION_PROFILE_PUBLICATION_INVALID");
    }
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
}
