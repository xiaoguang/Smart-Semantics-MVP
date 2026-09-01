package com.linguan.codemd.target.artifacts;

import java.util.Set;
import java.util.regex.Pattern;

final class ArtifactPolicyValidation {
    private static final Pattern ARTIFACT_TYPE = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Pattern ID_PREFIX = Pattern.compile("[a-z][a-z0-9-]{0,47}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> ENVELOPE_KINDS =
            Set.of("MODULE_ARTIFACT_JSON", "STANDALONE_JSON", "CANONICAL_JSONL", "RAW_UTF8");
    private static final Set<String> PUBLIC_CONTENT_EXPOSURES =
            Set.of("METADATA_ONLY", "PATH_FREE_COMPLETE_UTF8");

    private ArtifactPolicyValidation() {}

    static ArtifactStoreException invalid(String detail) {
        return new ArtifactStoreException("ARTIFACT_POLICY_REGISTRY_INVALID", detail);
    }

    static String text(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || containsControl(value)) {
            throw invalid(field + " must be nonblank canonical text");
        }
        return value;
    }

    static String artifactType(String value) {
        text(value, "artifactType");
        if (!ARTIFACT_TYPE.matcher(value).matches()) {
            throw invalid("artifactType must be an uppercase registered token");
        }
        return value;
    }

    static String artifactIdPrefix(String value) {
        text(value, "artifactIdPrefix");
        if (!ID_PREFIX.matcher(value).matches()) {
            throw invalid("artifactIdPrefix must be a lowercase safe prefix");
        }
        return value;
    }

    static String mediaType(String value) {
        text(value, "mediaType");
        if (!Set.of("application/json", "application/x-ndjson", "text/markdown").contains(value)) {
            throw invalid("mediaType is not supported by the target artifact contract");
        }
        return value;
    }

    static String envelopeKind(String value) {
        text(value, "envelopeKind");
        if (!ENVELOPE_KINDS.contains(value)) {
            throw invalid("envelopeKind is not a closed target value");
        }
        return value;
    }

    static String publicContentExposure(String value) {
        text(value, "publicContentExposure");
        if (!PUBLIC_CONTENT_EXPOSURES.contains(value)) {
            throw invalid("publicContentExposure is not a closed target value");
        }
        return value;
    }

    static String sha256(String value, String field) {
        text(value, field);
        if (!SHA_256.matcher(value).matches()) {
            throw invalid(field + " must be lowercase SHA-256");
        }
        return value;
    }

    static String registryArtifactId(String value) {
        text(value, "artifactPolicyRegistryId");
        String prefix = "artifact-policy-registry:";
        if (!value.startsWith(prefix) || !SHA_256.matcher(value.substring(prefix.length())).matches()) {
            throw invalid("artifactPolicyRegistryId must use the artifact-policy-registry prefix");
        }
        return value;
    }

    private static boolean containsControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
