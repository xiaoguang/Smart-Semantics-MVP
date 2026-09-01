package com.linguan.codemd.target.stage02.applicationprofile;

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
import com.linguan.codemd.target.artifacts.ImmutableBytes;
import com.linguan.codemd.target.artifacts.ModuleArtifactEnvelope;
import com.linguan.codemd.target.artifacts.ModuleArtifactEnvelopeDraft;
import com.linguan.codemd.target.artifacts.ModuleInstallRequest;
import com.linguan.codemd.target.artifacts.ModulePublicationReference;
import com.linguan.codemd.target.artifacts.ReopenedStagePublication;
import com.linguan.codemd.target.artifacts.StageModuleAddress;
import com.linguan.codemd.target.artifacts.VerifiedCanonicalPayload;
import com.linguan.codemd.target.contracts.SourceExcerptV1;
import com.linguan.codemd.target.contracts.SourceLocatorV1;
import com.linguan.codemd.target.stage01.publish.Stage01Reference;
import com.linguan.codemd.target.stage01.sourceindex.RegisteredSnapshotRegistry;
import com.linguan.codemd.target.stage01.sourceindex.RegisteredSourceFileMetadata;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

/**
 * Stage02 M1. Reopens only Stage01 public artifacts and registered frozen bytes to select static
 * Java/Spring MVC/MyBatis parsers. A Maven dependency is a parser signal, never runtime proof.
 */
public final class ApplicationProfileDetector {
    private static final ObjectMapper JSON = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private static final ArtifactPolicyKey PROFILE_DRAFT =
            new ArtifactPolicyKey("STAGE02_APPLICATION_PROFILE_DRAFT", "stage02-application-profile-draft-v2");
    private static final Set<String> STAGE01_FILES =
            Set.of("source-input.json", "source-inventory.jsonl", "verified-snapshot.json");

    private final CanonicalStageArtifactStore stageStore;
    private final CanonicalModuleArtifactStore moduleStore;
    private final CanonicalArtifactPolicyRegistry policies;
    private final RegisteredSnapshotRegistry snapshots;
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

    public ApplicationProfileDetector(
            CanonicalStageArtifactStore stageStore,
            CanonicalModuleArtifactStore moduleStore,
            CanonicalArtifactPolicyRegistry policies,
            RegisteredSnapshotRegistry snapshots) {
        if (stageStore == null || moduleStore == null || policies == null || snapshots == null) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "Stage02 M1 dependencies are required");
        }
        this.stageStore = stageStore;
        this.moduleStore = moduleStore;
        this.policies = policies;
        this.snapshots = snapshots;
    }

    /** Detects the profile and atomically persists its one-payload M1 module publication. */
    public ApplicationProfileDetection detect(Stage01Reference frozenSource, DiscoveryProfile discoveryProfile) {
        if (frozenSource == null || discoveryProfile == null) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "Stage01 reference and discovery profile are required");
        }
        ReopenedStagePublication stage01 = stageStore.reopen(frozenSource.stagePublicationReference());
        if (stage01.reference().address().stageNumber() != 1
                || !"freeze-source".equals(stage01.reference().address().stageKey())
                || !"SUCCEEDED".equals(stage01.receipt().status())) {
            throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "Stage01 publication is not complete");
        }
        Map<String, VerifiedCanonicalPayload> payloads = stagePayloads(stage01);
        ObjectNode sourceInput = object(payloads.get("source-input.json").canonicalUtf8(), "source-input.json");
        ObjectNode snapshot = object(payloads.get("verified-snapshot.json").canonicalUtf8(), "verified-snapshot.json");
        String sourceRegistrationId = requiredText(sourceInput, "sourceRegistrationId");
        String snapshotId = requiredText(snapshot, "snapshotId");
        String scopeKind = requiredText(object(snapshot.get("inventoryScope"), "inventoryScope"), "kind");
        boolean repositoryCompletionEligible = requiredBoolean(snapshot, "repositoryCompletionEligible");
        if (!repositoryCompletionEligible || !"COMPLETE_CAPTURE".equals(scopeKind)) {
            throw new ApplicationProfileException(
                    "APPLICATION_PROFILE_UNRESOLVED", "Stage02 M1 only accepts a complete Stage01 capture");
        }
        ArtifactReference capabilityProfile = reference(object(sourceInput.get("profileBundleRef"), "profileBundleRef"));
        RegisteredSnapshotRegistry.RegisteredSnapshotHandle sourceHandle = snapshots.resolve(sourceRegistrationId);
        if (sourceHandle == null || !snapshotId.equals(sourceHandle.snapshotId())) {
            throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "registered source snapshot differs from Stage01");
        }
        List<InventoryFile> inventory = inventory(payloads.get("source-inventory.jsonl").canonicalUtf8());
        if (inventory.size() > discoveryProfile.maxInspectedTextFiles()) {
            throw new ApplicationProfileException(
                    "STAGE02_RESOURCE_LIMIT_EXCEEDED", "Stage01 text inventory exceeds the discovery file budget");
        }

        List<SourceFile> pomFiles = sourceFiles(inventory, sourceHandle, discoveryProfile, ApplicationProfileDetector::isPom);
        if (pomFiles.isEmpty()) {
            throw new ApplicationProfileException("APPLICATION_PROFILE_UNRESOLVED", "no frozen Maven POM was found");
        }
        List<SourceFile> configFiles = sourceFiles(inventory, sourceHandle, discoveryProfile, ApplicationProfileDetector::isConfig);
        List<Integer> javaVersions = new ArrayList<>();
        List<FrameworkSignal> frameworkSignals = new ArrayList<>();
        for (SourceFile pom : pomFiles) {
            Model model = parsePom(pom);
            Integer release = javaRelease(model);
            if (release != null) {
                javaVersions.add(release);
            }
            for (Dependency dependency : model.getDependencies()) {
                FrameworkSignalKind kind = frameworkKind(dependency);
                if (kind != null) {
                    String token = dependency.getArtifactId();
                    frameworkSignals.add(new FrameworkSignal(
                            kind, pom.excerpt(token), SignalDisposition.SUPPORTED, null));
                }
            }
        }
        Integer languageVersion = uniqueJavaVersion(javaVersions);
        frameworkSignals = sortedDistinctFrameworkSignals(frameworkSignals);
        List<ConfigSignal> configSignals = new ArrayList<>();
        for (SourceFile config : configFiles) {
            configSignals.addAll(myBatisMapperLocationSignals(config));
        }
        configSignals = sortedDistinctConfigSignals(configSignals);
        ApplicationProfile profile = new ApplicationProfile(
                profileId(
                        snapshotId,
                        scopeKind,
                        repositoryCompletionEligible,
                        languageVersion,
                        frameworkSignals,
                        configSignals,
                        capabilityProfile,
                        stage01.receipt().controls()),
                snapshotId,
                scopeKind,
                repositoryCompletionEligible,
                ApplicationLanguage.JAVA,
                languageVersion,
                frameworkSignals,
                configSignals,
                capabilityProfile);
        ModulePublicationReference draft = persistDraft(stage01, payloads, profile);
        return new ApplicationProfileDetection(profile, draft);
    }

    private ModulePublicationReference persistDraft(
            ReopenedStagePublication stage01, Map<String, VerifiedCanonicalPayload> stage01Payloads, ApplicationProfile profile) {
        CanonicalArtifactPolicy policy = policies.resolve(PROFILE_DRAFT);
        ObjectNode payload = profileNode(profile);
        StageModuleAddress address = new StageModuleAddress(
                stage01.reference().address().runId(), 2, "discover-application-and-entries", 1, "application-profile");
        List<ArtifactReference> upstream = stage01Payloads.values().stream()
                .map(VerifiedCanonicalPayload::descriptor)
                .map(descriptor -> new ArtifactReference(descriptor.artifactId(), descriptor.sha256()))
                .sorted(Comparator.comparing(ArtifactReference::artifactId))
                .toList();
        ModuleArtifactEnvelope envelope = ModuleArtifactEnvelope.write(
                new ModuleArtifactEnvelopeDraft(
                        address, "v2", upstream, stage01.receipt().controls(), "SUCCEEDED", List.of(), payload),
                policy);
        return moduleStore.install(new ModuleInstallRequest(
                        address,
                        "v2",
                        upstream,
                        stage01.receipt().controls(),
                        "SUCCEEDED",
                        List.of(),
                        List.of(new CanonicalModulePayload(
                                "application-profile-draft.json",
                                policy.key().artifactType(),
                                policy.key().schemaVersion(),
                                envelope.reference().artifactId(),
                                policy.mediaType(),
                                envelope.canonicalUtf8()))))
                .reference();
    }

    private static Map<String, VerifiedCanonicalPayload> stagePayloads(ReopenedStagePublication stage01) {
        Map<String, VerifiedCanonicalPayload> result = new LinkedHashMap<>();
        for (VerifiedCanonicalPayload payload : stage01.semanticPayloads()) {
            String fileName = payload.descriptor().fileName();
            if (result.put(fileName, payload) != null) {
                throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "Stage01 has duplicate semantic filenames");
            }
        }
        if (!result.keySet().equals(STAGE01_FILES)) {
            throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "Stage01 semantic payload set differs");
        }
        return Map.copyOf(result);
    }

    private static List<InventoryFile> inventory(ImmutableBytes bytes) {
        String jsonl = decode(bytes.copyToByteArray(), "source inventory");
        if (jsonl.isEmpty() || !jsonl.endsWith("\n")) {
            throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "Stage01 source inventory is not canonical JSONL");
        }
        List<InventoryFile> files = new ArrayList<>();
        String[] lines = jsonl.substring(0, jsonl.length() - 1).split("\n", -1);
        String previous = null;
        for (String line : lines) {
            ObjectNode item = object(ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8)), "source inventory line");
            InventoryFile file = new InventoryFile(
                    requiredText(item, "fileId"),
                    requiredText(item, "path"),
                    item.path("sizeBytes").asLong(-1),
                    requiredText(item, "sha256"),
                    requiredText(item, "analysisDisposition"),
                    nullableText(item, "textEncoding"));
            if (previous != null && previous.compareTo(file.path()) >= 0) {
                throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "Stage01 inventory paths are not canonical sorted");
            }
            previous = file.path();
            files.add(file);
        }
        return List.copyOf(files);
    }

    private static List<SourceFile> sourceFiles(
            List<InventoryFile> inventory,
            RegisteredSnapshotRegistry.RegisteredSnapshotHandle sourceHandle,
            DiscoveryProfile profile,
            java.util.function.Predicate<InventoryFile> selector) {
        List<SourceFile> result = new ArrayList<>();
        for (InventoryFile file : inventory) {
            if (!"ANALYZABLE_TEXT".equals(file.analysisDisposition()) || !selector.test(file)) {
                continue;
            }
            if (file.sizeBytes() > profile.maxTextFileBytes()) {
                throw new ApplicationProfileException(
                        "STAGE02_RESOURCE_LIMIT_EXCEEDED", "a selected source file exceeds the discovery byte budget");
            }
            RegisteredSourceFileMetadata metadata = sourceHandle.inspect(file.fileId());
            if (metadata == null
                    || !metadata.regularFile()
                    || metadata.sizeBytes() != file.sizeBytes()
                    || !metadata.sha256().equals(file.sha256())) {
                throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "registered file metadata differs from Stage01 inventory");
            }
            ImmutableBytes read = sourceHandle.read(file.fileId());
            byte[] raw = read.copyToByteArray();
            if (raw.length != file.sizeBytes() || !sha256(raw).equals(file.sha256())) {
                throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "registered source bytes differ from Stage01 inventory");
            }
            result.add(new SourceFile(file, raw));
        }
        return List.copyOf(result);
    }

    private static Model parsePom(SourceFile pom) {
        try {
            return new MavenXpp3Reader().read(new StringReader(pom.text()));
        } catch (Exception exception) {
            throw new ApplicationProfileException("APPLICATION_PROFILE_UNRESOLVED", "cannot parse frozen Maven POM", exception);
        }
    }

    private static Integer javaRelease(Model model) {
        String release = firstNonBlank(
                model.getProperties().getProperty("maven.compiler.release"),
                model.getProperties().getProperty("maven.compiler.source"));
        if (release == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(release);
            if (parsed < 1) {
                throw new NumberFormatException("release is not positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new ApplicationProfileException("APPLICATION_PROFILE_UNRESOLVED", "Maven Java release is not a positive integer", exception);
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.strip();
        }
        if (second != null && !second.isBlank()) {
            return second.strip();
        }
        return null;
    }

    private static FrameworkSignalKind frameworkKind(Dependency dependency) {
        if (dependency == null || dependency.getGroupId() == null || dependency.getArtifactId() == null) {
            return null;
        }
        String group = dependency.getGroupId();
        String artifact = dependency.getArtifactId();
        if (group.startsWith("org.mybatis") || artifact.contains("mybatis")) {
            return FrameworkSignalKind.MYBATIS;
        }
        if ("org.springframework".equals(group) && artifact.equals("spring-webmvc")) {
            return FrameworkSignalKind.SPRING_MVC;
        }
        return null;
    }

    private static Integer uniqueJavaVersion(List<Integer> versions) {
        List<Integer> unique = versions.stream().distinct().sorted().toList();
        if (unique.size() > 1) {
            throw new ApplicationProfileException("APPLICATION_PROFILE_CONFLICT", "Maven modules declare conflicting Java releases");
        }
        return unique.isEmpty() ? null : unique.get(0);
    }

    private static List<FrameworkSignal> sortedDistinctFrameworkSignals(List<FrameworkSignal> values) {
        return values.stream()
                .sorted(Comparator.comparing((FrameworkSignal signal) -> signal.kind().name())
                        .thenComparing(signal -> signal.sourceExcerpt().locator().path())
                        .thenComparingLong(signal -> signal.sourceExcerpt().locator().startByte()))
                .toList();
    }

    private static List<ConfigSignal> sortedDistinctConfigSignals(List<ConfigSignal> values) {
        return values.stream()
                .sorted(Comparator.comparing((ConfigSignal signal) -> signal.kind().name())
                        .thenComparing(signal -> signal.sourceExcerpt().locator().path())
                        .thenComparingLong(signal -> signal.sourceExcerpt().locator().startByte()))
                .toList();
    }

    private static List<ConfigSignal> myBatisMapperLocationSignals(SourceFile config) {
        List<ConfigSignal> result = new ArrayList<>();
        String[] lines = config.text().split("\n", -1);
        int position = 0;
        boolean inMyBatisBlock = false;
        for (String line : lines) {
            String trimmed = line.stripLeading();
            boolean topLevel = !line.isEmpty() && line.length() == trimmed.length();
            if (topLevel) {
                inMyBatisBlock = "mybatis:".equals(trimmed);
            }
            if ((inMyBatisBlock && trimmed.startsWith("mapper-locations:"))
                    || trimmed.startsWith("mybatis.mapper-locations=")) {
                int colon = line.indexOf(':');
                int equals = line.indexOf('=');
                int separator = colon >= 0 ? colon : equals;
                String value = line.substring(separator + 1).strip();
                if (!value.isEmpty()) {
                    int valueStart = position + line.indexOf(value);
                    result.add(new ConfigSignal(
                            ConfigSignalKind.MYBATIS_MAPPER_LOCATION,
                            config.excerpt(valueStart, valueStart + value.getBytes(StandardCharsets.UTF_8).length),
                            value,
                            SignalDisposition.SUPPORTED,
                            null));
                }
            }
            position += line.getBytes(StandardCharsets.UTF_8).length + 1;
        }
        return result;
    }

    private static String profileId(
            String snapshotId,
            String scopeKind,
            boolean eligible,
            Integer javaVersion,
            List<FrameworkSignal> frameworks,
            List<ConfigSignal> config,
            ArtifactReference capabilityProfile,
            ArtifactControls controls) {
        ObjectNode material = JSON.createObjectNode();
        material.put("snapshotId", snapshotId);
        material.put("inventoryScopeKind", scopeKind);
        material.put("repositoryCompletionEligible", eligible);
        material.put("language", ApplicationLanguage.JAVA.name());
        if (javaVersion == null) {
            material.putNull("languageVersion");
        } else {
            material.put("languageVersion", javaVersion);
        }
        material.set("frameworkSignals", frameworkArray(frameworks));
        material.set("configSignals", configArray(config));
        material.set("capabilityProfileRef", referenceNode(capabilityProfile));
        material.put("toolchainSha256", controls.toolchainSha256());
        return "application-profile:" + framedDigest(
                "application-profile-id-v2", new CanonicalJsonCodec().canonicalize(material).copyToByteArray());
    }

    private static ObjectNode profileNode(ApplicationProfile profile) {
        ObjectNode node = JSON.createObjectNode();
        node.put("applicationProfileId", profile.applicationProfileId());
        node.put("snapshotId", profile.snapshotId());
        node.put("inventoryScopeKind", profile.inventoryScopeKind());
        node.put("repositoryCompletionEligible", profile.repositoryCompletionEligible());
        node.put("language", profile.language().name());
        if (profile.languageVersion() == null) {
            node.putNull("languageVersion");
        } else {
            node.put("languageVersion", profile.languageVersion());
        }
        node.set("frameworkSignals", frameworkArray(profile.frameworkSignals()));
        node.set("configSignals", configArray(profile.configSignals()));
        node.set("capabilityProfileRef", referenceNode(profile.capabilityProfileRef()));
        return node;
    }

    private static ArrayNode frameworkArray(List<FrameworkSignal> values) {
        ArrayNode result = JSON.createArrayNode();
        for (FrameworkSignal value : values) {
            ObjectNode signal = result.addObject();
            signal.put("kind", value.kind().name());
            signal.set("sourceExcerpt", excerptNode(value.sourceExcerpt()));
            signal.put("disposition", value.disposition().name());
            nullable(signal, "reasonCode", value.reasonCode());
        }
        return result;
    }

    private static ArrayNode configArray(List<ConfigSignal> values) {
        ArrayNode result = JSON.createArrayNode();
        for (ConfigSignal value : values) {
            ObjectNode signal = result.addObject();
            signal.put("kind", value.kind().name());
            signal.set("sourceExcerpt", excerptNode(value.sourceExcerpt()));
            nullable(signal, "value", value.value());
            signal.put("disposition", value.disposition().name());
            nullable(signal, "reasonCode", value.reasonCode());
        }
        return result;
    }

    private static ObjectNode excerptNode(SourceExcerptV1 excerpt) {
        ObjectNode result = JSON.createObjectNode();
        SourceLocatorV1 locator = excerpt.locator();
        ObjectNode location = result.putObject("locator");
        location.put("fileId", locator.fileId());
        location.put("path", locator.path());
        location.put("startByte", locator.startByte());
        location.put("endByteExclusive", locator.endByteExclusive());
        location.put("startLine", locator.startLine());
        location.put("startColumn", locator.startColumn());
        location.put("endLine", locator.endLine());
        location.put("endColumn", locator.endColumn());
        result.put("rawUtf8", excerpt.rawUtf8());
        result.put("rawUtf8Sha256", excerpt.rawUtf8Sha256());
        return result;
    }

    private static ObjectNode referenceNode(ArtifactReference reference) {
        ObjectNode node = JSON.createObjectNode();
        node.put("artifactId", reference.artifactId());
        node.put("sha256", reference.sha256());
        return node;
    }

    private static ObjectNode object(ImmutableBytes bytes, String name) {
        try {
            JsonNode parsed = JSON.readTree(bytes.copyToByteArray());
            if (parsed == null || !parsed.isObject()) {
                throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", name + " must be an object");
            }
            return (ObjectNode) parsed;
        } catch (ApplicationProfileException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "cannot parse " + name, exception);
        }
    }

    private static ObjectNode object(JsonNode node, String name) {
        if (node == null || !node.isObject()) {
            throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", name + " must be an object");
        }
        return (ObjectNode) node;
    }

    private static String requiredText(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", field + " must be a nonblank string");
        }
        return value.textValue();
    }

    private static String nullableText(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", field + " must be nullable text");
        }
        return value.textValue();
    }

    private static boolean requiredBoolean(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", field + " must be boolean");
        }
        return value.booleanValue();
    }

    private static ArtifactReference reference(ObjectNode node) {
        return new ArtifactReference(requiredText(node, "artifactId"), requiredText(node, "sha256"));
    }

    private static boolean isPom(InventoryFile file) {
        return "pom.xml".equals(file.path()) || file.path().endsWith("/pom.xml");
    }

    private static boolean isConfig(InventoryFile file) {
        return file.path().endsWith("application.yml")
                || file.path().endsWith("application.yaml")
                || file.path().endsWith("application.properties");
    }

    private static String decode(byte[] bytes, String name) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", name + " is not strict UTF-8", exception);
        }
    }

    private static void nullable(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String framedDigest(String domain, byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            frame(digest, domain.getBytes(StandardCharsets.UTF_8));
            frame(digest, bytes);
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void frame(MessageDigest digest, byte[] bytes) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array());
        digest.update(bytes);
    }

    private record InventoryFile(
            String fileId, String path, long sizeBytes, String sha256, String analysisDisposition, String textEncoding) {
        InventoryFile {
            if (!fileId.matches("file:[0-9a-f]{64}")
                    || path.isBlank()
                    || sizeBytes < 0
                    || !sha256.matches("[0-9a-f]{64}")
                    || (!"ANALYZABLE_TEXT".equals(analysisDisposition)
                            && !"NON_ANALYZABLE_MEDIA".equals(analysisDisposition))
                    || ("ANALYZABLE_TEXT".equals(analysisDisposition) && !"UTF-8".equals(textEncoding))
                    || ("NON_ANALYZABLE_MEDIA".equals(analysisDisposition) && textEncoding != null)) {
                throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "source inventory item is invalid");
            }
        }
    }

    private static final class SourceFile {
        private final InventoryFile inventory;
        private final byte[] bytes;
        private final String text;

        private SourceFile(InventoryFile inventory, byte[] bytes) {
            this.inventory = inventory;
            this.bytes = Arrays.copyOf(bytes, bytes.length);
            this.text = decode(bytes, inventory.path());
        }

        private String text() {
            return text;
        }

        private SourceExcerptV1 excerpt(String token) {
            int start = indexOf(bytes, token.getBytes(StandardCharsets.UTF_8));
            if (start < 0) {
                throw new ApplicationProfileException("APPLICATION_PROFILE_UNRESOLVED", "POM model token has no frozen source span");
            }
            return excerpt(start, start + token.getBytes(StandardCharsets.UTF_8).length);
        }

        private SourceExcerptV1 excerpt(int startByte, int endByteExclusive) {
            if (startByte < 0 || endByteExclusive <= startByte || endByteExclusive > bytes.length) {
                throw new ApplicationProfileException("SNAPSHOT_REOPEN_MISMATCH", "source span is outside frozen bytes");
            }
            String raw = decode(Arrays.copyOfRange(bytes, startByte, endByteExclusive), inventory.path() + " excerpt");
            String prefix = decode(Arrays.copyOfRange(bytes, 0, startByte), inventory.path() + " prefix");
            String throughEnd = decode(Arrays.copyOfRange(bytes, 0, endByteExclusive), inventory.path() + " prefix");
            int startLine = line(prefix);
            int startColumn = column(prefix);
            int endLine = line(throughEnd);
            int endColumn = column(throughEnd);
            return new SourceExcerptV1(
                    new SourceLocatorV1(
                            inventory.fileId(), inventory.path(), startByte, endByteExclusive, startLine, startColumn, endLine, endColumn),
                    raw,
                    sha256(raw.getBytes(StandardCharsets.UTF_8)));
        }

        private static int line(String prefix) {
            return (int) prefix.chars().filter(character -> character == '\n').count() + 1;
        }

        private static int column(String prefix) {
            int lineStart = prefix.lastIndexOf('\n') + 1;
            return prefix.codePointCount(lineStart, prefix.length()) + 1;
        }

        private static int indexOf(byte[] source, byte[] target) {
            outer: for (int offset = 0; offset <= source.length - target.length; offset++) {
                for (int index = 0; index < target.length; index++) {
                    if (source[offset + index] != target[index]) {
                        continue outer;
                    }
                }
                return offset;
            }
            return -1;
        }
    }
}
