package org.sourceanalysis.app.analysis.discovery;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** Discovers only statically resolvable class-plus-method Spring MVC HTTP routes. */
public final class SpringHttpEntryDiscoverer {

  private static final CanonicalJsonCodec CANONICAL_JSON = new CanonicalJsonCodec();
  private static final String SPRING_WEB_ANNOTATION_PACKAGE =
      "org.springframework.web.bind.annotation";

  private final VerifiedSourceTextReader sourceReader;

  SpringHttpEntryDiscoverer(VerifiedSourceTextReader sourceReader) {
    if (sourceReader == null) {
      throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_REQUEST_INVALID");
    }
    this.sourceReader = sourceReader;
  }

  /** Reads verified Java bytes and composes explicit class and method Spring MVC routes. */
  public HttpEntryDiscovery discoverEntries(
      ApplicationProfile profile, VerifiedSourceInventoryReference frozenSource) {
    try {
      requireSpringMvcProfile(profile);
      VerifiedSourceTextSet source = sourceReader.reopen(frozenSource);
      return discoverEntries(profile, source, defaultShards(source));
    } catch (ApplicationDiscoveryException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
    }
  }

  /**
   * Reads explicit disjoint Java source shards without allowing a shard to change the denominator.
   */
  public HttpEntryDiscovery discoverEntries(
      ApplicationProfile profile,
      VerifiedSourceInventoryReference frozenSource,
      List<JavaSourceShard> sourceShards) {
    try {
      requireSpringMvcProfile(profile);
      VerifiedSourceTextSet source = sourceReader.reopen(frozenSource);
      return discoverEntries(profile, source, sourceShards);
    } catch (ApplicationDiscoveryException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
    }
  }

  private static HttpEntryDiscovery discoverEntries(
      ApplicationProfile profile,
      VerifiedSourceTextSet source,
      List<JavaSourceShard> sourceShards) {
    try {
      requireSameVerifiedBasis(profile, source);
      List<HttpEntryPoint> entries = new ArrayList<>();
      List<HttpEntrySite> sites = new ArrayList<>();
      for (VerifiedSourceTextDocument document : selectedJavaDocuments(source, sourceShards)) {
        ParsedHttpEntries parsed = entries(document, profile.snapshotId());
        entries.addAll(parsed.entries());
        sites.addAll(parsed.sites());
      }
      return HttpEntryDiscovery.ordered(entries, sites, shardReceipts(sourceShards, sites));
    } catch (ApplicationDiscoveryException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
    }
  }

  private static List<JavaSourceShard> defaultShards(VerifiedSourceTextSet source) {
    List<ArtifactId> javaFileIds =
        source.documents().stream()
            .filter(document -> document.path().endsWith(".java"))
            .map(VerifiedSourceTextDocument::fileId)
            .sorted(Comparator.comparing(ArtifactId::value))
            .toList();
    if (javaFileIds.isEmpty()) {
      return List.of();
    }
    String identity =
        javaFileIds.stream()
            .map(ArtifactId::value)
            .reduce("", (left, right) -> left + "\n" + right);
    return List.of(
        new JavaSourceShard(
            ArtifactId.parse("entry-shard:" + sha256(frame(identity))), javaFileIds));
  }

  private static List<VerifiedSourceTextDocument> selectedJavaDocuments(
      VerifiedSourceTextSet source, List<JavaSourceShard> sourceShards) {
    if (sourceShards == null) {
      throw new ApplicationDiscoveryException("HTTP_ENTRY_SHARD_INVALID");
    }
    Map<ArtifactId, VerifiedSourceTextDocument> javaById = new HashMap<>();
    for (VerifiedSourceTextDocument document : source.documents()) {
      if (document.path().endsWith(".java") && javaById.put(document.fileId(), document) != null) {
        throw new ApplicationDiscoveryException("HTTP_ENTRY_SHARD_INVALID");
      }
    }
    Set<ArtifactId> sharded = new HashSet<>();
    List<VerifiedSourceTextDocument> selected = new ArrayList<>();
    for (JavaSourceShard shard : sourceShards) {
      if (shard == null) {
        throw new ApplicationDiscoveryException("HTTP_ENTRY_SHARD_INVALID");
      }
      for (ArtifactId fileId : shard.sourceFileIds()) {
        VerifiedSourceTextDocument document = javaById.get(fileId);
        if (document == null || !sharded.add(fileId)) {
          throw new ApplicationDiscoveryException("HTTP_ENTRY_SHARD_INVALID");
        }
        selected.add(document);
      }
    }
    if (!sharded.equals(javaById.keySet())) {
      throw new ApplicationDiscoveryException("HTTP_ENTRY_SHARD_INVALID");
    }
    selected.sort(Comparator.comparing(VerifiedSourceTextDocument::path));
    return List.copyOf(selected);
  }

  private static ParsedHttpEntries entries(VerifiedSourceTextDocument document, String snapshotId) {
    String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    ParseResult<CompilationUnit> parsed = new JavaParser().parse(source);
    CompilationUnit unit =
        parsed
            .getResult()
            .orElseThrow(() -> new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID"));
    String packageName =
        unit.getPackageDeclaration().map(value -> value.getNameAsString()).orElse("");
    List<HttpEntryPoint> entries = new ArrayList<>();
    List<HttpEntrySite> sites = new ArrayList<>();
    for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
      Optional<AnnotationExpr> classMapping = annotation(unit, type, "RequestMapping");
      if (classMapping.isEmpty()) {
        recordMethodsWithoutClassRoute(document, source, snapshotId, unit, type, sites);
        continue;
      }
      SourceExcerptV1 classExcerpt = excerpt(document, source, classMapping.get());
      Optional<String> classRoute = staticSingleRoute(classMapping.get());
      if (classRoute.isEmpty()) {
        sites.add(
            new HttpEntrySite(
                siteId(
                    snapshotId,
                    classExcerpt,
                    List.of(),
                    SignalDisposition.UNSUPPORTED,
                    "DYNAMIC_ROUTE"),
                classExcerpt,
                List.of(),
                SignalDisposition.UNSUPPORTED,
                "DYNAMIC_ROUTE",
                gapId(snapshotId, classExcerpt, "DYNAMIC_ROUTE")));
        continue;
      }
      for (MethodDeclaration method : type.getMethods()) {
        Optional<AnnotationExpr> requestMapping = annotation(unit, method, "RequestMapping");
        List<HttpMethodMapping> mappings = httpMethodMappings(unit, method);
        if (mappings.size() > 1) {
          SourceExcerptV1 methodExcerpt = excerpt(document, source, mappings.get(0).annotation());
          sites.add(
              new HttpEntrySite(
                  siteId(
                      snapshotId,
                      methodExcerpt,
                      List.of(),
                      SignalDisposition.AMBIGUOUS,
                      "MULTIPLE_HTTP_MAPPING_ANNOTATIONS"),
                  methodExcerpt,
                  List.of(),
                  SignalDisposition.AMBIGUOUS,
                  "MULTIPLE_HTTP_MAPPING_ANNOTATIONS",
                  gapId(snapshotId, methodExcerpt, "MULTIPLE_HTTP_MAPPING_ANNOTATIONS")));
          continue;
        }
        Optional<HttpMethodMapping> mapping = mappings.stream().findFirst();
        if (mapping.isEmpty()) {
          if (requestMapping.isPresent()) {
            SourceExcerptV1 methodExcerpt = excerpt(document, source, requestMapping.get());
            sites.add(
                new HttpEntrySite(
                    siteId(
                        snapshotId,
                        methodExcerpt,
                        List.of(),
                        SignalDisposition.UNSUPPORTED,
                        "UNSPECIFIED_HTTP_METHOD"),
                    methodExcerpt,
                    List.of(),
                    SignalDisposition.UNSUPPORTED,
                    "UNSPECIFIED_HTTP_METHOD",
                    gapId(snapshotId, methodExcerpt, "UNSPECIFIED_HTTP_METHOD")));
          }
          continue;
        }
        SourceExcerptV1 methodExcerpt = excerpt(document, source, mapping.get().annotation());
        Optional<String> methodRoute = staticSingleRoute(mapping.get().annotation());
        if (methodRoute.isEmpty()) {
          sites.add(
              new HttpEntrySite(
                  siteId(
                      snapshotId,
                      methodExcerpt,
                      List.of(),
                      SignalDisposition.UNSUPPORTED,
                      "DYNAMIC_ROUTE"),
                  methodExcerpt,
                  List.of(),
                  SignalDisposition.UNSUPPORTED,
                  "DYNAMIC_ROUTE",
                  gapId(snapshotId, methodExcerpt, "DYNAMIC_ROUTE")));
          continue;
        }
        String handlerFqn = qualifiedType(packageName, type) + "#" + method.getNameAsString();
        List<String> parameters =
            method.getParameters().stream().map(parameter -> parameter.getNameAsString()).toList();
        List<String> routeParts =
            List.of(normalizeRoute(classRoute.get()), normalizeRoute(methodRoute.get()));
        String route = composeRoute(routeParts.get(0), routeParts.get(1));
        HttpEntryPoint entry =
            new HttpEntryPoint(
                entryId(
                    snapshotId,
                    mapping.get().method(),
                    routeParts,
                    handlerFqn,
                    parameters,
                    classExcerpt,
                    methodExcerpt),
                HttpEntryKind.SPRING_MVC_HTTP,
                "HTTP",
                mapping.get().method(),
                route,
                routeParts,
                handlerFqn,
                parameters,
                List.of(classExcerpt, methodExcerpt));
        entries.add(entry);
        sites.add(
            new HttpEntrySite(
                siteId(
                    snapshotId,
                    methodExcerpt,
                    List.of(entry.entryId()),
                    SignalDisposition.SUPPORTED,
                    null),
                methodExcerpt,
                List.of(entry.entryId()),
                SignalDisposition.SUPPORTED,
                null,
                null));
      }
    }
    return new ParsedHttpEntries(List.copyOf(entries), List.copyOf(sites));
  }

  private static void recordMethodsWithoutClassRoute(
      VerifiedSourceTextDocument document,
      String source,
      String snapshotId,
      CompilationUnit unit,
      ClassOrInterfaceDeclaration type,
      List<HttpEntrySite> sites) {
    for (MethodDeclaration method : type.getMethods()) {
      List<HttpMethodMapping> mappings = httpMethodMappings(unit, method);
      if (mappings.isEmpty()) {
        continue;
      }
      SourceExcerptV1 methodExcerpt = excerpt(document, source, mappings.get(0).annotation());
      sites.add(
          new HttpEntrySite(
              siteId(
                  snapshotId,
                  methodExcerpt,
                  List.of(),
                  SignalDisposition.UNSUPPORTED,
                  "MISSING_CLASS_ROUTE"),
              methodExcerpt,
              List.of(),
              SignalDisposition.UNSUPPORTED,
              "MISSING_CLASS_ROUTE",
              gapId(snapshotId, methodExcerpt, "MISSING_CLASS_ROUTE")));
    }
  }

  private static Optional<AnnotationExpr> annotation(
      CompilationUnit unit, NodeWithAnnotations<?> annotated, String simpleName) {
    return annotated.getAnnotations().stream()
        .filter(value -> isSpringAnnotation(unit, value, simpleName))
        .findFirst();
  }

  private static boolean isSpringAnnotation(
      CompilationUnit unit, AnnotationExpr annotation, String simpleName) {
    String qualifiedName = SPRING_WEB_ANNOTATION_PACKAGE + "." + simpleName;
    if (annotation.getNameAsString().equals(qualifiedName)) {
      return true;
    }
    if (!annotation.getNameAsString().equals(simpleName)) {
      return false;
    }
    return unit.getImports().stream()
        .anyMatch(
            imported ->
                !imported.isStatic()
                    && ((imported.isAsterisk()
                            && imported.getNameAsString().equals(SPRING_WEB_ANNOTATION_PACKAGE))
                        || (!imported.isAsterisk()
                            && imported.getNameAsString().equals(qualifiedName))));
  }

  private static List<HttpMethodMapping> httpMethodMappings(
      CompilationUnit unit, MethodDeclaration method) {
    List<HttpMethodMapping> mappings =
        List.of(
                new HttpMethodAnnotation("GetMapping", "GET"),
                new HttpMethodAnnotation("PostMapping", "POST"),
                new HttpMethodAnnotation("PutMapping", "PUT"),
                new HttpMethodAnnotation("DeleteMapping", "DELETE"),
                new HttpMethodAnnotation("PatchMapping", "PATCH"))
            .stream()
            .flatMap(
                known ->
                    annotation(unit, method, known.annotationName()).stream()
                        .map(annotation -> new HttpMethodMapping(annotation, known.method())))
            .toList();
    if (!mappings.isEmpty()) {
      return mappings;
    }
    return annotation(unit, method, "RequestMapping")
        .flatMap(
            annotation ->
                explicitRequestMappingMethod(annotation)
                    .map(httpMethod -> new HttpMethodMapping(annotation, httpMethod)))
        .stream()
        .toList();
  }

  private static Optional<String> staticSingleRoute(AnnotationExpr annotation) {
    if (annotation.isSingleMemberAnnotationExpr()
        && annotation.asSingleMemberAnnotationExpr().getMemberValue()
            instanceof StringLiteralExpr value) {
      return Optional.of(value.getValue());
    }
    if (annotation.isNormalAnnotationExpr()) {
      List<MemberValuePair> routePairs =
          annotation.asNormalAnnotationExpr().getPairs().stream()
              .filter(
                  pair ->
                      pair.getNameAsString().equals("value")
                          || pair.getNameAsString().equals("path"))
              .toList();
      if (routePairs.size() == 1
          && routePairs.get(0).getValue() instanceof StringLiteralExpr value) {
        return Optional.of(value.getValue());
      }
    }
    return Optional.empty();
  }

  private static Optional<String> explicitRequestMappingMethod(AnnotationExpr annotation) {
    if (!annotation.isNormalAnnotationExpr()) {
      return Optional.empty();
    }
    List<MemberValuePair> methodPairs =
        annotation.asNormalAnnotationExpr().getPairs().stream()
            .filter(pair -> pair.getNameAsString().equals("method"))
            .toList();
    if (methodPairs.size() != 1
        || !(methodPairs.get(0).getValue() instanceof FieldAccessExpr method)) {
      return Optional.empty();
    }
    if (!method.getScope().toString().equals("RequestMethod")) {
      return Optional.empty();
    }
    return switch (method.getNameAsString()) {
      case "GET", "POST", "PUT", "DELETE", "PATCH" -> Optional.of(method.getNameAsString());
      default -> Optional.empty();
    };
  }

  private static String qualifiedType(String packageName, ClassOrInterfaceDeclaration type) {
    return packageName.isEmpty()
        ? type.getNameAsString()
        : packageName + "." + type.getNameAsString();
  }

  private static String normalizeRoute(String route) {
    if (route == null || route.isBlank()) {
      throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
    }
    return route.startsWith("/") ? route : "/" + route;
  }

  private static String composeRoute(String classRoute, String methodRoute) {
    String prefix =
        classRoute.endsWith("/") ? classRoute.substring(0, classRoute.length() - 1) : classRoute;
    String suffix = methodRoute.startsWith("/") ? methodRoute : "/" + methodRoute;
    return prefix + suffix;
  }

  private static SourceExcerptV1 excerpt(
      VerifiedSourceTextDocument document, String source, AnnotationExpr annotation) {
    var range =
        annotation
            .getRange()
            .orElseThrow(() -> new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID"));
    int characterStart = characterOffset(source, range.begin.line, range.begin.column);
    int characterEndExclusive = characterOffset(source, range.end.line, range.end.column + 1);
    byte[] rawUtf8 = document.rawUtf8().copyToByteArray();
    int startByte = source.substring(0, characterStart).getBytes(StandardCharsets.UTF_8).length;
    int endByte =
        source.substring(0, characterEndExclusive).getBytes(StandardCharsets.UTF_8).length;
    ImmutableBytes bytes =
        ImmutableBytes.copyOf(java.util.Arrays.copyOfRange(rawUtf8, startByte, endByte));
    return new SourceExcerptV1(
        new SourceLocatorV1(
            document.fileId(),
            document.path(),
            startByte,
            endByte,
            range.begin.line,
            range.begin.column,
            range.end.line,
            range.end.column + 1),
        bytes,
        Sha256Digest.parse(sha256(bytes.copyToByteArray())));
  }

  private static int characterOffset(String source, int line, int column) {
    if (line < 1 || column < 1) {
      throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
    }
    int offset = 0;
    for (int current = 1; current < line; current++) {
      int newline = source.indexOf('\n', offset);
      if (newline < 0) {
        throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
      }
      offset = newline + 1;
    }
    int result = offset + column - 1;
    if (result < offset || result > source.length()) {
      throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
    }
    return result;
  }

  private static ArtifactId entryId(
      String snapshotId,
      String method,
      List<String> routeParts,
      String handlerFqn,
      List<String> parameterNames,
      SourceExcerptV1 classExcerpt,
      SourceExcerptV1 methodExcerpt) {
    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("snapshotId", snapshotId);
    material.put("kind", HttpEntryKind.SPRING_MVC_HTTP.name());
    material.put("protocol", "HTTP");
    material.put("method", method);
    ArrayNode routes = material.putArray("routeParts");
    routeParts.forEach(routes::add);
    material.put("handlerFqn", handlerFqn);
    ArrayNode parameters = material.putArray("parameterNames");
    parameterNames.forEach(parameters::add);
    ArrayNode excerpts = material.putArray("routeSourceExcerpts");
    excerpts.add(excerptIdentity(classExcerpt));
    excerpts.add(excerptIdentity(methodExcerpt));
    return ArtifactId.parse(
        "entry:"
            + sha256(
                concatenate(
                    frame("application-discovery-http-entry-id-v2"),
                    frame(CANONICAL_JSON.encodeCanonical(material).copyToByteArray()))));
  }

  private static ArtifactId siteId(
      String snapshotId,
      SourceExcerptV1 primaryExcerpt,
      List<ArtifactId> affectedEntryIds,
      SignalDisposition disposition,
      String reasonCode) {
    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("snapshotId", snapshotId);
    material.set("primaryExcerpt", excerptIdentity(primaryExcerpt));
    ArrayNode affected = material.putArray("affectedEntryIds");
    affectedEntryIds.stream().map(ArtifactId::value).sorted().forEach(affected::add);
    material.put("disposition", disposition.name());
    if (reasonCode == null) {
      material.putNull("reasonCode");
    } else {
      material.put("reasonCode", reasonCode);
    }
    return ArtifactId.parse(
        "site:"
            + sha256(
                concatenate(
                    frame("application-discovery-http-site-id-v2"),
                    frame(CANONICAL_JSON.encodeCanonical(material).copyToByteArray()))));
  }

  private static ArtifactId gapId(
      String snapshotId, SourceExcerptV1 primaryExcerpt, String reasonCode) {
    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("snapshotId", snapshotId);
    material.set("primaryExcerpt", excerptIdentity(primaryExcerpt));
    material.put("reasonCode", reasonCode);
    return ArtifactId.parse(
        "gap:"
            + sha256(
                concatenate(
                    frame("application-discovery-http-entry-gap-id-v2"),
                    frame(CANONICAL_JSON.encodeCanonical(material).copyToByteArray()))));
  }

  private static List<HttpEntryShardReceipt> shardReceipts(
      List<JavaSourceShard> sourceShards, List<HttpEntrySite> sites) {
    Map<ArtifactId, List<HttpEntrySite>> sitesByFileId = new HashMap<>();
    for (HttpEntrySite site : sites) {
      sitesByFileId
          .computeIfAbsent(site.primaryExcerpt().locator().fileId(), ignored -> new ArrayList<>())
          .add(site);
    }
    List<HttpEntryShardReceipt> receipts = new ArrayList<>();
    for (JavaSourceShard shard : sourceShards) {
      List<HttpEntrySite> shardSites = new ArrayList<>();
      for (ArtifactId fileId : shard.sourceFileIds()) {
        shardSites.addAll(sitesByFileId.getOrDefault(fileId, List.of()));
      }
      List<ArtifactId> siteIds =
          shardSites.stream()
              .map(HttpEntrySite::siteId)
              .sorted(Comparator.comparing(ArtifactId::value))
              .toList();
      List<ArtifactId> gapIds =
          shardSites.stream()
              .map(HttpEntrySite::gapId)
              .filter(java.util.Objects::nonNull)
              .sorted(Comparator.comparing(ArtifactId::value))
              .toList();
      receipts.add(
          new HttpEntryShardReceipt(
              shard.shardId(),
              siteIds,
              siteIds,
              gapIds.isEmpty() ? "SUCCEEDED" : "SUCCEEDED_WITH_GAPS",
              gapIds));
    }
    return List.copyOf(receipts);
  }

  private static ObjectNode excerptIdentity(SourceExcerptV1 excerpt) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    ObjectNode locator = result.putObject("locator");
    locator.put("fileId", excerpt.locator().fileId().value());
    locator.put("path", excerpt.locator().path());
    locator.put("startByte", excerpt.locator().startByte());
    locator.put("endByteExclusive", excerpt.locator().endByteExclusive());
    locator.put("startLine", excerpt.locator().startLine());
    locator.put("startColumn", excerpt.locator().startColumn());
    locator.put("endLine", excerpt.locator().endLine());
    locator.put("endColumn", excerpt.locator().endColumn());
    result.put("rawUtf8Sha256", excerpt.rawUtf8Sha256().value());
    return result;
  }

  private static void requireSpringMvcProfile(ApplicationProfile profile) {
    if (profile == null
        || profile.frameworkSignals().stream()
            .noneMatch(
                signal ->
                    signal.kind() == FrameworkSignalKind.SPRING_MVC
                        && signal.disposition() == SignalDisposition.SUPPORTED)) {
      throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_REQUEST_INVALID");
    }
  }

  private static void requireSameVerifiedBasis(
      ApplicationProfile profile, VerifiedSourceTextSet source) {
    if (!profile.snapshotId().equals(source.snapshotId())
        || !profile.capabilityProfileRef().equals(source.capabilityProfileRef())
        || !profile.sourceInventoryRef().equals(source.sourceInventoryRef())
        || !profile.verifiedSnapshotRef().equals(source.verifiedSnapshotRef())
        || !profile.controls().equals(source.controls())) {
      throw new ApplicationDiscoveryException("SNAPSHOT_REOPEN_MISMATCH");
    }
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[] first, byte[] second) {
    byte[] result = new byte[first.length + second.length];
    System.arraycopy(first, 0, result, 0, first.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private record HttpMethodAnnotation(String annotationName, String method) {}

  private record HttpMethodMapping(AnnotationExpr annotation, String method) {}

  private record ParsedHttpEntries(List<HttpEntryPoint> entries, List<HttpEntrySite> sites) {}
}
