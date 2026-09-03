package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** RED for exact seven-artifact upstream closure of the M1 candidate module. */
class FactCandidateExactUpstreamTest {

  @TempDir Path temporaryDirectory;

  @Test
  void exposesExactDiscoveryAndGraphRootsAndRejectsAValidDecoyUpstreamReference()
      throws Exception {
    Path storeRoot = temporaryDirectory.resolve("candidate-upstream-store");
    Files.createDirectory(storeRoot);
    try (ProgramGraphsPublicFixture fixture =
            ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("graph-input"));
        RunStoreHandle handle = RunStoreBootstrap.openForTest(storeRoot)) {
      CanonicalAnalysisStepArtifactStore steps = fixture.stepArtifacts();
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              handle,
              new CanonicalJsonCodec(),
              fixture.artifactPolicies(),
              new ArtifactStoreLimits(8, 2_000_000, 4_000_000, 16));
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(steps, fixture.sourceReader())
              .reopen(fixture.sourceInventory(), fixture.applicationDiscovery(), fixture.programGraphs());

      List<ArtifactReference> expectedDiscovery = discoveryReferences(fixture);
      List<ArtifactReference> upstream = candidateModuleUpstreamArtifacts(inputs);
      assertThat(upstream).hasSize(7);
      assertThat(upstream).containsExactlyElementsOf(sorted(concatenate(inputs.sourceGraphRoots(), expectedDiscovery)));
      assertThat(upstream).contains(expectedDiscovery.get(0), expectedDiscovery.get(1));

      FactCandidateSet candidateSet =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());
      List<ArtifactReference> decoyUpstream = new ArrayList<>(upstream);
      int discoveryIndex =
          java.util.stream.IntStream.range(0, upstream.size())
              .filter(index -> expectedDiscovery.contains(upstream.get(index)))
              .findFirst()
              .orElseThrow();
      decoyUpstream.set(discoveryIndex, validDecoy(decoyUpstream.get(discoveryIndex)));
      decoyUpstream = sorted(decoyUpstream);

      AnalysisStepModuleAddress destination =
          new AnalysisStepModuleAddress(
              fixture.programGraphs().publication().address().runId(),
              AnalysisStepKey.PROVEN_CODE_FACTS,
              1,
              "candidates");
      ModulePublicationReference publication =
          publishStrict(modules, destination, inputs, candidateSet);

      // A malformed publication is installed directly through the canonical store. It must not
      // be generated through the publisher because the publisher's strict seam owns the complete
      // upstream set and would reject the decoy before the persisted-reader check.
      ReopenedModulePublication validPublication = modules.reopen(publication);
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      ObjectNode decoyEnvelope =
          ((ObjectNode) json.parseCanonical(validPublication.payloads().get(0).canonicalUtf8()))
              .deepCopy();
      replaceUpstream(decoyEnvelope, decoyUpstream);
      AnalysisStepModuleAddress decoyDestination =
          new AnalysisStepModuleAddress(
              org.sourceanalysis.app.artifact.AnalysisRunId.parse(
                  "analysis-run:" + digest("fact-candidate-exact-upstream-decoy-run")),
              AnalysisStepKey.PROVEN_CODE_FACTS,
              1,
              "candidates");
      ((ObjectNode) decoyEnvelope.path("producer").path("address"))
          .put("runId", decoyDestination.runId().value());
      String decoyArtifactId = artifactId(decoyEnvelope, json);
      decoyEnvelope.put("artifactId", decoyArtifactId);
      CanonicalModulePayload decoyPayload =
          new CanonicalModulePayload(
              "fact-candidate-set.json",
              "PROVEN_CODE_FACTS_FACT_CANDIDATE_SET",
              "proven-code-facts-fact-candidate-set-v2",
              ArtifactId.parse(decoyArtifactId),
              CanonicalMediaType.APPLICATION_JSON,
              json.encodeCanonical(decoyEnvelope));
      InstalledModulePublication decoyPublication =
          modules.install(
              new ModuleInstallRequest(
                  decoyDestination,
                  "v2",
                  decoyUpstream,
                  fixture.artifactControls(),
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  List.of(decoyPayload)));

      assertThatThrownBy(
              () ->
                  new PersistedFactCandidateSetReader(modules)
                      .reopen(
                          decoyPublication.reference(),
                          inputs,
                          FactRegistry.standardJavaBoundary()))
          .isInstanceOf(FactCandidateReferenceException.class)
          .hasMessage("PROOF_PACK_REFERENCE_BROKEN");
    }
  }

  private static ModulePublicationReference publishStrict(
      CanonicalModuleArtifactStore modules,
      AnalysisStepModuleAddress destination,
      FactCandidateInputs inputs,
      FactCandidateSet candidateSet)
      throws Exception {
    java.lang.reflect.Method publish;
    try {
      publish =
          FactCandidateSetModulePublisher.class.getMethod(
              "publish",
              AnalysisStepModuleAddress.class,
              FactCandidateInputs.class,
              FactCandidateSet.class);
    } catch (NoSuchMethodException missing) {
      throw new AssertionError("FACT_CANDIDATE_STRICT_PUBLISHER_SEAM_NOT_IMPLEMENTED", missing);
    }
    try {
      Object result = publish.invoke(new FactCandidateSetModulePublisher(modules), destination, inputs, candidateSet);
      if (!(result instanceof ModulePublicationReference reference)) {
        throw new AssertionError("FACT_CANDIDATE_STRICT_PUBLISHER_RETURN_TYPE_INVALID");
      }
      return reference;
    } catch (java.lang.reflect.InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("FACT_CANDIDATE_STRICT_PUBLISHER_FAILED", cause);
    }
  }

  private static void replaceUpstream(
      ObjectNode envelope, List<ArtifactReference> replacement) {
    ArrayNode upstream = envelope.putArray("upstreamArtifacts");
    replacement.forEach(
        reference ->
            upstream
                .addObject()
                .put("artifactId", reference.artifactId().value())
                .put("sha256", reference.sha256().value()));
  }

  private static String artifactId(ObjectNode envelope, CanonicalJsonCodec json) {
    ObjectNode withoutArtifactId = envelope.deepCopy();
    withoutArtifactId.remove("artifactId");
    return "proven-code-facts-fact-candidate-set:"
        + digest(
            concatenate(
                frame("canonical-module-artifact-id-v1"),
                frame("proven-code-facts-fact-candidate-set-v2"),
                frame("PROVEN_CODE_FACTS_FACT_CANDIDATE_SET"),
                frame(json.encodeCanonical(withoutArtifactId).copyToByteArray())));
  }

  private static List<ArtifactReference> candidateModuleUpstreamArtifacts(
      FactCandidateInputs inputs) throws Exception {
    java.lang.reflect.Method accessor;
    try {
      accessor = FactCandidateInputs.class.getMethod("candidateModuleUpstreamArtifacts");
    } catch (NoSuchMethodException missing) {
      throw new AssertionError("FACT_CANDIDATE_UPSTREAM_ACCESSOR_NOT_IMPLEMENTED", missing);
    }
    Object result;
    try {
      result = accessor.invoke(inputs);
    } catch (java.lang.reflect.InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("FACT_CANDIDATE_UPSTREAM_ACCESSOR_FAILED", cause);
    }
    if (!(result instanceof List<?> values)) {
      throw new AssertionError("FACT_CANDIDATE_UPSTREAM_ACCESSOR_RETURN_TYPE_INVALID");
    }
    List<ArtifactReference> references = new ArrayList<>();
    for (Object value : values) {
      if (!(value instanceof ArtifactReference reference)) {
        throw new AssertionError("FACT_CANDIDATE_UPSTREAM_ACCESSOR_ELEMENT_TYPE_INVALID");
      }
      references.add(reference);
    }
    return List.copyOf(references);
  }

  private static List<ArtifactReference> discoveryReferences(ProgramGraphsPublicFixture fixture) {
    return sorted(
        fixture.stepArtifacts().reopen(fixture.applicationDiscovery().publication()).semanticPayloads().stream()
            .filter(
                payload ->
                    payload.descriptor().fileName().equals("capability-report.json")
                        || payload.descriptor().fileName().equals("entry-points.jsonl"))
            .map(
                payload ->
                    new ArtifactReference(
                        payload.descriptor().artifactId(), payload.descriptor().sha256()))
            .toList());
  }

  private static ArtifactReference validDecoy(ArtifactReference replaced) {
    String prefix = replaced.artifactId().value().substring(0, replaced.artifactId().value().indexOf(':'));
    String digest = digest("fact-candidate-valid-upstream-decoy:" + prefix);
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + digest), new Sha256Digest(digest));
  }

  private static List<ArtifactReference> concatenate(
      List<ArtifactReference> first, List<ArtifactReference> second) {
    List<ArtifactReference> result = new ArrayList<>(first);
    result.addAll(second);
    return result;
  }

  private static List<ArtifactReference> sorted(List<ArtifactReference> references) {
    return references.stream()
        .sorted(
            Comparator.comparing(
                    (ArtifactReference reference) -> reference.artifactId().value(),
                    FactCandidateExactUpstreamTest::compareUtf8)
                .thenComparing(
                    reference -> reference.sha256().value(), FactCandidateExactUpstreamTest::compareUtf8))
        .toList();
  }

  private static int compareUtf8(String left, String right) {
    byte[] first = left.getBytes(StandardCharsets.UTF_8);
    byte[] second = right.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(first.length, second.length); index++) {
      int comparison = Integer.compare(Byte.toUnsignedInt(first[index]), Byte.toUnsignedInt(second[index]));
      if (comparison != 0) return comparison;
    }
    return Integer.compare(first.length, second.length);
  }

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new AssertionError(unavailable);
    }
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] bytes) {
    return ByteBuffer.allocate(Long.BYTES + bytes.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(bytes.length)
        .put(bytes)
        .array();
  }

  private static byte[] concatenate(byte[]... values) {
    int length = 0;
    for (byte[] value : values) length += value.length;
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }
}
