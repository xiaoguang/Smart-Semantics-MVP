package com.linguan.codemd.discovery;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 contract for deterministic Spring MVC/MyBatis source discovery.
 *
 * <p>The fixtures are deliberately source-only: they are not compiled,
 * executed, or resolved against a customer's Maven project. Four source files
 * are enough to exercise the supported chain: controller, service, mapper
 * interface, and mapper XML.</p>
 */
class RepositoryDiscovererTest {
    @Test
    void discoversRoutesCallsUniqueBindingsAndStaticUpdatesInStableOrder() throws Exception {
        Fixture fixture = Fixture.staticUpdates(Files.createTempDirectory("phase1-static-"));

        DiscoveryResult first = discover(fixture);
        DiscoveryResult second = discover(fixture);

        assertEquals(first, second, "repeated discovery must be value-equivalent");
        assertEquals(List.of("POST /orders/approve", "POST /orders/cancel"),
                first.httpRoutes().stream()
                        .map(route -> route.httpMethod() + " " + route.path())
                        .toList());

        HttpRoute approveRoute = first.httpRoutes().get(0);
        assertEquals("src/main/java/example/OrderController.java",
                approveRoute.controllerMethodLocator().relativePath());
        assertEquals(14, approveRoute.controllerMethodLocator().startLine());
        assertEquals(17, approveRoute.controllerMethodLocator().endLine());

        assertEquals(List.of(
                        "example.OrderController#approve -> example.OrderService#approve",
                        "example.OrderController#cancel -> example.OrderService#cancel",
                        "example.OrderService#approve -> example.OrderMapper#approve",
                        "example.OrderService#cancel -> example.OrderMapper#cancel"),
                first.directCallEdges().stream()
                        .map(edge -> edge.caller() + " -> " + edge.callee())
                        .toList());

        assertEquals(List.of(
                        "example.OrderMapper#approve @ src/main/resources/mapper/OrderMapper.xml#approve",
                        "example.OrderMapper#cancel @ src/main/resources/mapper/OrderMapper.xml#cancel"),
                first.mapperBindings().stream()
                        .map(binding -> binding.mapperType() + "#" + binding.methodName()
                                + " @ " + binding.xmlPath() + "#" + binding.statementId())
                        .toList());

        assertEquals(List.of(
                        "approve:orders.status=APPROVED",
                        "cancel:orders.status=CANCELLED"),
                first.sqlUpdateFacts().stream()
                        .map(fact -> fact.mapperMethod() + ":" + fact.table() + "."
                                + fact.field() + "=" + fact.value())
                        .toList());
        assertTrue(first.gaps().isEmpty(), "the static fixture must have no discovery gaps");
    }

    @Test
    void parsesStandardMyBatisDoctypeWithoutExternalRetrieval() throws Exception {
        Fixture fixture = Fixture.staticUpdates(Files.createTempDirectory("phase1-doctype-"));
        Path mapperXml = fixture.repositoryRoot().resolve("src/main/resources/mapper/OrderMapper.xml");
        String xml = Files.readString(mapperXml, StandardCharsets.UTF_8);
        Files.writeString(mapperXml, xml.replace(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\"\n"
                        + "  \"https://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n"),
                StandardCharsets.UTF_8);

        DiscoveryResult result = discover(fixture);

        assertEquals(List.of(
                        "example.OrderMapper#approve @ src/main/resources/mapper/OrderMapper.xml#approve",
                        "example.OrderMapper#cancel @ src/main/resources/mapper/OrderMapper.xml#cancel"),
                result.mapperBindings().stream()
                        .map(binding -> binding.mapperType() + "#" + binding.methodName()
                                + " @ " + binding.xmlPath() + "#" + binding.statementId())
                        .toList());
        assertEquals(List.of(
                        "approve:orders.status=APPROVED",
                        "cancel:orders.status=CANCELLED"),
                result.sqlUpdateFacts().stream()
                        .map(fact -> fact.mapperMethod() + ":" + fact.table() + "."
                                + fact.field() + "=" + fact.value())
                        .toList());
        assertTrue(result.gaps().isEmpty(),
                "the standard MyBatis DOCTYPE must not require external retrieval");
    }

    @Test
    void leavesDynamicSqlOutOfFactsAndReportsAnExplicitGap() throws Exception {
        Fixture fixture = Fixture.dynamicSql(Files.createTempDirectory("phase1-dynamic-"));

        DiscoveryResult result = discover(fixture);

        assertEquals(List.of("POST /orders/status"),
                result.httpRoutes().stream()
                        .map(route -> route.httpMethod() + " " + route.path())
                        .toList());
        assertEquals(List.of(
                        "example.OrderController#status -> example.OrderService#status",
                        "example.OrderService#status -> example.OrderMapper#updateStatus"),
                result.directCallEdges().stream()
                        .map(edge -> edge.caller() + " -> " + edge.callee())
                        .toList());
        assertEquals(List.of("example.OrderMapper#updateStatus"),
                result.mapperBindings().stream()
                        .map(binding -> binding.mapperType() + "#" + binding.methodName())
                        .toList());
        assertTrue(result.sqlUpdateFacts().isEmpty(),
                "dynamic SQL must not become a deterministic SQL fact");
        assertEquals(List.of("DYNAMIC_SQL_UNRESOLVED"),
                result.gaps().stream().map(DiscoveryGap::code).toList());
    }

    private static DiscoveryResult discover(Fixture fixture) {
        return new RepositoryDiscoverer().discover(new DiscoveryRequest(fixture.repositoryRoot()));
    }

    private record Fixture(Path repositoryRoot) {
        private static Fixture staticUpdates(Path root) throws IOException {
            writeSources(root,
                    "    @PostMapping(\"/cancel\")\n"
                            + "    public void cancel(String id) {\n"
                            + "        service.cancel(id);\n"
                            + "    }\n"
                            + "    @PostMapping(\"/approve\")\n"
                            + "    public void approve(String id) {\n"
                            + "        service.approve(id);\n"
                            + "    }\n",
                    "    public void cancel(String id) {\n"
                            + "        mapper.cancel(id);\n"
                            + "    }\n"
                            + "    public void approve(String id) {\n"
                            + "        mapper.approve(id);\n"
                            + "    }\n",
                    "    int cancel(String id);\n"
                            + "    int approve(String id);\n",
                    "  <update id=\"cancel\">\n"
                            + "    update orders set status = 'CANCELLED' where id = #{id}\n"
                            + "  </update>\n"
                            + "  <update id=\"approve\">\n"
                            + "    update orders set status = 'APPROVED' where id = #{id}\n"
                            + "  </update>\n");
            return new Fixture(root);
        }

        private static Fixture dynamicSql(Path root) throws IOException {
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
            return new Fixture(root);
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
