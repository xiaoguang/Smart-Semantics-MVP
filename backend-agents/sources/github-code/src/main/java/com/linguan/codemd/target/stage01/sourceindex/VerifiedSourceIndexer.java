package com.linguan.codemd.target.stage01.sourceindex;

import com.linguan.codemd.target.artifacts.ArtifactControls;
import com.linguan.codemd.target.artifacts.ArtifactPolicyKey;
import com.linguan.codemd.target.artifacts.ArtifactReference;
import com.linguan.codemd.target.artifacts.CanonicalArtifactPolicy;
import com.linguan.codemd.target.artifacts.CanonicalArtifactPolicyRegistry;
import com.linguan.codemd.target.artifacts.CanonicalModuleArtifactStore;
import com.linguan.codemd.target.artifacts.CanonicalModulePayload;
import com.linguan.codemd.target.artifacts.ImmutableBytes;
import com.linguan.codemd.target.artifacts.ModuleArtifactEnvelope;
import com.linguan.codemd.target.artifacts.ModuleArtifactEnvelopeDraft;
import com.linguan.codemd.target.artifacts.ModuleInstallRequest;
import com.linguan.codemd.target.artifacts.ModulePublicationReference;
import com.linguan.codemd.target.artifacts.ReopenedModulePublication;
import com.linguan.codemd.target.stage01.capture.AnalysisDisposition;
import com.linguan.codemd.target.stage01.requestadmission.AdmittedSourceFile;
import com.linguan.codemd.target.stage01.requestadmission.AdmittedSourceRequest;
import com.linguan.codemd.target.stage01.requestadmission.AdmittedSourceRequestArtifact;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Stage01 M2: reopens M1, proves every declared byte sequence still matches, and persists the
 * complete rootless index for M3.
 */
public final class VerifiedSourceIndexer {
    private static final ArtifactPolicyKey M1_POLICY =
            new ArtifactPolicyKey("STAGE01_ADMITTED_SOURCE_REQUEST", "stage01-admitted-source-request-v2");
    private static final ArtifactPolicyKey M2_POLICY =
            new ArtifactPolicyKey("STAGE01_VERIFIED_SOURCE_INDEX", "stage01-verified-source-index-v2");

    public ModulePublicationReference index(
            ModulePublicationReference m1Reference,
            RegisteredSnapshotRegistry snapshots,
            SourceIndexPublicationInput publication,
            CanonicalArtifactPolicyRegistry policies,
            CanonicalModuleArtifactStore store) {
        if (m1Reference == null || snapshots == null || publication == null || policies == null || store == null) {
            throw new SourceIndexException("REQUEST_SCHEMA_INVALID", "M2 arguments must not be null");
        }
        CanonicalArtifactPolicy m1Policy = policies.resolve(M1_POLICY);
        CanonicalArtifactPolicy m2Policy = policies.resolve(M2_POLICY);
        ReopenedModulePublication m1 = store.reopen(m1Reference);
        if (!(m1.reference().address() instanceof com.linguan.codemd.target.artifacts.StageModuleAddress address)
                || address.stageNumber() != 1
                || address.moduleNumber() != 1
                || !"freeze-source".equals(address.stageKey())
                || !"request-admission".equals(address.moduleKey())
                || m1.payloads().size() != 1
                || !publication.controls().equals(m1.receipt().controls())) {
            throw new SourceIndexException("SOURCE_HANDLE_INVALID", "M1 publication cannot feed M2");
        }
        var m1Payload = m1.payloads().get(0);
        if (!M1_POLICY.artifactType().equals(m1Payload.descriptor().artifactType())
                || !M1_POLICY.schemaVersion().equals(m1Payload.descriptor().schemaVersion())) {
            throw new SourceIndexException("SOURCE_HANDLE_INVALID", "M1 payload policy differs from M2 input");
        }
        ModuleArtifactEnvelope m1Envelope = ModuleArtifactEnvelope.parse(m1Payload.canonicalUtf8(), m1Policy);
        AdmittedSourceRequest request = AdmittedSourceRequestArtifact.parse(m1Envelope.payload());
        ArtifactReference m1PayloadReference = new ArtifactReference(
                m1Payload.descriptor().artifactId(), m1Payload.descriptor().sha256());
        ArtifactReference sourceRegistrationReference = sourceRegistrationReference(m1Envelope, request);
        RegisteredSnapshotRegistry.RegisteredSnapshotHandle snapshot = resolve(snapshots, request.sourceRegistrationId());
        String snapshotId = snapshot.snapshotId();
        if (snapshotId == null || !snapshotId.matches("snapshot:[0-9a-f]{64}")) {
            throw new SourceIndexException("SOURCE_HANDLE_INVALID", "registered snapshot identity is invalid");
        }

        List<VerifiedSourceFile> files = new ArrayList<>();
        for (AdmittedSourceFile admitted : request.files()) {
            files.add(verifyFile(snapshot, admitted));
        }
        files.sort(Comparator.comparing(VerifiedSourceFile::path));
        List<String> allFileIds = files.stream().map(VerifiedSourceFile::fileId).sorted().toList();
        String shardId = "source-shard:" + framedDigest("stage01-source-shard-v2", allFileIds);
        SourceShardReceipt shard = new SourceShardReceipt(shardId, allFileIds, allFileIds, "SUCCEEDED", List.of());
        long textCount = files.stream()
                .filter(file -> file.analysisDisposition() == AnalysisDisposition.ANALYZABLE_TEXT)
                .count();
        VerifiedSourceIndex index = new VerifiedSourceIndex(
                snapshotId,
                m1PayloadReference.artifactId(),
                files.size(),
                textCount,
                files.size() - textCount,
                files,
                List.of(shard),
                "VERIFIED");
        ModuleArtifactEnvelope envelope = ModuleArtifactEnvelope.write(
                new ModuleArtifactEnvelopeDraft(
                        publication.address(),
                        publication.moduleVersion(),
                        sortedReferences(m1PayloadReference, sourceRegistrationReference),
                        publication.controls(),
                        m1.receipt().status(),
                        m1.receipt().gapRefs(),
                        VerifiedSourceIndexArtifact.write(index)),
                m2Policy);
        ModulePublicationReference reference = store.install(new ModuleInstallRequest(
                        publication.address(),
                        publication.moduleVersion(),
                        sortedReferences(m1PayloadReference, sourceRegistrationReference),
                        publication.controls(),
                        m1.receipt().status(),
                        m1.receipt().gapRefs(),
                        List.of(new CanonicalModulePayload(
                                "verified-source-index.json",
                                m2Policy.key().artifactType(),
                                m2Policy.key().schemaVersion(),
                                envelope.reference().artifactId(),
                                m2Policy.mediaType(),
                                envelope.canonicalUtf8()))))
                .reference();
        ReopenedModulePublication reopened = store.reopen(reference);
        if (reopened.payloads().size() != 1) {
            throw new SourceIndexException("SOURCE_HANDLE_INVALID", "M2 reopen has unexpected payload count");
        }
        ModuleArtifactEnvelope reopenedEnvelope =
                ModuleArtifactEnvelope.parse(reopened.payloads().get(0).canonicalUtf8(), m2Policy);
        if (!index.equals(VerifiedSourceIndexArtifact.parse(reopenedEnvelope.payload()))) {
            throw new SourceIndexException("SOURCE_HANDLE_INVALID", "M2 reopen differs from verified index");
        }
        return reference;
    }

    private static ArtifactReference sourceRegistrationReference(
            ModuleArtifactEnvelope envelope, AdmittedSourceRequest request) {
        List<ArtifactReference> matches = envelope.draft().upstreamArtifacts().stream()
                .filter(reference -> request.sourceRegistrationId().equals(reference.artifactId()))
                .toList();
        if (matches.size() != 1) {
            throw new SourceIndexException(
                    "SOURCE_HANDLE_INVALID", "M1 must bind exactly one source registration artifact");
        }
        return matches.get(0);
    }

    private static RegisteredSnapshotRegistry.RegisteredSnapshotHandle resolve(
            RegisteredSnapshotRegistry snapshots, String sourceRegistrationId) {
        try {
            RegisteredSnapshotRegistry.RegisteredSnapshotHandle handle = snapshots.resolve(sourceRegistrationId);
            if (handle == null) {
                throw new SourceIndexException("SOURCE_REGISTRATION_NOT_FOUND", "registered snapshot is unavailable");
            }
            return handle;
        } catch (SourceIndexException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SourceIndexException("SOURCE_REGISTRATION_NOT_FOUND", "registered snapshot is unavailable", exception);
        }
    }

    private static VerifiedSourceFile verifyFile(
            RegisteredSnapshotRegistry.RegisteredSnapshotHandle snapshot, AdmittedSourceFile admitted) {
        RegisteredSourceFileMetadata before = inspect(snapshot, admitted.fileId());
        requireExpectedMetadata(before, admitted);
        ImmutableBytes immutable;
        try {
            immutable = snapshot.read(admitted.fileId());
        } catch (RuntimeException exception) {
            throw new SourceIndexException("SOURCE_HANDLE_INVALID", "registered source bytes cannot be read", exception);
        }
        if (immutable == null) {
            throw new SourceIndexException("SOURCE_HANDLE_INVALID", "registered source bytes are unavailable");
        }
        byte[] bytes = immutable.copyToByteArray();
        if (bytes.length != admitted.sizeBytes()) {
            throw new SourceIndexException("SOURCE_SIZE_MISMATCH", "registered source size differs from M1");
        }
        if (!sha256(bytes).equals(admitted.sha256())) {
            throw new SourceIndexException("SOURCE_HASH_MISMATCH", "registered source hash differs from M1");
        }
        RegisteredSourceFileMetadata after = inspect(snapshot, admitted.fileId());
        requireExpectedMetadata(after, admitted);
        if (!before.equals(after)) {
            throw new SourceIndexException("SOURCE_HASH_MISMATCH", "registered source identity changed during read");
        }
        if (admitted.analysisDisposition() == AnalysisDisposition.ANALYZABLE_TEXT) {
            requireStrictText(bytes);
            return new VerifiedSourceFile(
                    admitted.fileId(),
                    admitted.path(),
                    admitted.gitMode(),
                    admitted.mediaType(),
                    admitted.sizeBytes(),
                    admitted.sha256(),
                    admitted.analysisDisposition(),
                    admitted.textEncoding(),
                    lineIndexDigest(bytes));
        }
        if (isStrictText(bytes)) {
            throw new SourceIndexException(
                    "SOURCE_TEXT_DISPOSITION_INVALID", "M1 media disposition differs from registered source bytes");
        }
        return new VerifiedSourceFile(
                admitted.fileId(),
                admitted.path(),
                admitted.gitMode(),
                admitted.mediaType(),
                admitted.sizeBytes(),
                admitted.sha256(),
                admitted.analysisDisposition(),
                null,
                null);
    }

    private static RegisteredSourceFileMetadata inspect(
            RegisteredSnapshotRegistry.RegisteredSnapshotHandle snapshot, String fileId) {
        try {
            RegisteredSourceFileMetadata metadata = snapshot.inspect(fileId);
            if (metadata == null) {
                throw new SourceIndexException("SOURCE_HANDLE_INVALID", "registered source metadata is unavailable");
            }
            return metadata;
        } catch (SourceIndexException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SourceIndexException("SOURCE_HANDLE_INVALID", "registered source metadata cannot be inspected", exception);
        }
    }

    private static void requireExpectedMetadata(RegisteredSourceFileMetadata metadata, AdmittedSourceFile admitted) {
        if (!metadata.regularFile()) {
            throw new SourceIndexException("SOURCE_NOT_REGULAR", "registered source is not a regular file");
        }
        if (metadata.sizeBytes() != admitted.sizeBytes()) {
            throw new SourceIndexException("SOURCE_SIZE_MISMATCH", "registered source size differs from M1");
        }
        if (!metadata.sha256().equals(admitted.sha256())) {
            throw new SourceIndexException("SOURCE_HASH_MISMATCH", "registered source hash differs from M1");
        }
    }

    private static void requireStrictText(byte[] bytes) {
        if (!isStrictText(bytes)) {
            throw new SourceIndexException(
                    "SOURCE_TEXT_DISPOSITION_INVALID", "M1 text disposition differs from registered source bytes");
        }
    }

    private static boolean isStrictText(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException exception) {
            return false;
        }
        for (byte value : bytes) {
            int unsigned = Byte.toUnsignedInt(value);
            if (unsigned == 0
                    || unsigned == 127
                    || (unsigned < 32 && unsigned != '\t' && unsigned != '\n' && unsigned != '\r')) {
                return false;
            }
        }
        return true;
    }

    private static String lineIndexDigest(byte[] bytes) {
        List<Long> lineStarts = new ArrayList<>();
        lineStarts.add(0L);
        for (int index = 0; index < bytes.length; index++) {
            if (bytes[index] == '\n' && index + 1 < bytes.length) {
                lineStarts.add((long) index + 1L);
            }
        }
        ByteArrayOutputStream material = new ByteArrayOutputStream();
        writeFrame(material, "stage01-line-index-v2".getBytes(StandardCharsets.UTF_8));
        writeFrame(material, ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array());
        for (long start : lineStarts) {
            writeFrame(material, ByteBuffer.allocate(Long.BYTES).putLong(start).array());
        }
        return sha256(material.toByteArray());
    }

    private static List<ArtifactReference> sortedReferences(ArtifactReference... references) {
        return java.util.Arrays.stream(references)
                .sorted(Comparator.comparing(ArtifactReference::artifactId))
                .toList();
    }

    private static String framedDigest(String domain, List<String> values) {
        ByteArrayOutputStream material = new ByteArrayOutputStream();
        writeFrame(material, domain.getBytes(StandardCharsets.UTF_8));
        for (String value : values) {
            writeFrame(material, value.getBytes(StandardCharsets.UTF_8));
        }
        return sha256(material.toByteArray());
    }

    private static void writeFrame(ByteArrayOutputStream output, byte[] bytes) {
        output.writeBytes(ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array());
        output.writeBytes(bytes);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
