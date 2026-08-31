package com.linguan.codemd.cli;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguan.codemd.discovery.DiscoveryGap;
import com.linguan.codemd.discovery.DiscoveryRequest;
import com.linguan.codemd.discovery.DiscoveryResult;
import com.linguan.codemd.discovery.RepositoryDiscoverer;
import com.linguan.codemd.mvp.CandidateArchiveService;
import com.linguan.codemd.mvp.CandidateReference;
import com.linguan.codemd.mvp.BaselineGenerationRequest;
import com.linguan.codemd.mvp.GenerationRequest;
import com.linguan.codemd.mvp.ModelProvider;
import com.linguan.codemd.mvp.ModelTask;
import com.linguan.codemd.mvp.TraceEvidence;
import com.linguan.codemd.mvp.TraceQuery;
import com.linguan.codemd.mvp.TraceView;
import com.linguan.codemd.mvp.ValidationReceipt;
import com.linguan.codemd.stage01.FrozenRepositoryRequest;
import com.linguan.codemd.stage04.CandidateArtifactReader;
import com.linguan.codemd.stage04.BoundedInput;
import com.linguan.codemd.stage04.CodeToMarkdownAgent;
import com.linguan.codemd.stage04.FilesystemSourceRegistry;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/** Offline-only command line for archived, recorded provider outputs. */
@Command(name = "code-md", mixinStandardHelpOptions = true,
        description = "Deterministic code-to-nine-section Markdown agent.",
        subcommands = {CodeMdCli.GenerateCommand.class, CodeMdCli.TraceCommand.class,
                CodeMdCli.ValidateCommand.class, CodeMdCli.InspectCommand.class,
                CodeMdCli.DiscoverCommand.class, CodeMdCli.BaselineCommand.class,
                CodeMdCli.CandidateCommand.class})
public final class CodeMdCli implements Callable<Integer> {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final String PROFILE = "WALKING_SLICE_V0";
    private final CodeToMarkdownAgent targetAgent;
    private final CandidateArtifactReader artifactReader;

    @Spec
    private CommandSpec spec;

    /** Retains the old, explicitly diagnostic/POC command surface. */
    public CodeMdCli() {
        this(null, null);
    }

    /** Target commands have no legacy archive fallback when only an Agent is supplied. */
    public CodeMdCli(CodeToMarkdownAgent targetAgent) {
        this(targetAgent, null);
    }

    /** Target adapter seam: mutation through Agent, reads through the explicit reader. */
    public CodeMdCli(CodeToMarkdownAgent targetAgent, CandidateArtifactReader artifactReader) {
        this.targetAgent = targetAgent;
        this.artifactReader = artifactReader;
    }

    @Override
    public Integer call() {
        spec.commandLine().usage(out());
        return 2;
    }

    private PrintWriter out() {
        return spec.commandLine().getOut();
    }

    private boolean targetMode() {
        return targetAgent != null;
    }

    @Command(name = "inspect", mixinStandardHelpOptions = true,
            description = "Inspect one local source repository without executing it.")
    static final class InspectCommand implements Callable<Integer> {
        @ParentCommand
        private CodeMdCli root;

        @Option(names = "--repository-root", required = true, paramLabel = "PATH")
        private Path repositoryRoot;

        @Override
        public Integer call() {
            DiscoveryResult discovery = discoverOrGap(repositoryRoot);
            SourceScan scan = scanSources(repositoryRoot);
            var summary = JSON.createObjectNode();
            summary.put("profile", PROFILE);
            summary.put("fileCount", scan.fileCount());
            summary.put("scannedFileCount", scan.fileCount());
            summary.put("javaFileCount", scan.javaFileCount());
            summary.put("xmlFileCount", scan.xmlFileCount());
            summary.put("routeCount", discovery.httpRoutes().size());
            summary.set("gaps", JSON.valueToTree(mergedGaps(discovery.gaps(), scan.gaps())));
            root.out().println(json(summary, "INSPECTION_SERIALIZATION_FAILURE"));
            return 0;
        }
    }

    @Command(name = "discover", mixinStandardHelpOptions = true,
            description = "Discover deterministic source facts from one local repository.")
    static final class DiscoverCommand implements Callable<Integer> {
        @ParentCommand
        private CodeMdCli root;

        @Option(names = "--repository-root", required = true, paramLabel = "PATH")
        private Path repositoryRoot;

        @Override
        public Integer call() {
            root.out().println(json(discoverOrGap(repositoryRoot),
                    "DISCOVERY_SERIALIZATION_FAILURE"));
            return 0;
        }
    }

    @Command(name = "generate", mixinStandardHelpOptions = true,
            description = "Generate one candidate from two recorded JSON provider responses.")
    static final class GenerateCommand implements Callable<Integer> {
        @ParentCommand
        private CodeMdCli root;

        @Option(names = "--manifest", paramLabel = "PATH")
        private Path manifest;

        @Option(names = "--snapshot-root", paramLabel = "PATH")
        private Path snapshotRoot;

        @Option(names = "--workspace", paramLabel = "PATH")
        private Path workspace;

        @Option(names = "--recorded-r1", paramLabel = "PATH")
        private Path recordedR1;

        @Option(names = "--recorded-r2", paramLabel = "PATH")
        private Path recordedR2;

        @Option(names = "--config", paramLabel = "PATH")
        private Path config;

        @Option(names = "--source", paramLabel = "ID")
        private String source;

        @Override
        public Integer call() {
            if (root.targetMode()) {
                return root.targetGenerate(config, source);
            }
            requireLegacy(manifest, snapshotRoot, workspace, recordedR1, recordedR2);
            Path checkedManifest = existingRegularFile(manifest, "MANIFEST_PATH_INVALID");
            Path checkedSnapshot = existingDirectory(snapshotRoot, "SNAPSHOT_ROOT_INVALID");
            JsonNode r1 = recordedObject(recordedR1, "RECORDED_R1_INVALID");
            JsonNode r2 = recordedObject(recordedR2, "RECORDED_R2_INVALID");
            RecordedProvider provider = new RecordedProvider(r1, r2);
            CandidateReference candidate = new CandidateArchiveService(workspace).archive(
                    new GenerationRequest(checkedManifest, checkedSnapshot, provider));
            provider.requireConsumedExactlyOnce();
            root.out().println(candidate.candidateId());
            return 0;
        }
    }

    @Command(name = "baseline", mixinStandardHelpOptions = true,
            description = "Generate one provider-free deterministic baseline candidate.")
    static final class BaselineCommand implements Callable<Integer> {
        @ParentCommand
        private CodeMdCli root;

        @Option(names = "--manifest", required = true, paramLabel = "PATH")
        private Path manifest;

        @Option(names = "--snapshot-root", required = true, paramLabel = "PATH")
        private Path snapshotRoot;

        @Option(names = "--workspace", required = true, paramLabel = "PATH")
        private Path workspace;

        @Override
        public Integer call() {
            Path checkedManifest = existingRegularFile(manifest, "MANIFEST_PATH_INVALID");
            Path checkedSnapshot = existingDirectory(snapshotRoot, "SNAPSHOT_ROOT_INVALID");
            CandidateReference candidate = new CandidateArchiveService(workspace).archive(
                    new BaselineGenerationRequest(checkedManifest, checkedSnapshot));
            root.out().println(candidate.candidateId());
            return 0;
        }
    }

    @Command(name = "trace", mixinStandardHelpOptions = true,
            description = "Read one archived item trace without any in-memory candidate state.")
    static final class TraceCommand implements Callable<Integer> {
        @ParentCommand
        private CodeMdCli root;

        @Option(names = "--workspace", paramLabel = "PATH")
        private Path workspace;

        @Option(names = "--candidate-id", paramLabel = "ID")
        private String candidateId;

        @Option(names = "--item-key", paramLabel = "KEY")
        private String itemKey;

        @Option(names = "--config", paramLabel = "PATH")
        private Path config;

        @Option(names = "--candidate", paramLabel = "ID")
        private String targetCandidateId;

        @Option(names = "--item", paramLabel = "KEY")
        private String targetItemKey;

        @Override
        public Integer call() {
            if (root.targetMode()) {
                return root.targetTrace(config, targetCandidateId, targetItemKey);
            }
            requireLegacy(workspace, candidateId, itemKey);
            rejectUnsafeToken(candidateId, "CANDIDATE_ID_INVALID");
            rejectUnsafeToken(itemKey, "TRACE_ITEM_KEY_INVALID");
            TraceView trace = new CandidateArchiveService(workspace).trace(
                    new TraceQuery(candidateId, itemKey));
            PrintWriter out = root.out();
            out.println(trace.candidateId() + " " + trace.itemKey());
            for (TraceEvidence evidence : trace.evidence()) {
                out.println(evidence.relativePath() + ":" + evidence.startLine() + ":"
                        + evidence.startColumn() + "-" + evidence.endLine() + ":"
                        + evidence.endColumn() + " " + evidence.excerptSha256());
            }
            return 0;
        }
    }

    @Command(name = "validate", mixinStandardHelpOptions = true,
            description = "Validate one archived candidate without changing its Markdown.")
    static final class ValidateCommand implements Callable<Integer> {
        @ParentCommand
        private CodeMdCli root;

        @Option(names = "--workspace", paramLabel = "PATH")
        private Path workspace;

        @Option(names = "--candidate-id", paramLabel = "ID")
        private String candidateId;

        @Option(names = "--config", paramLabel = "PATH")
        private Path config;

        @Option(names = "--candidate", paramLabel = "ID")
        private String targetCandidateId;

        @Override
        public Integer call() {
            if (root.targetMode()) {
                return root.targetValidate(config, targetCandidateId);
            }
            requireLegacy(workspace, candidateId);
            rejectUnsafeToken(candidateId, "CANDIDATE_ID_INVALID");
            Path checkedWorkspace = existingDirectory(workspace, "WORKSPACE_INVALID");
            CandidateReference candidate = archivedCandidate(checkedWorkspace, candidateId);
            ValidationReceipt receipt = new CandidateArchiveService(checkedWorkspace).validate(candidate);
            root.out().println(validationReceiptJson(receipt));
            return receipt.valid() ? 0 : 1;
        }
    }

    @Command(name = "candidate", mixinStandardHelpOptions = true,
            description = "Read one immutable Candidate reference through the target read seam.")
    static final class CandidateCommand implements Callable<Integer> {
        @ParentCommand
        private CodeMdCli root;

        @Option(names = "--config", required = true, paramLabel = "PATH")
        private Path config;

        @Option(names = "--candidate", required = true, paramLabel = "ID")
        private String candidateId;

        @Override
        public Integer call() {
            return root.targetCandidate(config, candidateId);
        }
    }

    private Integer targetGenerate(Path config, String registrationId) {
        return targetCall(() -> {
            TargetConfig parsed = targetConfig(config);
            FrozenRepositoryRequest registered = new FilesystemSourceRegistry(parsed.sourceRegistry()).resolve(registrationId);
            return targetAgent.generateCandidate(registered);
        }, false);
    }

    private Integer targetValidate(Path config, String candidateId) {
        return targetCall(() -> {
            targetConfig(config);
            return CandidateArtifactReader.validate(targetAgent, requiredReader(), candidateId);
        }, true);
    }

    private Integer targetTrace(Path config, String candidateId, String itemKey) {
        return targetCall(() -> {
            targetConfig(config);
            return CandidateArtifactReader.trace(targetAgent, candidateId, itemKey);
        }, false);
    }

    private Integer targetCandidate(Path config, String candidateId) {
        return targetCall(() -> {
            targetConfig(config);
            return CandidateArtifactReader.candidateValue(requiredReader(), candidateId);
        }, false);
    }

    private CandidateArtifactReader requiredReader() {
        if (artifactReader == null) {
            throw new IllegalArgumentException("reader required");
        }
        return artifactReader;
    }

    private Integer targetCall(TargetCall call, boolean validation) {
        try {
            JsonNode response = canonicalNode(call.invoke());
            out().println(JSON.writeValueAsString(response));
            return validation && !response.path("valid").asBoolean(false) ? 1 : 0;
        } catch (RuntimeException | IOException failure) {
            try {
                var error = JSON.createObjectNode();
                error.put("code", CandidateArtifactReader.failureCode(failure));
                out().println(JSON.writeValueAsString(error));
            } catch (IOException impossible) {
                // Picocli still receives a stable non-success code below.
            }
            return 2;
        }
    }

    private static JsonNode canonicalNode(Object value) {
        JsonNode node = JSON.valueToTree(value);
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("target response must be object");
        }
        return sortJson(node);
    }

    private static JsonNode sortJson(JsonNode node) {
        if (node.isObject()) {
            var sorted = JSON.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.sort(Comparator.naturalOrder());
            for (String name : names) {
                sorted.set(name, sortJson(node.get(name)));
            }
            return sorted;
        }
        if (node.isArray()) {
            var sorted = JSON.createArrayNode();
            node.elements().forEachRemaining(value -> sorted.add(sortJson(value)));
            return sorted;
        }
        return node;
    }

    private static TargetConfig targetConfig(Path path) {
        if (path == null || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("config invalid");
        }
        try {
            String source = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(BoundedInput.readConfiguration(path))).toString();
            return TargetConfig.parse(source);
        } catch (RuntimeException invalid) {
            if (BoundedInput.isSizeLimitFailure(invalid)) {
                throw invalid;
            }
            throw new IllegalArgumentException("config invalid");
        } catch (IOException invalid) {
            throw new IllegalArgumentException("config invalid");
        }
    }

    private static void requireLegacy(Object... values) {
        for (Object value : values) {
            if (value == null) {
                throw failure("CLI_ARGUMENT_INVALID");
            }
        }
    }

    @FunctionalInterface
    private interface TargetCall {
        Object invoke() throws IOException;
    }

    /** Strict, deliberately finite parser for the human TOML adapter configuration. */
    private record TargetConfig(Path workspace, Path sourceRegistry, String bind, int port,
                                String bearerTokenEnvironment, int maxRequestBytes, int maxQueuedRuns) {
        private static final Set<String> ROOT = Set.of("config_version");
        private static final Map<String, Set<String>> FIELDS = Map.of(
                "storage", Set.of("workspace", "source_registry"),
                "profile", Set.of("bundle_id", "bundle_sha256"),
                "provider", Set.of("adapter", "auth_mode", "expected_upstream_provider", "model",
                        "reasoning_effort", "sandbox", "base_url", "allowed_hosts", "api_key_env"),
                "server", Set.of("bind", "port", "bearer_token_env"),
                "limits", Set.of("max_request_bytes", "max_queued_runs", "max_event_bytes",
                        "max_attempt_bytes", "max_candidate_bytes", "max_sidecar_bytes",
                        "max_trace_records", "max_validation_receipts_per_candidate"));

        static TargetConfig parse(String source) {
            if (source == null || source.indexOf('\u0000') >= 0) {
                throw new IllegalArgumentException("toml invalid");
            }
            Map<String, Map<String, String>> sections = new HashMap<>();
            sections.put("", new HashMap<>());
            String current = "";
            for (String raw : source.split("\\n", -1)) {
                String line = uncomment(raw).trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]")
                        && line.indexOf(']') == line.length() - 1) {
                    String section = line.substring(1, line.length() - 1);
                    if (!FIELDS.containsKey(section) || sections.containsKey(section)) {
                        throw new IllegalArgumentException("toml invalid");
                    }
                    sections.put(section, new HashMap<>());
                    current = section;
                    continue;
                }
                int equals = line.indexOf('=');
                if (equals <= 0 || line.indexOf('=', equals + 1) >= 0) {
                    throw new IllegalArgumentException("toml invalid");
                }
                String key = line.substring(0, equals).trim();
                String rawValue = line.substring(equals + 1).trim();
                Set<String> permitted = current.isEmpty() ? ROOT : FIELDS.get(current);
                if (!key.matches("[a-z][a-z0-9_]*") || !permitted.contains(key)
                        || rawValue.isEmpty() || sections.get(current).putIfAbsent(key, rawValue) != null) {
                    throw new IllegalArgumentException("toml invalid");
                }
            }
            if (!sections.keySet().equals(Set.of("", "storage", "profile", "provider", "server", "limits"))
                    || !sections.get("").keySet().equals(ROOT)) {
                throw new IllegalArgumentException("toml invalid");
            }
            for (Map.Entry<String, Set<String>> entry : FIELDS.entrySet()) {
                if (!sections.get(entry.getKey()).keySet().equals(entry.getValue())) {
                    throw new IllegalArgumentException("toml invalid");
                }
            }
            if (!"1".equals(sections.get("").get("config_version"))) {
                throw new IllegalArgumentException("toml invalid");
            }
            Map<String, String> storage = strings(sections.get("storage"));
            Map<String, String> profile = strings(sections.get("profile"));
            Map<String, String> provider = stringsExceptArray(sections.get("provider"));
            Map<String, String> server = sections.get("server");
            Map<String, String> limits = sections.get("limits");
            String bind = quoted(server.get("bind"));
            String bearerTokenEnvironment = quoted(server.get("bearer_token_env"));
            if (!"[]".equals(provider.remove("allowed_hosts")) || !profile.get("bundle_sha256").matches("[0-9a-f]{64}")
                    || profile.get("bundle_id").isBlank() || storage.values().stream().anyMatch(String::isBlank)
                    || provider.values().stream().anyMatch(value -> value == null)
                    || bind.isBlank() || !bearerTokenEnvironment.matches("[A-Z_][A-Z0-9_]*")) {
                throw new IllegalArgumentException("toml invalid");
            }
            String apiKeyEnvironment = provider.get("api_key_env");
            if (!apiKeyEnvironment.isEmpty() && !apiKeyEnvironment.matches("[A-Z_][A-Z0-9_]*")) {
                throw new IllegalArgumentException("toml invalid");
            }
            Path workspace = absolute(storage.get("workspace"));
            Path registry = absolute(storage.get("source_registry"));
            int port = integer(server.get("port"), 0, 65535);
            int requestBytes = integer(limits.get("max_request_bytes"), 1, Integer.MAX_VALUE);
            int queuedRuns = integer(limits.get("max_queued_runs"), 1, Integer.MAX_VALUE);
            for (String value : limits.values()) {
                integer(value, 1, Integer.MAX_VALUE);
            }
            return new TargetConfig(workspace, registry, bind, port, bearerTokenEnvironment,
                    requestBytes, queuedRuns);
        }

        private static Map<String, String> strings(Map<String, String> values) {
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                result.put(entry.getKey(), quoted(entry.getValue()));
            }
            return result;
        }

        private static Map<String, String> stringsExceptArray(Map<String, String> values) {
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                result.put(entry.getKey(), "allowed_hosts".equals(entry.getKey())
                        ? entry.getValue() : quoted(entry.getValue()));
            }
            return result;
        }

        private static String quoted(String value) {
            if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
                throw new IllegalArgumentException("toml invalid");
            }
            StringBuilder decoded = new StringBuilder();
            boolean escaped = false;
            for (int index = 1; index < value.length() - 1; index++) {
                char character = value.charAt(index);
                if (escaped) {
                    if (character != '"' && character != '\\') {
                        throw new IllegalArgumentException("toml invalid");
                    }
                    decoded.append(character);
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (Character.isISOControl(character)) {
                    throw new IllegalArgumentException("toml invalid");
                } else {
                    decoded.append(character);
                }
            }
            if (escaped) {
                throw new IllegalArgumentException("toml invalid");
            }
            return decoded.toString();
        }

        private static String uncomment(String raw) {
            boolean quoted = false;
            boolean escaped = false;
            for (int index = 0; index < raw.length(); index++) {
                char character = raw.charAt(index);
                if (escaped) {
                    escaped = false;
                } else if (character == '\\' && quoted) {
                    escaped = true;
                } else if (character == '"') {
                    quoted = !quoted;
                } else if (character == '#' && !quoted) {
                    return raw.substring(0, index);
                }
            }
            if (quoted || escaped) {
                throw new IllegalArgumentException("toml invalid");
            }
            return raw;
        }

        private static Path absolute(String value) {
            try {
                Path path = Path.of(value);
                if (!path.isAbsolute()) {
                    throw new IllegalArgumentException("toml invalid");
                }
                return path.normalize();
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("toml invalid");
            }
        }

        private static int integer(String value, int minimum, int maximum) {
            try {
                if (!value.matches("0|[1-9][0-9]*")) {
                    throw new IllegalArgumentException("toml invalid");
                }
                int parsed = Integer.parseInt(value);
                if (parsed < minimum || parsed > maximum) {
                    throw new IllegalArgumentException("toml invalid");
                }
                return parsed;
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("toml invalid");
            }
        }
    }

    private static final class RecordedProvider implements ModelProvider {
        private static final ObjectMapper JSON = new ObjectMapper();

        private final JsonNode r1;
        private final JsonNode r2;
        private boolean r1Consumed;
        private boolean r2Consumed;

        private RecordedProvider(JsonNode r1, JsonNode r2) {
            this.r1 = r1.deepCopy();
            this.r2 = r2.deepCopy();
        }

        @Override
        public String execute(ModelTask task) {
            try {
                if (task.round() == 1) {
                    if (r1Consumed) {
                        throw failure("RECORDED_R1_ALREADY_CONSUMED");
                    }
                    r1Consumed = true;
                    return JSON.writeValueAsString(r1);
                }
                if (task.round() == 2) {
                    if (r2Consumed) {
                        throw failure("RECORDED_R2_ALREADY_CONSUMED");
                    }
                    r2Consumed = true;
                    return JSON.writeValueAsString(r2);
                }
                throw failure("RECORDED_ROUND_INVALID");
            } catch (IOException serializationFailure) {
                throw failure("RECORDED_RESPONSE_SERIALIZATION_FAILURE", serializationFailure);
            }
        }

        private void requireConsumedExactlyOnce() {
            if (!r1Consumed || !r2Consumed) {
                throw failure("RECORDED_ROUND_NOT_CONSUMED");
            }
        }
    }

    private static JsonNode recordedObject(Path path, String errorCode) {
        Path regular = existingRegularFile(path, errorCode);
        try {
            JsonNode node = JSON.readTree(Files.readAllBytes(regular));
            if (node == null || !node.isObject()) {
                throw failure(errorCode);
            }
            return node;
        } catch (IOException invalid) {
            throw failure(errorCode, invalid);
        }
    }

    private static CandidateReference archivedCandidate(Path workspace, String expectedCandidateId) {
        Path candidateSidecar = existingRegularFile(workspace.resolve("candidate.json"),
                "CANDIDATE_SIDECAR_INVALID");
        if (!workspace.equals(candidateSidecar.getParent())) {
            throw failure("CANDIDATE_SIDECAR_INVALID");
        }
        JsonNode recorded = strictObject(candidateSidecar, "CANDIDATE_SIDECAR_INVALID");
        if (!exactFields(recorded, Set.of("schemaVersion", "candidateId", "candidateContentId",
                "documentSha256")) || !isSchemaVersionOne(recorded)) {
            throw failure("CANDIDATE_SIDECAR_INVALID");
        }
        String archivedCandidateId = requiredCandidateId(recorded, "candidateId",
                "CANDIDATE_SIDECAR_INVALID");
        String candidateContentId = requiredSha256(recorded, "candidateContentId",
                "CANDIDATE_SIDECAR_INVALID");
        String documentSha256 = requiredSha256(recorded, "documentSha256",
                "CANDIDATE_SIDECAR_INVALID");
        if (!expectedCandidateId.equals(archivedCandidateId)) {
            throw failure("UNKNOWN_CANDIDATE");
        }
        if (!candidateContentId.equals(documentSha256)) {
            throw failure("CANDIDATE_SIDECAR_IDENTITY_MISMATCH");
        }
        return new CandidateReference(archivedCandidateId, candidateContentId,
                archivedMarkdown(workspace));
    }

    private static String archivedMarkdown(Path workspace) {
        Path document = workspace.resolve("document.md");
        if (Files.isSymbolicLink(document)
                || !Files.isRegularFile(document, LinkOption.NOFOLLOW_LINKS)) {
            return "";
        }
        try {
            Path resolvedDocument = document.toRealPath();
            if (!workspace.equals(resolvedDocument.getParent())) {
                throw failure("DOCUMENT_PATH_INVALID");
            }
            return new String(Files.readAllBytes(resolvedDocument), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return "";
        }
    }

    private static JsonNode strictObject(Path path, String errorCode) {
        try {
            JsonNode node = JSON.readTree(Files.readAllBytes(path));
            if (node == null || !node.isObject()) {
                throw failure(errorCode);
            }
            return node;
        } catch (IOException invalid) {
            throw failure(errorCode, invalid);
        }
    }

    private static boolean exactFields(JsonNode object, Set<String> expected) {
        Set<String> fields = new java.util.HashSet<>();
        object.fieldNames().forEachRemaining(fields::add);
        return fields.equals(expected);
    }

    private static boolean isSchemaVersionOne(JsonNode object) {
        JsonNode version = object.get("schemaVersion");
        return version != null && version.isInt() && version.intValue() == 1;
    }

    private static String requiredCandidateId(JsonNode object, String field, String errorCode) {
        String candidateId = requiredText(object, field, errorCode);
        if (!candidateId.matches("candidate:[0-9a-f]{64}")) {
            throw failure(errorCode);
        }
        return candidateId;
    }

    private static String requiredSha256(JsonNode object, String field, String errorCode) {
        String value = requiredText(object, field, errorCode);
        if (!value.matches("[0-9a-f]{64}")) {
            throw failure(errorCode);
        }
        return value;
    }

    private static String requiredText(JsonNode object, String field, String errorCode) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw failure(errorCode);
        }
        return value.textValue();
    }

    private static String validationReceiptJson(ValidationReceipt receipt) {
        var node = JSON.createObjectNode();
        node.put("valid", receipt.valid());
        node.put("candidateId", receipt.candidateId());
        node.put("candidateContentId", receipt.candidateContentId());
        node.put("documentSha256", receipt.documentSha256());
        var findings = node.putArray("findings");
        receipt.findings().forEach(findings::add);
        try {
            return JSON.writeValueAsString(node);
        } catch (IOException serializationFailure) {
            throw failure("VALIDATION_RECEIPT_SERIALIZATION_FAILURE", serializationFailure);
        }
    }

    private static DiscoveryResult discoverOrGap(Path repositoryRoot) {
        try {
            return new RepositoryDiscoverer().discover(new DiscoveryRequest(repositoryRoot));
        } catch (RuntimeException unresolved) {
            return new DiscoveryResult(List.of(), List.of(), List.of(), List.of(),
                    List.of(new DiscoveryGap("DISCOVERY_UNRESOLVED")));
        }
    }

    private static SourceScan scanSources(Path repositoryRoot) {
        if (repositoryRoot == null) {
            return new SourceScan(0, 0, 0, List.of(new DiscoveryGap("SOURCE_TREE_READ_UNRESOLVED")));
        }
        Path root = repositoryRoot.toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(root)) {
            int javaFileCount = 0;
            int xmlFileCount = 0;
            for (Path path : paths.filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList()) {
                String name = path.getFileName().toString();
                if (name.endsWith(".java")) {
                    javaFileCount++;
                } else if (name.endsWith(".xml")) {
                    xmlFileCount++;
                }
            }
            return new SourceScan(javaFileCount + xmlFileCount, javaFileCount, xmlFileCount, List.of());
        } catch (IOException | RuntimeException unreadable) {
            return new SourceScan(0, 0, 0, List.of(new DiscoveryGap("SOURCE_TREE_READ_UNRESOLVED")));
        }
    }

    private static List<DiscoveryGap> mergedGaps(List<DiscoveryGap> first,
                                                   List<DiscoveryGap> second) {
        List<DiscoveryGap> gaps = new ArrayList<>(first);
        gaps.addAll(second);
        return gaps.stream().distinct().sorted(Comparator.comparing(DiscoveryGap::code)).toList();
    }

    private static String json(Object value, String errorCode) {
        try {
            return JSON.writeValueAsString(value);
        } catch (IOException serializationFailure) {
            throw failure(errorCode, serializationFailure);
        }
    }

    private static Path existingRegularFile(Path path, String errorCode) {
        Path checked = normalizePath(path, errorCode);
        if (Files.isSymbolicLink(checked)
                || !Files.isRegularFile(checked, LinkOption.NOFOLLOW_LINKS)) {
            throw failure(errorCode);
        }
        try {
            return checked.toRealPath();
        } catch (IOException invalid) {
            throw failure(errorCode, invalid);
        }
    }

    private static Path existingDirectory(Path path, String errorCode) {
        Path checked = normalizePath(path, errorCode);
        if (Files.isSymbolicLink(checked)
                || !Files.isDirectory(checked, LinkOption.NOFOLLOW_LINKS)) {
            throw failure(errorCode);
        }
        try {
            return checked.toRealPath();
        } catch (IOException invalid) {
            throw failure(errorCode, invalid);
        }
    }

    private static Path normalizePath(Path path, String errorCode) {
        if (path == null) {
            throw failure(errorCode);
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.getFileName() == null) {
            throw failure(errorCode);
        }
        return normalized;
    }

    private static void rejectUnsafeToken(String value, String errorCode) {
        if (value == null || value.isBlank() || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0
                || value.contains("..") || value.chars().anyMatch(Character::isISOControl)) {
            throw failure(errorCode);
        }
    }

    private static IllegalStateException failure(String errorCode) {
        return new IllegalStateException(errorCode);
    }

    private static IllegalStateException failure(String errorCode, Exception cause) {
        return new IllegalStateException(errorCode, cause);
    }

    private record SourceScan(int fileCount, int javaFileCount, int xmlFileCount,
                              List<DiscoveryGap> gaps) {
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CodeMdCli()).execute(args);
        System.exit(exitCode);
    }
}
