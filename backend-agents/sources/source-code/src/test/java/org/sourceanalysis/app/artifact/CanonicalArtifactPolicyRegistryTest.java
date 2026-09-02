package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class CanonicalArtifactPolicyRegistryTest {

  @Test
  void loadsCanonicalRegistryAndResolvesExactPolicyWithoutAPath() throws Exception {
    CanonicalJsonCodec codec = new CanonicalJsonCodec();
    ObjectNode documentWithoutRegistryId = new ObjectMapper().createObjectNode();
    documentWithoutRegistryId.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode policies = documentWithoutRegistryId.putArray("policies");
    ObjectNode policy = policies.addObject();
    policy.put("artifactType", "PROGRAM_GRAPHS_CALL_GRAPH");
    policy.put("schemaVersion", "program-graphs-call-graph-v1");
    policy.put("artifactIdPrefix", "program-graphs-call-graph");
    policy.put("mediaType", "application/json");
    policy.put("envelopeKind", "MODULE_ARTIFACT_JSON");
    policy.put("emptyJsonlAllowed", false);
    policy.put("publicContentExposure", "METADATA_ONLY");

    String registryId =
        "artifact-policy-registry:"
            + sha256Hex(
                concat(
                    frame("canonical-artifact-policy-registry-id-v2"),
                    frame(codec.encodeCanonical(documentWithoutRegistryId).copyToByteArray())));
    ObjectNode document = documentWithoutRegistryId.deepCopy();
    document.put("artifactPolicyRegistryId", registryId);
    ImmutableBytes canonicalDocument = codec.encodeCanonical(document);
    String documentSha256 = sha256Hex(canonicalDocument.copyToByteArray());

    Class<?> registryType =
        classOrNull("org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry");
    assertThat(registryType).as("the public path-free policy registry type must exist").isNotNull();
    if (registryType == null) {
      return;
    }

    Method load =
        methodOrNull(registryType, "load", ImmutableBytes.class, CanonicalJsonCodec.class);
    assertThat(load)
        .as("load(ImmutableBytes, CanonicalJsonCodec) must be the public construction seam")
        .isNotNull();
    if (load == null) {
      return;
    }

    Object registry = load.invoke(null, canonicalDocument, codec);
    Method reference = registryType.getMethod("reference");
    Object registryReference = reference.invoke(registry);
    assertThat(valueOf(registryReference, "artifactId")).isEqualTo(registryId);
    assertThat(valueOf(registryReference, "sha256")).isEqualTo(documentSha256);

    Class<?> keyType = classOrNull("org.sourceanalysis.app.artifact.ArtifactPolicyKey");
    assertThat(keyType).as("the exact policy lookup key must be public").isNotNull();
    if (keyType == null) {
      return;
    }
    Constructor<?> keyConstructor = keyType.getConstructor(String.class, String.class);
    Object key =
        keyConstructor.newInstance("PROGRAM_GRAPHS_CALL_GRAPH", "program-graphs-call-graph-v1");
    Method resolve = registryType.getMethod("resolve", keyType);
    Object resolvedPolicy = resolve.invoke(registry, key);

    assertThat(valueOf(resolvedPolicy, "key")).isEqualTo(String.valueOf(key));
    assertThat(valueOf(resolvedPolicy, "artifactIdPrefix")).isEqualTo("program-graphs-call-graph");
    assertThat(canonicalValue(resolvedPolicy, "mediaType")).isEqualTo("APPLICATION_JSON");
    assertThat(canonicalValue(resolvedPolicy, "envelopeKind")).isEqualTo("MODULE_ARTIFACT_JSON");
    assertThat(valueOf(resolvedPolicy, "emptyJsonlAllowed")).isEqualTo("false");
    assertThat(canonicalValue(resolvedPolicy, "publicContentExposure")).isEqualTo("METADATA_ONLY");
  }

  private static Class<?> classOrNull(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException missing) {
      return null;
    }
  }

  private static Method methodOrNull(Class<?> owner, String name, Class<?>... parameterTypes) {
    try {
      return owner.getMethod(name, parameterTypes);
    } catch (NoSuchMethodException missing) {
      return null;
    }
  }

  private static String valueOf(Object owner, String accessor) throws Exception {
    return String.valueOf(owner.getClass().getMethod(accessor).invoke(owner));
  }

  private static String canonicalValue(Object owner, String accessor) throws Exception {
    Object value = owner.getClass().getMethod(accessor).invoke(owner);
    return value instanceof Enum<?> enumeration ? enumeration.name() : String.valueOf(value);
  }

  private static byte[] frame(String text) {
    return frame(text.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] bytes) {
    return ByteBuffer.allocate(Long.BYTES + bytes.length).putLong(bytes.length).put(bytes).array();
  }

  private static byte[] concat(byte[] first, byte[] second) {
    byte[] joined = new byte[first.length + second.length];
    System.arraycopy(first, 0, joined, 0, first.length);
    System.arraycopy(second, 0, joined, first.length, second.length);
    return joined;
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
