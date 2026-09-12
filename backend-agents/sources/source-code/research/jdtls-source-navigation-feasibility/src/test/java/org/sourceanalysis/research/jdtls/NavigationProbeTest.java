package org.sourceanalysis.research.jdtls;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** RED contract for the isolated materializer, navigator boundary, and packet oracle. */
class NavigationProbeTest {

  private static final String SNAPSHOT_COMMIT = "8c30ce7861570458920175e200bb2a6442713580";
  private static final Path SNAPSHOT_REPOSITORY =
      Path.of(
          "/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code"
              + "/.workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580");
  private static final String PROBE_CLASS = "org.sourceanalysis.research.jdtls.NavigationProbe";
  private static final String CHECKER_CLASS =
      "org.sourceanalysis.research.jdtls.PacketOracleCheck";
  private static final String ARCHIVE_NAME =
      "jshERP-8c30ce7861570458920175e200bb2a6442713580.tar";
  private static final String MANIFEST_NAME = "projection-manifest.json";
  private static final String REGISTRATION_ORACLE =
      "checks/registration-oracle.json";
  private static final String FINANCIAL_ORACLE = "checks/financial-oracle.json";

  @Test
  void materializerCopiesEveryMainJavaBlobFromTheFrozenCommit(
      @TempDir Path output) throws Exception {
    assertTrue(Files.isDirectory(SNAPSHOT_REPOSITORY), "the approved object repository is required");
    Method materialize = materializeMethod();
    assertEquals(
        List.of(Path.class, String.class, Path.class),
        Arrays.asList(materialize.getParameterTypes()),
        "materialize(Path snapshotRepository, String snapshotCommit, Path trialRoot) is the research seam");
    assertTrue(Modifier.isStatic(materialize.getModifiers()), "materialization is a stateless Git-object operation");

    materialize.invoke(null, SNAPSHOT_REPOSITORY, SNAPSHOT_COMMIT, output);

    Path archive = output.resolve("input").resolve(ARCHIVE_NAME);
    assertTrue(Files.isRegularFile(archive), "materialization must retain the exact frozen archive");
    Path projection = output.resolve("projection/src");
    List<String> expectedSources = expectedMainJavaPaths();
    assertEquals(
        sha256(gitObject("archive", "--format=tar", SNAPSHOT_COMMIT)),
        sha256(Files.readAllBytes(archive)),
        "the retained archive must be byte-identical to git archive at the pinned commit");
    List<String> archiveEntries = archiveEntries(archive);
    assertTrue(
        archiveEntries.containsAll(expectedSources),
        "the frozen archive must contain every selected main Java source path");

    Path manifest = output.resolve("input").resolve(MANIFEST_NAME);
    assertTrue(Files.isRegularFile(manifest), "materialization must write a projection manifest");
    assertProjectionManifest(manifest, expectedSources, output.resolve("projection/src"));

    List<String> projectedSources;
    try (Stream<Path> files = Files.walk(projection)) {
      projectedSources =
          files.filter(Files::isRegularFile)
              .map(projection::relativize)
              .map(Path::toString)
              .map(path -> path.replace('\\', '/'))
              .sorted()
              .toList();
    }
    List<String> expectedProjectionPaths =
        expectedSources.stream()
            .map(path -> path.substring(path.indexOf("/src/main/java/") + "/src/main/java/".length()))
            .sorted()
            .toList();
    assertEquals(expectedProjectionPaths, projectedSources, "all and only main Java files must be projected");

    for (String sourcePath : expectedSources) {
      String projectedPath = projectedPath(sourcePath);
      byte[] sourceBytes = gitObject("cat-file", "blob", SNAPSHOT_COMMIT + ":" + sourcePath);
      assertEquals(
          sha256(sourceBytes),
          sha256(Files.readAllBytes(projection.resolve(projectedPath))),
          "projected bytes drifted for " + sourcePath);
    }
    assertTrue(
        Files.notExists(output.resolve("projection/pom.xml")),
        "the unmanaged workspace must not contain Maven input");
    assertTrue(
        Files.notExists(output.resolve("projection/build.gradle")),
        "the unmanaged workspace must not contain Gradle input");
    assertNoProhibitedProjectionInputs(output.resolve("projection"));
  }

  @Test
  void preparesIsolatedBuildshipCacheFromTheSelectedJdtDistribution(@TempDir Path trialRoot)
      throws Exception {
    Path install = trialRoot.resolve("tools/selected").toAbsolutePath().normalize();
    Path coreJar = install.resolve("plugins/org.eclipse.jdt.ls.core_test.jar");
    Files.createDirectories(coreJar.getParent());
    byte[] embeddedCatalog =
        "[{\"version\":\"9.8\",\"snapshot\":\"false\",\"activeRc\":\"false\",\"rcFor\":\"\",\"broken\":\"false\"}]"
            .getBytes(StandardCharsets.UTF_8);
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(coreJar))) {
      jar.putNextEntry(new JarEntry("gradle/checksums/versions.json"));
      jar.write(embeddedCatalog);
      jar.closeEntry();
    }
    ObjectNode manifest = new ObjectMapper().createObjectNode();
    manifest.putObject("jdtLanguageServer").put("installDirectory", install.toString());

    Method prepare;
    try {
      prepare =
          TrialRunner.class.getDeclaredMethod("prepareJdtRuntime", Path.class, JsonNode.class);
    } catch (NoSuchMethodException missing) {
      fail("isolated Buildship runtime preparation is missing", missing);
      throw new AssertionError("unreachable");
    }
    prepare.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, String> environment =
        (Map<String, String>) prepare.invoke(null, trialRoot, manifest);

    Path cacheHome = trialRoot.resolve("runtime/cache").toAbsolutePath().normalize();
    Path buildshipCatalog = cacheHome.resolve("tooling/gradle/versions.json");
    assertEquals(cacheHome.toString(), environment.get("XDG_CACHE_HOME"));
    assertTrue(Files.isRegularFile(buildshipCatalog));
    assertArrayEquals(embeddedCatalog, Files.readAllBytes(buildshipCatalog));
  }

  @Test
  void probeSeamAcceptsOnlySnapshotProjectionEntryRangeAndSafetyLimits() {
    Method probe = probeMethod();
    assertEquals(
        List.of(
            String.class,
            Path.class,
            String.class,
            int.class,
            int.class,
            int.class,
            int.class,
            Duration.class),
        Arrays.asList(probe.getParameterTypes()),
        "probe(snapshotCommit, projectionRoot, entryFile, entryStartLine, entryEndLine, maxMethods, maxDepth, timeout)");
    assertTrue(Modifier.isStatic(probe.getModifiers()), "the probe boundary is a stateless request operation");
    assertEquals(
        List.of(
            "snapshotCommit",
            "projectionRoot",
            "entryFile",
            "entryStartLine",
            "entryEndLine",
            "maxMethods",
            "maxDepth",
            "timeout"),
        Arrays.stream(probe.getParameters()).map(Parameter::getName).toList(),
        "the probe API must expose only the frozen snapshot, projection, entry range, and limits");
  }

  @Test
  void probeDoesNotReadSiblingChecksOrInjectHardCodedTargets(@TempDir Path trialRoot) throws Exception {
    Method probe = probeMethod();
    Path projection = trialRoot.resolve("projection");
    Path entry = projection.resolve("src/example/Entry.java");
    Files.createDirectories(entry.getParent());
    String randomTarget = "Target" + UUID.randomUUID().toString().replace("-", "");
    Files.writeString(
        entry,
        "package example;\nclass Entry {\n  void run() { " + randomTarget + ".call(); }\n}\n",
        StandardCharsets.UTF_8);
    Path siblingChecks = trialRoot.resolve("checks");
    Files.createDirectories(siblingChecks);
    String canary = "ORACLE_CANARY_" + UUID.randomUUID();
    Files.writeString(
        siblingChecks.resolve("registration-oracle.json"),
        "{\"canary\":\"" + canary + "\",\"target\":\"" + randomTarget + ".java\"}",
        StandardCharsets.UTF_8);

    Object result =
        probe.invoke(
            null,
            SNAPSHOT_COMMIT,
            projection,
            "src/example/Entry.java",
            1,
            4,
            30,
            4,
            Duration.ofSeconds(1));
    assertTrue(result != null, "probe must return an observable packet/result");
    String packet = result instanceof Path path ? Files.readString(path, StandardCharsets.UTF_8) : result.toString();
    assertFalse(packet.contains(canary), "probe must not read sibling checks input");
    assertFalse(packet.contains(randomTarget + ".java"), "probe must not inject a target from sibling checks");
    assertFalse(packet.contains("jshERP-boot/src/main/java/com/jsh/erp/service"), "probe must not inject a target path");
  }

  @Test
  void navigatorSourceDoesNotContainFrozenOracleTargetsOrLineConstants() throws IOException {
    Path probeSource =
        Path.of("src/main/java/org/sourceanalysis/research/jdtls/NavigationProbe.java");
    assertTrue(Files.isRegularFile(probeSource), "future NavigationProbe.java source is required");
    String source = Files.readString(probeSource, StandardCharsets.UTF_8);
    String lowerCaseSource = source.toLowerCase(Locale.ROOT);
    assertFalse(lowerCaseSource.contains("checks/"), "navigator source must not register a checks directory");
    assertFalse(lowerCaseSource.contains("oracle"), "navigator source must not mention an oracle input");
    for (String targetName :
        List.of(
            "UserController",
            "UserService",
            "AccountHeadController",
            "AccountHeadService",
            "AccountHeadMapperEx")) {
      assertFalse(source.contains(targetName), "navigator source must not hard-code target " + targetName);
    }
    for (String expectedLine :
        List.of(
            "16", "39", "44", "45", "150", "153", "155", "181", "187", "190", "195", "196",
            "297", "319", "357", "363", "364", "365", "367", "442", "443", "444", "607", "608",
            "661", "776", "805")) {
      assertFalse(
          source.matches("(?s).*\\b" + expectedLine + "\\b.*"),
          "navigator source must not hard-code frozen target line " + expectedLine);
    }
  }

  @Test
  void navigatorPublicApiDoesNotAcceptCheckerOrOracleInputs() {
    Class<?> probe = requiredType(PROBE_CLASS);
    for (Method method : probe.getMethods()) {
      String methodName = method.getName().toLowerCase(Locale.ROOT);
      assertFalse(methodName.contains("oracle"), method + " must not expose an oracle operation");
      assertFalse(methodName.contains("check"), method + " must not expose a checker operation");
      for (Parameter parameter : method.getParameters()) {
        String parameterName = parameter.getName().toLowerCase(Locale.ROOT);
        String parameterType = parameter.getType().getName().toLowerCase(Locale.ROOT);
        assertFalse(parameterName.contains("oracle"), method + " must not accept an oracle parameter");
        assertFalse(parameterName.contains("check"), method + " must not accept a checks path");
        assertFalse(parameterType.contains("oracle"), method + " must not depend on checker types");
        assertFalse(parameterType.contains("packetoracle"), method + " must not depend on checker types");
      }
    }
    Method materialize = materializeMethod();
    assertTrue(
        Arrays.stream(materialize.getParameters())
            .map(Parameter::getName)
            .noneMatch(name -> name.equalsIgnoreCase("checks") || name.equalsIgnoreCase("oracle")),
        "materializer parameters must be snapshot/projection inputs only");
  }

  @Test
  void packetOracleCheckRejectsApacketWithAnUntrustedSnapshot(@TempDir Path packetDirectory)
      throws Exception {
    Path packet = packetDirectory.resolve("packet.json");
    String validPacket = registrationPacket(null);
    String untrustedPacket =
        validPacket.replace(
            "\"snapshotCommit\": \"" + SNAPSHOT_COMMIT + "\"",
            "\"snapshotCommit\": \"ad6cf886-not-the-approved-commit\"");
    assertValidJson(untrustedPacket);
    Method check = checkerMethod();
    Files.writeString(
        packet,
        untrustedPacket,
        StandardCharsets.UTF_8);
    Object result = check.invoke(null, packet, Path.of(REGISTRATION_ORACLE));
    assertEquals(Boolean.FALSE, result, "a packet from another snapshot must fail the oracle gate");
  }

  @Test
  void packetOracleCheckAcceptsAcompleteRegistrationPacket(@TempDir Path packetDirectory)
      throws Exception {
    Path packet = packetDirectory.resolve("registration-packet.json");
    String completePacket = registrationPacket(null);
    assertValidJson(completePacket);
    Method check = checkerMethod();
    Files.writeString(packet, completePacket, StandardCharsets.UTF_8);
    Object result = check.invoke(null, packet, Path.of(REGISTRATION_ORACLE));
    assertEquals(Boolean.TRUE, result, "a complete registration packet must pass its independent oracle");
  }

  @Test
  void packetOracleCheckAcceptsAcompleteFinancialPacket(@TempDir Path packetDirectory)
      throws Exception {
    Path packet = packetDirectory.resolve("financial-packet.json");
    String completePacket = financialPacket();
    assertValidJson(completePacket);
    Method check = checkerMethod();
    Files.writeString(packet, completePacket, StandardCharsets.UTF_8);
    Object result = check.invoke(null, packet, Path.of(FINANCIAL_ORACLE));
    assertEquals(Boolean.TRUE, result, "a complete financial packet must pass its independent oracle");
  }

  @ParameterizedTest(name = "one incomplete registration body: {0}")
  @ValueSource(strings = {"validateCaptcha", "registerUser", "checkLoginName"})
  void packetOracleCheckRejectsApacketWithOneIncompleteRegistrationBody(
      String truncatedMethod, @TempDir Path packetDirectory) throws Exception {
    Path packet = packetDirectory.resolve("packet.json");
    String completePacket = registrationPacket(null);
    String signatureOnlyPacket = registrationPacket(truncatedMethod);
    assertEquals(
        withoutSnippets(completePacket),
        withoutSnippets(signatureOnlyPacket),
        "the negative packet must differ from the valid packet only by complete-body extent");
    assertValidJson(signatureOnlyPacket);
    Method check = checkerMethod();
    Files.writeString(packet, signatureOnlyPacket, StandardCharsets.UTF_8);
    Object result = check.invoke(null, packet, Path.of(REGISTRATION_ORACLE));
    assertEquals(
        Boolean.FALSE,
        result,
        "a packet with only " + truncatedMethod + " incomplete must not pass the source-body gate");
  }

  @Test
  void checkerContractUsesPacketAndIndependentOraclePathsOnly() {
    Method check = checkerMethod();
    assertTrue(Modifier.isStatic(check.getModifiers()), "packet checking is an independent pure boundary");
    assertEquals(
        List.of(Path.class, Path.class),
        Arrays.asList(check.getParameterTypes()),
        "check(Path packet, Path oracle) keeps checker input separate from navigation input");
    assertTrue(
        Arrays.stream(check.getParameters())
            .map(Parameter::getName)
            .anyMatch(name -> name.equalsIgnoreCase("oracle")),
        "the checker must name its independent oracle input");
    assertTrue(
        Arrays.stream(check.getParameters())
            .map(Parameter::getName)
            .anyMatch(name -> name.equalsIgnoreCase("packet")),
        "the checker must name its persisted packet input");
    assertTrue(Files.isRegularFile(Path.of(REGISTRATION_ORACLE)), "registration oracle is a checker input");
    assertTrue(Files.isRegularFile(Path.of(FINANCIAL_ORACLE)), "financial oracle is a checker input");
  }

  @Test
  void independentOraclesMatchThePinnedGitObjects() throws Exception {
    List<OracleSourceExpectation> expectations =
        List.of(
            new OracleSourceExpectation(
                REGISTRATION_ORACLE,
                "jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java",
                "aa46f148b32818078a93e051253d58006fde2695",
                "afb1b350543031fa4b2f3c8a0570a75f738b471e6d6d54d982c74043e9f18c67"),
            new OracleSourceExpectation(
                REGISTRATION_ORACLE,
                "jshERP-boot/src/main/java/com/jsh/erp/service/UserService.java",
                "592be4a9a0795fda635358436e533f217df677d7",
                "95552b925cec2902502f2b1dcd6631164c5497eeb9f48c3104b25e7f553c343a"),
            new OracleSourceExpectation(
                FINANCIAL_ORACLE,
                "jshERP-boot/src/main/java/com/jsh/erp/controller/AccountHeadController.java",
                "9455500ffa7355368e8ad591246b4eb5a3a9749c",
                "0d0ef06b07e8b60c297e1005f239aac1d446b8e87c02cb5ec4bbcaafb444b520"),
            new OracleSourceExpectation(
                FINANCIAL_ORACLE,
                "jshERP-boot/src/main/java/com/jsh/erp/service/AccountHeadService.java",
                "68081dd4132a0d8358f884ccffa1d1a0acac755d",
                "def5bb314ee9185efff5978f73d3f236eb5b5171fc90139181ab82b3211ae1b4"),
            new OracleSourceExpectation(
                FINANCIAL_ORACLE,
                "jshERP-boot/src/main/java/com/jsh/erp/datasource/mappers/AccountHeadMapperEx.java",
                "be14215efe632791be9e87df168b0895a78d35c5",
                "31bc1092b847a69673b99727bd4081228d2cf4402cb3f4ef5f6a675c23bd3a7d"),
            new OracleSourceExpectation(
                FINANCIAL_ORACLE,
                "jshERP-boot/src/main/resources/mapper_xml/AccountHeadMapperEx.xml",
                "d2c4a090bb3c7fd1d70e4adddad7004781995930",
                "cf7f17843da43f8648616dbe9ebb166230bee54440b09014228bd60ceb517229"));
    for (OracleSourceExpectation expectation : expectations) {
      String oracle = Files.readString(Path.of(expectation.oraclePath()), StandardCharsets.UTF_8);
      assertTrue(oracle.contains("\"exampleKind\": \"INDEPENDENT_CHECKER_INPUT_NOT_NAVIGATOR_INPUT\""));
      assertTrue(oracle.contains("\"snapshotCommit\": \"" + SNAPSHOT_COMMIT + "\""));
      assertTrue(oracle.contains("\"path\": \"" + expectation.sourcePath() + "\""));
      assertTrue(oracle.contains("\"gitBlob\": \"" + expectation.gitBlob() + "\""));
      assertTrue(oracle.contains("\"sha256\": \"" + expectation.sha256() + "\""));
      assertEquals(
          expectation.gitBlob(),
          new String(
                  gitObject("rev-parse", SNAPSHOT_COMMIT + ":" + expectation.sourcePath()),
                  StandardCharsets.UTF_8)
              .trim());
      assertEquals(
          expectation.sha256(),
          sha256(gitObject("cat-file", "blob", SNAPSHOT_COMMIT + ":" + expectation.sourcePath())));
    }
    assertOracleLine(
        FINANCIAL_ORACLE,
        "jshERP-boot/src/main/java/com/jsh/erp/controller/AccountHeadController.java",
        "mapping",
        181,
        "@GetMapping(value = \"/getFinancialBillNoByBillId\")");
    assertOracleLine(
        FINANCIAL_ORACLE,
        "jshERP-boot/src/main/java/com/jsh/erp/controller/AccountHeadController.java",
        "serviceCall",
        187,
        "accountHeadService.getFinancialBillNoByBillId(billId)");
    assertOracleLine(
        FINANCIAL_ORACLE,
        "jshERP-boot/src/main/java/com/jsh/erp/controller/AccountHeadController.java",
        "catch",
        190,
        "} catch(Exception e){");
    assertOracleLine(
        FINANCIAL_ORACLE,
        "jshERP-boot/src/main/java/com/jsh/erp/controller/AccountHeadController.java",
        "return",
        195,
        "return res;");
    assertOracleRange(
        FINANCIAL_ORACLE,
        "jshERP-boot/src/main/java/com/jsh/erp/controller/AccountHeadController.java",
        181,
        196);
    assertOracleMethodRange(FINANCIAL_ORACLE, 442, 444);
    assertOracleMethodRange(FINANCIAL_ORACLE, 44, 45);
    assertOracleExpectedRange(FINANCIAL_ORACLE, 150, 155);
    assertOracleLine(
        FINANCIAL_ORACLE,
        "jshERP-boot/src/main/java/com/jsh/erp/service/AccountHeadService.java",
        "serviceMethodStart",
        442,
        "getFinancialBillNoByBillId(Long billId)");
    assertOracleLine(
        FINANCIAL_ORACLE,
        "jshERP-boot/src/main/java/com/jsh/erp/service/AccountHeadService.java",
        "serviceMethodCall",
        443,
        "accountHeadMapperEx.getFinancialBillNoByBillId(billId)");
    assertOracleLine(
        FINANCIAL_ORACLE,
        "jshERP-boot/src/main/java/com/jsh/erp/datasource/mappers/AccountHeadMapperEx.java",
        "mapperMethodStart",
        44,
        "getFinancialBillNoByBillId(");
    assertOracleLine(
        FINANCIAL_ORACLE,
        "jshERP-boot/src/main/java/com/jsh/erp/datasource/mappers/AccountHeadMapperEx.java",
        "mapperMethodParameter",
        45,
        "@Param(\"billId\") Long billId");
    assertOracleLine(
        FINANCIAL_ORACLE,
        "jshERP-boot/src/main/resources/mapper_xml/AccountHeadMapperEx.xml",
        "xmlSelect",
        150,
        "<select id=\"getFinancialBillNoByBillId\"");
    assertOracleLine(
        FINANCIAL_ORACLE,
        "jshERP-boot/src/main/resources/mapper_xml/AccountHeadMapperEx.xml",
        "xmlWhere",
        153,
        "where ai.bill_id=#{billId}");
    assertOracleLine(
        REGISTRATION_ORACLE,
        "jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java",
        "serviceImport",
        16,
        "com.jsh.erp.service.*");
    assertOracleLine(
        REGISTRATION_ORACLE,
        "jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java",
        "classMapping",
        39,
        "@RequestMapping(value = \"/user\")");
    assertOracleLine(
        REGISTRATION_ORACLE,
        "jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java",
        "validateCaptchaCall",
        363,
        "userService.validateCaptcha(ue.getCode(), ue.getUuid())");
    assertOracleLine(
        REGISTRATION_ORACLE,
        "jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java",
        "checkLoginNameCall",
        364,
        "userService.checkLoginName(ue)");
    assertOracleLine(
        REGISTRATION_ORACLE,
        "jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java",
        "registerUserCall",
        365,
        "userService.registerUser(ue,manageRoleId,request)");
    assertOracleRange(
        REGISTRATION_ORACLE,
        "jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java",
        357,
        367);
  }

  private static void assertOracleLine(
      String oraclePath, String sourcePath, String key, int line, String marker)
      throws IOException {
    String oracle = Files.readString(Path.of(oraclePath), StandardCharsets.UTF_8);
    assertTrue(oracle.contains("\"" + key + "\": " + line), oraclePath + " missing " + key);
    assertTrue(sourceLine(sourcePath, line).contains(marker), sourcePath + ":" + line);
  }

  private static void assertOracleRange(String oraclePath, String sourcePath, int startLine, int endLine)
      throws IOException {
    String oracle = Files.readString(Path.of(oraclePath), StandardCharsets.UTF_8);
    assertTrue(
        oracle.contains(
            "\"entryRange\": {\"startLine\": " + startLine + ", \"endLine\": " + endLine + "}"),
        oraclePath + " missing entry range for " + sourcePath);
    assertTrue(sourceLine(sourcePath, startLine) != null);
    assertTrue(sourceLine(sourcePath, endLine) != null);
  }

  private static void assertOracleMethodRange(String oraclePath, int startLine, int endLine)
      throws IOException {
    String oracle = Files.readString(Path.of(oraclePath), StandardCharsets.UTF_8);
    assertTrue(
        oracle.contains(
            "\"range\": {\"startLine\": " + startLine + ", \"endLine\": " + endLine + "}"),
        oraclePath + " missing method range");
  }

  private static void assertOracleExpectedRange(String oraclePath, int startLine, int endLine)
      throws IOException {
    String oracle = Files.readString(Path.of(oraclePath), StandardCharsets.UTF_8);
    assertTrue(
        oracle.contains(
            "\"expectedRange\": {\"startLine\": " + startLine + ", \"endLine\": " + endLine + "}"),
        oraclePath + " missing optional XML range");
  }

  private static String sourceLine(String sourcePath, int line) throws IOException {
    String source =
        new String(gitObject("cat-file", "blob", SNAPSHOT_COMMIT + ":" + sourcePath), StandardCharsets.UTF_8);
    String[] lines = source.split("\\R", -1);
    assertTrue(line >= 1 && line <= lines.length, sourcePath + " has no line " + line);
    return lines[line - 1];
  }

  private static void assertProjectionManifest(
      Path manifest, List<String> expectedSources, Path projection) throws Exception {
    JsonNode root = new ObjectMapper().readTree(Files.readString(manifest, StandardCharsets.UTF_8));
    assertEquals(SNAPSHOT_COMMIT, root.path("snapshotCommit").asText());
    assertEquals("projection/src", root.path("projectionRoot").asText());
    assertEquals(expectedSources.size(), root.path("sourceCount").asInt());
    JsonNode entries = root.path("sources");
    assertTrue(entries.isArray(), "projection manifest must contain a sources array");
    assertEquals(expectedSources.size(), entries.size(), "manifest source count must match the Git selection");

    Map<String, JsonNode> entriesByOriginalPath = new TreeMap<>();
    for (JsonNode entry : entries) {
      assertTrue(entry.isObject(), "each manifest source entry must be an object");
      for (String field : List.of("originalPath", "projectedPath", "gitBlob", "sha256")) {
        assertTrue(entry.hasNonNull(field), "manifest source entry missing " + field);
        assertTrue(!entry.path(field).asText().isBlank(), "manifest source entry has blank " + field);
      }
      String originalPath = entry.path("originalPath").asText();
      assertTrue(
          entriesByOriginalPath.put(originalPath, entry) == null,
          "manifest must not duplicate originalPath " + originalPath);
    }
    assertEquals(
        expectedSources,
        entriesByOriginalPath.keySet().stream().sorted().toList(),
        "manifest originalPath set must exactly match the frozen Git Java selection");

    for (String originalPath : expectedSources) {
      JsonNode entry = entriesByOriginalPath.get(originalPath);
      String expectedProjectedPath = projectedPath(originalPath);
      byte[] sourceBytes = gitObject("cat-file", "blob", SNAPSHOT_COMMIT + ":" + originalPath);
      String expectedBlob =
          new String(
                  gitObject("rev-parse", SNAPSHOT_COMMIT + ":" + originalPath), StandardCharsets.UTF_8)
              .trim();
      String expectedSha256 = sha256(sourceBytes);
      assertEquals(expectedProjectedPath, entry.path("projectedPath").asText(), originalPath);
      assertEquals(expectedBlob, entry.path("gitBlob").asText(), originalPath);
      assertEquals(expectedSha256, entry.path("sha256").asText(), originalPath);
      Path projectedFile = projection.resolve(expectedProjectedPath);
      assertTrue(Files.isRegularFile(projectedFile), "manifest projectedPath is missing: " + originalPath);
      assertEquals(expectedSha256, sha256(Files.readAllBytes(projectedFile)), originalPath);
    }
  }

  private static String projectedPath(String sourcePath) {
    return sourcePath.substring(sourcePath.indexOf("/src/main/java/") + "/src/main/java/".length());
  }

  private static String registrationPacket(String truncatedMethod) throws IOException {
    String controllerPath =
        "jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java";
    String servicePath = "jshERP-boot/src/main/java/com/jsh/erp/service/UserService.java";
    return "{\n"
        + "  \"packetVersion\": \"jdtls-source-navigation-feasibility-packet-v1\",\n"
        + "  \"snapshotCommit\": "
        + json(SNAPSHOT_COMMIT)
        + ",\n"
        + "  \"entry\": {\"method\": \"POST\", \"path\": \"/user/registerUser\", \"file\": "
        + json(controllerPath)
        + ", \"range\": {\"startLine\": 357, \"endLine\": 367}},\n"
        + "  \"methods\": [\n"
        + methodJson(
            "com.jsh.erp.controller.UserController#registerUser",
            controllerPath,
            357,
            367,
            true,
            357)
        + ",\n"
        + methodJson(
            "com.jsh.erp.service.UserService#validateCaptcha",
            servicePath,
            297,
            319,
            !"validateCaptcha".equals(truncatedMethod),
            297)
        + ",\n"
        + methodJson(
            "com.jsh.erp.service.UserService#registerUser",
            servicePath,
            607,
            661,
            !"registerUser".equals(truncatedMethod),
            608)
        + ",\n"
        + methodJson(
            "com.jsh.erp.service.UserService#checkLoginName",
            servicePath,
            776,
            805,
            !"checkLoginName".equals(truncatedMethod),
            776)
        + "\n  ],\n"
        + "  \"calls\": [\n"
        + callJson(
            controllerPath,
            363,
            "userService.validateCaptcha(ue.getCode(), ue.getUuid())",
            List.of("ue.getCode()", "ue.getUuid()"))
        + ",\n"
        + callJson(controllerPath, 364, "userService.checkLoginName(ue)", List.of("ue"))
        + ",\n"
        + callJson(
            controllerPath,
            365,
            "userService.registerUser(ue,manageRoleId,request)",
            List.of("ue", "manageRoleId", "request"))
        + "\n  ],\n"
        + "  \"diagnostics\": [],\n"
        + "  \"limits\": {\"maxMethods\": 30, \"maxDepth\": 4, \"timeoutSeconds\": 900}\n"
        + "}\n";
  }

  private static String financialPacket() throws IOException {
    String controllerPath =
        "jshERP-boot/src/main/java/com/jsh/erp/controller/AccountHeadController.java";
    String servicePath = "jshERP-boot/src/main/java/com/jsh/erp/service/AccountHeadService.java";
    String mapperPath = "jshERP-boot/src/main/java/com/jsh/erp/datasource/mappers/AccountHeadMapperEx.java";
    String xmlPath = "jshERP-boot/src/main/resources/mapper_xml/AccountHeadMapperEx.xml";
    return "{\n"
        + "  \"packetVersion\": \"jdtls-source-navigation-feasibility-packet-v1\",\n"
        + "  \"snapshotCommit\": "
        + json(SNAPSHOT_COMMIT)
        + ",\n"
        + "  \"entry\": {\"method\": \"GET\", \"path\": \"/accountHead/getFinancialBillNoByBillId\", \"file\": "
        + json(controllerPath)
        + ", \"range\": {\"startLine\": 181, \"endLine\": 196}},\n"
        + "  \"methods\": [\n"
        + methodJson(controllerPath, controllerPath, 181, 196, true, 181)
        + ",\n"
        + methodJson(
            "com.jsh.erp.service.AccountHeadService#getFinancialBillNoByBillId(java.lang.Long)",
            servicePath,
            442,
            444,
            true,
            442)
        + ",\n"
        + methodJson(
            "com.jsh.erp.datasource.mappers.AccountHeadMapperEx#getFinancialBillNoByBillId(java.lang.Long)",
            mapperPath,
            44,
            45,
            true,
            44)
        + ",\n"
        + methodJson("mapper_xml.AccountHeadMapperEx#getFinancialBillNoByBillId", xmlPath, 150, 155, true, 150)
        + "\n  ],\n"
        + "  \"calls\": [\n"
        + callJson(
            controllerPath,
            187,
            "accountHeadService.getFinancialBillNoByBillId(billId)",
            List.of("billId"))
        + ",\n"
        + callJson(
            servicePath,
            443,
            "accountHeadMapperEx.getFinancialBillNoByBillId(billId)",
            List.of("billId"))
        + "\n  ],\n"
        + "  \"diagnostics\": [],\n"
        + "  \"limits\": {\"maxMethods\": 30, \"maxDepth\": 4, \"timeoutSeconds\": 900}\n"
        + "}\n";
  }

  private static String methodJson(
      String symbol,
      String path,
      int startLine,
      int endLine,
      boolean completeBody,
      int signatureLine)
      throws IOException {
    int snippetStart = completeBody ? startLine : signatureLine;
    return "    {\"symbol\": "
        + json(symbol)
        + ", \"path\": "
        + json(path)
        + ", \"range\": {\"startLine\": "
        + startLine
        + ", \"endLine\": "
        + endLine
        + "}, \"snippet\": "
        + json(sourceSnippet(path, snippetStart, completeBody ? endLine : signatureLine))
        + "}";
  }

  private static String callJson(String path, int line, String text, List<String> actualArguments) {
    return "    {\"path\": "
        + json(path)
        + ", \"line\": "
        + line
        + ", \"text\": "
        + json(text)
        + ", \"actualArguments\": ["
        + actualArguments.stream().map(NavigationProbeTest::json).collect(java.util.stream.Collectors.joining(", "))
        + "], \"resolution\": \"UNIQUE\", \"disposition\": \"EXPANDED\"}";
  }

  private static String sourceSnippet(String path, int startLine, int endLine) throws IOException {
    String source =
        new String(gitObject("cat-file", "blob", SNAPSHOT_COMMIT + ":" + path), StandardCharsets.UTF_8);
    String[] lines = source.split("\\R", -1);
    assertTrue(startLine >= 1 && endLine >= startLine && endLine <= lines.length, "invalid frozen source range");
    return Arrays.stream(lines, startLine - 1, endLine).collect(java.util.stream.Collectors.joining("\n"));
  }

  private static String json(String value) {
    return "\""
        + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n")
        + "\"";
  }

  private static String withoutSnippets(String packet) throws IOException {
    JsonNode root = new ObjectMapper().readTree(packet);
    removeSnippets(root);
    return root.toString();
  }

  private static void removeSnippets(JsonNode node) {
    if (node.isObject()) {
      ObjectNode object = (ObjectNode) node;
      object.remove("snippet");
      object.elements().forEachRemaining(NavigationProbeTest::removeSnippets);
    } else if (node.isArray()) {
      node.elements().forEachRemaining(NavigationProbeTest::removeSnippets);
    }
  }

  private static void assertValidJson(String packet) throws IOException {
    new ObjectMapper().readTree(packet);
  }

  private static Method materializeMethod() {
    Class<?> probe = requiredType(PROBE_CLASS);
    try {
      return probe.getMethod("materialize", Path.class, String.class, Path.class);
    } catch (NoSuchMethodException missing) {
      fail("NAVIGATION_PROBE_MATERIALIZE_SEAM_MISSING", missing);
      throw new AssertionError("unreachable");
    }
  }

  private static Method probeMethod() {
    Class<?> probe = requiredType(PROBE_CLASS);
    try {
      return probe.getMethod(
          "probe",
          String.class,
          Path.class,
          String.class,
          int.class,
          int.class,
          int.class,
          int.class,
          Duration.class);
    } catch (NoSuchMethodException missing) {
      fail("NAVIGATION_PROBE_INPUT_ONLY_SEAM_MISSING", missing);
      throw new AssertionError("unreachable");
    }
  }

  private static Method checkerMethod() {
    Class<?> checker = requiredType(CHECKER_CLASS);
    try {
      return checker.getMethod("check", Path.class, Path.class);
    } catch (NoSuchMethodException missing) {
      fail("PACKET_ORACLE_CHECK_SEAM_MISSING", missing);
      throw new AssertionError("unreachable");
    }
  }

  private static Class<?> requiredType(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException missing) {
      fail(name + " production seam is not implemented", missing);
      throw new AssertionError("unreachable");
    }
  }

  private static List<String> expectedMainJavaPaths() throws IOException {
    return Arrays.stream(
            new String(gitObject("ls-tree", "-r", "--name-only", SNAPSHOT_COMMIT), StandardCharsets.UTF_8)
                .split("\\R"))
        .filter(path -> path.matches(".+/src/main/java/.+\\.java"))
        .sorted()
        .toList();
  }

  private static List<String> archiveEntries(Path archive) throws IOException {
    ProcessBuilder processBuilder =
        new ProcessBuilder("tar", "-tf", archive.toString()).redirectErrorStream(true);
    Process process = processBuilder.start();
    byte[] output = process.getInputStream().readAllBytes();
    try {
      int exit = process.waitFor();
      assertEquals(0, exit, "frozen archive is not a readable tar archive");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted while listing frozen archive", interrupted);
    }
    return Arrays.stream(new String(output, StandardCharsets.UTF_8).split("\\R"))
        .filter(path -> !path.isBlank())
        .toList();
  }

  private static void assertNoProhibitedProjectionInputs(Path projection) throws IOException {
    List<String> prohibitedNames =
        List.of(
            "pom.xml",
            "build.gradle",
            "build.gradle.kts",
            "settings.gradle",
            "settings.gradle.kts",
            ".project",
            ".classpath",
            ".factorypath",
            ".settings",
            "target",
            "bin",
            ".apt_generated",
            "generated-sources",
            "annotation-processors");
    try (Stream<Path> files = Files.walk(projection)) {
      files
          .map(projection::relativize)
          .forEach(
              relative -> {
                for (Path component : relative) {
                  String name = component.toString();
                  assertFalse(
                      prohibitedNames.contains(name)
                          || name.endsWith(".factorypath")
                          || name.endsWith(".classpath"),
                      "prohibited build/Eclipse/AP input projected: " + relative);
                }
              });
    }
  }

  private static byte[] gitObject(String... arguments) throws IOException {
    List<String> command = new ArrayList<>();
    command.add("git");
    command.add("-C");
    command.add(SNAPSHOT_REPOSITORY.toString());
    command.addAll(Arrays.asList(arguments));
    ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
    processBuilder.environment().put("GIT_NO_LAZY_FETCH", "1");
    Process process = processBuilder.start();
    byte[] output = process.getInputStream().readAllBytes();
    try {
      int exit = process.waitFor();
      assertEquals(0, exit, "Git object read failed: " + String.join(" ", command));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted while reading Git object", interrupted);
    }
    return output;
  }

  private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
    StringBuilder hex = new StringBuilder(digest.length * 2);
    for (byte value : digest) {
      hex.append(String.format(Locale.ROOT, "%02x", value));
    }
    return hex.toString();
  }

  private record OracleSourceExpectation(
      String oraclePath, String sourcePath, String gitBlob, String sha256) {}
}
