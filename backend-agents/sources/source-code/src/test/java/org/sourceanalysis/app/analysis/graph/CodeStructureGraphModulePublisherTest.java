package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;

class CodeStructureGraphModulePublisherTest {

  @TempDir Path temporaryDirectory;

  @Test
  void persistsAndFreshReopensTheCodeStructureDraftBeforeLaterGraphsCanReadIt() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
    ArtifactControls controls = controls(policies);
    CodeStructureSource source = source(controls);
    CodeStructureDiscovery discovery = discovery();
    CodeStructureGraphDraft draft = draft(source, discovery);
    AnalysisStepModuleAddress destination =
        new AnalysisStepModuleAddress(
            AnalysisRunId.parse("analysis-run:" + "1".repeat(64)),
            AnalysisStepKey.PROGRAM_GRAPHS,
            1,
            "code-structure");

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      FileSystemCanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(2, 100_000, 200_000, 8));

      CodeStructureGraphDraftReference reference =
          new CodeStructureGraphModulePublisher(store).publish(destination, source, discovery, draft);

      var reopened = store.reopen(reference.publication());
      assertThat(reopened.receipt().address()).isEqualTo(destination);
      assertThat(reopened.receipt().status()).isEqualTo(ModuleCompletionStatus.SUCCEEDED);
      assertThat(reopened.receipt().controls()).isEqualTo(controls);
      assertThat(reopened.receipt().upstreamArtifacts())
          .containsExactlyInAnyOrder(
              discovery.applicationProfileRef(),
              discovery.capabilityReportRef(),
              discovery.entryPointsRef(),
              discovery.mapperCatalogRef(),
              source.sourceInventoryRef(),
              source.verifiedSnapshotRef(),
              draft.graphProfileRef());
      assertThat(reopened.receipt().upstreamArtifacts())
          .isSortedAccordingTo(
              java.util.Comparator.comparing(value -> value.artifactId().value()));
      assertThat(reopened.payloads())
          .singleElement()
          .satisfies(
              payload -> {
                assertThat(payload.descriptor().fileName()).isEqualTo("code-structure-draft.json");
                assertThat(payload.descriptor().artifactType())
                    .isEqualTo("PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT");
                assertThat(payload.descriptor().schemaVersion())
                    .isEqualTo(CodeStructureGraphDraft.SCHEMA_VERSION);
                JsonNode body = canonicalJson.parseCanonical(payload.canonicalUtf8()).get("payload");
                assertThat(body.get("graphKind").textValue()).isEqualTo("CODE_STRUCTURE");
                assertThat(body.get("snapshotId").textValue()).isEqualTo(source.snapshotId());
                assertThat(body.get("nodes")).hasSize(1);
              });
    }
  }

  private static CodeStructureGraphDraft draft(
      CodeStructureSource source, CodeStructureDiscovery discovery) {
    ArtifactId node = id("program-node", "type");
    ArtifactId provenance = id("provenance", "type");
    CodeStructureGraphProfile profile =
        new CodeStructureGraphProfile(reference("graph-profile", "code-structure-v1"));
    return new CodeStructureGraphDraft(
        CodeStructureGraphDraft.SCHEMA_VERSION,
        ProgramGraphKind.CODE_STRUCTURE,
        id("program-graph", "code-structure"),
        source.snapshotId(),
        discovery.applicationProfileId(),
        profile.graphProfileRef(),
        discovery.entryIds(),
        List.of(
            new DraftProgramNode(
                node, ProgramNodeKind.TYPE, "com.example.DepotHead", discovery.entryIds(), List.of(provenance))),
        List.of(),
        new GraphCoverage(List.of(node), List.of(node), List.of(), List.of(), List.of(), true));
  }

  private static CodeStructureSource source(ArtifactControls controls) {
    return new CodeStructureSource(
        "snapshot:" + "2".repeat(64),
        "COMPLETE_CAPTURE",
        true,
        reference("source-inventory", "inventory"),
        reference("verified-snapshot", "snapshot"),
        controls,
        List.of(
            new CodeStructureSourceDocument(
                id("file", "source"),
                "src/main/java/com/example/DepotHead.java",
                org.sourceanalysis.app.artifact.ImmutableBytes.copyOf(
                    "package com.example; class DepotHead {}".getBytes(StandardCharsets.UTF_8)),
                new Sha256Digest(digest("package com.example; class DepotHead {}")))));
  }

  private static CodeStructureDiscovery discovery() {
    return new CodeStructureDiscovery(
        id("application-profile", "profile"),
        reference("application-profile", "profile"),
        reference("capability-report", "capability"),
        reference("entry-points", "entries"),
        reference("mapper-catalog", "mappers"),
        List.of(id("entry", "depot-head")));
  }

  private static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(
        new Sha256Digest("a".repeat(64)),
        new Sha256Digest("b".repeat(64)),
        new Sha256Digest("c".repeat(64)),
        null,
        policies.reference());
  }

  private static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(id(prefix, value), new Sha256Digest(digest(value)));
  }

  private static ArtifactId id(String prefix, String value) {
    return ArtifactId.parse(prefix + ":" + digest(value));
  }

  private static CanonicalArtifactPolicyRegistry policies(CanonicalJsonCodec canonicalJson) {
    ObjectNode withoutId = JsonNodeFactory.instance.objectNode();
    withoutId.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode entries = withoutId.putArray("policies");
    entries
        .addObject()
        .put("artifactType", "PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT")
        .put("schemaVersion", CodeStructureGraphDraft.SCHEMA_VERSION)
        .put("artifactIdPrefix", "code-structure-graph")
        .put("mediaType", "application/json")
        .put("envelopeKind", "MODULE_ARTIFACT_JSON")
        .put("emptyJsonlAllowed", false)
        .put("publicContentExposure", "PATH_FREE_COMPLETE_UTF8");
    withoutId.put(
        "artifactPolicyRegistryId",
        "artifact-policy-registry:"
            + digest(
                concatenate(
                    frame("canonical-artifact-policy-registry-id-v2"),
                    frame(canonicalJson.encodeCanonical(withoutId).copyToByteArray()))));
    return CanonicalArtifactPolicyRegistry.load(
        canonicalJson.encodeCanonical(withoutId), canonicalJson);
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

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
