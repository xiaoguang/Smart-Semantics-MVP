package com.linguan.codemd.analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.linguan.codemd.discovery.SourceLocator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Offline Phase 2 Java source analyzer. It uses declarations and Java syntax
 * only: no classpath, symbol solver, source execution, network, or model.
 */
public final class SourceAnalyzer {
    /** Analyzes a verified local source tree into facts and explicit gaps. */
    public AnalysisResult analyze(AnalysisRequest request) {
        Path root = verifiedRoot(request.repositoryRoot());
        ParsedSources parsed = parseSources(root);
        List<JavaType> types = declaredTypes(parsed.sources());

        List<CodeFact> codeFacts = new ArrayList<>();
        List<Gap> gaps = new ArrayList<>(parsed.gaps());
        for (JavaType callerType : types) {
            findDirectCalls(callerType, types, codeFacts, gaps);
        }
        return new AnalysisResult(codeFacts, findConditions(types), gaps);
    }

    private static Path verifiedRoot(Path root) {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("repositoryRoot must be a non-link directory");
        }
        return root;
    }

    private static ParsedSources parseSources(Path root) {
        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));
        List<JavaSource> sources = new ArrayList<>();
        List<Gap> gaps = new ArrayList<>();
        for (Path path : javaSources(root, gaps)) {
            String relativePath = relativePath(root, path);
            try {
                ParseResult<CompilationUnit> parsed = parser.parse(Files.readString(path,
                        StandardCharsets.UTF_8));
                if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
                    gaps.add(new Gap("JAVA_PARSE_UNRESOLVED", relativePath,
                            new SourceLocator(relativePath, 1, 1)));
                    continue;
                }
                sources.add(new JavaSource(relativePath, parsed.getResult().get()));
            } catch (IOException unreadable) {
                gaps.add(new Gap("JAVA_READ_UNRESOLVED", relativePath,
                        new SourceLocator(relativePath, 1, 1)));
            } catch (RuntimeException invalid) {
                gaps.add(new Gap("JAVA_PARSE_UNRESOLVED", relativePath,
                        new SourceLocator(relativePath, 1, 1)));
            }
        }
        return new ParsedSources(List.copyOf(sources), List.copyOf(gaps));
    }

    private static List<Path> javaSources(Path root, List<Gap> gaps) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted(Comparator.comparing(path -> relativePath(root, path))).toList();
        } catch (IOException unreadable) {
            gaps.add(new Gap("SOURCE_TREE_READ_UNRESOLVED", root.toString(),
                    new SourceLocator(".", 1, 1)));
            return List.of();
        }
    }

    private static List<JavaType> declaredTypes(List<JavaSource> sources) {
        List<JavaType> types = new ArrayList<>();
        for (JavaSource source : sources) {
            String packageName = source.unit().getPackageDeclaration()
                    .map(declaration -> declaration.getNameAsString()).orElse("");
            for (var declaration : source.unit().getTypes()) {
                if (declaration instanceof ClassOrInterfaceDeclaration type) {
                    String simpleName = type.getNameAsString();
                    String qualifiedName = qualify(packageName, simpleName);
                    types.add(new JavaType(qualifiedName, simpleName, packageName, source, type));
                }
            }
        }
        return types.stream().sorted(Comparator.comparing(JavaType::qualifiedName)
                .thenComparing(type -> type.source().relativePath())).toList();
    }

    private static void findDirectCalls(JavaType callerType,
                                        List<JavaType> allTypes,
                                        List<CodeFact> codeFacts,
                                        List<Gap> gaps) {
        Map<String, List<JavaType>> fields = fieldTypes(callerType, allTypes);
        for (MethodDeclaration method : callerType.declaration().getMethods()) {
            Set<String> localNames = localNames(method);
            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                Optional<Receiver> receiver = fieldReceiver(call);
                if (receiver.isEmpty()
                        || (!receiver.get().explicitThis() && localNames.contains(receiver.get().name()))) {
                    continue;
                }
                List<JavaType> targets = fields.get(receiver.get().name());
                Optional<SourceLocator> locator = locator(callerType.source().relativePath(), call);
                if (targets == null || targets.size() != 1 || locator.isEmpty()) {
                    unresolvedCall(callerType, method, receiver.get().name(), call, locator, gaps);
                    continue;
                }
                JavaType target = targets.get(0);
                if (matchingMethodCount(target, call) != 1) {
                    unresolvedCall(callerType, method, receiver.get().name(), call, locator, gaps);
                    continue;
                }
                String subject = methodId(callerType, method);
                String object = target.qualifiedName() + "#" + call.getNameAsString();
                SourceLocator callLocator = locator.get();
                codeFacts.add(new CodeFact(factId("CALL", subject, object, callLocator), "CALL",
                        subject, object, "EXACT", callLocator));
            }
        }
    }

    private static void unresolvedCall(JavaType callerType,
                                       MethodDeclaration method,
                                       String receiverName,
                                       MethodCallExpr call,
                                       Optional<SourceLocator> locator,
                                       List<Gap> gaps) {
        locator.ifPresent(value -> gaps.add(new Gap("UNRESOLVED",
                methodId(callerType, method) + ":" + receiverName + "."
                        + call.getNameAsString(), value)));
    }

    private static List<ConditionFact> findConditions(List<JavaType> types) {
        List<ConditionFact> conditions = new ArrayList<>();
        for (JavaType type : types) {
            for (IfStmt ifStatement : type.declaration().findAll(IfStmt.class)) {
                Optional<SourceLocator> locator = locator(type.source().relativePath(),
                        ifStatement.getCondition());
                if (locator.isEmpty()) {
                    continue;
                }
                String expression = ifStatement.getCondition().toString();
                SourceLocator conditionLocator = locator.get();
                conditions.add(new ConditionFact(factId("CONDITION", expression, conditionLocator),
                        expression, conditionLocator));
            }
        }
        return conditions;
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

    private static List<JavaType> resolveType(Type fieldType,
                                              JavaType owner,
                                              List<JavaType> allTypes) {
        if (!(fieldType instanceof ClassOrInterfaceType classType)) {
            return List.of();
        }
        String declaredName = classType.getNameWithScope();
        if (declaredName.contains(".")) {
            return typesNamed(declaredName, allTypes);
        }
        List<JavaType> candidates = new ArrayList<>();
        candidates.addAll(typesNamed(qualify(owner.packageName(), declaredName), allTypes));
        for (ImportDeclaration imported : owner.source().unit().getImports()) {
            if (imported.isStatic()) {
                continue;
            }
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
        return unique.values().stream().sorted(Comparator.comparing(JavaType::qualifiedName)
                .thenComparing(type -> type.source().relativePath())).toList();
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

    private static int matchingMethodCount(JavaType target, MethodCallExpr call) {
        return (int) target.declaration().getMethods().stream()
                .filter(method -> method.getNameAsString().equals(call.getNameAsString()))
                .filter(method -> method.getParameters().size() == call.getArguments().size())
                .count();
    }

    private static Optional<SourceLocator> locator(String relativePath,
                                                   com.github.javaparser.ast.Node node) {
        return node.getRange().map(range -> new SourceLocator(relativePath,
                range.begin.line, range.end.line));
    }

    private static String methodId(JavaType type, MethodDeclaration method) {
        return type.qualifiedName() + "#" + method.getNameAsString();
    }

    private static String qualify(String packageName, String simpleName) {
        return packageName.isBlank() ? simpleName : packageName + "." + simpleName;
    }

    private static String relativePath(Path root, Path file) {
        return root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
    }

    private static String factId(String kind, String subject, String object, SourceLocator locator) {
        return factId(kind + "\n" + subject + "\n" + object + "\n"
                + locator.relativePath() + "\n" + locator.startLine() + "\n" + locator.endLine());
    }

    private static String factId(String kind, String expression, SourceLocator locator) {
        return factId(kind + "\n" + expression + "\n" + locator.relativePath() + "\n"
                + locator.startLine() + "\n" + locator.endLine());
    }

    private static String factId(String payload) {
        try {
            return "fact:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private record ParsedSources(List<JavaSource> sources, List<Gap> gaps) {
    }

    private record JavaSource(String relativePath, CompilationUnit unit) {
    }

    private record JavaType(String qualifiedName,
                            String simpleName,
                            String packageName,
                            JavaSource source,
                            ClassOrInterfaceDeclaration declaration) {
    }

    private record Receiver(String name, boolean explicitThis) {
    }
}
