package com.linguan.codemd.stage04;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage01.FrozenRepositoryRequest;
import com.linguan.codemd.stage01.GapExpectationProfileRef;
import com.linguan.codemd.stage01.Stage01Analyzer;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.VerifiedSnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Read-only, exact-field registration resolver. A registration's root is a
 * private transport binding: it is checked before return and never becomes a
 * source-content identity field.
 */
public final class FilesystemSourceRegistry {
    private static final Pattern REGISTRATION_ID = Pattern.compile("source-registration:[0-9a-f]{64}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> REGISTRATION_FIELDS = Set.of("schemaVersion", "registrationId",
            "expectedSnapshotId", "rootlessRequestSha256", "frozenRepositoryRequest", "snapshotRoot");
    private static final Set<String> FROZEN_FIELDS = Set.of("origin", "captureProof", "inventoryScope",
            "files", "verificationPolicyId", "resourceBudget", "capabilityProfileRef");
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    private final Path registryRoot;

    public FilesystemSourceRegistry(Path registryRoot) {
        if (registryRoot == null) {
            throw Stage04Validation.failure(M8FailureCode.SOURCE_REGISTRATION_INVALID);
        }
        this.registryRoot = registryRoot.toAbsolutePath().normalize();
    }

    /** Resolves one canonical registration and verifies its frozen source bytes before return. */
    public FrozenRepositoryRequest resolve(String registrationId) {
        return registration(registrationId).request();
    }

    Registration registrationFor(FrozenRepositoryRequest request) {
        if (request == null) {
            throw Stage04Validation.failure(M8FailureCode.SOURCE_REGISTRATION_INVALID);
        }
        Registration matched = null;
        for (String registrationId : registrationIds()) {
            Registration candidate = registration(registrationId);
            if (candidate.request().equals(request)) {
                if (matched != null) {
                    throw Stage04Validation.failure(M8FailureCode.SOURCE_REGISTRATION_INVALID);
                }
                matched = candidate;
            }
        }
        if (matched == null) {
            throw Stage04Validation.failure(M8FailureCode.SOURCE_REGISTRATION_INVALID);
        }
        return matched;
    }

    Stage01Request stage01ForSnapshot(String snapshotId, GapExpectationProfileRef gapProfile) {
        if (!validSnapshotId(snapshotId) || gapProfile == null) {
            return null;
        }
        Registration matched = null;
        for (String registrationId : registrationIds()) {
            Registration candidate = registration(registrationId);
            if (snapshotId.equals(candidate.expectedSnapshotId())) {
                if (matched != null) {
                    throw Stage04Validation.failure(M8FailureCode.SOURCE_REGISTRATION_INVALID);
                }
                matched = candidate;
            }
        }
        return matched == null ? null : new Stage01Request("stage01-request-v1", matched.request(), gapProfile);
    }

    private Registration registration(String registrationId) {
        if (!REGISTRATION_ID.matcher(registrationId == null ? "" : registrationId).matches()) {
            throw Stage04Validation.failure(M8FailureCode.SOURCE_REGISTRATION_INVALID);
        }
        requireDirectory(registryRoot);
        Path entry = registryRoot.resolve(registrationId + ".json");
        if (!entry.getParent().equals(registryRoot)) {
            throw Stage04Validation.failure(M8FailureCode.SOURCE_REGISTRATION_INVALID);
        }
        if (!Files.exists(entry, LinkOption.NOFOLLOW_LINKS)) {
            throw Stage04Validation.failure(M8FailureCode.SOURCE_REGISTRATION_NOT_FOUND);
        }
        requireRegular(entry);
        try {
            byte[] bytes = CandidateValidationSupport.readBoundedRegular(entry,
                    CandidateValidationSupport.DEFAULT_UNTRUSTED_RECORD_BYTES,
                    M8FailureCode.SOURCE_REGISTRATION_INVALID);
            JsonNode node = parseCanonical(bytes);
            if (!(node instanceof ObjectNode registration) || !fieldNames(registration).equals(REGISTRATION_FIELDS)
                    || !"source-registration-v1".equals(text(registration, "schemaVersion"))
                    || !registrationId.equals(text(registration, "registrationId"))
                    || !validSnapshotId(text(registration, "expectedSnapshotId"))
                    || !SHA256.matcher(text(registration, "rootlessRequestSha256") == null ? "" :
                    text(registration, "rootlessRequestSha256")).matches()
                    || !(registration.get("frozenRepositoryRequest") instanceof ObjectNode frozen)
                    || !fieldNames(frozen).equals(FROZEN_FIELDS) || !registration.hasNonNull("snapshotRoot")
                    || !registration.get("snapshotRoot").isTextual()) {
                throw invalid();
            }
            String expectedRootless = sha256(CandidateValidationSupport.canonicalBytes(frozen));
            if (!expectedRootless.equals(text(registration, "rootlessRequestSha256"))) {
                throw invalid();
            }
            Path root = absoluteRoot(text(registration, "snapshotRoot"));
            ObjectNode complete = frozen.deepCopy();
            complete.put("snapshotRoot", root.toString());
            FrozenRepositoryRequest request = JSON.treeToValue(complete, FrozenRepositoryRequest.class);
            VerifiedSnapshot verified = new Stage01Analyzer().verify(request);
            if (!text(registration, "expectedSnapshotId").equals(verified.snapshotId())) {
                throw Stage04Validation.failure(M8FailureCode.SOURCE_SNAPSHOT_MISMATCH);
            }
            return new Registration(registrationId, text(registration, "expectedSnapshotId"), expectedRootless, request);
        } catch (M8Exception invalid) {
            throw invalid;
        } catch (com.linguan.codemd.stage01.Stage01Exception drift) {
            throw Stage04Validation.failure(M8FailureCode.SOURCE_SNAPSHOT_MISMATCH);
        } catch (IOException | RuntimeException malformed) {
            throw invalid();
        }
    }

    private List<String> registrationIds() {
        requireDirectory(registryRoot);
        try {
            List<String> ids = new ArrayList<>();
            for (Path path : CandidateValidationSupport.readBoundedDirectory(registryRoot,
                    CandidateValidationSupport.DEFAULT_UNTRUSTED_DIRECTORY_ENTRIES,
                    M8FailureCode.SOURCE_REGISTRATION_INVALID)) {
                requireRegular(path);
                String name = path.getFileName().toString();
                if (!name.endsWith(".json")) {
                    throw invalid();
                }
                String id = name.substring(0, name.length() - ".json".length());
                if (!REGISTRATION_ID.matcher(id).matches() || !path.equals(registryRoot.resolve(id + ".json"))) {
                    throw invalid();
                }
                ids.add(id);
            }
            ids.sort(Comparator.naturalOrder());
            return List.copyOf(ids);
        } catch (M8Exception invalid) {
            if (M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED.name().equals(invalid.failureCode())) {
                throw invalid();
            }
            throw invalid;
        }
    }

    private static JsonNode parseCanonical(byte[] bytes) {
        try (JsonParser parser = JSON.getFactory().createParser(CandidateValidationSupport.strictUtf8(bytes))) {
            JsonNode node = JSON.readTree(parser);
            if (node == null || parser.nextToken() != null
                    || !java.util.Arrays.equals(bytes, CandidateValidationSupport.canonicalBytes(node))) {
                throw invalid();
            }
            return node;
        } catch (M8Exception invalid) {
            throw invalid;
        } catch (IOException malformed) {
            throw invalid();
        }
    }

    private static Path absoluteRoot(String value) {
        try {
            Path root = Path.of(value);
            if (!root.isAbsolute()) {
                throw invalid();
            }
            root = root.normalize();
            requireDirectory(root);
            return root;
        } catch (M8Exception invalid) {
            throw invalid;
        } catch (RuntimeException invalid) {
            throw invalid();
        }
    }

    private static void requireDirectory(Path path) {
        requireNoSymlink(path);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw invalid();
        }
    }

    private static void requireRegular(Path path) {
        requireNoSymlink(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw invalid();
        }
    }

    private static void requireNoSymlink(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path root = absolute.getRoot();
        if (root == null) {
            throw invalid();
        }
        Path current = root;
        if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
            throw invalid();
        }
        for (Path segment : root.relativize(absolute)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                if (!configuredTemporaryDirectoryStartsWith(current)) {
                    throw invalid();
                }
            }
        }
    }

    /**
     * macOS may spell the process-controlled temporary root through `/var`, a
     * system alias of `/private/var`.  It is not an input-controlled registry
     * segment.  Every symlink at or below the configured temporary root still
     * fails the walk above.
     */
    private static boolean configuredTemporaryDirectoryStartsWith(Path candidate) {
        try {
            Path temporary = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
            return temporary.startsWith(candidate);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private static Set<String> fieldNames(ObjectNode node) {
        java.util.Set<String> names = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }

    private static String text(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static boolean validSnapshotId(String value) {
        return value != null && value.matches("snapshot:[0-9a-f]{64}");
    }

    private static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException unavailable) {
            throw Stage04Validation.failure(M8FailureCode.CANONICALIZATION_FAILED);
        }
    }

    private static M8Exception invalid() {
        return Stage04Validation.failure(M8FailureCode.SOURCE_REGISTRATION_INVALID);
    }

    record Registration(String registrationId, String expectedSnapshotId, String rootlessRequestSha256,
                        FrozenRepositoryRequest request) {
    }
}
