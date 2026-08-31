package com.linguan.codemd.stage01;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test-only mutations built from the frozen six-file reservation fixture. */
final class M2Fixtures {
    private static final String JAVA_ROOT = "src/main/java/";

    private M2Fixtures() {
    }

    static FrozenRepositoryRequest requestWithBudget(Path snapshotRoot, ResourceBudget budget) {
        return Stage01Fixtures.request(snapshotRoot, Stage01Fixtures.declaredFiles(), budget);
    }

    static FrozenRepositoryRequest dynamicMapperRequest(Path snapshotRoot) throws IOException {
        String path = "src/main/resources/mappers/InventoryMapper.xml";
        String original = Files.readString(snapshotRoot.resolve(path), StandardCharsets.UTF_8);
        String dynamic = original.replace(
                "WHERE sku = #{sku} AND version = #{version}",
                "<if test=\"sku != null\">WHERE sku = ${sku} AND version = #{version}</if>");
        return requestWithReplacements(snapshotRoot,
                Map.of(path, new Replacement("XML", dynamic)));
    }

    static FrozenRepositoryRequest overloadedMapperRequest(Path snapshotRoot) throws IOException {
        String mapperPath = JAVA_ROOT + "example/inventory/InventoryMapper.java";
        String mapper = Files.readString(snapshotRoot.resolve(mapperPath), StandardCharsets.UTF_8)
                .replace("  InventoryRow findBySku(@Param(\"sku\") String sku);\n",
                        "  InventoryRow findBySku(@Param(\"sku\") String sku);\n"
                                + "  InventoryRow findBySku(Integer sku);\n");
        String servicePath = JAVA_ROOT + "example/inventory/ReservationService.java";
        String service = Files.readString(snapshotRoot.resolve(servicePath), StandardCharsets.UTF_8)
                .replace("mapper.findBySku(sku)", "mapper.findBySku(null)");
        Map<String, Replacement> replacements = new LinkedHashMap<>();
        replacements.put(mapperPath, new Replacement("JAVA", mapper));
        replacements.put(servicePath, new Replacement("JAVA", service));
        return requestWithReplacements(snapshotRoot, replacements);
    }

    static FrozenRepositoryRequest arityMismatchRequest(Path snapshotRoot) throws IOException {
        String servicePath = JAVA_ROOT + "example/inventory/ReservationService.java";
        String service = Files.readString(snapshotRoot.resolve(servicePath), StandardCharsets.UTF_8)
                .replace("mapper.findBySku(sku)", "mapper.findBySku(sku, quantity)");
        return requestWithReplacements(snapshotRoot,
                Map.of(servicePath, new Replacement("JAVA", service)));
    }

    static FrozenRepositoryRequest insertDeleteMapperRequest(Path snapshotRoot) throws IOException {
        String mapperPath = JAVA_ROOT + "example/inventory/InventoryMapper.java";
        String mapper = Files.readString(snapshotRoot.resolve(mapperPath), StandardCharsets.UTF_8);
        mapper = appendBeforeFinalBrace(mapper,
                "  int insertReservation(@Param(\"sku\") String sku,\n"
                        + "                     @Param(\"quantity\") int quantity);\n"
                        + "  int deleteReservation(@Param(\"sku\") String sku);\n");

        String xmlPath = "src/main/resources/mappers/InventoryMapper.xml";
        String xml = Files.readString(snapshotRoot.resolve(xmlPath), StandardCharsets.UTF_8)
                .replace("</mapper>\n",
                        "  <insert id=\"insertReservation\">\n"
                                + "    INSERT INTO inventory (sku, reserved_qty)\n"
                                + "    VALUES (#{sku}, #{quantity})\n"
                                + "  </insert>\n"
                                + "  <delete id=\"deleteReservation\">\n"
                                + "    DELETE FROM inventory WHERE sku = #{sku}\n"
                                + "  </delete>\n"
                                + "</mapper>\n");
        Map<String, Replacement> replacements = new LinkedHashMap<>();
        replacements.put(mapperPath, new Replacement("JAVA", mapper));
        replacements.put(xmlPath, new Replacement("XML", xml));
        return requestWithReplacements(snapshotRoot, replacements);
    }

    static FrozenRepositoryRequest duplicateMapperStatementRequest(Path snapshotRoot)
            throws IOException {
        String xmlPath = "src/main/resources/mappers/InventoryMapper.xml";
        String xml = Files.readString(snapshotRoot.resolve(xmlPath), StandardCharsets.UTF_8)
                .replace("</mapper>\n",
                        "  <update id=\"addReservation\">\n"
                                + "    UPDATE inventory SET reserved_qty = reserved_qty + #{quantity}\n"
                                + "  </update>\n"
                                + "</mapper>\n");
        return requestWithReplacements(snapshotRoot,
                Map.of(xmlPath, new Replacement("XML", xml)));
    }

    static FrozenRepositoryRequest unresolvedLocalMappingRequest(Path snapshotRoot)
            throws IOException {
        Map<String, Replacement> additions = new LinkedHashMap<>();
        additions.put(JAVA_ROOT + "local/web/MappingAnnotations.java",
                new Replacement("JAVA", "package local.web;\n"
                        + "@interface RestController {}\n"
                        + "@interface RequestMapping { String value(); }\n"
                        + "@interface PostMapping {}\n"));
        additions.put(JAVA_ROOT + "local/web/LocalAnnotationController.java",
                new Replacement("JAVA", "package local.web;\n"
                        + "@RestController\n"
                        + "@RequestMapping(\"/not-spring\")\n"
                        + "final class LocalAnnotationController {\n"
                        + "  @PostMapping\n"
                        + "  void execute() {}\n"
                        + "}\n"));
        return requestWithReplacements(snapshotRoot, additions);
    }

    static FrozenRepositoryRequest wildcardImportRequest(Path snapshotRoot) throws IOException {
        Map<String, Replacement> additions = new LinkedHashMap<>();
        additions.put(JAVA_ROOT + "wildcard/WildcardController.java",
                new Replacement("JAVA", "package wildcard;\n"
                        + "import org.springframework.web.bind.annotation.PostMapping;\n"
                        + "import org.springframework.web.bind.annotation.RestController;\n"
                        + "@RestController\n"
                        + "final class WildcardController {\n"
                        + "  private final WildcardService service;\n"
                        + "  WildcardController(WildcardService service) { this.service = service; }\n"
                        + "  @PostMapping(\"/wildcard\")\n"
                        + "  void read(String sku) { service.read(sku); }\n"
                        + "}\n"));
        additions.put(JAVA_ROOT + "wildcard/WildcardService.java",
                new Replacement("JAVA", "package wildcard;\n"
                        + "import one.*;\n"
                        + "final class WildcardService {\n"
                        + "  private final InventoryMapper mapper;\n"
                        + "  WildcardService(InventoryMapper mapper) { this.mapper = mapper; }\n"
                        + "  void read(String sku) { mapper.findBySku(sku); }\n"
                        + "}\n"));
        additions.put(JAVA_ROOT + "one/InventoryMapper.java",
                new Replacement("JAVA", "package one;\n"
                        + "public interface InventoryMapper { void findBySku(String sku); }\n"));
        return requestWithReplacements(snapshotRoot, additions);
    }

    static FrozenRepositoryRequest sameSimpleNameReceiverRequest(Path snapshotRoot)
            throws IOException {
        Map<String, Replacement> additions = new LinkedHashMap<>();
        additions.put(JAVA_ROOT + "ambiguous/AmbiguousController.java",
                new Replacement("JAVA", "package ambiguous;\n"
                        + "import org.springframework.web.bind.annotation.PostMapping;\n"
                        + "import org.springframework.web.bind.annotation.RestController;\n"
                        + "@RestController\n"
                        + "final class AmbiguousController {\n"
                        + "  private final AmbiguousService service;\n"
                        + "  AmbiguousController(AmbiguousService service) { this.service = service; }\n"
                        + "  @PostMapping(\"/ambiguous\")\n"
                        + "  void read(String sku) { service.read(sku); }\n"
                        + "}\n"));
        additions.put(JAVA_ROOT + "ambiguous/AmbiguousService.java",
                new Replacement("JAVA", "package ambiguous;\n"
                        + "import one.InventoryMapper;\n"
                        + "import two.InventoryMapper;\n"
                        + "final class AmbiguousService {\n"
                        + "  private final InventoryMapper mapper;\n"
                        + "  AmbiguousService(InventoryMapper mapper) { this.mapper = mapper; }\n"
                        + "  void read(String sku) { mapper.findBySku(sku); }\n"
                        + "}\n"));
        additions.put(JAVA_ROOT + "one/InventoryMapper.java",
                new Replacement("JAVA", "package one;\n"
                        + "public interface InventoryMapper { void findBySku(String sku); }\n"));
        additions.put(JAVA_ROOT + "two/InventoryMapper.java",
                new Replacement("JAVA", "package two;\n"
                        + "public interface InventoryMapper { void findBySku(String sku); }\n"));
        return requestWithReplacements(snapshotRoot, additions);
    }

    private static FrozenRepositoryRequest requestWithReplacements(Path snapshotRoot,
                                                                    Map<String, Replacement> replacements)
            throws IOException {
        return requestWithReplacements(snapshotRoot, replacements, Stage01Fixtures.defaultBudget());
    }

    private static FrozenRepositoryRequest requestWithReplacements(Path snapshotRoot,
                                                                    Map<String, Replacement> replacements,
                                                                    ResourceBudget budget)
            throws IOException {
        List<DeclaredFile> declaredFiles = new ArrayList<>(Stage01Fixtures.declaredFiles());
        for (Map.Entry<String, Replacement> entry : replacements.entrySet()) {
            byte[] bytes = entry.getValue().content().getBytes(StandardCharsets.UTF_8);
            Path target = snapshotRoot.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            DeclaredFile declaration = new DeclaredFile(entry.getKey(), entry.getValue().mediaType(),
                    bytes.length, Stage01Fixtures.sha256(bytes), "UTF-8");
            declaredFiles.removeIf(file -> file.path().equals(entry.getKey()));
            declaredFiles.add(declaration);
        }
        return Stage01Fixtures.request(snapshotRoot, declaredFiles, budget);
    }

    private static String appendBeforeFinalBrace(String source, String addition) {
        int closingBrace = source.lastIndexOf('}');
        if (closingBrace < 0) {
            throw new AssertionError("fixture source has no closing brace");
        }
        return source.substring(0, closingBrace) + addition + source.substring(closingBrace);
    }

    private record Replacement(String mediaType, String content) {
    }
}
