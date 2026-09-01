package com.linguan.codemd.target.stage02.applicationprofile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.JavaParser;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.StringLiteralExpr;
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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Deterministically discovers static Spring MVC routes from persisted artifacts and frozen Java bytes. */
public final class SpringHttpEntryDiscoverer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ArtifactPolicyKey M1 = new ArtifactPolicyKey(
            "STAGE02_APPLICATION_PROFILE_DRAFT", "stage02-application-profile-draft-v2");
    private static final ArtifactPolicyKey M2 = new ArtifactPolicyKey(
            "STAGE02_HTTP_ENTRY_DISCOVERY", "stage02-http-entry-discovery-v2");
    private static final Set<String> CONTROLLERS = Set.of("RestController", "Controller");
    private static final Set<String> MAPPINGS = Set.of("GetMapping", "PostMapping", "RequestMapping");

    private final CanonicalStageArtifactStore stages;
    private final CanonicalModuleArtifactStore modules;
    private final CanonicalArtifactPolicyRegistry policies;
    private final RegisteredSnapshotRegistry snapshots;

    public SpringHttpEntryDiscoverer(
            CanonicalStageArtifactStore stages,
            CanonicalModuleArtifactStore modules,
            CanonicalArtifactPolicyRegistry policies,
            RegisteredSnapshotRegistry snapshots) {
        if (stages == null || modules == null || policies == null || snapshots == null) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "M2 dependencies are required");
        }
        this.stages = stages;
        this.modules = modules;
        this.policies = policies;
        this.snapshots = snapshots;
    }

    /** Reopens M1 and Stage01, closes the site denominator, and immediately persists M2's result. */
    public HttpEntryDiscovery discover(
            Stage01Reference stage01,
            ModulePublicationReference profileDraftReference,
            HttpEntryDiscoveryProfile profile) {
        if (stage01 == null || profileDraftReference == null || profile == null) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "M2 inputs are required");
        }
        ReopenedStagePublication stage = stages.reopen(stage01.stagePublicationReference());
        Map<String, VerifiedCanonicalPayload> stagePayloads = byName(stage.semanticPayloads());
        ObjectNode sourceInput = object(stagePayloads.get("source-input.json").canonicalUtf8().copyToByteArray());
        ObjectNode snapshot = object(stagePayloads.get("verified-snapshot.json").canonicalUtf8().copyToByteArray());
        ProfileDraft profileDraft = reopenProfile(profileDraftReference, stage.receipt().controls());
        requireSpringMvc(profileDraft.payload(), snapshot);

        RegisteredSnapshotRegistry.RegisteredSnapshotHandle handle = snapshots.resolve(sourceInput.path("sourceRegistrationId").asText());
        if (handle == null || !snapshot.path("snapshotId").asText().equals(handle.snapshotId())) {
            throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "frozen source registration changed");
        }
        DiscoveryMaterial material = scan(
                handle,
                inventory(stagePayloads.get("source-inventory.jsonl").canonicalUtf8().copyToByteArray()),
                profile);
        ModulePublicationReference publication = persist(stage01, stage, profileDraft, material);
        return new HttpEntryDiscovery(
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
            HttpEntryDiscoveryProfile profile) {
        List<SiteDecision> decisions = new ArrayList<>();
        Map<String, HttpEntryPoint> entriesById = new TreeMap<>();
        int javaFiles = 0;
        for (Inventory file : inventory) {
            if (!file.isAnalyzableJava()) {
                continue;
            }
            if (++javaFiles > profile.maxJavaFiles()) {
                throw new ApplicationProfileException("STAGE02_RESOURCE_LIMIT_EXCEEDED", "Java file budget exceeded");
            }
            byte[] bytes = handle.read(file.fileId()).copyToByteArray();
            if (bytes.length != file.sizeBytes() || !sha256(bytes).equals(file.sha256())) {
                throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "frozen Java bytes changed");
            }
            SourceText text = new SourceText(bytes);
            CompilationUnit unit = new JavaParser()
                    .parse(text.value())
                    .getResult()
                    .orElseThrow(() -> new ApplicationProfileException(
                            "ENTRY_DISCOVERY_INVARIANT_BROKEN", "frozen Java source cannot parse"));
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (!isController(type)) {
                    continue;
                }
                List<AnnotationExpr> classMappings = mappings(type.getAnnotations());
                RouteValues classRoutes = classMappings.size() == 1
                        ? routes(classMappings.get(0))
                        : classMappings.isEmpty()
                                ? RouteValues.staticValues(List.of(""))
                                : RouteValues.gap("AMBIGUOUS_CLASS_MAPPING_ANNOTATIONS");
                String typeFqn = type.getFullyQualifiedName().orElse(type.getNameAsString());
                for (MethodDeclaration method : type.getMethods()) {
                    List<AnnotationExpr> methodMappings = mappings(method.getAnnotations());
                    for (AnnotationExpr methodMapping : methodMappings) {
                        SourceExcerptV1 primary = sourceExcerpt(file, text, methodMapping);
                        List<SourceExcerptV1> evidence = evidence(file, text, classMappings, primary);
                        RouteValues methodRoutes = routes(methodMapping);
                        String reason = reason(classMappings, classRoutes, methodMappings, methodMapping, methodRoutes);
                        if (reason != null) {
                            decisions.add(SiteDecision.gap(primary, evidence, reason));
                            continue;
                        }
                        List<String> affectedEntries = new ArrayList<>();
                        for (String prefix : classRoutes.paths()) {
                            for (String suffix : methodRoutes.paths()) {
                                String methodName = httpMethod(methodMapping);
                                String route = join(prefix, suffix);
                                String handler = typeFqn + "#" + method.getNameAsString();
                                HttpEntryPoint entry = new HttpEntryPoint(
                                        entryId(file.fileId(), methodName, route, handler),
                                        "SPRING_MVC_HTTP",
                                        "HTTP",
                                        methodName,
                                        route,
                                        parts(prefix, suffix),
                                        handler,
                                        method.getParameters().stream().map(parameter -> parameter.getNameAsString()).toList(),
                                        evidence);
                                HttpEntryPoint prior = entriesById.putIfAbsent(entry.entryId(), entry);
                                if (prior != null && !prior.equals(entry)) {
                                    throw new ApplicationProfileException(
                                            "ENTRY_IDENTITY_CONFLICT", "two HTTP declarations share one identity");
                                }
                                affectedEntries.add(entry.entryId());
                            }
                        }
                        decisions.add(SiteDecision.supported(primary, evidence, affectedEntries));
                    }
                }
            }
        }
        List<CapabilitySiteV2> sites = decisions.stream()
                .map(SiteDecision::toSite)
                .sorted(Comparator.comparing(CapabilitySiteV2::siteId))
                .toList();
        List<String> gaps = decisions.stream()
                .map(SiteDecision::gapId)
                .flatMap(Optional::stream)
                .sorted()
                .toList();
        List<String> siteIds = sites.stream().map(CapabilitySiteV2::siteId).toList();
        String shardId = "entry-shard:" + digest("stage02-http-entry-shard-v2", String.join("\n", siteIds));
        List<CapabilityShardReceipt> shards = List.of(new CapabilityShardReceipt(
                shardId,
                siteIds,
                siteIds,
                gaps.isEmpty() ? "SUCCEEDED" : "SUCCEEDED_WITH_GAPS",
                gaps));
        List<HttpEntryPoint> entries = List.copyOf(entriesById.values());
        if (entries.size() > profile.maxEntries()) {
            throw new ApplicationProfileException("STAGE02_RESOURCE_LIMIT_EXCEEDED", "entry budget exceeded");
        }
        return new DiscoveryMaterial(entries, sites, shards, denominator(sites), gaps);
    }

    private ModulePublicationReference persist(
            Stage01Reference stage01,
            ReopenedStagePublication stage,
            ProfileDraft profile,
            DiscoveryMaterial material) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("applicationProfileId", profile.payload().path("applicationProfileId").asText());
        ArrayNode entries = payload.putArray("entries");
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
        CanonicalArtifactPolicy policy = policies.resolve(M2);
        StageModuleAddress address = new StageModuleAddress(
                stage01.stagePublicationReference().address().runId(),
                2,
                "discover-application-and-entries",
                2,
                "http-entry");
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
                                "http-entry-discovery.json",
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

    private static void requireSpringMvc(ObjectNode profile, ObjectNode snapshot) {
        boolean springMvc = false;
        for (JsonNode signal : profile.path("frameworkSignals")) {
            if ("SPRING_MVC".equals(signal.path("kind").asText())
                    && "SUPPORTED".equals(signal.path("disposition").asText())) {
                springMvc = true;
            }
        }
        if (!springMvc || !"COMPLETE_CAPTURE".equals(snapshot.path("inventoryScope").path("kind").asText())) {
            throw new ApplicationProfileException("APPLICATION_PROFILE_UNRESOLVED", "M1 does not permit Spring MVC discovery");
        }
    }

    private static boolean isController(ClassOrInterfaceDeclaration type) {
        return type.getAnnotations().stream()
                .map(AnnotationExpr::getNameAsString)
                .map(SpringHttpEntryDiscoverer::shortName)
                .anyMatch(CONTROLLERS::contains);
    }

    private static List<AnnotationExpr> mappings(List<AnnotationExpr> annotations) {
        return annotations.stream().filter(annotation -> MAPPINGS.contains(shortName(annotation.getNameAsString()))).toList();
    }

    private static String reason(
            List<AnnotationExpr> classMappings,
            RouteValues classRoutes,
            List<AnnotationExpr> methodMappings,
            AnnotationExpr methodMapping,
            RouteValues methodRoutes) {
        if (methodMappings.size() != 1) return "AMBIGUOUS_METHOD_MAPPING_ANNOTATIONS";
        if (classMappings.size() > 1) return "AMBIGUOUS_CLASS_MAPPING_ANNOTATIONS";
        if (!classRoutes.isStatic()) return "DYNAMIC_CLASS_ROUTE_EXPRESSION";
        if (!methodRoutes.isStatic()) return methodRoutes.reasonCode();
        return httpMethod(methodMapping) == null ? "UNSUPPORTED_HTTP_MAPPING_ANNOTATION" : null;
    }

    private static RouteValues routes(AnnotationExpr annotation) {
        if (annotation.isMarkerAnnotationExpr()) return RouteValues.staticValues(List.of(""));
        List<Expression> values = new ArrayList<>();
        if (annotation.isSingleMemberAnnotationExpr()) {
            values.add(annotation.asSingleMemberAnnotationExpr().getMemberValue());
        } else {
            annotation.asNormalAnnotationExpr().getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals("value") || pair.getNameAsString().equals("path"))
                    .map(pair -> pair.getValue())
                    .forEach(values::add);
        }
        if (values.isEmpty()) return RouteValues.staticValues(List.of(""));
        if (values.size() != 1) return RouteValues.gap("AMBIGUOUS_ROUTE_ALIAS_VALUES");
        Expression value = values.get(0);
        if (value instanceof StringLiteralExpr literal) return RouteValues.staticValues(List.of(literal.asString()));
        if (value instanceof ArrayInitializerExpr array) {
            List<String> routes = new ArrayList<>();
            for (Expression element : array.getValues()) {
                if (!(element instanceof StringLiteralExpr literal)) return RouteValues.gap("DYNAMIC_ROUTE_EXPRESSION");
                routes.add(literal.asString());
            }
            return RouteValues.staticValues(routes);
        }
        return RouteValues.gap("DYNAMIC_ROUTE_EXPRESSION");
    }

    private static String httpMethod(AnnotationExpr annotation) {
        return switch (shortName(annotation.getNameAsString())) {
            case "GetMapping" -> "GET";
            case "PostMapping" -> "POST";
            default -> null;
        };
    }

    private static List<SourceExcerptV1> evidence(
            Inventory file, SourceText text, List<AnnotationExpr> classMappings, SourceExcerptV1 methodExcerpt) {
        List<SourceExcerptV1> values = new ArrayList<>();
        if (classMappings.size() == 1) values.add(sourceExcerpt(file, text, classMappings.get(0)));
        values.add(methodExcerpt);
        return values.stream().sorted(Comparator.comparing(SpringHttpEntryDiscoverer::locatorKey)).toList();
    }

    private static SourceExcerptV1 sourceExcerpt(Inventory file, SourceText text, AnnotationExpr annotation) {
        Range range = annotation.getRange().orElseThrow(() -> new ApplicationProfileException(
                "ENTRY_DISCOVERY_INVARIANT_BROKEN", "annotation source range is absent"));
        int start = text.characterOffset(range.begin.line, range.begin.column);
        int end = text.characterOffset(range.end.line, range.end.column) + 1;
        String raw = text.value().substring(start, end);
        SourceText.Position endPosition = text.positionAt(end);
        return new SourceExcerptV1(
                new SourceLocatorV1(
                        file.fileId(),
                        file.path(),
                        text.byteOffset(start),
                        text.byteOffset(end),
                        range.begin.line,
                        range.begin.column,
                        endPosition.line(),
                        endPosition.column()),
                raw,
                sha256(raw.getBytes(StandardCharsets.UTF_8)));
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

    private static ObjectNode entryNode(HttpEntryPoint entry) {
        ObjectNode node = JSON.createObjectNode();
        node.put("entryId", entry.entryId());
        node.put("kind", entry.kind());
        node.put("protocol", entry.protocol());
        node.put("method", entry.method());
        node.put("route", entry.route());
        array(node, "routeParts", entry.routeParts());
        node.put("handlerFqn", entry.handlerFqn());
        array(node, "parameterNames", entry.parameterNames());
        ArrayNode excerpts = node.putArray("routeSourceExcerpts");
        entry.routeSourceExcerpts().forEach(excerpt -> excerpts.add(excerptNode(excerpt)));
        return node;
    }

    private static ObjectNode siteNode(CapabilitySiteV2 site) {
        ObjectNode node = JSON.createObjectNode();
        node.put("siteId", site.siteId());
        node.put("kind", site.kind().name());
        node.set("primaryLocator", locatorNode(site.primaryLocator()));
        array(node, "affectedEntryIds", site.affectedEntryIds());
        node.put("disposition", site.disposition().name());
        if (site.reasonCode() == null) node.putNull("reasonCode"); else node.put("reasonCode", site.reasonCode());
        ArrayNode evidence = node.putArray("evidenceRefs");
        site.evidenceRefs().forEach(reference -> evidence.add(evidenceNode(reference)));
        return node;
    }

    private static ObjectNode evidenceNode(CapabilityEvidenceRefV2 evidence) {
        ObjectNode node = JSON.createObjectNode();
        node.put("kind", evidence.kind().name());
        if (evidence.kind() == CapabilityEvidenceRefV2.Kind.SOURCE_EXCERPT) {
            node.set("sourceExcerpt", excerptNode(evidence.sourceExcerpt()));
            node.putNull("artifactEvidence");
        } else {
            node.putNull("sourceExcerpt");
            ObjectNode artifact = node.putObject("artifactEvidence");
            artifact.set("artifactRef", referenceNode(evidence.artifactEvidence().artifactRef()));
            artifact.put("artifactType", evidence.artifactEvidence().artifactType());
            artifact.put("schemaVersion", evidence.artifactEvidence().schemaVersion());
        }
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

    private static ObjectNode referenceNode(ArtifactReference reference) {
        ObjectNode node = JSON.createObjectNode();
        node.put("artifactId", reference.artifactId());
        node.put("sha256", reference.sha256());
        return node;
    }

    private static ObjectNode shardNode(CapabilityShardReceipt shard) {
        ObjectNode node = JSON.createObjectNode();
        node.put("shardId", shard.shardId());
        array(node, "denominatorSiteIds", shard.denominatorSiteIds());
        array(node, "dispositionSiteIds", shard.dispositionSiteIds());
        node.put("status", shard.status());
        array(node, "gapIds", shard.gapIds());
        return node;
    }

    private static void array(ObjectNode node, String name, List<String> values) {
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
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\\n")) {
            if (!line.isEmpty()) {
                ObjectNode node = object(line.getBytes(StandardCharsets.UTF_8));
                values.add(new Inventory(
                        node.path("fileId").asText(),
                        node.path("path").asText(),
                        node.path("sizeBytes").asLong(),
                        node.path("sha256").asText(),
                        node.path("analysisDisposition").asText()));
            }
        }
        values.sort(Comparator.comparing(Inventory::fileId));
        return List.copyOf(values);
    }

    private static ObjectNode object(byte[] bytes) {
        try {
            JsonNode node = JSON.readTree(bytes);
            if (node == null || !node.isObject()) throw new IllegalArgumentException("not an object");
            return (ObjectNode) node;
        } catch (Exception exception) {
            throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "artifact payload is invalid", exception);
        }
    }

    private static ArtifactReference referenceOf(VerifiedCanonicalPayload payload) {
        ArtifactDescriptor descriptor = payload.descriptor();
        return new ArtifactReference(descriptor.artifactId(), descriptor.sha256());
    }

    private static String entryId(String fileId, String method, String route, String handler) {
        return "entry:" + digest("stage02-http-entry-id-v2", fileId, method, route, handler);
    }

    private static String siteId(SourceLocatorV1 locator) {
        return "site:" + digest(
                "stage02-http-entry-site-id-v2",
                locator.fileId(),
                Long.toString(locator.startByte()),
                Long.toString(locator.endByteExclusive()));
    }

    private static String gapId(String siteId, String reason) {
        return "gap:" + digest("stage02-http-entry-gap-id-v2", siteId, reason);
    }

    private static String digest(String domain, String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            frame(digest, domain.getBytes(StandardCharsets.UTF_8));
            for (String value : values) frame(digest, value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void frame(MessageDigest digest, byte[] value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value.length).array());
        digest.update(value);
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String shortName(String annotationName) {
        int separator = annotationName.lastIndexOf('.');
        return separator < 0 ? annotationName : annotationName.substring(separator + 1);
    }

    private static String join(String prefix, String suffix) {
        String route = ("/" + prefix + "/" + suffix).replaceAll("/{2,}", "/");
        return route.length() > 1 && route.endsWith("/") ? route.substring(0, route.length() - 1) : route;
    }

    private static List<String> parts(String prefix, String suffix) {
        List<String> parts = new ArrayList<>();
        if (!prefix.isEmpty()) parts.add(prefix);
        if (!suffix.isEmpty()) parts.add(suffix);
        return parts.isEmpty() ? List.of("") : List.copyOf(parts);
    }

    private static String locatorKey(SourceExcerptV1 excerpt) {
        SourceLocatorV1 locator = excerpt.locator();
        return locator.path() + "\n" + locator.startByte() + "\n" + locator.endByteExclusive();
    }

    private record ProfileDraft(ObjectNode payload, ArtifactReference payloadReference) {}

    private record DiscoveryMaterial(
            List<HttpEntryPoint> entries,
            List<CapabilitySiteV2> sites,
            List<CapabilityShardReceipt> shards,
            CapabilityDenominator denominator,
            List<String> gapIds) {}

    private record Inventory(String fileId, String path, long sizeBytes, String sha256, String disposition) {
        boolean isAnalyzableJava() {
            return path.endsWith(".java") && "ANALYZABLE_TEXT".equals(disposition);
        }
    }

    private record RouteValues(List<String> paths, String reasonCode) {
        static RouteValues staticValues(List<String> paths) {
            if (paths.stream().anyMatch(path -> path == null || path.isBlank() && !path.isEmpty())) {
                throw new ApplicationProfileException("ENTRY_ROUTE_INVALID", "static route path is invalid");
            }
            return new RouteValues(List.copyOf(paths), null);
        }

        static RouteValues gap(String reasonCode) {
            return new RouteValues(List.of(), reasonCode);
        }

        boolean isStatic() {
            return reasonCode == null;
        }
    }

    private record SiteDecision(
            String siteId,
            SourceExcerptV1 primary,
            List<SourceExcerptV1> evidence,
            List<String> affectedEntries,
            SignalDisposition disposition,
            String reasonCode) {
        static SiteDecision supported(SourceExcerptV1 primary, List<SourceExcerptV1> evidence, List<String> affectedEntries) {
            return new SiteDecision(
                    SpringHttpEntryDiscoverer.siteId(primary.locator()),
                    primary,
                    evidence,
                    affectedEntries.stream().sorted().toList(),
                    SignalDisposition.SUPPORTED,
                    null);
        }

        static SiteDecision gap(SourceExcerptV1 primary, List<SourceExcerptV1> evidence, String reasonCode) {
            return new SiteDecision(
                    SpringHttpEntryDiscoverer.siteId(primary.locator()),
                    primary,
                    evidence,
                    List.of(),
                    SignalDisposition.AMBIGUOUS,
                    reasonCode);
        }

        CapabilitySiteV2 toSite() {
            return new CapabilitySiteV2(
                    siteId,
                    CapabilitySiteKind.HTTP_ENTRY_DECLARATION,
                    primary.locator(),
                    affectedEntries,
                    disposition,
                    reasonCode,
                    evidence.stream().map(CapabilityEvidenceRefV2::source).toList());
        }

        Optional<String> gapId() {
            return reasonCode == null ? Optional.empty() : Optional.of(SpringHttpEntryDiscoverer.gapId(siteId, reasonCode));
        }
    }

    private static final class SourceText {
        private final String value;
        private final int[] starts;

        SourceText(byte[] bytes) {
            value = new String(bytes, StandardCharsets.UTF_8);
            List<Integer> lineStarts = new ArrayList<>();
            lineStarts.add(0);
            for (int index = 0; index < value.length(); index++) if (value.charAt(index) == '\n') lineStarts.add(index + 1);
            starts = lineStarts.stream().mapToInt(Integer::intValue).toArray();
        }

        String value() {
            return value;
        }

        int characterOffset(int line, int column) {
            if (line < 1 || line > starts.length || column < 1) throw rangeError();
            int offset = starts[line - 1] + column - 1;
            int end = line == starts.length ? value.length() : starts[line] - 1;
            if (offset < starts[line - 1] || offset >= end) throw rangeError();
            return offset;
        }

        int byteOffset(int characterOffset) {
            return value.substring(0, characterOffset).getBytes(StandardCharsets.UTF_8).length;
        }

        Position positionAt(int characterOffset) {
            if (characterOffset < 0 || characterOffset > value.length()) throw rangeError();
            int line = 0;
            while (line + 1 < starts.length && starts[line + 1] <= characterOffset) line++;
            return new Position(line + 1, characterOffset - starts[line] + 1);
        }

        private ApplicationProfileException rangeError() {
            return new ApplicationProfileException("ENTRY_DISCOVERY_INVARIANT_BROKEN", "parser range is outside frozen source");
        }

        private record Position(int line, int column) {}
    }
}
