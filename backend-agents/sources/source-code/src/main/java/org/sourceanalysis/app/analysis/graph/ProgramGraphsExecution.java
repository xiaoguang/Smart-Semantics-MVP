package org.sourceanalysis.app.analysis.graph;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.code.CodeEngineException;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexPublicationSpecifier;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;

/**
 * Executes the inseparable program-graphs analysis step from its two persisted predecessor
 * publications.
 *
 * <p>The caller supplies only the two typed predecessor references, one frozen graph profile, and
 * the controls to be checked against persisted input. Every graph module is published and then
 * fresh-reopened before its successor receives it. This seam deliberately does not accept a path,
 * source bytes, a raw graph draft, or an assembled intermediate input.
 */
public final class ProgramGraphsExecution {

  private final ProgramGraphInputReader inputs;
  private final CanonicalModuleArtifactStore modules;
  private final CanonicalAnalysisStepArtifactStore analysisSteps;

  /** Creates the execution seam with its only source and canonical storage dependencies. */
  public ProgramGraphsExecution(
      VerifiedSourceTextReader sourceReader,
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore analysisSteps) {
    this.modules = Objects.requireNonNull(modules, "module artifact store");
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis-step artifact store");
    inputs =
        new PersistedProgramGraphInputReader(
            this.analysisSteps, Objects.requireNonNull(sourceReader, "verified source reader"));
  }

  /**
   * Publishes M1 through M6 in fixed dependency order and returns the one public graph-set
   * reference. Any predecessor failure stops the execution before its successor begins.
   */
  public ProgramGraphsReference execute(
      VerifiedSourceInventoryReference verifiedSource,
      ApplicationDiscoveryReference applicationDiscovery,
      ArtifactReference graphProfileRef,
      ArtifactControls controls) {
    Objects.requireNonNull(verifiedSource, "verified source inventory");
    Objects.requireNonNull(applicationDiscovery, "application discovery");
    Objects.requireNonNull(graphProfileRef, "graph profile reference");
    Objects.requireNonNull(controls, "artifact controls");

    ReopenedProgramGraphInputs initial = inputs.reopen(verifiedSource, applicationDiscovery);
    requireControls(initial, controls);
    AnalysisStepModuleAddress codeStructureAddress = address(verifiedSource, 1, "code-structure");
    CodeStructureGraphDraftReference codeStructureReference =
        new CodeStructureGraphExecution(
                inputs,
                new CodeStructureGraphBuilder(),
                new CodeStructureGraphModulePublisher(modules))
            .execute(
                verifiedSource,
                applicationDiscovery,
                codeStructureAddress,
                new CodeStructureGraphProfile(graphProfileRef));

    CallGraphDraftReference callReference =
        new CallGraphExecution(
                inputs,
                new PersistedCodeStructureGraphReader(modules),
                new CallGraphBuilder(),
                new CallGraphModulePublisher(modules))
            .execute(
                verifiedSource,
                applicationDiscovery,
                codeStructureReference,
                address(verifiedSource, 2, "call-graph"),
                new CallGraphProfile(graphProfileRef));

    ReopenedProgramGraphInputs controlInputs = inputs.reopen(verifiedSource, applicationDiscovery);
    requireControls(controlInputs, controls);
    ReopenedCodeStructureGraph controlStructure =
        new PersistedCodeStructureGraphReader(modules)
            .reopen(codeStructureReference, controlInputs, graphProfileRef);
    ReopenedCallGraph controlCalls =
        new PersistedCallGraphReader(modules)
            .reopen(callReference, controlInputs, controlStructure, graphProfileRef);
    ControlFlowGraphDraftReference controlReference =
        new ControlFlowGraphModulePublisher(modules)
            .publish(
                address(verifiedSource, 3, "control-flow"),
                controlStructure,
                controlCalls,
                controlInputs,
                new ControlFlowGraphBuilder()
                    .buildControlFlow(
                        new ControlFlowInputs(controlStructure, controlCalls, controlInputs),
                        new ControlFlowGraphProfile(graphProfileRef)));

    ReopenedProgramGraphInputs dataInputs = inputs.reopen(verifiedSource, applicationDiscovery);
    requireControls(dataInputs, controls);
    ReopenedCodeStructureGraph dataStructure =
        new PersistedCodeStructureGraphReader(modules)
            .reopen(codeStructureReference, dataInputs, graphProfileRef);
    ReopenedCallGraph dataCalls =
        new PersistedCallGraphReader(modules)
            .reopen(callReference, dataInputs, dataStructure, graphProfileRef);
    ReopenedControlFlowGraph dataControl =
        new PersistedControlFlowGraphReader(modules)
            .reopen(controlReference, dataInputs, dataStructure, dataCalls, graphProfileRef);
    DataFlowGraphDraftReference dataReference =
        new DataFlowGraphModulePublisher(modules)
            .publish(
                address(verifiedSource, 4, "data-flow"),
                dataStructure,
                dataCalls,
                dataControl,
                dataInputs,
                new DataFlowGraphBuilder()
                    .buildDataFlow(
                        new DataFlowInputs(dataStructure, dataCalls, dataControl, dataInputs),
                        new DataFlowGraphProfile(graphProfileRef)));

    ReopenedProgramGraphInputs evidenceInputs = inputs.reopen(verifiedSource, applicationDiscovery);
    requireControls(evidenceInputs, controls);
    ReopenedCodeStructureGraph evidenceStructure =
        new PersistedCodeStructureGraphReader(modules)
            .reopen(codeStructureReference, evidenceInputs, graphProfileRef);
    ReopenedCallGraph evidenceCalls =
        new PersistedCallGraphReader(modules)
            .reopen(callReference, evidenceInputs, evidenceStructure, graphProfileRef);
    ReopenedControlFlowGraph evidenceControl =
        new PersistedControlFlowGraphReader(modules)
            .reopen(
                controlReference,
                evidenceInputs,
                evidenceStructure,
                evidenceCalls,
                graphProfileRef);
    ReopenedDataFlowGraph evidenceData =
        new PersistedDataFlowGraphReader(modules)
            .reopen(
                dataReference,
                evidenceInputs,
                evidenceStructure,
                evidenceCalls,
                evidenceControl,
                graphProfileRef);
    EvidenceGraphDraftReference evidenceReference =
        new EvidenceGraphModulePublisher(modules)
            .publish(
                address(verifiedSource, 5, "evidence-graph"),
                evidenceStructure,
                evidenceCalls,
                evidenceControl,
                evidenceData,
                evidenceInputs,
                new EvidenceGraphBuilder()
                    .buildEvidence(
                        List.of(
                            evidenceStructure.draft(),
                            evidenceCalls.draft(),
                            evidenceControl.draft(),
                            evidenceData.draft()),
                        evidenceInputs.source()));

    ReopenedProgramGraphInputs publicationInputs =
        inputs.reopen(verifiedSource, applicationDiscovery);
    requireControls(publicationInputs, controls);
    ReopenedCodeStructureGraph publicationStructure =
        new PersistedCodeStructureGraphReader(modules)
            .reopen(codeStructureReference, publicationInputs, graphProfileRef);
    ReopenedCallGraph publicationCalls =
        new PersistedCallGraphReader(modules)
            .reopen(callReference, publicationInputs, publicationStructure, graphProfileRef);
    ReopenedControlFlowGraph publicationControl =
        new PersistedControlFlowGraphReader(modules)
            .reopen(
                controlReference,
                publicationInputs,
                publicationStructure,
                publicationCalls,
                graphProfileRef);
    ReopenedDataFlowGraph publicationData =
        new PersistedDataFlowGraphReader(modules)
            .reopen(
                dataReference,
                publicationInputs,
                publicationStructure,
                publicationCalls,
                publicationControl,
                graphProfileRef);
    ReopenedEvidenceGraph publicationEvidence =
        new PersistedEvidenceGraphReader(modules)
            .reopen(
                evidenceReference,
                publicationInputs,
                publicationStructure,
                publicationCalls,
                publicationControl,
                publicationData,
                graphProfileRef);
    return new ProgramGraphSetPublicationSpecifier(modules, analysisSteps)
        .specifyGraphSet(
            new ProgramGraphsPublicationInputs(
                verifiedSource.publication(),
                applicationDiscovery.publication(),
                publicationStructure,
                publicationCalls,
                publicationControl,
                publicationData,
                publicationEvidence),
            controls);
  }

  /**
   * Collects and publishes the selected JDT session's neutral navigation index without invoking the
   * legacy JavaParser graph builders.
   */
  public ProgramGraphsReference execute(
      VerifiedSourceInventoryReference verifiedSource,
      ApplicationDiscoveryReference applicationDiscovery,
      JavaCodeSession session,
      ArtifactControls controls) {
    Objects.requireNonNull(verifiedSource, "verified source inventory");
    Objects.requireNonNull(applicationDiscovery, "application discovery");
    Objects.requireNonNull(session, "Java code session");
    Objects.requireNonNull(controls, "artifact controls");
    ReopenedProgramGraphInputs reopened = inputs.reopen(verifiedSource, applicationDiscovery);
    requireControls(reopened, controls);
    var catalog = session.catalog();
    if (!catalog.snapshotId().equals(reopened.source().snapshotId())) {
      throw new GraphReferenceException();
    }
    List<JavaCodeIndex.EntryCollection> entries =
        reopened.discovery().entries().stream()
            .map(
                entry -> {
                  EntrySeed seed =
                      new EntrySeed(
                          entry.entryId().value(),
                          entry.methodKey(),
                          entry.methodRange(),
                          entry.methodCondition().display() + " " + entry.route());
                  try {
                    return JavaCodeIndex.EntryCollection.collected(seed, session.collect(seed));
                  } catch (CodeEngineException failure) {
                    return JavaCodeIndex.EntryCollection.notCollected(
                        seed, failure.code() + ": " + failure.getMessage());
                  }
                })
            .sorted(java.util.Comparator.comparing(value -> value.seed().entryId()))
            .toList();
    EntryCodeContext.TechnicalEnhancements enhancements =
        entries.stream()
            .map(JavaCodeIndex.EntryCollection::context)
            .filter(Objects::nonNull)
            .map(EntryCodeContext::technicalEnhancements)
            .findFirst()
            .orElseGet(
                () ->
                    new EntryCodeContext.TechnicalEnhancements(
                        EntryCodeContext.Availability.NOT_PRODUCED,
                        "STRICT_GRAPH_ENRICHMENT_NOT_REQUESTED_BY_JDT_EXECUTION",
                        List.of(),
                        List.of(),
                        null));
    return new JavaCodeIndexPublicationSpecifier(modules, analysisSteps)
        .publish(
            verifiedSource,
            applicationDiscovery,
            controls,
            new JavaCodeIndex(
                session.descriptor(),
                catalog.snapshotId(),
                reopened.source().verifiedSnapshotRef(),
                catalog,
                entries,
                enhancements));
  }

  private static AnalysisStepModuleAddress address(
      VerifiedSourceInventoryReference source, int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        source.publication().address().runId(),
        AnalysisStepKey.PROGRAM_GRAPHS,
        moduleNumber,
        moduleKey);
  }

  private static void requireControls(
      ReopenedProgramGraphInputs reopened, ArtifactControls expectedControls) {
    if (!reopened.source().controls().equals(expectedControls)) {
      throw new GraphReferenceException();
    }
  }
}
