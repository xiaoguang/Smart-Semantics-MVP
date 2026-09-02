package org.sourceanalysis.app.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** The immutable, path-free registry that authoritatively governs artifact policy lookup. */
public interface CanonicalArtifactPolicyRegistry {

  /**
   * Loads exactly one canonical registry document without accepting a path, stream, caller-selected
   * reference, or mutable registration.
   *
   * @throws ArtifactStoreException when any public registry invariant is invalid
   */
  static CanonicalArtifactPolicyRegistry load(
      ImmutableBytes canonicalDocument, CanonicalJsonCodec canonicalJson) {
    return LoadedCanonicalArtifactPolicyRegistry.load(canonicalDocument, canonicalJson);
  }

  /** Returns the immutable identity and full-document digest of this registry. */
  ArtifactPolicyRegistryReference reference();

  /** Resolves one exact registered artifact type and schema version. */
  CanonicalArtifactPolicy resolve(ArtifactPolicyKey key);
}

/** Package-private immutable implementation behind the sole public registry contract. */
final class LoadedCanonicalArtifactPolicyRegistry implements CanonicalArtifactPolicyRegistry {

  private static final String SCHEMA_VERSION = "artifact-policy-registry-v2";
  private static final String ID_PREFIX = "artifact-policy-registry:";
  private static final String ID_DOMAIN = "canonical-artifact-policy-registry-id-v2";
  private static final String INVALID_CODE = "ARTIFACT_POLICY_REGISTRY_INVALID";
  private static final String NOT_FOUND_CODE = "ARTIFACT_POLICY_NOT_FOUND";
  private static final Set<String> DOCUMENT_FIELDS =
      Set.of("schemaVersion", "artifactPolicyRegistryId", "policies");
  private static final Set<String> POLICY_FIELDS =
      Set.of(
          "artifactType",
          "schemaVersion",
          "artifactIdPrefix",
          "mediaType",
          "envelopeKind",
          "emptyJsonlAllowed",
          "publicContentExposure");
  private static final Comparator<ArtifactPolicyKey> KEY_ORDER =
      Comparator.comparing(
              ArtifactPolicyKey::artifactType, LoadedCanonicalArtifactPolicyRegistry::compareUtf8)
          .thenComparing(
              ArtifactPolicyKey::schemaVersion, LoadedCanonicalArtifactPolicyRegistry::compareUtf8);

  private final ArtifactPolicyRegistryReference reference;
  private final Map<ArtifactPolicyKey, CanonicalArtifactPolicy> policies;

  private LoadedCanonicalArtifactPolicyRegistry(
      ArtifactPolicyRegistryReference reference,
      Map<ArtifactPolicyKey, CanonicalArtifactPolicy> policies) {
    this.reference = reference;
    this.policies = Collections.unmodifiableMap(new LinkedHashMap<>(policies));
  }

  /**
   * Loads exactly one canonical {@code artifact-policy-registry-v2} document without accepting a
   * path, stream, caller-selected reference, or mutable registration.
   *
   * @throws ArtifactStoreException when any public registry invariant is invalid
   */
  static CanonicalArtifactPolicyRegistry load(
      ImmutableBytes canonicalDocument, CanonicalJsonCodec canonicalJson) {
    try {
      if (canonicalDocument == null || canonicalJson == null) {
        throw new IllegalArgumentException("registry inputs are required");
      }
      JsonNode parsed = canonicalJson.parseCanonical(canonicalDocument);
      if (!(parsed instanceof ObjectNode document)) {
        throw new IllegalArgumentException("registry document must be an object");
      }
      requireExactFields(document, DOCUMENT_FIELDS);
      requireExactText(document, "schemaVersion", SCHEMA_VERSION);
      String suppliedId = requiredText(document, "artifactPolicyRegistryId");
      if (!suppliedId.startsWith(ID_PREFIX)) {
        throw new IllegalArgumentException("registry identity has the wrong prefix");
      }
      ArtifactId suppliedArtifactId = ArtifactId.parse(suppliedId);
      ArrayNode policiesNode = requiredArray(document, "policies");
      LinkedHashMap<ArtifactPolicyKey, CanonicalArtifactPolicy> policies =
          parsePolicies(policiesNode);

      ObjectNode documentWithoutId = document.deepCopy();
      documentWithoutId.remove("artifactPolicyRegistryId");
      String expectedId =
          ID_PREFIX + sha256Hex(frame(ID_DOMAIN, canonicalJson.encodeCanonical(documentWithoutId)));
      if (!expectedId.equals(suppliedArtifactId.value())) {
        throw new IllegalArgumentException("registry identity does not match canonical content");
      }

      ArtifactPolicyRegistryReference reference =
          new ArtifactPolicyRegistryReference(
              suppliedArtifactId,
              Sha256Digest.parse(sha256Hex(canonicalDocument.copyToByteArray())));
      return new LoadedCanonicalArtifactPolicyRegistry(reference, policies);
    } catch (ArtifactStoreException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ArtifactStoreException(INVALID_CODE);
    }
  }

  /** Returns the immutable identity and full-document digest of this registry. */
  @Override
  public ArtifactPolicyRegistryReference reference() {
    return reference;
  }

  /** Resolves one exact registered artifact type and schema version. */
  @Override
  public CanonicalArtifactPolicy resolve(ArtifactPolicyKey key) {
    if (key == null) {
      throw new ArtifactStoreException(NOT_FOUND_CODE);
    }
    CanonicalArtifactPolicy policy = policies.get(key);
    if (policy == null) {
      throw new ArtifactStoreException(NOT_FOUND_CODE);
    }
    return policy;
  }

  private static LinkedHashMap<ArtifactPolicyKey, CanonicalArtifactPolicy> parsePolicies(
      ArrayNode policiesNode) {
    LinkedHashMap<ArtifactPolicyKey, CanonicalArtifactPolicy> policies = new LinkedHashMap<>();
    Set<ArtifactPolicyKey> seen = new HashSet<>();
    ArtifactPolicyKey precedingKey = null;
    for (JsonNode policyNode : policiesNode) {
      if (!(policyNode instanceof ObjectNode policyObject)) {
        throw new IllegalArgumentException("registry policy must be an object");
      }
      requireExactFields(policyObject, POLICY_FIELDS);
      ArtifactPolicyKey key =
          new ArtifactPolicyKey(
              requiredText(policyObject, "artifactType"),
              requiredText(policyObject, "schemaVersion"));
      if (!seen.add(key) || (precedingKey != null && KEY_ORDER.compare(precedingKey, key) >= 0)) {
        throw new IllegalArgumentException("registry policies must be unique and strictly ordered");
      }
      CanonicalArtifactPolicy policy =
          new CanonicalArtifactPolicy(
              key,
              requiredText(policyObject, "artifactIdPrefix"),
              CanonicalMediaType.parse(requiredText(policyObject, "mediaType")),
              CanonicalEnvelopeKind.parse(requiredText(policyObject, "envelopeKind")),
              requiredBoolean(policyObject, "emptyJsonlAllowed"),
              PublicContentExposure.parse(requiredText(policyObject, "publicContentExposure")));
      policies.put(key, policy);
      precedingKey = key;
    }
    return policies;
  }

  private static void requireExactFields(ObjectNode object, Set<String> requiredFields) {
    Set<String> actualFields = new HashSet<>();
    object.fieldNames().forEachRemaining(actualFields::add);
    if (!actualFields.equals(requiredFields)) {
      throw new IllegalArgumentException("object fields do not match the required registry schema");
    }
  }

  private static void requireExactText(ObjectNode object, String fieldName, String expectedValue) {
    if (!expectedValue.equals(requiredText(object, fieldName))) {
      throw new IllegalArgumentException("registry field does not have the required value");
    }
  }

  private static String requiredText(ObjectNode object, String fieldName) {
    JsonNode value = object.get(fieldName);
    if (value == null || !value.isTextual()) {
      throw new IllegalArgumentException("registry field must be text");
    }
    return value.textValue();
  }

  private static ArrayNode requiredArray(ObjectNode object, String fieldName) {
    JsonNode value = object.get(fieldName);
    if (!(value instanceof ArrayNode array)) {
      throw new IllegalArgumentException("registry field must be an array");
    }
    return array;
  }

  private static boolean requiredBoolean(ObjectNode object, String fieldName) {
    JsonNode value = object.get(fieldName);
    if (value == null || !value.isBoolean()) {
      throw new IllegalArgumentException("registry field must be boolean");
    }
    return value.booleanValue();
  }

  private static int compareUtf8(String first, String second) {
    byte[] firstBytes = first.getBytes(StandardCharsets.UTF_8);
    byte[] secondBytes = second.getBytes(StandardCharsets.UTF_8);
    int commonLength = Math.min(firstBytes.length, secondBytes.length);
    for (int index = 0; index < commonLength; index++) {
      int comparison =
          Integer.compare(
              Byte.toUnsignedInt(firstBytes[index]), Byte.toUnsignedInt(secondBytes[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(firstBytes.length, secondBytes.length);
  }

  private static byte[] frame(String domain, ImmutableBytes content) {
    return concatenate(
        frame(domain.getBytes(StandardCharsets.UTF_8)), frame(content.copyToByteArray()));
  }

  private static byte[] frame(byte[] bytes) {
    return ByteBuffer.allocate(Long.BYTES + bytes.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(bytes.length)
        .put(bytes)
        .array();
  }

  private static byte[] concatenate(byte[] first, byte[] second) {
    byte[] result = new byte[first.length + second.length];
    System.arraycopy(first, 0, result, 0, first.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }

  private static String sha256Hex(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }
}
