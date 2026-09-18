package org.sourceanalysis.app.analysis.discovery;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Run-owned, securely parsed Mapper XML resources from one immutable verified source text set.
 *
 * <p>The retained DOM is read-only by contract. Consumers may derive read projections from it but
 * must clone a node before applying an XML transformation such as MyBatis include expansion.
 */
public final class MapperXmlResourceView {

  private static final String MYBATIS_MAPPER_DTD = "http://mybatis.org/dtd/mybatis-3-mapper.dtd";

  private final VerifiedSourceTextSet frozenSource;
  private final List<MapperXmlResource> mapperResources;
  private final Map<String, MapperXmlResource> resourceByPath;
  private final Map<String, List<MapperXmlResource>> resourcesByNamespace;
  private final Map<String, RejectedXmlResource> rejectedByPath;

  private MapperXmlResourceView(
      VerifiedSourceTextSet frozenSource,
      List<MapperXmlResource> mapperResources,
      Map<String, RejectedXmlResource> rejectedByPath) {
    this.frozenSource = frozenSource;
    this.mapperResources = List.copyOf(mapperResources);
    Map<String, MapperXmlResource> byPath = new LinkedHashMap<>();
    Map<String, List<MapperXmlResource>> byNamespace = new LinkedHashMap<>();
    for (MapperXmlResource resource : this.mapperResources) {
      if (byPath.put(resource.document().path(), resource) != null) {
        throw new IllegalArgumentException("Mapper XML resource paths must be unique");
      }
      byNamespace.computeIfAbsent(resource.namespace(), ignored -> new ArrayList<>()).add(resource);
    }
    this.resourceByPath = Map.copyOf(byPath);
    Map<String, List<MapperXmlResource>> copiedNamespaces = new LinkedHashMap<>();
    byNamespace.forEach(
        (namespace, resources) -> copiedNamespaces.put(namespace, List.copyOf(resources)));
    this.resourcesByNamespace = Map.copyOf(copiedNamespaces);
    this.rejectedByPath = Map.copyOf(rejectedByPath);
  }

  /** Opens the exact frozen XML members once for this run; there is no process-global cache. */
  public static MapperXmlResourceView open(VerifiedSourceTextSet frozenSource) {
    Objects.requireNonNull(frozenSource, "verified frozen source");
    List<MapperXmlResource> mapperResources = new ArrayList<>();
    Map<String, RejectedXmlResource> rejected = new LinkedHashMap<>();
    for (VerifiedSourceTextDocument document :
        frozenSource.documents().stream()
            .filter(candidate -> candidate.path().endsWith(".xml"))
            .sorted(Comparator.comparing(VerifiedSourceTextDocument::path))
            .toList()) {
      String rawSource = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      try {
        Document parsed = parseSecurely(rawSource);
        Element root = parsed.getDocumentElement();
        if (root == null || !"mapper".equals(root.getTagName())) {
          continue;
        }
        String namespace = root.getAttribute("namespace");
        if (namespace.isBlank()) {
          rejected.put(
              document.path(),
              new RejectedXmlResource(document, rawSource, RejectionKind.MAPPER_NAMESPACE_MISSING));
          continue;
        }
        mapperResources.add(new MapperXmlResource(document, rawSource, namespace, parsed));
      } catch (XmlSecurityPolicyException failure) {
        throw new IllegalStateException("XML_SECURITY_POLICY_UNENFORCEABLE", failure);
      } catch (SAXException failure) {
        rejected.put(
            document.path(),
            new RejectedXmlResource(document, rawSource, RejectionKind.XML_SECURITY_REJECTED));
      } catch (ParserConfigurationException | IOException | RuntimeException failure) {
        rejected.put(
            document.path(),
            new RejectedXmlResource(document, rawSource, RejectionKind.XML_PARSE_REJECTED));
      }
    }
    mapperResources.sort(Comparator.comparing(resource -> resource.document().path()));
    return new MapperXmlResourceView(frozenSource, mapperResources, rejected);
  }

  /** Rejects a shared view that was made from a different frozen inventory content. */
  public void requireSameFrozenSource(VerifiedSourceTextSet source) {
    if (!frozenSource.equals(source)) {
      throw new IllegalArgumentException(
          "Mapper XML resource view belongs to a different verified source");
    }
  }

  public List<MapperXmlResource> mapperResources() {
    return mapperResources;
  }

  public Optional<MapperXmlResource> mapperResource(String path) {
    return Optional.ofNullable(resourceByPath.get(path));
  }

  public Optional<RejectedXmlResource> rejectedResource(String path) {
    return Optional.ofNullable(rejectedByPath.get(path));
  }

  public List<RejectedXmlResource> rejectedResources() {
    return rejectedByPath.values().stream()
        .sorted(Comparator.comparing(resource -> resource.document().path()))
        .toList();
  }

  /**
   * Finds all resource variants whose namespace qualifies a static MyBatis reference.
   *
   * <p>The longest matching namespace wins so a dotted fragment id is not misread as part of a
   * shorter namespace.
   */
  public List<MapperXmlResource> resourcesForReference(String currentNamespace, String reference) {
    if (currentNamespace == null
        || currentNamespace.isBlank()
        || reference == null
        || reference.isBlank()
        || reference.contains("${")) {
      return List.of();
    }
    String qualified = reference.contains(".") ? reference : currentNamespace + "." + reference;
    return resourcesByNamespace.entrySet().stream()
        .filter(entry -> qualified.startsWith(entry.getKey() + "."))
        .max(Comparator.comparingInt(entry -> entry.getKey().length()))
        .map(Map.Entry::getValue)
        .orElse(List.of());
  }

  /**
   * One verified mapper resource with its immutable original text and read-only-by-contract DOM.
   */
  public record MapperXmlResource(
      VerifiedSourceTextDocument document,
      String rawSource,
      String namespace,
      Document parsedDocument) {

    public MapperXmlResource {
      Objects.requireNonNull(document, "verified mapper XML document");
      Objects.requireNonNull(rawSource, "verified mapper XML source");
      if (namespace == null || namespace.isBlank()) {
        throw new IllegalArgumentException("Mapper XML namespace is required");
      }
      Objects.requireNonNull(parsedDocument, "secure mapper XML DOM");
    }
  }

  /**
   * A rejected frozen XML member remains identifiable without admitting it as a mapper resource.
   */
  public record RejectedXmlResource(
      VerifiedSourceTextDocument document, String rawSource, RejectionKind rejectionKind) {

    public RejectedXmlResource {
      Objects.requireNonNull(document, "rejected XML document");
      Objects.requireNonNull(rawSource, "rejected XML source");
      Objects.requireNonNull(rejectionKind, "XML rejection kind");
    }
  }

  public enum RejectionKind {
    XML_SECURITY_REJECTED,
    XML_PARSE_REJECTED,
    MAPPER_NAMESPACE_MISSING
  }

  private static Document parseSecurely(String xml)
      throws ParserConfigurationException, SAXException, IOException, XmlSecurityPolicyException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    try {
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      if (factory.isXIncludeAware() || factory.isExpandEntityReferences()) {
        throw new XmlSecurityPolicyException(
            "Required XML parser controls were not retained.", null);
      }
    } catch (UnsupportedOperationException failure) {
      throw new XmlSecurityPolicyException(
          "Required XML parser controls are unavailable.", failure);
    }
    requireFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
    requireFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
    requireFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
    requireFeature(
        factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    requireAttribute(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
    requireAttribute(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

    DocumentBuilder builder = factory.newDocumentBuilder();
    builder.setEntityResolver(rejectingResolver());
    Document document = builder.parse(new InputSource(new StringReader(xml)));
    rejectUnsafeDom(document);
    return document;
  }

  private static void requireFeature(DocumentBuilderFactory factory, String feature, boolean value)
      throws XmlSecurityPolicyException {
    try {
      factory.setFeature(feature, value);
    } catch (ParserConfigurationException failure) {
      throw new XmlSecurityPolicyException(
          "Required XML feature is unavailable: " + feature, failure);
    }
  }

  private static void requireAttribute(
      DocumentBuilderFactory factory, String attribute, String value)
      throws XmlSecurityPolicyException {
    try {
      factory.setAttribute(attribute, value);
    } catch (IllegalArgumentException failure) {
      throw new XmlSecurityPolicyException(
          "Required XML attribute is unavailable: " + attribute, failure);
    }
  }

  private static EntityResolver rejectingResolver() {
    return (publicId, systemId) -> {
      if (MYBATIS_MAPPER_DTD.equals(systemId)) {
        return new InputSource(new StringReader(""));
      }
      throw new SAXException("External XML entity resolution is rejected.");
    };
  }

  private static void rejectUnsafeDom(Document document) throws SAXException {
    DocumentType documentType = document.getDoctype();
    if (documentType != null) {
      String systemId = documentType.getSystemId();
      if (systemId != null && !MYBATIS_MAPPER_DTD.equals(systemId)) {
        throw new SAXException("External DTD is rejected for frozen Mapper XML.");
      }
      String internalSubset = documentType.getInternalSubset();
      if (internalSubset != null && internalSubset.contains("<!ENTITY")) {
        throw new SAXException("Entity declarations are rejected for frozen Mapper XML.");
      }
    }
    if (document.getElementsByTagNameNS("http://www.w3.org/2001/XInclude", "include").getLength()
            > 0
        || containsEntityReference(document)) {
      throw new SAXException("External XML entity resolution is rejected.");
    }
  }

  private static boolean containsEntityReference(Node node) {
    if (node.getNodeType() == Node.ENTITY_REFERENCE_NODE) {
      return true;
    }
    NodeList children = node.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      if (containsEntityReference(children.item(index))) {
        return true;
      }
    }
    return false;
  }

  private static final class XmlSecurityPolicyException extends Exception {

    private XmlSecurityPolicyException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
