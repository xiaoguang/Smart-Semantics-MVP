package org.sourceanalysis.app.analysis.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

  private static final System.Logger LOGGER =
      System.getLogger(ProgramGraphsExecution.class.getName());

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
    PreparedProgramGraphSet prepared =
        prepare(verifiedSource, applicationDiscovery, graphProfileRef, controls);
    return new ProgramGraphSetPublicationSpecifier(modules, analysisSteps)
        .publishPrepared(
            verifiedSource.publication(), applicationDiscovery.publication(), controls, prepared);
  }

  /** Builds and installs M1 through M6 without installing the public Step 03 boundary. */
  public PreparedProgramGraphSet prepare(
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
        .prepareGraphSet(
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
    return publishNavigationIndex(
        verifiedSource,
        applicationDiscovery,
        session,
        controls,
        null,
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.NOT_PRODUCED,
            "STRICT_GRAPH_ENRICHMENT_NOT_REQUESTED_BY_JDT_EXECUTION",
            List.of(),
            List.of(),
            null),
        List.of());
  }

  /**
   * Publishes only the selected JDT entry contexts while retaining the complete discovery
   * denominator.
   */
  public ProgramGraphsReference execute(
      VerifiedSourceInventoryReference verifiedSource,
      ApplicationDiscoveryReference applicationDiscovery,
      JavaCodeSession session,
      ArtifactReference graphProfileRef,
      ArtifactControls controls,
      List<String> selectedEntryIds) {
    Objects.requireNonNull(session, "Java code session");
    Objects.requireNonNull(graphProfileRef, "graph profile reference");
    if (!"jdt".equals(session.descriptor().engineId())) {
      throw new IllegalArgumentException("selected entry collection requires the JDT engine");
    }
    return publishNavigationIndex(
        verifiedSource,
        applicationDiscovery,
        session,
        controls,
        null,
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.NOT_PRODUCED,
            "STRICT_GRAPH_ENRICHMENT_NOT_REQUESTED_BY_JDT_EXECUTION",
            List.of(),
            List.of(),
            null),
        selectedEntryIds);
  }

  /** Publishes the selected engine route, retaining strict graphs for JavaParser only. */
  public ProgramGraphsReference execute(
      VerifiedSourceInventoryReference verifiedSource,
      ApplicationDiscoveryReference applicationDiscovery,
      JavaCodeSession session,
      ArtifactReference graphProfileRef,
      ArtifactControls controls) {
    Objects.requireNonNull(session, "Java code session");
    if (!"javaparser".equals(session.descriptor().engineId())) {
      return execute(verifiedSource, applicationDiscovery, session, controls);
    }
    PreparedProgramGraphSet graphSet =
        prepare(verifiedSource, applicationDiscovery, graphProfileRef, controls);
    EntryCodeContext.TechnicalEnhancements enhancements =
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.AVAILABLE,
            null,
            graphSet.semanticPayloadReferences().stream()
                .map(value -> value.artifactId().value())
                .toList(),
            List.of(),
            null);
    return publishNavigationIndex(
        verifiedSource, applicationDiscovery, session, controls, graphSet, enhancements, List.of());
  }

  private ProgramGraphsReference publishNavigationIndex(
      VerifiedSourceInventoryReference verifiedSource,
      ApplicationDiscoveryReference applicationDiscovery,
      JavaCodeSession session,
      ArtifactControls controls,
      PreparedProgramGraphSet graphSet,
      EntryCodeContext.TechnicalEnhancements enhancements,
      List<String> selectedEntryIds) {
    Objects.requireNonNull(verifiedSource, "verified source inventory");
    Objects.requireNonNull(applicationDiscovery, "application discovery");
    Objects.requireNonNull(session, "Java code session");
    Objects.requireNonNull(controls, "artifact controls");
    ReopenedProgramGraphInputs reopened = inputs.reopen(verifiedSource, applicationDiscovery);
    requireControls(reopened, controls);
    List<EntrySeed> seeds =
        reopened.discovery().entries().stream()
            .map(
                entry ->
                    new EntrySeed(
                        entry.entryId().value(),
                        entry.methodKey(),
                        entry.methodRange(),
                        entry.methodCondition().display() + " " + entry.route()))
            .toList();
    List<String> selected = selectedEntryIds(selectedEntryIds, seeds);
    Set<String> selectedSet = Set.copyOf(selected);
    boolean selectedScope = !selectedSet.isEmpty();
    int selectedTotal = selectedScope ? selectedSet.size() : seeds.size();
    var catalog = session.catalog();
    if (!catalog.snapshotId().equals(reopened.source().snapshotId())) {
      throw new GraphReferenceException();
    }
    List<JavaCodeIndex.EntryCollection> entries = new ArrayList<>(seeds.size());
    int completedSelected = 0;
    for (EntrySeed seed : seeds) {
      if (selectedScope && !selectedSet.contains(seed.entryId())) {
        entries.add(JavaCodeIndex.EntryCollection.notCollected(seed, "NOT_SELECTED_FOR_SAMPLE"));
        continue;
      }
      long started = System.nanoTime();
      LOGGER.log(
          System.Logger.Level.INFO,
          () ->
              "JDT_NAVIGATION_ENTRY_START entryId=%s selectedTotal=%d"
                  .formatted(seed.entryId(), selectedTotal));
      try {
        EntryCodeContext context = session.collect(seed);
        completedSelected++;
        long elapsed = System.nanoTime() - started;
        int completed = completedSelected;
        LOGGER.log(
            System.Logger.Level.INFO,
            () ->
                ("JDT_NAVIGATION_ENTRY_COMPLETE entryId=%s completedSelected=%d selectedTotal=%d"
                        + " methods=%d calls=%d elapsedNanos=%d")
                    .formatted(
                        seed.entryId(),
                        completed,
                        selectedTotal,
                        context.methods().size(),
                        context.calls().size(),
                        elapsed));
        entries.add(JavaCodeIndex.EntryCollection.collected(seed, context));
      } catch (CodeEngineException failure) {
        completedSelected++;
        long elapsed = System.nanoTime() - started;
        int completed = completedSelected;
        LOGGER.log(
            System.Logger.Level.INFO,
            () ->
                ("JDT_NAVIGATION_ENTRY_FAILED entryId=%s completedSelected=%d selectedTotal=%d"
                        + " code=%s elapsedNanos=%d")
                    .formatted(seed.entryId(), completed, selectedTotal, failure.code(), elapsed));
        entries.add(
            JavaCodeIndex.EntryCollection.notCollected(
                seed, failure.code() + ": " + failure.getMessage()));
      } catch (RuntimeException failure) {
        long elapsed = System.nanoTime() - started;
        LOGGER.log(
            System.Logger.Level.INFO,
            () ->
                ("JDT_NAVIGATION_ENTRY_FAILED entryId=%s selectedTotal=%d failureType=%s"
                        + " elapsedNanos=%d")
                    .formatted(
                        seed.entryId(),
                        selectedTotal,
                        failure.getClass().getSimpleName(),
                        elapsed));
        throw failure;
      }
    }
    int completed = completedSelected;
    LOGGER.log(
        System.Logger.Level.INFO,
        () ->
            "JDT_NAVIGATION_COLLECTION_COMPLETE completedSelected=%d selectedTotal=%d"
                .formatted(completed, selectedTotal));
    entries.sort(java.util.Comparator.comparing(value -> value.seed().entryId()));
    List<JavaCodeIndex.EntryCollection> enrichedEntries =
        entries.stream().map(entry -> withEnhancements(entry, enhancements)).toList();
    JavaCodeIndex index =
        new JavaCodeIndex(
            session.descriptor(),
            catalog.snapshotId(),
            reopened.source().verifiedSnapshotRef(),
            catalog,
            enrichedEntries,
            enhancements);
    JavaCodeIndexPublicationSpecifier publisher =
        new JavaCodeIndexPublicationSpecifier(modules, analysisSteps);
    long publicationStarted = System.nanoTime();
    LOGGER.log(
        System.Logger.Level.INFO,
        () -> "JDT_NAVIGATION_PUBLICATION_START selectedTotal=%d".formatted(selectedTotal));
    try {
      ProgramGraphsReference reference =
          graphSet == null
              ? publisher.publish(verifiedSource, applicationDiscovery, controls, index)
              : publisher.publishWithGraphEnhancements(
                  verifiedSource, applicationDiscovery, controls, index, graphSet);
      long elapsed = System.nanoTime() - publicationStarted;
      LOGGER.log(
          System.Logger.Level.INFO,
          () ->
              "JDT_NAVIGATION_PUBLICATION_COMPLETE selectedTotal=%d elapsedNanos=%d"
                  .formatted(selectedTotal, elapsed));
      return reference;
    } catch (RuntimeException failure) {
      long elapsed = System.nanoTime() - publicationStarted;
      LOGGER.log(
          System.Logger.Level.INFO,
          () ->
              "JDT_NAVIGATION_PUBLICATION_FAILED selectedTotal=%d failureType=%s elapsedNanos=%d"
                  .formatted(selectedTotal, failure.getClass().getSimpleName(), elapsed));
      throw failure;
    }
  }

  private static List<String> selectedEntryIds(
      List<String> requestedEntryIds, List<EntrySeed> discoveredEntries) {
    if (requestedEntryIds == null) {
      throw new IllegalArgumentException("selected entry IDs are required");
    }
    List<String> selected = new ArrayList<>(requestedEntryIds.size());
    for (String entryId : requestedEntryIds) {
      if (entryId == null || entryId.isBlank()) {
        throw new IllegalArgumentException("selected entry ID cannot be blank");
      }
      selected.add(entryId);
    }
    selected.sort(java.util.Comparator.naturalOrder());
    if (new HashSet<>(selected).size() != selected.size()) {
      throw new IllegalArgumentException("selected entry IDs cannot repeat");
    }
    Set<String> discovered =
        discoveredEntries.stream()
            .map(EntrySeed::entryId)
            .collect(java.util.stream.Collectors.toSet());
    if (!discovered.containsAll(selected)) {
      throw new IllegalArgumentException("selected entry ID is not in application discovery");
    }
    return List.copyOf(selected);
  }

  private static JavaCodeIndex.EntryCollection withEnhancements(
      JavaCodeIndex.EntryCollection entry, EntryCodeContext.TechnicalEnhancements enhancements) {
    if (entry.context() == null) {
      return entry;
    }
    EntryCodeContext context = entry.context();
    return JavaCodeIndex.EntryCollection.collected(
        entry.seed(),
        new EntryCodeContext(
            context.schemaVersion(),
            context.entryId(),
            context.entryMethodKey(),
            context.methods(),
            context.calls(),
            context.supportingSources(),
            context.limitations(),
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
