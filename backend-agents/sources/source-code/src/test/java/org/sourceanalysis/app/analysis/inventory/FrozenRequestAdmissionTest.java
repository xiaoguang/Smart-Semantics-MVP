package org.sourceanalysis.app.analysis.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

class FrozenRequestAdmissionTest {

  private static final CanonicalJsonCodec CANONICAL_JSON = new CanonicalJsonCodec();

  @Test
  void admitsTheBoundedDepotHeadCaptureAsOneCanonicalAndPathFreeRequest() throws IOException {
    ArtifactReference frozenRequest = reference("frozen-request", '1');
    ArtifactReference profile = reference("capability-profile", '2');
    ArtifactReference budget = reference("resource-budget", '3');
    ArtifactReference captureReceipt = reference("capture-receipt", '4');
    ArtifactReference snapshotManifest = reference("snapshot-manifest", '5');
    ArtifactId sourceRegistration = ArtifactId.parse("source-registration:" + "6".repeat(64));
    List<CapturedRegularFile> inventory = depotHeadInventory();
    CaptureReceiptView receipt =
        new CaptureReceiptView(
            sourceRegistration,
            "https://gitee.com/jishenghua/JSH_ERP.git",
            "8c30ce7861570458920175e200bb2a6442713580",
            frozenRequest,
            captureReceipt,
            snapshotManifest,
            InventoryScope.boundedPathSet("jshERP-boot"),
            false,
            inventory);
    ProfileView profileView = new ProfileView(profile, budget, 8, 8_192L);

    byte[] requestBytes = analysisRunRequest(sourceRegistration, frozenRequest, profile, budget);
    AdmittedSourceRequest admitted =
        new FrozenRequestAdmission().admit(requestBytes, receipt, profileView);

    assertThat(admitted.requestIdentity())
        .isEqualTo(
            "run-request:"
                + sha256Hex(concatenate(frame("analysis-run-request-id-v2"), frame(requestBytes))));
    assertThat(admitted.sourceRegistrationId()).isEqualTo(sourceRegistration);
    assertThat(admitted.originRepositoryUrl())
        .isEqualTo("https://gitee.com/jishenghua/JSH_ERP.git");
    assertThat(admitted.originRevision()).isEqualTo("8c30ce7861570458920175e200bb2a6442713580");
    assertThat(admitted.inventoryScope()).isEqualTo(InventoryScope.boundedPathSet("jshERP-boot"));
    assertThat(admitted.repositoryCompletionEligible()).isFalse();
    assertThat(admitted.declaredPathCount()).isEqualTo(8);
    assertThat(admitted.files()).extracting(CapturedRegularFile::path).isSorted();
    assertThat(admitted.analyzableTextFileIds()).hasSize(8);
    assertThat(admitted.nonAnalyzableMediaFileIds()).isEmpty();
    assertThat(admitted.files())
        .allSatisfy(file -> assertThat(file.textEncoding()).isEqualTo("UTF-8"));
  }

  @Test
  void rejectsARepositoryPathEscapeAtTheAdmissionBoundary() {
    ArtifactReference frozenRequest = reference("frozen-request", '1');
    ArtifactReference profile = reference("capability-profile", '2');
    ArtifactReference budget = reference("resource-budget", '3');
    ArtifactId sourceRegistration = ArtifactId.parse("source-registration:" + "6".repeat(64));
    CaptureReceiptView receipt =
        new CaptureReceiptView(
            sourceRegistration,
            "https://gitee.com/jishenghua/JSH_ERP.git",
            "8c30ce7861570458920175e200bb2a6442713580",
            frozenRequest,
            reference("capture-receipt", '4'),
            reference("snapshot-manifest", '5'),
            InventoryScope.boundedPathSet("jshERP-boot"),
            false,
            List.of(
                new CapturedRegularFile(
                    ArtifactId.parse("file:" + "d".repeat(64)),
                    "../outside.java",
                    "100644",
                    "text/x-java-source",
                    1L,
                    Sha256Digest.parse("e".repeat(64)),
                    SourceAnalysisDisposition.ANALYZABLE_TEXT,
                    "UTF-8")));

    assertThatThrownBy(
            () ->
                new FrozenRequestAdmission()
                    .admit(
                        analysisRunRequest(sourceRegistration, frozenRequest, profile, budget),
                        receipt,
                        new ProfileView(profile, budget, 8, 8_192L)))
        .isInstanceOfSatisfying(
            FrozenRequestAdmissionException.class,
            failure -> assertThat(failure.code()).isEqualTo("SOURCE_PATH_INVALID"));
  }

  @Test
  void rejectsAnEmptyCapturedInventoryAtTheAdmissionBoundary() {
    ArtifactReference frozenRequest = reference("frozen-request", '1');
    ArtifactReference profile = reference("capability-profile", '2');
    ArtifactReference budget = reference("resource-budget", '3');
    ArtifactId sourceRegistration = ArtifactId.parse("source-registration:" + "6".repeat(64));
    CaptureReceiptView receipt =
        new CaptureReceiptView(
            sourceRegistration,
            "https://gitee.com/jishenghua/JSH_ERP.git",
            "8c30ce7861570458920175e200bb2a6442713580",
            frozenRequest,
            reference("capture-receipt", '4'),
            reference("snapshot-manifest", '5'),
            InventoryScope.boundedPathSet("jshERP-boot"),
            false,
            List.of());

    assertThatThrownBy(
            () ->
                new FrozenRequestAdmission()
                    .admit(
                        analysisRunRequest(sourceRegistration, frozenRequest, profile, budget),
                        receipt,
                        new ProfileView(profile, budget, 8, 8_192L)))
        .isInstanceOfSatisfying(
            FrozenRequestAdmissionException.class,
            failure -> assertThat(failure.code()).isEqualTo("REQUEST_SCHEMA_INVALID"));
  }

  @Test
  void rejectsARequestWhoseSourceRegistrationDoesNotMatchTheRegisteredCapture() throws IOException {
    ArtifactReference frozenRequest = reference("frozen-request", '1');
    ArtifactReference profile = reference("capability-profile", '2');
    ArtifactReference budget = reference("resource-budget", '3');
    ArtifactId requestRegistration = ArtifactId.parse("source-registration:" + "6".repeat(64));
    CaptureReceiptView receipt =
        new CaptureReceiptView(
            ArtifactId.parse("source-registration:" + "7".repeat(64)),
            "https://gitee.com/jishenghua/JSH_ERP.git",
            "8c30ce7861570458920175e200bb2a6442713580",
            frozenRequest,
            reference("capture-receipt", '4'),
            reference("snapshot-manifest", '5'),
            InventoryScope.boundedPathSet("jshERP-boot"),
            false,
            depotHeadInventory());

    assertThatThrownBy(
            () ->
                new FrozenRequestAdmission()
                    .admit(
                        analysisRunRequest(requestRegistration, frozenRequest, profile, budget),
                        receipt,
                        new ProfileView(profile, budget, 8, 8_192L)))
        .isInstanceOfSatisfying(
            FrozenRequestAdmissionException.class,
            failure -> assertThat(failure.code()).isEqualTo("CAPTURE_IDENTITY_INVALID"));
  }

  @Test
  void admitsAWellFormedSecondReaderCandidateRoundWithoutChangingTheFrozenCapture()
      throws IOException {
    ArtifactReference frozenRequest = reference("frozen-request", '1');
    ArtifactReference profile = reference("capability-profile", '2');
    ArtifactReference budget = reference("resource-budget", '3');
    ArtifactId sourceRegistration = ArtifactId.parse("source-registration:" + "6".repeat(64));
    ObjectNode roundTwo =
        (ObjectNode)
            CANONICAL_JSON
                .parseCanonical(
                    ImmutableBytes.copyOf(
                        analysisRunRequest(sourceRegistration, frozenRequest, profile, budget)))
                .deepCopy();
    roundTwo.put("readerCandidateRound", "ROUND_2");
    appendReference(roundTwo.putObject("parentCandidateRef"), reference("candidate", '7'));
    appendReference(
        roundTwo.putArray("approvedFindingRefs").addObject(), reference("finding", '8'));
    byte[] roundTwoBytes = CANONICAL_JSON.encodeCanonical(roundTwo).copyToByteArray();
    CaptureReceiptView receipt =
        new CaptureReceiptView(
            sourceRegistration,
            "https://gitee.com/jishenghua/JSH_ERP.git",
            "8c30ce7861570458920175e200bb2a6442713580",
            frozenRequest,
            reference("capture-receipt", '4'),
            reference("snapshot-manifest", '5'),
            InventoryScope.boundedPathSet("jshERP-boot"),
            false,
            depotHeadInventory());

    AdmittedSourceRequest admitted =
        new FrozenRequestAdmission()
            .admit(roundTwoBytes, receipt, new ProfileView(profile, budget, 8, 8_192L));

    assertThat(admitted.sourceRegistrationId()).isEqualTo(sourceRegistration);
    assertThat(admitted.frozenRepositoryRequestRef()).isEqualTo(frozenRequest);
    assertThat(admitted.declaredPathCount()).isEqualTo(8);
  }

  @Test
  void canonicalizesAnUnorderedCaptureInventoryBeforeHandingItToSourceVerification()
      throws IOException {
    ArtifactReference frozenRequest = reference("frozen-request", '1');
    ArtifactReference profile = reference("capability-profile", '2');
    ArtifactReference budget = reference("resource-budget", '3');
    ArtifactId sourceRegistration = ArtifactId.parse("source-registration:" + "6".repeat(64));
    List<CapturedRegularFile> unordered = new ArrayList<>(depotHeadInventory());
    Collections.reverse(unordered);
    byte[] request = analysisRunRequest(sourceRegistration, frozenRequest, profile, budget);

    AdmittedSourceRequest ordered =
        new FrozenRequestAdmission()
            .admit(
                request,
                new CaptureReceiptView(
                    sourceRegistration,
                    "https://gitee.com/jishenghua/JSH_ERP.git",
                    "8c30ce7861570458920175e200bb2a6442713580",
                    frozenRequest,
                    reference("capture-receipt", '4'),
                    reference("snapshot-manifest", '5'),
                    InventoryScope.boundedPathSet("jshERP-boot"),
                    false,
                    depotHeadInventory()),
                new ProfileView(profile, budget, 8, 8_192L));
    AdmittedSourceRequest reordered =
        new FrozenRequestAdmission()
            .admit(
                request,
                new CaptureReceiptView(
                    sourceRegistration,
                    "https://gitee.com/jishenghua/JSH_ERP.git",
                    "8c30ce7861570458920175e200bb2a6442713580",
                    frozenRequest,
                    reference("capture-receipt", '4'),
                    reference("snapshot-manifest", '5'),
                    InventoryScope.boundedPathSet("jshERP-boot"),
                    false,
                    unordered),
                new ProfileView(profile, budget, 8, 8_192L));

    assertThat(reordered).isEqualTo(ordered);
  }

  private static byte[] analysisRunRequest(
      ArtifactId sourceRegistration,
      ArtifactReference frozenRequest,
      ArtifactReference profile,
      ArtifactReference budget) {
    ObjectNode request = JsonNodeFactory.instance.objectNode();
    request.putArray("approvedFindingRefs");
    appendReference(
        request.putObject("artifactPolicyRegistryRef"), reference("artifact-policy-registry", '8'));
    appendReference(request.putObject("candidateSeriesRef"), reference("candidate-series", '9'));
    appendReference(request.putObject("frozenRepositoryRequestRef"), frozenRequest);
    request.putNull("organizationRegistrySeedRef");
    request.putNull("parentCandidateRef");
    appendReference(request.putObject("profileBundleRef"), profile);
    appendReference(request.putObject("promptBundleRef"), reference("prompt-bundle", 'a'));
    request.put("readerCandidateRound", "ROUND_1");
    appendReference(request.putObject("resourceBudgetRef"), budget);
    appendReference(request.putObject("schemaBundleRef"), reference("schema-bundle", 'b'));
    request.put("schemaVersion", "analysis-run-request-v2");
    request.put("sourceRegistrationId", sourceRegistration.value());
    appendReference(request.putObject("toolchainRef"), reference("toolchain", 'c'));
    return CANONICAL_JSON.encodeCanonical(request).copyToByteArray();
  }

  private static void appendReference(ObjectNode node, ArtifactReference reference) {
    node.put("artifactId", reference.artifactId().value());
    node.put("sha256", reference.sha256().value());
  }

  private static ArtifactReference reference(String prefix, char digit) {
    String digest = String.valueOf(digit).repeat(64);
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + digest), Sha256Digest.parse(digest));
  }

  private static List<CapturedRegularFile> depotHeadInventory() throws IOException {
    try (var input =
        FrozenRequestAdmissionTest.class.getResourceAsStream(
            "/analysis/inventory/request-admission/depothead-eight-paths.txt")) {
      assertThat(input).as("the frozen path fixture must be present").isNotNull();
      List<String> paths =
          new String(input.readAllBytes(), StandardCharsets.UTF_8)
              .lines()
              .filter(line -> !line.isBlank())
              .toList();
      return paths.stream()
          .map(
              path ->
                  new CapturedRegularFile(
                      ArtifactId.parse(
                          "file:" + "d".repeat(63) + Integer.toHexString(paths.indexOf(path))),
                      path,
                      "100644",
                      mediaType(path),
                      1L,
                      Sha256Digest.parse("d".repeat(63) + Integer.toHexString(paths.indexOf(path))),
                      SourceAnalysisDisposition.ANALYZABLE_TEXT,
                      "UTF-8"))
          .toList();
    }
  }

  private static String mediaType(String path) {
    if (path.endsWith(".java")) {
      return "text/x-java-source";
    }
    if (path.endsWith(".xml")) {
      return "application/xml";
    }
    if (path.endsWith(".yml")) {
      return "application/yaml";
    }
    return "application/xml";
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

  private static String sha256Hex(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
