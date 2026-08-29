package com.linguan.codemd.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Small source-only repositories for the Phase 2 CodeFact contract. These
 * files are inputs to the parser; the fixture is never compiled or executed.
 */
final class CodeFactFixtures {
    private CodeFactFixtures() {
    }

    static Path exactCallChain(Path root) throws IOException {
        write(root, "src/main/java/example/OrderController.java",
                "package example;\n"
                        + "\n"
                        + "public class OrderController {\n"
                        + "    private final OrderService service;\n"
                        + "    public OrderController(OrderService service) {\n"
                        + "        this.service = service;\n"
                        + "    }\n"
                        + "\n"
                        + "    public void approve() {\n"
                        + "        service.approve();\n"
                        + "    }\n"
                        + "}\n");
        write(root, "src/main/java/example/OrderService.java",
                "package example;\n"
                        + "\n"
                        + "public class OrderService {\n"
                        + "    private final OrderMapper mapper;\n"
                        + "    public OrderService(OrderMapper mapper) {\n"
                        + "        this.mapper = mapper;\n"
                        + "    }\n"
                        + "\n"
                        + "    public void approve() {\n"
                        + "        mapper.approve();\n"
                        + "    }\n"
                        + "}\n");
        write(root, "src/main/java/example/OrderMapper.java",
                "package example;\n"
                        + "\n"
                        + "public interface OrderMapper {\n"
                        + "    void approve();\n"
                        + "}\n");
        return root;
    }

    static Path ambiguousReceiver(Path root) throws IOException {
        write(root, "src/main/java/example/OrderService.java",
                "package example;\n"
                        + "\n"
                        + "public class OrderService {\n"
                        + "    private final Port port;\n"
                        + "\n"
                        + "    public void approve() {\n"
                        + "        port.approve();\n"
                        + "    }\n"
                        + "}\n");
        write(root, "src/main/java/example/a/Port.java",
                "package example.a;\n"
                        + "\n"
                        + "public class Port {\n"
                        + "    public void approve() {}\n"
                        + "}\n");
        write(root, "src/main/java/example/b/Port.java",
                "package example.b;\n"
                        + "\n"
                        + "public class Port {\n"
                        + "    public void approve() {}\n"
                        + "}\n");
        return root;
    }

    static Path guardedFlow(Path root, String guardLiteral) throws IOException {
        write(root, "src/main/java/example/OrderService.java",
                "package example;\n"
                        + "\n"
                        + "public class OrderService {\n"
                        + "    public void approve(String status) {\n"
                        + "        if (\"" + guardLiteral + "\".equals(status)) {\n"
                        + "            persist();\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    private void persist() {}\n"
                        + "}\n");
        return root;
    }

    private static void write(Path root, String relativePath, String content)
            throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
