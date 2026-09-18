package org.sourceanalysis.app.analysis.material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;

/** Shared output-only formatter for complete reading materials and individual packets. */
public final class CodeReadingMaterialMarkdown {

  private CodeReadingMaterialMarkdown() {}

  public static String render(CodeReadingMaterialSet materials) {
    Objects.requireNonNull(materials, "code reading materials");
    StringBuilder output = new StringBuilder("# Code Reading Materials\n\n");
    output.append("Snapshot: ").append(materials.header().sourceSnapshotId()).append("\n\n");
    for (CodeReadingMaterialSet.Packet packet : materials.packets()) {
      output.append(renderPacket(packet)).append("\n");
    }
    appendCoverage(output, materials.coverage());
    return output.toString();
  }

  private static void appendCoverage(
      StringBuilder output, List<CodeReadingMaterialSet.EntryCoverage> coverage) {
    if (coverage.isEmpty()) {
      return;
    }
    output.append("## Entry coverage\n");
    for (CodeReadingMaterialSet.EntryCoverage entry : coverage) {
      output
          .append("- ")
          .append(entry.entryId())
          .append(": ")
          .append(entry.status())
          .append(" packets=")
          .append(entry.packetIds())
          .append(" limitations=")
          .append(entry.limitations())
          .append("\n");
    }
  }

  public static String renderPacket(CodeReadingMaterialSet.Packet packet) {
    Objects.requireNonNull(packet, "code reading material packet");
    StringBuilder output = new StringBuilder();
    output.append("## Packet ").append(packet.packetId()).append("\n\n");
    output.append("### Entries\n");
    for (var entry : packet.entries()) {
      output.append("- ").append(entry.entryId()).append(": ").append(entry.trigger()).append("\n");
    }

    Map<String, MethodLabel> methodLabels = methodLabels(packet.methods());
    appendCallTree(output, packet, methodLabels);

    output.append("\n### Java methods\n");
    for (var method : packet.methods()) {
      MethodLabel label = methodLabels.get(method.methodKey());
      output
          .append("#### ")
          .append(label.label())
          .append(" ")
          .append(method.methodKey())
          .append("\n\n");
      output
          .append("- Declaration: ")
          .append(methodDisplayName(method))
          .append("\n- Source: ")
          .append(method.source().path())
          .append(":")
          .append(method.source().startLine())
          .append("-")
          .append(method.source().endLine())
          .append("\n");
      if (!method.parameters().isEmpty()) {
        output.append("- Parameters:\n");
        for (var parameter : method.parameters()) {
          output
              .append("  - #")
              .append(parameter.ordinal())
              .append(" ")
              .append(parameter.typeText())
              .append(" ")
              .append(parameter.name());
          if (!parameter.annotationTexts().isEmpty()) {
            output.append(" ").append(parameter.annotationTexts());
          }
          output.append("\n");
        }
      }
      output.append("\n");
      fenced(output, "java", method.source().text());
    }

    output.append("### Entry-owned calls\n");
    for (var entryCall : packet.calls()) {
      output
          .append("- [")
          .append(entryCall.entryId())
          .append("] ")
          .append(entryCall.call().callKey())
          .append(" at lines ")
          .append(entryCall.call().site().startLine())
          .append("-")
          .append(entryCall.call().site().endLine())
          .append(": ")
          .append(entryCall.call().callerMethodKey())
          .append(" -> ")
          .append(entryCall.call().expression())
          .append(" [")
          .append(entryCall.call().resolution())
          .append("]\n");
      if (entryCall.call().resolutionDetail() != null) {
        output.append("  - Stop: ").append(entryCall.call().resolutionDetail()).append("\n");
      }
      if (!entryCall.call().actualArguments().isEmpty()) {
        output
            .append("  - Actual arguments: ")
            .append(entryCall.call().actualArguments())
            .append("\n");
      }
      for (EntryCodeContext.CallTarget target : entryCall.call().targets()) {
        output
            .append("  - Candidate: ")
            .append(target.displayName())
            .append(" [")
            .append(target.roles())
            .append(", ")
            .append(target.expansion())
            .append("]\n");
        if (target.reason() != null) {
          output.append("    - Stop: ").append(target.reason()).append("\n");
        }
        for (EntryCodeContext.ArgumentAssociation association : target.argumentAssociations()) {
          output
              .append("    - Actual ")
              .append(association.actualOrdinals())
              .append(" -> formal ")
              .append(association.formalOrdinal())
              .append(" [")
              .append(association.kind())
              .append("]\n");
        }
      }
    }

    appendPersistence(output, packet.persistence());
    appendSourceReferences(output, packet.sourceReferences());
    appendUnselectedUnits(output, packet.unselectedUnits());
    appendLimitations(output, packet.limitations());
    return output.toString();
  }

  private static void appendCallTree(
      StringBuilder output,
      CodeReadingMaterialSet.Packet packet,
      Map<String, MethodLabel> methodLabels) {
    output.append("\n### Call tree\n");
    Map<String, Map<String, List<IndexedCall>>> callsByEntryAndCaller =
        callsByEntryAndCaller(packet.calls());
    for (var entry : packet.entries()) {
      output.append("#### ").append(entry.entryId()).append("\n");
      appendMethodTree(
          output,
          entry.methodKey(),
          methodLabels,
          callsByEntryAndCaller.getOrDefault(entry.entryId(), Map.of()),
          "",
          new LinkedHashSet<>(),
          new LinkedHashSet<>());
    }
  }

  private static Map<String, MethodLabel> methodLabels(List<EntryCodeContext.MethodCode> methods) {
    Map<String, MethodLabel> labels = new LinkedHashMap<>();
    for (int index = 0; index < methods.size(); index++) {
      EntryCodeContext.MethodCode method = methods.get(index);
      labels.put(method.methodKey(), new MethodLabel("M" + (index + 1), methodDisplayName(method)));
    }
    return labels;
  }

  private static Map<String, Map<String, List<IndexedCall>>> callsByEntryAndCaller(
      List<CodeReadingMaterialSet.EntryCall> calls) {
    Map<String, Map<String, List<IndexedCall>>> result = new LinkedHashMap<>();
    for (int index = 0; index < calls.size(); index++) {
      CodeReadingMaterialSet.EntryCall entryCall = calls.get(index);
      result
          .computeIfAbsent(entryCall.entryId(), ignored -> new LinkedHashMap<>())
          .computeIfAbsent(entryCall.call().callerMethodKey(), ignored -> new ArrayList<>())
          .add(new IndexedCall("C" + (index + 1), entryCall));
    }
    return result;
  }

  private static void appendMethodTree(
      StringBuilder output,
      String methodKey,
      Map<String, MethodLabel> methodLabels,
      Map<String, List<IndexedCall>> callsByCaller,
      String indentation,
      Set<String> activeMethods,
      Set<String> expandedMethods) {
    output
        .append(indentation)
        .append("- ")
        .append(methodLabel(methodLabels, methodKey).label())
        .append(" ")
        .append(methodLabel(methodLabels, methodKey).display())
        .append("\n");
    activeMethods.add(methodKey);
    expandedMethods.add(methodKey);
    for (IndexedCall indexedCall : callsByCaller.getOrDefault(methodKey, List.of())) {
      EntryCodeContext.CallSite call = indexedCall.entryCall().call();
      output
          .append(indentation)
          .append("  - ")
          .append(indexedCall.label())
          .append(": ")
          .append(call.expression())
          .append(" [")
          .append(call.resolution())
          .append("]\n");
      for (EntryCodeContext.CallTarget target : call.targets()) {
        appendCallTarget(
            output,
            target,
            methodLabels,
            callsByCaller,
            indentation + "    ",
            activeMethods,
            expandedMethods);
      }
    }
    activeMethods.remove(methodKey);
  }

  private static void appendCallTarget(
      StringBuilder output,
      EntryCodeContext.CallTarget target,
      Map<String, MethodLabel> methodLabels,
      Map<String, List<IndexedCall>> callsByCaller,
      String indentation,
      Set<String> activeMethods,
      Set<String> expandedMethods) {
    String targetKey = target.methodKey();
    output.append(indentation).append("- Candidate: ");
    if (targetKey == null) {
      output.append(target.displayName());
    } else {
      MethodLabel label = methodLabel(methodLabels, targetKey);
      output.append(label.label()).append(" ").append(label.display());
    }
    output.append(" [").append(target.expansion()).append("]");
    if (target.reason() != null) {
      output.append(" ").append(target.reason());
    }
    if (!"BODY_INCLUDED".equals(target.expansion())
        || targetKey == null
        || !methodLabels.containsKey(targetKey)) {
      output.append("\n");
      return;
    }
    if (activeMethods.contains(targetKey)) {
      output.append(" (cycle reference)\n");
      return;
    }
    if (expandedMethods.contains(targetKey)) {
      output.append(" (shared reference)\n");
      return;
    }
    output.append("\n");
    appendMethodTree(
        output,
        targetKey,
        methodLabels,
        callsByCaller,
        indentation + "  ",
        activeMethods,
        expandedMethods);
  }

  private static MethodLabel methodLabel(Map<String, MethodLabel> methodLabels, String methodKey) {
    return methodLabels.getOrDefault(methodKey, new MethodLabel("M?", methodKey));
  }

  private static String methodDisplayName(EntryCodeContext.MethodCode method) {
    if (method.name() == null) {
      return method.declaringType() + "#" + method.kind().toLowerCase(java.util.Locale.ROOT);
    }
    String parameterTypes =
        String.join(
            ", ", method.parameters().stream().map(parameter -> parameter.typeText()).toList());
    return method.declaringType() + "#" + method.name() + "(" + parameterTypes + ")";
  }

  private record IndexedCall(String label, CodeReadingMaterialSet.EntryCall entryCall) {}

  private record MethodLabel(String label, String display) {}

  private static void appendPersistence(
      StringBuilder output, CodeReadingMaterialSet.PersistenceSelection persistence) {
    if (!persistence.resources().isEmpty()) {
      output.append("\n### Mapper XML\n");
      for (var resource : persistence.resources()) {
        output.append("#### ").append(resource.resourcePath()).append("\n\n");
        output
            .append("- Static dependencies: ")
            .append(resource.dependencyResourcePaths())
            .append("\n\n");
        fenced(output, "xml", resource.rawSource());
      }
    }
    if (!persistence.statements().isEmpty()) {
      output.append("### Mapper statements\n");
      for (var statement : persistence.statements()) {
        output
            .append("- ")
            .append(statement.statementRef())
            .append(": ")
            .append(statement.statementKind())
            .append(" ")
            .append(statement.statementId())
            .append(" dependencies=")
            .append(statement.dependencyRefs())
            .append("\n");
      }
    }
    if (!persistence.bindings().isEmpty()) {
      output.append("### Mapper bindings\n");
      for (var binding : persistence.bindings()) {
        output
            .append("- ")
            .append(binding.methodKey())
            .append(": ")
            .append(binding.candidateNature())
            .append(" statements=")
            .append(binding.statementRefs())
            .append(" parameters=")
            .append(binding.parameters())
            .append("\n");
      }
    }
    if (!persistence.sqlAnalyses().isEmpty()) {
      output.append("### SQL analysis copies\n");
      for (var analysis : persistence.sqlAnalyses()) {
        output.append("#### ").append(analysis.statementRef()).append("\n\n");
        output
            .append("- Status: ")
            .append(analysis.status())
            .append("\n- Transformations: ")
            .append(analysis.transformations())
            .append("\n");
        if (analysis.reason() != null) {
          output.append("- Reason: ").append(analysis.reason()).append("\n");
        }
        if (analysis.ast() != null) {
          output.append("- AST:\n");
          appendAst(output, analysis.ast(), 2);
        }
        if (analysis.analysisCopy() != null) {
          output.append("\n");
          fenced(output, "sql", analysis.analysisCopy());
        }
      }
    }
    if (!persistence.diagnostics().isEmpty()) {
      output.append("### Persistence diagnostics\n");
      for (PersistenceMaterialIndex.Diagnostic diagnostic : persistence.diagnostics()) {
        output
            .append("- ")
            .append(diagnostic.code())
            .append(" ")
            .append(diagnostic.subjectRef())
            .append(": ")
            .append(diagnostic.detail())
            .append("\n");
      }
    }
  }

  private static void appendSourceReferences(
      StringBuilder output, List<CodeReadingMaterialSet.SourceReference> sourceReferences) {
    if (sourceReferences.isEmpty()) {
      return;
    }
    output.append("### Source references\n");
    for (var reference : sourceReferences) {
      CodeReadingMaterialSet.UnitLocation location = reference.location();
      output
          .append("- ")
          .append(reference.sourceRef())
          .append(" -> ")
          .append(location.unitKind())
          .append(" ")
          .append(location.unitRef())
          .append(" at ")
          .append(location.path())
          .append(":")
          .append(location.startLine())
          .append("-")
          .append(location.endLine())
          .append("\n");
    }
  }

  private static void appendUnselectedUnits(
      StringBuilder output, List<CodeReadingMaterialSet.UnselectedUnit> unselectedUnits) {
    if (unselectedUnits.isEmpty()) {
      return;
    }
    output.append("### Unselected complete units\n");
    for (var unit : unselectedUnits) {
      output
          .append("- [")
          .append(unit.entryId())
          .append("] ")
          .append(unit.unitKind())
          .append(" ")
          .append(unit.unitRef())
          .append(": ")
          .append(unit.reason())
          .append("\n");
    }
  }

  private static void appendLimitations(StringBuilder output, List<String> limitations) {
    if (limitations.isEmpty()) {
      return;
    }
    output.append("### Limitations\n");
    for (String limitation : limitations) {
      output.append("- ").append(limitation).append("\n");
    }
  }

  private static void fenced(StringBuilder output, String language, String text) {
    output.append("```").append(language).append("\n");
    output.append(text);
    if (!text.endsWith("\n")) {
      output.append("\n");
    }
    output.append("```\n\n");
  }

  private static void appendAst(
      StringBuilder output, PersistenceMaterialIndex.SqlAstNode node, int indentation) {
    output.append(" ".repeat(indentation)).append("- ").append(node.kind());
    if (node.value() != null) {
      output.append(": ").append(node.value());
    }
    if (!node.attributes().isEmpty()) {
      output.append(" ").append(node.attributes());
    }
    output.append("\n");
    for (PersistenceMaterialIndex.SqlAstNode child : node.children()) {
      appendAst(output, child, indentation + 2);
    }
  }
}
