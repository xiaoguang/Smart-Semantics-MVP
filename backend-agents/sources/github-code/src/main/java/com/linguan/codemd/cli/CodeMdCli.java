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
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/** Offline-only command line for archived, recorded provider outputs. */
@Command(name = "code-md", mixinStandardHelpOptions = true,
        description = "Deterministic code-to-nine-section Markdown agent.",
        subcommands = {CodeMdCli.GenerateCommand.class, CodeMdCli.TraceCommand.class,
                CodeMdCli.ValidateCommand.class, CodeMdCli.InspectCommand.class,
                CodeMdCli.DiscoverCommand.class, CodeMdCli.BaselineCommand.class})
public final class CodeMdCli implements Callable<Integer> {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final String PROFILE = "WALKING_SLICE_V0";

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(out());
        return 2;
    }

    private PrintWriter out() {
        return spec.commandLine().getOut();
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

        @Option(names = "--manifest", required = true, paramLabel = "PATH")
        private Path manifest;

        @Option(names = "--snapshot-root", required = true, paramLabel = "PATH")
        private Path snapshotRoot;

        @Option(names = "--workspace", required = true, paramLabel = "PATH")
        private Path workspace;

        @Option(names = "--recorded-r1", required = true, paramLabel = "PATH")
        private Path recordedR1;

        @Option(names = "--recorded-r2", required = true, paramLabel = "PATH")
        private Path recordedR2;

        @Override
        public Integer call() {
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

        @Option(names = "--workspace", required = true, paramLabel = "PATH")
        private Path workspace;

        @Option(names = "--candidate-id", required = true, paramLabel = "ID")
        private String candidateId;

        @Option(names = "--item-key", required = true, paramLabel = "KEY")
        private String itemKey;

        @Override
        public Integer call() {
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

        @Option(names = "--workspace", required = true, paramLabel = "PATH")
        private Path workspace;

        @Option(names = "--candidate-id", required = true, paramLabel = "ID")
        private String candidateId;

        @Override
        public Integer call() {
            rejectUnsafeToken(candidateId, "CANDIDATE_ID_INVALID");
            Path checkedWorkspace = existingDirectory(workspace, "WORKSPACE_INVALID");
            CandidateReference candidate = archivedCandidate(checkedWorkspace, candidateId);
            ValidationReceipt receipt = new CandidateArchiveService(checkedWorkspace).validate(candidate);
            root.out().println(validationReceiptJson(receipt));
            return receipt.valid() ? 0 : 1;
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
