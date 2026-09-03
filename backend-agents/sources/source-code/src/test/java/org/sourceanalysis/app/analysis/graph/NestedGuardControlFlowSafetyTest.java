package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression seam for nested guard handling in the entry-rooted control-flow builder. */
class NestedGuardControlFlowSafetyTest {

  @TempDir Path temporaryDirectory;

  @Test
  void doesNotInventASinglePathForNestedGuards() {
    try (ControlFlowGraphBuilderTest.Fixture fixture = nestedFixture(temporaryDirectory)) {
      ControlFlowGraphDraft draft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(
                      fixture.structure(), fixture.calls(), fixture.reopenedInputs()),
                  new ControlFlowGraphProfile(fixture.graphProfileRef()));

      List<GraphGapDraft> gaps = draft.gapDrafts();
      if (!gaps.isEmpty()) {
        assertThat(gaps)
            .singleElement()
            .satisfies(
                gap -> {
                  assertThat(gap.reasonCode()).isEqualTo("BRANCH_SLICE_NOT_INSTALLED");
                  assertThat(gap.affectedEntryIds()).containsExactly(fixture.entryId());
                  assertThat(gap.sourceLocator().path())
                      .isEqualTo("src/main/java/com/example/DepotHeadService.java");
                });
        assertThat(draft.nodes())
            .extracting(ControlFlowNode::kind)
            .contains(ControlFlowNodeKind.PROFILE_STOP_TERMINAL);
        assertThat(draft.edges())
            .filteredOn(edge -> edge.kind() == ControlFlowEdgeKind.CALL)
            .noneMatch(
                edge ->
                    edge.evidenceDraftRefs().stream()
                        .map(
                            evidenceId ->
                                draft.provenanceDrafts().stream()
                                    .filter(
                                        provenance ->
                                            provenance.provenanceDraftId().equals(evidenceId))
                                    .findFirst()
                                    .orElseThrow())
                        .anyMatch(
                            provenance ->
                                provenance
                                    .sourceLocator()
                                    .path()
                                    .endsWith("src/main/java/com/example/DepotHeadService.java")));
        return;
      }

      List<ControlFlowNode> guards =
          draft.nodes().stream().filter(node -> node.kind() == ControlFlowNodeKind.GUARD).toList();
      assertThat(guards).as("an exact implementation must retain both nested guards").hasSize(2);
      for (ControlFlowNode guard : guards) {
        assertThat(draft.edges())
            .filteredOn(edge -> edge.fromNodeId().equals(guard.nodeId()))
            .filteredOn(edge -> edge.kind() == ControlFlowEdgeKind.TRUE)
            .singleElement()
            .satisfies(
                edge -> {
                  assertThat(edge.guardNodeId()).isEqualTo(guard.nodeId());
                  assertThat(edge.polarity()).isEqualTo(ControlFlowPolarity.TRUE);
                });
        assertThat(draft.edges())
            .filteredOn(edge -> edge.fromNodeId().equals(guard.nodeId()))
            .filteredOn(edge -> edge.kind() == ControlFlowEdgeKind.FALSE)
            .singleElement()
            .satisfies(
                edge -> {
                  assertThat(edge.guardNodeId()).isEqualTo(guard.nodeId());
                  assertThat(edge.polarity()).isEqualTo(ControlFlowPolarity.FALSE);
                });
      }
      assertThat(draft.nodes())
          .extracting(ControlFlowNode::kind)
          .contains(ControlFlowNodeKind.ENTRY_RETURN_TERMINAL, ControlFlowNodeKind.THROW_TERMINAL);
      assertThat(draft.nodes())
          .allSatisfy(
              node -> {
                if (node.kind() == ControlFlowNodeKind.ENTRY_RETURN_TERMINAL
                    || node.kind() == ControlFlowNodeKind.THROW_TERMINAL) {
                  assertThat(draft.edges())
                      .noneMatch(edge -> edge.fromNodeId().equals(node.nodeId()));
                }
              });
    }
  }

  private static ControlFlowGraphBuilderTest.Fixture nestedFixture(Path temporaryDirectory) {
    try {
      Method factory =
          ControlFlowGraphBuilderTest.Fixture.class.getDeclaredMethod(
              "create", Path.class, String.class);
      factory.setAccessible(true);
      return (ControlFlowGraphBuilderTest.Fixture)
          factory.invoke(null, temporaryDirectory, nestedServiceSource());
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException runtimeFailure) {
        throw runtimeFailure;
      }
      throw new AssertionError("nested fixture factory failed", cause);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("nested fixture factory is unavailable", failure);
    }
  }

  private static String nestedServiceSource() {
    return """
        package com.example;

        class DepotHeadService {
          private final DepotHeadMapper depotHeadMapper = null;

          void batchSetStatus(String status) {
            if (status == null) {
              return;
            }
            if (status != null) {
              depotHeadMapper.updateStatus(status);
            } else {
              throw new IllegalArgumentException("status");
            }
          }
        }
        """;
  }
}
