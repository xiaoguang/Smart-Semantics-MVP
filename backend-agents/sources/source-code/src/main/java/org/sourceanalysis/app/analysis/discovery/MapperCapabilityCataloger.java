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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
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
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Catalogs static MyBatis Java/XML candidates from verified source bytes without call binding. */
public final class MapperCapabilityCataloger {

  private static final CanonicalJsonCodec CANONICAL_JSON = new CanonicalJsonCodec();
  private static final List<String> STATEMENT_KINDS =
      List.of("select", "insert", "update", "delete");

  private final VerifiedSourceTextReader sourceReader;

  MapperCapabilityCataloger(VerifiedSourceTextReader sourceReader) {
    if (sourceReader == null) {
      throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_REQUEST_INVALID");
    }
    this.sourceReader = sourceReader;
  }

  /**
   * Uses the selected Java engine's catalog for Java declarations and the verified bytes for XML.
   */
  public MapperCatalogDiscovery catalogMappers(
      ApplicationProfile profile,
      VerifiedSourceInventoryReference frozenSource,
      JavaDeclarationCatalog javaCatalog) {
    try {
      VerifiedSourceTextSet source = sourceReader.reopen(frozenSource);
      return catalogMappers(profile, source, javaCatalog, MapperXmlResourceView.open(source));
    } catch (ApplicationDiscoveryException failure) {
      throw failure;
    } catch (IllegalStateException failure) {
      if ("XML_SECURITY_POLICY_UNENFORCEABLE".equals(failure.getMessage())) {
        throw new ApplicationDiscoveryException("XML_SECURITY_POLICY_UNENFORCEABLE", failure);
      }
      throw new ApplicationDiscoveryException("MAPPER_CATALOG_REFERENCE_BROKEN");
    } catch (RuntimeException failure) {
      throw new ApplicationDiscoveryException("MAPPER_CATALOG_REFERENCE_BROKEN");
    }
  }

  /**
   * Same-run internal seam: discovery consumes a caller-owned secure mapper view instead of
   * reopening or reparsing the frozen XML.
   */
  MapperCatalogDiscovery catalogMappers(
      ApplicationProfile profile,
      VerifiedSourceTextSet source,
      JavaDeclarationCatalog javaCatalog,
      MapperXmlResourceView mapperResources) {
    try {
      requireCatalogInputs(profile, source, javaCatalog, mapperResources);
      requireSameVerifiedBasis(profile, source);
      mapperResources.requireSameFrozenSource(source);
      if (!profile.snapshotId().equals(javaCatalog.snapshotId())) {
        throw new ApplicationDiscoveryException("SNAPSHOT_REOPEN_MISMATCH");
      }
      Map<String, JavaMapperInterface> interfaces =
          javaInterfaces(source, profile.snapshotId(), javaCatalog);
      return catalog(
          profile.snapshotId(),
          interfaces,
          xmlMapperResources(mapperResources, profile.snapshotId()),
          mapperResources.rejectedResources());
    } catch (ApplicationDiscoveryException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ApplicationDiscoveryException("MAPPER_CATALOG_REFERENCE_BROKEN");
    }
  }

  private static MapperCatalogDiscovery catalog(
      String snapshotId,
      Map<String, JavaMapperInterface> interfaces,
      List<XmlMapperResource> resources,
      List<MapperXmlResourceView.RejectedXmlResource> rejectedResources) {
    List<MapperCatalogEntry> entries = new ArrayList<>();
    List<MapperCatalogSite> sites = new ArrayList<>();
    Set<String> resourceNamespaces = new HashSet<>();
    for (XmlMapperResource resource : resources) {
      resourceNamespaces.add(resource.namespace());
      JavaMapperInterface mapperInterface = interfaces.get(resource.namespace());
      if (mapperInterface == null) {
        sites.add(
            unsupportedSite(
                snapshotId, resource.namespaceExcerpt(), "XML_NAMESPACE_NO_JAVA_INTERFACE"));
        continue;
      }
      entries.add(entry(snapshotId, mapperInterface, resource));
      sites.add(supportedSite(snapshotId, resource.namespaceExcerpt()));
    }
    for (MapperXmlResourceView.RejectedXmlResource rejectedResource : rejectedResources) {
      if (!rejectedResource.rawSource().isEmpty()) {
        sites.add(rejectedSite(snapshotId, rejectedResource));
      }
    }
    for (JavaMapperInterface mapperInterface : interfaces.values()) {
      if (!resourceNamespaces.contains(mapperInterface.fqn())) {
        sites.add(
            unsupportedSite(
                snapshotId,
                mapperInterface.declarationExcerpt(),
                "JAVA_INTERFACE_NO_XML_NAMESPACE"));
      }
    }
    return MapperCatalogDiscovery.ordered(entries, sites);
  }

  private static Map<String, JavaMapperInterface> javaInterfaces(
      VerifiedSourceTextSet source, String snapshotId, JavaDeclarationCatalog catalog) {
    Map<String, VerifiedSourceTextDocument> documents = new HashMap<>();
    for (VerifiedSourceTextDocument document : source.documents()) {
      documents.put(document.path(), document);
    }
    Map<String, JavaDeclarationCatalog.MethodDeclarationView> methods = new HashMap<>();
    for (JavaDeclarationCatalog.MethodDeclarationView method : catalog.methods()) {
      if (methods.put(method.methodKey(), method) != null) {
        throw new ApplicationDiscoveryException("MAPPER_CATALOG_AMBIGUOUS");
      }
    }
    Map<String, JavaMapperInterface> interfaces = new HashMap<>();
    for (JavaDeclarationCatalog.TypeDeclaration type : catalog.types()) {
      if (!"INTERFACE".equals(type.kind()) || type.qualifiedName() == null) {
        continue;
      }
      VerifiedSourceTextDocument document = documents.get(type.sourcePath());
      if (document == null) {
        throw new ApplicationDiscoveryException("MAPPER_CATALOG_REFERENCE_BROKEN");
      }
      String text = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      List<MapperMethodCandidate> candidates = new ArrayList<>();
      for (String methodKey : type.methodKeys()) {
        JavaDeclarationCatalog.MethodDeclarationView method = methods.get(methodKey);
        if (method == null
            || !type.qualifiedName().equals(method.declaringType())
            || !type.sourcePath().equals(method.sourcePath())) {
          throw new ApplicationDiscoveryException("MAPPER_CATALOG_REFERENCE_BROKEN");
        }
        candidates.add(methodCandidate(snapshotId, type.qualifiedName(), document, text, method));
      }
      candidates.sort(Comparator.comparing(candidate -> candidate.methodCandidateId().value()));
      JavaMapperInterface mapperInterface =
          new JavaMapperInterface(
              type.qualifiedName(),
              excerpt(document, text, type.sourceRange()),
              List.copyOf(candidates));
      if (interfaces.put(type.qualifiedName(), mapperInterface) != null) {
        throw new ApplicationDiscoveryException("MAPPER_CATALOG_AMBIGUOUS");
      }
    }
    return interfaces;
  }

  private static List<XmlMapperResource> xmlMapperResources(
      MapperXmlResourceView mapperResources, String snapshotId) {
    return mapperResources.mapperResources().stream()
        .map(resource -> xmlMapperResource(snapshotId, resource))
        .sorted(Comparator.comparing(XmlMapperResource::path))
        .toList();
  }

  private static XmlMapperResource xmlMapperResource(
      String snapshotId, MapperXmlResourceView.MapperXmlResource resource) {
    VerifiedSourceTextDocument document = resource.document();
    String text = resource.rawSource();
    Element root = resource.parsedDocument().getDocumentElement();
    String namespace = resource.namespace();
    SourceExcerptV1 wholeResource = wholeResourceExcerpt(document, text);
    List<MapperStatementCandidate> statements = new ArrayList<>();
    Set<StatementClue> clues = new HashSet<>();
    for (String kind : STATEMENT_KINDS) {
      NodeList nodes = root.getElementsByTagName(kind);
      for (int index = 0; index < nodes.getLength(); index++) {
        if (!(nodes.item(index) instanceof Element element) || !element.hasAttribute("id")) {
          continue;
        }
        String statementId = element.getAttribute("id");
        if (statementId.isBlank()) {
          throw new ApplicationDiscoveryException("MAPPER_CATALOG_REFERENCE_BROKEN");
        }
        if (clues.add(new StatementClue(namespace, statementId, kind))) {
          statements.add(
              statementCandidate(snapshotId, namespace, statementId, kind, wholeResource));
        }
      }
    }
    statements.sort(Comparator.comparing(candidate -> candidate.statementCandidateId().value()));
    return new XmlMapperResource(
        document.path(), namespace, wholeResource, List.copyOf(statements));
  }

  private static MapperMethodCandidate methodCandidate(
      String snapshotId,
      String interfaceFqn,
      VerifiedSourceTextDocument document,
      String text,
      JavaDeclarationCatalog.MethodDeclarationView method) {
    SourceExcerptV1 declaration = excerpt(document, text, method.sourceRange());
    String signature =
        interfaceFqn
            + "#"
            + method.name()
            + "("
            + method.parameters().stream()
                .map(JavaDeclarationCatalog.ParameterView::typeText)
                .reduce("", (left, right) -> left.isEmpty() ? right : left + "," + right)
            + ")";
    return new MapperMethodCandidate(
        artifactId(
            "mapper-method-candidate",
            "application-discovery-mapper-method-candidate-id-v2",
            material(snapshotId, "signature", signature, declaration)),
        signature,
        declaration);
  }

  private static MapperStatementCandidate statementCandidate(
      String snapshotId,
      String namespace,
      String statementId,
      String statementKind,
      SourceExcerptV1 declaration) {
    ObjectNode material = material(snapshotId, "namespace", namespace, declaration);
    material.put("statementId", statementId);
    material.put("statementKind", statementKind);
    return new MapperStatementCandidate(
        artifactId(
            "mapper-statement-candidate",
            "application-discovery-mapper-statement-candidate-id-v2",
            material),
        statementId,
        statementKind,
        declaration);
  }

  private static MapperCatalogEntry entry(
      String snapshotId, JavaMapperInterface mapperInterface, XmlMapperResource resource) {
    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("snapshotId", snapshotId);
    material.put("javaInterfaceFqn", mapperInterface.fqn());
    material.put("xmlResourcePath", resource.path());
    material.put("xmlNamespace", resource.namespace());
    ArrayNode methods = material.putArray("javaMethodCandidateIds");
    mapperInterface.methods().forEach(method -> methods.add(method.methodCandidateId().value()));
    ArrayNode statements = material.putArray("xmlStatementCandidateIds");
    resource
        .statements()
        .forEach(statement -> statements.add(statement.statementCandidateId().value()));
    return new MapperCatalogEntry(
        artifactId(
            "mapper-catalog-entry", "application-discovery-mapper-catalog-entry-id-v2", material),
        mapperInterface.fqn(),
        mapperInterface.methods(),
        resource.path(),
        resource.namespace(),
        resource.statements(),
        "CANDIDATE_NOT_YET_BOUND");
  }

  private static MapperCatalogSite supportedSite(String snapshotId, SourceExcerptV1 excerpt) {
    return new MapperCatalogSite(
        siteId(snapshotId, excerpt, SignalDisposition.SUPPORTED, null),
        excerpt,
        SignalDisposition.SUPPORTED,
        null,
        null);
  }

  private static MapperCatalogSite unsupportedSite(
      String snapshotId, SourceExcerptV1 excerpt, String reasonCode) {
    return new MapperCatalogSite(
        siteId(snapshotId, excerpt, SignalDisposition.UNSUPPORTED, reasonCode),
        excerpt,
        SignalDisposition.UNSUPPORTED,
        reasonCode,
        artifactId(
            "gap",
            "application-discovery-mapper-gap-id-v2",
            material(snapshotId, "reasonCode", reasonCode, excerpt)));
  }

  private static MapperCatalogSite rejectedSite(
      String snapshotId, MapperXmlResourceView.RejectedXmlResource rejectedResource) {
    String source = rejectedResource.rawSource();
    int[] end = lineColumn(source, source.length());
    SourceExcerptV1 excerpt =
        excerpt(rejectedResource.document(), source, 0, source.length(), 1, 1, end[0], end[1]);
    return unsupportedSite(
        snapshotId, excerpt, rejectedReasonCode(rejectedResource.rejectionKind()));
  }

  private static String rejectedReasonCode(MapperXmlResourceView.RejectionKind rejectionKind) {
    return switch (rejectionKind) {
      case XML_SECURITY_REJECTED -> "XML_EXTERNAL_RESOLUTION_ATTEMPT";
      case XML_PARSE_REJECTED -> "XML_PARSE_REJECTED";
      case MAPPER_NAMESPACE_MISSING -> "MAPPER_NAMESPACE_MISSING";
    };
  }

  private static ArtifactId siteId(
      String snapshotId,
      SourceExcerptV1 excerpt,
      SignalDisposition disposition,
      String reasonCode) {
    ObjectNode material = material(snapshotId, "disposition", disposition.name(), excerpt);
    if (reasonCode == null) {
      material.putNull("reasonCode");
    } else {
      material.put("reasonCode", reasonCode);
    }
    return artifactId("mapper-site", "application-discovery-mapper-site-id-v2", material);
  }

  private static ObjectNode material(
      String snapshotId, String attributeName, String attributeValue, SourceExcerptV1 excerpt) {
    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("snapshotId", snapshotId);
    material.put(attributeName, attributeValue);
    material.set("excerpt", excerptIdentity(excerpt));
    return material;
  }

  private static ArtifactId artifactId(String prefix, String domain, ObjectNode material) {
    return ArtifactId.parse(
        prefix
            + ":"
            + sha256(
                concatenate(
                    frame(domain),
                    frame(CANONICAL_JSON.encodeCanonical(material).copyToByteArray()))));
  }

  private static SourceExcerptV1 excerpt(
      VerifiedSourceTextDocument document, String source, SourceRange range) {
    int start = range.startOffsetUtf16();
    int end = start + range.lengthUtf16();
    if (end < start || end > source.length()) {
      throw new ApplicationDiscoveryException("MAPPER_CATALOG_REFERENCE_BROKEN");
    }
    int[] begin = lineColumn(source, start);
    int[] finish = lineColumn(source, end);
    int[] last = lineColumn(source, range.lengthUtf16() == 0 ? start : end - 1);
    if (begin[0] != range.startLine() || last[0] != range.endLine()) {
      throw new ApplicationDiscoveryException("MAPPER_CATALOG_REFERENCE_BROKEN");
    }
    return excerpt(document, source, start, end, begin[0], begin[1], finish[0], finish[1]);
  }

  private static SourceExcerptV1 wholeResourceExcerpt(
      VerifiedSourceTextDocument document, String source) {
    int[] end = lineColumn(source, source.length());
    return excerpt(document, source, 0, source.length(), 1, 1, end[0], end[1]);
  }

  private static SourceExcerptV1 excerpt(
      VerifiedSourceTextDocument document,
      String source,
      int characterStart,
      int characterEndExclusive,
      int startLine,
      int startColumn,
      int endLine,
      int endColumn) {
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
            startLine,
            startColumn,
            endLine,
            endColumn),
        bytes,
        Sha256Digest.parse(sha256(bytes.copyToByteArray())));
  }

  private static int[] lineColumn(String source, int offset) {
    int line = 1;
    int lineStart = 0;
    for (int index = 0; index < offset; index++) {
      if (source.charAt(index) == '\n') {
        line++;
        lineStart = index + 1;
      }
    }
    return new int[] {line, offset - lineStart + 1};
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

  private static void requireCatalogInputs(
      ApplicationProfile profile,
      VerifiedSourceTextSet source,
      JavaDeclarationCatalog javaCatalog,
      MapperXmlResourceView mapperResources) {
    if (profile == null || source == null || javaCatalog == null || mapperResources == null) {
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

  private record JavaMapperInterface(
      String fqn, SourceExcerptV1 declarationExcerpt, List<MapperMethodCandidate> methods) {}

  private record XmlMapperResource(
      String path,
      String namespace,
      SourceExcerptV1 namespaceExcerpt,
      List<MapperStatementCandidate> statements) {}

  /** One Step02 mapper statement hint; database variants remain a Step04 DOM concern. */
  private record StatementClue(String namespace, String statementId, String statementKind) {}
}
