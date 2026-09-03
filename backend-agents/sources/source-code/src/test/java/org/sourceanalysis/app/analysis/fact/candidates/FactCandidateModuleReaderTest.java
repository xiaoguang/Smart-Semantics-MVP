package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;

/** RED for fresh reopening of the persisted M1 Fact-candidate module artifact. */
class FactCandidateModuleReaderTest {

  private static final String READER_CLASS =
      "org.sourceanalysis.app.analysis.fact.candidates.PersistedFactCandidateSetReader";
  private static final String ARTIFACT_TYPE = "PROVEN_CODE_FACTS_FACT_CANDIDATE_SET";
  private static final String SCHEMA_VERSION = "proven-code-facts-fact-candidate-set-v1";

  @TempDir Path temporaryDirectory;

  @Test
  void freshReopenReturnsTypedCandidateSetAndRejectsPersistedPayloadTamper() throws Exception {
    Path storeRoot = temporaryDirectory.resolve("candidate-reader-store");
    Files.createDirectory(storeRoot);
    try (ProgramGraphsPublicFixture fixture =
            ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("graph-input"));
        RunStoreHandle handle = RunStoreBootstrap.openForTest(storeRoot)) {
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      // Reuse the fixture's registry and controls so publication and fresh input have one
      // canonical control identity.
      CanonicalArtifactPolicyRegistry policies = fixture.artifactPolicies();
      CanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, json, policies, new ArtifactStoreLimits(8, 2_000_000, 4_000_000, 16));
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(fixture.sourceInventory(), fixture.applicationDiscovery(), fixture.programGraphs());
      FactRegistry registry = FactRegistry.standardJavaBoundary();
      FactCandidateSet expected = new FactCandidateEnumerator().enumerate(inputs, registry);
      AnalysisStepModuleAddress destination =
          new AnalysisStepModuleAddress(
              fixture.programGraphs().publication().address().runId(),
              AnalysisStepKey.PROVEN_CODE_FACTS,
              1,
              "candidates");

      ModulePublicationReference reference =
          invokePublisher(store, destination, inputs, expected);
      FactCandidateSet reopened = invokeReader(store, reference, inputs, registry);
      assertThat(reopened).isEqualTo(expected);
      assertThat(reopened.candidateSetId()).isEqualTo(expected.candidateSetId());
      assertThat(reopened.candidates()).containsExactlyElementsOf(expected.candidates());
      assertThat(reopened.denominator()).isEqualTo(expected.denominator());

      Path payloadPath = payloadPath(storeRoot, reference);
      ObjectNode envelope =
          (ObjectNode)
              json.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(payloadPath))).deepCopy();
      ObjectNode payload = (ObjectNode) envelope.path("payload");
      payload.put(
          "candidateSetId",
          "proven-code-facts-fact-candidate-set:0000000000000000000000000000000000000000000000000000000000000000");
      Files.write(payloadPath, json.encodeCanonical(envelope).copyToByteArray());

      assertThatThrownBy(() -> invokeReader(store, reference, inputs, registry))
          .isInstanceOf(AssertionError.class)
          .hasMessage("FACT_CANDIDATE_MODULE_REOPEN_FAILED");
    }
  }

  private static FactCandidateSet invokeReader(
      CanonicalModuleArtifactStore store,
      ModulePublicationReference reference,
      FactCandidateInputs inputs,
      FactRegistry registry)
      throws Exception {
    Class<?> readerType;
    try {
      readerType = Class.forName(READER_CLASS);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("FACT_CANDIDATE_MODULE_READER_NOT_IMPLEMENTED", missing);
    }
    Constructor<?> constructor;
    try {
      constructor = readerType.getConstructor(CanonicalModuleArtifactStore.class);
    } catch (NoSuchMethodException missing) {
      throw new AssertionError("FACT_CANDIDATE_MODULE_READER_CONSTRUCTOR_MISSING", missing);
    }
    Method reopen;
    try {
      reopen =
          readerType.getMethod(
              "reopen", ModulePublicationReference.class, FactCandidateInputs.class, FactRegistry.class);
    } catch (NoSuchMethodException missing) {
      throw new AssertionError("FACT_CANDIDATE_MODULE_READER_SEAM_MISSING", missing);
    }
    try {
      Object result = reopen.invoke(constructor.newInstance(store), reference, inputs, registry);
      if (!(result instanceof FactCandidateSet candidateSet)) {
        throw new AssertionError("FACT_CANDIDATE_MODULE_READER_RETURN_TYPE_INVALID");
      }
      return candidateSet;
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("FACT_CANDIDATE_MODULE_REOPEN_FAILED", cause);
    }
  }

  private static ModulePublicationReference invokePublisher(
      CanonicalModuleArtifactStore store,
      AnalysisStepModuleAddress destination,
      FactCandidateInputs inputs,
      FactCandidateSet candidateSet)
      throws Exception {
    Class<?> publisherType =
        Class.forName("org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSetModulePublisher");
    Constructor<?> constructor = publisherType.getConstructor(CanonicalModuleArtifactStore.class);
    Method publish =
        publisherType.getMethod(
            "publish",
            AnalysisStepModuleAddress.class,
            FactCandidateInputs.class,
            FactCandidateSet.class);
    try {
      Object result = publish.invoke(constructor.newInstance(store), destination, inputs, candidateSet);
      return (ModulePublicationReference) result;
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("FACT_CANDIDATE_MODULE_PUBLICATION_FAILED", cause);
    }
  }

  private static Path payloadPath(Path storeRoot, ModulePublicationReference reference) {
    return storeRoot
        .resolve("runs")
        .resolve(reference.address().runId().value().replace(":", "--"))
        .resolve("steps")
        .resolve(AnalysisStepKey.PROVEN_CODE_FACTS.directoryName())
        .resolve("modules")
        .resolve("01-candidates")
        .resolve("fact-candidate-set.json");
  }

}
