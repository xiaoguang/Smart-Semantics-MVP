package com.linguan.codemd.discovery;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Offline Phase 1 Java/Spring MVC/MyBatis discovery. This intentionally uses
 * parsed source text only; there is no symbol solver, source execution, or
 * MyBatis runtime.
 */
public final class RepositoryDiscoverer {
    private static final Set<String> MAPPER_STATEMENTS = Set.of(
            "select", "insert", "update", "delete");
    private static final Pattern WHERE_CLAUSE = Pattern.compile("(?i)\\bwhere\\b");
    private static final Pattern STATIC_UPDATE = Pattern.compile(
            "(?is)^\\s*update\\s+([a-z_][a-z0-9_.$]*)\\s+set\\s+"
                    + "([a-z_][a-z0-9_.$]*)\\s*=\\s*"
                    + "('(?:''|[^'])*'|-?\\d+(?:\\.\\d+)?|null|true|false)"
                    + "(?:\\s+where\\b.*)?\\s*;?\\s*$");

    /** Discovers the supported facts from the request's already-local source tree. */
    public DiscoveryResult discover(DiscoveryRequest request) {
        Path root = verifiedRoot(request.repositoryRoot());
        JavaSourceDiscovery javaSources = parseJavaSources(root);
        XmlStatementDiscovery xmlStatements = parseXmlStatements(root);
        List<JavaType> types = declaredTypes(javaSources.sources());

        List<HttpRoute> routes = findHttpRoutes(types);
        List<DirectCallEdge> directEdges = findDirectCallEdges(types);
        BindingDiscovery bindings = findMapperBindings(types, xmlStatements.statements());
        SqlDiscovery sqlFacts = findStaticUpdateFacts(bindings.bindings(), xmlStatements.statements());

        Set<String> gapCodes = new LinkedHashSet<>(javaSources.gapCodes());
        gapCodes.addAll(xmlStatements.gapCodes());
        gapCodes.addAll(bindings.gapCodes());
        gapCodes.addAll(sqlFacts.gapCodes());
        return new DiscoveryResult(routes, directEdges, bindings.bindings(), sqlFacts.facts(),
                gapCodes.stream().map(DiscoveryGap::new).toList());
    }

    private static Path verifiedRoot(Path root) {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("repositoryRoot must be a non-link directory");
        }
        return root;
    }

    private static JavaSourceDiscovery parseJavaSources(Path root) {
        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));
        List<JavaSource> result = new ArrayList<>();
        Set<String> gapCodes = new LinkedHashSet<>();
        for (Path path : sourceFilesOrGap(root, ".java", gapCodes)) {
            try {
                ParseResult<CompilationUnit> parsed = parser.parse(Files.readString(path,
                        StandardCharsets.UTF_8));
                if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
                    gapCodes.add("JAVA_PARSE_UNRESOLVED");
                    continue;
                }
                result.add(new JavaSource(relativePath(root, path), parsed.getResult().get()));
            } catch (IOException unreadable) {
                gapCodes.add("JAVA_READ_UNRESOLVED");
            } catch (RuntimeException invalid) {
                gapCodes.add("JAVA_PARSE_UNRESOLVED");
            }
        }
        return new JavaSourceDiscovery(List.copyOf(result), gapCodes);
    }

    private static List<JavaType> declaredTypes(List<JavaSource> sources) {
        List<JavaType> types = new ArrayList<>();
        for (JavaSource source : sources) {
            String packageName = source.unit().getPackageDeclaration()
                    .map(declaration -> declaration.getNameAsString()).orElse("");
            for (var declaration : source.unit().getTypes()) {
                if (declaration instanceof ClassOrInterfaceDeclaration classOrInterface) {
                    String simpleName = classOrInterface.getNameAsString();
                    String qualifiedName = packageName.isBlank()
                            ? simpleName : packageName + "." + simpleName;
                    types.add(new JavaType(qualifiedName, simpleName, packageName,
                            source, classOrInterface));
                }
            }
        }
        return types.stream().sorted(Comparator.comparing(JavaType::qualifiedName)
                .thenComparing(type -> type.source().relativePath())).toList();
    }

    private static List<HttpRoute> findHttpRoutes(List<JavaType> types) {
        List<HttpRoute> routes = new ArrayList<>();
        for (JavaType type : types) {
            Optional<AnnotationExpr> classMapping = annotation(type.declaration(), "RequestMapping");
            if (classMapping.isEmpty()) {
                continue;
            }
            List<String> prefixes = mappingPaths(classMapping.get());
            if (prefixes.isEmpty()) {
                prefixes = List.of("");
            }
            for (MethodDeclaration method : type.declaration().getMethods()) {
                Optional<AnnotationExpr> postMapping = annotation(method, "PostMapping");
                if (postMapping.isEmpty()) {
                    continue;
                }
                Optional<SourceLocator> locator = methodLocator(type.source().relativePath(), method);
                if (locator.isEmpty()) {
                    continue;
                }
                List<String> suffixes = mappingPaths(postMapping.get());
                if (suffixes.isEmpty()) {
                    suffixes = List.of("");
                }
                for (String prefix : prefixes) {
                    for (String suffix : suffixes) {
                        routes.add(new HttpRoute("POST", joinPath(prefix, suffix), locator.get()));
                    }
                }
            }
        }
        return routes;
    }

    private static Optional<SourceLocator> methodLocator(String relativePath,
                                                          MethodDeclaration method) {
        return method.getRange().map(range -> new SourceLocator(relativePath,
                range.begin.line, range.end.line));
    }

    private static List<String> mappingPaths(AnnotationExpr annotation) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            return stringValues(annotation.asSingleMemberAnnotationExpr().getMemberValue());
        }
        if (annotation.isNormalAnnotationExpr()) {
            NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
            List<String> paths = new ArrayList<>();
            normal.getPairs().forEach(pair -> {
                if (pair.getNameAsString().equals("value") || pair.getNameAsString().equals("path")) {
                    paths.addAll(stringValues(pair.getValue()));
                }
            });
            return paths.stream().distinct().sorted().toList();
        }
        return List.of();
    }

    private static List<String> stringValues(Expression expression) {
        if (expression instanceof StringLiteralExpr literal) {
            return List.of(literal.asString());
        }
        if (expression instanceof ArrayInitializerExpr array) {
            return array.getValues().stream().flatMap(value -> stringValues(value).stream())
                    .distinct().sorted().toList();
        }
        return List.of();
    }

    private static String joinPath(String prefix, String suffix) {
        String left = prefix == null ? "" : prefix.trim();
        String right = suffix == null ? "" : suffix.trim();
        String joined = (left + "/" + right).replaceAll("/{2,}", "/");
        if (!joined.startsWith("/")) {
            joined = "/" + joined;
        }
        return joined.length() > 1 && joined.endsWith("/")
                ? joined.substring(0, joined.length() - 1) : joined;
    }

    private static List<DirectCallEdge> findDirectCallEdges(List<JavaType> types) {
        List<DirectCallEdge> edges = new ArrayList<>();
        for (JavaType callerType : types) {
            Map<String, List<JavaType>> fields = fieldTypes(callerType, types);
            if (fields.isEmpty()) {
                continue;
            }
            for (MethodDeclaration method : callerType.declaration().getMethods()) {
                Set<String> localNames = localNames(method);
                for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                    Optional<Receiver> receiver = fieldReceiver(call);
                    if (receiver.isEmpty()
                            || (!receiver.get().explicitThis() && localNames.contains(receiver.get().name()))) {
                        continue;
                    }
                    List<JavaType> targets = fields.get(receiver.get().name());
                    if (targets == null || targets.size() != 1) {
                        continue;
                    }
                    JavaType target = targets.get(0);
                    if (methodCount(target, call.getNameAsString()) != 1) {
                        continue;
                    }
                    edges.add(new DirectCallEdge(methodId(callerType, method),
                            target.qualifiedName() + "#" + call.getNameAsString()));
                }
            }
        }
        return edges;
    }

    private static Map<String, List<JavaType>> fieldTypes(JavaType owner,
                                                           List<JavaType> allTypes) {
        Map<String, List<JavaType>> fields = new LinkedHashMap<>();
        owner.declaration().getFields().forEach(field -> {
            List<JavaType> targets = resolveType(field.getElementType(), owner, allTypes);
            field.getVariables().forEach(variable -> fields.put(variable.getNameAsString(), targets));
        });
        return fields;
    }

    private static List<JavaType> resolveType(Type fieldType, JavaType owner,
                                              List<JavaType> allTypes) {
        if (!(fieldType instanceof ClassOrInterfaceType classType)) {
            return List.of();
        }
        String declaredName = classType.getNameWithScope();
        List<JavaType> candidates = new ArrayList<>();
        if (declaredName.contains(".")) {
            candidates.addAll(typesNamed(declaredName, allTypes));
        } else {
            candidates.addAll(typesNamed(qualify(owner.packageName(), declaredName), allTypes));
            for (ImportDeclaration imported : owner.source().unit().getImports()) {
                if (!imported.isAsterisk() && imported.getName().getIdentifier().equals(declaredName)) {
                    candidates.addAll(typesNamed(imported.getNameAsString(), allTypes));
                }
                if (imported.isAsterisk()) {
                    candidates.addAll(typesNamed(imported.getNameAsString() + "." + declaredName,
                            allTypes));
                }
            }
            if (candidates.isEmpty()) {
                for (JavaType type : allTypes) {
                    if (type.simpleName().equals(declaredName)) {
                        candidates.add(type);
                    }
                }
            }
        }
        return uniqueTypes(candidates);
    }

    private static List<JavaType> typesNamed(String qualifiedName, List<JavaType> types) {
        return types.stream().filter(type -> type.qualifiedName().equals(qualifiedName)).toList();
    }

    private static List<JavaType> uniqueTypes(Collection<JavaType> candidates) {
        Map<String, JavaType> unique = new LinkedHashMap<>();
        for (JavaType candidate : candidates) {
            unique.put(candidate.qualifiedName() + "\u0000" + candidate.source().relativePath(), candidate);
        }
        return List.copyOf(unique.values());
    }

    private static Set<String> localNames(MethodDeclaration method) {
        Set<String> names = new HashSet<>();
        method.getParameters().forEach(parameter -> names.add(parameter.getNameAsString()));
        method.findAll(VariableDeclarationExpr.class).forEach(declaration ->
                declaration.getVariables().forEach(variable -> names.add(variable.getNameAsString())));
        return names;
    }

    private static Optional<Receiver> fieldReceiver(MethodCallExpr call) {
        if (call.getScope().isEmpty()) {
            return Optional.empty();
        }
        Expression scope = call.getScope().get();
        if (scope instanceof NameExpr name) {
            return Optional.of(new Receiver(name.getNameAsString(), false));
        }
        if (scope instanceof FieldAccessExpr access && access.getScope() instanceof ThisExpr) {
            return Optional.of(new Receiver(access.getNameAsString(), true));
        }
        return Optional.empty();
    }

    private static int methodCount(JavaType type, String methodName) {
        return (int) type.declaration().getMethods().stream()
                .filter(method -> method.getNameAsString().equals(methodName)).count();
    }

    private static String methodId(JavaType type, MethodDeclaration method) {
        return type.qualifiedName() + "#" + method.getNameAsString();
    }

    private static BindingDiscovery findMapperBindings(List<JavaType> types,
                                                        List<XmlStatement> statements) {
        List<MapperBinding> bindings = new ArrayList<>();
        Set<String> gaps = new LinkedHashSet<>();
        for (JavaType mapper : types) {
            if (!mapper.declaration().isInterface()) {
                continue;
            }
            Map<String, Long> methodNames = mapper.declaration().getMethods().stream()
                    .collect(java.util.stream.Collectors.groupingBy(MethodDeclaration::getNameAsString,
                            java.util.stream.Collectors.counting()));
            for (String methodName : methodNames.keySet().stream().sorted().toList()) {
                if (methodNames.get(methodName) != 1) {
                    continue;
                }
                List<XmlStatement> matches = statements.stream()
                        .filter(statement -> statement.namespace().equals(mapper.qualifiedName()))
                        .filter(statement -> statement.id().equals(methodName))
                        .sorted(Comparator.comparing(XmlStatement::relativePath)
                                .thenComparing(XmlStatement::id))
                        .toList();
                if (matches.size() == 1) {
                    XmlStatement match = matches.get(0);
                    bindings.add(new MapperBinding(mapper.qualifiedName(), methodName,
                            match.relativePath(), match.id()));
                } else if (matches.size() > 1) {
                    gaps.add("AMBIGUOUS_MAPPER_BINDING");
                }
            }
        }
        return new BindingDiscovery(bindings, gaps);
    }

    private static SqlDiscovery findStaticUpdateFacts(List<MapperBinding> bindings,
                                                       List<XmlStatement> statements) {
        List<SqlUpdateFact> facts = new ArrayList<>();
        Set<String> gaps = new LinkedHashSet<>();
        for (MapperBinding binding : bindings) {
            XmlStatement statement = bindingStatement(binding, statements);
            if (!statement.elementName().equals("update")) {
                continue;
            }
            Optional<SqlUpdateFact> staticFact = staticUpdateFact(binding, statement);
            if (staticFact.isPresent()) {
                facts.add(staticFact.get());
            } else if (dynamicOrUnsafe(statement)) {
                gaps.add("DYNAMIC_SQL_UNRESOLVED");
            }
        }
        return new SqlDiscovery(facts, gaps);
    }

    private static XmlStatement bindingStatement(MapperBinding binding,
                                                 List<XmlStatement> statements) {
        return statements.stream()
                .filter(statement -> statement.relativePath().equals(binding.xmlPath()))
                .filter(statement -> statement.id().equals(binding.statementId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "missing XML statement for mapper binding"));
    }

    private static Optional<SqlUpdateFact> staticUpdateFact(MapperBinding binding,
                                                            XmlStatement statement) {
        Matcher matcher = STATIC_UPDATE.matcher(statement.sqlText());
        if (!matcher.matches() || dynamicOrUnsafe(statement)) {
            return Optional.empty();
        }
        String literal = matcher.group(3);
        String value = literal.startsWith("'")
                ? literal.substring(1, literal.length() - 1).replace("''", "'") : literal;
        return Optional.of(new SqlUpdateFact(binding.methodName(), matcher.group(1), matcher.group(2), value));
    }

    private static boolean dynamicOrUnsafe(XmlStatement statement) {
        String text = statement.sqlText();
        if (statement.hasNestedElement() || text.contains("${")) {
            return true;
        }
        Matcher where = WHERE_CLAUSE.matcher(text);
        String assignment = where.find() ? text.substring(0, where.start()) : text;
        return assignment.contains("#{");
    }

    private static XmlStatementDiscovery parseXmlStatements(Path root) {
        List<XmlStatement> statements = new ArrayList<>();
        Set<String> gapCodes = new LinkedHashSet<>();
        for (Path path : sourceFilesOrGap(root, ".xml", gapCodes)) {
            Document document;
            try {
                document = parseXml(path);
            } catch (RuntimeException invalid) {
                gapCodes.add("XML_PARSE_UNRESOLVED");
                continue;
            }
            Element mapper = document.getDocumentElement();
            if (mapper == null || !mapper.getTagName().equals("mapper")) {
                continue;
            }
            String namespace = mapper.getAttribute("namespace");
            if (namespace.isBlank()) {
                continue;
            }
            NodeList children = mapper.getChildNodes();
            for (int indexValue = 0; indexValue < children.getLength(); indexValue++) {
                org.w3c.dom.Node child = children.item(indexValue);
                if (!(child instanceof Element statementElement)
                        || !MAPPER_STATEMENTS.contains(statementElement.getTagName())) {
                    continue;
                }
                String id = statementElement.getAttribute("id");
                if (id.isBlank()) {
                    continue;
                }
                XmlStatement statement = new XmlStatement(relativePath(root, path), namespace, id,
                        statementElement.getTagName(), statementElement.getTextContent(),
                        hasNestedElement(statementElement));
                statements.add(statement);
            }
        }
        List<XmlStatement> ordered = statements.stream().sorted(Comparator.comparing(XmlStatement::namespace)
                .thenComparing(XmlStatement::id)
                .thenComparing(XmlStatement::relativePath)).toList();
        return new XmlStatementDiscovery(ordered, gapCodes);
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

    private static Document parseXml(Path path) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            return builder.parse(path.toFile());
        } catch (ParserConfigurationException | SAXException | IOException invalid) {
            throw new IllegalArgumentException("invalid mapper XML: " + path, invalid);
        }
    }

    private static List<Path> sourceFiles(Path root, String extension) {
        try (Stream<Path> entries = Files.walk(root)) {
            return entries.filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(extension))
                    .sorted(Comparator.comparing(path -> relativePath(root, path))).toList();
        } catch (IOException unreadable) {
            throw new IllegalArgumentException("cannot read repository source tree", unreadable);
        }
    }

    private static List<Path> sourceFilesOrGap(Path root, String extension, Set<String> gapCodes) {
        try {
            return sourceFiles(root, extension);
        } catch (IllegalArgumentException unreadable) {
            gapCodes.add("SOURCE_TREE_READ_UNRESOLVED");
            return List.of();
        }
    }

    private static String relativePath(Path root, Path file) {
        return root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
    }

    private static Optional<AnnotationExpr> annotation(NodeWithAnnotations<?> node,
                                                        String simpleName) {
        return node.getAnnotations().stream()
                .filter(candidate -> candidate.getName().getIdentifier().equals(simpleName))
                .findFirst();
    }

    private static String qualify(String packageName, String simpleName) {
        return packageName.isBlank() ? simpleName : packageName + "." + simpleName;
    }

    private record JavaSource(String relativePath, CompilationUnit unit) {
    }

    private record JavaSourceDiscovery(List<JavaSource> sources, Set<String> gapCodes) {
    }

    private record JavaType(String qualifiedName, String simpleName, String packageName,
                            JavaSource source, ClassOrInterfaceDeclaration declaration) {
    }

    private record Receiver(String name, boolean explicitThis) {
    }

    private record XmlStatement(String relativePath, String namespace, String id,
                                String elementName, String sqlText, boolean hasNestedElement) {
    }

    private record XmlStatementDiscovery(List<XmlStatement> statements, Set<String> gapCodes) {
    }

    private record BindingDiscovery(List<MapperBinding> bindings, Set<String> gapCodes) {
    }

    private record SqlDiscovery(List<SqlUpdateFact> facts, Set<String> gapCodes) {
    }

}
