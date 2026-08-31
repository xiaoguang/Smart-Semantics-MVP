package com.linguan.codemd.stage01;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic, source-only M2 compiler. It never walks a repository root. */
final class RepositoryCompiler {
    private static final String SCHEMA_VERSION = "repository-understanding-v1";
    private static final String RULE_ROUTE_PART = "m2-route-part-v1";
    private static final String RULE_CALL_TARGET = "m2-direct-field-call-v1";
    private static final String RULE_NAMESPACE_INTERFACE = "m2-mybatis-namespace-v1";
    private static final String RULE_METHOD_STATEMENT = "m2-mybatis-method-statement-v1";
    private static final String RULE_STATEMENT_SQL = "m2-static-sql-v1";
    private static final String RULE_RESULT_TYPE = "m2-result-type-v1";
    private static final String RULE_CONFIG_MAPPER = "m2-config-mapper-v1";
    private static final String RULE_CFG_ENTRY = "m2-cfg-entry-v1";
    private static final String RULE_CFG_NEXT = "m2-cfg-next-v1";
    private static final String RULE_CFG_TRUE = "m2-cfg-true-v1";
    private static final String RULE_CFG_FALSE = "m2-cfg-false-v1";
    private static final String RULE_CFG_CALL = "m2-cfg-call-v1";
    private static final String RULE_CFG_RETURN = "m2-cfg-return-v1";
    private static final String RULE_CFG_TERMINAL = "m2-cfg-terminal-v1";
    private static final Set<String> MAPPER_STATEMENTS = Set.of("select", "insert", "update", "delete");
    private static final Pattern XML_OPENING_TAG = Pattern.compile(
            "<(select|insert|update|delete)\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern XML_ATTRIBUTE = Pattern.compile(
            "\\b([A-Za-z_:][A-Za-z0-9_.:-]*)\\s*=\\s*(['\\\"])(.*?)\\2", Pattern.DOTALL);
    private static final Pattern EXTERNAL_ENTITY_DECLARATION = Pattern.compile(
            "(?is)<!ENTITY\\s+(?:%\\s+)?[^>]*\\b(?:SYSTEM|PUBLIC)\\b");
    private static final Pattern SQL_SELECT = Pattern.compile(
            "(?is)\\bselect\\s+(.+?)\\s+from\\s+([A-Za-z_][A-Za-z0-9_.$]*)");
    private static final Pattern SQL_UPDATE = Pattern.compile(
            "(?is)\\bupdate\\s+([A-Za-z_][A-Za-z0-9_.$]*)\\s+set\\s+(.+?)(?:\\s+where\\s+(.+))?$");
    private static final Pattern MAPPER_LOCATIONS = Pattern.compile(
            "(?m)^\\s*mybatis\\.mapper-locations\\s*:\\s*([^\\s#]+)");

    RepositoryUnderstanding understand(VerifiedSnapshot snapshot, FrozenRepositoryRequest request,
                                       Map<String, byte[]> verifiedBytes) {
        if (snapshot == null || request == null || verifiedBytes == null
                || verifiedBytes.size() != snapshot.files().size()) {
            throw failure(Stage01FailureCode.M2_GRAPH_INVARIANT_BROKEN);
        }
        Map<String, SourceText> sources = declaredSources(snapshot, verifiedBytes);
        Graph graph = new Graph(snapshot.snapshotId());
        ResourceBudget budget = snapshot.resourceBudget();
        List<JavaTypeData> javaTypes = parseJavaSources(sources, budget,
                CapabilityProfileRegistry.parserLanguage(snapshot.capabilityProfileRef()), graph);
        Map<String, JavaTypeData> typesByName = javaTypes.stream().collect(
                java.util.stream.Collectors.toMap(JavaTypeData::qualifiedName, type -> type,
                        (left, right) -> left, LinkedHashMap::new));
        indexJavaMembers(javaTypes, typesByName, graph);

        List<XmlMapperData> xmlMappers = parseXmlSources(sources, budget, graph);
        addMavenAndConfigNodes(sources, xmlMappers, graph);

        List<RouteCandidate> routes = findRoutes(javaTypes, graph);
        List<RepositoryEntry> entries = buildEntries(snapshot.snapshotId(), routes);
        List<String> allEntryIds = entries.stream().map(RepositoryEntry::entryId).toList();
        Map<String, RepositoryEntry> entryByMethodNode = new HashMap<>();
        for (RepositoryEntry entry : entries) {
            entryByMethodNode.put(entry.methodNodeId(), entry);
            graph.site("SPRING_MVC_ROUTE", graph.node(entry.methodNodeId()).locator(),
                    List.of(entry.entryId()), "SUPPORTED", null);
        }

        Map<String, MethodData> methodsByNodeId = methodsByNodeId(javaTypes);
        DirectCallDiscovery directCalls = findDirectCalls(javaTypes, graph);
        Map<String, Set<String>> callOwners = entryOwners(entries, directCalls.targetsByCaller());
        for (PendingCallSite site : directCalls.sites()) {
            graph.site("DIRECT_FIELD_CALL", site.locator(), new ArrayList<>(callOwners
                    .getOrDefault(site.callerMethodNodeId(), Set.of())), site.disposition(), site.reasonCode());
        }
        addMyBatisBindings(javaTypes, typesByName, xmlMappers, budget, graph, allEntryIds);
        List<ControlFlow> controlFlows = buildControlFlows(entries, directCalls.firstTargets(), methodsByNodeId,
                budget, graph);

        List<RepositoryNode> nodes = graph.nodes();
        List<RepositoryEdge> edges = graph.edges();
        validateGraph(entries, nodes, edges, controlFlows, graph.sites());
        String modelId = "repository-model:" + sha256(snapshot.snapshotId() + "\n"
                + canonicalModel(entries, nodes, edges, controlFlows));
        RepositoryModel model = new RepositoryModel(modelId, entries, nodes, edges, controlFlows);

        List<CapabilitySite> sites = graph.sites();
        CapabilityCoverage coverage = coverage(sites);
        String reportId = "capability-report:" + sha256(snapshot.snapshotId() + "\n"
                + canonicalReport(snapshot.capabilityProfileRef(), sites, coverage));
        CapabilityReport report = new CapabilityReport(reportId, snapshot.capabilityProfileRef(), sites,
                coverage);
        return new RepositoryUnderstanding(SCHEMA_VERSION, snapshot.snapshotId(), model, report);
    }

    private static Map<String, SourceText> declaredSources(VerifiedSnapshot snapshot,
                                                             Map<String, byte[]> verifiedBytes) {
        Map<String, SourceText> result = new LinkedHashMap<>();
        for (VerifiedFile file : snapshot.files()) {
            byte[] bytes = verifiedBytes.get(file.path());
            if (bytes == null || bytes.length != file.sizeBytes()
                    || !sha256(bytes).equals(file.sha256())) {
                throw failure(Stage01FailureCode.VERIFIED_SOURCE_REOPEN_MISMATCH);
            }
            result.put(file.path(), new SourceText(file.path(), file.mediaType(), bytes));
        }
        return Map.copyOf(result);
    }

    private static List<JavaTypeData> parseJavaSources(Map<String, SourceText> sources,
                                                       ResourceBudget budget,
                                                       ParserConfiguration.LanguageLevel languageLevel,
                                                       Graph graph) {
        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(languageLevel));
        List<JavaTypeData> result = new ArrayList<>();
        for (SourceText source : sources.values()) {
            if (!"JAVA".equals(source.mediaType())) {
                continue;
            }
            ParseResult<CompilationUnit> parsed = parser.parse(source.text());
            if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
                graph.site("JAVA_PARSE", source.whole(), List.of(), "UNSUPPORTED",
                        "JAVA_PARSE_UNRESOLVED");
                continue;
            }
            CompilationUnit unit = parsed.getResult().get();
            if (unit.findAll(Node.class).size() > budget.maxAstNodes()) {
                graph.site("JAVA_AST", source.whole(), List.of(), "OVER_LIMIT", "AST_NODE_LIMIT");
                continue;
            }
            String packageName = unit.getPackageDeclaration()
                    .map(declaration -> declaration.getNameAsString()).orElse("");
            unit.getPackageDeclaration().ifPresent(declaration -> graph.addNode("JAVA_PACKAGE", source,
                    declaration, "v1:" + packageName));
            for (ImportDeclaration imported : unit.getImports()) {
                graph.addNode("JAVA_IMPORT", source, imported, "v1:" + imported.getNameAsString()
                        + ":" + imported.isAsterisk());
            }
            for (ClassOrInterfaceDeclaration declaration : unit.getTypes().stream()
                    .filter(ClassOrInterfaceDeclaration.class::isInstance)
                    .map(ClassOrInterfaceDeclaration.class::cast).toList()) {
                String qualifiedName = packageName.isBlank() ? declaration.getNameAsString()
                        : packageName + "." + declaration.getNameAsString();
                RepositoryNode typeNode = graph.addNode("JAVA_TYPE", source, declaration,
                        "v1:" + qualifiedName + ":" + declaration.isInterface());
                result.add(new JavaTypeData(qualifiedName, packageName, source, unit, declaration,
                        typeNode.nodeId()));
            }
            for (RecordDeclaration declaration : unit.findAll(RecordDeclaration.class)) {
                String qualifiedName = packageName.isBlank() ? declaration.getNameAsString()
                        : packageName + "." + declaration.getNameAsString();
                RepositoryNode recordNode = graph.addNode("JAVA_RECORD_DECLARATION", source, declaration,
                        "v1:" + qualifiedName);
                declaration.getParameters().forEach(component -> {
                    RepositoryNode componentNode = graph.addNode("JAVA_RECORD_COMPONENT", source, component,
                            "v1:" + qualifiedName + "#" + component.getNameAsString());
                    graph.addEdge("RECORD_DECLARATION_COMPONENT", recordNode.nodeId(), componentNode.nodeId(),
                            "JAVA_RECORD_DECLARATION_TO_COMPONENT_V1", "EXACT");
                });
            }
        }
        return result.stream().sorted(Comparator.comparing(JavaTypeData::qualifiedName)
                .thenComparing(type -> type.source().path())).toList();
    }

    private static void indexJavaMembers(List<JavaTypeData> javaTypes,
                                         Map<String, JavaTypeData> typesByName, Graph graph) {
        for (JavaTypeData type : javaTypes) {
            for (FieldDeclaration field : type.declaration().getFields()) {
                TypeResolution resolution = resolveType(field.getElementType(), type, typesByName);
                for (var variable : field.getVariables()) {
                    RepositoryNode fieldNode = graph.addNode("JAVA_FIELD", type.source(), field,
                            "v1:" + type.qualifiedName() + "#" + variable.getNameAsString());
                    type.fields().put(variable.getNameAsString(), new FieldData(fieldNode.nodeId(),
                            resolution.candidates(), resolution.reasonCode()));
                    if (resolution.candidates().size() == 1) {
                        graph.addEdge("RECEIVER_TYPE", fieldNode.nodeId(),
                                resolution.candidates().get(0).typeNodeId(),
                                "m2-receiver-type-v1", "EXACT");
                    }
                }
            }
            for (MethodDeclaration method : ordered(type.declaration().getMethods(), type.source())) {
                RepositoryNode methodNode = graph.addNode("JAVA_METHOD", type.source(), method,
                        "v1:" + type.qualifiedName() + "#" + method.getNameAsString()
                                + "/" + method.getParameters().size());
                MethodData methodData = new MethodData(type, method, methodNode.nodeId());
                type.methods().add(methodData);
                for (var parameter : method.getParameters()) {
                    RepositoryNode parameterNode = graph.addNode("JAVA_PARAMETER", type.source(), parameter,
                            "v1:" + methodData.nodeId() + ":" + parameter.getNameAsString());
                    for (RecordDeclaration record : type.unit().findAll(RecordDeclaration.class)) {
                        if (!parameter.getType().asString().equals(record.getNameAsString())) {
                            continue;
                        }
                        String qualifiedName = type.packageName().isBlank() ? record.getNameAsString()
                                : type.packageName() + "." + record.getNameAsString();
                        RepositoryNode recordNode = graph.addNode("JAVA_RECORD_DECLARATION", type.source(), record,
                                "v1:" + qualifiedName);
                        graph.addEdge("PARAMETER_TYPE", parameterNode.nodeId(), recordNode.nodeId(),
                                "m2-parameter-type-v1", "EXACT");
                    }
                }
                // M3 candidate accounting must remain total even when a surrounding route binding
                // is incomplete. These parsed semantic nodes are therefore indexed per method,
                // while buildControlFlows below only decides which supported entry owns them.
                for (IfStmt guard : ordered(method.findAll(IfStmt.class), type.source())) {
                    graph.addNode("JAVA_GUARD", type.source(), guard,
                            "v1:" + normalize(guard.getCondition().toString()));
                }
                for (ThrowStmt terminal : ordered(method.findAll(ThrowStmt.class), type.source())) {
                    graph.addNode("JAVA_THROW", type.source(), terminal,
                            "v1:" + normalize(terminal.toString()));
                }
                for (ReturnStmt terminal : ordered(method.findAll(ReturnStmt.class), type.source())) {
                    graph.addNode("JAVA_RETURN", type.source(), terminal,
                            "v1:" + normalize(terminal.toString()));
                }
            }
        }
    }

    private static List<RouteCandidate> findRoutes(List<JavaTypeData> javaTypes, Graph graph) {
        List<RouteCandidate> result = new ArrayList<>();
        for (JavaTypeData type : javaTypes) {
            Optional<AnnotationExpr> classMapping = springAnnotation(type, type.declaration(),
                    "org.springframework.web.bind.annotation.RequestMapping");
            String prefix = "";
            RepositoryNode classAnnotation = null;
            if (classMapping.isPresent()) {
                Optional<String> declaredPrefix = mappingPath(classMapping.get());
                if (declaredPrefix.isEmpty()) {
                    graph.site("SPRING_MVC_ROUTE", type.source().locator(classMapping.get()), List.of(),
                            "UNSUPPORTED", "DYNAMIC_ROUTE_UNRESOLVED");
                    continue;
                }
                prefix = declaredPrefix.get();
                classAnnotation = graph.addNode("JAVA_ANNOTATION", type.source(), classMapping.get(),
                        "v1:" + normalize(classMapping.get().toString()));
            }
            for (MethodData method : type.methods()) {
                Optional<AnnotationExpr> postMapping = springAnnotation(type, method.declaration(),
                        "org.springframework.web.bind.annotation.PostMapping");
                if (postMapping.isEmpty()) {
                    continue;
                }
                Optional<String> suffix = mappingPath(postMapping.get());
                if (suffix.isEmpty()) {
                    graph.site("SPRING_MVC_ROUTE", type.source().locator(postMapping.get()), List.of(),
                            "UNSUPPORTED", "DYNAMIC_ROUTE_UNRESOLVED");
                    continue;
                }
                RepositoryNode methodAnnotation = graph.addNode("JAVA_ANNOTATION", type.source(),
                        postMapping.get(), "v1:" + normalize(postMapping.get().toString()));
                List<String> routeNodes = new ArrayList<>();
                if (classAnnotation != null) {
                    graph.addEdge("ROUTE_PART", classAnnotation.nodeId(), methodAnnotation.nodeId(),
                            RULE_ROUTE_PART, "EXACT");
                    routeNodes.add(classAnnotation.nodeId());
                }
                routeNodes.add(methodAnnotation.nodeId());
                result.add(new RouteCandidate(method.nodeId(), joinPath(prefix, suffix.get()), routeNodes));
            }
        }
        return result.stream().sorted(Comparator.comparing(RouteCandidate::route)
                .thenComparing(RouteCandidate::methodNodeId)).toList();
    }

    private static List<RepositoryEntry> buildEntries(String snapshotId, List<RouteCandidate> routes) {
        return routes.stream().map(route -> new RepositoryEntry("entry:" + sha256(snapshotId + "\nPOST\n"
                + route.route() + "\n" + route.methodNodeId()), "SPRING_MVC_HTTP", "POST",
                route.route(), route.routeNodeIds(), route.methodNodeId())).sorted(
                Comparator.comparing(RepositoryEntry::entryId)).toList();
    }

    private static Map<String, MethodData> methodsByNodeId(List<JavaTypeData> javaTypes) {
        Map<String, MethodData> result = new HashMap<>();
        for (JavaTypeData type : javaTypes) {
            for (MethodData method : type.methods()) {
                result.put(method.nodeId(), method);
            }
        }
        return Map.copyOf(result);
    }

    private static DirectCallDiscovery findDirectCalls(List<JavaTypeData> javaTypes, Graph graph) {
        Map<String, List<String>> directTargets = new LinkedHashMap<>();
        List<PendingCallSite> sites = new ArrayList<>();
        for (JavaTypeData type : javaTypes) {
            for (MethodData method : type.methods()) {
                Set<String> localNames = localNames(method.declaration());
                for (MethodCallExpr call : ordered(method.declaration().findAll(MethodCallExpr.class),
                        type.source())) {
                    Optional<String> receiver = fieldReceiver(call, localNames);
                    if (receiver.isEmpty()) {
                        continue;
                    }
                    FieldData field = type.fields().get(receiver.get());
                    if (field == null) {
                        continue;
                    }
                    RepositoryNode callNode = graph.addNode("JAVA_CALL", type.source(), call,
                            "v1:" + normalize(call.toString()));
                    if (field.candidates().size() != 1) {
                        String reason = field.reasonCode() == null ? "AMBIGUOUS_RECEIVER_TYPE"
                                : field.reasonCode();
                        String disposition = field.reasonCode() == null ? "AMBIGUOUS" : "UNSUPPORTED";
                        sites.add(new PendingCallSite(method.nodeId(), callNode.locator(), disposition, reason));
                        continue;
                    }
                    JavaTypeData targetType = field.candidates().get(0);
                    List<MethodData> targets = targetType.methods().stream()
                            .filter(candidate -> candidate.declaration().getNameAsString()
                                    .equals(call.getNameAsString())).toList();
                    if (targets.size() != 1) {
                        sites.add(new PendingCallSite(method.nodeId(), callNode.locator(), "AMBIGUOUS",
                                "AMBIGUOUS_OVERLOAD"));
                        continue;
                    }
                    MethodData target = targets.get(0);
                    if (target.declaration().getParameters().size() != call.getArguments().size()) {
                        sites.add(new PendingCallSite(method.nodeId(), callNode.locator(), "UNSUPPORTED",
                                "CALL_ARITY_MISMATCH"));
                        continue;
                    }
                    graph.addEdge("CALL_TARGET", callNode.nodeId(), target.nodeId(), RULE_CALL_TARGET,
                            "EXACT");
                    sites.add(new PendingCallSite(method.nodeId(), callNode.locator(), "SUPPORTED", null));
                    directTargets.computeIfAbsent(method.nodeId(), ignored -> new ArrayList<>())
                            .add(target.nodeId());
                }
            }
        }
        Map<String, List<String>> orderedTargets = new LinkedHashMap<>();
        directTargets.forEach((caller, targets) -> orderedTargets.put(caller,
                targets.stream().distinct().sorted().toList()));
        Map<String, String> firstTargets = new LinkedHashMap<>();
        orderedTargets.forEach((caller, targets) -> {
            if (!targets.isEmpty()) {
                firstTargets.put(caller, targets.get(0));
            }
        });
        return new DirectCallDiscovery(Map.copyOf(orderedTargets), Map.copyOf(firstTargets), List.copyOf(sites));
    }

    private static Map<String, Set<String>> entryOwners(List<RepositoryEntry> entries,
                                                         Map<String, List<String>> targetsByCaller) {
        Map<String, Set<String>> owners = new HashMap<>();
        for (RepositoryEntry entry : entries) {
            ArrayDeque<String> pending = new ArrayDeque<>();
            Set<String> visited = new HashSet<>();
            pending.add(entry.methodNodeId());
            while (!pending.isEmpty()) {
                String methodNodeId = pending.removeFirst();
                if (!visited.add(methodNodeId)) {
                    continue;
                }
                owners.computeIfAbsent(methodNodeId, ignored -> new LinkedHashSet<>())
                        .add(entry.entryId());
                for (String target : targetsByCaller.getOrDefault(methodNodeId, List.of())) {
                    pending.addLast(target);
                }
            }
        }
        return owners;
    }

    private static void addMavenAndConfigNodes(Map<String, SourceText> sources,
                                                List<XmlMapperData> mappers, Graph graph) {
        for (SourceText source : sources.values()) {
            if ("MAVEN_POM".equals(source.mediaType())) {
                RepositoryNode node = graph.addNode("MAVEN_DECLARATION", source, source.whole(),
                        "v1:" + normalize(source.text()));
                graph.site("MAVEN_DECLARATION", node.locator(), List.of(), "SUPPORTED", null);
            }
            if ("YAML".equals(source.mediaType()) && source.text().contains("mybatis.mapper-locations")) {
                RepositoryNode config = graph.addNode("CONFIG_ENTRY", source, source.whole(),
                        "v1:" + normalize(source.text()));
                Optional<String> mapperLocation = mapperLocation(source.text());
                for (XmlMapperData mapper : mappers) {
                    if (mapperLocation.isPresent()
                            && matchesMapperLocation(mapper.source().path(), mapperLocation.get())) {
                        graph.addEdge("CONFIG_RESOLVES_MAPPER", config.nodeId(), mapper.namespaceNodeId(),
                                RULE_CONFIG_MAPPER, "EXACT");
                    }
                }
                graph.site("CONFIG_MAPPER_LOCATION", config.locator(), List.of(), "SUPPORTED", null);
            }
        }
    }

    private static Optional<String> mapperLocation(String yaml) {
        Matcher matcher = MAPPER_LOCATIONS.matcher(yaml);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String value = matcher.group(1).replace("classpath*:", "").replace("classpath:", "");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static boolean matchesMapperLocation(String declaredPath, String locationPattern) {
        String marker = "src/main/resources/";
        int resourceRoot = declaredPath.indexOf(marker);
        if (resourceRoot < 0) {
            return false;
        }
        String resourcePath = declaredPath.substring(resourceRoot + marker.length());
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < locationPattern.length(); index++) {
            char character = locationPattern.charAt(index);
            if (character == '*') {
                regex.append("[^/]*");
            } else if ("\\.^$|?+()[]{}".indexOf(character) >= 0) {
                regex.append('\\').append(character);
            } else {
                regex.append(character);
            }
        }
        return resourcePath.matches(regex.append('$').toString());
    }

    private static List<XmlMapperData> parseXmlSources(Map<String, SourceText> sources,
                                                        ResourceBudget budget, Graph graph) {
        List<XmlMapperData> result = new ArrayList<>();
        for (SourceText source : sources.values()) {
            if (!"XML".equals(source.mediaType())) {
                continue;
            }
            Document document = parseSecureXml(source);
            if (document == null) {
                graph.site("MYBATIS_XML", source.whole(), List.of(), "UNSUPPORTED",
                        "XML_PARSE_UNRESOLVED");
                continue;
            }
            if (xmlNodeCount(document) > budget.maxXmlNodes()) {
                graph.site("MYBATIS_XML", source.whole(), List.of(), "OVER_LIMIT", "XML_NODE_LIMIT");
                continue;
            }
            Element mapper = document.getDocumentElement();
            if (mapper == null || !"mapper".equals(mapper.getTagName())
                    || mapper.getAttribute("namespace").isBlank()) {
                graph.site("MYBATIS_XML", source.whole(), List.of(), "UNSUPPORTED",
                        "MAPPER_NAMESPACE_UNRESOLVED");
                continue;
            }
            String namespace = mapper.getAttribute("namespace");
            RepositoryLocator mapperLocator = xmlElementLocator(source, "mapper", namespace, 0);
            RepositoryNode namespaceNode = graph.addNode("XML_MAPPER_NAMESPACE", source, mapperLocator,
                    "v1:" + namespace);
            result.add(new XmlMapperData(source, namespace, namespaceNode.nodeId(),
                    xmlStatements(source, mapper, graph)));
        }
        return result.stream().sorted(Comparator.comparing(XmlMapperData::namespace)
                .thenComparing(mapper -> mapper.source().path())).toList();
    }

    private static void addMyBatisBindings(List<JavaTypeData> javaTypes,
                                           Map<String, JavaTypeData> typesByName,
                                           List<XmlMapperData> mappers, ResourceBudget budget, Graph graph,
                                           List<String> entryIds) {
        for (XmlMapperData mapper : mappers) {
            JavaTypeData interfaceType = typesByName.get(mapper.namespace());
            if (interfaceType == null || !interfaceType.declaration().isInterface()) {
                graph.site("MYBATIS_NAMESPACE", graph.node(mapper.namespaceNodeId()).locator(), entryIds,
                        "UNSUPPORTED", "NAMESPACE_INTERFACE_UNRESOLVED");
                continue;
            }
            graph.addEdge("NAMESPACE_INTERFACE", mapper.namespaceNodeId(), interfaceType.typeNodeId(),
                    RULE_NAMESPACE_INTERFACE, "EXACT");
            graph.site("MYBATIS_NAMESPACE", graph.node(mapper.namespaceNodeId()).locator(), entryIds,
                    "SUPPORTED", null);
            Map<String, List<XmlStatementData>> statementsById = mapper.statements().stream()
                    .collect(java.util.stream.Collectors.groupingBy(XmlStatementData::id,
                            LinkedHashMap::new, java.util.stream.Collectors.toList()));
            for (List<XmlStatementData> candidates : statementsById.values()) {
                XmlStatementData statement = candidates.get(0);
                List<MethodData> methods = interfaceType.methods().stream()
                        .filter(method -> method.declaration().getNameAsString().equals(statement.id())).toList();
                if (methods.size() != 1 || candidates.size() != 1) {
                    String reason = methods.size() != 1 ? "AMBIGUOUS_MAPPER_METHOD"
                            : "AMBIGUOUS_MAPPER_STATEMENT";
                    for (XmlStatementData candidate : candidates) {
                        graph.site("MAPPER_METHOD_STATEMENT", candidate.locator(), entryIds,
                                "AMBIGUOUS", reason);
                    }
                    continue;
                }
                graph.addEdge("METHOD_STATEMENT", methods.get(0).nodeId(), statement.nodeId(),
                        RULE_METHOD_STATEMENT, "EXACT");
                graph.site("MAPPER_METHOD_STATEMENT", statement.locator(), entryIds,
                        "SUPPORTED", null);
                if (statement.dynamic()) {
                    graph.site("STATIC_SQL", statement.locator(), entryIds, "UNSUPPORTED",
                            "DYNAMIC_SQL_UNRESOLVED");
                } else if (statement.sql().length() > budget.maxSqlChars()) {
                    graph.site("STATIC_SQL", statement.locator(), entryIds, "OVER_LIMIT",
                            "SQL_CHAR_LIMIT");
                } else if (!"select".equals(statement.elementName())
                        && !"update".equals(statement.elementName())) {
                    graph.site("STATIC_SQL", statement.locator(), entryIds, "UNSUPPORTED",
                            "UNSUPPORTED_SQL_OPERATION");
                } else {
                    addStaticSql(statement, graph);
                    graph.site("STATIC_SQL", statement.locator(), entryIds, "SUPPORTED", null);
                }
                if (statement.resultType() != null && !statement.resultType().isBlank()) {
                    RepositoryNode resultType = graph.addNode("XML_RESULT_TYPE", mapper.source(),
                            statement.locator(), "v1:" + statement.resultType());
                    graph.addEdge("RESULT_TYPE", statement.nodeId(), resultType.nodeId(), RULE_RESULT_TYPE,
                            "EXACT");
                    graph.site("MYBATIS_RESULT_TYPE", statement.locator(), entryIds,
                            "SUPPORTED", null);
                }
            }
        }
    }

    private static void addStaticSql(XmlStatementData statement, Graph graph) {
        String sql = normalize(statement.sql());
        RepositoryNode fragment;
        Matcher select = SQL_SELECT.matcher(sql);
        Matcher update = SQL_UPDATE.matcher(sql);
        if (select.find()) {
            RepositoryNode table = graph.addNode("SQL_TABLE", statement.source(), statement.locator(),
                    "v1:" + select.group(2));
            RepositoryNode projection = graph.addNode("SQL_PROJECTION", statement.source(), statement.locator(),
                    "v1:" + normalize(select.group(1)));
            graph.addEdge("STATEMENT_SQL_FRAGMENT", statement.nodeId(), table.nodeId(), RULE_STATEMENT_SQL,
                    "EXACT");
            graph.addEdge("STATEMENT_SQL_FRAGMENT", statement.nodeId(), projection.nodeId(), RULE_STATEMENT_SQL,
                    "EXACT");
            int where = indexOfIgnoreCase(sql, " where ");
            if (where >= 0) {
                fragment = graph.addNode("SQL_PREDICATE", statement.source(), statement.locator(),
                        "v1:" + sql.substring(where + 7));
                graph.addEdge("STATEMENT_SQL_FRAGMENT", statement.nodeId(), fragment.nodeId(),
                        RULE_STATEMENT_SQL, "EXACT");
            }
        } else if (update.find()) {
            RepositoryNode table = graph.addNode("SQL_TABLE", statement.source(), statement.locator(),
                    "v1:" + update.group(1));
            RepositoryNode assignment = graph.addNode("SQL_ASSIGNMENT", statement.source(), statement.locator(),
                    "v1:" + normalize(update.group(2)));
            graph.addEdge("STATEMENT_SQL_FRAGMENT", statement.nodeId(), table.nodeId(), RULE_STATEMENT_SQL,
                    "EXACT");
            graph.addEdge("STATEMENT_SQL_FRAGMENT", statement.nodeId(), assignment.nodeId(), RULE_STATEMENT_SQL,
                    "EXACT");
            if (update.group(3) != null) {
                fragment = graph.addNode("SQL_PREDICATE", statement.source(), statement.locator(),
                        "v1:" + normalize(update.group(3)));
                graph.addEdge("STATEMENT_SQL_FRAGMENT", statement.nodeId(), fragment.nodeId(),
                        RULE_STATEMENT_SQL, "EXACT");
            }
        } else {
            fragment = graph.addNode("SQL_TABLE", statement.source(), statement.locator(), "v1:" + sql);
            graph.addEdge("STATEMENT_SQL_FRAGMENT", statement.nodeId(), fragment.nodeId(), RULE_STATEMENT_SQL,
                    "EXACT");
        }
    }

    private static List<ControlFlow> buildControlFlows(List<RepositoryEntry> entries,
                                                        Map<String, String> directTargets,
                                                        Map<String, MethodData> methodsByNodeId,
                                                        ResourceBudget budget, Graph graph) {
        List<ControlFlow> flows = new ArrayList<>();
        for (RepositoryEntry entry : entries) {
            String entryMethodNodeId = entry.methodNodeId();
            MethodData entryMethod = methodsByNodeId.get(entryMethodNodeId);
            String targetMethodNodeId = directTargets.getOrDefault(entryMethodNodeId, entryMethodNodeId);
            MethodData method = methodsByNodeId.get(targetMethodNodeId);
            if (method == null || method.owner().declaration().isInterface()) {
                continue;
            }
            if (method.declaration().findAll(Node.class).size() > budget.maxControlFlowNodes()) {
                graph.site("CONTROL_FLOW", graph.node(method.nodeId()).locator(), List.of(entry.entryId()),
                        "OVER_LIMIT", "CONTROL_FLOW_NODE_LIMIT");
                continue;
            }
            List<String> flowEdgeIds = new ArrayList<>();
            if (!entryMethodNodeId.equals(method.nodeId())) {
                flowEdgeIds.add(graph.addCfgEdge("CFG_ENTRY", entryMethodNodeId, method.nodeId(),
                        RULE_CFG_ENTRY, null, null));
                flowEdgeIds.add(graph.addCfgEdge("CFG_CALL", entryMethodNodeId, method.nodeId(),
                        RULE_CFG_CALL, null, null));
                flowEdgeIds.add(graph.addCfgEdge("CFG_RETURN", method.nodeId(), entryMethodNodeId,
                        RULE_CFG_RETURN, null, null));
            } else {
                flowEdgeIds.add(graph.addCfgEdge("CFG_ENTRY", method.nodeId(), method.nodeId(),
                        RULE_CFG_ENTRY, null, null));
            }

            // This profile supports only top-level binary guards whose true arm is one explicit
            // throw and whose false arm continues through the enclosing method block.  The
            // relationship comes from the parsed AST block/branch structure, never from a list
            // of source line numbers or terminal ordering.
            List<IfStmt> guardsInMethod = method.declaration().getBody().stream()
                    .flatMap(body -> body.getStatements().stream())
                    .filter(IfStmt.class::isInstance).map(IfStmt.class::cast).toList();
            List<String> guards = new ArrayList<>();
            String predecessor = method.nodeId();
            String pendingFalseGuard = null;
            for (IfStmt guard : guardsInMethod) {
                RepositoryNode guardNode = graph.addNode("JAVA_GUARD", method.owner().source(), guard,
                        "v1:" + normalize(guard.getCondition().toString()));
                guards.add(guardNode.nodeId());
                if (pendingFalseGuard == null) {
                    flowEdgeIds.add(graph.addCfgEdge("CFG_NEXT", predecessor, guardNode.nodeId(),
                            RULE_CFG_NEXT, null, null));
                } else {
                    flowEdgeIds.add(graph.addCfgEdge("CFG_FALSE", pendingFalseGuard, guardNode.nodeId(),
                            RULE_CFG_FALSE, pendingFalseGuard, "FALSE"));
                }
                List<ThrowStmt> trueTerminals = guard.getThenStmt().findAll(ThrowStmt.class);
                if (trueTerminals.size() != 1 || guard.getElseStmt().isPresent()) {
                    graph.site("CONTROL_FLOW", guardNode.locator(), List.of(entry.entryId()),
                            "UNSUPPORTED", "CFG_BRANCH_UNRESOLVED");
                    pendingFalseGuard = null;
                    predecessor = guardNode.nodeId();
                    continue;
                }
                RepositoryNode terminal = graph.addNode("JAVA_THROW", method.owner().source(),
                        trueTerminals.get(0), "v1:" + normalize(trueTerminals.get(0).toString()));
                flowEdgeIds.add(graph.addCfgEdge("CFG_TRUE", guardNode.nodeId(), terminal.nodeId(),
                        RULE_CFG_TRUE, guardNode.nodeId(), "TRUE"));
                flowEdgeIds.add(graph.addCfgEdge("CFG_TERMINAL", terminal.nodeId(), terminal.nodeId(),
                        RULE_CFG_TERMINAL, null, null));
                pendingFalseGuard = guardNode.nodeId();
                predecessor = guardNode.nodeId();
            }
            List<String> terminals = new ArrayList<>();
            for (ThrowStmt terminal : ordered(method.declaration().findAll(ThrowStmt.class), method.owner().source())) {
                terminals.add(graph.addNode("JAVA_THROW", method.owner().source(), terminal,
                        "v1:" + normalize(terminal.toString())).nodeId());
            }
            for (ReturnStmt terminal : ordered(method.declaration().findAll(ReturnStmt.class), method.owner().source())) {
                terminals.add(graph.addNode("JAVA_RETURN", method.owner().source(), terminal,
                        "v1:" + normalize(terminal.toString())).nodeId());
            }
            terminals.sort(Comparator.comparing(nodeId -> graph.node(nodeId).locator().startByte()));
            List<ReturnStmt> returns = method.declaration().getBody().stream()
                    .flatMap(body -> body.getStatements().stream())
                    .filter(ReturnStmt.class::isInstance).map(ReturnStmt.class::cast).toList();
            if (pendingFalseGuard != null && returns.size() == 1) {
                RepositoryNode terminal = graph.addNode("JAVA_RETURN", method.owner().source(), returns.get(0),
                        "v1:" + normalize(returns.get(0).toString()));
                flowEdgeIds.add(graph.addCfgEdge("CFG_FALSE", pendingFalseGuard, terminal.nodeId(),
                        RULE_CFG_FALSE, pendingFalseGuard, "FALSE"));
                flowEdgeIds.add(graph.addCfgEdge("CFG_TERMINAL", terminal.nodeId(), terminal.nodeId(),
                        RULE_CFG_TERMINAL, null, null));
            } else if (!guards.isEmpty()) {
                graph.site("CONTROL_FLOW", graph.node(method.nodeId()).locator(), List.of(entry.entryId()),
                        "UNSUPPORTED", "CFG_RETURN_UNRESOLVED");
            }
            flows.add(new ControlFlow(entry.entryId(), entryMethodNodeId, guards, terminals,
                    flowEdgeIds.stream().distinct().sorted().toList()));
            graph.site("CONTROL_FLOW", graph.node(method.nodeId()).locator(), List.of(entry.entryId()),
                    "SUPPORTED", null);
        }
        return flows.stream().sorted(Comparator.comparing(ControlFlow::entryId)).toList();
    }

    private static Document parseSecureXml(SourceText source) {
        if (EXTERNAL_ENTITY_DECLARATION.matcher(source.text()).find()) {
            throw failure(Stage01FailureCode.XML_EXTERNAL_RESOLUTION_ATTEMPT);
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);
        } catch (ParserConfigurationException | IllegalArgumentException securityUnavailable) {
            throw failure(Stage01FailureCode.XML_SECURITY_POLICY_UNENFORCEABLE);
        }
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> {
                throw new ExternalResolutionAttempt();
            });
            return builder.parse(new InputSource(new StringReader(source.text())));
        } catch (ExternalResolutionAttempt resolutionAttempt) {
            throw failure(Stage01FailureCode.XML_EXTERNAL_RESOLUTION_ATTEMPT);
        } catch (ParserConfigurationException securityUnavailable) {
            throw failure(Stage01FailureCode.XML_SECURITY_POLICY_UNENFORCEABLE);
        } catch (SAXException | IOException invalid) {
            return null;
        }
    }

    private static List<XmlStatementData> xmlStatements(SourceText source, Element mapper, Graph graph) {
        List<XmlStatementData> statements = new ArrayList<>();
        NodeList children = mapper.getChildNodes();
        int searchFrom = 0;
        for (int index = 0; index < children.getLength(); index++) {
            org.w3c.dom.Node child = children.item(index);
            if (!(child instanceof Element statement) || !MAPPER_STATEMENTS.contains(statement.getTagName())) {
                continue;
            }
            String id = statement.getAttribute("id");
            if (id.isBlank()) {
                continue;
            }
            Matcher opening = findXmlOpening(source.text(), statement.getTagName(), id, searchFrom);
            if (opening == null) {
                continue;
            }
            searchFrom = opening.end();
            RepositoryLocator locator = source.locator(opening.start(), opening.end());
            RepositoryNode statementNode = graph.addNode("XML_STATEMENT", source, locator,
                    "v1:" + statement.getTagName() + ":" + id);
            boolean dynamic = hasNestedElement(statement) || statement.getTextContent().contains("${");
            statements.add(new XmlStatementData(source, id, statement.getTagName(),
                    statement.getAttribute("resultType"), statement.getTextContent(), dynamic, locator,
                    statementNode.nodeId()));
        }
        return statements.stream().sorted(Comparator.comparing(XmlStatementData::id)
                .thenComparing(XmlStatementData::nodeId)).toList();
    }

    private static Matcher findXmlOpening(String text, String expectedTag, String expectedId,
                                          int searchFrom) {
        Matcher matcher = XML_OPENING_TAG.matcher(text);
        while (matcher.find(searchFrom)) {
            if (!expectedTag.equalsIgnoreCase(matcher.group(1))) {
                searchFrom = matcher.end();
                continue;
            }
            Map<String, String> attributes = attributes(matcher.group(2));
            if (expectedId.equals(attributes.get("id"))) {
                return matcher;
            }
            searchFrom = matcher.end();
        }
        return null;
    }

    private static RepositoryLocator xmlElementLocator(SourceText source, String tag, String namespace,
                                                        int searchFrom) {
        Pattern pattern = Pattern.compile("<" + Pattern.quote(tag) + "\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(source.text());
        while (matcher.find(searchFrom)) {
            if (namespace.equals(attributes(matcher.group(1)).get("namespace"))) {
                return source.locator(matcher.start(), matcher.end());
            }
            searchFrom = matcher.end();
        }
        return source.whole();
    }

    private static Map<String, String> attributes(String source) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher matcher = XML_ATTRIBUTE.matcher(source);
        while (matcher.find()) {
            result.put(matcher.group(1), matcher.group(3));
        }
        return result;
    }

    private static boolean hasNestedElement(Element element) {
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element) {
                return true;
            }
        }
        return false;
    }

    private static int xmlNodeCount(Document document) {
        int count = 0;
        ArrayDeque<org.w3c.dom.Node> pending = new ArrayDeque<>();
        pending.add(document);
        while (!pending.isEmpty()) {
            org.w3c.dom.Node current = pending.removeFirst();
            count++;
            NodeList children = current.getChildNodes();
            for (int index = 0; index < children.getLength(); index++) {
                pending.addLast(children.item(index));
            }
        }
        return count;
    }

    private static Optional<AnnotationExpr> annotation(ClassOrInterfaceDeclaration declaration,
                                                        String name) {
        return declaration.getAnnotations().stream()
                .filter(candidate -> candidate.getName().getIdentifier().equals(name)).findFirst();
    }

    private static Optional<AnnotationExpr> annotation(MethodDeclaration declaration, String name) {
        return declaration.getAnnotations().stream()
                .filter(candidate -> candidate.getName().getIdentifier().equals(name)).findFirst();
    }

    private static Optional<AnnotationExpr> springAnnotation(JavaTypeData type,
                                                              ClassOrInterfaceDeclaration declaration,
                                                              String qualifiedAnnotation) {
        return declaration.getAnnotations().stream()
                .filter(candidate -> resolvesImportedAnnotation(type, candidate, qualifiedAnnotation)).findFirst();
    }

    private static Optional<AnnotationExpr> springAnnotation(JavaTypeData type,
                                                              MethodDeclaration declaration,
                                                              String qualifiedAnnotation) {
        return declaration.getAnnotations().stream()
                .filter(candidate -> resolvesImportedAnnotation(type, candidate, qualifiedAnnotation)).findFirst();
    }

    private static boolean resolvesImportedAnnotation(JavaTypeData type, AnnotationExpr candidate,
                                                       String qualifiedAnnotation) {
        if (qualifiedAnnotation.equals(candidate.getNameAsString())) {
            return true;
        }
        return candidate.getName().getIdentifier().equals(simpleName(qualifiedAnnotation))
                && type.unit().getImports().stream().anyMatch(imported -> !imported.isAsterisk()
                && qualifiedAnnotation.equals(imported.getNameAsString()));
    }

    private static String simpleName(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }

    private static Optional<String> mappingPath(AnnotationExpr annotation) {
        if (annotation.isMarkerAnnotationExpr()) {
            return Optional.of("");
        }
        if (annotation.isSingleMemberAnnotationExpr()) {
            return stringLiteral(annotation.asSingleMemberAnnotationExpr().getMemberValue());
        }
        if (annotation.isNormalAnnotationExpr()) {
            for (var pair : annotation.asNormalAnnotationExpr().getPairs()) {
                if (pair.getNameAsString().equals("value") || pair.getNameAsString().equals("path")) {
                    return stringLiteral(pair.getValue());
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> stringLiteral(Expression expression) {
        return expression instanceof StringLiteralExpr literal ? Optional.of(literal.asString())
                : Optional.empty();
    }

    private static String joinPath(String prefix, String suffix) {
        String joined = ((prefix == null ? "" : prefix.trim()) + "/"
                + (suffix == null ? "" : suffix.trim())).replaceAll("/{2,}", "/");
        if (!joined.startsWith("/")) {
            joined = "/" + joined;
        }
        return joined.length() > 1 && joined.endsWith("/") ? joined.substring(0, joined.length() - 1) : joined;
    }

    private static Optional<String> fieldReceiver(MethodCallExpr call, Set<String> localNames) {
        if (call.getScope().isEmpty()) {
            return Optional.empty();
        }
        Expression scope = call.getScope().get();
        if (scope instanceof NameExpr name && !localNames.contains(name.getNameAsString())) {
            return Optional.of(name.getNameAsString());
        }
        if (scope instanceof FieldAccessExpr access && access.getScope() instanceof ThisExpr) {
            return Optional.of(access.getNameAsString());
        }
        return Optional.empty();
    }

    private static Set<String> localNames(MethodDeclaration method) {
        Set<String> names = new HashSet<>();
        method.getParameters().forEach(parameter -> names.add(parameter.getNameAsString()));
        method.findAll(VariableDeclarationExpr.class).forEach(declaration ->
                declaration.getVariables().forEach(variable -> names.add(variable.getNameAsString())));
        return names;
    }

    private static TypeResolution resolveType(com.github.javaparser.ast.type.Type fieldType,
                                              JavaTypeData owner,
                                              Map<String, JavaTypeData> typesByName) {
        if (!(fieldType instanceof ClassOrInterfaceType type)) {
            return new TypeResolution(List.of(), "UNSUPPORTED_RECEIVER_TYPE");
        }
        String declaredName = type.getNameWithScope();
        Map<String, JavaTypeData> candidates = new LinkedHashMap<>();
        boolean wildcardImport = false;
        if (declaredName.contains(".")) {
            addCandidate(candidates, typesByName.get(declaredName));
        } else {
            addCandidate(candidates, typesByName.get(qualify(owner.packageName(), declaredName)));
            for (ImportDeclaration imported : owner.unit().getImports()) {
                if (!imported.isAsterisk() && imported.getName().getIdentifier().equals(declaredName)) {
                    addCandidate(candidates, typesByName.get(imported.getNameAsString()));
                }
                if (imported.isAsterisk()) {
                    wildcardImport = true;
                }
            }
        }
        List<JavaTypeData> resolved = candidates.values().stream()
                .sorted(Comparator.comparing(JavaTypeData::qualifiedName)).toList();
        return new TypeResolution(resolved, resolved.isEmpty() && wildcardImport
                ? "WILDCARD_IMPORT_UNRESOLVED" : null);
    }

    private static void addCandidate(Map<String, JavaTypeData> candidates, JavaTypeData candidate) {
        if (candidate != null) {
            candidates.put(candidate.qualifiedName(), candidate);
        }
    }

    private static String qualify(String packageName, String simpleName) {
        return packageName.isBlank() ? simpleName : packageName + "." + simpleName;
    }

    private static <T extends Node> List<T> ordered(Collection<T> nodes, SourceText source) {
        return nodes.stream().sorted(Comparator.comparingInt((T node) ->
                source.locator(node).startByte()).thenComparing(node -> node.toString())).toList();
    }

    private static void validateGraph(List<RepositoryEntry> entries, List<RepositoryNode> nodes,
                                      List<RepositoryEdge> edges, List<ControlFlow> flows,
                                      List<CapabilitySite> sites) {
        Set<String> nodeIds = nodes.stream().map(RepositoryNode::nodeId).collect(
                java.util.stream.Collectors.toSet());
        if (nodeIds.size() != nodes.size() || entries.stream().map(RepositoryEntry::entryId).distinct().count()
                != entries.size()) {
            throw failure(Stage01FailureCode.M2_GRAPH_INVARIANT_BROKEN);
        }
        for (RepositoryEdge edge : edges) {
            if (!nodeIds.contains(edge.fromNodeId()) || !nodeIds.contains(edge.toNodeId())) {
                throw failure(Stage01FailureCode.M2_GRAPH_INVARIANT_BROKEN);
            }
        }
        for (ControlFlow flow : flows) {
            if (!nodeIds.contains(flow.methodNodeId()) || flow.guardNodeIds().stream()
                    .anyMatch(nodeId -> !nodeIds.contains(nodeId)) || flow.terminalNodeIds().stream()
                    .anyMatch(nodeId -> !nodeIds.contains(nodeId)) || flow.flowEdgeIds().stream()
                    .anyMatch(edgeId -> edges.stream().noneMatch(edge -> edge.edgeId().equals(edgeId)))) {
                throw failure(Stage01FailureCode.M2_GRAPH_INVARIANT_BROKEN);
            }
        }
        CapabilityCoverage coverage = coverage(sites);
        if (coverage.reachableSemanticSites() != coverage.supportedSemanticSites()
                + coverage.unsupportedReachableSites() + coverage.ambiguousReachableSites()
                + coverage.overLimitReachableSites()) {
            throw failure(Stage01FailureCode.M2_GRAPH_INVARIANT_BROKEN);
        }
    }

    private static CapabilityCoverage coverage(List<CapabilitySite> sites) {
        int supported = 0;
        int unsupported = 0;
        int ambiguous = 0;
        int overLimit = 0;
        for (CapabilitySite site : sites) {
            if (("SUPPORTED".equals(site.disposition())) != (site.reasonCode() == null)) {
                throw failure(Stage01FailureCode.M2_GRAPH_INVARIANT_BROKEN);
            }
            switch (site.disposition()) {
                case "SUPPORTED" -> supported++;
                case "UNSUPPORTED" -> unsupported++;
                case "AMBIGUOUS" -> ambiguous++;
                case "OVER_LIMIT" -> overLimit++;
                default -> throw failure(Stage01FailureCode.M2_GRAPH_INVARIANT_BROKEN);
            }
        }
        return new CapabilityCoverage(sites.size(), supported, unsupported, ambiguous, overLimit);
    }

    private static String canonicalModel(List<RepositoryEntry> entries, List<RepositoryNode> nodes,
                                         List<RepositoryEdge> edges, List<ControlFlow> flows) {
        return canonical(entries) + "\n" + canonical(nodes) + "\n" + canonical(edges) + "\n" + canonical(flows);
    }

    private static String canonicalReport(CapabilityProfileRef profile, List<CapabilitySite> sites,
                                          CapabilityCoverage coverage) {
        return profile.profileId() + "\n" + profile.profileSha256() + "\n" + canonical(sites)
                + "\n" + coverage.reachableSemanticSites() + ":" + coverage.supportedSemanticSites()
                + ":" + coverage.unsupportedReachableSites() + ":" + coverage.ambiguousReachableSites()
                + ":" + coverage.overLimitReachableSites();
    }

    private static String canonical(Collection<?> values) {
        return values.stream().map(Object::toString).sorted().reduce("", (left, right) -> left + "\n" + right);
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static int indexOfIgnoreCase(String value, String token) {
        return value.toLowerCase(java.util.Locale.ROOT).indexOf(token.toLowerCase(java.util.Locale.ROOT));
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private static Stage01Exception failure(Stage01FailureCode code) {
        return new Stage01Exception(code);
    }

    private record JavaTypeData(String qualifiedName, String packageName, SourceText source,
                                CompilationUnit unit, ClassOrInterfaceDeclaration declaration,
                                String typeNodeId, Map<String, FieldData> fields,
                                List<MethodData> methods) {
        private JavaTypeData(String qualifiedName, String packageName, SourceText source,
                             CompilationUnit unit, ClassOrInterfaceDeclaration declaration,
                             String typeNodeId) {
            this(qualifiedName, packageName, source, unit, declaration, typeNodeId,
                    new LinkedHashMap<>(), new ArrayList<>());
        }
    }

    private record FieldData(String nodeId, List<JavaTypeData> candidates, String reasonCode) {
    }

    private record TypeResolution(List<JavaTypeData> candidates, String reasonCode) {
    }

    private record MethodData(JavaTypeData owner, MethodDeclaration declaration, String nodeId) {
    }

    private record PendingCallSite(String callerMethodNodeId, RepositoryLocator locator,
                                   String disposition, String reasonCode) {
    }

    private record DirectCallDiscovery(Map<String, List<String>> targetsByCaller,
                                       Map<String, String> firstTargets,
                                       List<PendingCallSite> sites) {
    }

    private record RouteCandidate(String methodNodeId, String route, List<String> routeNodeIds) {
    }

    private record XmlMapperData(SourceText source, String namespace, String namespaceNodeId,
                                 List<XmlStatementData> statements) {
    }

    private record XmlStatementData(SourceText source, String id, String elementName, String resultType,
                                    String sql, boolean dynamic, RepositoryLocator locator, String nodeId) {
    }

    private static final class ExternalResolutionAttempt extends SAXException {
        private ExternalResolutionAttempt() {
            super("external XML resolution attempted");
        }
    }

    private static final class SourceText {
        private final String path;
        private final String mediaType;
        private final byte[] bytes;
        private final String text;
        private final int[] lineStarts;

        private SourceText(String path, String mediaType, byte[] bytes) {
            this.path = path;
            this.mediaType = mediaType;
            this.bytes = bytes.clone();
            this.text = new String(bytes, StandardCharsets.UTF_8);
            this.lineStarts = lineStarts(text);
        }

        private String path() {
            return path;
        }

        private String mediaType() {
            return mediaType;
        }

        private String text() {
            return text;
        }

        private RepositoryLocator whole() {
            return locator(0, text.length());
        }

        private RepositoryLocator locator(Node node) {
            return node.getRange().map(range -> locator(range.begin.line, range.begin.column,
                    range.end.line, range.end.column)).orElseGet(this::whole);
        }

        private RepositoryLocator locator(int startLine, int startColumn, int endLine, int endColumn) {
            int start = charIndex(startLine, startColumn);
            int end = Math.min(text.length(), charIndex(endLine, endColumn) + 1);
            return locator(start, end);
        }

        private RepositoryLocator locator(int startChar, int endChar) {
            int safeStart = Math.max(0, Math.min(startChar, text.length()));
            int safeEnd = Math.max(safeStart, Math.min(endChar, text.length()));
            int startByte = text.substring(0, safeStart).getBytes(StandardCharsets.UTF_8).length;
            int endByte = text.substring(0, safeEnd).getBytes(StandardCharsets.UTF_8).length;
            int startLine = lineFor(safeStart);
            int endLine = lineFor(Math.max(safeStart, safeEnd == 0 ? 0 : safeEnd - 1));
            int startColumn = safeStart - lineStarts[startLine - 1] + 1;
            int endColumn = Math.max(1, safeEnd - lineStarts[endLine - 1] + 1);
            return new RepositoryLocator(path, startByte, endByte, startLine, startColumn, endLine, endColumn);
        }

        private String spanSha256(RepositoryLocator locator) {
            int start = locator.startByte();
            int end = locator.endByteExclusive();
            if (start < 0 || end < start || end > bytes.length) {
                throw failure(Stage01FailureCode.M2_GRAPH_INVARIANT_BROKEN);
            }
            return sha256(java.util.Arrays.copyOfRange(bytes, start, end));
        }

        private int charIndex(int line, int column) {
            if (line < 1 || line > lineStarts.length || column < 1) {
                return text.length();
            }
            return Math.min(text.length(), lineStarts[line - 1] + column - 1);
        }

        private int lineFor(int charIndex) {
            int line = java.util.Arrays.binarySearch(lineStarts, charIndex);
            if (line >= 0) {
                return line + 1;
            }
            return -line - 1;
        }

        private static int[] lineStarts(String text) {
            List<Integer> starts = new ArrayList<>();
            starts.add(0);
            for (int index = 0; index < text.length(); index++) {
                if (text.charAt(index) == '\n' && index + 1 < text.length()) {
                    starts.add(index + 1);
                }
            }
            return starts.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    private static final class Graph {
        private final String snapshotId;
        private final Map<String, RepositoryNode> nodes = new LinkedHashMap<>();
        private final Map<String, RepositoryEdge> edges = new LinkedHashMap<>();
        private final Map<String, CapabilitySite> sites = new LinkedHashMap<>();

        private Graph(String snapshotId) {
            this.snapshotId = snapshotId;
        }

        private RepositoryNode addNode(String kind, SourceText source, Node node, String canonicalValue) {
            return addNode(kind, source, source.locator(node), canonicalValue);
        }

        private RepositoryNode addNode(String kind, SourceText source, RepositoryLocator locator,
                                       String canonicalValue) {
            String span = source.spanSha256(locator);
            String material = snapshotId + "\n" + kind + "\n" + locator.path() + "\n"
                    + locator.startByte() + "\n" + locator.endByteExclusive() + "\n" + span + "\n"
                    + canonicalValue;
            String id = "node:" + sha256(material);
            return nodes.computeIfAbsent(id, ignored -> new RepositoryNode(id, kind, locator, span,
                    canonicalValue));
        }

        private String addEdge(String kind, String fromNodeId, String toNodeId, String ruleId,
                               String resolution) {
            return addEdge(kind, fromNodeId, toNodeId, ruleId, resolution, null, null);
        }

        private String addCfgEdge(String kind, String fromNodeId, String toNodeId, String ruleId,
                                  String guardNodeId, String polarity) {
            return addEdge(kind, fromNodeId, toNodeId, ruleId, "EXACT", guardNodeId, polarity);
        }

        private String addEdge(String kind, String fromNodeId, String toNodeId, String ruleId,
                               String resolution, String guardNodeId, String polarity) {
            String id = "edge:" + sha256(snapshotId + "\n" + kind + "\n" + fromNodeId + "\n"
                    + toNodeId + "\n" + ruleId + "\n" + resolution + "\n"
                    + (guardNodeId == null ? "" : guardNodeId) + "\n"
                    + (polarity == null ? "" : polarity));
            edges.putIfAbsent(id, new RepositoryEdge(id, kind, fromNodeId, toNodeId, ruleId, resolution,
                    guardNodeId, polarity));
            return id;
        }

        private void site(String kind, RepositoryLocator locator, List<String> entryIds,
                          String disposition, String reasonCode) {
            List<String> orderedEntries = entryIds.stream().distinct().sorted().toList();
            String id = "site:" + sha256(snapshotId + "\n" + kind + "\n" + locator.path() + "\n"
                    + locator.startByte() + "\n" + locator.endByteExclusive() + "\n"
                    + String.join(",", orderedEntries) + "\n" + disposition + "\n"
                    + (reasonCode == null ? "" : reasonCode));
            sites.putIfAbsent(id, new CapabilitySite(id, kind, locator, orderedEntries, disposition, reasonCode));
        }

        private RepositoryNode node(String nodeId) {
            RepositoryNode node = nodes.get(nodeId);
            if (node == null) {
                throw failure(Stage01FailureCode.M2_GRAPH_INVARIANT_BROKEN);
            }
            return node;
        }

        private List<RepositoryNode> nodes() {
            return nodes.values().stream().sorted(Comparator.comparing(RepositoryNode::nodeId)).toList();
        }

        private List<RepositoryEdge> edges() {
            return edges.values().stream().sorted(Comparator.comparing(RepositoryEdge::edgeId)).toList();
        }

        private List<CapabilitySite> sites() {
            return sites.values().stream().sorted(Comparator.comparing(CapabilitySite::siteId)).toList();
        }
    }
}
