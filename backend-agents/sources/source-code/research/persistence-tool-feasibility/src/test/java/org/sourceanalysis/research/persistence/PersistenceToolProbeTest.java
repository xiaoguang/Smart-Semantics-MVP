package org.sourceanalysis.research.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * RED contract for the independent MyBatis/JSqlParser feasibility probe.
 *
 * <p>The test intentionally uses reflection: the research harness does not yet have a production
 * probe. The reflective names are the small API agreed with the implementer and keep this test
 * source compilable while the implementation is absent.
 */
class PersistenceToolProbeTest {

  private static final String PROBE =
      "org.sourceanalysis.research.persistence.PersistenceToolProbe";
  private static final String PROBE_PACKAGE = "org.sourceanalysis.research.persistence.";
  private static final String NAMESPACE = "sample.alpha.RecordMapper";
  private static final String MIXED_PATH = "src/main/resources/mappers/record.xml";
  private static final String VARIANT_PATH = "src/main/resources/mappers/record-variant.xml";

  @Test
  void preservesRawMixedXmlAndDynamicConditionIncludeProjection() throws Exception {
    Object result = analyze(List.of(resource(MIXED_PATH, MIXED_XML)), NAMESPACE);

    assertEquals(MIXED_XML, rawSource(result, MIXED_PATH));

    Object statement = statement(result, "findByKey", null, MIXED_PATH);
    assertEquals("findByKey", value(statement, "id"));
    String rawStatement = (String) value(statement, "rawSource");
    assertTrue(rawStatement.contains("<include refid=\"baseColumns\"/>"));
    assertTrue(rawStatement.contains("<if test=\"key != null and key != ''\">"));
    assertTrue(rawStatement.contains("<![CDATA[AND a.note <> '' ]]>"));
    assertTrue(rawStatement.indexOf("<include") < rawStatement.indexOf("FROM alpha_record"));

    @SuppressWarnings("unchecked")
    List<String> dependencies = (List<String>) value(statement, "dependencies");
    assertEquals(List.of("baseColumns", "dynamicFilter"), dependencies);

    @SuppressWarnings("unchecked")
    List<Object> conditions = (List<Object>) value(statement, "conditions");
    assertTrue(
        conditions.stream()
            .map(condition -> String.valueOf(value(condition, "expression")))
            .collect(Collectors.toSet())
            .containsAll(
                Set.of(
                    "key != null and key != ''",
                    "mode == 'one'",
                    "keys")));
    assertTrue(
        conditions.stream()
            .map(condition -> String.valueOf(value(condition, "tag")))
            .collect(Collectors.toSet())
            .containsAll(Set.of("if", "when", "foreach")));
  }

  @Test
  void retainsJoinWhereAndAggregateStructureWithAnExplicitPartialStatus() throws Exception {
    Object result = analyze(List.of(resource(MIXED_PATH, MIXED_XML)), NAMESPACE);

    Object sql = sqlAnalysis(result, "findByKey", MIXED_PATH);
    String status = String.valueOf(value(sql, "status"));
    assertTrue(Set.of("PARSED", "PARTIAL").contains(status), "status=" + status);
    String structure = String.valueOf(value(sql, "structure")).toLowerCase(Locale.ROOT);
    assertTrue(structure.contains("sum"), "aggregate must remain visible: " + structure);
    assertTrue(structure.contains("left") && structure.contains("join"),
        "join must remain distinct from the filter: " + structure);
    assertTrue(structure.contains("on"), "join ON structure must remain visible: " + structure);
    assertTrue(structure.contains("where"), "WHERE structure must remain visible: " + structure);
    assertFalse(structure.contains("purchase"), "the neutral fixture must not encode business vocabulary");
  }

  @Test
  void retainsMultipleXmlStatementCandidatesInsteadOfOverwritingVariants() throws Exception {
    Object result =
        analyze(
            List.of(resource(MIXED_PATH, MIXED_XML), resource(VARIANT_PATH, VARIANT_XML)), NAMESPACE);

    List<Object> candidates = statements(result, "findByKey");
    assertEquals(2, candidates.size());
    assertEquals(
        Set.of(MIXED_PATH, VARIANT_PATH),
        candidates.stream()
            .map(statement -> String.valueOf(value(statement, "resourcePath")))
            .collect(Collectors.toSet()));
    Object primary =
        candidates.stream()
            .filter(statement -> MIXED_PATH.equals(value(statement, "resourcePath")))
            .findFirst()
            .orElseThrow();
    Object alternate =
        candidates.stream()
            .filter(statement -> VARIANT_PATH.equals(value(statement, "resourcePath")))
            .findFirst()
            .orElseThrow();
    assertNull(value(primary, "databaseId"));
    assertEquals("alt", value(alternate, "databaseId"));
    assertTrue(
        candidates.stream()
            .map(statement -> String.valueOf(value(statement, "rawSource")))
            .anyMatch(raw -> raw.contains("variant_marker")));
  }

  @Test
  void preservesInsertSelectiveColumnAndValueCorrespondence() throws Exception {
    Object result = analyze(List.of(resource(MIXED_PATH, MIXED_XML)), NAMESPACE);
    Object statement = statement(result, "insertSelective", null, MIXED_PATH);

    String raw = (String) value(statement, "rawSource");
    assertTrue(raw.contains("<if test=\"record.id != null\">record_id,</if>"));
    assertTrue(raw.contains("<if test=\"record.label != null\">record_label,</if>"));
    assertTrue(raw.contains("#{record.id}"));
    assertTrue(raw.contains("#{record.label}"));

    @SuppressWarnings("unchecked")
    List<Object> conditions = (List<Object>) value(statement, "conditions");
    Set<String> expressions =
        conditions.stream()
            .map(condition -> String.valueOf(value(condition, "expression")))
            .collect(Collectors.toSet());
    assertTrue(expressions.contains("record.id != null"));
    assertTrue(expressions.contains("record.label != null"));
  }

  @Test
  void retainsUnsupportedDynamicSqlAndReportsItsStatus() throws Exception {
    Object result = analyze(List.of(resource(MIXED_PATH, MIXED_XML)), NAMESPACE);
    Object statement = statement(result, "dynamicTarget", null, MIXED_PATH);

    Object sql = sqlAnalysis(result, "dynamicTarget", MIXED_PATH);
    String status = String.valueOf(value(sql, "status"));
    assertTrue(Set.of("PARTIAL", "UNSUPPORTED").contains(status), "status=" + status);
    String raw = (String) value(statement, "rawSource");
    assertTrue(raw.contains("${tableName}"));
    assertTrue(raw.contains("${columnName}"));
    assertTrue(raw.contains("<foreach collection=\"keys\""));
  }

  @Test
  void doesNotEvaluateOgnlOrInvokeCustomerCode() throws Exception {
    PersistenceToolProbeInvocationSentinel.reset();

    Object result = analyze(List.of(resource(MIXED_PATH, OGNL_XML)), NAMESPACE);
    Object statement = statement(result, "safeRead", null, MIXED_PATH);

    assertEquals(0, PersistenceToolProbeInvocationSentinel.invocations());
    @SuppressWarnings("unchecked")
    List<Object> conditions = (List<Object>) value(statement, "conditions");
    assertTrue(
        conditions.stream()
            .map(condition -> String.valueOf(value(condition, "expression")))
            .anyMatch(expression -> expression.contains("PersistenceToolProbeInvocationSentinel")));
    assertTrue(((String) value(statement, "rawSource")).contains("<bind"));
  }

  @Test
  void rejectsExternalEntityWithoutReadingOutsideTheFrozenResource(@TempDir Path temporaryDirectory)
      throws Exception {
    Path secret = temporaryDirectory.resolve("external-secret.txt");
    Files.writeString(secret, "EXTERNAL_ENTITY_SECRET");
    String xxe = xxeXml(secret.toUri());

    Object result = analyze(List.of(resource("src/main/resources/mappers/xxe.xml", xxe)), NAMESPACE);
    assertEquals(xxe, rawSource(result, "src/main/resources/mappers/xxe.xml"));

    String serialized = String.valueOf(result);
    assertFalse(serialized.contains("EXTERNAL_ENTITY_SECRET"));
    assertTrue(serialized.contains("xxe") || serialized.contains("external"));
    assertTrue(
        diagnostics(result).stream()
            .map(String::valueOf)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(
                value ->
                    value.contains("security") || value.contains("external") || value.contains("xxe")));
    assertTrue(
        statements(result, "externalRead").isEmpty(),
        "an unsafe XML resource must not yield a parsed statement");
  }

  @Test
  void bindsMapperDescriptorToNamespaceAndRetainsParameterAliases() throws Exception {
    Object result = analyze(List.of(resource(MIXED_PATH, MIXED_XML)), NAMESPACE);
    List<Object> bindings = bindings(result);
    assertTrue(
        bindings.stream()
            .map(String::valueOf)
            .anyMatch(
                binding ->
                    binding.contains(NAMESPACE)
                        && binding.contains("method:find-by-key")
                        && binding.contains("findByKey")
                        && binding.contains("key")));
    assertFalse(
        bindings.stream().map(String::valueOf).anyMatch(binding -> binding.contains("notPresent")));
    assertFalse(
        bindings.stream().map(String::valueOf).anyMatch(binding -> binding.contains("other.Mapper")));
  }

  @Test
  @Timeout(2)
  void reportsUnresolvedAndCyclicIncludesWithoutDroppingTheOriginalStatements() throws Exception {
    String unresolvedAndCyclicXml =
        """
        <mapper namespace="sample.alpha.RecordMapper">
          <sql id="cycleA"><include refid="cycleB"/></sql>
          <sql id="cycleB"><include refid="cycleA"/></sql>
          <select id="unresolved">SELECT <include refid="missingFragment"/></select>
        </mapper>
        """;
    Object result =
        analyze(
            List.of(resource("src/main/resources/mappers/unresolved.xml", unresolvedAndCyclicXml)),
            NAMESPACE);

    Object unresolved =
        statement(result, "unresolved", null, "src/main/resources/mappers/unresolved.xml");
    assertTrue(((String) value(unresolved, "rawSource")).contains("missingFragment"));
    String diagnosticsText =
        diagnostics(result).stream().map(String::valueOf).collect(Collectors.joining("\n"));
    assertTrue(diagnosticsText.contains("missingFragment"));
    assertTrue(diagnosticsText.contains("cycleA") || diagnosticsText.contains("cycleB"));
  }

  @Test
  void expandsIncludedDynamicContentIntoObservationsAndDoesNotClaimAStaticParse() throws Exception {
    String includeOnlyXml =
        """
        <mapper namespace="sample.alpha.RecordMapper">
          <sql id="dynamicFragment">
            <if test="flag != null">AND a.flag = #{flag}</if>
          </sql>
          <select id="includeOnly">
            SELECT a.id FROM alpha_record a <include refid="dynamicFragment"/>
          </select>
        </mapper>
        """;
    Object result =
        analyze(
            List.of(resource("src/main/resources/mappers/include-only.xml", includeOnlyXml)),
            NAMESPACE);

    Object statement =
        statement(result, "includeOnly", null, "src/main/resources/mappers/include-only.xml");
    assertEquals(List.of("dynamicFragment"), value(statement, "dependencies"));
    assertTrue(String.valueOf(value(statement, "expandedWorkCopy")).contains("#{flag}"));

    @SuppressWarnings("unchecked")
    List<Object> conditions = (List<Object>) value(statement, "conditions");
    assertTrue(
        conditions.stream()
            .map(condition -> String.valueOf(value(condition, "expression")))
            .anyMatch("flag != null"::equals));

    Object binding =
        bindings(result).stream()
            .filter(candidate -> String.valueOf(candidate).contains("method:include-only"))
            .findFirst()
            .orElseThrow();
    @SuppressWarnings("unchecked")
    List<Object> parameterBindings = (List<Object>) value(binding, "parameterBindings");
    assertTrue(
        parameterBindings.stream()
            .map(parameterBinding -> String.valueOf(value(parameterBinding, "xmlExpressions")))
            .anyMatch(expressions -> expressions.contains("flag")));

    Object sql = sqlAnalysis(result, "includeOnly", "src/main/resources/mappers/include-only.xml");
    assertFalse("PARSED".equals(String.valueOf(value(sql, "status"))));
  }

  @Test
  @Timeout(2)
  void rejectsExternalParameterEntitiesAndXIncludeWithoutEmittingExternalMaterial(
      @TempDir Path temporaryDirectory) throws Exception {
    Path secret = temporaryDirectory.resolve("non-frozen-secret.txt");
    Files.writeString(secret, "NON_FROZEN_SECRET");
    String parameterEntityXml = parameterEntityXml(secret.toUri());
    String xincludeXml =
        """
        <mapper xmlns:xi="http://www.w3.org/2001/XInclude" namespace="sample.alpha.RecordMapper">
          <xi:include href="%s" parse="xml"/>
          <select id="xincludeRead">SELECT 1</select>
        </mapper>
        """.formatted(secret.toUri());
    List<Object> resources =
        List.of(
            resource("src/main/resources/mappers/parameter-entity.xml", parameterEntityXml),
            resource("src/main/resources/mappers/xinclude.xml", xincludeXml));

    Object result = analyze(resources, NAMESPACE);
    assertEquals("REJECTED", resourceStatus(result, "src/main/resources/mappers/parameter-entity.xml"));
    assertEquals("REJECTED", resourceStatus(result, "src/main/resources/mappers/xinclude.xml"));
    assertFalse(String.valueOf(result).contains("NON_FROZEN_SECRET"));
    assertTrue(statements(result, "externalRead").isEmpty());
    assertTrue(statements(result, "xincludeRead").isEmpty());

    String diagnosticsText =
        diagnostics(result).stream().map(String::valueOf).collect(Collectors.joining("\n"));
    assertTrue(
        diagnosticsText.toLowerCase(Locale.ROOT).contains("external")
            || diagnosticsText.toLowerCase(Locale.ROOT).contains("security"));
    assertTrue(diagnosticsText.toLowerCase(Locale.ROOT).contains("xinclude"));
  }

  @Test
  void preservesRepeatedWhitespaceInsideStaticSqlStringLiterals() throws Exception {
    String whitespaceXml =
        """
        <mapper namespace="sample.alpha.RecordMapper">
          <select id="literalSpacing">SELECT 'a  b' AS label FROM neutral_table</select>
        </mapper>
        """;
    Object result =
        analyze(
            List.of(resource("src/main/resources/mappers/whitespace.xml", whitespaceXml)),
            NAMESPACE);

    Object sql = sqlAnalysis(result, "literalSpacing", "src/main/resources/mappers/whitespace.xml");
    assertEquals(
        "SELECT 'a  b' AS label FROM neutral_table",
        value(sql, "analysisCopy"));
  }

  @Test
  void retainsUnionSqlAndReportsBoundedStatusWithoutCrashingTheBatch() throws Exception {
    String unionXml =
        """
        <mapper namespace="sample.alpha.RecordMapper">
          <select id="unionRead">SELECT 1 AS value UNION SELECT 2 AS value</select>
        </mapper>
        """;

    Object result =
        assertDoesNotThrow(
            () ->
                analyze(
                    List.of(resource("src/main/resources/mappers/union.xml", unionXml)),
                    NAMESPACE));
    Object statement = statement(result, "unionRead", null, "src/main/resources/mappers/union.xml");
    assertTrue(((String) value(statement, "rawSource")).contains("UNION"));

    Object sql = sqlAnalysis(result, "unionRead", "src/main/resources/mappers/union.xml");
    String status = String.valueOf(value(sql, "status"));
    assertTrue(Set.of("PARTIAL", "UNSUPPORTED").contains(status), "status=" + status);
  }

  @Test
  void preservesJoinWithoutOnExpressionWithoutCrashingTheBatch() throws Exception {
    String crossJoinXml =
        """
        <mapper namespace="sample.alpha.RecordMapper">
          <select id="crossJoinRead">SELECT a.id FROM alpha_record a CROSS JOIN beta_record b</select>
        </mapper>
        """;
    Object result =
        assertDoesNotThrow(
            () ->
                analyze(
                    List.of(resource("src/main/resources/mappers/cross-join.xml", crossJoinXml)),
                    NAMESPACE));
    Object statement =
        statement(result, "crossJoinRead", null, "src/main/resources/mappers/cross-join.xml");
    assertTrue(((String) value(statement, "rawSource")).contains("CROSS JOIN beta_record b"));

    Object sql = sqlAnalysis(result, "crossJoinRead", "src/main/resources/mappers/cross-join.xml");
    assertEquals("PARSED", value(sql, "status"));
    Object structure = value(sql, "structure");
    @SuppressWarnings("unchecked")
    List<Object> joins = (List<Object>) value(structure, "joins");
    assertEquals(1, joins.size());
    Object join = joins.get(0);
    assertTrue(String.valueOf(value(join, "joinKind")).contains("JOIN"));
    assertEquals("beta_record b", value(join, "right"));
    assertNull(value(join, "on"));
  }

  private static Object analyze(List<Object> resources, String namespace) throws Exception {
    Class<?> probeType = probeType();
    Class<?> resourceType = Class.forName(PROBE_PACKAGE + "MapperResource");
    Class<?> methodType = Class.forName(PROBE_PACKAGE + "MapperMethodDescriptor");
    Class<?> parameterType = Class.forName(PROBE_PACKAGE + "MapperParameter");
    List<Object> mapperMethods = new ArrayList<>();
    mapperMethods.add(
        construct(
            methodType,
            NAMESPACE,
            "method:find-by-key",
            "findByKey",
            List.of(construct(parameterType, "key", "key"), construct(parameterType, "mode", "mode"))));
    mapperMethods.add(
        construct(
            methodType,
            NAMESPACE,
            "method:insert-selective",
            "insertSelective",
            List.of(construct(parameterType, "record", "record"))));
    mapperMethods.add(
        construct(
            methodType,
            NAMESPACE,
            "method:dynamic-target",
            "dynamicTarget",
            List.of(construct(parameterType, "tableName", "tableName"), construct(parameterType, "columnName", "columnName"))));
    mapperMethods.add(
        construct(
            methodType,
            NAMESPACE,
            "method:safe-read",
            "safeRead",
            List.of(construct(parameterType, "input", "input"))));
    mapperMethods.add(
        construct(
            methodType,
            NAMESPACE,
            "method:external-read",
            "externalRead",
            List.of(construct(parameterType, "input", "input"))));
    mapperMethods.add(
        construct(
            methodType,
            NAMESPACE,
            "method:unresolved",
            "unresolved",
            List.of(construct(parameterType, "input", "input"))));
    mapperMethods.add(
        construct(
            methodType,
            NAMESPACE,
            "method:include-only",
            "includeOnly",
            List.of(construct(parameterType, "flag", "flag"))));
    mapperMethods.add(
        construct(
            methodType,
            NAMESPACE,
            "method:xinclude-read",
            "xincludeRead",
            List.of(construct(parameterType, "input", "input"))));
    mapperMethods.add(
        construct(
            methodType,
            NAMESPACE,
            "method:not-present",
            "notPresent",
            List.of(construct(parameterType, "input", "input"))));
    mapperMethods.add(
        construct(
            methodType,
            "other.Mapper",
            "method:foreign-find",
            "findByKey",
            List.of(construct(parameterType, "key", "key"))));
    mapperMethods.add(
        construct(
            methodType,
            NAMESPACE,
            "method:literal-spacing",
            "literalSpacing",
            List.of()));
    mapperMethods.add(
        construct(
            methodType,
            NAMESPACE,
            "method:union-read",
            "unionRead",
            List.of()));
    mapperMethods.add(
        construct(
            methodType,
            NAMESPACE,
            "method:cross-join-read",
            "crossJoinRead",
            List.of()));
    Object probe = probeType.getConstructor().newInstance();
    Method analyze = probeType.getMethod("analyze", List.class, List.class);
    return analyze.invoke(probe, resources, mapperMethods);
  }

  private static Object resource(String path, String rawXml) throws Exception {
    Class<?> resourceType = Class.forName(PROBE_PACKAGE + "MapperResource");
    return construct(resourceType, path, rawXml);
  }

  private static Object statement(Object result, String id, String databaseId, String resourcePath) {
    return statements(result, id).stream()
        .filter(statement -> equalsOrNull(databaseId, value(statement, "databaseId")))
        .filter(statement -> resourcePath.equals(value(statement, "resourcePath")))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "missing statement id=" + id + ", databaseId=" + databaseId + ", path=" + resourcePath));
  }

  private static List<Object> statements(Object result, String id) {
    @SuppressWarnings("unchecked")
    List<Object> statements = (List<Object>) value(result, "statements");
    return statements.stream()
        .filter(statement -> id.equals(value(statement, "id")))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private static String rawSource(Object result, String path) {
    @SuppressWarnings("unchecked")
    List<Object> resources = (List<Object>) value(result, "resources");
    return resources.stream()
        .filter(resource -> path.equals(value(resource, "resourcePath")))
        .map(resource -> String.valueOf(value(resource, "rawSource")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing raw resource " + path));
  }

  private static String resourceStatus(Object result, String path) {
    @SuppressWarnings("unchecked")
    List<Object> resources = (List<Object>) value(result, "resources");
    return resources.stream()
        .filter(resource -> path.equals(value(resource, "resourcePath")))
        .map(resource -> String.valueOf(value(resource, "parseStatus")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing resource " + path));
  }

  private static Object sqlAnalysis(Object result, String statementId, String resourcePath) {
    @SuppressWarnings("unchecked")
    List<Object> analyses = (List<Object>) value(result, "sqlAnalyses");
    return analyses.stream()
        .filter(analysis -> matchesAnalysis(analysis, statementId, resourcePath))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("missing SQL analysis " + statementId + " at " + resourcePath));
  }

  private static boolean matchesAnalysis(Object analysis, String statementId, String resourcePath) {
    String text = String.valueOf(analysis);
    return text.contains(statementId) && text.contains(resourcePath);
  }

  private static List<Object> bindings(Object result) {
    @SuppressWarnings("unchecked")
    List<Object> bindings = (List<Object>) value(result, "bindings");
    return bindings;
  }

  private static List<Object> diagnostics(Object result) {
    @SuppressWarnings("unchecked")
    List<Object> diagnostics = (List<Object>) value(result, "diagnostics");
    return diagnostics;
  }

  private static Object value(Object target, String property) {
    return invoke(target, property);
  }

  private static Object invoke(Object target, String methodName, Object... arguments) {
    try {
      Class<?>[] parameterTypes = new Class<?>[arguments.length];
      for (int index = 0; index < arguments.length; index++) {
        parameterTypes[index] = arguments[index].getClass();
      }
      Method method = target.getClass().getMethod(methodName, parameterTypes);
      return method.invoke(target, arguments);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      throw new AssertionError("probe method failed: " + methodName, cause);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("probe API must expose " + methodName + "()", failure);
    }
  }

  private static Object construct(Class<?> type, Object... arguments) throws Exception {
    for (Constructor<?> constructor : type.getConstructors()) {
      if (constructor.getParameterCount() != arguments.length) {
        continue;
      }
      try {
        return constructor.newInstance(arguments);
      } catch (IllegalArgumentException ignored) {
        // Keep looking for the constructor whose erased List/String types match this contract.
      }
    }
    fail("missing public constructor on " + type.getName() + " for " + arguments.length + " arguments");
    return null;
  }

  private static Class<?> probeType() {
    try {
      Class<?> type = Class.forName(PROBE);
      assertNotNull(type, "the feasibility probe class must exist");
      return type;
    } catch (ClassNotFoundException missing) {
      fail("RED: implement " + PROBE + " before this feasibility gate can pass", missing);
      return null;
    }
  }

  private static boolean equalsOrNull(Object expected, Object actual) {
    return expected == null ? actual == null : expected.equals(actual);
  }

  private static String xxeXml(URI externalEntity) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE mapper [
          <!ENTITY external SYSTEM "%s">
        ]>
        <mapper namespace="sample.alpha.RecordMapper">
          <select id="externalRead">SELECT '&external;'</select>
        </mapper>
        """.formatted(externalEntity);
  }

  private static String parameterEntityXml(URI externalEntity) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE mapper [
          <!ENTITY %% external SYSTEM "%s">
          %%external;
        ]>
        <mapper namespace="sample.alpha.RecordMapper">
          <select id="externalRead">SELECT 1</select>
        </mapper>
        """.formatted(externalEntity);
  }

  private static final String MIXED_XML =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
      <mapper namespace="sample.alpha.RecordMapper">
        <sql id="baseColumns">a.id, a.label, a.amount</sql>
        <sql id="dynamicFilter">AND a.kind = #{kind}</sql>
        <select id="findByKey" resultType="java.util.Map">
          SELECT <include refid="baseColumns"/>, SUM(a.amount) AS total_amount
          FROM alpha_record a
          LEFT JOIN beta_record b ON b.record_id = a.id AND b.active = 1
          <where>
            a.active = 1
            <![CDATA[AND a.note <> '' ]]>
            <if test="key != null and key != ''">
              AND a.key_value = #{key,jdbcType=VARCHAR}
            </if>
            <choose>
              <when test="mode == 'one'">AND a.mode = #{mode}</when>
              <otherwise>AND a.mode &lt;&gt; 'one'</otherwise>
            </choose>
            <include refid="dynamicFilter"/>
            <foreach collection="keys" item="keyItem" open="AND a.id IN (" separator="," close=")">
              #{keyItem}
            </foreach>
          </where>
          GROUP BY a.id, a.label, a.amount
        </select>
        <insert id="insertSelective" parameterType="java.util.Map">
          INSERT INTO alpha_record
          <trim prefix="(" suffix=")" suffixOverrides=",">
            <if test="record.id != null">record_id,</if>
            <if test="record.label != null">record_label,</if>
          </trim>
          <trim prefix="VALUES (" suffix=")" suffixOverrides=",">
            <if test="record.id != null">#{record.id},</if>
            <if test="record.label != null">#{record.label},</if>
          </trim>
        </insert>
        <select id="dynamicTarget">
          SELECT ${columnName} FROM ${tableName}
          WHERE id IN
          <foreach collection="keys" item="keyItem" open="(" separator="," close=")">#{keyItem}</foreach>
        </select>
      </mapper>
      """;

  private static final String VARIANT_XML =
      """
      <mapper namespace="sample.alpha.RecordMapper">
        <select id="findByKey" databaseId="alt">
          SELECT variant_marker, a.id FROM alpha_record_variant a WHERE a.id = #{key}
        </select>
      </mapper>
      """;

  private static final String OGNL_XML =
      """
      <mapper namespace="sample.alpha.RecordMapper">
        <select id="safeRead">
          SELECT 1
          <bind name="unsafe" value="@org.sourceanalysis.research.persistence.PersistenceToolProbeInvocationSentinel@touch()"/>
          <if test="@org.sourceanalysis.research.persistence.PersistenceToolProbeInvocationSentinel@touch() == 'called'">
            AND 1 = 0
          </if>
        </select>
      </mapper>
      """;
}

/** Test-only class that makes accidental OGNL execution observable without customer code. */
final class PersistenceToolProbeInvocationSentinel {

  private static int invocations;

  private PersistenceToolProbeInvocationSentinel() {}

  public static String touch() {
    invocations++;
    return "called";
  }

  static void reset() {
    invocations = 0;
  }

  static int invocations() {
    return invocations;
  }
}
