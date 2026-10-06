package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.EvidenceUnit;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.SearchResult;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitPage;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** Executes only model-selected bounded navigation and source reads against one verified corpus. */
public final class OntologyReadingCoordinator {
  private final OntologyEvidenceCorpus corpus;
  private final OntologyDecisionRunner decisions;
  private final int maxRounds;
  private final int maxPageItems;
  private final int maxPacketBytes;
  private final int maxFormalUnitBytes;
  private final int maxFormalRequestBytes;
  private final int maxFormalNavigationEntries;
  private final boolean formalV4;
  private FormalState observedFormalState;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  public OntologyReadingCoordinator(
      OntologyEvidenceCorpus corpus,
      OntologyDecisionRunner decisions,
      int maxRounds,
      int maxPageItems,
      int maxPacketBytes) {
    this(
        corpus,
        decisions,
        maxRounds,
        maxPageItems,
        maxPacketBytes,
        maxPacketBytes,
        maxPacketBytes,
        100);
  }

  private OntologyReadingCoordinator(
      OntologyEvidenceCorpus corpus,
      OntologyDecisionRunner decisions,
      int maxRounds,
      int maxPageItems,
      int maxPacketBytes,
      int maxFormalUnitBytes,
      int maxFormalRequestBytes,
      int maxFormalNavigationEntries) {
    this(
        corpus,
        decisions,
        maxRounds,
        maxPageItems,
        maxPacketBytes,
        maxFormalUnitBytes,
        maxFormalRequestBytes,
        maxFormalNavigationEntries,
        false);
  }

  private OntologyReadingCoordinator(
      OntologyEvidenceCorpus corpus,
      OntologyDecisionRunner decisions,
      int maxRounds,
      int maxPageItems,
      int maxPacketBytes,
      int maxFormalUnitBytes,
      int maxFormalRequestBytes,
      int maxFormalNavigationEntries,
      boolean formalV4) {
    this.corpus = Objects.requireNonNull(corpus, "ontology corpus");
    this.decisions = Objects.requireNonNull(decisions, "ontology reading decisions");
    if (maxRounds < 1
        || maxPageItems < 1
        || maxPacketBytes < 1
        || maxFormalUnitBytes < 1
        || maxFormalRequestBytes < 1
        || maxFormalNavigationEntries < 1) {
      throw new IllegalArgumentException("ONTOLOGY_READING_LIMIT_INVALID");
    }
    this.maxRounds = maxRounds;
    this.maxPageItems = maxPageItems;
    this.maxPacketBytes = maxPacketBytes;
    this.maxFormalUnitBytes = maxFormalUnitBytes;
    this.maxFormalRequestBytes = maxFormalRequestBytes;
    this.maxFormalNavigationEntries = maxFormalNavigationEntries;
    this.formalV4 = formalV4;
  }

  /** Formal-reading convenience constructor; historical callers continue to inject their runner. */
  public OntologyReadingCoordinator(
      OntologyEvidenceCorpus corpus,
      StructuredModelProvider provider,
      int maxRounds,
      int maxActionsPerRound,
      int maxUnitBytes) {
    this(corpus, provider, maxRounds, maxActionsPerRound, maxUnitBytes, 1_000_000, 100);
  }

  /**
   * Formal-reading seam with independently bounded source units, request envelope, and navigation.
   * Runtime wiring supplies these values from the immutable ontology configuration.
   */
  public OntologyReadingCoordinator(
      OntologyEvidenceCorpus corpus,
      StructuredModelProvider provider,
      int maxRounds,
      int maxActionsPerRound,
      int maxUnitBytes,
      int maxRequestBytes,
      int maxNavigationEntries) {
    this(
        corpus,
        provider,
        maxRounds,
        maxActionsPerRound,
        maxUnitBytes,
        maxRequestBytes,
        maxNavigationEntries,
        null);
  }

  /**
   * Saved-rule runtime seam for provider-backed formal reading. Existing provider constructors
   * intentionally retain the v3 packet family.
   */
  public OntologyReadingCoordinator(
      OntologyEvidenceCorpus corpus,
      StructuredModelProvider provider,
      int maxRounds,
      int maxActionsPerRound,
      int maxUnitBytes,
      int maxRequestBytes,
      int maxNavigationEntries,
      boolean formalV4) {
    this(
        corpus,
        new OntologyDecisionRunner(provider, maxRequestBytes, 100_000),
        maxRounds,
        maxActionsPerRound,
        maxUnitBytes,
        maxRequestBytes,
        maxNavigationEntries,
        formalV4);
  }

  /** Same formal limits with an immutable prompt/schema snapshot supplied by the later runtime. */
  public OntologyReadingCoordinator(
      OntologyEvidenceCorpus corpus,
      StructuredModelProvider provider,
      int maxRounds,
      int maxActionsPerRound,
      int maxUnitBytes,
      int maxRequestBytes,
      int maxNavigationEntries,
      OntologyDecisionRunner.FormalReadingMaterial formalReadingMaterial) {
    this(
        corpus,
        formalReadingMaterial == null
            ? new OntologyDecisionRunner(provider, maxRequestBytes, 100_000)
            : new OntologyDecisionRunner(provider, maxRequestBytes, 100_000, formalReadingMaterial),
        maxRounds,
        maxActionsPerRound,
        maxRequestBytes,
        maxUnitBytes,
        maxRequestBytes,
        maxNavigationEntries);
  }

  /**
   * Runtime-facing formal seam: the caller owns the actual provider envelope and immutable
   * prompt/schema snapshot, while this coordinator owns source/request/navigation selection limits.
   */
  public OntologyReadingCoordinator(
      OntologyEvidenceCorpus corpus,
      OntologyDecisionRunner decisions,
      int maxRounds,
      int maxActionsPerRound,
      int maxUnitBytes,
      int maxRequestBytes,
      int maxNavigationEntries) {
    this(
        corpus,
        decisions,
        maxRounds,
        maxActionsPerRound,
        maxRequestBytes,
        maxUnitBytes,
        maxRequestBytes,
        maxNavigationEntries);
  }

  /**
   * Saved-rule runtime seam. The boolean is supplied only from an already verified O0 projection
   * rule; existing constructors deliberately continue to freeze the v3 packet family.
   */
  public OntologyReadingCoordinator(
      OntologyEvidenceCorpus corpus,
      OntologyDecisionRunner decisions,
      int maxRounds,
      int maxActionsPerRound,
      int maxUnitBytes,
      int maxRequestBytes,
      int maxNavigationEntries,
      boolean formalV4) {
    this(
        corpus,
        decisions,
        maxRounds,
        maxActionsPerRound,
        maxRequestBytes,
        maxUnitBytes,
        maxRequestBytes,
        maxNavigationEntries,
        formalV4);
  }

  public Result complete(
      String questionId,
      String question,
      List<EvidenceUnit> initialUnits,
      List<OntologyNavigationView> initialNavigationViews) {
    if (initialUnits == null || initialNavigationViews == null) {
      throw new IllegalArgumentException("ONTOLOGY_READING_INPUT_INVALID");
    }
    List<EvidenceUnit> selected = verifyInitial(initialUnits);
    List<OntologyNavigationView> navigation = new ArrayList<>(initialNavigationViews);
    List<OntologyDecisionRunner.Decision> history = new ArrayList<>();
    List<String> unresolved = new ArrayList<>();
    OntologyReadingPacket packet =
        selected.isEmpty() ? null : OntologyReadingPacket.of(corpus.sourceIdentity(), selected);
    if (packet != null && packet.modelInput().size() > maxPacketBytes) {
      decisions.saveInitialReadingObservation(
          questionId, question, corpus.sourceIdentity(), packet, navigation);
      return new Result(
          Status.INCOMPLETE, packet, List.of(), List.of(), "ONTOLOGY_READING_PACKET_TOO_LARGE");
    }
    for (int round = 0; round < maxRounds; round++) {
      List<OntologyNavigationView> visible = new ArrayList<>(navigation);
      if (packet != null) {
        visible.add(OntologyNavigationView.packet(packet));
      }
      OntologyDecisionRunner.Decision decision =
          decisions.readingCheck(
              questionId,
              question,
              corpus.sourceIdentity(),
              packet,
              navigation,
              maxPageItems,
              maxRounds - round);
      history.add(decision);
      JsonNode output = json.parseCanonical(decision.output());
      output.path("unresolved").forEach(item -> unresolved.add(item.asText()));
      List<EvidenceUnit> next = new ArrayList<>();
      for (JsonNode reference : output.path("retainedRefs")) {
        OntologyNavigationView.Target retained =
            OntologyNavigationView.resolve(visible, OntologyNavigationView.parse(reference));
        for (String entryId : retained.packet().entryUses()) {
          next.add(corpus.read(entryId, retained.packet().kind(), retained.packet().originalId()));
        }
      }
      for (JsonNode request : output.path("readRequests")) {
        OntologyNavigationView.Target selectedUnit =
            OntologyNavigationView.resolve(
                visible, OntologyNavigationView.parse(request.path("unitRef")));
        UnitHandle handle = selectedUnit.unit();
        next.add(corpus.read(handle.entryId(), handle.kind(), handle.originalId()));
      }
      boolean navigationProgress = false;
      int requestedPageItems = 0;
      for (JsonNode query : output.path("queries")) {
        int limit = query.path("limit").asInt();
        requestedPageItems += limit;
        if (limit > maxPageItems || requestedPageItems > maxPageItems) {
          throw new IllegalArgumentException("ONTOLOGY_READING_QUERY_LIMIT_INVALID");
        }
        OntologyNavigationView.Target key =
            OntologyNavigationView.resolve(
                visible, OntologyNavigationView.parse(query.path("keyRef")));
        String kind = query.path("kind").asText();
        String lookupKey = lookupKey(key);
        UnitPage page = query(kind, lookupKey, query.path("offset").asInt(), limit);
        navigation.add(
            OntologyNavigationView.query(corpus.sourceIdentity(), corpus, kind, lookupKey, page));
        navigationProgress = true;
      }
      for (JsonNode search : output.path("literalSearches")) {
        int limit = search.path("limit").asInt();
        requestedPageItems += limit;
        if (limit > maxPageItems || requestedPageItems > maxPageItems) {
          throw new IllegalArgumentException("ONTOLOGY_READING_QUERY_LIMIT_INVALID");
        }
        String literal = search.path("query").asText();
        SearchResult found = corpus.searchLiteral(literal, search.path("offset").asInt(), limit);
        navigation.add(
            OntologyNavigationView.literalSearch(corpus.sourceIdentity(), corpus, literal, found));
        navigationProgress = true;
      }
      String outcome = output.path("decision").asText();
      if ("UNRESOLVED".equals(outcome)) {
        return finish(
            decision,
            round + 1,
            questionId,
            Status.UNRESOLVED,
            packet,
            history,
            navigation,
            unresolved,
            "");
      }
      if (next.isEmpty()) {
        if (!navigationProgress) {
          return finish(
              decision,
              round + 1,
              questionId,
              Status.INCOMPLETE,
              packet,
              history,
              navigation,
              unresolved,
              "");
        }
        packet = null;
        continue;
      }
      OntologyReadingPacket nextPacket;
      try {
        nextPacket = checkedPacket(next);
      } catch (IllegalArgumentException oversized) {
        if (!"ONTOLOGY_READING_PACKET_TOO_LARGE".equals(oversized.getMessage())) {
          throw oversized;
        }
        return finish(
            decision,
            round + 1,
            questionId,
            Status.INCOMPLETE,
            packet,
            history,
            navigation,
            unresolved,
            "ONTOLOGY_READING_PACKET_TOO_LARGE");
      }
      if ("READY_TO_EXTRACT".equals(outcome) && !navigationProgress) {
        return finish(
            decision,
            round + 1,
            questionId,
            Status.READY,
            nextPacket,
            history,
            navigation,
            unresolved,
            "");
      }
      boolean progress =
          packet == null || !nextPacket.packetId().equals(packet.packetId()) || navigationProgress;
      packet = nextPacket;
      if (!progress) {
        return finish(
            decision,
            round + 1,
            questionId,
            Status.INCOMPLETE,
            packet,
            history,
            navigation,
            unresolved,
            "");
      }
    }
    JsonNode lastOutput = json.parseCanonical(history.get(history.size() - 1).output());
    boolean unreadNavigation =
        !lastOutput.path("queries").isEmpty() || !lastOutput.path("literalSearches").isEmpty();
    return finish(
        history.get(history.size() - 1),
        maxRounds,
        questionId,
        Status.INCOMPLETE,
        packet,
        history,
        navigation,
        unresolved,
        unreadNavigation
            ? "ONTOLOGY_READING_ROUND_LIMIT_WITH_UNREAD_NAVIGATION"
            : "ONTOLOGY_READING_ROUND_LIMIT");
  }

  /**
   * Executes the formal request-bound reading profile. It is deliberately separate from the
   * historical viewId response path above.
   */
  public FormalResult completeFormal(
      OntologyScopeReader.Scope scope, String questionId, String taskId) {
    return completeFormal(scope, questionId, taskId, false);
  }

  public FormalResult completeFormal(
      OntologyScopeReader.Scope scope, String questionId, String taskId, boolean lean) {
    if (scope == null || questionId == null || taskId == null) {
      throw new IllegalArgumentException("ONTOLOGY_READING_INPUT_INVALID");
    }
    OntologyScopeReader.Question question =
        scope.questions().stream()
            .filter(candidate -> questionId.equals(candidate.questionId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_READING_QUESTION_UNKNOWN"));
    OntologyScopeReader.Task task =
        question.tasks().stream()
            .filter(candidate -> taskId.equals(candidate.taskId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_READING_TASK_UNKNOWN"));
    if (task.taskKind() == OntologyScopeReader.TaskKind.LINK) {
      return completeCoherentFormal(
          question,
          task,
          scope.mode() == OntologyScopeReader.Mode.DISCOVERY
              ? OntologyScopeReader.SelectionMode.MODEL
              : OntologyScopeReader.SelectionMode.EXPLICIT,
          lean);
    }
    return completeFormal(
        new FormalQuestion(
            question.questionId(), question.question(), question.entryRefs(), question.clueRefs()),
        new FormalTask(
            task.taskId(),
            OntologyTaskRunner.TaskKind.valueOf(task.taskKind().name()),
            task.readingMode(),
            task.unitUses(),
            task.requiredUnitUses()));
  }

  /** LINK preparation is mechanical, not a fabricated model reading decision. */
  private FormalResult completeCoherentFormal(
      OntologyScopeReader.Question question,
      OntologyScopeReader.Task task,
      OntologyScopeReader.SelectionMode selectionMode,
      boolean lean) {
    observedFormalState = null;
    OntologyCoherentLinkBundle.Result bundle =
        lean
            ? OntologyCoherentLinkBundle.prepareLean(
                corpus, question, task, maxFormalUnitBytes, maxFormalRequestBytes, selectionMode)
            : OntologyCoherentLinkBundle.prepare(
                corpus, question, task, maxFormalUnitBytes, maxFormalRequestBytes, selectionMode);
    JsonNode decision = bundle.decision();
    Set<String> entries = new LinkedHashSet<>(question.entryRefs());
    decision.path("derivedEntries").forEach(ref -> entries.add(ref.asText()));
    Set<FormalUnitUse> active = new LinkedHashSet<>();
    if (bundle.packet() != null) {
      for (OntologyReadingPacket.PackedUnit unit : bundle.packet().units()) {
        for (String entryRef : bundle.packet().entryRefs(unit.localRef())) {
          active.add(FormalUnitUse.of(bundle.packet().evidenceUnitRef(unit.localRef()), entryRef));
        }
      }
    }
    Set<FormalUnitUse> unread = new LinkedHashSet<>();
    decision
        .path("requiredButUnread")
        .forEach(
            use -> {
              if (use.has("unitRef")) {
                unread.add(
                    FormalUnitUse.of(use.path("unitRef").asText(), use.path("entryRef").asText()));
              }
            });
    FormalState state =
        new FormalState(
            entries,
            Set.copyOf(task.anchorRefs()),
            Set.of(),
            List.of(),
            active,
            unread,
            List.of(),
            List.of());
    FormalResult result =
        new FormalResult(
            bundle.packet() == null ? Status.INCOMPLETE : Status.READY,
            state,
            List.of(),
            bundle.issueCode() == null ? "" : bundle.issueCode(),
            bundle.packet(),
            bundle.packet(),
            bundle.packet(),
            new FreezeEnvelope("technical-bundle-v1", null, null, 0),
            bundle.decisionDocument());
    observedFormalState = state;
    decisions.saveFormalReadingObservation(
        new FormalQuestion(
            question.questionId(), question.question(), question.entryRefs(), question.clueRefs()),
        new FormalTask(
            task.taskId(),
            OntologyTaskRunner.TaskKind.LINK,
            task.readingMode(),
            task.unitUses(),
            task.requiredUnitUses()),
        corpus,
        result,
        bundle.packet(),
        null,
        null);
    return result;
  }

  /** Uses the same bounded reading protocol for an exact O2 question, without posing as OBJECT. */
  public FormalResult completeFormal(OntologySelectionReader.Question question) {
    Objects.requireNonNull(question, "formal relation question");
    return completeFormal(
        new FormalQuestion(
            question.questionId(), question.question(), question.entryRefs(), question.clueRefs()),
        new FormalTask(
            question.taskId(),
            OntologyTaskRunner.TaskKind.RELATE,
            question.readingMode(),
            question.unitUses(),
            question.requiredUnitUses()));
  }

  /**
   * Actual state observed in this coordinator's current synchronous task, including failed reads.
   */
  public FormalState observedFormalState() {
    return observedFormalState;
  }

  private FormalResult completeFormal(FormalQuestion question, FormalTask task) {
    observedFormalState = null;
    FormalRun formalRun = new FormalRun(question, task);
    LinkedHashSet<String> selectedEntries =
        new LinkedHashSet<>(new TreeSet<>(question.entryRefs()));
    LinkedHashSet<String> selectedClues = new LinkedHashSet<>(new TreeSet<>(question.clueRefs()));
    LinkedHashSet<String> discovered = new LinkedHashSet<>();
    LinkedHashSet<FormalUnitUse> available = new LinkedHashSet<>();
    LinkedHashSet<FormalUnitUse> active = new LinkedHashSet<>();
    LinkedHashSet<FormalUnitUse> required = new LinkedHashSet<>();
    for (OntologyScopeReader.UnitUse use : task.unitUses()) {
      active.add(FormalUnitUse.of(use.unitRef(), use.entryRef()));
    }
    for (OntologyScopeReader.UnitUse use : task.requiredUnitUses()) {
      required.add(FormalUnitUse.of(use.unitRef(), use.entryRef()));
    }
    if (task.readingMode() == OntologyScopeReader.ReadingMode.EXPLICIT) {
      return explicitFormalResult(
          formalRun, selectedEntries, selectedClues, discovered, active, required);
    }

    List<ReadHistory> readHistory = new ArrayList<>();
    List<String> unresolved = new ArrayList<>();
    List<FormalUnresolved> dispositions = new ArrayList<>();
    List<FormalQueryObservation> queryObservations = new ArrayList<>();
    OntologyDecisionRunner.FormalDecision lastDecision = null;
    for (int round = 0; round < maxRounds; round++) {
      OntologyReadingPacket activePacket;
      try {
        activePacket = active.isEmpty() ? null : formalPacket(active);
      } catch (IllegalArgumentException capacity) {
        if (!"ONTOLOGY_UNIT_TOO_LARGE".equals(capacity.getMessage())) {
          throw capacity;
        }
        return formalResult(
            formalRun,
            Status.INCOMPLETE,
            selectedEntries,
            selectedClues,
            discovered,
            readHistory,
            active,
            required,
            unresolved,
            dispositions,
            queryObservations,
            "ONTOLOGY_UNIT_TOO_LARGE",
            null,
            null);
      }
      if (activePacket != null && activePacket.modelInput().size() > maxFormalRequestBytes) {
        return formalResult(
            formalRun,
            Status.INCOMPLETE,
            selectedEntries,
            selectedClues,
            discovered,
            readHistory,
            active,
            required,
            unresolved,
            dispositions,
            queryObservations,
            "ONTOLOGY_READING_PACKET_TOO_LARGE",
            null,
            null,
            activePacket,
            null,
            null);
      }
      FormalVisibleScope visible =
          FormalVisibleScope.forReading(
                  corpus,
                  selectedEntries,
                  selectedClues,
                  available,
                  maxFormalNavigationEntries,
                  queryObservations)
              .withActive(active)
              .withRequired(required);
      observedFormalState =
          new FormalState(
              selectedEntries,
              selectedClues,
              discovered,
              readHistory,
              active,
              required,
              dispositions,
              queryObservations);
      OntologyDecisionRunner.FormalDecision decision;
      try {
        decision =
            decisions.formalReadingCheck(
                question.questionId(),
                question.question(),
                task,
                visible,
                List.copyOf(active),
                maxPageItems,
                maxFormalNavigationEntries,
                maxFormalUnitBytes,
                maxFormalRequestBytes,
                maxRounds - round,
                activePacket);
      } catch (StructuredModelProviderFailure failure) {
        formalResult(
            formalRun,
            Status.INCOMPLETE,
            selectedEntries,
            selectedClues,
            discovered,
            readHistory,
            active,
            required,
            unresolved,
            dispositions,
            queryObservations,
            "ONTOLOGY_READING_PROVIDER_FAILURE",
            null,
            null,
            activePacket,
            failure.requestStarted() ? visible : null,
            failure);
        throw failure;
      } catch (IllegalArgumentException capacity) {
        if (!"ONTOLOGY_TASK_INPUT_TOO_LARGE".equals(capacity.getMessage())) {
          throw capacity;
        }
        return formalResult(
            formalRun,
            Status.INCOMPLETE,
            selectedEntries,
            selectedClues,
            discovered,
            readHistory,
            active,
            required,
            unresolved,
            dispositions,
            queryObservations,
            "ONTOLOGY_TASK_INPUT_TOO_LARGE",
            null,
            null,
            activePacket,
            null,
            null);
      }
      lastDecision = decision;
      if (activePacket != null) {
        for (FormalUnitUse use : active) {
          boolean alreadyDispatched = false;
          for (ReadHistory previous : readHistory) {
            if (previous.unitRef().equals(use.unitRef())
                && previous.entryRef().equals(use.entryRef())) {
              alreadyDispatched = true;
              break;
            }
          }
          if (!alreadyDispatched) {
            readHistory.add(
                new ReadHistory(
                    round + 1,
                    decision.decision().jobKey(),
                    use.unitRef(),
                    use.entryRef(),
                    activePacket.packetId()));
            required.remove(use);
          }
        }
      }
      ValidatedFormalResponse response =
          validateFormalResponse(
              corpus,
              json.parseCanonical(decision.decision().output()),
              visible,
              maxPageItems,
              maxFormalNavigationEntries);
      requireSelectedRemovals(response.entrySelection(), selectedEntries);
      requireSelectedRemovals(response.clueSelection(), selectedClues);
      boolean requiredChanged =
          addUnreadRequirements(required, response.requiredUnitUses(), readHistory);
      boolean selectionChanged =
          applySelection(selectedEntries, response.entrySelection(), visible.displayedEntries())
              | applySelection(selectedClues, response.clueSelection(), visible.displayedClues());
      for (SelectionRemoval removal : response.entrySelection().removals()) {
        List<FormalUnitUse> affected =
            required.stream()
                .filter(use -> removal.ref().equals(use.entryRef()))
                .sorted(formalUseOrder())
                .toList();
        if (!affected.isEmpty()) {
          requiredChanged |= required.removeAll(affected);
          FormalUnresolved disposition =
              new FormalUnresolved(removal.reason(), Disposition.EXCLUDED_FROM_TASK, affected);
          dispositions.add(disposition);
          unresolved.add(disposition.reason());
        }
      }
      for (FormalUnresolved disposition : response.unresolved()) {
        dispositions.add(disposition);
        unresolved.add(disposition.reason());
        if (disposition.disposition() == Disposition.EXCLUDED_FROM_TASK) {
          if (!required.containsAll(disposition.unitUses())) {
            throw new IllegalArgumentException("ONTOLOGY_READING_REQUIRED_DISPOSITION_INVALID");
          }
          requiredChanged |= required.removeAll(disposition.unitUses());
        }
      }
      LinkedHashSet<FormalUnitUse> nextActive = new LinkedHashSet<>(response.retainedUnitUses());
      observedFormalState =
          new FormalState(
              selectedEntries,
              selectedClues,
              discovered,
              readHistory,
              nextActive,
              required,
              dispositions,
              queryObservations);
      boolean discoveredChanged = false;
      for (FormalAction action : response.actions()) {
        if (action instanceof FormalQuery query) {
          FormalQueryObservation observation = formalQuery(query);
          boolean observationChanged = !queryObservations.contains(observation);
          queryObservations.add(observation);
          List<FormalUnitUse> queryResults =
              observation.items().stream().map(FormalQueryItem::unitUse).toList();
          discoveredChanged |= observationChanged | available.addAll(queryResults);
          queryResults.forEach(use -> discovered.add(use.unitRef()));
        } else {
          FormalRead read = (FormalRead) action;
          try {
            formalPacket(Set.of(read.unitUse()));
          } catch (IllegalArgumentException capacity) {
            if (!"ONTOLOGY_UNIT_TOO_LARGE".equals(capacity.getMessage())) {
              throw capacity;
            }
            return formalResult(
                Status.INCOMPLETE,
                selectedEntries,
                selectedClues,
                discovered,
                readHistory,
                active,
                required,
                unresolved,
                dispositions,
                queryObservations,
                "ONTOLOGY_UNIT_TOO_LARGE",
                null,
                decision);
          }
          nextActive.add(read.unitUse());
        }
      }
      boolean activeChanged = !nextActive.equals(active);
      String outcome = response.decision();
      if ("UNRESOLVED".equals(outcome)) {
        return formalResult(
            Status.UNRESOLVED,
            selectedEntries,
            selectedClues,
            discovered,
            readHistory,
            nextActive,
            required,
            unresolved,
            dispositions,
            queryObservations,
            "",
            null,
            decision);
      }
      if ("READY_TO_EXTRACT".equals(outcome)) {
        if (!required.isEmpty()) {
          return formalResult(
              Status.INCOMPLETE,
              selectedEntries,
              selectedClues,
              discovered,
              readHistory,
              nextActive,
              required,
              unresolved,
              dispositions,
              queryObservations,
              "ONTOLOGY_READING_REQUIRED_UNIT_UNREAD",
              null,
              decision);
        }
        if (nextActive.isEmpty()) {
          return formalResult(
              Status.INCOMPLETE,
              selectedEntries,
              selectedClues,
              discovered,
              readHistory,
              nextActive,
              required,
              unresolved,
              dispositions,
              queryObservations,
              "ONTOLOGY_READING_EMPTY_PACKET_INVALID",
              null,
              decision);
        }
        OntologyReadingPacket frozen;
        try {
          frozen = formalPacket(nextActive);
        } catch (IllegalArgumentException capacity) {
          if (!"ONTOLOGY_UNIT_TOO_LARGE".equals(capacity.getMessage())) {
            throw capacity;
          }
          return formalResult(
              Status.INCOMPLETE,
              selectedEntries,
              selectedClues,
              discovered,
              readHistory,
              nextActive,
              required,
              unresolved,
              dispositions,
              queryObservations,
              "ONTOLOGY_UNIT_TOO_LARGE",
              null,
              decision);
        }
        if (frozen.modelInput().size() > maxFormalRequestBytes) {
          return formalResult(
              Status.INCOMPLETE,
              selectedEntries,
              selectedClues,
              discovered,
              readHistory,
              nextActive,
              required,
              unresolved,
              dispositions,
              queryObservations,
              "ONTOLOGY_READING_PACKET_TOO_LARGE",
              null,
              decision);
        }
        return formalResult(
            Status.READY,
            selectedEntries,
            selectedClues,
            discovered,
            readHistory,
            nextActive,
            required,
            unresolved,
            dispositions,
            queryObservations,
            "",
            frozen,
            decision);
      }
      if (!activeChanged && !selectionChanged && !requiredChanged && !discoveredChanged) {
        return formalResult(
            Status.INCOMPLETE,
            selectedEntries,
            selectedClues,
            discovered,
            readHistory,
            nextActive,
            required,
            unresolved,
            dispositions,
            queryObservations,
            "ONTOLOGY_READING_NO_PROGRESS",
            null,
            decision);
      }
      active = nextActive;
    }
    return formalResult(
        Status.INCOMPLETE,
        selectedEntries,
        selectedClues,
        discovered,
        readHistory,
        active,
        required,
        unresolved,
        dispositions,
        queryObservations,
        required.isEmpty()
            ? "ONTOLOGY_READING_ROUND_LIMIT"
            : "ONTOLOGY_READING_REQUIRED_UNIT_UNREAD",
        null,
        lastDecision);
  }

  private FormalResult explicitFormalResult(
      FormalRun formalRun,
      Set<String> selectedEntries,
      Set<String> selectedClues,
      Set<String> discovered,
      Set<FormalUnitUse> active,
      Set<FormalUnitUse> required) {
    if (!required.isEmpty() && !active.containsAll(required)) {
      return formalResult(
          formalRun,
          Status.INCOMPLETE,
          selectedEntries,
          selectedClues,
          discovered,
          List.of(),
          active,
          required,
          List.of(),
          List.of(),
          List.of(),
          "ONTOLOGY_READING_REQUIRED_UNIT_UNREAD",
          null,
          null);
    }
    if (active.isEmpty()) {
      return formalResult(
          formalRun,
          Status.INCOMPLETE,
          selectedEntries,
          selectedClues,
          discovered,
          List.of(),
          active,
          required,
          List.of(),
          List.of(),
          List.of(),
          "ONTOLOGY_READING_EMPTY_PACKET_INVALID",
          null,
          null);
    }
    OntologyReadingPacket packet;
    try {
      packet = formalPacket(active);
    } catch (IllegalArgumentException capacity) {
      if (!"ONTOLOGY_UNIT_TOO_LARGE".equals(capacity.getMessage())) {
        throw capacity;
      }
      return formalResult(
          formalRun,
          Status.INCOMPLETE,
          selectedEntries,
          selectedClues,
          discovered,
          List.of(),
          active,
          required,
          List.of(),
          List.of(),
          List.of(),
          "ONTOLOGY_UNIT_TOO_LARGE",
          null,
          null);
    }
    if (packet.modelInput().size() > maxFormalRequestBytes) {
      return formalResult(
          formalRun,
          Status.INCOMPLETE,
          selectedEntries,
          selectedClues,
          discovered,
          List.of(),
          active,
          required,
          List.of(),
          List.of(),
          List.of(),
          "ONTOLOGY_READING_PACKET_TOO_LARGE",
          null,
          null,
          packet,
          null,
          null);
    }
    return formalResult(
        formalRun,
        Status.READY,
        selectedEntries,
        selectedClues,
        discovered,
        List.of(),
        active,
        required,
        List.of(),
        List.of(),
        List.of(),
        "",
        packet,
        null);
  }

  private FormalResult formalResult(
      FormalRun formalRun,
      Status status,
      Set<String> selectedEntries,
      Set<String> selectedClues,
      Set<String> discovered,
      List<ReadHistory> readHistory,
      Set<FormalUnitUse> active,
      Set<FormalUnitUse> required,
      List<String> unresolved,
      List<FormalUnresolved> dispositions,
      List<FormalQueryObservation> queryObservations,
      String issueCode,
      OntologyReadingPacket frozen,
      OntologyDecisionRunner.FormalDecision decision) {
    return formalResult(
        formalRun,
        status,
        selectedEntries,
        selectedClues,
        discovered,
        readHistory,
        active,
        required,
        unresolved,
        dispositions,
        queryObservations,
        issueCode,
        frozen,
        decision,
        frozen,
        null,
        null);
  }

  private FormalResult formalResult(
      FormalRun formalRun,
      Status status,
      Set<String> selectedEntries,
      Set<String> selectedClues,
      Set<String> discovered,
      List<ReadHistory> readHistory,
      Set<FormalUnitUse> active,
      Set<FormalUnitUse> required,
      List<String> unresolved,
      List<FormalUnresolved> dispositions,
      List<FormalQueryObservation> queryObservations,
      String issueCode,
      OntologyReadingPacket frozen,
      OntologyDecisionRunner.FormalDecision decision,
      OntologyReadingPacket material,
      FormalVisibleScope dispatchedVisibleScope,
      StructuredModelProviderFailure failure) {
    FormalResult result =
        formalResult(
            status,
            selectedEntries,
            selectedClues,
            discovered,
            readHistory,
            active,
            required,
            unresolved,
            dispositions,
            queryObservations,
            issueCode,
            frozen,
            decision);
    if (decision == null) {
      decisions.saveFormalReadingObservation(
          formalRun.question(),
          formalRun.task(),
          corpus,
          result,
          material,
          dispatchedVisibleScope,
          failure);
    }
    return result;
  }

  private FormalResult formalResult(
      Status status,
      Set<String> selectedEntries,
      Set<String> selectedClues,
      Set<String> discovered,
      List<ReadHistory> readHistory,
      Set<FormalUnitUse> active,
      Set<FormalUnitUse> required,
      List<String> unresolved,
      List<FormalUnresolved> dispositions,
      List<FormalQueryObservation> queryObservations,
      String issueCode,
      OntologyReadingPacket frozen,
      OntologyDecisionRunner.FormalDecision decision) {
    FreezeEnvelope envelope =
        new FreezeEnvelope(
            decision == null ? "formal-reading-v1" : decision.prompt(),
            decision == null ? null : decision.validationSchema(),
            null,
            maxPageItems);
    FormalResult result =
        new FormalResult(
            status,
            new FormalState(
                selectedEntries,
                selectedClues,
                discovered,
                readHistory,
                active,
                required,
                dispositions,
                queryObservations),
            List.copyOf(unresolved),
            issueCode,
            frozen,
            frozen,
            frozen,
            envelope);
    observedFormalState = result.state();
    decisions.saveFormalReadingState(decision, result);
    return result;
  }

  private OntologyReadingPacket formalPacket(Set<FormalUnitUse> uses) {
    if (uses.isEmpty()) {
      throw new IllegalArgumentException("ONTOLOGY_READING_PACKET_EMPTY");
    }
    List<UnitHandle> handles = new ArrayList<>();
    for (FormalUnitUse use : uses) {
      UnitHandle canonical = corpus.aliases().unit(use.unitRef());
      String entryId = corpus.aliases().entry(use.entryRef()).entryId();
      handles.add(new UnitHandle(entryId, canonical.kind(), canonical.originalId()));
    }
    if (corpus.usesBusinessLinkNavigation()) {
      return OntologyReadingPacket.formalV5(corpus, handles, maxFormalUnitBytes);
    }
    return formalV4
        ? OntologyReadingPacket.formalV4(corpus, handles, maxFormalUnitBytes)
        : OntologyReadingPacket.formal(corpus, handles, maxFormalUnitBytes);
  }

  private FormalQueryObservation formalQuery(FormalQuery query) {
    OntologyEvidenceCorpus.AliasCatalog aliases = corpus.aliases();
    UnitPage page;
    if ("ENTRY_UNITS".equals(query.queryKind())) {
      page =
          corpus.entryUnits(aliases.entry(query.keyRef()).entryId(), query.offset(), query.limit());
    } else if ("METHOD_USES".equals(query.queryKind())) {
      page = corpus.methodUses(queryLookupKey(aliases, query), query.offset(), query.limit());
    } else if ("STATEMENT_USES".equals(query.queryKind())) {
      page = corpus.statementUses(queryLookupKey(aliases, query), query.offset(), query.limit());
    } else if ("TABLE_STATEMENTS".equals(query.queryKind())) {
      page =
          corpus.tableStatements(
              aliases.clue(query.keyRef()).lookupKey(), query.offset(), query.limit());
    } else if ("COLUMN_STATEMENTS".equals(query.queryKind())) {
      page =
          corpus.columnStatements(
              aliases.clue(query.keyRef()).lookupKey(), query.offset(), query.limit());
    } else if ("CONTROL_USES".equals(query.queryKind())) {
      page =
          corpus.controlUses(
              aliases.clue(query.keyRef()).lookupKey(), query.offset(), query.limit());
    } else {
      SearchResult result = corpus.searchLiteral(query.literal(), query.offset(), query.limit());
      List<FormalQueryItem> items =
          result.matches().stream()
              .map(
                  match -> {
                    UnitHandle handle =
                        new UnitHandle(match.entryId(), match.kind(), match.originalId());
                    return new FormalQueryItem(
                        FormalUnitUse.of(
                            aliases.unitRef(handle), aliases.entryRef(match.entryId())),
                        match.excerpt(),
                        aliases.clueRefsFor(handle));
                  })
              .sorted(formalQueryItemOrder())
              .toList();
      return new FormalQueryObservation(
          query.queryKind(),
          query.keyRef(),
          query.literal(),
          query.offset(),
          query.limit(),
          result.totalMatches(),
          items);
    }
    List<FormalQueryItem> items =
        page.items().stream()
            .map(
                handle ->
                    new FormalQueryItem(
                        FormalUnitUse.of(
                            aliases.unitRef(handle), aliases.entryRef(handle.entryId())),
                        null,
                        aliases.clueRefsFor(handle)))
            .sorted(formalQueryItemOrder())
            .toList();
    return new FormalQueryObservation(
        query.queryKind(),
        query.keyRef(),
        null,
        query.offset(),
        query.limit(),
        page.total(),
        items);
  }

  private static String queryLookupKey(
      OntologyEvidenceCorpus.AliasCatalog aliases, FormalQuery query) {
    if (query.keyRef().startsWith("K")) {
      return aliases.clue(query.keyRef()).lookupKey();
    }
    return aliases.unit(query.keyRef()).originalId();
  }

  private static boolean applySelection(
      Set<String> selected, SelectionChange change, Set<String> visibleSelection) {
    boolean changed = false;
    for (SelectionRemoval removal : change.removals()) {
      if (!visibleSelection.contains(removal.ref())) {
        throw new IllegalArgumentException("ONTOLOGY_READING_REFERENCE_NOT_DISPLAYED");
      }
      changed |= selected.remove(removal.ref());
    }
    for (String added : change.addRefs()) {
      if (!visibleSelection.contains(added)) {
        throw new IllegalArgumentException("ONTOLOGY_READING_REFERENCE_NOT_DISPLAYED");
      }
      changed |= selected.add(added);
    }
    return changed;
  }

  private static void requireSelectedRemovals(SelectionChange change, Set<String> selected) {
    for (SelectionRemoval removal : change.removals()) {
      if (!selected.contains(removal.ref())) {
        throw new IllegalArgumentException("ONTOLOGY_READING_SELECTION_REMOVAL_INVALID");
      }
    }
  }

  private static boolean addUnreadRequirements(
      Set<FormalUnitUse> required, List<FormalUnitUse> claimed, List<ReadHistory> readHistory) {
    boolean changed = false;
    for (FormalUnitUse use : claimed) {
      boolean dispatched =
          readHistory.stream()
              .anyMatch(
                  history ->
                      history.unitRef().equals(use.unitRef())
                          && history.entryRef().equals(use.entryRef()));
      if (!dispatched) {
        changed |= required.add(use);
      }
    }
    return changed;
  }

  static ValidatedFormalResponse validateFormalResponse(
      OntologyEvidenceCorpus corpus,
      JsonNode response,
      FormalVisibleScope visible,
      int maxActionsPerRound) {
    return validateFormalResponse(corpus, response, visible, maxActionsPerRound, Integer.MAX_VALUE);
  }

  static ValidatedFormalResponse validateFormalResponse(
      OntologyEvidenceCorpus corpus,
      JsonNode response,
      FormalVisibleScope visible,
      int maxActionsPerRound,
      int maxNavigationEntries) {
    if (corpus == null
        || visible == null
        || visible.corpus() != corpus
        || response == null
        || !response.isObject()
        || maxActionsPerRound < 1
        || maxNavigationEntries < 1) {
      throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
    }
    requireFormalFields(
        response,
        Set.of(
            "schemaVersion",
            "decision",
            "entrySelection",
            "clueSelection",
            "retainedUnitUses",
            "requiredUnitUses",
            "actions",
            "unresolved"));
    String expectedVersion =
        corpus.usesBusinessLinkNavigation() ? "reading-response-v4" : "reading-response-v3";
    if (!expectedVersion.equals(response.path("schemaVersion").asText())) {
      throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
    }
    String decision = response.path("decision").asText();
    if (!Set.of("NEEDS_MORE_MATERIAL", "READY_TO_EXTRACT", "UNRESOLVED").contains(decision)) {
      throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
    }
    SelectionChange entrySelection =
        selectionChange(
            response.path("entrySelection"),
            visible.displayedEntries(),
            visible.selectedEntries(),
            "E[1-9][0-9]*");
    SelectionChange clueSelection =
        selectionChange(
            response.path("clueSelection"),
            visible.displayedClues(),
            visible.selectedClues(),
            "K[1-9][0-9]*");
    List<FormalUnitUse> retained =
        formalUses(response.path("retainedUnitUses"), corpus, visible, true);
    List<FormalUnitUse> required =
        formalUses(response.path("requiredUnitUses"), corpus, visible, false);
    JsonNode rawActions = response.path("actions");
    if (!rawActions.isArray()) {
      throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
    }
    if (rawActions.size() > maxActionsPerRound) {
      throw new IllegalArgumentException("ONTOLOGY_READING_ACTION_LIMIT_EXCEEDED");
    }
    List<FormalAction> actions = new ArrayList<>();
    for (JsonNode rawAction : rawActions) {
      FormalAction action = formalAction(rawAction, corpus, visible);
      if (action instanceof FormalQuery query && query.limit() > maxNavigationEntries) {
        throw new IllegalArgumentException("ONTOLOGY_READING_QUERY_LIMIT_INVALID");
      }
      actions.add(action);
    }
    if ("READY_TO_EXTRACT".equals(decision) && !actions.isEmpty()) {
      throw new IllegalArgumentException("ONTOLOGY_READING_READY_WITH_ACTION_INVALID");
    }
    JsonNode rawUnresolved = response.path("unresolved");
    if (!rawUnresolved.isArray()) {
      throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
    }
    List<FormalUnresolved> unresolved = new ArrayList<>();
    for (JsonNode raw : rawUnresolved) {
      if (!raw.isObject()) {
        throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
      }
      requireFormalFields(raw, Set.of("reason", "disposition", "unitUses"));
      String reason = raw.path("reason").asText();
      if (reason.isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
      }
      Disposition disposition;
      try {
        disposition = Disposition.valueOf(raw.path("disposition").asText());
      } catch (IllegalArgumentException invalid) {
        throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
      }
      List<FormalUnitUse> uses = formalUses(raw.path("unitUses"), corpus, visible, false);
      if (disposition == Disposition.EXCLUDED_FROM_TASK && uses.isEmpty()) {
        throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
      }
      unresolved.add(new FormalUnresolved(reason, disposition, uses));
    }
    return new ValidatedFormalResponse(
        decision, entrySelection, clueSelection, retained, required, actions, unresolved);
  }

  private static SelectionChange selectionChange(
      JsonNode rawSelection, Set<String> visibleRefs, Set<String> selectedRefs, String pattern) {
    if (!rawSelection.isObject()) {
      throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
    }
    requireFormalFields(rawSelection, Set.of("addRefs", "remove"));
    JsonNode rawAdditions = rawSelection.path("addRefs");
    JsonNode rawRemovals = rawSelection.path("remove");
    if (!rawAdditions.isArray() || !rawRemovals.isArray()) {
      throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
    }
    LinkedHashSet<String> additions = new LinkedHashSet<>();
    for (JsonNode raw : rawAdditions) {
      if (!raw.isTextual()
          || !raw.asText().matches(pattern)
          || !visibleRefs.contains(raw.asText())) {
        throw new IllegalArgumentException("ONTOLOGY_READING_REFERENCE_NOT_DISPLAYED");
      }
      if (!additions.add(raw.asText())) {
        throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
      }
    }
    LinkedHashSet<String> removals = new LinkedHashSet<>();
    List<SelectionRemoval> removed = new ArrayList<>();
    for (JsonNode raw : rawRemovals) {
      if (!raw.isObject()) {
        throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
      }
      requireFormalFields(raw, Set.of("ref", "reason"));
      String ref = raw.path("ref").asText();
      if (!ref.matches(pattern)
          || !raw.path("reason").isTextual()
          || raw.path("reason").asText().isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
      }
      if (!visibleRefs.contains(ref)) {
        throw new IllegalArgumentException("ONTOLOGY_READING_REFERENCE_NOT_DISPLAYED");
      }
      if (!selectedRefs.contains(ref)) {
        throw new IllegalArgumentException("ONTOLOGY_READING_SELECTION_REMOVAL_INVALID");
      }
      if (!removals.add(ref)) {
        throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
      }
      removed.add(new SelectionRemoval(ref, raw.path("reason").asText()));
    }
    if (additions.stream().anyMatch(removals::contains)) {
      throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
    }
    return new SelectionChange(List.copyOf(additions), List.copyOf(removed));
  }

  private static List<FormalUnitUse> formalUses(
      JsonNode rawUses,
      OntologyEvidenceCorpus corpus,
      FormalVisibleScope visible,
      boolean activeOnly) {
    if (!rawUses.isArray()) {
      throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
    }
    LinkedHashSet<FormalUnitUse> uses = new LinkedHashSet<>();
    for (JsonNode rawUse : rawUses) {
      FormalUnitUse use = resolveFormalUse(rawUse, corpus);
      if (activeOnly && !visible.activeUnits().contains(use)) {
        throw new IllegalArgumentException("ONTOLOGY_READING_REFERENCE_NOT_DISPLAYED");
      }
      if (!activeOnly
          && !visible.availableUnits().contains(use)
          && !visible.requiredUnits().contains(use)) {
        throw new IllegalArgumentException("ONTOLOGY_READING_REFERENCE_NOT_DISPLAYED");
      }
      if (!uses.add(use)) {
        throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
      }
    }
    return List.copyOf(uses);
  }

  private static FormalAction formalAction(
      JsonNode rawAction, OntologyEvidenceCorpus corpus, FormalVisibleScope visible) {
    if (!rawAction.isObject()) {
      throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
    }
    String kind = rawAction.path("kind").asText();
    if ("READ".equals(kind)) {
      requireFormalFields(rawAction, Set.of("kind", "unitRef", "entryRef"));
      FormalUnitUse use = resolveFormalActionUse(rawAction, corpus);
      if (!visible.availableUnits().contains(use)) {
        throw new IllegalArgumentException("ONTOLOGY_READING_REFERENCE_NOT_DISPLAYED");
      }
      return new FormalRead(use);
    }
    if (!"QUERY".equals(kind)) {
      throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
    }
    String queryKind = rawAction.path("queryKind").asText();
    if ("LITERAL_SEARCH".equals(queryKind)) {
      requireFormalFields(rawAction, Set.of("kind", "queryKind", "query", "offset", "limit"));
      String literal = rawAction.path("query").asText();
      if (literal.isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
      }
      return new FormalQuery(queryKind, null, literal, offset(rawAction), limit(rawAction));
    }
    requireFormalFields(rawAction, Set.of("kind", "queryKind", "keyRef", "offset", "limit"));
    String keyRef = rawAction.path("keyRef").asText();
    requireQueryReference(corpus, visible, queryKind, keyRef);
    return new FormalQuery(queryKind, keyRef, null, offset(rawAction), limit(rawAction));
  }

  private static void requireQueryReference(
      OntologyEvidenceCorpus corpus, FormalVisibleScope visible, String queryKind, String keyRef) {
    try {
      switch (queryKind) {
        case "ENTRY_UNITS" -> {
          corpus.aliases().entry(keyRef);
          if (!visible.displayedEntries().contains(keyRef)) {
            throw new IllegalArgumentException("ONTOLOGY_READING_REFERENCE_NOT_DISPLAYED");
          }
        }
        case "METHOD_USES" ->
            requireClueOrActiveUnit(
                corpus,
                visible,
                keyRef,
                OntologyEvidenceCorpus.ClueKind.METHOD,
                OntologyEvidenceCorpus.UnitKind.JAVA_METHOD);
        case "STATEMENT_USES" ->
            requireClueOrActiveUnit(
                corpus,
                visible,
                keyRef,
                OntologyEvidenceCorpus.ClueKind.STATEMENT,
                OntologyEvidenceCorpus.UnitKind.XML_STATEMENT);
        case "TABLE_STATEMENTS" ->
            requireClue(corpus, visible, keyRef, OntologyEvidenceCorpus.ClueKind.TABLE);
        case "COLUMN_STATEMENTS" ->
            requireClue(corpus, visible, keyRef, OntologyEvidenceCorpus.ClueKind.COLUMN);
        case "CONTROL_USES" -> {
          if (!corpus.usesBusinessLinkNavigation()) {
            throw new IllegalArgumentException("ONTOLOGY_READING_QUERY_INVALID");
          }
          requireClue(corpus, visible, keyRef, OntologyEvidenceCorpus.ClueKind.CONTROL_REFERENCE);
        }
        default -> throw new IllegalArgumentException("ONTOLOGY_READING_QUERY_INVALID");
      }
    } catch (IllegalArgumentException invalid) {
      if ("ONTOLOGY_READING_REFERENCE_NOT_DISPLAYED".equals(invalid.getMessage())) {
        throw invalid;
      }
      throw new IllegalArgumentException("ONTOLOGY_READING_REFERENCE_NOT_DISPLAYED");
    }
  }

  private static void requireClue(
      OntologyEvidenceCorpus corpus,
      FormalVisibleScope visible,
      String clueRef,
      OntologyEvidenceCorpus.ClueKind expectedKind) {
    if (!visible.displayedClues().contains(clueRef)
        || corpus.aliases().clue(clueRef).kind() != expectedKind) {
      throw new IllegalArgumentException("ONTOLOGY_READING_REFERENCE_NOT_DISPLAYED");
    }
  }

  private static void requireClueOrActiveUnit(
      OntologyEvidenceCorpus corpus,
      FormalVisibleScope visible,
      String reference,
      OntologyEvidenceCorpus.ClueKind expectedClue,
      OntologyEvidenceCorpus.UnitKind expectedUnit) {
    if (reference.startsWith("K")) {
      requireClue(corpus, visible, reference, expectedClue);
      return;
    }
    OntologyEvidenceCorpus.UnitHandle canonical = corpus.aliases().unit(reference);
    if (canonical.kind() != expectedUnit) {
      throw new IllegalArgumentException("ONTOLOGY_READING_REFERENCE_NOT_DISPLAYED");
    }
    boolean active =
        visible.activeUnits().stream()
            .filter(use -> reference.equals(use.unitRef()))
            .anyMatch(
                use -> {
                  corpus.aliases().read(use.unitRef(), use.entryRef());
                  return true;
                });
    if (!active) {
      throw new IllegalArgumentException("ONTOLOGY_READING_REFERENCE_NOT_DISPLAYED");
    }
  }

  private static FormalUnitUse resolveFormalUse(JsonNode rawUse, OntologyEvidenceCorpus corpus) {
    requireFormalFields(rawUse, Set.of("unitRef", "entryRef"));
    return resolveFormalUseFields(rawUse, corpus);
  }

  private static FormalUnitUse resolveFormalActionUse(
      JsonNode rawAction, OntologyEvidenceCorpus corpus) {
    requireFormalFields(rawAction, Set.of("kind", "unitRef", "entryRef"));
    return resolveFormalUseFields(rawAction, corpus);
  }

  private static FormalUnitUse resolveFormalUseFields(
      JsonNode rawUse, OntologyEvidenceCorpus corpus) {
    String unitRef = rawUse.path("unitRef").asText();
    String entryRef = rawUse.path("entryRef").asText();
    if (!unitRef.matches("U[1-9][0-9]*") || !entryRef.matches("E[1-9][0-9]*")) {
      throw new IllegalArgumentException("ONTOLOGY_READING_REFERENCE_NOT_DISPLAYED");
    }
    try {
      corpus.aliases().read(unitRef, entryRef);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("ONTOLOGY_READING_REFERENCE_NOT_DISPLAYED");
    }
    return FormalUnitUse.of(unitRef, entryRef);
  }

  private static int offset(JsonNode action) {
    if (!action.path("offset").canConvertToInt() || action.path("offset").asInt() < 0) {
      throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
    }
    return action.path("offset").asInt();
  }

  private static int limit(JsonNode action) {
    if (!action.path("limit").canConvertToInt() || action.path("limit").asInt() < 1) {
      throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
    }
    return action.path("limit").asInt();
  }

  private static Comparator<FormalUnitUse> formalUseOrder() {
    return Comparator.comparing(FormalUnitUse::unitRef).thenComparing(FormalUnitUse::entryRef);
  }

  private static Comparator<FormalQueryItem> formalQueryItemOrder() {
    return Comparator.comparing((FormalQueryItem item) -> item.unitUse().unitRef())
        .thenComparing(item -> item.unitUse().entryRef());
  }

  private static void requireFormalFields(JsonNode node, Set<String> fields) {
    node.fieldNames()
        .forEachRemaining(
            field -> {
              if (!fields.contains(field)) {
                throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
              }
            });
    for (String field : fields) {
      if (!node.has(field)) {
        throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
      }
    }
  }

  public record FormalUnitUse(String unitRef, String entryRef) {
    public static FormalUnitUse of(String unitRef, String entryRef) {
      return new FormalUnitUse(unitRef, entryRef);
    }
  }

  public record FormalVisibleScope(
      OntologyEvidenceCorpus corpus,
      Set<String> displayedEntries,
      Set<String> displayedClues,
      Set<String> selectedEntries,
      Set<String> selectedClues,
      Set<FormalUnitUse> availableUnits,
      Set<FormalUnitUse> activeUnits,
      Set<FormalUnitUse> requiredUnits,
      List<FormalNavigationEntry> navigation,
      List<FormalQueryObservation> queryObservations) {
    public FormalVisibleScope {
      corpus = Objects.requireNonNull(corpus, "ontology corpus");
      displayedEntries = Set.copyOf(displayedEntries);
      displayedClues = Set.copyOf(displayedClues);
      selectedEntries = Set.copyOf(selectedEntries);
      selectedClues = Set.copyOf(selectedClues);
      availableUnits = Set.copyOf(availableUnits);
      activeUnits = Set.copyOf(activeUnits);
      requiredUnits = Set.copyOf(requiredUnits);
      navigation = List.copyOf(navigation);
      queryObservations = List.copyOf(queryObservations);
    }

    public static FormalVisibleScope of(
        OntologyEvidenceCorpus corpus,
        Set<String> selectedEntries,
        Set<String> selectedClues,
        Set<FormalUnitUse> visibleUnits) {
      return forReading(corpus, selectedEntries, selectedClues, visibleUnits, 100, List.of());
    }

    static FormalVisibleScope forReading(
        OntologyEvidenceCorpus corpus,
        Set<String> selectedEntries,
        Set<String> selectedClues,
        Set<FormalUnitUse> visibleUnits,
        int maxNavigationEntries,
        List<FormalQueryObservation> queryObservations) {
      if (selectedEntries == null || selectedClues == null || visibleUnits == null) {
        throw new IllegalArgumentException("ONTOLOGY_READING_VISIBLE_SCOPE_INVALID");
      }
      if (maxNavigationEntries < 1 || queryObservations == null) {
        throw new IllegalArgumentException("ONTOLOGY_READING_VISIBLE_SCOPE_INVALID");
      }
      for (String entryRef : selectedEntries) {
        corpus.aliases().entry(entryRef);
      }
      for (String clueRef : selectedClues) {
        corpus.aliases().clue(clueRef);
      }
      for (FormalUnitUse use : visibleUnits) {
        corpus.aliases().read(use.unitRef(), use.entryRef());
      }
      LinkedHashSet<String> displayedEntries = new LinkedHashSet<>();
      corpus.navigation(0, maxNavigationEntries).entries().stream()
          .map(entry -> corpus.aliases().entryRef(entry.entryId()))
          .forEach(displayedEntries::add);
      selectedEntries.stream().sorted().forEach(displayedEntries::add);
      queryObservations.stream()
          .flatMap(observation -> observation.items().stream())
          .map(item -> item.unitUse().entryRef())
          .sorted()
          .forEach(displayedEntries::add);
      LinkedHashSet<String> displayedClues = new LinkedHashSet<>();
      LinkedHashSet<FormalUnitUse> available = new LinkedHashSet<>(visibleUnits);
      List<FormalNavigationEntry> navigation = new ArrayList<>();
      for (String entryRef : displayedEntries) {
        OntologyEvidenceCorpus.EntrySummary entry = corpus.aliases().entry(entryRef);
        FormalUnitUse controller = controllerUse(corpus, entryRef, entry);
        if (controller != null) {
          available.add(controller);
        }
        OntologyEvidenceCorpus.EntryCluePage cluePage = corpus.entryClues(entry.entryId(), 2);
        List<FormalNavigationClue> clues = new ArrayList<>();
        cluePage
            .clues()
            .forEach(
                clue -> {
                  String clueRef = corpus.aliases().clueRef(clue.kind(), clue.lookupKey());
                  FormalUnitUse readable =
                      FormalUnitUse.of(
                          corpus.aliases().unitRef(clue.readableUnit()),
                          corpus.aliases().entryRef(clue.readableUnit().entryId()));
                  displayedClues.add(clueRef);
                  available.add(readable);
                  clues.add(new FormalNavigationClue(clueRef, clue, readable));
                });
        navigation.add(
            new FormalNavigationEntry(
                entryRef,
                entry,
                controller,
                clues,
                cluePage.disclosure(),
                cluePage.sqlAnalysesWithoutAst()));
      }
      selectedClues.stream().sorted().forEach(displayedClues::add);
      queryObservations.stream()
          .flatMap(observation -> observation.items().stream())
          .flatMap(item -> item.clueRefs().stream())
          .sorted()
          .forEach(displayedClues::add);
      return new FormalVisibleScope(
          corpus,
          displayedEntries,
          displayedClues,
          selectedEntries,
          selectedClues,
          available,
          visibleUnits,
          Set.of(),
          navigation,
          queryObservations);
    }

    FormalVisibleScope withActive(Set<FormalUnitUse> active) {
      LinkedHashSet<FormalUnitUse> combined = new LinkedHashSet<>(availableUnits);
      combined.addAll(active);
      return new FormalVisibleScope(
          corpus,
          displayedEntries,
          displayedClues,
          selectedEntries,
          selectedClues,
          combined,
          active,
          requiredUnits,
          navigation,
          queryObservations);
    }

    FormalVisibleScope withRequired(Set<FormalUnitUse> required) {
      return new FormalVisibleScope(
          corpus,
          displayedEntries,
          displayedClues,
          selectedEntries,
          selectedClues,
          availableUnits,
          activeUnits,
          required,
          navigation,
          queryObservations);
    }

    private static FormalUnitUse controllerUse(
        OntologyEvidenceCorpus corpus, String entryRef, OntologyEvidenceCorpus.EntrySummary entry) {
      if (entry.methodKey().isBlank()) {
        return null;
      }
      try {
        OntologyEvidenceCorpus.UnitHandle handle =
            new OntologyEvidenceCorpus.UnitHandle(
                entry.entryId(), OntologyEvidenceCorpus.UnitKind.JAVA_METHOD, entry.methodKey());
        return FormalUnitUse.of(corpus.aliases().unitRef(handle), entryRef);
      } catch (IllegalArgumentException unavailable) {
        return null;
      }
    }
  }

  public record FormalNavigationEntry(
      String entryRef,
      OntologyEvidenceCorpus.EntrySummary entry,
      FormalUnitUse controllerUnit,
      List<FormalNavigationClue> clues,
      Map<OntologyEvidenceCorpus.ClueKind, OntologyEvidenceCorpus.ClueDisclosure> clueDisclosure,
      int sqlAnalysesWithoutAst) {
    public FormalNavigationEntry {
      clues = List.copyOf(clues);
      clueDisclosure = Map.copyOf(clueDisclosure);
      if (sqlAnalysesWithoutAst < 0) {
        throw new IllegalArgumentException("ONTOLOGY_READING_VISIBLE_SCOPE_INVALID");
      }
    }
  }

  public record FormalNavigationClue(
      String clueRef, OntologyEvidenceCorpus.NavigationClue clue, FormalUnitUse readableUnit) {}

  public record FormalQueryItem(FormalUnitUse unitUse, String excerpt, List<String> clueRefs) {
    public FormalQueryItem {
      clueRefs = List.copyOf(clueRefs);
    }
  }

  public record FormalQueryObservation(
      String queryKind,
      String keyRef,
      String literal,
      int offset,
      int limit,
      int total,
      List<FormalQueryItem> items) {
    public FormalQueryObservation {
      items = List.copyOf(items);
    }
  }

  public enum Disposition {
    UNRESOLVED,
    EXCLUDED_FROM_TASK
  }

  public record FormalUnresolved(
      String reason, Disposition disposition, List<FormalUnitUse> unitUses) {
    public FormalUnresolved {
      if (reason == null || reason.isBlank() || disposition == null) {
        throw new IllegalArgumentException("ONTOLOGY_READING_RESPONSE_INVALID");
      }
      unitUses = List.copyOf(unitUses);
    }
  }

  public record FormalState(
      List<String> selectedEntries,
      List<String> selectedClues,
      List<String> discoveredHandles,
      List<ReadHistory> readHistory,
      List<FormalUnitUse> activeUnits,
      List<FormalUnitUse> requiredButUnread,
      List<FormalUnresolved> unresolvedDispositions,
      List<FormalQueryObservation> queryObservations,
      boolean scopeNarrowed) {
    public FormalState {
      selectedEntries = List.copyOf(selectedEntries);
      selectedClues = List.copyOf(selectedClues);
      discoveredHandles = List.copyOf(discoveredHandles);
      readHistory = List.copyOf(readHistory);
      activeUnits = List.copyOf(activeUnits);
      requiredButUnread = List.copyOf(requiredButUnread);
      unresolvedDispositions = List.copyOf(unresolvedDispositions);
      queryObservations = List.copyOf(queryObservations);
    }

    FormalState(
        Set<String> selectedEntries,
        Set<String> selectedClues,
        Set<String> discoveredHandles,
        List<ReadHistory> readHistory,
        Set<FormalUnitUse> activeUnits,
        Set<FormalUnitUse> requiredButUnread,
        List<FormalUnresolved> unresolvedDispositions,
        List<FormalQueryObservation> queryObservations) {
      this(
          selectedEntries.stream().sorted().toList(),
          selectedClues.stream().sorted().toList(),
          discoveredHandles.stream().sorted().toList(),
          List.copyOf(readHistory),
          orderedUses(activeUnits),
          orderedUses(requiredButUnread),
          List.copyOf(unresolvedDispositions),
          List.copyOf(queryObservations),
          unresolvedDispositions.stream()
              .anyMatch(item -> item.disposition() == Disposition.EXCLUDED_FROM_TASK));
    }

    private static List<FormalUnitUse> orderedUses(Set<FormalUnitUse> uses) {
      return uses.stream()
          .sorted(
              Comparator.comparing(FormalUnitUse::unitRef).thenComparing(FormalUnitUse::entryRef))
          .toList();
    }
  }

  public record ReadHistory(
      int round, String requestId, String unitRef, String entryRef, String packetId) {}

  public record FreezeEnvelope(
      String prompt,
      org.sourceanalysis.app.artifact.ImmutableBytes schema,
      JsonNode draft,
      int maxActionsPerRound) {}

  public record FormalResult(
      Status status,
      FormalState state,
      List<String> unresolved,
      String issueCode,
      OntologyReadingPacket frozenPacket,
      OntologyReadingPacket extractPacket,
      OntologyReadingPacket reviewPacket,
      FreezeEnvelope freezeEnvelope,
      org.sourceanalysis.app.artifact.ImmutableBytes bundleDecisionDocument) {
    public FormalResult(
        Status status,
        FormalState state,
        List<String> unresolved,
        String issueCode,
        OntologyReadingPacket frozenPacket,
        OntologyReadingPacket extractPacket,
        OntologyReadingPacket reviewPacket,
        FreezeEnvelope freezeEnvelope) {
      this(
          status,
          state,
          unresolved,
          issueCode,
          frozenPacket,
          extractPacket,
          reviewPacket,
          freezeEnvelope,
          null);
    }

    public JsonNode bundleDecision() {
      return bundleDecisionDocument == null
          ? null
          : new CanonicalJsonCodec().parseCanonical(bundleDecisionDocument);
    }

    public FormalResult {
      unresolved = List.copyOf(unresolved);
    }
  }

  private record SelectionChange(List<String> addRefs, List<SelectionRemoval> removals) {}

  private record SelectionRemoval(String ref, String reason) {}

  private sealed interface FormalAction permits FormalQuery, FormalRead {}

  private record FormalQuery(String queryKind, String keyRef, String literal, int offset, int limit)
      implements FormalAction {}

  private record FormalRead(FormalUnitUse unitUse) implements FormalAction {}

  static record FormalQuestion(
      String questionId, String question, List<String> entryRefs, List<String> clueRefs) {
    FormalQuestion {
      entryRefs = List.copyOf(entryRefs);
      clueRefs = List.copyOf(clueRefs);
    }
  }

  static record FormalTask(
      String taskId,
      OntologyTaskRunner.TaskKind taskKind,
      OntologyScopeReader.ReadingMode readingMode,
      List<OntologyScopeReader.UnitUse> unitUses,
      List<OntologyScopeReader.UnitUse> requiredUnitUses) {
    FormalTask {
      unitUses = List.copyOf(unitUses);
      requiredUnitUses = List.copyOf(requiredUnitUses);
    }
  }

  private record FormalRun(FormalQuestion question, FormalTask task) {}

  static record ValidatedFormalResponse(
      String decision,
      SelectionChange entrySelection,
      SelectionChange clueSelection,
      List<FormalUnitUse> retainedUnitUses,
      List<FormalUnitUse> requiredUnitUses,
      List<FormalAction> actions,
      List<FormalUnresolved> unresolved) {
    ValidatedFormalResponse {
      retainedUnitUses = List.copyOf(retainedUnitUses);
      requiredUnitUses = List.copyOf(requiredUnitUses);
      actions = List.copyOf(actions);
      unresolved = List.copyOf(unresolved);
    }
  }

  private Result finish(
      OntologyDecisionRunner.Decision decision,
      int round,
      String questionId,
      Status status,
      OntologyReadingPacket packet,
      List<OntologyDecisionRunner.Decision> history,
      List<OntologyNavigationView> navigation,
      List<String> unresolved,
      String issueCode) {
    decisions.saveReadingObservation(
        decision,
        round,
        questionId,
        corpus.sourceIdentity(),
        status,
        packet,
        navigation,
        unresolved,
        issueCode);
    return new Result(status, packet, history, unresolved, issueCode);
  }

  private List<EvidenceUnit> verifyInitial(List<EvidenceUnit> initialUnits) {
    List<EvidenceUnit> selected = new ArrayList<>();
    for (EvidenceUnit unit : initialUnits) {
      EvidenceUnit verified = corpus.read(unit.entryId(), unit.kind(), unit.originalId());
      if (!verified.canonicalJson().equals(unit.canonicalJson())) {
        throw new IllegalArgumentException("ONTOLOGY_READING_UNIT_SOURCE_MISMATCH");
      }
      selected.add(verified);
    }
    return selected;
  }

  private static String lookupKey(OntologyNavigationView.Target key) {
    return switch (key.kind()) {
      case ENTRY -> key.entryId();
      case CLUE -> key.clue().lookupKey();
      case PACKET_UNIT -> key.packet().originalId();
      default -> throw new IllegalArgumentException("ONTOLOGY_READING_QUERY_REFERENCE_INVALID");
    };
  }

  private UnitPage query(String kind, String lookupKey, int offset, int limit) {
    return switch (kind) {
      case "ENTRY_UNITS" -> corpus.entryUnits(lookupKey, offset, limit);
      case "METHOD_USES" -> corpus.methodUses(lookupKey, offset, limit);
      case "STATEMENT_USES" -> corpus.statementUses(lookupKey, offset, limit);
      case "TABLE_STATEMENTS" -> corpus.tableStatements(lookupKey, offset, limit);
      case "COLUMN_STATEMENTS" -> corpus.columnStatements(lookupKey, offset, limit);
      default -> throw new IllegalArgumentException("ONTOLOGY_READING_QUERY_INVALID");
    };
  }

  private OntologyReadingPacket checkedPacket(List<EvidenceUnit> units) {
    OntologyReadingPacket packet = OntologyReadingPacket.of(corpus.sourceIdentity(), units);
    if (packet.modelInput().size() > maxPacketBytes) {
      throw new IllegalArgumentException("ONTOLOGY_READING_PACKET_TOO_LARGE");
    }
    return packet;
  }

  public enum Status {
    READY,
    INCOMPLETE,
    UNRESOLVED
  }

  public record Result(
      Status status,
      OntologyReadingPacket packet,
      List<OntologyDecisionRunner.Decision> decisions,
      List<String> unresolved,
      String issueCode) {
    public Result {
      decisions = List.copyOf(decisions);
      unresolved = List.copyOf(unresolved);
    }
  }
}
