package com.linguan.codemd.target.stage02.publish;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.target.artifacts.ArtifactControls;
import com.linguan.codemd.target.artifacts.ArtifactPolicyKey;
import com.linguan.codemd.target.artifacts.ArtifactReference;
import com.linguan.codemd.target.artifacts.CanonicalArtifactPolicy;
import com.linguan.codemd.target.artifacts.CanonicalArtifactPolicyRegistry;
import com.linguan.codemd.target.artifacts.CanonicalJsonCodec;
import com.linguan.codemd.target.artifacts.CanonicalModuleArtifactStore;
import com.linguan.codemd.target.artifacts.CanonicalModulePayload;
import com.linguan.codemd.target.artifacts.CanonicalStageArtifactStore;
import com.linguan.codemd.target.artifacts.CanonicalStagePayload;
import com.linguan.codemd.target.artifacts.ImmutableBytes;
import com.linguan.codemd.target.artifacts.ModuleArtifactEnvelope;
import com.linguan.codemd.target.artifacts.ModuleInstallRequest;
import com.linguan.codemd.target.artifacts.ModulePublicationReference;
import com.linguan.codemd.target.artifacts.ReopenedModulePublication;
import com.linguan.codemd.target.artifacts.ReopenedStagePublication;
import com.linguan.codemd.target.artifacts.StageInstallRequest;
import com.linguan.codemd.target.artifacts.StageModuleAddress;
import com.linguan.codemd.target.artifacts.StagePublisherModuleProvenance;
import com.linguan.codemd.target.artifacts.VerifiedCanonicalPayload;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stage02 M4. Reopens M1–M3, verifies their common Stage01 basis and publishes exactly the four
 * Stage02 semantic artifacts. It does not parse source bytes or invent an entry, catalog binding,
 * or discovery disposition.
 */
public final class Stage02PublicationSpecifier {
    private static final ObjectMapper JSON = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private static final CanonicalJsonCodec CANONICAL_JSON = new CanonicalJsonCodec();
    private static final ArtifactPolicyKey M1 = new ArtifactPolicyKey(
            "STAGE02_APPLICATION_PROFILE_DRAFT", "stage02-application-profile-draft-v2");
    private static final ArtifactPolicyKey M2 =
            new ArtifactPolicyKey("STAGE02_HTTP_ENTRY_DISCOVERY", "stage02-http-entry-discovery-v2");
    private static final ArtifactPolicyKey M3 = new ArtifactPolicyKey(
            "STAGE02_MAPPER_CATALOG_DRAFT", "stage02-mapper-catalog-draft-v2");
    private static final ArtifactPolicyKey PROFILE =
            new ArtifactPolicyKey("STAGE02_APPLICATION_PROFILE", "stage02-application-profile-v2");
    private static final ArtifactPolicyKey ENTRIES =
            new ArtifactPolicyKey("STAGE02_ENTRY_POINTS", "stage02-entry-points-v2");
    private static final ArtifactPolicyKey CATALOG =
            new ArtifactPolicyKey("STAGE02_MAPPER_CATALOG", "stage02-mapper-catalog-v2");
    private static final ArtifactPolicyKey REPORT =
            new ArtifactPolicyKey("STAGE02_CAPABILITY_REPORT", "stage02-capability-report-v2");
    private static final Set<String> STAGE01_FILES =
            Set.of("source-input.json", "source-inventory.jsonl", "verified-snapshot.json");

    /** Publishes M4 and the receipt-last Stage02 semantic set from fresh persisted upstream artifacts. */
    public Stage02Reference publish(
            Stage02PublicationSpecificationInput input,
            CanonicalArtifactPolicyRegistry policies,
            CanonicalModuleArtifactStore moduleStore,
            CanonicalStageArtifactStore stageStore) {
        if (input == null || policies == null || moduleStore == null || stageStore == null) {
            throw new Stage02PublicationException("STAGE02_REQUEST_INVALID", "M4 dependencies are required");
        }
        ReopenedStagePublication stage01 = stageStore.reopen(input.frozenSource().stagePublicationReference());
        requireStage01(stage01, input.destination().runId());
        Map<String, VerifiedCanonicalPayload> stage01Payloads = byFileName(stage01.semanticPayloads(), STAGE01_FILES, "Stage01");
        ModuleMaterial m1 = reopen(moduleStore, input.applicationProfilePublication(), policies.resolve(M1), 1, "application-profile");
        ModuleMaterial m2 = reopen(moduleStore, input.httpEntryPublication(), policies.resolve(M2), 2, "http-entry");
        ModuleMaterial m3 = reopen(moduleStore, input.mapperCatalogPublication(), policies.resolve(M3), 3, "mapper-catalog");
        requireCommonBasis(stage01.receipt().controls(), m1, m2, m3);

        String profileId = requiredText(m1.payload(), "applicationProfileId", "M1 profile");
        if (!profileId.equals(requiredText(m2.payload(), "applicationProfileId", "M2 discovery"))
                || !profileId.equals(requiredText(m3.payload(), "applicationProfileId", "M3 catalog"))) {
            throw new Stage02PublicationException(
                    "CAPABILITY_ACCOUNTING_BROKEN", "M1–M3 application profile identities differ");
        }
        DiscoveryPart http = discoveryPart(m2.payload(), "entries", "entryId", "M2 HTTP discovery");
        DiscoveryPart mapper = discoveryPart(m3.payload(), "catalogEntries", "catalogEntryId", "M3 Mapper catalog");
        List<String> sourceFileIds = sourceFileIds(stage01Payloads.get("source-inventory.jsonl"));
        RepositoryCoverage coverage = coverage(profileId, sourceFileIds, http, mapper);
        List<String> gaps = gaps(m1, m2, m3, coverage);
        String status = gaps.isEmpty() ? "SUCCEEDED" : "SUCCEEDED_WITH_GAPS";

        List<Payload> payloads = List.of(
                standalone("application-profile.json", policies.resolve(PROFILE), m1.payload().deepCopy()),
                jsonl("entry-points.jsonl", policies.resolve(ENTRIES), http.entries()),
                jsonl("mapper-catalog.jsonl", policies.resolve(CATALOG), mapper.entries()),
                standalone("capability-report.json", policies.resolve(REPORT), report(profileId, http, mapper, coverage, gaps)));
        List<ArtifactReference> upstream = List.of(m1.payloadReference(), m2.payloadReference(), m3.payloadReference()).stream()
                .sorted(Comparator.comparing(ArtifactReference::artifactId))
                .toList();
        StageModuleAddress publisherAddress = new StageModuleAddress(
                input.destination().runId(), 2, "discover-application-and-entries", 4, "publish");
        ModulePublicationReference publisher = moduleStore
                .install(new ModuleInstallRequest(
                        publisherAddress,
                        "v2",
                        upstream,
                        stage01.receipt().controls(),
                        status,
                        gaps,
                        payloads.stream().map(Payload::modulePayload).toList()))
                .reference();
        ReopenedModulePublication reopenedPublisher = moduleStore.reopen(publisher);
        if (reopenedPublisher.payloads().size() != 4) {
            throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", "M4 must install exactly four payloads");
        }
        var stage = stageStore
                .install(new StageInstallRequest(
                        input.destination(),
                        new StagePublisherModuleProvenance(publisher),
                        List.of(input.frozenSource().stagePublicationReference()),
                        stage01.receipt().controls(),
                        status,
                        gaps,
                        payloads.stream().map(Payload::stagePayload).toList(),
                        null))
                .reference();
        stageStore.reopen(stage);
        return new Stage02Reference(publisher, stage);
    }

    private static ModuleMaterial reopen(
            CanonicalModuleArtifactStore store,
            ModulePublicationReference reference,
            CanonicalArtifactPolicy policy,
            int moduleNumber,
            String moduleKey) {
        ReopenedModulePublication publication = store.reopen(reference);
        if (!(publication.reference().address() instanceof StageModuleAddress address)
                || address.stageNumber() != 2
                || !"discover-application-and-entries".equals(address.stageKey())
                || address.moduleNumber() != moduleNumber
                || !moduleKey.equals(address.moduleKey())
                || publication.payloads().size() != 1) {
            throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", "Stage02 draft module address or payload count is invalid");
        }
        VerifiedCanonicalPayload payload = publication.payloads().get(0);
        ModuleArtifactEnvelope envelope = ModuleArtifactEnvelope.parse(payload.canonicalUtf8(), policy);
        if (!envelope.reference().artifactId().equals(payload.descriptor().artifactId())
                || !envelope.reference().sha256().equals(payload.descriptor().sha256())
                || !publication.receipt().controls().equals(envelope.draft().controls())
                || !publication.receipt().status().equals(envelope.draft().status())
                || !publication.receipt().gapRefs().equals(envelope.draft().gapRefs())) {
            throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", "Stage02 draft receipt and envelope differ");
        }
        return new ModuleMaterial(publication, (ObjectNode) envelope.payload(), new ArtifactReference(
                payload.descriptor().artifactId(), payload.descriptor().sha256()));
    }

    private static void requireStage01(ReopenedStagePublication stage01, String expectedRunId) {
        if (stage01.reference().address().stageNumber() != 1
                || !"freeze-source".equals(stage01.reference().address().stageKey())
                || !expectedRunId.equals(stage01.reference().address().runId())
                || !"SUCCEEDED".equals(stage01.receipt().status())) {
            throw new Stage02PublicationException("SNAPSHOT_REOPEN_MISMATCH", "Stage01 publication cannot be an M4 basis");
        }
    }

    private static void requireCommonBasis(ArtifactControls controls, ModuleMaterial... modules) {
        for (ModuleMaterial module : modules) {
            if (!controls.equals(module.publication().receipt().controls())) {
                throw new Stage02PublicationException("SNAPSHOT_REOPEN_MISMATCH", "Stage02 modules do not share Stage01 controls");
            }
        }
    }

    private static DiscoveryPart discoveryPart(
            ObjectNode payload, String entriesField, String entryIdentifier, String subject) {
        ArrayNode entries = requiredArray(payload, entriesField, subject);
        ArrayNode sites = requiredArray(payload, "sites", subject);
        ArrayNode shards = requiredArray(payload, "shardReceipts", subject);
        ObjectNode denominator = requiredObject(payload, "denominator", subject);
        List<ObjectNode> entryNodes = sortedObjects(entries, entryIdentifier, subject + " entries");
        List<ObjectNode> siteNodes = sortedObjects(sites, "siteId", subject + " sites");
        List<ObjectNode> shardNodes = sortedObjects(shards, "shardId", subject + " shards");
        List<String> siteIds = siteNodes.stream().map(node -> requiredText(node, "siteId", subject)).toList();
        requireDenominator(denominator, siteNodes, subject);
        Set<String> shardDenominator = new LinkedHashSet<>();
        Set<String> shardDisposition = new LinkedHashSet<>();
        for (ObjectNode shard : shardNodes) {
            List<String> denominatorIds = sortedTexts(requiredArray(shard, "denominatorSiteIds", subject), subject + " shard denominator");
            List<String> dispositionIds = sortedTexts(requiredArray(shard, "dispositionSiteIds", subject), subject + " shard disposition");
            boolean duplicateDenominator = !denominatorIds.isEmpty() && !shardDenominator.addAll(denominatorIds);
            boolean duplicateDisposition = !dispositionIds.isEmpty() && !shardDisposition.addAll(dispositionIds);
            if (!denominatorIds.equals(dispositionIds) || duplicateDenominator || duplicateDisposition) {
                throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", subject + " shard accounting is invalid");
            }
        }
        if (!shardDenominator.equals(new LinkedHashSet<>(siteIds)) || !shardDisposition.equals(new LinkedHashSet<>(siteIds))) {
            throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", subject + " shard union differs from site denominator");
        }
        return new DiscoveryPart(entryNodes, entryIdentifier, siteNodes, shardNodes);
    }

    private static void requireDenominator(ObjectNode denominator, List<ObjectNode> sites, String subject) {
        int supported = 0;
        int unsupported = 0;
        int ambiguous = 0;
        int overLimit = 0;
        for (ObjectNode site : sites) {
            String disposition = requiredText(site, "disposition", subject + " site");
            switch (disposition) {
                case "SUPPORTED" -> supported++;
                case "UNSUPPORTED" -> unsupported++;
                case "AMBIGUOUS" -> ambiguous++;
                case "OVER_LIMIT" -> overLimit++;
                default -> throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", subject + " has an unknown disposition");
            }
        }
        if (integer(denominator, "candidateSites", subject) != sites.size()
                || integer(denominator, "supported", subject) != supported
                || integer(denominator, "unsupported", subject) != unsupported
                || integer(denominator, "ambiguous", subject) != ambiguous
                || integer(denominator, "overLimit", subject) != overLimit) {
            throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", subject + " denominator differs from sites");
        }
    }

    private static RepositoryCoverage coverage(
            String profileId, List<String> sourceFileIds, DiscoveryPart http, DiscoveryPart mapper) {
        List<String> entryIds = http.entries().stream()
                .map(node -> requiredText(node, http.entryIdentifier(), "HTTP entry"))
                .toList();
        Set<String> declaredEntryIds = new LinkedHashSet<>();
        for (ObjectNode site : http.sites()) {
            for (String entryId : sortedTexts(requiredArray(site, "affectedEntryIds", "HTTP site"), "HTTP site affected entries")) {
                if (!declaredEntryIds.add(entryId)) {
                    throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", "an HTTP entry has multiple site dispositions");
                }
            }
        }
        if (!declaredEntryIds.equals(new LinkedHashSet<>(entryIds))) {
            throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", "HTTP entry inventory differs from site dispositions");
        }
        List<String> discoverySites = concatIds(http.sites(), mapper.sites(), "siteId");
        List<String> shardIds = concatIds(http.shards(), mapper.shards(), "shardId");
        List<String> gapped = http.sites().stream()
                .filter(site -> !"SUPPORTED".equals(requiredText(site, "disposition", "HTTP site")))
                .flatMap(site -> sortedTexts(requiredArray(site, "affectedEntryIds", "HTTP site"), "HTTP site affected entries").stream())
                .distinct()
                .sorted()
                .toList();
        return new RepositoryCoverage(
                profileId,
                sourceFileIds,
                discoverySites,
                entryIds,
                entryIds,
                gapped,
                List.of(),
                shardIds,
                true);
    }

    private static List<String> concatIds(List<ObjectNode> first, List<ObjectNode> second, String field) {
        List<String> values = new ArrayList<>();
        first.forEach(node -> values.add(requiredText(node, field, "Stage02 record")));
        second.forEach(node -> values.add(requiredText(node, field, "Stage02 record")));
        values.sort(String::compareTo);
        for (int index = 1; index < values.size(); index++) {
            if (values.get(index - 1).equals(values.get(index))) {
                throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", "Stage02 identifier is duplicated");
            }
        }
        return List.copyOf(values);
    }

    private static List<String> sourceFileIds(VerifiedCanonicalPayload inventory) {
        String text = new String(inventory.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8);
        if (text.isEmpty() || !text.endsWith("\n")) {
            throw new Stage02PublicationException("SNAPSHOT_REOPEN_MISMATCH", "Stage01 inventory is not canonical JSONL");
        }
        List<String> ids = new ArrayList<>();
        for (String line : text.substring(0, text.length() - 1).split("\n", -1)) {
            ids.add(requiredText(parsedObject(line.getBytes(StandardCharsets.UTF_8), "Stage01 inventory"), "fileId", "Stage01 inventory"));
        }
        ids.sort(String::compareTo);
        return List.copyOf(ids);
    }

    private static List<String> gaps(ModuleMaterial m1, ModuleMaterial m2, ModuleMaterial m3, RepositoryCoverage coverage) {
        List<String> gaps = new ArrayList<>();
        gaps.addAll(m1.publication().receipt().gapRefs());
        gaps.addAll(m2.publication().receipt().gapRefs());
        gaps.addAll(m3.publication().receipt().gapRefs());
        if (coverage.entryIds().isEmpty()) {
            gaps.add(noEntryGapId(coverage.applicationProfileId()));
        }
        gaps.sort(String::compareTo);
        for (int index = 1; index < gaps.size(); index++) {
            if (gaps.get(index - 1).equals(gaps.get(index))) {
                throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", "Stage02 gap references must be unique");
            }
        }
        return List.copyOf(gaps);
    }

    private static ObjectNode report(
            String profileId, DiscoveryPart http, DiscoveryPart mapper, RepositoryCoverage coverage, List<String> gaps) {
        ObjectNode material = JSON.createObjectNode();
        material.put("applicationProfileId", profileId);
        material.set("httpEntryDiscovery", discoveryNode(http));
        material.set("mapperCatalogDiscovery", discoveryNode(mapper));
        material.set("repositoryEntryCoverage", coverageNode(coverage));
        ArrayNode gapsNode = material.putArray("gapRefs");
        gaps.forEach(gapsNode::add);
        ArrayNode reasons = material.putArray("gapReasons");
        if (coverage.entryIds().isEmpty()) {
            reasons.addObject()
                    .put("gapId", noEntryGapId(coverage.applicationProfileId()))
                    .put("reasonCode", "NO_ENTRY_DISCOVERED");
        }
        String reportId = "capability-report:" + digest(
                "stage02-capability-report-id-v2", CANONICAL_JSON.canonicalize(material).copyToByteArray());
        material.put("capabilityReportId", reportId);
        return material;
    }

    private static ObjectNode discoveryNode(DiscoveryPart part) {
        ObjectNode node = JSON.createObjectNode();
        ArrayNode sites = node.putArray("sites");
        part.sites().forEach(sites::add);
        ArrayNode shards = node.putArray("shardReceipts");
        part.shards().forEach(shards::add);
        return node;
    }

    private static ObjectNode coverageNode(RepositoryCoverage coverage) {
        ObjectNode node = JSON.createObjectNode();
        strings(node.putArray("sourceFileIds"), coverage.sourceFileIds());
        strings(node.putArray("discoverySiteIds"), coverage.discoverySiteIds());
        strings(node.putArray("entryIds"), coverage.entryIds());
        strings(node.putArray("supportedEntryIds"), coverage.supportedEntryIds());
        strings(node.putArray("gappedEntryIds"), coverage.gappedEntryIds());
        strings(node.putArray("excludedEntryIds"), coverage.excludedEntryIds());
        strings(node.putArray("shardReceiptIds"), coverage.shardReceiptIds());
        node.put("closed", coverage.closed());
        return node;
    }

    private static Payload standalone(String fileName, CanonicalArtifactPolicy policy, ObjectNode body) {
        if (!"STANDALONE_JSON".equals(policy.envelopeKind())) {
            throw new Stage02PublicationException("STAGE02_REQUEST_INVALID", "published JSON policy must be standalone");
        }
        String artifactId = policy.artifactIdPrefix() + ":" + digest(
                "canonical-standalone-json-artifact-id-v1",
                policy.key().schemaVersion().getBytes(StandardCharsets.UTF_8),
                policy.key().artifactType().getBytes(StandardCharsets.UTF_8),
                CANONICAL_JSON.canonicalize(body).copyToByteArray());
        ObjectNode complete = body.deepCopy();
        complete.put("artifactId", artifactId);
        ImmutableBytes bytes = CANONICAL_JSON.canonicalize(complete);
        return payload(fileName, policy, artifactId, bytes);
    }

    private static Payload jsonl(String fileName, CanonicalArtifactPolicy policy, List<ObjectNode> lines) {
        if (!"CANONICAL_JSONL".equals(policy.envelopeKind()) || (lines.isEmpty() && !policy.emptyJsonlAllowed())) {
            throw new Stage02PublicationException("STAGE02_REQUEST_INVALID", "published JSONL policy does not allow this payload");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        lines.forEach(line -> {
            bytes.writeBytes(CANONICAL_JSON.canonicalize(line).copyToByteArray());
            bytes.write('\n');
        });
        ImmutableBytes canonical = ImmutableBytes.copyOf(bytes.toByteArray());
        String artifactId = policy.artifactIdPrefix() + ":" + digest(
                "canonical-jsonl-artifact-id-v1",
                policy.key().schemaVersion().getBytes(StandardCharsets.UTF_8),
                policy.key().artifactType().getBytes(StandardCharsets.UTF_8),
                canonical.copyToByteArray());
        return payload(fileName, policy, artifactId, canonical);
    }

    private static Payload payload(String fileName, CanonicalArtifactPolicy policy, String artifactId, ImmutableBytes bytes) {
        return new Payload(
                new CanonicalModulePayload(
                        fileName,
                        policy.key().artifactType(),
                        policy.key().schemaVersion(),
                        artifactId,
                        policy.mediaType(),
                        bytes),
                new CanonicalStagePayload(
                        fileName,
                        policy.key().artifactType(),
                        policy.key().schemaVersion(),
                        artifactId,
                        policy.mediaType(),
                        bytes));
    }

    private static Map<String, VerifiedCanonicalPayload> byFileName(
            List<VerifiedCanonicalPayload> payloads, Set<String> expected, String subject) {
        Map<String, VerifiedCanonicalPayload> result = new LinkedHashMap<>();
        for (VerifiedCanonicalPayload payload : payloads) {
            if (result.put(payload.descriptor().fileName(), payload) != null) {
                throw new Stage02PublicationException("SNAPSHOT_REOPEN_MISMATCH", subject + " has duplicate payload names");
            }
        }
        if (!result.keySet().equals(expected)) {
            throw new Stage02PublicationException("SNAPSHOT_REOPEN_MISMATCH", subject + " payload set is invalid");
        }
        return Map.copyOf(result);
    }

    private static List<ObjectNode> sortedObjects(ArrayNode values, String identifier, String subject) {
        List<ObjectNode> result = new ArrayList<>();
        String previous = null;
        for (JsonNode value : values) {
            if (!(value instanceof ObjectNode object)) {
                throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", subject + " must contain objects");
            }
            String current = requiredText(object, identifier, subject);
            if (previous != null && previous.compareTo(current) >= 0) {
                throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", subject + " IDs must be sorted and unique");
            }
            previous = current;
            result.add(object.deepCopy());
        }
        return List.copyOf(result);
    }

    private static List<String> sortedTexts(ArrayNode values, String subject) {
        List<String> result = new ArrayList<>();
        String previous = null;
        for (JsonNode value : values) {
            if (!value.isTextual() || value.textValue().isBlank()) {
                throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", subject + " must contain text");
            }
            String current = value.textValue();
            if (previous != null && previous.compareTo(current) >= 0) {
                throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", subject + " must be sorted and unique");
            }
            previous = current;
            result.add(current);
        }
        return List.copyOf(result);
    }

    private static ObjectNode parsedObject(byte[] bytes, String subject) {
        try {
            JsonNode node = JSON.readTree(bytes);
            if (!(node instanceof ObjectNode object)
                    || !Arrays.equals(bytes, CANONICAL_JSON.canonicalize(object).copyToByteArray())) {
                throw new Stage02PublicationException("SNAPSHOT_REOPEN_MISMATCH", subject + " is not canonical JSON");
            }
            return object;
        } catch (Stage02PublicationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new Stage02PublicationException("SNAPSHOT_REOPEN_MISMATCH", subject + " cannot be parsed", exception);
        }
    }

    private static ObjectNode requiredObject(ObjectNode parent, String field, String subject) {
        JsonNode value = parent.get(field);
        if (!(value instanceof ObjectNode object)) {
            throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", subject + " missing object " + field);
        }
        return object;
    }

    private static ArrayNode requiredArray(ObjectNode parent, String field, String subject) {
        JsonNode value = parent.get(field);
        if (!(value instanceof ArrayNode array)) {
            throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", subject + " missing array " + field);
        }
        return array;
    }

    private static String requiredText(ObjectNode parent, String field, String subject) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", subject + " missing text " + field);
        }
        return value.textValue();
    }

    private static int integer(ObjectNode parent, String field, String subject) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isInt()) {
            throw new Stage02PublicationException("CAPABILITY_ACCOUNTING_BROKEN", subject + " missing integer " + field);
        }
        return value.intValue();
    }

    private static void strings(ArrayNode array, List<String> values) {
        values.forEach(array::add);
    }

    private static String noEntryGapId(String applicationProfileId) {
        return "gap:" + digest("stage02-no-entry-discovered-v2", applicationProfileId);
    }

    private static String digest(String domain, String... values) {
        return digest(domain, Arrays.stream(values)
                .map(value -> value.getBytes(StandardCharsets.UTF_8))
                .toArray(byte[][]::new));
    }

    private static String digest(String domain, byte[]... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            frame(digest, domain.getBytes(StandardCharsets.UTF_8));
            for (byte[] value : values) {
                frame(digest, value);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void frame(MessageDigest digest, byte[] bytes) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array());
        digest.update(bytes);
    }

    private record ModuleMaterial(
            ReopenedModulePublication publication, ObjectNode payload, ArtifactReference payloadReference) {}

    private record DiscoveryPart(
            List<ObjectNode> entries, String entryIdentifier, List<ObjectNode> sites, List<ObjectNode> shards) {}

    private record RepositoryCoverage(
            String applicationProfileId,
            List<String> sourceFileIds,
            List<String> discoverySiteIds,
            List<String> entryIds,
            List<String> supportedEntryIds,
            List<String> gappedEntryIds,
            List<String> excludedEntryIds,
            List<String> shardReceiptIds,
            boolean closed) {}

    private record Payload(CanonicalModulePayload modulePayload, CanonicalStagePayload stagePayload) {}
}
