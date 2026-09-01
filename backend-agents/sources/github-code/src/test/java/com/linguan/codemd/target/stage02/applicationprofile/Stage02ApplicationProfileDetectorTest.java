package com.linguan.codemd.target.stage02.applicationprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguan.codemd.target.artifacts.ArtifactControls;
import com.linguan.codemd.target.artifacts.ArtifactPolicyKey;
import com.linguan.codemd.target.artifacts.ArtifactPolicyRegistryReference;
import com.linguan.codemd.target.artifacts.ArtifactReference;
import com.linguan.codemd.target.artifacts.ArtifactStoreLimits;
import com.linguan.codemd.target.artifacts.CanonicalArtifactPolicy;
import com.linguan.codemd.target.artifacts.CanonicalArtifactPolicyRegistry;
import com.linguan.codemd.target.artifacts.CanonicalJsonCodec;
import com.linguan.codemd.target.artifacts.CanonicalModuleArtifactStore;
import com.linguan.codemd.target.artifacts.CanonicalModulePayload;
import com.linguan.codemd.target.artifacts.CanonicalStageArtifactStore;
import com.linguan.codemd.target.artifacts.FileSystemCanonicalModuleArtifactStore;
import com.linguan.codemd.target.artifacts.FileSystemCanonicalStageArtifactStore;
import com.linguan.codemd.target.artifacts.ImmutableBytes;
import com.linguan.codemd.target.artifacts.ModuleArtifactEnvelope;
import com.linguan.codemd.target.artifacts.ModuleArtifactEnvelopeDraft;
import com.linguan.codemd.target.artifacts.ModuleInstallRequest;
import com.linguan.codemd.target.artifacts.ModulePublicationReference;
import com.linguan.codemd.target.artifacts.RunStoreBootstrap;
import com.linguan.codemd.target.artifacts.RunStoreHandle;
import com.linguan.codemd.target.artifacts.StageModuleAddress;
import com.linguan.codemd.target.artifacts.StagePublicationAddress;
import com.linguan.codemd.target.stage01.capture.AnalysisDisposition;
import com.linguan.codemd.target.stage01.publish.Stage01InputArtifactRegistry;
import com.linguan.codemd.target.stage01.publish.Stage01PublicationSpecificationInput;
import com.linguan.codemd.target.stage01.publish.Stage01PublicationSpecifier;
import com.linguan.codemd.target.stage01.publish.Stage01Reference;
import com.linguan.codemd.target.stage01.requestadmission.AdmittedSourceFile;
import com.linguan.codemd.target.stage01.requestadmission.AdmittedSourceRequest;
import com.linguan.codemd.target.stage01.requestadmission.AdmittedSourceRequestArtifact;
import com.linguan.codemd.target.stage01.requestadmission.InventoryScope;
import com.linguan.codemd.target.stage01.sourceindex.RegisteredSnapshotRegistry;
import com.linguan.codemd.target.stage01.sourceindex.RegisteredSourceFileMetadata;
import com.linguan.codemd.target.stage01.sourceindex.SourceShardReceipt;
import com.linguan.codemd.target.stage01.sourceindex.VerifiedSourceFile;
import com.linguan.codemd.target.stage01.sourceindex.VerifiedSourceIndex;
import com.linguan.codemd.target.stage01.sourceindex.VerifiedSourceIndexArtifact;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Stage02ApplicationProfileDetectorTest {
    private static final String RUN_ID = "analysis-run:" + "a".repeat(64);

    @TempDir Path temporaryDirectory;

    @Test
    void detectsJavaSpringMvcAndMyBatisOnlyFromTheFrozenStage01Publication() throws Exception {
        try (Fixture fixture = Fixture.create(temporaryDirectory)) {
            ApplicationProfileDetection detection = new ApplicationProfileDetector(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .detect(fixture.stage01, DiscoveryProfile.javaSpringMvcMyBatisV2());

            assertThat(detection.profile().language()).isEqualTo(ApplicationLanguage.JAVA);
            assertThat(detection.profile().languageVersion()).isEqualTo(17);
            assertThat(detection.profile().inventoryScopeKind()).isEqualTo("COMPLETE_CAPTURE");
            assertThat(detection.profile().repositoryCompletionEligible()).isTrue();
            assertThat(detection.profile().frameworkSignals())
                    .extracting(FrameworkSignal::kind)
                    .containsExactly(FrameworkSignalKind.MYBATIS, FrameworkSignalKind.SPRING_MVC);
            assertThat(detection.profile().configSignals())
                    .extracting(ConfigSignal::kind)
                    .containsExactly(ConfigSignalKind.MYBATIS_MAPPER_LOCATION);
            assertThat(detection.profile().configSignals().get(0).value())
                    .isEqualTo("classpath*:mapper/**/*.xml");
            assertThat(fixture.moduleStore.reopen(detection.draftPublication()).payloads())
                    .hasSize(1)
                    .extracting(payload -> payload.descriptor().fileName())
                    .containsExactly("application-profile-draft.json");
        }
    }

    @Test
    void rejectsConflictingFrozenMavenJavaReleasesInsteadOfChoosingOne() throws Exception {
        Map<String, byte[]> files = Fixture.sourceFiles();
        files.put(
                "module-two/pom.xml",
                new String(files.get("pom.xml"), StandardCharsets.UTF_8)
                        .replace("<maven.compiler.release>17</maven.compiler.release>", "<maven.compiler.release>21</maven.compiler.release>")
                        .getBytes(StandardCharsets.UTF_8));
        try (Fixture fixture = Fixture.create(temporaryDirectory, files)) {
            assertThatThrownBy(() -> new ApplicationProfileDetector(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .detect(fixture.stage01, DiscoveryProfile.javaSpringMvcMyBatisV2()))
                    .isInstanceOf(ApplicationProfileException.class)
                    .extracting(throwable -> ((ApplicationProfileException) throwable).code())
                    .isEqualTo("APPLICATION_PROFILE_CONFLICT");
        }
    }

    @Test
    void producesTheSamePersistedDraftFromTheSameFrozenBytesInDifferentStoreRoots() throws Exception {
        Path firstRoot = Files.createDirectory(temporaryDirectory.resolve("first"));
        Path secondRoot = Files.createDirectory(temporaryDirectory.resolve("second"));
        try (Fixture first = Fixture.create(firstRoot); Fixture second = Fixture.create(secondRoot)) {
            ApplicationProfileDetection firstResult = new ApplicationProfileDetector(
                            first.stageStore, first.moduleStore, first.policies, first.sourceRegistry)
                    .detect(first.stage01, DiscoveryProfile.javaSpringMvcMyBatisV2());
            ApplicationProfileDetection secondResult = new ApplicationProfileDetector(
                            second.stageStore, second.moduleStore, second.policies, second.sourceRegistry)
                    .detect(second.stage01, DiscoveryProfile.javaSpringMvcMyBatisV2());

            assertThat(firstResult.profile()).isEqualTo(secondResult.profile());
            assertThat(first.moduleStore.reopen(firstResult.draftPublication()).payloads().get(0).canonicalUtf8().copyToByteArray())
                    .isEqualTo(second.moduleStore
                            .reopen(secondResult.draftPublication())
                            .payloads()
                            .get(0)
                            .canonicalUtf8()
                            .copyToByteArray());
        }
    }

    @Test
    void keepsFrozenBinaryFilesInTheRepositoryDenominatorWithoutTryingToDecodeThem() throws Exception {
        Map<String, byte[]> files = Fixture.sourceFiles();
        files.put("static/logo.bin", new byte[] {0, 1, 2, 3, 4});
        try (Fixture fixture = Fixture.create(temporaryDirectory, files)) {
            ApplicationProfileDetection detection = new ApplicationProfileDetector(
                            fixture.stageStore, fixture.moduleStore, fixture.policies, fixture.sourceRegistry)
                    .detect(fixture.stage01, DiscoveryProfile.javaSpringMvcMyBatisV2());

            assertThat(detection.profile().language()).isEqualTo(ApplicationLanguage.JAVA);
        }
    }

    static final class Fixture implements AutoCloseable {
        final RunStoreHandle handle;
        final CanonicalArtifactPolicyRegistry policies;
        final CanonicalModuleArtifactStore moduleStore;
        final CanonicalStageArtifactStore stageStore;
        final Stage01Reference stage01;
        final RegisteredSnapshotRegistry sourceRegistry;

        private Fixture(
                RunStoreHandle handle,
                CanonicalArtifactPolicyRegistry policies,
                CanonicalModuleArtifactStore moduleStore,
                CanonicalStageArtifactStore stageStore,
                Stage01Reference stage01,
                RegisteredSnapshotRegistry sourceRegistry) {
            this.handle = handle;
            this.policies = policies;
            this.moduleStore = moduleStore;
            this.stageStore = stageStore;
            this.stage01 = stage01;
            this.sourceRegistry = sourceRegistry;
        }

        static Fixture create(Path directory) throws Exception {
            return create(directory, sourceFiles());
        }

        static Fixture create(Path directory, Map<String, byte[]> sourceFiles) throws Exception {
            CanonicalJsonCodec codec = new CanonicalJsonCodec();
            ArtifactPolicyRegistryReference policyReference =
                    new ArtifactPolicyRegistryReference("artifact-policy-registry:" + "b".repeat(64), "b".repeat(64));
            CanonicalArtifactPolicy requestPolicy = policy(
                    "STAGE01_ADMITTED_SOURCE_REQUEST", "stage01-admitted-source-request-v2", "source-request", "MODULE_ARTIFACT_JSON");
            CanonicalArtifactPolicy indexPolicy = policy(
                    "STAGE01_VERIFIED_SOURCE_INDEX", "stage01-verified-source-index-v2", "source-index", "MODULE_ARTIFACT_JSON");
            CanonicalArtifactPolicy sourceInputPolicy =
                    policy("STAGE01_SOURCE_INPUT", "stage01-source-input-v2", "stage01-source-input", "STANDALONE_JSON");
            CanonicalArtifactPolicy inventoryPolicy = policy(
                    "STAGE01_SOURCE_INVENTORY", "stage01-source-inventory-v2", "stage01-source-inventory", "CANONICAL_JSONL");
            CanonicalArtifactPolicy snapshotPolicy =
                    policy("VERIFIED_SNAPSHOT", "verified-snapshot-v2", "verified-snapshot", "STANDALONE_JSON");
            CanonicalArtifactPolicy profileDraftPolicy = policy(
                    "STAGE02_APPLICATION_PROFILE_DRAFT", "stage02-application-profile-draft-v2", "application-profile", "MODULE_ARTIFACT_JSON");
            CanonicalArtifactPolicy httpEntryPolicy = policy(
                    "STAGE02_HTTP_ENTRY_DISCOVERY", "stage02-http-entry-discovery-v2", "http-entry-discovery", "MODULE_ARTIFACT_JSON");
            CanonicalArtifactPolicy mapperCatalogPolicy = policy(
                    "STAGE02_MAPPER_CATALOG_DRAFT", "stage02-mapper-catalog-draft-v2", "mapper-catalog", "MODULE_ARTIFACT_JSON");
            CanonicalArtifactPolicy publishedProfilePolicy = policy(
                    "STAGE02_APPLICATION_PROFILE", "stage02-application-profile-v2", "application-profile", "STANDALONE_JSON");
            CanonicalArtifactPolicy publishedEntriesPolicy = policy(
                    "STAGE02_ENTRY_POINTS", "stage02-entry-points-v2", "entry-points", "CANONICAL_JSONL", true);
            CanonicalArtifactPolicy publishedMapperPolicy = policy(
                    "STAGE02_MAPPER_CATALOG", "stage02-mapper-catalog-v2", "mapper-catalog", "CANONICAL_JSONL", true);
            CanonicalArtifactPolicy capabilityReportPolicy = policy(
                    "STAGE02_CAPABILITY_REPORT", "stage02-capability-report-v2", "capability-report", "STANDALONE_JSON");
            CanonicalArtifactPolicyRegistry policies = registry(
                    policyReference,
                    requestPolicy,
                    indexPolicy,
                    sourceInputPolicy,
                    inventoryPolicy,
                    snapshotPolicy,
                    profileDraftPolicy,
                    httpEntryPolicy,
                    mapperCatalogPolicy,
                    publishedProfilePolicy,
                    publishedEntriesPolicy,
                    publishedMapperPolicy,
                    capabilityReportPolicy);
            ArtifactControls controls =
                    new ArtifactControls("1".repeat(64), "2".repeat(64), "3".repeat(64), "4".repeat(64), policyReference);
            RunStoreHandle handle = RunStoreBootstrap.openForTest(directory);
            CanonicalModuleArtifactStore moduleStore = new FileSystemCanonicalModuleArtifactStore(
                    handle, codec, policies, new ArtifactStoreLimits(4, 65_536, 200_000, 16));
            CanonicalStageArtifactStore stageStore = new FileSystemCanonicalStageArtifactStore(
                    handle, codec, policies, new ArtifactStoreLimits(4, 65_536, 200_000, 16));
            Map<String, byte[]> files = new LinkedHashMap<>(sourceFiles);
            List<AdmittedSourceFile> admitted = files.entrySet().stream()
                    .map(entry -> admitted(entry.getKey(), entry.getValue()))
                    .sorted(Comparator.comparing(AdmittedSourceFile::path))
                    .toList();
            ArtifactReference registration = ref("source-registration", '7');
            ArtifactReference captureReceipt = ref("capture-receipt", '8');
            ArtifactReference manifest = ref("snapshot-manifest", '9');
            AdmittedSourceRequest request = new AdmittedSourceRequest(
                    "source-request:" + "c".repeat(64),
                    registration.artifactId(),
                    "https://example.invalid/customer.git",
                    "d".repeat(40),
                    new InventoryScope(InventoryScope.Kind.COMPLETE_CAPTURE, null, admitted.size()),
                    true,
                    admitted.size(),
                    admitted,
                    admitted.stream()
                            .filter(file -> file.analysisDisposition() == AnalysisDisposition.ANALYZABLE_TEXT)
                            .map(AdmittedSourceFile::fileId)
                            .sorted()
                            .toList(),
                    admitted.stream()
                            .filter(file -> file.analysisDisposition() == AnalysisDisposition.NON_ANALYZABLE_MEDIA)
                            .map(AdmittedSourceFile::fileId)
                            .sorted()
                            .toList(),
                    ref("capability-profile", 'e'),
                    ref("resource-budget", 'f'),
                    ref("verification-policy", '0'));
            ModulePublicationReference m1 = installM1(moduleStore, controls, requestPolicy, request, registration, captureReceipt, manifest);
            ModulePublicationReference m2 = installM2(moduleStore, controls, indexPolicy, request, registration, m1, files);
            ArtifactReference frozenRequest = contentReference("frozen-request", frozenRequestJson(request, captureReceipt, manifest));
            byte[] analysisRequestBytes = analysisRequestJson(request, controls, frozenRequest);
            ArtifactReference analysisRequest = analysisRequestReference(analysisRequestBytes);
            Map<ArtifactReference, byte[]> inputs = Map.of(
                    frozenRequest, frozenRequestJson(request, captureReceipt, manifest), analysisRequest, analysisRequestBytes);
            Stage01InputArtifactRegistry inputRegistry = reference -> {
                byte[] bytes = inputs.get(reference);
                if (bytes == null) {
                    throw new AssertionError("unexpected Stage01 input reference " + reference.artifactId());
                }
                return ImmutableBytes.copyOf(bytes);
            };
            Stage01Reference stage01 = new Stage01PublicationSpecifier().publish(
                    new Stage01PublicationSpecificationInput(
                            new StagePublicationAddress(RUN_ID, 1, "freeze-source"), m1, m2, analysisRequest, frozenRequest),
                    inputRegistry,
                    policies,
                    moduleStore,
                    stageStore);
            String snapshotId = new ObjectMapper()
                    .readTree(stageStore.reopen(stage01.stagePublicationReference()).semanticPayloads().stream()
                            .filter(payload -> payload.descriptor().fileName().equals("verified-snapshot.json"))
                            .findFirst()
                            .orElseThrow()
                            .canonicalUtf8()
                            .copyToByteArray())
                    .get("snapshotId")
                    .textValue();
            RegisteredSnapshotRegistry sourceRegistry = registryHandle(registration.artifactId(), snapshotId, admitted, files);
            return new Fixture(handle, policies, moduleStore, stageStore, stage01, sourceRegistry);
        }

        private static ModulePublicationReference installM1(
                CanonicalModuleArtifactStore store,
                ArtifactControls controls,
                CanonicalArtifactPolicy policy,
                AdmittedSourceRequest request,
                ArtifactReference registration,
                ArtifactReference captureReceipt,
                ArtifactReference manifest) {
            StageModuleAddress address = new StageModuleAddress(RUN_ID, 1, "freeze-source", 1, "request-admission");
            ModuleArtifactEnvelope envelope = ModuleArtifactEnvelope.write(
                    new ModuleArtifactEnvelopeDraft(
                            address,
                            "v2",
                            List.of(captureReceipt, manifest, registration),
                            controls,
                            "SUCCEEDED",
                            List.of(),
                            AdmittedSourceRequestArtifact.write(request)),
                    policy);
            return store.install(new ModuleInstallRequest(
                            address,
                            "v2",
                            List.of(captureReceipt, manifest, registration),
                            controls,
                            "SUCCEEDED",
                            List.of(),
                            List.of(payload("admitted-source-request.json", policy, envelope))))
                    .reference();
        }

        private static ModulePublicationReference installM2(
                CanonicalModuleArtifactStore store,
                ArtifactControls controls,
                CanonicalArtifactPolicy policy,
                AdmittedSourceRequest request,
                ArtifactReference registration,
                ModulePublicationReference m1,
                Map<String, byte[]> files) {
            var m1Descriptor = store.reopen(m1).payloads().get(0).descriptor();
            ArtifactReference m1Payload = new ArtifactReference(m1Descriptor.artifactId(), m1Descriptor.sha256());
            List<VerifiedSourceFile> verified = request.files().stream()
                    .map(file -> new VerifiedSourceFile(
                            file.fileId(),
                            file.path(),
                            file.gitMode(),
                            file.mediaType(),
                            file.sizeBytes(),
                            file.sha256(),
                            file.analysisDisposition(),
                            file.textEncoding(),
                            file.analysisDisposition() == AnalysisDisposition.ANALYZABLE_TEXT
                                    ? sha256Unchecked(files.get(file.path()))
                                    : null))
                    .toList();
            VerifiedSourceIndex index = new VerifiedSourceIndex(
                    "snapshot:" + "2".repeat(64),
                    m1Payload.artifactId(),
                    verified.size(),
                    verified.stream()
                            .filter(file -> file.analysisDisposition() == AnalysisDisposition.ANALYZABLE_TEXT)
                            .count(),
                    verified.stream()
                            .filter(file -> file.analysisDisposition() == AnalysisDisposition.NON_ANALYZABLE_MEDIA)
                            .count(),
                    verified,
                    List.of(new SourceShardReceipt(
                            "source-shard:" + "3".repeat(64),
                            verified.stream().map(VerifiedSourceFile::fileId).sorted().toList(),
                            verified.stream().map(VerifiedSourceFile::fileId).sorted().toList(),
                            "SUCCEEDED",
                            List.of())),
                    "VERIFIED");
            StageModuleAddress address = new StageModuleAddress(RUN_ID, 1, "freeze-source", 2, "source-index");
            ModuleArtifactEnvelope envelope = ModuleArtifactEnvelope.write(
                    new ModuleArtifactEnvelopeDraft(
                            address,
                            "v2",
                            List.of(registration, m1Payload),
                            controls,
                            "SUCCEEDED",
                            List.of(),
                            VerifiedSourceIndexArtifact.write(index)),
                    policy);
            return store.install(new ModuleInstallRequest(
                            address,
                            "v2",
                            List.of(registration, m1Payload),
                            controls,
                            "SUCCEEDED",
                            List.of(),
                            List.of(payload("verified-source-index.json", policy, envelope))))
                    .reference();
        }

        private static CanonicalModulePayload payload(String fileName, CanonicalArtifactPolicy policy, ModuleArtifactEnvelope envelope) {
            return new CanonicalModulePayload(
                    fileName,
                    policy.key().artifactType(),
                    policy.key().schemaVersion(),
                    envelope.reference().artifactId(),
                    policy.mediaType(),
                    envelope.canonicalUtf8());
        }

        private static RegisteredSnapshotRegistry registryHandle(
                String registrationId, String snapshotId, List<AdmittedSourceFile> admitted, Map<String, byte[]> files) {
            Map<String, AdmittedSourceFile> byId = admitted.stream()
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(AdmittedSourceFile::fileId, file -> file));
            return requestedRegistrationId -> {
                assertThat(requestedRegistrationId).isEqualTo(registrationId);
                return new RegisteredSnapshotRegistry.RegisteredSnapshotHandle() {
                    @Override
                    public String snapshotId() {
                        return snapshotId;
                    }

                    @Override
                    public RegisteredSourceFileMetadata inspect(String fileId) {
                        AdmittedSourceFile file = byId.get(fileId);
                        if (file == null) {
                            throw new AssertionError("unknown file id");
                        }
                        return new RegisteredSourceFileMetadata(file.sizeBytes(), file.sha256(), true);
                    }

                    @Override
                    public ImmutableBytes read(String fileId) {
                        AdmittedSourceFile file = byId.get(fileId);
                        if (file == null) {
                            throw new AssertionError("unknown file id");
                        }
                        return ImmutableBytes.copyOf(files.get(file.path()));
                    }
                };
            };
        }

        private static AdmittedSourceFile admitted(String path, byte[] bytes) {
            return AdmittedSourceFile.fromWire(
                    path,
                    "100644",
                    path.endsWith(".bin") ? "application/octet-stream" : path.endsWith(".java") ? "text/x-java-source" : "text/plain",
                    bytes.length,
                    sha256Unchecked(bytes),
                    path.endsWith(".bin") ? AnalysisDisposition.NON_ANALYZABLE_MEDIA : AnalysisDisposition.ANALYZABLE_TEXT,
                    path.endsWith(".bin") ? null : "UTF-8");
        }

        private static byte[] resource(String name) throws Exception {
            try (InputStream stream = Stage02ApplicationProfileDetectorTest.class
                    .getResourceAsStream("/target/stage02/application-profile/" + name)) {
                if (stream == null) {
                    throw new AssertionError("missing test resource " + name);
                }
                return stream.readAllBytes();
            }
        }

        static Map<String, byte[]> sourceFiles() throws Exception {
            Map<String, byte[]> files = new LinkedHashMap<>();
            files.put("application.yml", resource("application.yml"));
            files.put("pom.xml", resource("pom.xml"));
            files.put("src/main/java/example/depot/DepotHeadController.java", resource("DepotHeadController.java"));
            files.put("src/main/java/example/depot/DepotHeadMapper.java", resource("DepotHeadMapper.java"));
            files.put("src/main/java/example/other/OtherController.java", resource("OtherController.java"));
            files.put("src/main/resources/mapper/DepotHeadMapper.xml", resource("DepotHeadMapper.xml"));
            return files;
        }

        private static byte[] analysisRequestJson(
                AdmittedSourceRequest request, ArtifactControls controls, ArtifactReference frozenRequest) {
            return ("{"
                            + "\"approvedFindingRefs\":[],\"artifactPolicyRegistryRef\":"
                            + referenceJson(controls.artifactPolicyRegistryRef())
                            + ",\"candidateSeriesRef\":"
                            + referenceJson(ref("candidate-series", '1'))
                            + ",\"frozenRepositoryRequestRef\":"
                            + referenceJson(frozenRequest)
                            + ",\"organizationRegistrySeedRef\":null,\"parentCandidateRef\":null,"
                            + "\"profileBundleRef\":"
                            + referenceJson(request.capabilityProfileRef())
                            + ",\"promptBundleRef\":"
                            + referenceJson(ref("prompt-bundle", '2'))
                            + ",\"readerCandidateRound\":\"ROUND_1\",\"resourceBudgetRef\":"
                            + referenceJson(request.resourceBudgetRef())
                            + ",\"schemaBundleRef\":"
                            + referenceJson(ref("schema-bundle", '3'))
                            + ",\"schemaVersion\":\"analysis-run-request-v2\",\"sourceRegistrationId\":\""
                            + request.sourceRegistrationId()
                            + "\",\"toolchainRef\":"
                            + referenceJson(ref("toolchain", '4'))
                            + "}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        private static byte[] frozenRequestJson(
                AdmittedSourceRequest request, ArtifactReference captureReceipt, ArtifactReference manifest) {
            return ("{"
                            + "\"capabilityProfileRef\":"
                            + referenceJson(request.capabilityProfileRef())
                            + ",\"captureReceiptRef\":"
                            + referenceJson(captureReceipt)
                            + ",\"expectedOrigin\":{\"kind\":\"LOCAL_GIT\",\"repositoryUrl\":\""
                            + request.originRepositoryUrl()
                            + "\",\"revision40\":\""
                            + request.originRevision()
                            + "\"},\"inventoryScope\":{\"declaredPathCount\":"
                            + request.declaredPathCount()
                            + ",\"kind\":\"COMPLETE_CAPTURE\",\"scopeRoot\":null},\"resourceBudgetRef\":"
                            + referenceJson(request.resourceBudgetRef())
                            + ",\"schemaVersion\":\"frozen-repository-request-v2\",\"snapshotManifestRef\":"
                            + referenceJson(manifest)
                            + ",\"verificationPolicyRef\":"
                            + referenceJson(request.verificationPolicyRef())
                            + "}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        private static String referenceJson(ArtifactReference reference) {
            return "{\"artifactId\":\"" + reference.artifactId() + "\",\"sha256\":\"" + reference.sha256() + "\"}";
        }

        private static String referenceJson(ArtifactPolicyRegistryReference reference) {
            return "{\"artifactId\":\"" + reference.artifactId() + "\",\"sha256\":\"" + reference.sha256() + "\"}";
        }

        private static CanonicalArtifactPolicy policy(String type, String schema, String prefix, String envelopeKind) {
            return policy(type, schema, prefix, envelopeKind, false);
        }

        private static CanonicalArtifactPolicy policy(
                String type, String schema, String prefix, String envelopeKind, boolean emptyJsonlAllowed) {
            return new CanonicalArtifactPolicy(
                    new ArtifactPolicyKey(type, schema),
                    prefix,
                    "CANONICAL_JSONL".equals(envelopeKind) ? "application/x-ndjson" : "application/json",
                    envelopeKind,
                    emptyJsonlAllowed,
                    "METADATA_ONLY");
        }

        private static CanonicalArtifactPolicyRegistry registry(
                ArtifactPolicyRegistryReference reference, CanonicalArtifactPolicy... policies) {
            Map<ArtifactPolicyKey, CanonicalArtifactPolicy> entries = java.util.Arrays.stream(policies)
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(CanonicalArtifactPolicy::key, policy -> policy));
            return new CanonicalArtifactPolicyRegistry() {
                @Override
                public ArtifactPolicyRegistryReference reference() {
                    return reference;
                }

                @Override
                public CanonicalArtifactPolicy resolve(ArtifactPolicyKey key) {
                    CanonicalArtifactPolicy policy = entries.get(key);
                    if (policy == null) {
                        throw new IllegalArgumentException("fixture policy is missing " + key);
                    }
                    return policy;
                }
            };
        }

        private static ArtifactReference ref(String prefix, char character) {
            String digest = String.valueOf(character).repeat(64);
            return new ArtifactReference(prefix + ":" + digest, digest);
        }

        private static ArtifactReference contentReference(String prefix, byte[] bytes) {
            return new ArtifactReference(prefix + ":" + sha256Unchecked(bytes), sha256Unchecked(bytes));
        }

        private static ArtifactReference analysisRequestReference(byte[] bytes) throws Exception {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            frame(digest, "analysis-run-request-id-v2".getBytes(StandardCharsets.UTF_8));
            frame(digest, bytes);
            return new ArtifactReference("run-request:" + java.util.HexFormat.of().formatHex(digest.digest()), sha256Unchecked(bytes));
        }

        private static void frame(MessageDigest digest, byte[] bytes) {
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array());
            digest.update(bytes);
        }

        private static String sha256Unchecked(byte[] bytes) {
            try {
                return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }

        @Override
        public void close() throws Exception {
            handle.close();
        }
    }
}
