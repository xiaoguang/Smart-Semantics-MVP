package org.sourceanalysis.app.analysis.interpretation.activity;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.discovery.MapperXmlResourceView;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Deterministically projects verified Step05 packets into provider-visible Activity material. */
public final class ActivityMaterialProjector {

  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  public ActivityMaterialView project(
      CodeReadingMaterialSet.Packet packet, ActivityExplanationProfile profile) {
    Objects.requireNonNull(packet, "code reading packet");
    Objects.requireNonNull(profile, "activity explanation profile");

    Map<String, String> entryKeys = refs(packet.entries(), EntryCodeContextKey::entryId, "E");
    Map<String, String> methodRefs =
        refs(packet.methods(), EntryCodeContext.MethodCode::methodKey, "M");
    Map<String, String> callRefs = new LinkedHashMap<>();
    List<CodeReadingMaterialSet.EntryCall> orderedCalls = orderedCalls(packet);
    for (int index = 0; index < orderedCalls.size(); index++) {
      CodeReadingMaterialSet.EntryCall call = orderedCalls.get(index);
      callRefs.put(callIdentity(call), "C" + (index + 1));
    }
    Map<String, String> statementRefs =
        refs(
            packet.persistence().statements(),
            PersistenceMaterialIndex.Statement::statementRef,
            "X");
    Map<String, String> sourceRefs =
        refs(packet.sourceReferences(), CodeReadingMaterialSet.SourceReference::sourceRef, "S");

    Map<String, Document> parsedResources = parseResources(packet.persistence().resources());
    Map<String, List<ActivityMaterialView.XmlDependency>> dependencies = new LinkedHashMap<>();
    packet.persistence().statements().stream()
        .sorted(Comparator.comparing(PersistenceMaterialIndex.Statement::statementRef))
        .forEach(
            statement ->
                dependencies.put(
                    statement.statementRef(), dependencies(statement, parsedResources)));

    return new ActivityMaterialView(
        packet, profile, entryKeys, methodRefs, callRefs, statementRefs, sourceRefs, dependencies);
  }

  /** Materializes the complete selected packet; later reading coordination may select subsets. */
  public ActivityReadingPacket materialize(ActivityMaterialView view) {
    Objects.requireNonNull(view, "activity material view");
    CodeReadingMaterialSet.Packet packet = view.packet();
    ObjectNode readingPacket = JsonNodeFactory.instance.objectNode();
    readingPacket.put("schemaVersion", "activity-reading-packet-v1");
    readingPacket.set("entryKeys", strings(view.entryKeysById().values().stream().toList()));
    readingPacket.set("entries", entries(view));
    readingPacket.set("methods", methods(view));
    readingPacket.set("calls", calls(view));
    readingPacket.set("statements", statements(view));
    readingPacket.set("persistenceBindings", bindings(view));
    readingPacket.set("sqlAnalyses", sqlAnalyses(view));
    readingPacket.set("persistenceDiagnostics", diagnostics(view));
    readingPacket.set("allowlistedRefs", sourceAllowlist(view));
    readingPacket.set("unselectedUnits", unselectedUnits(view));
    readingPacket.set("limitations", strings(packet.limitations()));

    Map<String, String> entryIdsByKey = invert(view.entryKeysById());
    Map<String, String> sourceIdsByRef = invert(view.sourceRefsById());
    return new ActivityReadingPacket(
        packet.packetId(),
        entryIdsByKey,
        sourceIdsByRef,
        canonicalJson.encodeCanonical(readingPacket),
        !packet.limitations().isEmpty() || !packet.unselectedUnits().isEmpty());
  }

  private ArrayNode entries(ActivityMaterialView view) {
    ArrayNode entries = JsonNodeFactory.instance.arrayNode();
    view.packet().entries().stream()
        .sorted(Comparator.comparing(EntryCodeContextKey::entryId))
        .forEach(
            entry -> {
              ObjectNode value = entries.addObject();
              value.put("key", view.entryKeysById().get(entry.entryId()));
              value.put("methodRef", view.methodRefsByKey().get(entry.methodKey()));
              value.put("trigger", entry.trigger());
            });
    return entries;
  }

  private ArrayNode methods(ActivityMaterialView view) {
    ArrayNode methods = JsonNodeFactory.instance.arrayNode();
    view.packet().methods().stream()
        .sorted(Comparator.comparing(EntryCodeContext.MethodCode::methodKey))
        .forEach(
            method -> {
              ObjectNode value = methods.addObject();
              value.put("ref", view.methodRefsByKey().get(method.methodKey()));
              value.put("kind", method.kind());
              nullableText(value, "declaringType", method.declaringType());
              nullableText(value, "name", method.name());
              value.put("signature", method.signature());
              nullableText(
                  value,
                  "enclosingMethodRef",
                  view.methodRefsByKey().get(method.enclosingMethodKey()));
              value.set("parameters", parameters(method.parameters()));
              nullableText(value, "returnType", method.returnTypeText());
              value.set("modifiers", strings(method.modifiers()));
              value.set("annotations", strings(method.annotations()));
              value.put("bodyPresent", method.bodyPresent());
              value.put("code", method.source().text());
              value.set("sourceRefs", strings(sourceRefsForMethod(view, method.methodKey())));
              value.set("controls", controls(method));
              value.set("exits", exits(method));
            });
    return methods;
  }

  private ArrayNode parameters(List<JavaDeclarationCatalog.ParameterView> parameters) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    for (JavaDeclarationCatalog.ParameterView parameter : parameters) {
      ObjectNode value = values.addObject();
      value.put("ordinal", parameter.ordinal());
      value.put("name", parameter.name());
      value.put("type", parameter.typeText());
      value.put("varArgs", parameter.varArgs());
      value.set("annotations", strings(parameter.annotationTexts()));
    }
    return values;
  }

  private ArrayNode controls(EntryCodeContext.MethodCode method) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    for (int index = 0; index < method.controls().size(); index++) {
      EntryCodeContext.Control control = method.controls().get(index);
      ObjectNode value = values.addObject();
      value.put("index", index);
      value.put("kind", control.kind());
      nullableText(value, "expression", control.expression());
      if (control.parentControlIndex() == null) {
        value.putNull("parentControlIndex");
      } else {
        value.put("parentControlIndex", control.parentControlIndex());
      }
      value.set("position", relativePosition(method, control.sourceRange()));
    }
    return values;
  }

  private ArrayNode exits(EntryCodeContext.MethodCode method) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    for (EntryCodeContext.Exit exit : method.exits()) {
      ObjectNode value = values.addObject();
      value.put("kind", exit.kind());
      nullableText(value, "expression", exit.expression());
      value.set("position", relativePosition(method, exit.sourceRange()));
    }
    return values;
  }

  private ArrayNode calls(ActivityMaterialView view) {
    Map<String, EntryCodeContext.MethodCode> methodsByKey = new LinkedHashMap<>();
    view.packet().methods().forEach(method -> methodsByKey.put(method.methodKey(), method));
    ArrayNode calls = JsonNodeFactory.instance.arrayNode();
    for (CodeReadingMaterialSet.EntryCall entryCall : orderedCalls(view.packet())) {
      EntryCodeContext.CallSite call = entryCall.call();
      EntryCodeContext.MethodCode caller = methodsByKey.get(call.callerMethodKey());
      ObjectNode value = calls.addObject();
      value.put("ref", view.callRefsByIdentity().get(callIdentity(entryCall)));
      value.put("entryKey", view.entryKeysById().get(entryCall.entryId()));
      value.put("callerMethodRef", view.methodRefsByKey().get(call.callerMethodKey()));
      value.put("kind", call.kind());
      value.set("position", relativePosition(caller, call.site()));
      if (call.navigationSite() == null) {
        value.putNull("navigationPosition");
      } else {
        value.set("navigationPosition", relativePosition(caller, call.navigationSite()));
      }
      value.put("expression", call.expression());
      nullableText(value, "receiverExpression", call.receiverExpression());
      value.set("actualArguments", actualArguments(call.actualArguments()));
      value.set("enclosingControlIndexes", integers(call.enclosingControlIndexes()));
      value.put("deferred", call.deferred());
      value.set("targets", targets(view, call, methodsByKey));
      value.put("resolution", call.resolution());
      nullableText(value, "resolutionDetail", call.resolutionDetail());
    }
    return calls;
  }

  private ArrayNode actualArguments(List<EntryCodeContext.ActualArgument> arguments) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    for (EntryCodeContext.ActualArgument argument : arguments) {
      values
          .addObject()
          .put("ordinal", argument.ordinal())
          .put("expression", argument.expression());
    }
    return values;
  }

  private ArrayNode targets(
      ActivityMaterialView view,
      EntryCodeContext.CallSite call,
      Map<String, EntryCodeContext.MethodCode> methodsByKey) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    for (EntryCodeContext.CallTarget target : call.targets()) {
      ObjectNode value = values.addObject();
      nullableText(value, "methodRef", view.methodRefsByKey().get(target.methodKey()));
      value.set("roles", strings(target.roles()));
      value.put("displayName", target.displayName());
      value.set("navigationKinds", strings(target.navigationKinds()));
      value.put("expansion", target.expansion());
      nullableText(value, "reason", target.reason());
      ArrayNode associations = value.putArray("argumentAssociations");
      EntryCodeContext.MethodCode targetMethod = methodsByKey.get(target.methodKey());
      for (EntryCodeContext.ArgumentAssociation association : target.argumentAssociations()) {
        ObjectNode associationValue = associations.addObject();
        associationValue.set("actualOrdinals", integers(association.actualOrdinals()));
        associationValue.set(
            "actualExpressions",
            strings(
                association.actualOrdinals().stream()
                    .map(ordinal -> call.actualArguments().get(ordinal).expression())
                    .toList()));
        if (association.formalOrdinal() == null) {
          associationValue.putNull("formalOrdinal");
          associationValue.putNull("formalParameter");
        } else {
          associationValue.put("formalOrdinal", association.formalOrdinal());
          if (targetMethod == null
              || association.formalOrdinal() >= targetMethod.parameters().size()) {
            associationValue.putNull("formalParameter");
          } else {
            JavaDeclarationCatalog.ParameterView formal =
                targetMethod.parameters().get(association.formalOrdinal());
            ObjectNode formalValue = associationValue.putObject("formalParameter");
            formalValue.put("ordinal", formal.ordinal());
            formalValue.put("name", formal.name());
            formalValue.put("type", formal.typeText());
            formalValue.put("varArgs", formal.varArgs());
            formalValue.set("annotations", strings(formal.annotationTexts()));
          }
        }
        associationValue.put("kind", association.kind());
      }
    }
    return values;
  }

  private ArrayNode statements(ActivityMaterialView view) {
    ArrayNode statements = JsonNodeFactory.instance.arrayNode();
    view.packet().persistence().statements().stream()
        .sorted(Comparator.comparing(PersistenceMaterialIndex.Statement::statementRef))
        .forEach(
            statement -> {
              ObjectNode value = statements.addObject();
              value.put("ref", view.statementRefsById().get(statement.statementRef()));
              value.set(
                  "sourceRefs", strings(sourceRefsForResource(view, statement.resourceRef())));
              value.put("namespace", statement.namespace());
              value.put("statementId", statement.statementId());
              value.put("statementKind", statement.statementKind());
              nullableText(value, "databaseId", statement.databaseId());
              value.set("xml", xml(statement.xmlSubtree()));
              ArrayNode dependencies = value.putArray("dependencies");
              for (PersistenceMaterialIndex.DependencyRef dependency : statement.dependencyRefs()) {
                ObjectNode dependencyValue = dependencies.addObject();
                dependencyValue.put("kind", dependency.kind());
                dependencyValue.put("reference", dependency.reference());
                dependencyValue.put("resolution", dependency.resolution());
                ArrayNode nodes = dependencyValue.putArray("resolvedNodes");
                view
                    .dependenciesByStatementId()
                    .getOrDefault(statement.statementRef(), List.of())
                    .stream()
                    .filter(candidate -> sameDependency(candidate, dependency))
                    .forEach(candidate -> nodes.add(xmlDependency(candidate)));
              }
            });
    return statements;
  }

  private ArrayNode bindings(ActivityMaterialView view) {
    ArrayNode bindings = JsonNodeFactory.instance.arrayNode();
    view.packet().persistence().bindings().stream()
        .sorted(Comparator.comparing(PersistenceMaterialIndex.JavaBinding::methodKey))
        .forEach(
            binding -> {
              ObjectNode value = bindings.addObject();
              nullableText(value, "methodRef", view.methodRefsByKey().get(binding.methodKey()));
              value.put("javaInterface", binding.javaInterfaceFqn());
              value.put("methodSignature", binding.methodSignature());
              value.put("candidateNature", binding.candidateNature());
              ArrayNode parameters = value.putArray("parameters");
              for (PersistenceMaterialIndex.ParameterBinding parameter : binding.parameters()) {
                ObjectNode parameterValue = parameters.addObject();
                parameterValue.put("ordinal", parameter.ordinal());
                parameterValue.put("name", parameter.name());
                parameterValue.put("type", parameter.typeText());
                parameterValue.set("annotations", strings(parameter.annotationTexts()));
                parameterValue.set("placeholderPaths", strings(parameter.placeholderPaths()));
                parameterValue.set("limitations", strings(parameter.limitations()));
              }
              ArrayNode statementRefs = value.putArray("statements");
              for (PersistenceMaterialIndex.StatementRef statement : binding.statementRefs()) {
                ObjectNode statementValue = statementRefs.addObject();
                nullableText(
                    statementValue,
                    "statementRef",
                    view.statementRefsById().get(statement.statementRef()));
                nullableText(statementValue, "databaseId", statement.databaseId());
              }
              value.set("limitations", strings(binding.limitations()));
            });
    return bindings;
  }

  private ArrayNode sqlAnalyses(ActivityMaterialView view) {
    ArrayNode analyses = JsonNodeFactory.instance.arrayNode();
    view.packet().persistence().sqlAnalyses().stream()
        .sorted(Comparator.comparing(PersistenceMaterialIndex.SqlAnalysis::statementRef))
        .forEach(
            analysis -> {
              ObjectNode value = analyses.addObject();
              nullableText(
                  value, "statementRef", view.statementRefsById().get(analysis.statementRef()));
              value.put("status", analysis.status().name());
              value.set("transformations", strings(analysis.transformations()));
              nullableText(value, "reason", analysis.reason());
              if (analysis.ast() == null) {
                value.putNull("ast");
              } else {
                value.set("ast", sqlAst(analysis.ast()));
              }
            });
    return analyses;
  }

  private ObjectNode sqlAst(PersistenceMaterialIndex.SqlAstNode node) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("kind", node.kind());
    nullableText(value, "value", node.value());
    value.set("attributes", stringMap(node.attributes()));
    ArrayNode children = value.putArray("children");
    node.children().forEach(child -> children.add(sqlAst(child)));
    return value;
  }

  private ArrayNode diagnostics(ActivityMaterialView view) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    for (PersistenceMaterialIndex.Diagnostic diagnostic :
        view.packet().persistence().diagnostics()) {
      ObjectNode value = values.addObject();
      value.put("code", diagnostic.code());
      value.put("detail", diagnostic.detail());
      nullableText(value, "subjectRef", localUnitRef(view, diagnostic.subjectRef()));
    }
    return values;
  }

  private ArrayNode sourceAllowlist(ActivityMaterialView view) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    view.packet().sourceReferences().stream()
        .sorted(Comparator.comparing(CodeReadingMaterialSet.SourceReference::sourceRef))
        .forEach(
            source -> {
              ObjectNode value = values.addObject();
              value.put("ref", view.sourceRefsById().get(source.sourceRef()));
              value.put("unitKind", source.location().unitKind());
              nullableText(value, "unitRef", localUnitRef(view, source.location().unitRef()));
            });
    return values;
  }

  private ArrayNode unselectedUnits(ActivityMaterialView view) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    List<CodeReadingMaterialSet.UnselectedUnit> units =
        view.packet().unselectedUnits().stream()
            .sorted(
                Comparator.comparing(CodeReadingMaterialSet.UnselectedUnit::entryId)
                    .thenComparing(CodeReadingMaterialSet.UnselectedUnit::unitKind)
                    .thenComparing(CodeReadingMaterialSet.UnselectedUnit::unitRef))
            .toList();
    for (int index = 0; index < units.size(); index++) {
      CodeReadingMaterialSet.UnselectedUnit unit = units.get(index);
      ObjectNode value = values.addObject();
      value.put("ref", "U" + (index + 1));
      value.put("entryKey", view.entryKeysById().get(unit.entryId()));
      value.put("unitKind", unit.unitKind());
      value.put("reason", unit.reason());
    }
    return values;
  }

  private static List<CodeReadingMaterialSet.EntryCall> orderedCalls(
      CodeReadingMaterialSet.Packet packet) {
    return packet.calls().stream()
        .sorted(
            Comparator.comparing(CodeReadingMaterialSet.EntryCall::entryId)
                .thenComparing(value -> value.call().site().startOffsetUtf16())
                .thenComparing(value -> value.call().callKey()))
        .toList();
  }

  private static String callIdentity(CodeReadingMaterialSet.EntryCall call) {
    return call.entryId() + "\u0000" + call.call().callKey();
  }

  private static Map<String, Document> parseResources(
      List<PersistenceMaterialIndex.Resource> resources) {
    Map<String, Document> result = new LinkedHashMap<>();
    resources.stream()
        .sorted(Comparator.comparing(PersistenceMaterialIndex.Resource::resourcePath))
        .forEach(
            resource ->
                result.put(
                    resource.resourcePath(),
                    MapperXmlResourceView.parseSavedRawSource(resource.rawSource())));
    return Collections.unmodifiableMap(new LinkedHashMap<>(result));
  }

  private static List<ActivityMaterialView.XmlDependency> dependencies(
      PersistenceMaterialIndex.Statement statement, Map<String, Document> parsedResources) {
    List<ActivityMaterialView.XmlDependency> result = new ArrayList<>();
    Set<String> visited = new LinkedHashSet<>();
    for (PersistenceMaterialIndex.DependencyRef dependency : statement.dependencyRefs()) {
      for (String reference : references(dependency)) {
        collectDependency(
            dependency.kind(),
            reference,
            dependency.resolution(),
            statement.namespace(),
            parsedResources,
            visited,
            result);
      }
    }
    return List.copyOf(result);
  }

  private static List<String> references(PersistenceMaterialIndex.DependencyRef dependency) {
    if (!"RESULT_MAP".equals(dependency.kind())) {
      return List.of(dependency.reference());
    }
    return java.util.Arrays.stream(dependency.reference().split(","))
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .toList();
  }

  private static void collectDependency(
      String kind,
      String reference,
      String resolution,
      String currentNamespace,
      Map<String, Document> resources,
      Set<String> visited,
      List<ActivityMaterialView.XmlDependency> result) {
    if (reference.isBlank() || reference.contains("${")) {
      return;
    }
    QualifiedReference qualified = qualify(currentNamespace, reference, resources.values());
    String elementName =
        "INCLUDE".equals(kind) ? "sql" : "RESULT_MAP".equals(kind) ? "resultMap" : null;
    if (elementName == null) {
      return;
    }
    String identity = kind + "\u0000" + qualified.namespace() + "\u0000" + qualified.id();
    if (!visited.add(identity)) {
      return;
    }
    for (Document document : resources.values()) {
      Element mapper = document.getDocumentElement();
      if (mapper == null
          || !"mapper".equals(localName(mapper))
          || !qualified.namespace().equals(mapper.getAttribute("namespace"))) {
        continue;
      }
      for (Element element : elementsNamed(mapper, elementName)) {
        if (!qualified.id().equals(element.getAttribute("id"))) {
          continue;
        }
        result.add(
            new ActivityMaterialView.XmlDependency(
                kind,
                reference,
                resolution,
                qualified.namespace(),
                elementName,
                qualified.id(),
                xmlNode(element)));
        collectNestedDependencies(element, qualified.namespace(), resources, visited, result);
      }
    }
  }

  private static void collectNestedDependencies(
      Element element,
      String namespace,
      Map<String, Document> resources,
      Set<String> visited,
      List<ActivityMaterialView.XmlDependency> result) {
    for (Element include : elementsNamed(element, "include")) {
      String reference = include.getAttribute("refid");
      collectDependency(
          "INCLUDE", reference, "NESTED_SAVED_REFERENCE", namespace, resources, visited, result);
    }
    for (Element candidate : allElements(element)) {
      if (candidate.hasAttribute("resultMap")) {
        for (String reference : candidate.getAttribute("resultMap").split(",")) {
          collectDependency(
              "RESULT_MAP",
              reference.trim(),
              "NESTED_SAVED_REFERENCE",
              namespace,
              resources,
              visited,
              result);
        }
      }
      if ("resultMap".equals(localName(candidate)) && candidate.hasAttribute("extends")) {
        collectDependency(
            "RESULT_MAP",
            candidate.getAttribute("extends"),
            "NESTED_SAVED_REFERENCE",
            namespace,
            resources,
            visited,
            result);
      }
    }
  }

  private static QualifiedReference qualify(
      String currentNamespace, String reference, java.util.Collection<Document> resources) {
    List<String> namespaces =
        resources.stream()
            .map(Document::getDocumentElement)
            .filter(Objects::nonNull)
            .map(element -> element.getAttribute("namespace"))
            .filter(value -> !value.isBlank())
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();
    for (String namespace : namespaces) {
      if (reference.startsWith(namespace + ".")) {
        return new QualifiedReference(namespace, reference.substring(namespace.length() + 1));
      }
    }
    return new QualifiedReference(currentNamespace, reference);
  }

  private static List<Element> elementsNamed(Node parent, String name) {
    return allElements(parent).stream().filter(element -> name.equals(localName(element))).toList();
  }

  private static List<Element> allElements(Node parent) {
    List<Element> result = new ArrayList<>();
    NodeList children = parent.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node child = children.item(index);
      if (child instanceof Element element) {
        result.add(element);
        result.addAll(allElements(element));
      }
    }
    return result;
  }

  private static PersistenceMaterialIndex.XmlNode xmlNode(Node node) {
    return switch (node.getNodeType()) {
      case Node.ELEMENT_NODE ->
          new PersistenceMaterialIndex.XmlNode(
              PersistenceMaterialIndex.XmlNodeKind.ELEMENT,
              localName(node),
              attributes((Element) node),
              null,
              xmlChildren(node));
      case Node.TEXT_NODE ->
          new PersistenceMaterialIndex.XmlNode(
              PersistenceMaterialIndex.XmlNodeKind.TEXT,
              null,
              Map.of(),
              node.getNodeValue(),
              List.of());
      case Node.CDATA_SECTION_NODE ->
          new PersistenceMaterialIndex.XmlNode(
              PersistenceMaterialIndex.XmlNodeKind.CDATA,
              null,
              Map.of(),
              node.getNodeValue(),
              List.of());
      case Node.COMMENT_NODE ->
          new PersistenceMaterialIndex.XmlNode(
              PersistenceMaterialIndex.XmlNodeKind.COMMENT,
              null,
              Map.of(),
              node.getNodeValue(),
              List.of());
      default -> throw new IllegalArgumentException("ACTIVITY_XML_NODE_UNSUPPORTED");
    };
  }

  private static List<PersistenceMaterialIndex.XmlNode> xmlChildren(Node parent) {
    List<PersistenceMaterialIndex.XmlNode> children = new ArrayList<>();
    NodeList nodes = parent.getChildNodes();
    for (int index = 0; index < nodes.getLength(); index++) {
      Node child = nodes.item(index);
      if (child.getNodeType() == Node.ELEMENT_NODE
          || child.getNodeType() == Node.TEXT_NODE
          || child.getNodeType() == Node.CDATA_SECTION_NODE
          || child.getNodeType() == Node.COMMENT_NODE) {
        children.add(xmlNode(child));
      }
    }
    return List.copyOf(children);
  }

  private static Map<String, String> attributes(Element element) {
    Map<String, String> result = new LinkedHashMap<>();
    NamedNodeMap attributes = element.getAttributes();
    for (int index = 0; index < attributes.getLength(); index++) {
      Node attribute = attributes.item(index);
      result.put(attribute.getNodeName(), attribute.getNodeValue());
    }
    return Map.copyOf(result);
  }

  private ObjectNode xml(PersistenceMaterialIndex.XmlNode node) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("kind", node.kind().name());
    nullableText(value, "elementName", node.elementName());
    value.set("attributes", stringMap(node.attributes()));
    nullableText(value, "content", node.content());
    ArrayNode children = value.putArray("children");
    node.children().forEach(child -> children.add(xml(child)));
    return value;
  }

  private ObjectNode xmlDependency(ActivityMaterialView.XmlDependency dependency) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("namespace", dependency.namespace());
    value.put("elementName", dependency.elementName());
    value.put("elementId", dependency.elementId());
    value.set("xml", xml(dependency.xml()));
    return value;
  }

  private static boolean sameDependency(
      ActivityMaterialView.XmlDependency candidate,
      PersistenceMaterialIndex.DependencyRef declared) {
    return candidate.kind().equals(declared.kind())
        && (candidate.reference().equals(declared.reference())
            || ("RESULT_MAP".equals(declared.kind())
                && references(declared).contains(candidate.reference())));
  }

  private static String localUnitRef(ActivityMaterialView view, String originalRef) {
    String method = view.methodRefsByKey().get(originalRef);
    if (method != null) {
      return method;
    }
    return view.statementRefsById().get(originalRef);
  }

  private static List<String> sourceRefsForMethod(ActivityMaterialView view, String methodKey) {
    return view.packet().sourceReferences().stream()
        .filter(source -> methodKey.equals(source.location().unitRef()))
        .map(source -> view.sourceRefsById().get(source.sourceRef()))
        .toList();
  }

  private static List<String> sourceRefsForResource(ActivityMaterialView view, String resourceRef) {
    return view.packet().sourceReferences().stream()
        .filter(source -> resourceRef.equals(source.location().unitRef()))
        .map(source -> view.sourceRefsById().get(source.sourceRef()))
        .toList();
  }

  private static ObjectNode relativePosition(
      EntryCodeContext.MethodCode method, SourceRange position) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    int methodStart = method == null ? 0 : method.source().startOffsetUtf16();
    value.put("offsetUtf16", Math.max(0, position.startOffsetUtf16() - methodStart));
    value.put("lengthUtf16", position.lengthUtf16());
    return value;
  }

  private static ObjectNode stringMap(Map<String, String> values) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    values.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
    return result;
  }

  private static ArrayNode strings(java.util.Collection<String> values) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    values.forEach(result::add);
    return result;
  }

  private static ArrayNode integers(java.util.Collection<Integer> values) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    values.forEach(result::add);
    return result;
  }

  private static void nullableText(ObjectNode node, String field, String value) {
    if (value == null) {
      node.putNull(field);
    } else {
      node.put(field, value);
    }
  }

  private static <T> Map<String, String> refs(
      List<T> values, java.util.function.Function<T, String> identity, String prefix) {
    List<String> identities = values.stream().map(identity).distinct().sorted().toList();
    Map<String, String> result = new LinkedHashMap<>();
    for (int index = 0; index < identities.size(); index++) {
      result.put(identities.get(index), prefix + (index + 1));
    }
    return result;
  }

  private static Map<String, String> invert(Map<String, String> values) {
    Map<String, String> result = new LinkedHashMap<>();
    values.forEach((original, local) -> result.put(local, original));
    return result;
  }

  private static String localName(Node node) {
    return node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
  }

  private record QualifiedReference(String namespace, String id) {}

  private static final class EntryCodeContextKey {
    private EntryCodeContextKey() {}

    private static String entryId(org.sourceanalysis.app.analysis.code.EntrySeed entry) {
      return entry.entryId();
    }
  }
}
