package org.sourceanalysis.app.analysis.interpretation.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskCompilerTest;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** M6 RED contract: complete program material must survive a fresh module-store reopen. */
class CrossFlowCandidateModulePublisherTest {

  private static final String PACKAGE = "org.sourceanalysis.app.analysis.interpretation.process.";

  @TempDir Path temporaryDirectory;

  @Test
  void persistsCompleteProcessMaterialForFreshReopenBeforeAnyProcessModelTask() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m6-persisted-material"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      CrossFlowCandidateCompilation compilation =
          CrossFlowCandidateCompilerTest.compileForPublisher(fixture, facts, flows, registry);

      ModulePublicationReference publication = publish(fixture, compilation);
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(publication);
      assertThat(reopened.payloads()).hasSize(1);
      assertThat(reopened.payloads().get(0).descriptor().fileName())
          .isEqualTo("cross-flow-candidate-compilation.json");
      assertThat(reopened.payloads().get(0).descriptor().artifactType())
          .isEqualTo("FLOW_INTERPRETATION_CROSS_FLOW_CANDIDATE_COMPILATION");
      assertThat(reopened.payloads().get(0).descriptor().schemaVersion())
          .isEqualTo("flow-interpretation-cross-flow-candidate-compilation-v1");

      JsonNode payload =
          new CanonicalJsonCodec()
              .parseCanonical(reopened.payloads().get(0).canonicalUtf8())
              .path("payload");
      assertThat(payload.path("flowSlices")).hasSize(compilation.flowSliceIds().size());
      assertThat(payload.path("evidenceCapsules")).hasSize(compilation.flowSliceIds().size());
      assertThat(payload.path("codeFacts")).isNotEmpty();
      assertThat(payload.path("atomProofs")).isNotEmpty();
      assertThat(payload.path("evidenceNodes")).isNotEmpty();
      assertThat(payload.path("registryItems")).isNotEmpty();
      assertThat(payload.path("processEvidenceGroups"))
          .hasSize(compilation.processEvidenceGroups().size());
      JsonNode persistedMaterial =
          payload.path("processEvidenceGroups").get(0).path("persistedMaterial");
      JsonNode persistedFlow = persistedMaterial.path("flowViews").get(0);
      assertThat(persistedFlow.path("flowSlice").path("flowSliceId").asText()).isNotBlank();
      assertThat(persistedFlow.path("evidenceCapsule").path("evidenceCapsuleId").asText())
          .isNotBlank();
      assertThat(persistedFlow.path("evidenceCapsule").path("factViews")).isNotEmpty();
      assertThat(persistedFlow.path("evidenceCapsule").path("outcomePathViews")).isNotEmpty();
      assertThat(persistedFlow.path("evidenceCapsule").path("modelEvidenceSpans")).isNotEmpty();
      assertThat(persistedFlow.path("evidenceCapsule").path("projectionObligations")).isNotEmpty();
      assertThat(persistedMaterial.path("limits").path("maxFlows").asInt()).isPositive();
      assertThat(persistedMaterial.path("limits").path("maxInputBytes").asInt()).isPositive();
    }
  }

  private static ModulePublicationReference publish(
      ProgramGraphsPublicFixture fixture, CrossFlowCandidateCompilation compilation)
      throws Exception {
    Class<?> publisherType = Class.forName(PACKAGE + "CrossFlowCandidateModulePublisher");
    Object publisher = null;
    for (Constructor<?> constructor : publisherType.getConstructors()) {
      Class<?>[] types = constructor.getParameterTypes();
      if (types.length != 2) continue;
      Object[] values = new Object[2];
      for (int index = 0; index < types.length; index++) {
        if (types[index].isInstance(fixture.stepArtifacts()))
          values[index] = fixture.stepArtifacts();
        if (types[index].isInstance(fixture.moduleArtifacts()))
          values[index] = fixture.moduleArtifacts();
      }
      if (values[0] != null && values[1] != null) {
        publisher = constructor.newInstance(values);
        break;
      }
    }
    if (publisher == null) throw new AssertionError("M6_PUBLISHER_CONSTRUCTOR_SEAM_INVALID");
    Method publish = publisherType.getMethod("publish", CrossFlowCandidateCompilation.class);
    try {
      return (ModulePublicationReference) publish.invoke(publisher, compilation);
    } catch (InvocationTargetException failure) {
      throw new AssertionError(
          "M6_PERSISTED_MATERIAL_PUBLICATION_FAILED",
          failure.getCause() == null ? failure : failure.getCause());
    }
  }

  private static ProvenCodeFactsReference publishProvenFacts(ProgramGraphsPublicFixture fixture)
      throws Exception {
    Method method =
        RegistryProposalTaskCompilerTest.class.getDeclaredMethod(
            "publishProvenFacts", ProgramGraphsPublicFixture.class);
    method.setAccessible(true);
    return (ProvenCodeFactsReference) method.invoke(null, fixture);
  }

  private static ModulePublicationReference publishRegistry(
      ProgramGraphsPublicFixture fixture, BusinessFlowsReference flows) throws Exception {
    Method method =
        Class.forName(
                "org.sourceanalysis.app.analysis.interpretation.model.FiniteKeyFlowTaskCompilerTest")
            .getDeclaredMethod(
                "publishRegistry", ProgramGraphsPublicFixture.class, BusinessFlowsReference.class);
    method.setAccessible(true);
    return (ModulePublicationReference) method.invoke(null, fixture, flows);
  }
}
