package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.material.BuildBusinessMaterialsRequest;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilder;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.knowledge.RepositoryBusinessKnowledge;

/** Ensures normal report coordination uses the persisted source/discovery prefix. */
class RepositoryAnalysisRunCoordinatorTest {

  @TempDir Path temporaryDirectory;

  @Test
  void forwardsPersistedInventoryAndDiscoveryDirectlyWhenBusinessFlowPublicationIsUnavailable() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("repository-run-coordinator-direct-entry"))) {
      BusinessMaterialBuildResult materials =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      fixture.sourceInventory(),
                      fixture.applicationDiscovery(),
                      new BusinessMaterialProfile(8, 24, 12_000)));
      TechnicalDiscoveryWorkflowResult technical =
          new TechnicalDiscoveryWorkflowResult(
              fixture.sourceInventory(), fixture.applicationDiscovery());
      BusinessAnalysisWorkflowResult business =
          new BusinessAnalysisWorkflowResult(
              materials,
              new ActivityExplanationResult(List.of(), List.of()),
              new RepositoryBusinessKnowledge(
                  List.of(), List.of(), List.of(), List.of(), List.of()),
              null);
      AtomicReference<VerifiedSourceInventoryReference> suppliedInventory = new AtomicReference<>();
      AtomicReference<ApplicationDiscoveryReference> suppliedDiscovery = new AtomicReference<>();

      RepositoryAnalysisRunCoordinator coordinator =
          new RepositoryAnalysisRunCoordinator(
              runId -> {
                assertThat(runId)
                    .isEqualTo(fixture.sourceInventory().publication().address().runId());
                return technical;
              },
              (inventory, discovery) -> {
                suppliedInventory.set(inventory);
                suppliedDiscovery.set(discovery);
                return business;
              });

      RepositoryAnalysisRunResult result =
          coordinator.execute(fixture.sourceInventory().publication().address().runId());

      assertThat(suppliedInventory).hasValue(fixture.sourceInventory());
      assertThat(suppliedDiscovery).hasValue(fixture.applicationDiscovery());
      assertThat(result.technical()).isSameAs(technical);
      assertThat(result.business()).isSameAs(business);
    }
  }
}
