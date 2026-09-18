package org.sourceanalysis.app.analysis.discovery;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;
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

  /** Discovers routes from the selected Java engine catalog without invoking JavaParser. */
  public HttpEntryDiscovery discoverEntries(
      ApplicationProfile profile,
      VerifiedSourceInventoryReference frozenSource,
      JavaDeclarationCatalog catalog) {
    try {
      requireSpringMvcProfile(profile);
      VerifiedSourceTextSet source = sourceReader.reopen(frozenSource);
      requireSameVerifiedBasis(profile, source);
      if (catalog == null || !profile.snapshotId().equals(catalog.snapshotId())) {
        throw new ApplicationDiscoveryException("SNAPSHOT_REOPEN_MISMATCH");
      }
      return discoverEntries(profile, source, catalog);
    } catch (ApplicationDiscoveryException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
    }
  }

  private static HttpEntryDiscovery discoverEntries(
      ApplicationProfile profile, VerifiedSourceTextSet source, JavaDeclarationCatalog catalog) {
    Map<String, VerifiedSourceTextDocument> documents =
        source.documents().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    VerifiedSourceTextDocument::path,
                    value -> value,
                    (left, right) -> {
                      throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
                    },
                    HashMap::new));
    Map<String, JavaDeclarationCatalog.AnnotationView> annotations =
        catalog.annotations().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    JavaDeclarationCatalog.AnnotationView::annotationKey,
                    value -> value,
                    (left, right) -> {
                      throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
                    },
                    HashMap::new));
    Map<String, JavaDeclarationCatalog.MethodDeclarationView> methods =
        catalog.methods().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    JavaDeclarationCatalog.MethodDeclarationView::methodKey,
                    value -> value,
                    (left, right) -> {
                      throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
                    },
                    HashMap::new));
    List<HttpEntryPoint> entries = new ArrayList<>();
    List<HttpEntrySite> sites = new ArrayList<>();
    for (JavaDeclarationCatalog.TypeDeclaration type : catalog.types()) {
      if (!"CLASS".equals(type.kind())) {
        continue;
      }
      VerifiedSourceTextDocument document = documents.get(type.sourcePath());
      if (document == null) {
        throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
      }
      String text = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      List<JavaDeclarationCatalog.AnnotationView> classMappings =
          mappingAnnotations(type.annotationKeys(), annotations, "RequestMapping");
      if (classMappings.isEmpty()) {
        recordCatalogMethodsWithoutClassRoute(
            document, text, profile.snapshotId(), type, methods, annotations, sites);
        continue;
      }
      if (classMappings.size() > 1) {
        SourceExcerptV1 primary = excerpt(document, text, classMappings.get(0));
        sites.add(
            unsupportedCatalogSite(
                profile.snapshotId(),
                primary,
                SignalDisposition.AMBIGUOUS,
                "MULTIPLE_HTTP_MAPPING_ANNOTATIONS"));
        continue;
      }
      JavaDeclarationCatalog.AnnotationView classMapping = classMappings.get(0);
      SourceExcerptV1 classExcerpt = excerpt(document, text, classMapping);
      Optional<String> classRoute = staticSingleRoute(classMapping);
      if (classRoute.isEmpty()) {
        sites.add(
            unsupportedCatalogSite(
                profile.snapshotId(),
                classExcerpt,
                SignalDisposition.UNSUPPORTED,
                "DYNAMIC_ROUTE"));
        continue;
      }
      HttpMethodCondition classCondition = requestMappingMethodCondition(classMapping);
      for (String methodKey : type.methodKeys()) {
        JavaDeclarationCatalog.MethodDeclarationView method = methods.get(methodKey);
        if (method == null || !method.sourcePath().equals(type.sourcePath())) {
          throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
        }
        List<CatalogHttpMapping> mappings = catalogHttpMappings(method, annotations);
        if (mappings.size() > 1) {
          SourceExcerptV1 primary = excerpt(document, text, mappings.get(0).annotation());
          sites.add(
              unsupportedCatalogSite(
                  profile.snapshotId(),
                  primary,
                  SignalDisposition.AMBIGUOUS,
                  "MULTIPLE_HTTP_MAPPING_ANNOTATIONS"));
          continue;
        }
        if (mappings.isEmpty()) {
          recordUnresolvedCatalogMappings(
              document, text, profile.snapshotId(), method, annotations, sites);
          continue;
        }
        CatalogHttpMapping mapping = mappings.get(0);
        SourceExcerptV1 methodExcerpt = excerpt(document, text, mapping.annotation());
        Optional<String> methodRoute = staticSingleRoute(mapping.annotation());
        if (methodRoute.isEmpty()) {
          sites.add(
              unsupportedCatalogSite(
                  profile.snapshotId(),
                  methodExcerpt,
                  SignalDisposition.UNSUPPORTED,
                  "DYNAMIC_ROUTE"));
          continue;
        }
        List<String> routeParts =
            List.of(normalizeRoute(classRoute.get()), normalizeRoute(methodRoute.get()));
        HttpMethodCondition condition = classCondition.combine(mapping.methodCondition());
        String handlerFqn = type.qualifiedName() + "#" + method.name();
        List<String> parameters = method.parameters().stream().map(value -> value.name()).toList();
        HttpEntryPoint entry =
            new HttpEntryPoint(
                entryId(
                    profile.snapshotId(),
                    condition,
                    routeParts,
                    handlerFqn,
                    method.methodKey(),
                    method.sourceRange(),
                    parameters,
                    classExcerpt,
                    methodExcerpt),
                HttpEntryKind.SPRING_MVC_HTTP,
                "HTTP",
                condition,
                composeRoute(routeParts.get(0), routeParts.get(1)),
                routeParts,
                handlerFqn,
                method.methodKey(),
                method.sourceRange(),
                parameters,
                List.of(classExcerpt, methodExcerpt));
        entries.add(entry);
        sites.add(
            new HttpEntrySite(
                siteId(
                    profile.snapshotId(),
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
    return HttpEntryDiscovery.ordered(entries, sites, shardReceipts(defaultShards(source), sites));
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

  private static void recordCatalogMethodsWithoutClassRoute(
      VerifiedSourceTextDocument document,
      String source,
      String snapshotId,
      JavaDeclarationCatalog.TypeDeclaration type,
      Map<String, JavaDeclarationCatalog.MethodDeclarationView> methods,
      Map<String, JavaDeclarationCatalog.AnnotationView> annotations,
      List<HttpEntrySite> sites) {
    boolean unresolvedClassMapping =
        type.annotationKeys().stream()
            .map(annotations::get)
            .filter(Objects::nonNull)
            .anyMatch(SpringHttpEntryDiscoverer::unresolvedMappingAnnotation);
    for (String methodKey : type.methodKeys()) {
      JavaDeclarationCatalog.MethodDeclarationView method = methods.get(methodKey);
      if (method == null) {
        throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
      }
      List<CatalogHttpMapping> mappings = catalogHttpMappings(method, annotations);
      if (mappings.isEmpty()) {
        recordUnresolvedCatalogMappings(document, source, snapshotId, method, annotations, sites);
        continue;
      }
      SourceExcerptV1 primary = excerpt(document, source, mappings.get(0).annotation());
      sites.add(
          unsupportedCatalogSite(
              snapshotId,
              primary,
              SignalDisposition.UNSUPPORTED,
              unresolvedClassMapping ? "ANNOTATION_IDENTITY_UNRESOLVED" : "MISSING_CLASS_ROUTE"));
    }
  }

  private static void recordUnresolvedCatalogMappings(
      VerifiedSourceTextDocument document,
      String source,
      String snapshotId,
      JavaDeclarationCatalog.MethodDeclarationView method,
      Map<String, JavaDeclarationCatalog.AnnotationView> annotations,
      List<HttpEntrySite> sites) {
    method.annotationKeys().stream()
        .map(annotations::get)
        .filter(Objects::nonNull)
        .filter(SpringHttpEntryDiscoverer::unresolvedMappingAnnotation)
        .findFirst()
        .ifPresent(
            annotation ->
                sites.add(
                    unsupportedCatalogSite(
                        snapshotId,
                        excerpt(document, source, annotation),
                        SignalDisposition.UNSUPPORTED,
                        "ANNOTATION_IDENTITY_UNRESOLVED")));
  }

  private static HttpEntrySite unsupportedCatalogSite(
      String snapshotId, SourceExcerptV1 primary, SignalDisposition disposition, String reason) {
    return new HttpEntrySite(
        siteId(snapshotId, primary, List.of(), disposition, reason),
        primary,
        List.of(),
        disposition,
        reason,
        gapId(snapshotId, primary, reason));
  }

  private static List<JavaDeclarationCatalog.AnnotationView> mappingAnnotations(
      List<String> annotationKeys,
      Map<String, JavaDeclarationCatalog.AnnotationView> annotations,
      String simpleName) {
    String qualified = SPRING_WEB_ANNOTATION_PACKAGE + "." + simpleName;
    return annotationKeys.stream()
        .map(annotations::get)
        .filter(Objects::nonNull)
        .filter(annotation -> qualified.equals(annotation.qualifiedName()))
        .toList();
  }

  private static List<CatalogHttpMapping> catalogHttpMappings(
      JavaDeclarationCatalog.MethodDeclarationView method,
      Map<String, JavaDeclarationCatalog.AnnotationView> annotations) {
    List<CatalogHttpMapping> result = new ArrayList<>();
    Map<String, String> explicit =
        Map.of(
            "GetMapping", "GET",
            "PostMapping", "POST",
            "PutMapping", "PUT",
            "DeleteMapping", "DELETE",
            "PatchMapping", "PATCH");
    for (String key : method.annotationKeys()) {
      JavaDeclarationCatalog.AnnotationView annotation = annotations.get(key);
      if (annotation == null || annotation.qualifiedName() == null) {
        continue;
      }
      String prefix = SPRING_WEB_ANNOTATION_PACKAGE + ".";
      if (!annotation.qualifiedName().startsWith(prefix)) {
        continue;
      }
      String simple = annotation.qualifiedName().substring(prefix.length());
      if (explicit.containsKey(simple)) {
        result.add(
            new CatalogHttpMapping(
                annotation, HttpMethodCondition.explicit(List.of(explicit.get(simple)))));
      } else if ("RequestMapping".equals(simple)) {
        result.add(new CatalogHttpMapping(annotation, requestMappingMethodCondition(annotation)));
      }
    }
    return List.copyOf(result);
  }

  private static boolean unresolvedMappingAnnotation(
      JavaDeclarationCatalog.AnnotationView annotation) {
    return annotation.qualifiedName() == null
        && Set.of(
                "RequestMapping",
                "GetMapping",
                "PostMapping",
                "PutMapping",
                "DeleteMapping",
                "PatchMapping")
            .contains(annotation.nameText());
  }

  private static Optional<String> staticSingleRoute(
      JavaDeclarationCatalog.AnnotationView annotation) {
    List<Object> values = new ArrayList<>();
    if (annotation.staticValues().containsKey("value")) {
      values.add(annotation.staticValues().get("value"));
    }
    if (annotation.staticValues().containsKey("path")) {
      values.add(annotation.staticValues().get("path"));
    }
    if (values.size() != 1) {
      return Optional.empty();
    }
    Map<?, ?> value = map(values.get(0));
    return "STRING".equals(value.get("kind")) && value.get("value") instanceof String route
        ? Optional.of(route)
        : Optional.empty();
  }

  private static HttpMethodCondition requestMappingMethodCondition(
      JavaDeclarationCatalog.AnnotationView annotation) {
    Object raw = annotation.staticValues().get("method");
    if (raw == null) {
      return HttpMethodCondition.unrestricted();
    }
    Map<?, ?> value = map(raw);
    List<String> methods;
    if ("ARRAY".equals(value.get("kind"))) {
      Object elements = value.get("elements");
      if (!(elements instanceof List<?> list)) {
        throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
      }
      methods = list.stream().map(SpringHttpEntryDiscoverer::catalogRequestMethod).toList();
    } else {
      methods = List.of(catalogRequestMethod(value));
    }
    return methods.isEmpty()
        ? HttpMethodCondition.unrestricted()
        : HttpMethodCondition.explicit(methods);
  }

  private static String catalogRequestMethod(Object raw) {
    Map<?, ?> value = map(raw);
    if (!"SYMBOL".equals(value.get("kind")) || !(value.get("value") instanceof String symbol)) {
      throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
    }
    String name = symbol.substring(symbol.lastIndexOf('.') + 1);
    return switch (name) {
      case "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE" -> name;
      default -> throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
    };
  }

  private static Map<?, ?> map(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
    }
    return map;
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
      VerifiedSourceTextDocument document,
      String source,
      JavaDeclarationCatalog.AnnotationView annotation) {
    if (!document.path().equals(annotation.sourcePath())) {
      throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
    }
    SourceRange range = annotation.sourceRange();
    int start = range.startOffsetUtf16();
    int end = Math.addExact(start, range.lengthUtf16());
    if (start < 0 || end < start || end > source.length()) {
      throw new ApplicationDiscoveryException("HTTP_ENTRY_DISCOVERY_INVALID");
    }
    byte[] rawUtf8 = document.rawUtf8().copyToByteArray();
    int startByte = source.substring(0, start).getBytes(StandardCharsets.UTF_8).length;
    int endByte = source.substring(0, end).getBytes(StandardCharsets.UTF_8).length;
    int startColumn = oneBasedColumn(source, start);
    int endColumn = oneBasedColumn(source, end);
    ImmutableBytes bytes =
        ImmutableBytes.copyOf(java.util.Arrays.copyOfRange(rawUtf8, startByte, endByte));
    return new SourceExcerptV1(
        new SourceLocatorV1(
            document.fileId(),
            document.path(),
            startByte,
            endByte,
            range.startLine(),
            startColumn,
            range.endLine(),
            endColumn),
        bytes,
        Sha256Digest.parse(sha256(bytes.copyToByteArray())));
  }

  private static int oneBasedColumn(String source, int offset) {
    int lineStart = source.lastIndexOf('\n', Math.max(0, offset - 1));
    return offset - lineStart;
  }

  private static ArtifactId entryId(
      String snapshotId,
      HttpMethodCondition methodCondition,
      List<String> routeParts,
      String handlerFqn,
      String methodKey,
      SourceRange methodRange,
      List<String> parameterNames,
      SourceExcerptV1 classExcerpt,
      SourceExcerptV1 methodExcerpt) {
    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("snapshotId", snapshotId);
    material.put("kind", HttpEntryKind.SPRING_MVC_HTTP.name());
    material.put("protocol", "HTTP");
    ObjectNode method = material.putObject("methodCondition");
    method.put("kind", methodCondition.kind().name());
    ArrayNode methods = method.putArray("methods");
    methodCondition.methods().forEach(methods::add);
    ArrayNode routes = material.putArray("routeParts");
    routeParts.forEach(routes::add);
    material.put("handlerFqn", handlerFqn);
    material.put("methodKey", methodKey);
    ObjectNode range = material.putObject("methodRange");
    range.put("startOffsetUtf16", methodRange.startOffsetUtf16());
    range.put("lengthUtf16", methodRange.lengthUtf16());
    range.put("startLine", methodRange.startLine());
    range.put("endLine", methodRange.endLine());
    ArrayNode parameters = material.putArray("parameterNames");
    parameterNames.forEach(parameters::add);
    ArrayNode excerpts = material.putArray("routeSourceExcerpts");
    excerpts.add(excerptIdentity(classExcerpt));
    excerpts.add(excerptIdentity(methodExcerpt));
    return ArtifactId.parse(
        "entry:"
            + sha256(
                concatenate(
                    frame("application-discovery-http-entry-id-v3"),
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

  private record CatalogHttpMapping(
      JavaDeclarationCatalog.AnnotationView annotation, HttpMethodCondition methodCondition) {}
}
