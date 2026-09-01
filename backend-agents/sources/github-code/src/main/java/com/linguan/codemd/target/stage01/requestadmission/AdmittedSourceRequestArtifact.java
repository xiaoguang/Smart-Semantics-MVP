package com.linguan.codemd.target.stage01.requestadmission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.target.artifacts.ArtifactReference;
import com.linguan.codemd.target.stage01.capture.AnalysisDisposition;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Exact M1 payload schema, reconstructed solely from a fresh-reopened module envelope. */
public final class AdmittedSourceRequestArtifact {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> ROOT_FIELDS =
            Set.of(
                    "analyzableTextFileIds",
                    "capabilityProfileRef",
                    "declaredPathCount",
                    "files",
                    "inventoryScope",
                    "nonAnalyzableMediaFileIds",
                    "originRepositoryUrl",
                    "originRevision",
                    "repositoryCompletionEligible",
                    "requestIdentity",
                    "resourceBudgetRef",
                    "sourceRegistrationId",
                    "verificationPolicyRef");
    private static final Set<String> FILE_FIELDS =
            Set.of(
                    "analysisDisposition",
                    "gitMode",
                    "mediaType",
                    "path",
                    "sha256",
                    "sizeBytes",
                    "textEncoding");

    private AdmittedSourceRequestArtifact() {}

    public static ObjectNode write(AdmittedSourceRequest request) {
        if (request == null) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "M1 request must not be null");
        }
        ObjectNode root = JSON.createObjectNode();
        root.put("requestIdentity", request.requestIdentity());
        root.put("sourceRegistrationId", request.sourceRegistrationId());
        root.put("originRepositoryUrl", request.originRepositoryUrl());
        root.put("originRevision", request.originRevision());
        ObjectNode scope = root.putObject("inventoryScope");
        scope.put("kind", request.inventoryScope().kind().name());
        if (request.inventoryScope().scopeRoot() == null) {
            scope.putNull("scopeRoot");
        } else {
            scope.put("scopeRoot", request.inventoryScope().scopeRoot());
        }
        root.put("repositoryCompletionEligible", request.repositoryCompletionEligible());
        root.put("declaredPathCount", request.declaredPathCount());
        ArrayNode files = root.putArray("files");
        for (AdmittedSourceFile file : request.files()) {
            ObjectNode value = files.addObject();
            value.put("path", file.path());
            value.put("gitMode", file.gitMode());
            value.put("mediaType", file.mediaType());
            value.put("sizeBytes", file.sizeBytes());
            value.put("sha256", file.sha256());
            value.put("analysisDisposition", file.analysisDisposition().name());
            if (file.textEncoding() == null) {
                value.putNull("textEncoding");
            } else {
                value.put("textEncoding", file.textEncoding());
            }
        }
        strings(root.putArray("analyzableTextFileIds"), request.analyzableTextFileIds());
        strings(root.putArray("nonAnalyzableMediaFileIds"), request.nonAnalyzableMediaFileIds());
        reference(root, "capabilityProfileRef", request.capabilityProfileRef());
        reference(root, "resourceBudgetRef", request.resourceBudgetRef());
        reference(root, "verificationPolicyRef", request.verificationPolicyRef());
        return root;
    }

    public static AdmittedSourceRequest parse(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "M1 payload must be an object");
        }
        ObjectNode root = (ObjectNode) payload;
        exact(root, ROOT_FIELDS, "M1 payload");
        int declaredPathCount = integer(root, "declaredPathCount");
        return new AdmittedSourceRequest(
                text(root, "requestIdentity"),
                text(root, "sourceRegistrationId"),
                text(root, "originRepositoryUrl"),
                text(root, "originRevision"),
                parseScope(object(root, "inventoryScope"), declaredPathCount),
                bool(root, "repositoryCompletionEligible"),
                declaredPathCount,
                parseFiles(array(root, "files")),
                strings(array(root, "analyzableTextFileIds")),
                strings(array(root, "nonAnalyzableMediaFileIds")),
                reference(root, "capabilityProfileRef"),
                reference(root, "resourceBudgetRef"),
                reference(root, "verificationPolicyRef"));
    }

    private static InventoryScope parseScope(ObjectNode scope, int declaredPathCount) {
        exact(scope, Set.of("kind", "scopeRoot"), "inventoryScope");
        JsonNode scopeRoot = scope.get("scopeRoot");
        if (scopeRoot == null || !(scopeRoot.isNull() || scopeRoot.isTextual())) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "scopeRoot must be null or text");
        }
        try {
            return new InventoryScope(
                    InventoryScope.Kind.valueOf(text(scope, "kind")),
                    scopeRoot.isNull() ? null : scopeRoot.textValue(),
                    declaredPathCount);
        } catch (IllegalArgumentException exception) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "inventory scope kind is invalid");
        }
    }

    private static List<AdmittedSourceFile> parseFiles(ArrayNode values) {
        List<AdmittedSourceFile> files = new ArrayList<>();
        String priorPath = null;
        for (JsonNode node : values) {
            if (!node.isObject()) {
                throw new AdmissionException("REQUEST_SCHEMA_INVALID", "M1 file must be an object");
            }
            ObjectNode file = (ObjectNode) node;
            exact(file, FILE_FIELDS, "M1 file");
            String path = text(file, "path");
            if (priorPath != null && priorPath.compareTo(path) >= 0) {
                throw new AdmissionException("REQUEST_SCHEMA_INVALID", "M1 files must be sorted and unique");
            }
            JsonNode textEncoding = file.get("textEncoding");
            if (textEncoding == null || !(textEncoding.isNull() || textEncoding.isTextual())) {
                throw new AdmissionException("REQUEST_SCHEMA_INVALID", "M1 textEncoding must be null or text");
            }
            try {
                files.add(
                        AdmittedSourceFile.fromWire(
                                path,
                                text(file, "gitMode"),
                                text(file, "mediaType"),
                                longValue(file, "sizeBytes"),
                                text(file, "sha256"),
                                AnalysisDisposition.valueOf(text(file, "analysisDisposition")),
                                textEncoding.isNull() ? null : textEncoding.textValue()));
            } catch (IllegalArgumentException exception) {
                throw new AdmissionException("REQUEST_SCHEMA_INVALID", "M1 file disposition is invalid");
            }
            priorPath = path;
        }
        return List.copyOf(files);
    }

    private static void reference(ObjectNode root, String field, ArtifactReference reference) {
        ObjectNode node = root.putObject(field);
        node.put("artifactId", reference.artifactId());
        node.put("sha256", reference.sha256());
    }

    private static ArtifactReference reference(ObjectNode root, String field) {
        ObjectNode node = object(root, field);
        exact(node, Set.of("artifactId", "sha256"), field);
        return new ArtifactReference(text(node, "artifactId"), text(node, "sha256"));
    }

    private static void strings(ArrayNode destination, List<String> values) {
        values.forEach(destination::add);
    }

    private static List<String> strings(ArrayNode values) {
        List<String> result = new ArrayList<>();
        String prior = null;
        for (JsonNode value : values) {
            if (!value.isTextual() || (prior != null && prior.compareTo(value.textValue()) >= 0)) {
                throw new AdmissionException("REQUEST_SCHEMA_INVALID", "M1 file IDs must be sorted text");
            }
            result.add(value.textValue());
            prior = value.textValue();
        }
        return List.copyOf(result);
    }

    private static void exact(ObjectNode value, Set<String> expected, String subject) {
        Set<String> actual = new java.util.HashSet<>();
        value.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", subject + " fields are not exact");
        }
    }

    private static ObjectNode object(ObjectNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isObject()) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", field + " must be an object");
        }
        return (ObjectNode) value;
    }

    private static ArrayNode array(ObjectNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isArray()) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", field + " must be an array");
        }
        return (ArrayNode) value;
    }

    private static String text(ObjectNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", field + " must be text");
        }
        return value.textValue();
    }

    private static boolean bool(ObjectNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isBoolean()) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", field + " must be boolean");
        }
        return value.booleanValue();
    }

    private static int integer(ObjectNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", field + " must be integer");
        }
        return value.intValue();
    }

    private static long longValue(ObjectNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", field + " must be integer");
        }
        return value.longValue();
    }
}
