package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

/** Contract for invalidating old Step07 catalog jobs after a prompt/schema change. */
class BusinessProcessSemanticFingerprintV2Test {

  private static final ModelRuntimeIdentityV1 IDENTITY =
      new ModelRuntimeIdentityV1("scripted", "fixture-model", "high", "read-only");
  private static final String QUOTA_SCOPE = "scripted-account";

  @TempDir Path temporaryDirectory;

  @Test
  void semanticV2CatalogFingerprintCannotMatchAStoredV1Step07Pair() throws Exception {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    ProcessDiscoveryProfile profile =
        new ProcessDiscoveryProfile(8, 4, 8, 8_000, 64_000, 16_000, 4, 32, 2_000);
    ObjectNode input = JsonNodeFactory.instance.objectNode();
    input.putArray("activityIndexCards").addObject().put("activityId", "activity-1");
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    ModelJobProviderBinding binding = binding();

    String actualFingerprint =
        invokeFingerprint(
            "BUSINESS_CATALOG_DRAFT", "BUSINESS_CATALOG_REVIEW", input, schema, profile, binding);
    String legacyFingerprint =
        legacyFingerprint(
            "BUSINESS_CATALOG_DRAFT",
            "BUSINESS_CATALOG_REVIEW",
            input,
            schema,
            profile,
            binding,
            json);

    assertThat(actualFingerprint)
        .as("changing the Step07 prompt/schema contract must change the job fingerprint")
        .isNotEqualTo(legacyFingerprint);

    Path journal = Files.createDirectory(temporaryDirectory.resolve("legacy-job"));
    AnalysisRunId sourceBatch = AnalysisRunId.parse("analysis-run:" + "a".repeat(64));
    String jobKey = "business-catalog";
    new PrivateModelJobResultStore(journal, sourceBatch, "process-catalog")
        .write(jobKey, completePair(legacyFingerprint));

    Optional<ObjectNode> reused =
        new PrivateModelJobResultStore(journal, sourceBatch, "process-catalog")
            .readCompleted(jobKey, actualFingerprint, QUOTA_SCOPE, IDENTITY);
    assertThat(reused).as("a v1 Step07 pair must be rejected instead of reused as v2").isEmpty();
  }

  private static String invokeFingerprint(
      String draftKind,
      String reviewKind,
      ObjectNode input,
      ObjectNode schema,
      ProcessDiscoveryProfile profile,
      ModelJobProviderBinding binding)
      throws Exception {
    Method method =
        DefaultBusinessProcessDiscovery.class.getDeclaredMethod(
            "inputFingerprint",
            String.class,
            String.class,
            ObjectNode.class,
            ObjectNode.class,
            ProcessDiscoveryProfile.class,
            ModelJobProviderBinding.class);
    method.setAccessible(true);
    try {
      return (String)
          method.invoke(
              new DefaultBusinessProcessDiscovery(zeroProvider()),
              draftKind,
              reviewKind,
              input,
              schema,
              profile,
              binding);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new AssertionError(cause);
    }
  }

  private static String legacyFingerprint(
      String draftKind,
      String reviewKind,
      ObjectNode input,
      ObjectNode schema,
      ProcessDiscoveryProfile profile,
      ModelJobProviderBinding binding,
      CanonicalJsonCodec json) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", "business-process-job-input-fingerprint-v1");
    value.put("moduleVersion", "repository-business-process-catalog-v1");
    value.put("providerBindingKey", binding.key());
    value.put("quotaScope", binding.quotaScope());
    value.put("inputSha256", sha256(json.encodeCanonical(input)));
    value.put("draftInstructions", readV1Prompt(promptResource(draftKind)));
    value.put("reviewInstructions", readV1Prompt(promptResource(reviewKind)));
    value.put("outputSchemaSha256", sha256(json.encodeCanonical(schema)));
    value.put("maxModelInputBytes", profile.maxModelInputBytes());
    value.put("maxModelOutputBytes", profile.maxModelOutputBytes());
    ObjectNode identity = value.putObject("expectedRuntimeIdentity");
    identity.put("upstreamProvider", IDENTITY.upstreamProvider());
    identity.put("model", IDENTITY.model());
    identity.put("reasoningEffort", IDENTITY.reasoningEffort());
    identity.put("sandbox", IDENTITY.sandbox());
    return sha256(json.encodeCanonical(value));
  }

  private static String promptResource(String taskKind) {
    return switch (taskKind) {
      case "BUSINESS_CATALOG_DRAFT" -> "business-catalog-draft-v1.txt";
      case "BUSINESS_CATALOG_REVIEW" -> "business-catalog-review-v1.txt";
      default -> throw new IllegalArgumentException("unexpected legacy task kind: " + taskKind);
    };
  }

  private static String readV1Prompt(String resourceName) {
    try (InputStream stream =
        BusinessProcessSemanticFingerprintV2Test.class.getResourceAsStream(
            "/org/sourceanalysis/app/analysis/knowledge/" + resourceName)) {
      if (stream == null) {
        fail("the historical v1 prompt resource is required for the invalidation fixture");
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
    } catch (IOException failure) {
      throw new AssertionError("cannot read historical prompt resource", failure);
    }
  }

  private static String sha256(ImmutableBytes bytes) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.copyToByteArray()));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static ModelJobProviderBinding binding() {
    return new ModelJobProviderBinding("scripted", QUOTA_SCOPE, 1, zeroProvider(), IDENTITY);
  }

  private static StructuredModelProvider zeroProvider() {
    return request -> {
      throw new AssertionError("fingerprint tests must not call a model provider");
    };
  }

  private static ObjectNode completePair(String fingerprint) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", "model-job-reviewed-result-v2");
    value.put("status", "COMPLETED");
    value.put("inputFingerprint", fingerprint);
    value.put("providerBindingKey", "scripted");
    value.put("quotaScope", QUOTA_SCOPE);
    value.put("phase", "process-catalog");
    ObjectNode identity = value.putObject("runtimeIdentity");
    identity.put("upstreamProvider", IDENTITY.upstreamProvider());
    identity.put("model", IDENTITY.model());
    identity.put("reasoningEffort", IDENTITY.reasoningEffort());
    identity.put("sandbox", IDENTITY.sandbox());
    value.putObject("draft").put("resultId", "draft");
    value.putObject("review").put("resultId", "review");
    return value;
  }
}
