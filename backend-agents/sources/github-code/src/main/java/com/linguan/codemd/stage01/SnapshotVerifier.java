package com.linguan.codemd.stage01;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Package-private M1 verifier; it opens only caller-declared files with no link following. */
final class SnapshotVerifier {
    private static final String SCHEMA_VERSION = "verified-snapshot-v1";
    private static final String POLICY_ID = "frozen-snapshot-v1";
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> ORIGIN_KINDS = Set.of("GIT_COMMIT", "SYNTHETIC_FIXTURE");
    private static final Set<String> CAPTURE_KINDS = Set.of("UPSTREAM_CAPTURE_RECEIPT",
            "SYNTHETIC_FIXTURE_MANIFEST");
    private static final Set<String> SCOPE_KINDS = Set.of("BOUNDED_PATH_SET", "COMPLETE_CAPTURE");
    private static final Set<String> MEDIA_TYPES = Set.of("MAVEN_POM", "JAVA", "XML", "YAML",
            "PROPERTIES");

    VerifiedSnapshot verify(FrozenRepositoryRequest request) {
        validateRequest(request);
        List<DeclaredFile> files = canonicalFiles(request.files());
        validateCapture(request.origin(), request.captureProof(), request.inventoryScope(), files);
        validateProfile(request.capabilityProfileRef());
        validateBudget(request.resourceBudget(), files);
        Path root = verifiedRoot(request.snapshotRoot());

        List<VerifiedFile> verifiedFiles = new ArrayList<>(files.size());
        long totalBytes = 0;
        for (DeclaredFile declaredFile : files) {
            long remaining = remainingBytes(request.resourceBudget().maxTotalBytes(), totalBytes);
            VerifiedFile verifiedFile = verifyFile(root, declaredFile,
                    Math.min(request.resourceBudget().maxFileBytes(), remaining));
            try {
                totalBytes = Math.addExact(totalBytes, verifiedFile.sizeBytes());
            } catch (ArithmeticException overflow) {
                throw failure(Stage01FailureCode.M1_RESOURCE_LIMIT_EXCEEDED);
            }
            if (totalBytes > request.resourceBudget().maxTotalBytes()) {
                throw failure(Stage01FailureCode.M1_RESOURCE_LIMIT_EXCEEDED);
            }
            verifiedFiles.add(verifiedFile);
        }

        String snapshotId = "snapshot:" + sha256((SCHEMA_VERSION + "\n"
                + snapshotIdentityMaterial(request, verifiedFiles)).getBytes(StandardCharsets.UTF_8));
        return new VerifiedSnapshot(SCHEMA_VERSION, snapshotId, request.origin(),
                request.captureProof(), request.inventoryScope(), request.verificationPolicyId(),
                request.capabilityProfileRef(), request.resourceBudget(), verifiedFiles,
                new SourceIntegrity(files.size(), verifiedFiles.size(), totalBytes));
    }

    /**
     * Re-opens precisely the M1-declared files for M2. Any source drift after M1 is fatal rather
     * than a chance to parse different bytes. This deliberately has no directory traversal.
     */
    Map<String, byte[]> reopenVerifiedBytes(FrozenRepositoryRequest request,
                                             VerifiedSnapshot snapshot) {
        if (request == null || snapshot == null || request.snapshotRoot() == null) {
            throw failure(Stage01FailureCode.VERIFIED_SOURCE_REOPEN_MISMATCH);
        }
        Path root;
        try {
            BasicFileAttributes attributes = Files.readAttributes(request.snapshotRoot(),
                    BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || !attributes.isDirectory()) {
                throw failure(Stage01FailureCode.VERIFIED_SOURCE_REOPEN_MISMATCH);
            }
            root = request.snapshotRoot().normalize();
        } catch (IOException unavailable) {
            throw failure(Stage01FailureCode.VERIFIED_SOURCE_REOPEN_MISMATCH);
        }
        Map<String, byte[]> sourceBytes = new LinkedHashMap<>();
        for (VerifiedFile file : snapshot.files()) {
            try {
                Path source = verifiedSourcePath(root, file.path());
                byte[] bytes = readDeclaredBytes(source, file.sizeBytes());
                if (bytes.length != file.sizeBytes() || !sha256(bytes).equals(file.sha256())) {
                    throw failure(Stage01FailureCode.VERIFIED_SOURCE_REOPEN_MISMATCH);
                }
                sourceBytes.put(file.path(), bytes);
            } catch (Stage01Exception mismatch) {
                if (mismatch.failureCode() == Stage01FailureCode.VERIFIED_SOURCE_REOPEN_MISMATCH) {
                    throw mismatch;
                }
                throw failure(Stage01FailureCode.VERIFIED_SOURCE_REOPEN_MISMATCH);
            }
        }
        return Map.copyOf(sourceBytes);
    }

    private static void validateRequest(FrozenRepositoryRequest request) {
        if (request == null || request.origin() == null || request.captureProof() == null
                || request.inventoryScope() == null || request.files() == null
                || request.resourceBudget() == null || request.capabilityProfileRef() == null) {
            throw failure(Stage01FailureCode.REQUEST_SCHEMA_INVALID);
        }
        Origin origin = request.origin();
        if (!ORIGIN_KINDS.contains(origin.kind()) || !hasText(origin.repositoryUrl())
                || !hasText(origin.revision())) {
            throw failure(Stage01FailureCode.CAPTURE_IDENTITY_INVALID);
        }
        CaptureProof proof = request.captureProof();
        if (!CAPTURE_KINDS.contains(proof.kind()) || !hasText(proof.receiptId())
                || !hasText(proof.boundRepositoryUrl()) || !hasText(proof.boundRevision())
                || !isSha256(proof.inventorySha256()) || !isSha256(proof.receiptSha256())) {
            throw failure(Stage01FailureCode.CAPTURE_IDENTITY_INVALID);
        }
        if (!POLICY_ID.equals(request.verificationPolicyId())) {
            throw failure(Stage01FailureCode.REQUEST_SCHEMA_INVALID);
        }
        InventoryScope scope = request.inventoryScope();
        if (!SCOPE_KINDS.contains(scope.kind()) || !".".equals(scope.scopeRoot())
                || scope.declaredPathCount() <= 0 || scope.declaredPathCount() != request.files().size()) {
            throw failure(Stage01FailureCode.REQUEST_SCHEMA_INVALID);
        }
        if (request.snapshotRoot() == null) {
            throw failure(Stage01FailureCode.SNAPSHOT_ROOT_INVALID);
        }
    }

    private static List<DeclaredFile> canonicalFiles(List<DeclaredFile> declaredFiles) {
        if (declaredFiles.isEmpty()) {
            throw failure(Stage01FailureCode.REQUEST_SCHEMA_INVALID);
        }
        List<DeclaredFile> files = new ArrayList<>(declaredFiles.size());
        for (DeclaredFile file : declaredFiles) {
            if (file == null || !MEDIA_TYPES.contains(file.mediaType())
                    || file.sizeBytes() < 0 || !isSha256(file.sha256())
                    || !"UTF-8".equals(file.textEncoding())) {
                throw failure(Stage01FailureCode.REQUEST_SCHEMA_INVALID);
            }
            validateRepositoryPath(file.path());
            files.add(file);
        }
        files.sort(Comparator.comparing(DeclaredFile::path));
        for (int index = 1; index < files.size(); index++) {
            if (files.get(index - 1).path().equals(files.get(index).path())) {
                throw failure(Stage01FailureCode.DUPLICATE_SOURCE_PATH);
            }
        }
        return List.copyOf(files);
    }

    private static void validateCapture(Origin origin, CaptureProof proof, InventoryScope scope,
                                        List<DeclaredFile> files) {
        if (!origin.repositoryUrl().equals(proof.boundRepositoryUrl())
                || !origin.revision().equals(proof.boundRevision())
                || !expectedCaptureKind(origin.kind()).equals(proof.kind())) {
            throw failure(Stage01FailureCode.CAPTURE_IDENTITY_INVALID);
        }
        String expectedInventory = sha256(("declared-inventory-v1\n"
                + inventoryMaterial(scope, files)).getBytes(StandardCharsets.UTF_8));
        if (!expectedInventory.equals(proof.inventorySha256())) {
            throw failure(Stage01FailureCode.CAPTURE_IDENTITY_INVALID);
        }
        String expectedReceipt = sha256(receiptMaterial(proof).getBytes(StandardCharsets.UTF_8));
        if (!expectedReceipt.equals(proof.receiptSha256())) {
            throw failure(Stage01FailureCode.CAPTURE_IDENTITY_INVALID);
        }
    }

    private static String expectedCaptureKind(String originKind) {
        return "GIT_COMMIT".equals(originKind) ? "UPSTREAM_CAPTURE_RECEIPT"
                : "SYNTHETIC_FIXTURE_MANIFEST";
    }

    private static void validateProfile(CapabilityProfileRef profile) {
        if (!isSha256(profile.profileSha256()) || !CapabilityProfileRegistry.accepts(profile)) {
            throw failure(Stage01FailureCode.PROFILE_REFERENCE_INVALID);
        }
    }

    private static void validateBudget(ResourceBudget budget, List<DeclaredFile> files) {
        if (budget.maxFiles() <= 0 || budget.maxTotalBytes() <= 0 || budget.maxFileBytes() <= 0
                || budget.maxAstNodes() <= 0 || budget.maxXmlNodes() <= 0
                || budget.maxSqlChars() <= 0 || budget.maxControlFlowNodes() <= 0
                || budget.maxRecursionDepth() <= 0) {
            throw failure(Stage01FailureCode.REQUEST_SCHEMA_INVALID);
        }
        if (files.size() > budget.maxFiles()) {
            throw failure(Stage01FailureCode.M1_RESOURCE_LIMIT_EXCEEDED);
        }
        long declaredTotal = 0;
        for (DeclaredFile file : files) {
            if (file.sizeBytes() > budget.maxFileBytes()) {
                throw failure(Stage01FailureCode.M1_RESOURCE_LIMIT_EXCEEDED);
            }
            try {
                declaredTotal = Math.addExact(declaredTotal, file.sizeBytes());
            } catch (ArithmeticException overflow) {
                throw failure(Stage01FailureCode.M1_RESOURCE_LIMIT_EXCEEDED);
            }
            if (declaredTotal > budget.maxTotalBytes()) {
                throw failure(Stage01FailureCode.M1_RESOURCE_LIMIT_EXCEEDED);
            }
        }
    }

    private static Path verifiedRoot(Path root) {
        if (!root.isAbsolute()) {
            throw failure(Stage01FailureCode.SNAPSHOT_ROOT_INVALID);
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(root, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || !attributes.isDirectory()) {
                throw failure(attributes.isSymbolicLink() ? Stage01FailureCode.SYMLINK_FORBIDDEN
                        : Stage01FailureCode.SNAPSHOT_ROOT_INVALID);
            }
            return root.normalize();
        } catch (IOException unavailable) {
            throw failure(Stage01FailureCode.SNAPSHOT_ROOT_INVALID);
        }
    }

    private static VerifiedFile verifyFile(Path root, DeclaredFile declaredFile, long maximumBytes) {
        Path source = verifiedSourcePath(root, declaredFile.path());
        byte[] bytes = readDeclaredBytes(source, maximumBytes);
        if (bytes.length != declaredFile.sizeBytes()) {
            throw failure(Stage01FailureCode.SOURCE_SIZE_MISMATCH);
        }
        if (!sha256(bytes).equals(declaredFile.sha256())) {
            throw failure(Stage01FailureCode.SOURCE_HASH_MISMATCH);
        }
        validateUtf8(bytes);
        LineIndex lineIndex = lineIndex(bytes);
        return new VerifiedFile(declaredFile.path(), declaredFile.mediaType(), bytes.length,
                declaredFile.sha256(), lineIndex.lineCount(), lineIndex.sha256());
    }

    private static Path verifiedSourcePath(Path root, String relativePath) {
        Path current = root;
        String[] segments = relativePath.split("/", -1);
        for (int index = 0; index < segments.length; index++) {
            current = current.resolve(segments[index]);
            BasicFileAttributes attributes = attributes(current, Stage01FailureCode.SOURCE_NOT_REGULAR);
            if (attributes.isSymbolicLink()) {
                throw failure(Stage01FailureCode.SYMLINK_FORBIDDEN);
            }
            boolean finalSegment = index == segments.length - 1;
            if (finalSegment ? !attributes.isRegularFile() : !attributes.isDirectory()) {
                throw failure(Stage01FailureCode.SOURCE_NOT_REGULAR);
            }
        }
        return current;
    }

    private static byte[] readDeclaredBytes(Path source, long maximumBytes) {
        if (maximumBytes < 0) {
            throw failure(Stage01FailureCode.M1_RESOURCE_LIMIT_EXCEEDED);
        }
        MessageDigest digest = sha256Digest();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        long readBytes = 0;
        try (SeekableByteChannel channel = Files.newByteChannel(source, options)) {
            ByteBuffer buffer = ByteBuffer.allocate(8_192);
            while (channel.read(buffer) >= 0) {
                buffer.flip();
                int count = buffer.remaining();
                if (count > 0) {
                    try {
                        readBytes = Math.addExact(readBytes, count);
                    } catch (ArithmeticException overflow) {
                        throw failure(Stage01FailureCode.M1_RESOURCE_LIMIT_EXCEEDED);
                    }
                    if (readBytes > maximumBytes) {
                        throw failure(Stage01FailureCode.M1_RESOURCE_LIMIT_EXCEEDED);
                    }
                    digest.update(buffer.array(), buffer.arrayOffset() + buffer.position(), count);
                    output.write(buffer.array(), buffer.arrayOffset() + buffer.position(), count);
                }
                buffer.clear();
            }
        } catch (IOException unreadable) {
            throw failure(Stage01FailureCode.SOURCE_NOT_REGULAR);
        }
        BasicFileAttributes attributes = attributes(source, Stage01FailureCode.SOURCE_NOT_REGULAR);
        if (attributes.isSymbolicLink()) {
            throw failure(Stage01FailureCode.SYMLINK_FORBIDDEN);
        }
        if (!attributes.isRegularFile()) {
            throw failure(Stage01FailureCode.SOURCE_NOT_REGULAR);
        }
        byte[] bytes = output.toByteArray();
        String observedHash = HexFormat.of().formatHex(digest.digest());
        if (!observedHash.equals(sha256(bytes))) {
            throw failure(Stage01FailureCode.SOURCE_HASH_MISMATCH);
        }
        return bytes;
    }

    private static long remainingBytes(long maximum, long consumed) {
        try {
            long remaining = Math.subtractExact(maximum, consumed);
            if (remaining < 0) {
                throw failure(Stage01FailureCode.M1_RESOURCE_LIMIT_EXCEEDED);
            }
            return remaining;
        } catch (ArithmeticException overflow) {
            throw failure(Stage01FailureCode.M1_RESOURCE_LIMIT_EXCEEDED);
        }
    }

    private static BasicFileAttributes attributes(Path path, Stage01FailureCode unavailableCode) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException unavailable) {
            throw failure(unavailableCode);
        }
    }

    private static void validateUtf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException invalid) {
            throw failure(Stage01FailureCode.SOURCE_UTF8_INVALID);
        }
    }

    private static LineIndex lineIndex(byte[] bytes) {
        List<LineBoundary> lines = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < bytes.length; index++) {
            if (bytes[index] == '\n') {
                int end = index > start && bytes[index - 1] == '\r' ? index - 1 : index;
                lines.add(new LineBoundary(lines.size() + 1, start, end));
                start = index + 1;
            }
        }
        if (start < bytes.length || lines.isEmpty()) {
            lines.add(new LineBoundary(lines.size() + 1, start, bytes.length));
        }
        StringBuilder canonical = new StringBuilder("[");
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                canonical.append(',');
            }
            LineBoundary line = lines.get(index);
            canonical.append('[').append(line.number()).append(',').append(line.startByte())
                    .append(',').append(line.endByteExclusive()).append(']');
        }
        return new LineIndex(lines.size(), sha256(canonical.append(']').toString()
                .getBytes(StandardCharsets.UTF_8)));
    }

    private static String inventoryMaterial(InventoryScope scope, List<DeclaredFile> files) {
        StringBuilder json = new StringBuilder("{\"files\":[");
        for (int index = 0; index < files.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            DeclaredFile file = files.get(index);
            json.append("{\"mediaType\":").append(quoted(file.mediaType()))
                    .append(",\"path\":").append(quoted(file.path()))
                    .append(",\"sha256\":").append(quoted(file.sha256()))
                    .append(",\"sizeBytes\":").append(file.sizeBytes())
                    .append(",\"textEncoding\":").append(quoted(file.textEncoding()))
                    .append('}');
        }
        return json.append("],\"scope\":").append(scopeMaterial(scope)).append('}').toString();
    }

    private static String receiptMaterial(CaptureProof proof) {
        return "{\"boundRepositoryUrl\":" + quoted(proof.boundRepositoryUrl())
                + ",\"boundRevision\":" + quoted(proof.boundRevision())
                + ",\"inventorySha256\":" + quoted(proof.inventorySha256())
                + ",\"kind\":" + quoted(proof.kind())
                + ",\"receiptId\":" + quoted(proof.receiptId()) + '}';
    }

    private static String snapshotIdentityMaterial(FrozenRepositoryRequest request,
                                                   List<VerifiedFile> files) {
        StringBuilder json = new StringBuilder("{\"capabilityProfileRef\":")
                .append(profileMaterial(request.capabilityProfileRef()))
                .append(",\"captureProof\":").append(captureMaterial(request.captureProof()))
                .append(",\"files\":[");
        for (int index = 0; index < files.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            VerifiedFile file = files.get(index);
            json.append("{\"mediaType\":").append(quoted(file.mediaType()))
                    .append(",\"path\":").append(quoted(file.path()))
                    .append(",\"sha256\":").append(quoted(file.sha256()))
                    .append(",\"sizeBytes\":").append(file.sizeBytes()).append('}');
        }
        return json.append("],\"inventoryScope\":").append(scopeMaterial(request.inventoryScope()))
                .append(",\"origin\":").append(originMaterial(request.origin()))
                .append(",\"resourceBudget\":").append(budgetMaterial(request.resourceBudget()))
                .append(",\"verificationPolicyId\":").append(quoted(request.verificationPolicyId()))
                .append('}').toString();
    }

    private static String originMaterial(Origin origin) {
        return "{\"kind\":" + quoted(origin.kind()) + ",\"repositoryUrl\":"
                + quoted(origin.repositoryUrl()) + ",\"revision\":" + quoted(origin.revision()) + '}';
    }

    private static String captureMaterial(CaptureProof proof) {
        return "{\"boundRepositoryUrl\":" + quoted(proof.boundRepositoryUrl())
                + ",\"boundRevision\":" + quoted(proof.boundRevision())
                + ",\"inventorySha256\":" + quoted(proof.inventorySha256())
                + ",\"kind\":" + quoted(proof.kind()) + ",\"receiptId\":"
                + quoted(proof.receiptId()) + ",\"receiptSha256\":" + quoted(proof.receiptSha256())
                + '}';
    }

    private static String scopeMaterial(InventoryScope scope) {
        return "{\"declaredPathCount\":" + scope.declaredPathCount() + ",\"kind\":"
                + quoted(scope.kind()) + ",\"scopeRoot\":" + quoted(scope.scopeRoot()) + '}';
    }

    private static String profileMaterial(CapabilityProfileRef profile) {
        return "{\"profileId\":" + quoted(profile.profileId()) + ",\"profileSha256\":"
                + quoted(profile.profileSha256()) + '}';
    }

    private static String budgetMaterial(ResourceBudget budget) {
        return "{\"maxAstNodes\":" + budget.maxAstNodes() + ",\"maxControlFlowNodes\":"
                + budget.maxControlFlowNodes() + ",\"maxFileBytes\":" + budget.maxFileBytes()
                + ",\"maxFiles\":" + budget.maxFiles() + ",\"maxRecursionDepth\":"
                + budget.maxRecursionDepth() + ",\"maxSqlChars\":" + budget.maxSqlChars()
                + ",\"maxTotalBytes\":" + budget.maxTotalBytes() + ",\"maxXmlNodes\":"
                + budget.maxXmlNodes() + '}';
    }

    private static void validateRepositoryPath(String path) {
        if (!hasText(path) || path.startsWith("/") || path.indexOf('\\') >= 0) {
            throw failure(Stage01FailureCode.SOURCE_PATH_INVALID);
        }
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw failure(Stage01FailureCode.SOURCE_PATH_INVALID);
            }
        }
        try {
            Path parsed = Path.of(path);
            if (parsed.isAbsolute() || !parsed.normalize().toString().replace('\\', '/')
                    .equals(path)) {
                throw failure(Stage01FailureCode.SOURCE_PATH_INVALID);
            }
        } catch (java.nio.file.InvalidPathException invalid) {
            throw failure(Stage01FailureCode.SOURCE_PATH_INVALID);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isSha256(String value) {
        return value != null && SHA_256.matcher(value).matches();
    }

    private static String quoted(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(sha256Digest().digest(bytes));
    }

    private static Stage01Exception failure(Stage01FailureCode code) {
        return new Stage01Exception(code);
    }

    private record LineBoundary(int number, int startByte, int endByteExclusive) {
    }

    private record LineIndex(int lineCount, String sha256) {
    }
}
