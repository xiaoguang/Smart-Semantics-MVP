package com.linguan.codemd.target.stage02.mappercatalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.linguan.codemd.target.artifacts.ArtifactControls;
import com.linguan.codemd.target.artifacts.ArtifactDescriptor;
import com.linguan.codemd.target.artifacts.ArtifactPolicyKey;
import com.linguan.codemd.target.artifacts.ArtifactReference;
import com.linguan.codemd.target.artifacts.CanonicalArtifactPolicy;
import com.linguan.codemd.target.artifacts.CanonicalArtifactPolicyRegistry;
import com.linguan.codemd.target.artifacts.CanonicalModuleArtifactStore;
import com.linguan.codemd.target.artifacts.CanonicalModulePayload;
import com.linguan.codemd.target.artifacts.CanonicalStageArtifactStore;
import com.linguan.codemd.target.artifacts.ModuleArtifactEnvelope;
import com.linguan.codemd.target.artifacts.ModuleArtifactEnvelopeDraft;
import com.linguan.codemd.target.artifacts.ModuleInstallRequest;
import com.linguan.codemd.target.artifacts.ModulePublicationReference;
import com.linguan.codemd.target.artifacts.ReopenedModulePublication;
import com.linguan.codemd.target.artifacts.ReopenedStagePublication;
import com.linguan.codemd.target.artifacts.StageModuleAddress;
import com.linguan.codemd.target.artifacts.VerifiedCanonicalPayload;
import com.linguan.codemd.target.contracts.SourceExcerptV1;
import com.linguan.codemd.target.contracts.SourceLocatorV1;
import com.linguan.codemd.target.stage01.publish.Stage01Reference;
import com.linguan.codemd.target.stage01.sourceindex.RegisteredSnapshotRegistry;
import com.linguan.codemd.target.stage02.applicationprofile.ApplicationProfileException;
import com.linguan.codemd.target.stage02.applicationprofile.CapabilityDenominator;
import com.linguan.codemd.target.stage02.applicationprofile.CapabilityEvidenceRefV2;
import com.linguan.codemd.target.stage02.applicationprofile.CapabilityShardReceipt;
import com.linguan.codemd.target.stage02.applicationprofile.CapabilitySiteKind;
import com.linguan.codemd.target.stage02.applicationprofile.CapabilitySiteV2;
import com.linguan.codemd.target.stage02.applicationprofile.SignalDisposition;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Catalogs frozen MyBatis Java and XML candidates. It deliberately does not claim a Java method
 * to XML statement binding: that proof belongs to Stage03.
 */
public final class MapperCapabilityCataloger {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ArtifactPolicyKey M1 = new ArtifactPolicyKey(
            "STAGE02_APPLICATION_PROFILE_DRAFT", "stage02-application-profile-draft-v2");
    private static final ArtifactPolicyKey M3 = new ArtifactPolicyKey(
            "STAGE02_MAPPER_CATALOG_DRAFT", "stage02-mapper-catalog-draft-v2");
    private static final Set<String> SQL_STATEMENTS = Set.of("select", "insert", "update", "delete");
    private static final Pattern EXTERNAL_ENTITY = Pattern.compile("<!ENTITY\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROOT_MAPPER = Pattern.compile("(?s)<mapper\\b[^>]*>");

    private final CanonicalStageArtifactStore stages;
    private final CanonicalModuleArtifactStore modules;
    private final CanonicalArtifactPolicyRegistry policies;
    private final RegisteredSnapshotRegistry snapshots;

    public MapperCapabilityCataloger(
            CanonicalStageArtifactStore stages,
            CanonicalModuleArtifactStore modules,
            CanonicalArtifactPolicyRegistry policies,
            RegisteredSnapshotRegistry snapshots) {
        if (stages == null || modules == null || policies == null || snapshots == null) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "M3 dependencies are required");
        }
        this.stages = stages;
        this.modules = modules;
        this.policies = policies;
        this.snapshots = snapshots;
    }

    /** Reopens M1 and Stage01, catalogs candidate Java/XML mapper material, and persists M3. */
    public MapperCatalogDiscovery catalog(
            Stage01Reference stage01,
            ModulePublicationReference profileDraftReference,
            MapperCatalogProfile profile) {
        if (stage01 == null || profileDraftReference == null || profile == null) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "M3 inputs are required");
        }
        ReopenedStagePublication stage = stages.reopen(stage01.stagePublicationReference());
        Map<String, VerifiedCanonicalPayload> stagePayloads = byName(stage.semanticPayloads());
        ObjectNode sourceInput = object(stagePayloads.get("source-input.json").canonicalUtf8().copyToByteArray());
        ObjectNode snapshot = object(stagePayloads.get("verified-snapshot.json").canonicalUtf8().copyToByteArray());
        ProfileDraft profileDraft = reopenProfile(profileDraftReference, stage.receipt().controls());
        List<String> mapperPrefixes = mapperPrefixes(profileDraft.payload(), snapshot);
        RegisteredSnapshotRegistry.RegisteredSnapshotHandle handle = snapshots.resolve(sourceInput.path("sourceRegistrationId").asText());
        if (handle == null || !snapshot.path("snapshotId").asText().equals(handle.snapshotId())) {
            throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "frozen source registration changed");
        }

        DiscoveryMaterial material = scan(
                handle,
                inventory(stagePayloads.get("source-inventory.jsonl").canonicalUtf8().copyToByteArray()),
                mapperPrefixes,
                profile);
        ModulePublicationReference publication = persist(stage01, stage, profileDraft, material);
        return new MapperCatalogDiscovery(
                profileDraft.payload().path("applicationProfileId").asText(),
                material.entries(),
                material.sites(),
                material.shards(),
                material.denominator(),
                publication);
    }

    private DiscoveryMaterial scan(
            RegisteredSnapshotRegistry.RegisteredSnapshotHandle handle,
            List<Inventory> inventory,
            List<String> mapperPrefixes,
            MapperCatalogProfile profile) {
        Map<String, JavaMapper> javaMappers = new TreeMap<>();
        int javaFiles = 0;
        for (Inventory file : inventory) {
            if (!file.isAnalyzableJava()) {
                continue;
            }
            if (++javaFiles > profile.maxJavaFiles()) {
                throw new ApplicationProfileException("STAGE02_RESOURCE_LIMIT_EXCEEDED", "Java mapper-file budget exceeded");
            }
            byte[] bytes = readFrozen(handle, file);
            JavaMapper mapper = parseJavaMapper(file, new String(bytes, StandardCharsets.UTF_8));
            if (mapper != null && javaMappers.putIfAbsent(mapper.fqn(), mapper) != null) {
                throw new ApplicationProfileException("MAPPER_CATALOG_IDENTITY_CONFLICT", "duplicate Mapper interface FQN");
            }
        }

        List<MapperCatalogEntry> entries = new ArrayList<>();
        List<CapabilitySiteV2> sites = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        int xmlFiles = 0;
        for (Inventory file : inventory) {
            if (!file.isAnalyzableXml() || !matchesMapperLocation(file.path(), mapperPrefixes)) {
                continue;
            }
            if (++xmlFiles > profile.maxXmlFiles()) {
                throw new ApplicationProfileException("STAGE02_RESOURCE_LIMIT_EXCEEDED", "XML mapper-file budget exceeded");
            }
            byte[] bytes = readFrozen(handle, file);
            XmlMapper xml = parseXmlMapper(file, new String(bytes, StandardCharsets.UTF_8));
            JavaMapper javaMapper = javaMappers.get(xml.namespace());
            if (javaMapper == null) {
                String reason = "MAPPER_JAVA_INTERFACE_NOT_CATALOGED";
                CapabilitySiteV2 site = gapSite(xml.rootExcerpt(), reason);
                sites.add(site);
                gaps.add(gapId(site.siteId(), reason));
                continue;
            }
            MapperCatalogEntry entry = new MapperCatalogEntry(
                    catalogEntryId(javaMapper.fileId(), file.fileId(), xml.namespace()),
                    javaMapper.fqn(),
                    javaMapper.methodSignatures(),
                    file.path(),
                    xml.namespace(),
                    xml.statementIds(),
                    "CANDIDATE_NOT_YET_BOUND");
            entries.add(entry);
            sites.add(supportedSite(xml.rootExcerpt()));
        }
        entries.sort(Comparator.comparing(MapperCatalogEntry::catalogEntryId));
        sites.sort(Comparator.comparing(CapabilitySiteV2::siteId));
        gaps.sort(String::compareTo);
        List<String> siteIds = sites.stream().map(CapabilitySiteV2::siteId).toList();
        String shardId = "mapper-shard:" + digest("stage02-mapper-catalog-shard-v2", String.join("\n", siteIds));
        List<CapabilityShardReceipt> shards = List.of(new CapabilityShardReceipt(
                shardId,
                siteIds,
                siteIds,
                gaps.isEmpty() ? "SUCCEEDED" : "SUCCEEDED_WITH_GAPS",
                gaps));
        return new DiscoveryMaterial(
                List.copyOf(entries),
                List.copyOf(sites),
                shards,
                denominator(sites),
                List.copyOf(gaps));
    }

    private ModulePublicationReference persist(
            Stage01Reference stage01,
            ReopenedStagePublication stage,
            ProfileDraft profile,
            DiscoveryMaterial material) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("applicationProfileId", profile.payload().path("applicationProfileId").asText());
        ArrayNode entries = payload.putArray("catalogEntries");
        material.entries().forEach(entry -> entries.add(entryNode(entry)));
        ArrayNode sites = payload.putArray("sites");
        material.sites().forEach(site -> sites.add(siteNode(site)));
        ArrayNode shards = payload.putArray("shardReceipts");
        material.shards().forEach(shard -> shards.add(shardNode(shard)));
        ObjectNode denominator = payload.putObject("denominator");
        denominator.put("candidateSites", material.denominator().candidateSites());
        denominator.put("supported", material.denominator().supported());
        denominator.put("unsupported", material.denominator().unsupported());
        denominator.put("ambiguous", material.denominator().ambiguous());
        denominator.put("overLimit", material.denominator().overLimit());

        Map<String, VerifiedCanonicalPayload> source = byName(stage.semanticPayloads());
        List<ArtifactReference> upstream = List.of(
                        profile.payloadReference(),
                        referenceOf(source.get("source-inventory.jsonl")),
                        referenceOf(source.get("verified-snapshot.json")))
                .stream()
                .sorted(Comparator.comparing(ArtifactReference::artifactId))
                .toList();
        CanonicalArtifactPolicy policy = policies.resolve(M3);
        StageModuleAddress address = new StageModuleAddress(
                stage01.stagePublicationReference().address().runId(),
                2,
                "discover-application-and-entries",
                3,
                "mapper-catalog");
        String status = material.gapIds().isEmpty() ? "SUCCEEDED" : "SUCCEEDED_WITH_GAPS";
        ModuleArtifactEnvelope envelope = ModuleArtifactEnvelope.write(
                new ModuleArtifactEnvelopeDraft(
                        address,
                        "v2",
                        upstream,
                        stage.receipt().controls(),
                        status,
                        material.gapIds(),
                        payload),
                policy);
        return modules
                .install(new ModuleInstallRequest(
                        address,
                        "v2",
                        upstream,
                        stage.receipt().controls(),
                        status,
                        material.gapIds(),
                        List.of(new CanonicalModulePayload(
                                "mapper-catalog-draft.json",
                                policy.key().artifactType(),
                                policy.key().schemaVersion(),
                                envelope.reference().artifactId(),
                                policy.mediaType(),
                                envelope.canonicalUtf8()))))
                .reference();
    }

    private ProfileDraft reopenProfile(ModulePublicationReference reference, ArtifactControls controls) {
        ReopenedModulePublication publication = modules.reopen(reference);
        if (publication.payloads().size() != 1 || !publication.receipt().controls().equals(controls)) {
            throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "M1 publication is not reusable");
        }
        VerifiedCanonicalPayload payload = publication.payloads().get(0);
        return new ProfileDraft(
                ModuleArtifactEnvelope.parse(payload.canonicalUtf8(), policies.resolve(M1)).payload().deepCopy(),
                referenceOf(payload));
    }

    private static List<String> mapperPrefixes(ObjectNode profile, ObjectNode snapshot) {
        boolean myBatis = false;
        for (JsonNode signal : profile.path("frameworkSignals")) {
            if ("MYBATIS".equals(signal.path("kind").asText())
                    && "SUPPORTED".equals(signal.path("disposition").asText())) {
                myBatis = true;
            }
        }
        if (!myBatis || !"COMPLETE_CAPTURE".equals(snapshot.path("inventoryScope").path("kind").asText())) {
            throw new ApplicationProfileException("APPLICATION_PROFILE_UNRESOLVED", "M1 does not permit MyBatis discovery");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode signal : profile.path("configSignals")) {
            if (!"MYBATIS_MAPPER_LOCATION".equals(signal.path("kind").asText())
                    || !"SUPPORTED".equals(signal.path("disposition").asText())) {
                continue;
            }
            String location = signal.path("value").asText();
            String normalized = location.replaceFirst("^classpath\\*?:", "");
            int wildcard = normalized.indexOf('*');
            String prefix = (wildcard < 0 ? normalized : normalized.substring(0, wildcard)).replaceAll("^/+", "");
            if (!prefix.isBlank()) {
                values.add(prefix);
            }
        }
        if (values.isEmpty()) {
            throw new ApplicationProfileException("APPLICATION_PROFILE_UNRESOLVED", "no static MyBatis mapper location is available");
        }
        return values.stream().distinct().sorted().toList();
    }

    private static boolean matchesMapperLocation(String path, List<String> prefixes) {
        return prefixes.stream().anyMatch(prefix -> path.startsWith(prefix) || path.contains("/" + prefix));
    }

    private static JavaMapper parseJavaMapper(Inventory file, String source) {
        CompilationUnit unit = new JavaParser()
                .parse(source)
                .getResult()
                .orElseThrow(() -> new ApplicationProfileException(
                        "MAPPER_CATALOG_PARSE_FAILED", "frozen Java mapper source cannot parse"));
        for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            boolean mapper = type.getAnnotations().stream()
                    .anyMatch(annotation -> annotation.getNameAsString().endsWith("Mapper"));
            if (!mapper || !type.isInterface()) {
                continue;
            }
            String fqn = type.getFullyQualifiedName().orElse(type.getNameAsString());
            List<String> methods = type.getMethods().stream()
                    .map(MapperCapabilityCataloger::signature)
                    .distinct()
                    .sorted()
                    .toList();
            if (!methods.isEmpty()) {
                return new JavaMapper(file.fileId(), fqn, methods);
            }
        }
        return null;
    }

    private static String signature(MethodDeclaration method) {
        return method.getNameAsString()
                + "("
                + method.getParameters().stream()
                        .map(parameter -> parameter.getType().asString())
                        .collect(java.util.stream.Collectors.joining(","))
                + ")";
    }

    private static XmlMapper parseXmlMapper(Inventory file, String source) {
        if (EXTERNAL_ENTITY.matcher(source).find()) {
            throw new ApplicationProfileException("XML_EXTERNAL_RESOLUTION_ATTEMPT", "XML entity declaration is forbidden");
        }
        Document document = parseSecureXml(source);
        Element mapper = document.getDocumentElement();
        if (mapper == null || !"mapper".equals(mapper.getTagName()) || mapper.getAttribute("namespace").isBlank()) {
            throw new ApplicationProfileException("MAPPER_CATALOG_PARSE_FAILED", "Mapper XML root is invalid");
        }
        List<String> statements = new ArrayList<>();
        NodeList children = mapper.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element statement && SQL_STATEMENTS.contains(statement.getTagName())) {
                String id = statement.getAttribute("id");
                if (!id.isBlank()) {
                    statements.add(id);
                }
            }
        }
        if (statements.isEmpty()) {
            throw new ApplicationProfileException("MAPPER_CATALOG_PARSE_FAILED", "Mapper XML has no static statement IDs");
        }
        return new XmlMapper(mapper.getAttribute("namespace"), rootExcerpt(file, source), statements.stream().distinct().sorted().toList());
    }

    private static Document parseSecureXml(String source) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
        } catch (ParserConfigurationException | IllegalArgumentException unavailable) {
            throw new ApplicationProfileException(
                    "XML_SECURITY_POLICY_UNENFORCEABLE", "secure XML settings are unavailable", unavailable);
        }
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            return builder.parse(new InputSource(new StringReader(source)));
        } catch (ParserConfigurationException unavailable) {
            throw new ApplicationProfileException(
                    "XML_SECURITY_POLICY_UNENFORCEABLE", "secure XML builder is unavailable", unavailable);
        } catch (SAXException | IOException invalid) {
            throw new ApplicationProfileException("MAPPER_CATALOG_PARSE_FAILED", "Mapper XML cannot parse", invalid);
        }
    }

    private static SourceExcerptV1 rootExcerpt(Inventory file, String source) {
        Matcher matcher = ROOT_MAPPER.matcher(source);
        if (!matcher.find()) {
            throw new ApplicationProfileException("MAPPER_CATALOG_PARSE_FAILED", "Mapper root source span is unavailable");
        }
        int start = matcher.start();
        int end = matcher.end();
        Position begin = positionAt(source, start);
        Position finish = positionAt(source, end);
        String raw = source.substring(start, end);
        return new SourceExcerptV1(
                new SourceLocatorV1(
                        file.fileId(),
                        file.path(),
                        byteOffset(source, start),
                        byteOffset(source, end),
                        begin.line(),
                        begin.column(),
                        finish.line(),
                        finish.column()),
                raw,
                sha256(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private static CapabilitySiteV2 supportedSite(SourceExcerptV1 root) {
        return new CapabilitySiteV2(
                siteId(root.locator()),
                CapabilitySiteKind.MAPPER_RESOURCE_DECLARATION,
                root.locator(),
                List.of(),
                SignalDisposition.SUPPORTED,
                null,
                List.of(CapabilityEvidenceRefV2.source(root)));
    }

    private static CapabilitySiteV2 gapSite(SourceExcerptV1 root, String reason) {
        return new CapabilitySiteV2(
                siteId(root.locator()),
                CapabilitySiteKind.MAPPER_RESOURCE_DECLARATION,
                root.locator(),
                List.of(),
                SignalDisposition.AMBIGUOUS,
                reason,
                List.of(CapabilityEvidenceRefV2.source(root)));
    }

    private static CapabilityDenominator denominator(List<CapabilitySiteV2> sites) {
        int supported = 0, unsupported = 0, ambiguous = 0, overLimit = 0;
        for (CapabilitySiteV2 site : sites) {
            switch (site.disposition()) {
                case SUPPORTED -> supported++;
                case UNSUPPORTED -> unsupported++;
                case AMBIGUOUS -> ambiguous++;
                case OVER_LIMIT -> overLimit++;
            }
        }
        return new CapabilityDenominator(sites.size(), supported, unsupported, ambiguous, overLimit);
    }

    private static byte[] readFrozen(RegisteredSnapshotRegistry.RegisteredSnapshotHandle handle, Inventory file) {
        byte[] bytes = handle.read(file.fileId()).copyToByteArray();
        if (bytes.length != file.sizeBytes() || !sha256(bytes).equals(file.sha256())) {
            throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "frozen source bytes changed");
        }
        return bytes;
    }

    private static ObjectNode entryNode(MapperCatalogEntry entry) {
        ObjectNode node = JSON.createObjectNode();
        node.put("catalogEntryId", entry.catalogEntryId());
        node.put("javaInterfaceFqn", entry.javaInterfaceFqn());
        strings(node, "javaMethodCandidates", entry.javaMethodCandidates());
        node.put("xmlResourcePath", entry.xmlResourcePath());
        node.put("xmlNamespace", entry.xmlNamespace());
        strings(node, "xmlStatementCandidates", entry.xmlStatementCandidates());
        node.put("bindingState", entry.bindingState());
        return node;
    }

    private static ObjectNode siteNode(CapabilitySiteV2 site) {
        ObjectNode node = JSON.createObjectNode();
        node.put("siteId", site.siteId());
        node.put("kind", site.kind().name());
        node.set("primaryLocator", locatorNode(site.primaryLocator()));
        strings(node, "affectedEntryIds", site.affectedEntryIds());
        node.put("disposition", site.disposition().name());
        if (site.reasonCode() == null) node.putNull("reasonCode"); else node.put("reasonCode", site.reasonCode());
        ArrayNode evidence = node.putArray("evidenceRefs");
        site.evidenceRefs().forEach(reference -> evidence.add(evidenceNode(reference)));
        return node;
    }

    private static ObjectNode evidenceNode(CapabilityEvidenceRefV2 evidence) {
        ObjectNode node = JSON.createObjectNode();
        node.put("kind", evidence.kind().name());
        if (evidence.kind() != CapabilityEvidenceRefV2.Kind.SOURCE_EXCERPT) {
            throw new ApplicationProfileException("MAPPER_CATALOG_INVARIANT_BROKEN", "M3 only writes source evidence");
        }
        node.set("sourceExcerpt", excerptNode(evidence.sourceExcerpt()));
        node.putNull("artifactEvidence");
        return node;
    }

    private static ObjectNode excerptNode(SourceExcerptV1 excerpt) {
        ObjectNode node = JSON.createObjectNode();
        node.set("locator", locatorNode(excerpt.locator()));
        node.put("rawUtf8", excerpt.rawUtf8());
        node.put("rawUtf8Sha256", excerpt.rawUtf8Sha256());
        return node;
    }

    private static ObjectNode locatorNode(SourceLocatorV1 locator) {
        ObjectNode node = JSON.createObjectNode();
        node.put("fileId", locator.fileId());
        node.put("path", locator.path());
        node.put("startByte", locator.startByte());
        node.put("endByteExclusive", locator.endByteExclusive());
        node.put("startLine", locator.startLine());
        node.put("startColumn", locator.startColumn());
        node.put("endLine", locator.endLine());
        node.put("endColumn", locator.endColumn());
        return node;
    }

    private static ObjectNode shardNode(CapabilityShardReceipt shard) {
        ObjectNode node = JSON.createObjectNode();
        node.put("shardId", shard.shardId());
        strings(node, "denominatorSiteIds", shard.denominatorSiteIds());
        strings(node, "dispositionSiteIds", shard.dispositionSiteIds());
        node.put("status", shard.status());
        strings(node, "gapIds", shard.gapIds());
        return node;
    }

    private static void strings(ObjectNode node, String name, List<String> values) {
        ArrayNode array = node.putArray(name);
        values.forEach(array::add);
    }

    private static Map<String, VerifiedCanonicalPayload> byName(List<VerifiedCanonicalPayload> payloads) {
        Map<String, VerifiedCanonicalPayload> values = new HashMap<>();
        for (VerifiedCanonicalPayload payload : payloads) {
            if (values.put(payload.descriptor().fileName(), payload) != null) {
                throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "duplicate Stage01 payload name");
            }
        }
        if (!values.keySet().containsAll(Set.of("source-input.json", "source-inventory.jsonl", "verified-snapshot.json"))) {
            throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "Stage01 payload set is incomplete");
        }
        return Map.copyOf(values);
    }

    private static List<Inventory> inventory(byte[] bytes) {
        List<Inventory> values = new ArrayList<>();
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
            if (line.isEmpty()) continue;
            ObjectNode node = object(line.getBytes(StandardCharsets.UTF_8));
            values.add(new Inventory(
                    node.path("fileId").asText(),
                    node.path("path").asText(),
                    node.path("sizeBytes").asLong(),
                    node.path("sha256").asText(),
                    node.path("analysisDisposition").asText()));
        }
        values.sort(Comparator.comparing(Inventory::fileId));
        return List.copyOf(values);
    }

    private static ObjectNode object(byte[] bytes) {
        try {
            JsonNode node = JSON.readTree(bytes);
            if (node == null || !node.isObject()) throw new IllegalArgumentException("not an object");
            return (ObjectNode) node;
        } catch (Exception invalid) {
            throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "artifact payload is invalid", invalid);
        }
    }

    private static ArtifactReference referenceOf(VerifiedCanonicalPayload payload) {
        ArtifactDescriptor descriptor = payload.descriptor();
        return new ArtifactReference(descriptor.artifactId(), descriptor.sha256());
    }

    private static String catalogEntryId(String javaFileId, String xmlFileId, String namespace) {
        return "mapper-catalog-entry:" + digest("stage02-mapper-catalog-entry-id-v2", javaFileId, xmlFileId, namespace);
    }

    private static String siteId(SourceLocatorV1 locator) {
        return "site:" + digest(
                "stage02-mapper-site-id-v2",
                locator.fileId(),
                Long.toString(locator.startByte()),
                Long.toString(locator.endByteExclusive()));
    }

    private static String gapId(String siteId, String reason) {
        return "gap:" + digest("stage02-mapper-gap-id-v2", siteId, reason);
    }

    private static String digest(String domain, String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            frame(digest, domain.getBytes(StandardCharsets.UTF_8));
            for (String value : values) frame(digest, value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private static void frame(MessageDigest digest, byte[] value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value.length).array());
        digest.update(value);
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private static int byteOffset(String source, int characterOffset) {
        return source.substring(0, characterOffset).getBytes(StandardCharsets.UTF_8).length;
    }

    private static Position positionAt(String source, int characterOffset) {
        int line = 1;
        int column = 1;
        for (int index = 0; index < characterOffset; index++) {
            if (source.charAt(index) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new Position(line, column);
    }

    private record ProfileDraft(ObjectNode payload, ArtifactReference payloadReference) {}

    private record DiscoveryMaterial(
            List<MapperCatalogEntry> entries,
            List<CapabilitySiteV2> sites,
            List<CapabilityShardReceipt> shards,
            CapabilityDenominator denominator,
            List<String> gapIds) {}

    private record Inventory(String fileId, String path, long sizeBytes, String sha256, String disposition) {
        boolean isAnalyzableJava() {
            return path.endsWith(".java") && "ANALYZABLE_TEXT".equals(disposition);
        }

        boolean isAnalyzableXml() {
            return path.endsWith(".xml") && "ANALYZABLE_TEXT".equals(disposition);
        }
    }

    private record JavaMapper(String fileId, String fqn, List<String> methodSignatures) {}

    private record XmlMapper(String namespace, SourceExcerptV1 rootExcerpt, List<String> statementIds) {}

    private record Position(int line, int column) {}
}
