package com.linguan.codemd.stage01;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exact, dependency-free JSON reader for the public Stage01Request boundary. */
public final class Stage01RequestJson {
    private static final String GAP_ID = "gap-expectation-profile-v1";
    private static final String GAP_SHA = "e63f976bba3fcc0acbc62c3b72f0a924d539d240d69ca86b7a598905fafc0f8a";

    private Stage01RequestJson() {
    }

    public static Stage01Request parse(String json) {
        Object value = new Json(json).value();
        Map<String, Object> root = object(value, Set.of("schemaVersion", "frozenRepositoryRequest",
                "gapExpectationProfileRef"));
        String schema = string(root.get("schemaVersion"));
        Map<String, Object> gap = object(root.get("gapExpectationProfileRef"), Set.of("profileId", "profileSha256"));
        GapExpectationProfileRef profile = new GapExpectationProfileRef(string(gap.get("profileId")),
                string(gap.get("profileSha256")));
        if (!GAP_ID.equals(profile.profileId()) || !GAP_SHA.equals(profile.profileSha256())) {
            throw new Stage01Exception(Stage01FailureCode.PROFILE_REFERENCE_INVALID);
        }
        Map<String, Object> frozen = object(root.get("frozenRepositoryRequest"), Set.of("schemaVersion", "origin",
                "captureProof", "snapshotRoot", "inventoryScope", "files", "verificationPolicyId",
                "resourceBudget", "capabilityProfileRef"));
        if (!"frozen-repository-request-v1".equals(string(frozen.get("schemaVersion")))) {
            throw new Stage01Exception(Stage01FailureCode.REQUEST_SCHEMA_INVALID);
        }
        Map<String, Object> origin = object(frozen.get("origin"), Set.of("kind", "repositoryUrl", "revision"));
        Map<String, Object> capture = object(frozen.get("captureProof"), Set.of("kind", "receiptId",
                "boundRepositoryUrl", "boundRevision", "inventorySha256", "receiptSha256"));
        Map<String, Object> scope = object(frozen.get("inventoryScope"), Set.of("kind", "scopeRoot", "declaredPathCount"));
        Map<String, Object> budget = object(frozen.get("resourceBudget"), Set.of("maxFiles", "maxTotalBytes",
                "maxFileBytes", "maxAstNodes", "maxXmlNodes", "maxSqlChars", "maxControlFlowNodes", "maxRecursionDepth"));
        Map<String, Object> capability = object(frozen.get("capabilityProfileRef"), Set.of("profileId", "profileSha256"));
        List<DeclaredFile> files = new ArrayList<>();
        for (Object item : array(frozen.get("files"))) {
            Map<String, Object> file = object(item, Set.of("path", "mediaType", "sizeBytes", "sha256", "textEncoding"));
            files.add(new DeclaredFile(string(file.get("path")), string(file.get("mediaType")), number(file.get("sizeBytes")),
                    string(file.get("sha256")), string(file.get("textEncoding"))));
        }
        FrozenRepositoryRequest request = new FrozenRepositoryRequest(
                new Origin(string(origin.get("kind")), string(origin.get("repositoryUrl")), string(origin.get("revision"))),
                new CaptureProof(string(capture.get("kind")), string(capture.get("receiptId")),
                        string(capture.get("boundRepositoryUrl")), string(capture.get("boundRevision")),
                        string(capture.get("inventorySha256")), string(capture.get("receiptSha256"))),
                Path.of(string(frozen.get("snapshotRoot"))), new InventoryScope(string(scope.get("kind")),
                string(scope.get("scopeRoot")), (int) number(scope.get("declaredPathCount"))), files,
                string(frozen.get("verificationPolicyId")), new ResourceBudget((int) number(budget.get("maxFiles")),
                number(budget.get("maxTotalBytes")), number(budget.get("maxFileBytes")), (int) number(budget.get("maxAstNodes")),
                (int) number(budget.get("maxXmlNodes")), (int) number(budget.get("maxSqlChars")),
                (int) number(budget.get("maxControlFlowNodes")), (int) number(budget.get("maxRecursionDepth"))),
                new CapabilityProfileRef(string(capability.get("profileId")), string(capability.get("profileSha256"))));
        return new Stage01Request(schema, request, profile);
    }

    private static Map<String, Object> object(Object value, Set<String> fields) {
        if (!(value instanceof Map<?, ?>)) fail();
        Map<?, ?> map = (Map<?, ?>) value;
        if (!map.keySet().equals(fields)) fail();
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) { if (!(entry.getKey() instanceof String key)) fail(); else result.put(key, entry.getValue()); }
        return result;
    }
    private static List<?> array(Object value) { if (!(value instanceof List<?> list)) fail(); return (List<?>) value; }
    private static String string(Object value) { if (!(value instanceof String)) fail(); return (String) value; }
    private static long number(Object value) { if (!(value instanceof Long)) fail(); return (Long) value; }
    private static void fail() { throw new Stage01Exception(Stage01FailureCode.REQUEST_SCHEMA_INVALID); }

    private static final class Json {
        private final String text; private int index;
        private Json(String text) { this.text = text == null ? "" : text; }
        private Object value() { Object result = read(); space(); if (index != text.length()) fail(); return result; }
        private Object read() { space(); if (index >= text.length()) { fail(); return null; } return switch (text.charAt(index)) {
            case '{' -> map(); case '[' -> list(); case '"' -> quoted(); default -> integer(); }; }
        private Map<String,Object> map() { index++; space(); Map<String,Object> out = new LinkedHashMap<>(); if (take('}')) return out; while (true) { space(); String key = quoted(); space(); need(':'); Object value = read(); if (out.put(key, value) != null) fail(); space(); if (take('}')) return out; need(','); } }
        private List<Object> list() { index++; space(); List<Object> out = new ArrayList<>(); if (take(']')) return out; while (true) { out.add(read()); space(); if (take(']')) return out; need(','); } }
        private String quoted() { need('"'); StringBuilder out = new StringBuilder(); while (index < text.length() && text.charAt(index) != '"') { char c = text.charAt(index++); if (c == '\\') { if (index >= text.length()) fail(); c = text.charAt(index++); if (c == '"' || c == '\\' || c == '/') out.append(c); else if (c == 'n') out.append('\n'); else if (c == 'r') out.append('\r'); else if (c == 't') out.append('\t'); else fail(); } else out.append(c); } need('"'); return out.toString(); }
        private Long integer() { int start=index; if (take('-')) {} while (index < text.length() && Character.isDigit(text.charAt(index))) index++; if (start==index) fail(); try { return Long.parseLong(text.substring(start,index)); } catch(NumberFormatException bad) { fail(); return 0L; } }
        private void space() { while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++; }
        private boolean take(char c) { if (index < text.length() && text.charAt(index)==c) { index++; return true; } return false; }
        private void need(char c) { if (!take(c)) fail(); }
    }
}
