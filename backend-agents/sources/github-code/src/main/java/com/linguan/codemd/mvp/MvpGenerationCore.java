package com.linguan.codemd.mvp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;

/**
 * The MVP's deep module. It owns frozen-input verification, interpretation
 * admission, deterministic rendering, and the trace index. Only the compact
 * Java seam in the other files is visible to callers.
 */
final class MvpGenerationCore {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final List<String> SECTION_TITLES = List.of(
            "文档说明", "业务目标", "业务对象", "业务活动", "字段与维度",
            "对象关系", "指标口径", "示例问题", "待确认事项");
    private static final Set<String> FACT_REFERENCE_FIELDS = Set.of(
            "factBasis", "factIds", "relatedFactIds", "triggerFactRef",
            "conditionFactRefs", "outcomeFactRefs", "factRef", "factRefs");
    private static final Set<String> EVIDENCE_REFERENCE_FIELDS = Set.of(
            "evidenceBasis", "evidenceIds", "evidenceRef", "evidenceRefs");

    private MvpGenerationCore() {
    }

    static GeneratedCandidate generate(GenerationRequest request) {
        FrozenManifest manifest = FrozenManifest.loadAndVerify(request.manifest(), request.snapshotRoot());
        List<FlowAdmission> admittedFlows = new ArrayList<>();
        List<RecordedResponse> roundOneResponses = new ArrayList<>();
        List<RecordedResponse> roundTwoResponses = new ArrayList<>();

        for (Flow flow : manifest.flows()) {
            ModelTask firstTask = new ModelTask(taskSpecId(flow), flow.flowId(), capsuleId(flow), 1);
            JsonNode roundOne = parseProviderResponse(request.modelProvider(), firstTask, "R1");
            RoundOneAdmission r1 = admitRoundOne(roundOne, firstTask, flow, manifest);
            roundOneResponses.add(new RecordedResponse(flow.flowId(), roundOne));

            ModelTask secondTask = new ModelTask(firstTask.taskSpecId(), firstTask.flowSliceId(),
                    firstTask.capsuleId(), 2);
            JsonNode roundTwo = parseProviderResponse(request.modelProvider(), secondTask, "R2");
            RoundTwoAdmission r2 = admitRoundTwo(roundTwo, secondTask, flow, r1);
            roundTwoResponses.add(new RecordedResponse(flow.flowId(), roundTwo));
            admittedFlows.add(new FlowAdmission(flow, r1, r2));
        }

        String markdown = MarkdownRenderer.render(admittedFlows);
        String contentId = sha256(markdown.getBytes(StandardCharsets.UTF_8));
        Map<String, List<TraceEvidence>> traces = traceIndex(admittedFlows.stream()
                .map(FlowAdmission::flow)
                .toList());
        CandidateReference reference = new CandidateReference(
                "candidate:" + candidateId(contentId, manifest.snapshotContentId(), traces),
                contentId, markdown);
        return new GeneratedCandidate(reference, traces, manifest.evidence(),
                manifest.snapshotContentId(), roundOneResponses, roundTwoResponses);
    }

    static GeneratedCandidate generateBaseline(BaselineGenerationRequest request) {
        FrozenManifest manifest = FrozenManifest.loadAndVerify(request.manifest(), request.snapshotRoot());
        String markdown = MarkdownRenderer.renderDeterministicBaseline(manifest.flows(), manifest.facts());
        String contentId = sha256(markdown.getBytes(StandardCharsets.UTF_8));
        Map<String, List<TraceEvidence>> traces = traceIndex(manifest.flows());
        CandidateReference reference = new CandidateReference(
                "candidate:" + candidateId(contentId, manifest.snapshotContentId(), traces),
                contentId, markdown);
        return new GeneratedCandidate(reference, traces, manifest.evidence(),
                manifest.snapshotContentId(), List.of(), List.of());
    }

    static ValidationReceipt validate(CandidateReference candidate) {
        String observedHash = sha256(candidate.markdown().getBytes(StandardCharsets.UTF_8));
        List<String> findings = new ArrayList<>();
        if (!candidate.candidateContentId().equals(observedHash)) {
            findings.add("DOCUMENT_HASH_MISMATCH");
        }
        if (!SECTION_TITLES.equals(sectionTitles(candidate.markdown()))) {
            findings.add("NINE_SECTION_SHAPE_INVALID");
        }
        return new ValidationReceipt(findings.isEmpty(), candidate.candidateId(),
                candidate.candidateContentId(), observedHash, findings);
    }

    static RuntimeException failure(String errorCode) {
        return new IllegalStateException(errorCode);
    }

    private static JsonNode parseProviderResponse(ModelProvider provider, ModelTask task, String round) {
        final String raw;
        try {
            raw = provider.execute(task);
        } catch (RuntimeException providerFailure) {
            throw failure(round + "_PROVIDER_FAILURE");
        }
        if (raw == null || raw.isBlank()) {
            throw failure(round + "_MODEL_RESPONSE_INVALID");
        }
        try {
            JsonNode parsed = JSON.readTree(raw);
            if (parsed == null || !parsed.isObject()) {
                throw failure(round + "_MODEL_RESPONSE_INVALID");
            }
            return parsed;
        } catch (JsonProcessingException invalidJson) {
            throw failure(round + "_MODEL_RESPONSE_INVALID");
        }
    }

    private static RoundOneAdmission admitRoundOne(JsonNode response, ModelTask task,
                                                    Flow flow, FrozenManifest manifest) {
        assertTextEquals(response, "taskSpecId", task.taskSpecId(), "R1_TASK_IDENTITY_MISMATCH");
        assertTextEquals(response, "flowSliceId", task.flowSliceId(), "R1_FLOW_IDENTITY_MISMATCH");
        assertTextEquals(response, "capsuleId", task.capsuleId(), "R1_CAPSULE_IDENTITY_MISMATCH");
        verifyReferences(response, flow.factIds(), flow.evidenceIds());

        Map<String, String> entityNames = new TreeMap<>();
        for (JsonNode entity : requiredArray(response, "localEntities", "R1_MODEL_SCHEMA_INVALID")) {
            String localKey = requiredText(entity, "localKey", "R1_MODEL_SCHEMA_INVALID");
            if (entityNames.containsKey(localKey)) {
                throw failure("R1_DUPLICATE_LOCAL_ENTITY");
            }
            String anchor = requiredText(entity, "anchorRef", "R1_MODEL_SCHEMA_INVALID");
            if (!manifest.anchorIds().contains(anchor)) {
                throw failure("UNKNOWN_ANCHOR");
            }
            entityNames.put(localKey, cleanBusinessText(optionalText(entity, "proposedName")));
        }

        Map<String, Proposal> proposals = new TreeMap<>();
        for (JsonNode interpretation : requiredArray(response, "interpretations", "R1_MODEL_SCHEMA_INVALID")) {
            String key = requiredText(interpretation, "proposalKey", "R1_MODEL_SCHEMA_INVALID");
            if (proposals.containsKey(key)) {
                throw failure("R1_DUPLICATE_PROPOSAL");
            }
            proposals.put(key, new Proposal(key,
                    referenceValues(interpretation, FACT_REFERENCE_FIELDS),
                    referenceValues(interpretation, EVIDENCE_REFERENCE_FIELDS),
                    cleanBusinessText(optionalText(interpretation, "proposedLabel"))));
        }
        return new RoundOneAdmission(entityNames, proposals);
    }

    private static RoundTwoAdmission admitRoundTwo(JsonNode response, ModelTask task,
                                                    Flow flow, RoundOneAdmission roundOne) {
        assertTextEquals(response, "taskSpecId", task.taskSpecId(), "R2_TASK_IDENTITY_MISMATCH");
        assertTextEquals(response, "flowSliceId", task.flowSliceId(), "R2_FLOW_IDENTITY_MISMATCH");
        verifyReferences(response, flow.factIds(), flow.evidenceIds());

        Set<String> reviewed = new TreeSet<>();
        Map<String, String> decisions = new TreeMap<>();
        for (JsonNode review : requiredArray(response, "reviews", "R2_MODEL_SCHEMA_INVALID")) {
            String proposalKey = requiredText(review, "proposalKey", "R2_MODEL_SCHEMA_INVALID");
            Proposal original = roundOne.proposals().get(proposalKey);
            if (original == null) {
                throw failure("R2_NEW_PROPOSAL");
            }
            if (!reviewed.add(proposalKey)) {
                throw failure("R2_DUPLICATE_PROPOSAL_REVIEW");
            }
            Set<String> facts = referenceValues(review, FACT_REFERENCE_FIELDS);
            Set<String> evidence = referenceValues(review, EVIDENCE_REFERENCE_FIELDS);
            if (!original.factBasis().containsAll(facts)) {
                throw failure("R2_FACT_BASIS_EXPANSION");
            }
            if (!original.evidenceBasis().containsAll(evidence)) {
                throw failure("R2_EVIDENCE_EXPANSION");
            }
            decisions.put(proposalKey, requiredText(review, "decision", "R2_MODEL_SCHEMA_INVALID"));
        }
        if (!reviewed.equals(roundOne.proposals().keySet())) {
            throw failure("R2_MISSING_PROPOSAL_REVIEW");
        }
        return new RoundTwoAdmission(decisions);
    }

    private static void verifyReferences(JsonNode node, Set<String> allowedFacts,
                                         Set<String> allowedEvidence) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                if (FACT_REFERENCE_FIELDS.contains(field.getKey())) {
                    verifyReferenceValues(field.getValue(), allowedFacts, "UNKNOWN_FACT");
                } else if (EVIDENCE_REFERENCE_FIELDS.contains(field.getKey())) {
                    verifyReferenceValues(field.getValue(), allowedEvidence, "UNKNOWN_EVIDENCE");
                }
                verifyReferences(field.getValue(), allowedFacts, allowedEvidence);
            });
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                verifyReferences(child, allowedFacts, allowedEvidence);
            }
        }
    }

    private static Set<String> referenceValues(JsonNode node, Set<String> fieldNames) {
        Set<String> values = new TreeSet<>();
        collectReferenceValues(node, fieldNames, values);
        return values;
    }

    private static void collectReferenceValues(JsonNode node, Set<String> fieldNames,
                                               Set<String> target) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                if (fieldNames.contains(field.getKey())) {
                    target.addAll(textValues(field.getValue(), "MODEL_REFERENCE_SCHEMA_INVALID"));
                }
                collectReferenceValues(field.getValue(), fieldNames, target);
            });
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectReferenceValues(child, fieldNames, target);
            }
        }
    }

    private static void verifyReferenceValues(JsonNode value, Set<String> allowlist,
                                              String failureCode) {
        for (String reference : textValues(value, "MODEL_REFERENCE_SCHEMA_INVALID")) {
            if (!allowlist.contains(reference)) {
                throw failure(failureCode);
            }
        }
    }

    private static List<String> textValues(JsonNode value, String failureCode) {
        if (value.isTextual()) {
            return List.of(value.textValue());
        }
        if (!value.isArray()) {
            throw failure(failureCode);
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || item.textValue().isBlank()) {
                throw failure(failureCode);
            }
            values.add(item.textValue());
        }
        return values;
    }

    private static Map<String, List<TraceEvidence>> traceIndex(Collection<Flow> flows) {
        Map<String, List<TraceEvidence>> result = new TreeMap<>();
        for (Flow flow : flows) {
            List<TraceEvidence> trace = flow.evidenceIds().stream()
                    .map(flow.evidenceById()::get)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(TraceEvidence::relativePath)
                            .thenComparingInt(TraceEvidence::startLine)
                            .thenComparingInt(TraceEvidence::startColumn))
                    .toList();
            if (result.put(flow.traceItemKey(), trace) != null) {
                throw failure("DUPLICATE_TRACE_ITEM_KEY");
            }
        }
        return result;
    }

    private static String taskSpecId(Flow flow) {
        return sha256(("mvp-task-spec-v1\n" + flow.flowId() + "\n"
                + String.join("\n", flow.factIds()) + "\n"
                + String.join("\n", flow.evidenceIds()) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static String capsuleId(Flow flow) {
        return sha256(("mvp-evidence-capsule-v1\n" + flow.flowId() + "\n"
                + String.join("\n", flow.evidenceIds()) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static String candidateId(String contentId, String snapshotContentId,
                                      Map<String, List<TraceEvidence>> traces) {
        StringBuilder material = new StringBuilder("mvp-candidate-v1\n")
                .append(contentId).append('\n').append(snapshotContentId).append('\n');
        traces.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            material.append(entry.getKey()).append('\n');
            entry.getValue().forEach(evidence -> material.append(evidence.relativePath()).append('\n')
                    .append(evidence.startLine()).append(':').append(evidence.startColumn()).append('\n')
                    .append(evidence.endLine()).append(':').append(evidence.endColumn()).append('\n')
                    .append(evidence.excerptSha256()).append('\n'));
        });
        return sha256(material.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String requiredText(JsonNode object, String field, String failureCode) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw failure(failureCode);
        }
        return value.textValue();
    }

    private static String optionalText(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        if (!value.isTextual()) {
            throw failure("R1_MODEL_SCHEMA_INVALID");
        }
        return value.textValue();
    }

    private static void assertTextEquals(JsonNode object, String field, String expected,
                                         String failureCode) {
        if (!expected.equals(requiredText(object, field, failureCode))) {
            throw failure(failureCode);
        }
    }

    private static List<JsonNode> requiredArray(JsonNode object, String field,
                                                String failureCode) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null || !value.isArray()) {
            throw failure(failureCode);
        }
        List<JsonNode> items = new ArrayList<>();
        value.forEach(items::add);
        return items;
    }

    private static String cleanBusinessText(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ").trim();
        if (normalized.length() > 80 || normalized.chars().anyMatch(Character::isISOControl)) {
            return "";
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if (lowered.contains("fact:") || lowered.contains("evidence:")
                || lowered.contains("sha256") || lowered.contains("taskspec")
                || lowered.contains("prompt") || lowered.contains("capsule")
                || lowered.contains("flow:")) {
            return "";
        }
        return normalized;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private static List<String> sectionTitles(String markdown) {
        return markdown.lines()
                .filter(line -> line.startsWith("## "))
                .map(line -> line.substring(3))
                .toList();
    }

    record GeneratedCandidate(CandidateReference reference, Map<String, List<TraceEvidence>> traceIndex,
                              Map<String, TraceEvidence> verifiedEvidence,
                              String snapshotContentId,
                              List<RecordedResponse> roundOneResponses,
                              List<RecordedResponse> roundTwoResponses) {
        GeneratedCandidate {
            traceIndex = Map.copyOf(traceIndex);
            verifiedEvidence = Map.copyOf(verifiedEvidence);
            snapshotContentId = Objects.requireNonNull(snapshotContentId, "snapshotContentId");
            roundOneResponses = List.copyOf(roundOneResponses);
            roundTwoResponses = List.copyOf(roundTwoResponses);
        }

        TraceView trace(String itemKey) {
            List<TraceEvidence> trace = traceIndex.get(itemKey);
            if (trace == null) {
                throw failure("UNKNOWN_TRACE_ITEM");
            }
            return new TraceView(reference.candidateId(), itemKey, trace);
        }
    }

    record RecordedResponse(String flowId, JsonNode response) {
        RecordedResponse {
            flowId = Objects.requireNonNull(flowId, "flowId");
            response = Objects.requireNonNull(response, "response").deepCopy();
        }
    }

    private record Proposal(String proposalKey, Set<String> factBasis,
                            Set<String> evidenceBasis, String label) {
        private Proposal {
            factBasis = Set.copyOf(factBasis);
            evidenceBasis = Set.copyOf(evidenceBasis);
        }
    }

    private record RoundOneAdmission(Map<String, String> entityNames,
                                     Map<String, Proposal> proposals) {
        private RoundOneAdmission {
            entityNames = Map.copyOf(entityNames);
            proposals = Map.copyOf(proposals);
        }
    }

    private record RoundTwoAdmission(Map<String, String> decisions) {
        private RoundTwoAdmission {
            decisions = Map.copyOf(decisions);
        }
    }

    private record FlowAdmission(Flow flow, RoundOneAdmission roundOne,
                                 RoundTwoAdmission roundTwo) {
    }

    private record SourceFile(String relativePath, Path verifiedPath, String sourceSha256) {
    }

    private record Flow(String flowId, String traceItemKey, Set<String> factIds,
                        Set<String> evidenceIds, Map<String, TraceEvidence> evidenceById) {
        private Flow {
            factIds = stableSet(factIds);
            evidenceIds = stableSet(evidenceIds);
            evidenceById = Map.copyOf(evidenceById);
        }
    }

    private static Set<String> stableSet(Collection<String> ids) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(ids)));
    }

    private record LockedFact(String factId, String kind, Map<String, List<String>> attributes,
                              Set<String> evidenceIds) {
        private LockedFact {
            attributes = Map.copyOf(attributes);
            evidenceIds = stableSet(evidenceIds);
        }

        List<String> attribute(String key) {
            return attributes.getOrDefault(key, List.of());
        }
    }

    private record FrozenManifest(Map<String, SourceFile> files,
                                  Map<String, TraceEvidence> evidence,
                                  Map<String, LockedFact> facts, Set<String> anchorIds,
                                  List<Flow> flows) {
        private FrozenManifest {
            files = Map.copyOf(files);
            evidence = Map.copyOf(evidence);
            facts = Map.copyOf(facts);
            anchorIds = stableSet(anchorIds);
            flows = List.copyOf(flows);
        }

        static FrozenManifest loadAndVerify(Path manifestPath, Path snapshotRoot) {
            JsonNode root = readManifest(manifestPath);
            requireSchema(root);
            Path snapshot = realDirectory(snapshotRoot);
            Map<String, SourceFile> files = verifyFiles(root, snapshot);
            Map<String, TraceEvidence> evidence = verifyEvidence(root, files);
            Map<String, LockedFact> facts = parseFacts(root, evidence.keySet());
            Set<String> anchors = parseAnchors(root);
            List<Flow> flows = parseFlows(root, facts.keySet(), evidence);
            return new FrozenManifest(files, evidence, facts, anchors, flows);
        }

        String snapshotContentId() {
            StringBuilder material = new StringBuilder("mvp-frozen-snapshot-v1\n");
            files.values().stream().sorted(Comparator.comparing(SourceFile::relativePath))
                    .forEach(file -> material.append(file.relativePath()).append('\n')
                            .append(file.sourceSha256()).append('\n'));
            return sha256(material.toString().getBytes(StandardCharsets.UTF_8));
        }

        private static JsonNode readManifest(Path manifest) {
            if (!Files.isRegularFile(manifest)) {
                throw failure("MANIFEST_NOT_FOUND");
            }
            try {
                JsonNode root = JSON.readTree(manifest.toFile());
                if (root == null || !root.isObject()) {
                    throw failure("MANIFEST_SCHEMA_INVALID");
                }
                return root;
            } catch (IOException unreadable) {
                throw failure("MANIFEST_SCHEMA_INVALID");
            }
        }

        private static void requireSchema(JsonNode root) {
            JsonNode version = root.get("schemaVersion");
            if (version == null || !version.canConvertToInt() || version.intValue() != 1) {
                throw failure("MANIFEST_SCHEMA_INVALID");
            }
            JsonNode origin = root.get("origin");
            requiredText(origin, "repositoryUrl", "MANIFEST_SCHEMA_INVALID");
            requiredText(origin, "commitSha", "MANIFEST_SCHEMA_INVALID");
            requiredText(root, "rootName", "MANIFEST_SCHEMA_INVALID");
        }

        private static Path realDirectory(Path directory) {
            try {
                Path real = directory.toRealPath();
                if (!Files.isDirectory(real)) {
                    throw failure("SNAPSHOT_ROOT_INVALID");
                }
                return real;
            } catch (IOException invalid) {
                throw failure("SNAPSHOT_ROOT_INVALID");
            }
        }

        private static Map<String, SourceFile> verifyFiles(JsonNode root, Path snapshot) {
            Map<String, SourceFile> verified = new LinkedHashMap<>();
            for (JsonNode entry : requiredArray(root, "files", "MANIFEST_SCHEMA_INVALID")) {
                String relativePath = requiredText(entry, "path", "MANIFEST_SCHEMA_INVALID");
                Path file = resolveInside(snapshot, relativePath, "SOURCE_PATH_INVALID");
                if (verified.containsKey(relativePath)) {
                    throw failure("DUPLICATE_SOURCE_PATH");
                }
                long expectedSize = requiredSize(entry, "size", "MANIFEST_SCHEMA_INVALID");
                String expectedHash = requiredHash(entry, "sha256", "MANIFEST_SCHEMA_INVALID");
                final byte[] bytes;
                try {
                    bytes = Files.readAllBytes(file);
                } catch (IOException unreadable) {
                    throw failure("SOURCE_READ_FAILURE");
                }
                if (bytes.length != expectedSize || !expectedHash.equals(sha256(bytes))) {
                    throw failure("SOURCE_HASH_MISMATCH");
                }
                verified.put(relativePath, new SourceFile(relativePath, file, expectedHash));
            }
            if (verified.isEmpty()) {
                throw failure("MANIFEST_SCHEMA_INVALID");
            }
            return verified;
        }

        private static Map<String, TraceEvidence> verifyEvidence(JsonNode root,
                                                                  Map<String, SourceFile> files) {
            Map<String, TraceEvidence> verified = new LinkedHashMap<>();
            for (JsonNode entry : requiredArray(root, "evidence", "MANIFEST_SCHEMA_INVALID")) {
                String evidenceId = requiredText(entry, "evidenceId", "MANIFEST_SCHEMA_INVALID");
                String relativePath = requiredText(entry, "path", "MANIFEST_SCHEMA_INVALID");
                SourceFile source = files.get(relativePath);
                if (source == null) {
                    throw failure("EVIDENCE_SOURCE_NOT_DECLARED");
                }
                int startLine = requiredPositiveInt(entry, "startLine", "MANIFEST_SCHEMA_INVALID");
                int endLine = requiredPositiveInt(entry, "endLine", "MANIFEST_SCHEMA_INVALID");
                int startColumn = requiredPositiveInt(entry, "startColumn", "MANIFEST_SCHEMA_INVALID");
                int endColumn = requiredPositiveInt(entry, "endColumn", "MANIFEST_SCHEMA_INVALID");
                if (endLine < startLine || (startLine == endLine && endColumn < startColumn)) {
                    throw failure("MANIFEST_SCHEMA_INVALID");
                }
                String expectedHash = requiredHash(entry, "excerptSha256", "MANIFEST_SCHEMA_INVALID");
                if (!expectedHash.equals(excerptHash(source.verifiedPath(), startLine, endLine))) {
                    throw failure("EXCERPT_HASH_MISMATCH");
                }
                if (verified.put(evidenceId, new TraceEvidence(relativePath, startLine, endLine,
                        startColumn, endColumn, expectedHash)) != null) {
                    throw failure("DUPLICATE_EVIDENCE_ID");
                }
            }
            if (verified.isEmpty()) {
                throw failure("MANIFEST_SCHEMA_INVALID");
            }
            return verified;
        }

        private static Map<String, LockedFact> parseFacts(JsonNode root, Set<String> knownEvidence) {
            Map<String, LockedFact> facts = new TreeMap<>();
            for (JsonNode entry : requiredArray(root, "lockedFacts", "MANIFEST_SCHEMA_INVALID")) {
                String factId = requiredText(entry, "factId", "MANIFEST_SCHEMA_INVALID");
                if (facts.containsKey(factId)) {
                    throw failure("DUPLICATE_FACT_ID");
                }
                String kind = requiredText(entry, "kind", "MANIFEST_SCHEMA_INVALID");
                Set<String> evidenceIds = stableSet(textValues(requiredField(entry, "evidenceIds",
                        "MANIFEST_SCHEMA_INVALID"), "MANIFEST_SCHEMA_INVALID"));
                for (String evidenceId : evidenceIds) {
                    if (!knownEvidence.contains(evidenceId)) {
                        throw failure("UNKNOWN_EVIDENCE");
                    }
                }
                JsonNode attributes = entry.has("attributes")
                        ? entry.get("attributes") : entry.get("lockedAtoms");
                facts.put(factId, new LockedFact(factId, kind,
                        attributes(attributes), evidenceIds));
            }
            if (facts.isEmpty()) {
                throw failure("MANIFEST_SCHEMA_INVALID");
            }
            return facts;
        }

        private static Map<String, List<String>> attributes(JsonNode node) {
            if (node == null || !node.isObject()) {
                throw failure("MANIFEST_SCHEMA_INVALID");
            }
            Map<String, List<String>> result = new TreeMap<>();
            node.fields().forEachRemaining(entry -> {
                List<String> values = textValues(entry.getValue(), "MANIFEST_SCHEMA_INVALID");
                if (values.isEmpty()) {
                    throw failure("MANIFEST_SCHEMA_INVALID");
                }
                result.put(entry.getKey(), List.copyOf(values));
            });
            return result;
        }

        private static Set<String> parseAnchors(JsonNode root) {
            Set<String> anchors = new TreeSet<>();
            for (JsonNode entry : requiredArray(root, "anchors", "MANIFEST_SCHEMA_INVALID")) {
                String anchorId = requiredText(entry, "anchorId", "MANIFEST_SCHEMA_INVALID");
                if (!anchors.add(anchorId)) {
                    throw failure("DUPLICATE_ANCHOR_ID");
                }
            }
            return anchors;
        }

        private static List<Flow> parseFlows(JsonNode root, Set<String> knownFacts,
                                             Map<String, TraceEvidence> knownEvidence) {
            List<Flow> flows = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            Set<String> traceKeys = new HashSet<>();
            for (JsonNode entry : requiredArray(root, "flows", "MANIFEST_SCHEMA_INVALID")) {
                String flowId = requiredText(entry, "flowId", "MANIFEST_SCHEMA_INVALID");
                String traceItemKey = requiredText(entry, "traceItemKey", "MANIFEST_SCHEMA_INVALID");
                if (!ids.add(flowId) || !traceKeys.add(traceItemKey)) {
                    throw failure("DUPLICATE_FLOW_IDENTITY");
                }
                Set<String> factIds = stableSet(textValues(requiredField(entry, "factIds",
                        "MANIFEST_SCHEMA_INVALID"), "MANIFEST_SCHEMA_INVALID"));
                Set<String> evidenceIds = stableSet(textValues(requiredField(entry, "evidenceIds",
                        "MANIFEST_SCHEMA_INVALID"), "MANIFEST_SCHEMA_INVALID"));
                if (factIds.isEmpty() || evidenceIds.isEmpty()) {
                    throw failure("MANIFEST_SCHEMA_INVALID");
                }
                if (!knownFacts.containsAll(factIds)) {
                    throw failure("UNKNOWN_FACT");
                }
                if (!knownEvidence.keySet().containsAll(evidenceIds)) {
                    throw failure("UNKNOWN_EVIDENCE");
                }
                Map<String, TraceEvidence> evidenceById = new LinkedHashMap<>();
                for (String evidenceId : evidenceIds) {
                    evidenceById.put(evidenceId, knownEvidence.get(evidenceId));
                }
                flows.add(new Flow(flowId, traceItemKey, factIds, evidenceIds, evidenceById));
            }
            if (flows.isEmpty()) {
                throw failure("MANIFEST_SCHEMA_INVALID");
            }
            flows.sort(Comparator.comparing(Flow::flowId));
            return flows;
        }

        private static Path resolveInside(Path snapshot, String relativePath, String failureCode) {
            if (relativePath.isBlank() || relativePath.indexOf('\\') >= 0) {
                throw failure(failureCode);
            }
            final Path relative;
            try {
                relative = Path.of(relativePath);
            } catch (RuntimeException invalid) {
                throw failure(failureCode);
            }
            if (relative.isAbsolute() || relative.getNameCount() == 0) {
                throw failure(failureCode);
            }
            for (Path segment : relative) {
                String name = segment.toString();
                if (name.equals(".") || name.equals("..") || name.isBlank()) {
                    throw failure(failureCode);
                }
            }
            Path unresolved = snapshot.resolve(relative).normalize();
            if (!unresolved.startsWith(snapshot) || !Files.isRegularFile(unresolved)) {
                throw failure(failureCode);
            }
            try {
                Path resolved = unresolved.toRealPath();
                if (!resolved.startsWith(snapshot)) {
                    throw failure(failureCode);
                }
                return resolved;
            } catch (IOException invalid) {
                throw failure(failureCode);
            }
        }

        private static String excerptHash(Path file, int startLine, int endLine) {
            final List<String> lines;
            try {
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException unreadable) {
                throw failure("EXCERPT_READ_FAILURE");
            }
            if (endLine > lines.size()) {
                throw failure("EXCERPT_HASH_MISMATCH");
            }
            String excerpt = String.join("\n", lines.subList(startLine - 1, endLine)) + "\n";
            return sha256(excerpt.getBytes(StandardCharsets.UTF_8));
        }

        private static JsonNode requiredField(JsonNode object, String field, String failureCode) {
            JsonNode value = object == null ? null : object.get(field);
            if (value == null || value.isNull()) {
                throw failure(failureCode);
            }
            return value;
        }

        private static int requiredPositiveInt(JsonNode object, String field, String failureCode) {
            JsonNode value = object == null ? null : object.get(field);
            if (value == null || !value.canConvertToInt() || value.intValue() < 1) {
                throw failure(failureCode);
            }
            return value.intValue();
        }

        private static long requiredSize(JsonNode object, String field, String failureCode) {
            JsonNode value = object == null ? null : object.get(field);
            if (value == null || !value.canConvertToLong() || value.longValue() < 0) {
                throw failure(failureCode);
            }
            return value.longValue();
        }

        private static String requiredHash(JsonNode object, String field, String failureCode) {
            String hash = requiredText(object, field, failureCode);
            if (!hash.matches("[0-9a-f]{64}")) {
                throw failure(failureCode);
            }
            return hash;
        }
    }

    private static final class MarkdownRenderer {
        private MarkdownRenderer() {
        }

        static String render(List<FlowAdmission> flows) {
            List<String> entities = flows.stream()
                    .flatMap(flow -> flow.roundOne().entityNames().values().stream())
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .sorted()
                    .toList();
            List<String> activities = flows.stream()
                    .flatMap(flow -> flow.roundOne().proposals().entrySet().stream()
                            .filter(entry -> isAdmitted(flow.roundTwo(), entry.getKey()))
                            .map(entry -> entry.getValue().label()))
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .sorted()
                    .toList();

            List<String> lines = new ArrayList<>();
            addSection(lines, SECTION_TITLES.get(0), List.of(
                    "本文依据已校验的冻结代码证据生成，范围限于已验证流程。"));
            addSection(lines, SECTION_TITLES.get(1), List.of(
                    activities.isEmpty() ? "当前流程的业务目标需结合业务术语进一步确认。"
                            : "已验证流程覆盖以下业务处理：" + String.join("、", activities) + "。"));
            addSection(lines, SECTION_TITLES.get(2), List.of(
                    entities.isEmpty() ? "当前证据范围内的业务对象名称待确认。"
                            : "已识别的业务对象：" + String.join("、", entities) + "。"));
            addSection(lines, SECTION_TITLES.get(3), List.of(
                    activities.isEmpty() ? "当前证据范围内未形成可发布的业务活动名称。"
                            : "可追溯的业务活动：" + String.join("、", activities) + "。"));
            addSection(lines, SECTION_TITLES.get(4), List.of(
                    "字段与维度以已验证的处理条件和状态结果为准。"));
            addSection(lines, SECTION_TITLES.get(5), List.of(
                    "入口、处理步骤与持久化步骤构成当前已验证的对象关系。"));
            addSection(lines, SECTION_TITLES.get(6), List.of(
                    "当前证据范围内未确认可发布的指标口径。"));
            addSection(lines, SECTION_TITLES.get(7), List.of(
                    "该流程在何种条件下处理，并产生何种状态结果？"));
            addSection(lines, SECTION_TITLES.get(8), List.of(
                    "仍需业务方确认术语含义、运行时事务结果和指标定义。"));
            return String.join("\n", lines) + "\n";
        }

        static String renderDeterministicBaseline(List<Flow> flows,
                                                  Map<String, LockedFact> facts) {
            List<LockedFact> flowFacts = flows.stream()
                    .flatMap(flow -> flow.factIds().stream())
                    .map(facts::get)
                    .filter(Objects::nonNull)
                    .toList();
            LockedFact http = first(flowFacts, "HTTP_ENTRY");
            LockedFact guard = first(flowFacts, "GUARD");
            LockedFact stateGuard = first(flowFacts, "STATE_GUARD");
            LockedFact auditGuard = first(flowFacts, "STATE_AND_STOCK_GUARD");
            LockedFact stateWrite = first(flowFacts, "STATE_WRITE");
            LockedFact persistence = first(flowFacts, "PERSISTENCE_CALL");
            LockedFact mapping = first(flowFacts, "SQL_MAPPING");
            List<String> lines = new ArrayList<>();
            addSection(lines, SECTION_TITLES.get(0), List.of(
                    "本文为确定性基线，依据已校验的冻结代码和行段证据按固定规则生成；未使用模型解释。",
                    "范围包含 " + flows.size() + " 个已验证流程，正文仅陈述已锁定事实。"));
            addSection(lines, SECTION_TITLES.get(1), List.of(
                    http == null ? "当前流程的业务目标待结合已验证入口确认。"
                            : "入口：" + joined(http.attribute("method"), "HTTP") + " "
                            + joined(http.attribute("route"), "（入口待确认）") + "。该入口"
                            + "，对指定记录执行状态处理；其更高层业务价值仍待确认。"));
            addSection(lines, SECTION_TITLES.get(2), List.of(
                    persistence == null ? "当前证据未识别可发布的对象选择规则。"
                            : "处理对象为满足校验的记录集合；选择规则为："
                            + joined(persistence.attribute("selection"), "待确认") + "。",
                    mapping == null ? "对象对应的持久化映射待确认。"
                            : "状态写入映射到 " + joined(mapping.attribute("table"), "目标表") + " 的 "
                            + joined(mapping.attribute("column"), "状态字段") + " 字段。"));
            addSection(lines, SECTION_TITLES.get(3), List.of(
                    activity(http, "接收状态处理请求并取得参数"),
                    activity(stateGuard, "按既有状态执行反向状态处理校验"),
                    activity(auditGuard, "按既有状态执行审核与库存校验"),
                    activity(guard, "按已锁定条件校验记录"),
                    activity(persistence, "将通过校验的记录写入目标状态")));
            addSection(lines, SECTION_TITLES.get(4), List.of(
                    "请求维度：" + attributes(http, "parameters") + "。",
                    "状态/条件维度：" + combine(
                            attributes(stateGuard, "requestedStatus", "requiredCurrentStatus",
                                    "requiredPurchaseStatus"),
                            attributes(auditGuard, "requestedStatus", "requiredCurrentStatus",
                                    "stockCheckRequires"),
                            attributes(guard, "condition")) + "。",
                    "写入维度：" + combine(attributes(stateWrite, "field", "value"),
                            attributes(persistence, "assignedField"),
                            attributes(mapping, "mapping")) + "。"));
            addSection(lines, SECTION_TITLES.get(5), List.of(
                    "请求参数 → 记录状态校验 → 通过校验的记录集合 → 状态字段更新。",
                    persistence == null || mapping == null ? "持久化关联待确认。"
                            : joined(persistence.attribute("target"), "持久化调用") + " 负责写入 "
                            + joined(mapping.attribute("table"), "目标表") + "。"));
            addSection(lines, SECTION_TITLES.get(6), List.of(
                    "当前基线未确认可发布的指标口径，后续应由业务方补充。"));
            addSection(lines, SECTION_TITLES.get(7), List.of(
                    "哪些记录会因当前状态或采购状态不满足条件而被拒绝？",
                    "开启强审核且不允许负库存时，哪些单据组合需要库存校验？",
                    "本次状态更新影响了多少条记录？"));
            addSection(lines, SECTION_TITLES.get(8), List.of(
                    "状态码、采购状态码及各单据类型的完整业务含义待确认。",
                    "异常后的事务结果、库存更新的一致性和逐条更新结果待确认。",
                    "尚无源码公式或聚合支持业务指标口径。"));
            return String.join("\n", lines) + "\n";
        }

        private static LockedFact first(List<LockedFact> facts, String kind) {
            return facts.stream().filter(fact -> kind.equals(fact.kind())).findFirst().orElse(null);
        }

        private static String activity(LockedFact fact, String fallback) {
            return fact == null ? fallback + "（当前证据不足）" : fallback + "。";
        }

        private static String attributes(LockedFact fact, String... keys) {
            if (fact == null) {
                return "当前证据不足";
            }
            List<String> parts = new ArrayList<>();
            for (String key : keys) {
                List<String> values = fact.attribute(key);
                if (!values.isEmpty()) {
                    parts.add(key + "=" + String.join("、", values));
                }
            }
            return parts.isEmpty() ? "当前证据不足" : String.join("；", parts);
        }

        private static String joined(List<String> values, String fallback) {
            return values.isEmpty() ? fallback : String.join("、", values);
        }

        private static String combine(String... segments) {
            return String.join("；", segments);
        }

        private static boolean isAdmitted(RoundTwoAdmission roundTwo, String proposalKey) {
            return "KEEP".equals(roundTwo.decisions().get(proposalKey));
        }

        private static void addSection(List<String> lines, String title, List<String> content) {
            lines.add("## " + title);
            lines.addAll(content);
            lines.add("");
        }
    }
}
