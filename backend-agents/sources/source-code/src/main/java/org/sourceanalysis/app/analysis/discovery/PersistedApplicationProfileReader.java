package org.sourceanalysis.app.analysis.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** Reopens and cross-checks the M1 profile draft before M2 or M3 can inspect source bytes. */
final class PersistedApplicationProfileReader {

  private static final String ARTIFACT_TYPE = "APPLICATION_DISCOVERY_APPLICATION_PROFILE_DRAFT";
  private static final String SCHEMA_VERSION = "application-discovery-application-profile-draft-v2";

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final VerifiedSourceContentHandle sourceHandle;
  private final CanonicalJsonCodec canonicalJson;

  PersistedApplicationProfileReader(
      CanonicalModuleArtifactStore moduleArtifacts, VerifiedSourceContentHandle sourceHandle) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.sourceHandle = Objects.requireNonNull(sourceHandle, "verified source handle");
    this.canonicalJson = new CanonicalJsonCodec();
  }

  ApplicationProfile reopen(
      ApplicationProfileDraftReference draft, VerifiedSourceInventoryReference frozenSource) {
    try {
      ReopenedModulePublication publication = moduleArtifacts.reopen(draft.publication());
      requireDraft(publication);
      VerifiedSourceTextSet source = sourceHandle.reopen(frozenSource);
      if (!publication.receipt().controls().equals(source.controls())
          || !publication.receipt().upstreamArtifacts().contains(source.sourceInventoryRef())
          || !publication.receipt().upstreamArtifacts().contains(source.verifiedSnapshotRef())) {
        throw failure();
      }
      ObjectNode envelope =
          canonicalJson.parseCanonical(publication.payloads().get(0).canonicalUtf8()).deepCopy();
      if (!(envelope.get("payload") instanceof ObjectNode body)) {
        throw failure();
      }
      if (!source.snapshotId().equals(text(body, "snapshotId"))
          || !source.inventoryScopeKind().equals(text(body, "inventoryScopeKind"))
          || source.repositoryCompletionEligible()
              != booleanValue(body, "repositoryCompletionEligible")
          || !source.capabilityProfileRef().equals(reference(body, "capabilityProfileRef"))) {
        throw failure();
      }
      return new ApplicationProfile(
          ArtifactId.parse(text(body, "applicationProfileId")),
          text(body, "snapshotId"),
          text(body, "inventoryScopeKind"),
          booleanValue(body, "repositoryCompletionEligible"),
          enumValue(ApplicationLanguage.class, text(body, "language")),
          nullableInteger(body, "languageVersion"),
          frameworkSignals(array(body, "frameworkSignals")),
          configSignals(array(body, "configSignals")),
          reference(body, "capabilityProfileRef"),
          source.sourceInventoryRef(),
          source.verifiedSnapshotRef(),
          source.controls());
    } catch (ApplicationDiscoveryException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure();
    }
  }

  private static void requireDraft(ReopenedModulePublication publication) {
    if (!(publication.reference().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.APPLICATION_DISCOVERY
        || address.moduleNumber() != 1
        || !"application-profile".equals(address.moduleKey())
        || publication.payloads().size() != 1
        || !ARTIFACT_TYPE.equals(publication.payloads().get(0).descriptor().artifactType())
        || !SCHEMA_VERSION.equals(publication.payloads().get(0).descriptor().schemaVersion())) {
      throw failure();
    }
  }

  private static List<FrameworkSignal> frameworkSignals(ArrayNode values) {
    List<FrameworkSignal> signals = new ArrayList<>();
    for (JsonNode value : values) {
      ObjectNode node = object(value);
      signals.add(
          new FrameworkSignal(
              enumValue(FrameworkSignalKind.class, text(node, "kind")),
              excerpt(object(node.get("sourceExcerpt"))),
              enumValue(SignalDisposition.class, text(node, "disposition")),
              nullableText(node, "reasonCode")));
    }
    return List.copyOf(signals);
  }

  private static List<ConfigSignal> configSignals(ArrayNode values) {
    List<ConfigSignal> signals = new ArrayList<>();
    for (JsonNode value : values) {
      ObjectNode node = object(value);
      signals.add(
          new ConfigSignal(
              enumValue(ConfigSignalKind.class, text(node, "kind")),
              excerpt(object(node.get("sourceExcerpt"))),
              nullableText(node, "value"),
              enumValue(SignalDisposition.class, text(node, "disposition")),
              nullableText(node, "reasonCode")));
    }
    return List.copyOf(signals);
  }

  private static SourceExcerptV1 excerpt(ObjectNode node) {
    Set<String> expected = Set.of("locator", "rawUtf8", "rawUtf8Sha256");
    if (!fields(node).equals(expected)) {
      throw failure();
    }
    ObjectNode locator = object(node.get("locator"));
    if (!fields(locator)
        .equals(
            Set.of(
                "fileId",
                "path",
                "startByte",
                "endByteExclusive",
                "startLine",
                "startColumn",
                "endLine",
                "endColumn"))) {
      throw failure();
    }
    byte[] raw = text(node, "rawUtf8").getBytes(StandardCharsets.UTF_8);
    Sha256Digest digest = Sha256Digest.parse(text(node, "rawUtf8Sha256"));
    if (!digest.value().equals(sha256(raw))) {
      throw failure();
    }
    return new SourceExcerptV1(
        new SourceLocatorV1(
            ArtifactId.parse(text(locator, "fileId")),
            text(locator, "path"),
            integer(locator, "startByte"),
            integer(locator, "endByteExclusive"),
            integer(locator, "startLine"),
            integer(locator, "startColumn"),
            integer(locator, "endLine"),
            integer(locator, "endColumn")),
        ImmutableBytes.copyOf(raw),
        digest);
  }

  private static ArtifactReference reference(ObjectNode node, String fieldName) {
    ObjectNode reference = object(node.get(fieldName));
    if (!fields(reference).equals(Set.of("artifactId", "sha256"))) {
      throw failure();
    }
    return new ArtifactReference(
        ArtifactId.parse(text(reference, "artifactId")),
        Sha256Digest.parse(text(reference, "sha256")));
  }

  private static ObjectNode object(JsonNode node) {
    if (node instanceof ObjectNode object) {
      return object;
    }
    throw failure();
  }

  private static ArrayNode array(ObjectNode node, String fieldName) {
    if (node.get(fieldName) instanceof ArrayNode array) {
      return array;
    }
    throw failure();
  }

  private static String text(ObjectNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw failure();
    }
    return value.textValue();
  }

  private static String nullableText(ObjectNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    if (value == null || !(value.isNull() || value.isTextual())) {
      throw failure();
    }
    return value.isNull() ? null : value.textValue();
  }

  private static boolean booleanValue(ObjectNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    if (value == null || !value.isBoolean()) {
      throw failure();
    }
    return value.booleanValue();
  }

  private static int integer(ObjectNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    if (value == null || !value.isInt()) {
      throw failure();
    }
    return value.intValue();
  }

  private static Integer nullableInteger(ObjectNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    if (value == null || !(value.isNull() || value.isInt())) {
      throw failure();
    }
    return value.isNull() ? null : value.intValue();
  }

  private static <T extends Enum<T>> T enumValue(Class<T> enumType, String value) {
    try {
      return Enum.valueOf(enumType, value);
    } catch (IllegalArgumentException invalid) {
      throw failure();
    }
  }

  private static Set<String> fields(ObjectNode node) {
    Set<String> values = new HashSet<>();
    node.fieldNames().forEachRemaining(values::add);
    return Set.copyOf(values);
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private static ApplicationDiscoveryException failure() {
    return new ApplicationDiscoveryException("APPLICATION_PROFILE_REOPEN_MISMATCH");
  }
}
