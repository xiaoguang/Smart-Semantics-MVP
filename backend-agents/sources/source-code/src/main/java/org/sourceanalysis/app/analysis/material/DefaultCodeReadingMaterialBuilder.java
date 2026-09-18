package org.sourceanalysis.app.analysis.material;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;

/** Organizes already-read complete units without navigating, parsing, or invoking a provider. */
public final class DefaultCodeReadingMaterialBuilder implements CodeReadingMaterialBuilder {

  @Override
  public CodeReadingMaterialSet build(CodeReadingMaterialRequest request) {
    Objects.requireNonNull(request, "reading material request");
    requireSameUpstreamSnapshot(request);

    List<CodeReadingMaterialSet.Packet> packets = new ArrayList<>();
    List<CodeReadingMaterialSet.EntryCoverage> coverage = new ArrayList<>();
    List<EntryMaterial> current = new ArrayList<>();
    for (JavaCodeIndex.EntryCollection entry : request.javaCodeIndex().entries()) {
      if (entry.context() == null) {
        coverage.add(
            new CodeReadingMaterialSet.EntryCoverage(
                entry.seed().entryId(),
                List.of(),
                CodeReadingMaterialSet.CoverageStatus.NOT_COLLECTED,
                List.of(entry.reason())));
        continue;
      }

      EntryMaterial next =
          entryMaterial(
              entry.seed(), entry.context(), request.persistenceIndex(), request.profile());
      List<EntryMaterial> trial = new ArrayList<>(current);
      trial.add(next);
      CodeReadingMaterialSet.Packet trialPacket = packet(trial);
      if (withinProfile(trialPacket, request.profile())) {
        current = trial;
        continue;
      }

      if (!current.isEmpty()) {
        addPacket(current, packets, coverage);
        current = new ArrayList<>(List.of(next));
        if (withinProfile(packet(current), request.profile())) {
          continue;
        }
      }

      coverage.add(
          new CodeReadingMaterialSet.EntryCoverage(
              next.seed().entryId(),
              List.of(),
              CodeReadingMaterialSet.CoverageStatus.NOT_COLLECTED,
              List.of("MINIMUM_COMPLETE_ENTRY_EXCEEDS_PACKET_LIMIT")));
      current = new ArrayList<>();
    }
    if (!current.isEmpty()) {
      addPacket(current, packets, coverage);
    }

    return new CodeReadingMaterialSet(
        new CodeReadingMaterialSet.Header(
            request.sourceInventory(),
            request.navigationPublication(),
            request.persistencePublication(),
            request.javaCodeIndex().snapshotId(),
            request.profile()),
        packets,
        coverage);
  }

  private static void requireSameUpstreamSnapshot(CodeReadingMaterialRequest request) {
    if (!request
            .javaCodeIndex()
            .snapshotId()
            .equals(request.persistenceIndex().header().sourceSnapshotId())
        || !request
            .navigationPublication()
            .equals(request.persistenceIndex().header().navigationPublication())) {
      throw new IllegalArgumentException("CODE_READING_MATERIALS_INPUT_SNAPSHOT_MISMATCH");
    }
  }

  private static EntryMaterial entryMaterial(
      EntrySeed seed,
      EntryCodeContext context,
      PersistenceMaterialIndex persistenceIndex,
      CodeReadingMaterialProfile profile) {
    EntryCodeContext.MethodCode root = methodByKey(context.methods(), seed.methodKey());
    List<EntryCodeContext.MethodCode> selectedMethods = new ArrayList<>(List.of(root));
    CodeReadingMaterialSet.PersistenceSelection completePersistence =
        persistenceSelection(methodKeys(context.methods()), persistenceIndex);
    selectFittingJavaTargets(seed, context, completePersistence, profile, selectedMethods);
    CodeReadingMaterialSet.PersistenceSelection selectedPersistence =
        selectFittingPersistence(
            seed, context, completePersistence, persistenceIndex, profile, selectedMethods);
    return materialFor(seed, context, selectedMethods, selectedPersistence, completePersistence);
  }

  private static void selectFittingJavaTargets(
      EntrySeed seed,
      EntryCodeContext context,
      CodeReadingMaterialSet.PersistenceSelection completePersistence,
      CodeReadingMaterialProfile profile,
      List<EntryCodeContext.MethodCode> selectedMethods) {
    for (int index = 0; index < selectedMethods.size(); index++) {
      String callerMethodKey = selectedMethods.get(index).methodKey();
      for (EntryCodeContext.CallSite call : context.calls()) {
        if (!callerMethodKey.equals(call.callerMethodKey())) {
          continue;
        }
        for (EntryCodeContext.CallTarget target : call.targets()) {
          if (target.methodKey() == null || containsMethod(selectedMethods, target.methodKey())) {
            continue;
          }
          EntryCodeContext.MethodCode targetMethod =
              methodByKey(context.methods(), target.methodKey());
          List<EntryCodeContext.MethodCode> trialMethods = new ArrayList<>(selectedMethods);
          trialMethods.add(targetMethod);
          EntryMaterial trial =
              materialFor(seed, context, trialMethods, emptyPersistence(), completePersistence);
          if (withinProfile(packet(List.of(trial)), profile)) {
            selectedMethods.add(targetMethod);
          }
        }
      }
    }
  }

  private static CodeReadingMaterialSet.PersistenceSelection selectFittingPersistence(
      EntrySeed seed,
      EntryCodeContext context,
      CodeReadingMaterialSet.PersistenceSelection completePersistence,
      PersistenceMaterialIndex persistenceIndex,
      CodeReadingMaterialProfile profile,
      List<EntryCodeContext.MethodCode> selectedMethods) {
    List<String> selectedBindingMethodKeys = new ArrayList<>();
    CodeReadingMaterialSet.PersistenceSelection selected = emptyPersistence();
    for (EntryCodeContext.MethodCode method : selectedMethods) {
      List<String> trialBindingMethodKeys = new ArrayList<>(selectedBindingMethodKeys);
      trialBindingMethodKeys.add(method.methodKey());
      CodeReadingMaterialSet.PersistenceSelection trialPersistence =
          persistenceSelection(trialBindingMethodKeys, persistenceIndex);
      if (trialPersistence.equals(selected)) {
        continue;
      }
      EntryMaterial trial =
          materialFor(seed, context, selectedMethods, trialPersistence, completePersistence);
      if (withinProfile(packet(List.of(trial)), profile)) {
        selectedBindingMethodKeys = trialBindingMethodKeys;
        selected = trialPersistence;
      }
    }
    return selected;
  }

  private static EntryMaterial materialFor(
      EntrySeed seed,
      EntryCodeContext context,
      List<EntryCodeContext.MethodCode> selectedMethods,
      CodeReadingMaterialSet.PersistenceSelection selectedPersistence,
      CodeReadingMaterialSet.PersistenceSelection completePersistence) {
    List<CodeReadingMaterialSet.UnselectedUnit> unselectedUnits =
        omissions(
            seed, context.methods(), selectedMethods, completePersistence, selectedPersistence);
    List<String> limitations =
        new ArrayList<>(
            context.limitations().stream()
                .map(limitation -> limitation.code() + ": " + limitation.detail())
                .toList());
    if (!unselectedUnits.isEmpty() && !limitations.contains("COMPLETE_UNIT_EXCEEDS_PACKET_LIMIT")) {
      limitations.add("COMPLETE_UNIT_EXCEEDS_PACKET_LIMIT");
    }
    return new EntryMaterial(
        seed,
        selectedMethods,
        callsForSelectedMethods(seed, context, selectedMethods),
        selectedPersistence,
        unselectedUnits,
        List.copyOf(limitations));
  }

  private static CodeReadingMaterialSet.PersistenceSelection emptyPersistence() {
    return new CodeReadingMaterialSet.PersistenceSelection(
        List.of(), List.of(), List.of(), List.of(), List.of());
  }

  private static List<CodeReadingMaterialSet.EntryCall> callsForSelectedMethods(
      EntrySeed seed, EntryCodeContext context, List<EntryCodeContext.MethodCode> selectedMethods) {
    Set<String> selectedMethodKeys = new LinkedHashSet<>(methodKeys(selectedMethods));
    return context.calls().stream()
        .filter(call -> selectedMethodKeys.contains(call.callerMethodKey()))
        .map(call -> new CodeReadingMaterialSet.EntryCall(seed.entryId(), call))
        .toList();
  }

  private static List<CodeReadingMaterialSet.UnselectedUnit> omissions(
      EntrySeed seed,
      List<EntryCodeContext.MethodCode> completeMethods,
      List<EntryCodeContext.MethodCode> selectedMethods,
      CodeReadingMaterialSet.PersistenceSelection completePersistence,
      CodeReadingMaterialSet.PersistenceSelection selectedPersistence) {
    List<CodeReadingMaterialSet.UnselectedUnit> omissions = new ArrayList<>();
    Set<String> selectedMethodKeys = new LinkedHashSet<>(methodKeys(selectedMethods));
    for (EntryCodeContext.MethodCode method : completeMethods) {
      if (!selectedMethodKeys.contains(method.methodKey())) {
        omissions.add(
            new CodeReadingMaterialSet.UnselectedUnit(
                seed.entryId(),
                "JAVA_METHOD",
                method.methodKey(),
                "COMPLETE_UNIT_EXCEEDS_PACKET_LIMIT"));
      }
    }
    Set<String> selectedResourcePaths =
        selectedPersistence.resources().stream()
            .map(PersistenceMaterialIndex.Resource::resourcePath)
            .collect(java.util.stream.Collectors.toSet());
    for (PersistenceMaterialIndex.Resource resource : completePersistence.resources()) {
      if (!selectedResourcePaths.contains(resource.resourcePath())) {
        omissions.add(
            new CodeReadingMaterialSet.UnselectedUnit(
                seed.entryId(),
                "MAPPER_RESOURCE",
                resource.resourcePath(),
                "COMPLETE_UNIT_EXCEEDS_PACKET_LIMIT"));
      }
    }
    return List.copyOf(omissions);
  }

  private static EntryCodeContext.MethodCode methodByKey(
      List<EntryCodeContext.MethodCode> methods, String methodKey) {
    return methods.stream()
        .filter(method -> methodKey.equals(method.methodKey()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("CODE_READING_MATERIALS_CONTEXT_BROKEN"));
  }

  private static boolean containsMethod(
      List<EntryCodeContext.MethodCode> methods, String methodKey) {
    return methods.stream().anyMatch(method -> methodKey.equals(method.methodKey()));
  }

  private static List<String> methodKeys(List<EntryCodeContext.MethodCode> methods) {
    return methods.stream().map(EntryCodeContext.MethodCode::methodKey).toList();
  }

  private static CodeReadingMaterialSet.Packet packet(List<EntryMaterial> entryMaterials) {
    List<EntrySeed> entries = entryMaterials.stream().map(EntryMaterial::seed).toList();
    List<EntryCodeContext.MethodCode> methods = new ArrayList<>();
    List<CodeReadingMaterialSet.EntryCall> calls = new ArrayList<>();
    List<CodeReadingMaterialSet.UnselectedUnit> unselectedUnits = new ArrayList<>();
    List<String> limitations = new ArrayList<>();
    List<PersistenceMaterialIndex.Resource> resources = new ArrayList<>();
    List<PersistenceMaterialIndex.Statement> statements = new ArrayList<>();
    List<PersistenceMaterialIndex.JavaBinding> bindings = new ArrayList<>();
    List<PersistenceMaterialIndex.SqlAnalysis> sqlAnalyses = new ArrayList<>();
    List<PersistenceMaterialIndex.Diagnostic> diagnostics = new ArrayList<>();
    for (EntryMaterial entry : entryMaterials) {
      appendDistinct(methods, entry.methods(), EntryCodeContext.MethodCode::methodKey, "method");
      calls.addAll(entry.calls());
      appendDistinct(
          unselectedUnits,
          entry.unselectedUnits(),
          unit -> unit.entryId() + "\u0000" + unit.unitKind() + "\u0000" + unit.unitRef(),
          "unselected unit");
      appendDistinctStrings(limitations, entry.limitations());
      CodeReadingMaterialSet.PersistenceSelection persistence = entry.persistence();
      appendDistinct(
          resources,
          persistence.resources(),
          PersistenceMaterialIndex.Resource::resourcePath,
          "resource");
      appendDistinct(
          statements,
          persistence.statements(),
          PersistenceMaterialIndex.Statement::statementRef,
          "statement");
      appendDistinct(
          bindings,
          persistence.bindings(),
          PersistenceMaterialIndex.JavaBinding::methodKey,
          "binding");
      appendDistinct(
          sqlAnalyses,
          persistence.sqlAnalyses(),
          PersistenceMaterialIndex.SqlAnalysis::statementRef,
          "SQL analysis");
      appendDistinct(
          diagnostics,
          persistence.diagnostics(),
          DefaultCodeReadingMaterialBuilder::diagnosticIdentity,
          "diagnostic");
    }
    resources.sort(java.util.Comparator.comparing(PersistenceMaterialIndex.Resource::resourcePath));
    statements.sort(
        java.util.Comparator.comparing(PersistenceMaterialIndex.Statement::statementRef));
    bindings.sort(java.util.Comparator.comparing(PersistenceMaterialIndex.JavaBinding::methodKey));
    sqlAnalyses.sort(
        java.util.Comparator.comparing(PersistenceMaterialIndex.SqlAnalysis::statementRef));
    diagnostics.sort(
        java.util.Comparator.comparing(DefaultCodeReadingMaterialBuilder::diagnosticIdentity));

    CodeReadingMaterialSet.PersistenceSelection persistence =
        new CodeReadingMaterialSet.PersistenceSelection(
            resources, statements, bindings, sqlAnalyses, diagnostics);
    String packetId = packetId(entries);
    CodeReadingMaterialSet.Packet draft =
        new CodeReadingMaterialSet.Packet(
            packetId,
            entries,
            methods,
            calls,
            persistence,
            sourceReferences(methods, resources),
            unselectedUnits,
            limitations,
            0L);
    long bytes =
        CodeReadingMaterialMarkdown.renderPacket(draft).getBytes(StandardCharsets.UTF_8).length;
    return new CodeReadingMaterialSet.Packet(
        draft.packetId(),
        draft.entries(),
        draft.methods(),
        draft.calls(),
        draft.persistence(),
        draft.sourceReferences(),
        draft.unselectedUnits(),
        draft.limitations(),
        bytes);
  }

  private static boolean withinProfile(
      CodeReadingMaterialSet.Packet packet, CodeReadingMaterialProfile profile) {
    return packet.entries().size() <= profile.maxEntriesPerPacket()
        && packet.selfContainedUtf8Bytes() <= profile.maxPacketUtf8Bytes();
  }

  private static void addPacket(
      List<EntryMaterial> entryMaterials,
      List<CodeReadingMaterialSet.Packet> packets,
      List<CodeReadingMaterialSet.EntryCoverage> coverage) {
    CodeReadingMaterialSet.Packet packet = packet(entryMaterials);
    packets.add(packet);
    for (EntryMaterial entry : entryMaterials) {
      coverage.add(
          new CodeReadingMaterialSet.EntryCoverage(
              entry.seed().entryId(),
              List.of(packet.packetId()),
              entry.limitations().isEmpty() && entry.unselectedUnits().isEmpty()
                  ? CodeReadingMaterialSet.CoverageStatus.COLLECTED
                  : CodeReadingMaterialSet.CoverageStatus.COLLECTED_WITH_LIMITATIONS,
              entry.limitations()));
    }
  }

  private static CodeReadingMaterialSet.PersistenceSelection persistenceSelection(
      List<String> methodKeys, PersistenceMaterialIndex persistenceIndex) {
    Set<String> selectedMethodKeys = new LinkedHashSet<>(methodKeys);
    List<PersistenceMaterialIndex.JavaBinding> bindings =
        persistenceIndex.bindings().stream()
            .filter(binding -> selectedMethodKeys.contains(binding.methodKey()))
            .sorted(java.util.Comparator.comparing(PersistenceMaterialIndex.JavaBinding::methodKey))
            .toList();
    Set<String> statementRefs = new LinkedHashSet<>();
    bindings.forEach(
        binding ->
            binding
                .statementRefs()
                .forEach(reference -> statementRefs.add(reference.statementRef())));
    List<PersistenceMaterialIndex.Statement> statements =
        persistenceIndex.statements().stream()
            .filter(statement -> statementRefs.contains(statement.statementRef()))
            .sorted(
                java.util.Comparator.comparing(PersistenceMaterialIndex.Statement::statementRef))
            .toList();
    Set<String> resourcePaths = new LinkedHashSet<>();
    statements.forEach(statement -> resourcePaths.add(statement.resourceRef()));
    List<PersistenceMaterialIndex.Resource> resources =
        resourceClosure(resourcePaths, persistenceIndex.resources());
    Set<String> selectedStatementRefs =
        statements.stream()
            .map(PersistenceMaterialIndex.Statement::statementRef)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    List<PersistenceMaterialIndex.SqlAnalysis> sqlAnalyses =
        persistenceIndex.sqlAnalyses().stream()
            .filter(analysis -> selectedStatementRefs.contains(analysis.statementRef()))
            .sorted(
                java.util.Comparator.comparing(PersistenceMaterialIndex.SqlAnalysis::statementRef))
            .toList();
    List<PersistenceMaterialIndex.Diagnostic> diagnostics =
        persistenceIndex.diagnostics().stream()
            .filter(
                diagnostic ->
                    resourcePaths.contains(diagnostic.subjectRef())
                        || selectedStatementRefs.contains(diagnostic.subjectRef())
                        || selectedMethodKeys.contains(diagnostic.subjectRef()))
            .sorted(
                java.util.Comparator.comparing(
                    DefaultCodeReadingMaterialBuilder::diagnosticIdentity))
            .toList();
    return new CodeReadingMaterialSet.PersistenceSelection(
        resources, statements, bindings, sqlAnalyses, diagnostics);
  }

  private static List<PersistenceMaterialIndex.Resource> resourceClosure(
      Set<String> initialPaths, List<PersistenceMaterialIndex.Resource> availableResources) {
    List<PersistenceMaterialIndex.Resource> selected = new ArrayList<>();
    List<String> pending = new ArrayList<>(initialPaths);
    for (int index = 0; index < pending.size(); index++) {
      String path = pending.get(index);
      PersistenceMaterialIndex.Resource resource =
          availableResources.stream()
              .filter(candidate -> path.equals(candidate.resourcePath()))
              .findFirst()
              .orElse(null);
      if (resource == null) {
        throw new IllegalArgumentException(
            "CODE_READING_MATERIALS_RESOURCE_CLOSURE_MISSING: " + path);
      }
      if (selected.stream().anyMatch(value -> value.resourcePath().equals(path))) {
        continue;
      }
      selected.add(resource);
      resource.dependencyResourcePaths().stream()
          .filter(dependency -> !pending.contains(dependency))
          .sorted()
          .forEach(pending::add);
    }
    selected.sort(java.util.Comparator.comparing(PersistenceMaterialIndex.Resource::resourcePath));
    return List.copyOf(selected);
  }

  private static List<CodeReadingMaterialSet.SourceReference> sourceReferences(
      List<EntryCodeContext.MethodCode> methods,
      List<PersistenceMaterialIndex.Resource> resources) {
    List<CodeReadingMaterialSet.SourceReference> references = new ArrayList<>();
    int ordinal = 1;
    for (EntryCodeContext.MethodCode method : methods) {
      references.add(
          new CodeReadingMaterialSet.SourceReference(
              "source:" + ordinal++,
              new CodeReadingMaterialSet.UnitLocation(
                  "JAVA_METHOD",
                  method.methodKey(),
                  method.source().path(),
                  method.source().startLine(),
                  method.source().endLine())));
    }
    for (PersistenceMaterialIndex.Resource resource : resources) {
      references.add(
          new CodeReadingMaterialSet.SourceReference(
              "source:" + ordinal++,
              new CodeReadingMaterialSet.UnitLocation(
                  "MAPPER_RESOURCE",
                  resource.resourcePath(),
                  resource.resourcePath(),
                  1,
                  lineCount(resource.rawSource()))));
    }
    return List.copyOf(references);
  }

  private static int lineCount(String source) {
    return (int) source.chars().filter(character -> character == '\n').count() + 1;
  }

  private static String packetId(List<EntrySeed> entries) {
    return "packet:"
        + sha256(
            entries.stream()
                .map(EntrySeed::entryId)
                .collect(java.util.stream.Collectors.joining("\u0000")));
  }

  private static String diagnosticIdentity(PersistenceMaterialIndex.Diagnostic diagnostic) {
    return diagnostic.code() + "\u0000" + diagnostic.subjectRef() + "\u0000" + diagnostic.detail();
  }

  private static <T> void appendDistinct(
      List<T> destination, List<T> additions, Function<T, String> identity, String label) {
    for (T addition : additions) {
      String key = identity.apply(addition);
      T existing =
          destination.stream()
              .filter(candidate -> key.equals(identity.apply(candidate)))
              .findFirst()
              .orElse(null);
      if (existing == null) {
        destination.add(addition);
      } else if (!existing.equals(addition)) {
        throw new IllegalArgumentException(
            "CODE_READING_MATERIALS_CONFLICTING_" + label.toUpperCase());
      }
    }
  }

  private static void appendDistinctStrings(List<String> destination, List<String> additions) {
    for (String addition : additions) {
      if (!destination.contains(addition)) {
        destination.add(addition);
      }
    }
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private record EntryMaterial(
      EntrySeed seed,
      List<EntryCodeContext.MethodCode> methods,
      List<CodeReadingMaterialSet.EntryCall> calls,
      CodeReadingMaterialSet.PersistenceSelection persistence,
      List<CodeReadingMaterialSet.UnselectedUnit> unselectedUnits,
      List<String> limitations) {}
}
