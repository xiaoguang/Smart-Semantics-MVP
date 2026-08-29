package com.linguan.codemd.cli;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 contract for the fresh-process discovery CLI boundary.
 *
 * <p>The repository is deliberately synthetic and is never compiled or
 * executed. The CLI must expose only stable relative source identities and
 * conservative gaps for unsupported source, not temporary fixture paths.</p>
 */
class CodeMdCliDiscoveryTest {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    @Test
    void inspectReturnsOneJsonSummaryWithEnabledProfileAndDeterministicCounts() throws Exception {
        Path root = Files.createTempDirectory("phase1-cli-inspect-");
        Fixture.staticUpdates(root);

        Invocation invocation = invokeFresh("inspect", root);

        assertEquals(0, invocation.exitCode(), invocation.stdout());
        JsonNode summary = oneJsonObject(invocation.stdout());
        assertEquals("WALKING_SLICE_V0", summary.path("profile").asText());
        assertEquals(4, summary.path("fileCount").asInt());
        assertEquals(3, summary.path("javaFileCount").asInt());
        assertEquals(1, summary.path("xmlFileCount").asInt());
        assertEquals(1, summary.path("routeCount").asInt());
        assertNoEphemeralAbsoluteIdentity(invocation.stdout(), root);
    }

    @Test
    void discoverReturnsOneJsonObjectWithStaticFlowFactsFromAFreshCli() throws Exception {
        Path root = Files.createTempDirectory("phase1-cli-discover-");
        Fixture.staticUpdates(root);

        Invocation invocation = invokeFresh("discover", root);

        assertEquals(0, invocation.exitCode(), invocation.stdout());
        JsonNode result = oneJsonObject(invocation.stdout());
        assertEquals(List.of("POST /orders/approve"), routeNames(result));
        assertEquals(List.of(
                        "example.OrderController#approve -> example.OrderService#approve",
                        "example.OrderService#approve -> example.OrderMapper#approve"),
                directCallNames(result));
        assertEquals(List.of(
                        "example.OrderMapper#approve @ src/main/resources/mapper/OrderMapper.xml#approve"),
                mapperBindingNames(result));
        assertEquals(List.of("approve:orders.status=APPROVED"), sqlUpdateNames(result));
        assertTrue(result.path("gaps").isArray());
        assertTrue(result.path("gaps").isEmpty(),
                "a fully static fixture must not report a discovery gap");
        assertNoEphemeralAbsoluteIdentity(invocation.stdout(), root);
    }

    @Test
    void discoverReturnsGapInsteadOfAdmittingDynamicSqlOrUnparseableSource() throws Exception {
        Path root = Files.createTempDirectory("phase1-cli-gap-");
        Fixture.dynamicAndUnparseable(root);

        Invocation invocation = invokeFresh("discover", root);

        assertEquals(0, invocation.exitCode(), invocation.stdout());
        JsonNode result = oneJsonObject(invocation.stdout());
        assertTrue(result.path("sqlUpdateFacts").isArray());
        assertTrue(result.path("sqlUpdateFacts").isEmpty(),
                "dynamic SQL must not become a successful static update fact");
        assertEquals(List.of("POST /orders/status"), routeNames(result));
        assertTrue(gapCodes(result).contains("DYNAMIC_SQL_UNRESOLVED"),
                "dynamic SQL must be represented as an explicit gap");
        assertTrue(gapCodes(result).stream().anyMatch(code -> code.contains("PARSE")),
                "an unparseable source file must be represented as a parse gap");
        assertNoEphemeralAbsoluteIdentity(invocation.stdout(), root);
    }

    private static Invocation invokeFresh(String command, Path root) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        CommandLine cli = new CommandLine(new CodeMdCli());
        cli.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));
        cli.setErr(new PrintWriter(errors, true, StandardCharsets.UTF_8));
        int exitCode = cli.execute(command, "--repository-root", root.toString());
        return new Invocation(exitCode, output.toString(StandardCharsets.UTF_8),
                errors.toString(StandardCharsets.UTF_8));
    }

    private static JsonNode oneJsonObject(String stdout) throws IOException {
        JsonNode value = JSON.readTree(stdout);
        assertNotNull(value, "CLI stdout must contain one JSON value");
        assertTrue(value.isObject(), "CLI stdout must contain one JSON object");
        return value;
    }

    private static void assertNoEphemeralAbsoluteIdentity(String stdout, Path root) {
        String absoluteRoot = root.toAbsolutePath().normalize().toString();
        assertFalse(stdout.contains(absoluteRoot),
                "CLI identity must not depend on a temporary absolute fixture path");
    }

    private static List<String> routeNames(JsonNode result) {
        List<String> routes = new ArrayList<>();
        for (JsonNode route : result.path("httpRoutes")) {
            routes.add(route.path("httpMethod").asText() + " " + route.path("path").asText());
        }
        return routes;
    }

    private static List<String> directCallNames(JsonNode result) {
        List<String> edges = new ArrayList<>();
        for (JsonNode edge : result.path("directCallEdges")) {
            edges.add(edge.path("caller").asText() + " -> " + edge.path("callee").asText());
        }
        return edges;
    }

    private static List<String> mapperBindingNames(JsonNode result) {
        List<String> bindings = new ArrayList<>();
        for (JsonNode binding : result.path("mapperBindings")) {
            bindings.add(binding.path("mapperType").asText() + "#"
                    + binding.path("methodName").asText() + " @ "
                    + binding.path("xmlPath").asText() + "#"
                    + binding.path("statementId").asText());
        }
        return bindings;
    }

    private static List<String> sqlUpdateNames(JsonNode result) {
        List<String> facts = new ArrayList<>();
        for (JsonNode fact : result.path("sqlUpdateFacts")) {
            facts.add(fact.path("mapperMethod").asText() + ":"
                    + fact.path("table").asText() + "."
                    + fact.path("field").asText() + "="
                    + fact.path("value").asText());
        }
        return facts;
    }

    private static List<String> gapCodes(JsonNode result) {
        List<String> gaps = new ArrayList<>();
        for (JsonNode gap : result.path("gaps")) {
            gaps.add(gap.isTextual() ? gap.asText() : gap.path("code").asText());
        }
        return gaps;
    }

    private record Invocation(int exitCode, String stdout, String stderr) {
    }

    private static final class Fixture {
        private Fixture() {
        }

        private static void staticUpdates(Path root) throws IOException {
            writeSources(root,
                    "    @PostMapping(\"/approve\")\n"
                            + "    public void approve(String id) {\n"
                            + "        service.approve(id);\n"
                            + "    }\n",
                    "    public void approve(String id) {\n"
                            + "        mapper.approve(id);\n"
                            + "    }\n",
                    "    int approve(String id);\n",
                    "  <update id=\"approve\">\n"
                            + "    update orders set status = 'APPROVED' where id = #{id}\n"
                            + "  </update>\n");
        }

        private static void dynamicAndUnparseable(Path root) throws IOException {
            writeSources(root,
                    "    @PostMapping(\"/status\")\n"
                            + "    public void status(String id, String status) {\n"
                            + "        service.status(id, status);\n"
                            + "    }\n",
                    "    public void status(String id, String status) {\n"
                            + "        mapper.updateStatus(id, status);\n"
                            + "    }\n",
                    "    int updateStatus(String id, String status);\n",
                    "  <update id=\"updateStatus\">\n"
                            + "    update ${table} set status = #{status}\n"
                            + "    <if test=\"id != null\">where id = #{id}</if>\n"
                            + "  </update>\n");
            write(root.resolve("src/main/java/example/Broken.java"),
                    "package example;\n"
                            + "public class Broken {\n"
                            + "    public void missingBrace( {\n");
        }

        private static void writeSources(Path root, String controllerMethods,
                                         String serviceMethods, String mapperMethods,
                                         String xmlStatements) throws IOException {
            Path javaRoot = root.resolve("src/main/java/example");
            Path resourceRoot = root.resolve("src/main/resources/mapper");
            Files.createDirectories(javaRoot);
            Files.createDirectories(resourceRoot);

            write(javaRoot.resolve("OrderController.java"),
                    "package example;\n"
                            + "import org.springframework.web.bind.annotation.PostMapping;\n"
                            + "import org.springframework.web.bind.annotation.RequestMapping;\n"
                            + "import org.springframework.web.bind.annotation.RestController;\n"
                            + "@RestController\n"
                            + "@RequestMapping(\"/orders\")\n"
                            + "public class OrderController {\n"
                            + "    private final OrderService service;\n"
                            + "    public OrderController(OrderService service) { this.service = service; }\n"
                            + controllerMethods
                            + "}\n");
            write(javaRoot.resolve("OrderService.java"),
                    "package example;\n"
                            + "public class OrderService {\n"
                            + "    private final OrderMapper mapper;\n"
                            + "    public OrderService(OrderMapper mapper) { this.mapper = mapper; }\n"
                            + serviceMethods
                            + "}\n");
            write(javaRoot.resolve("OrderMapper.java"),
                    "package example;\n"
                            + "import org.apache.ibatis.annotations.Mapper;\n"
                            + "@Mapper\n"
                            + "public interface OrderMapper {\n"
                            + mapperMethods
                            + "}\n");
            write(resourceRoot.resolve("OrderMapper.xml"),
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<mapper namespace=\"example.OrderMapper\">\n"
                            + xmlStatements
                            + "</mapper>\n");
        }

        private static void write(Path path, String content) throws IOException {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        }
    }
}
