package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexReader;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsExecution;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialMarkdown;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialProfile;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.analysis.material.publish.CodeReadingMaterialPublisher;
import org.sourceanalysis.app.analysis.material.publish.CodeReadingMaterialReader;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex.DependencyRef;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex.XmlNode;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex.XmlNodeKind;
import org.sourceanalysis.app.analysis.persistence.publish.PersistenceMaterialPublisher;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/**
 * Direct RED coverage for the new Step05-to-Activity path.
 *
 * <p>The protected break is a new Activity request that silently rebuilds legacy M10 material or
 * drops saved Step05 method, call, XML dependency, or packet-local reference information before it
 * reaches the model boundary.
 */
class ActivityMaterialProjectorTest {

  private static final String CONTROLLER_PATH = "src/main/java/example/order/OrderController.java";
  private static final String SERVICE_PATH = "src/main/java/example/order/OrderService.java";
  private static final String MAPPER_PATH = "src/main/java/example/order/OrderMapper.java";
  private static final String XML_PATH = "src/main/resources/mapper/OrderMapper.xml";
  private static final String PRIMARY_MARKER = "primary-packet-marker";
  private static final String SECONDARY_MARKER = "secondary-packet-marker";
  private static final String PRIMARY_CONTROLLER_SOURCE =
      """
      public Response submit(OrderRequest request) {
        String packetMarker = \"primary-packet-marker\";
        return orderService.register(request.getOrderId(), request.getStatus());
      }
      """;
  private static final String SECONDARY_CONTROLLER_SOURCE =
      """
      public Response submitSecond(OrderRequest request) {
        String packetMarker = \"secondary-packet-marker\";
        return orderService.register(request.getOrderId(), request.getStatus());
      }
      """;
  private static final String SERVICE_SOURCE =
      """
      public Response register(String orderId, Integer status) {
        if (status == 0) {
          throw new IllegalStateException(\"status is closed\");
        }
        OrderRecord record = mapper.findById(orderId, status);
        return Response.accepted(record);
      }
      """;
  private static final String MAPPER_SOURCE =
      "OrderRecord findById(String orderId, Integer status);";
  private static final String XML_SOURCE =
      """
      <mapper namespace="example.order.OrderMapper">
        <resultMap id="orderResult" type="example.order.OrderRecord">
          <id property="orderId" column="order_id"/>
          <result property="status" column="status"/>
        </resultMap>
        <sql id="baseColumns">order_id, status</sql>
        <select id="findById" resultMap="orderResult">
          SELECT <include refid="baseColumns"/> FROM orders
          <where>
            <if test="orderId != null">AND order_id = #{orderId}</if>
            <if test="status != null">AND status = #{status}</if>
          </where>
        </select>
      </mapper>
      """;

  @Test
  void exposesTheDirectStep05ProjectionAndActivityRequestSeams() throws Exception {
    Class<?> projector = requiredType("ActivityMaterialProjector");
    Class<?> view = requiredType("ActivityMaterialView");
    Class<?> packet = requiredType("ActivityReadingPacket");
    Class<?> directRequest = requiredType("ExplainCodeReadingMaterialsRequest");

    assertThat(
            projector
                .getMethod(
                    "project",
                    CodeReadingMaterialSet.Packet.class,
                    ActivityExplanationProfile.class)
                .getReturnType())
        .isEqualTo(view);
    assertThat(projector.getMethod("materialize", view).getReturnType()).isEqualTo(packet);
    assertThat(
            directRequest.getConstructor(
                CodeReadingMaterialSet.class, ActivityExplanationProfile.class, int.class))
        .isNotNull();
    assertThat(ActivityExplainer.class.getMethod("explain", directRequest).getReturnType())
        .isEqualTo(ActivityExplanationResult.class);
  }

  @Test
  void sendsEachReopenedStep05PacketLosslesslyToActualDraftAndReviewRequests(
      @TempDir Path temporary) throws Exception {
    CodeReadingMaterialSet reopened =
        persistedStep05Material(temporary.resolve("persisted-step05"));
    RecordingProvider provider = new RecordingProvider();
    ActivityMaterialProjector projector = new ActivityMaterialProjector();
    for (CodeReadingMaterialSet.Packet packet : reopened.packets()) {
      assertThat(
              projector
                  .materialize(
                      projector.project(
                          packet, new ActivityExplanationProfile(128_000, 16_000, 2, 32, 4_000)))
                  .modelInputJson())
          .isEqualTo(
              projector
                  .materialize(
                      projector.project(
                          packet, new ActivityExplanationProfile(128_000, 16_000, 2, 32, 4_000)))
                  .modelInputJson());
    }

    invokeDirectStep05Activity(
        new ActivityExplainer(provider),
        reopened,
        new ActivityExplanationProfile(128_000, 16_000, 2, 32, 4_000),
        2);

    assertThat(provider.requests())
        .extracting(StructuredModelRequest::taskKind)
        .containsExactlyInAnyOrder(
            "ACTIVITY_DRAFT", "ACTIVITY_REVIEW", "ACTIVITY_DRAFT", "ACTIVITY_REVIEW");

    JsonNode primaryDraft = requestFor(provider.requests(), "ACTIVITY_DRAFT", PRIMARY_MARKER);
    JsonNode primaryReview = requestFor(provider.requests(), "ACTIVITY_REVIEW", PRIMARY_MARKER);
    JsonNode secondaryDraft = requestFor(provider.requests(), "ACTIVITY_DRAFT", SECONDARY_MARKER);
    JsonNode secondaryReview = requestFor(provider.requests(), "ACTIVITY_REVIEW", SECONDARY_MARKER);

    assertCompletePrimaryPacket(primaryDraft);
    assertCompletePrimaryPacket(primaryReview);
    assertPacketLocalSecondaryScope(secondaryDraft);
    assertPacketLocalSecondaryScope(secondaryReview);
  }

  private static void assertCompletePrimaryPacket(JsonNode input) {
    JsonNode readingPacket = input.path("readingPacket");
    assertThat(readingPacket.isObject()).isTrue();

    Set<String> text = scalarTexts(readingPacket);
    Set<String> fieldNames = fieldNames(input);
    assertThat(text)
        .contains(
            "E1",
            "M1",
            "C1",
            "C2",
            "X1",
            "S1",
            "S2",
            "S3",
            SERVICE_SOURCE,
            MAPPER_SOURCE,
            "orderService.register(request.getOrderId(), request.getStatus())",
            "mapper.findById(orderId, status)",
            "orderId",
            "status",
            "POSITIONAL",
            "BODY_INCLUDED",
            "DECLARATION_ONLY",
            "external mapper fallback",
            "MULTIPLE_CANDIDATES_RETAINED",
            "IF",
            "status == 0",
            "THROW",
            "new IllegalStateException(\"status is closed\")",
            "RETURN",
            "Response.accepted(record)",
            "findById",
            "orderResult",
            "baseColumns",
            "SELECT",
            "orders",
            "orderId != null",
            "status != null",
            "SAVED_STEP05_LIMITATION");
    assertThat(text).anySatisfy(value -> assertThat(value).contains(PRIMARY_CONTROLLER_SOURCE));
    assertThat(text).anySatisfy(value -> assertThat(value).contains(PRIMARY_MARKER));
    assertThat(text).anySatisfy(value -> assertThat(value).contains("#{orderId}"));
    assertThat(text).anySatisfy(value -> assertThat(value).contains("#{status}"));

    assertThat(fieldNames)
        .doesNotContain(
            "context",
            "technicalObservations",
            "flowRefs",
            "technicalProofRefs",
            "rawSource",
            "path",
            "startLine",
            "endLine",
            "methodKey",
            "packetId");
    assertThat(text)
        .allSatisfy(
            value ->
                assertThat(value)
                    .doesNotContain(
                        CONTROLLER_PATH, SERVICE_PATH, MAPPER_PATH, XML_PATH, "analysis-run:"));
  }

  private static void assertPacketLocalSecondaryScope(JsonNode input) {
    JsonNode readingPacket = input.path("readingPacket");
    assertThat(readingPacket.isObject()).isTrue();

    Set<String> text = scalarTexts(readingPacket);
    assertThat(text)
        .contains("E1", "M1", "C1", "C2", "S1", "S2")
        .doesNotContain("X1", PRIMARY_MARKER, "orderResult", "baseColumns", "#{orderId}");
    assertThat(text).anySatisfy(value -> assertThat(value).contains(SECONDARY_CONTROLLER_SOURCE));
    assertThat(text).anySatisfy(value -> assertThat(value).contains(SECONDARY_MARKER));
    assertThat(text).noneSatisfy(value -> assertThat(value).contains(PRIMARY_MARKER));
    assertThat(text).noneSatisfy(value -> assertThat(value).contains(PRIMARY_CONTROLLER_SOURCE));
  }

  private static JsonNode requestFor(
      List<StructuredModelRequest> requests, String taskKind, String marker) {
    List<JsonNode> matching =
        requests.stream()
            .filter(request -> taskKind.equals(request.taskKind()))
            .map(request -> new CanonicalJsonCodec().parseCanonical(request.untrustedInputJson()))
            .filter(value -> scalarTexts(value).stream().anyMatch(text -> text.contains(marker)))
            .toList();
    assertThat(matching).singleElement();
    return matching.get(0);
  }

  private static void invokeDirectStep05Activity(
      ActivityExplainer explainer,
      CodeReadingMaterialSet material,
      ActivityExplanationProfile profile,
      int maxMaterialsToStart)
      throws Exception {
    Class<?> directRequest = requiredType("ExplainCodeReadingMaterialsRequest");
    Constructor<?> constructor =
        directRequest.getConstructor(
            CodeReadingMaterialSet.class, ActivityExplanationProfile.class, int.class);
    Object request = constructor.newInstance(material, profile, maxMaterialsToStart);
    Method explain = ActivityExplainer.class.getMethod("explain", directRequest);
    try {
      explain.invoke(explainer, request);
    } catch (InvocationTargetException failure) {
      throw (failure.getCause() instanceof Exception exception) ? exception : failure;
    }
  }

  private static Class<?> requiredType(String simpleName) {
    try {
      return Class.forName(ActivityMaterialProjectorTest.class.getPackageName() + "." + simpleName);
    } catch (ClassNotFoundException absent) {
      return fail("missing direct Step05 Activity type: " + simpleName, absent);
    }
  }

  private static CodeReadingMaterialSet persistedStep05Material(Path root) throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(root)) {
      ProgramGraphsReference navigation = navigation(fixture);
      JavaCodeIndex index = new JavaCodeIndexReader(fixture.stepArtifacts()).reopen(navigation);
      PersistenceMaterialIndex persistence = persistence(index, navigation);
      AnalysisStepPublicationReference persistencePublication =
          new PersistenceMaterialPublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.artifactControls(),
                  persistence);
      CodeReadingMaterialSet expected =
          materialSet(fixture, index, navigation, persistencePublication, persistence);
      AnalysisStepPublicationReference publication =
          new CodeReadingMaterialPublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.artifactControls(), expected);

      CodeReadingMaterialSet reopened =
          new CodeReadingMaterialReader(fixture.stepArtifacts()).reopen(publication);
      assertThat(reopened).isEqualTo(expected);
      return reopened;
    }
  }

  private static ProgramGraphsReference navigation(ProgramGraphsPublicFixture fixture) {
    String snapshotId = fixture.sourceReader().reopen(fixture.sourceInventory()).snapshotId();
    try (JavaCodeSession session = richJavaSession(snapshotId)) {
      return new ProgramGraphsExecution(
              fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
          .execute(
              fixture.sourceInventory(),
              fixture.applicationDiscovery(),
              session,
              fixture.artifactControls());
    }
  }

  private static JavaCodeSession richJavaSession(String snapshotId) {
    EntryCodeContext.TechnicalEnhancements enhancements =
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.NOT_PRODUCED,
            "PERSISTED_STEP05_FIXTURE_ONLY",
            List.of(),
            List.of(),
            null);
    return new JavaCodeSession() {
      @Override
      public JavaDeclarationCatalog catalog() {
        return new JavaDeclarationCatalog(
            snapshotId,
            List.of(CONTROLLER_PATH, SERVICE_PATH, MAPPER_PATH),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of());
      }

      @Override
      public EntryCodeContext collect(EntrySeed entry) {
        boolean primary = isPrimary(entry);
        String serviceKey = "method:step05-service:" + entry.entryId();
        String mapperKey = "method:step05-mapper:" + entry.entryId();
        String controllerSource = primary ? PRIMARY_CONTROLLER_SOURCE : SECONDARY_CONTROLLER_SOURCE;
        EntryCodeContext.MethodCode controller =
            method(
                entry.methodKey(),
                "example.order.OrderController",
                primary ? "submit" : "submitSecond",
                controllerSource,
                CONTROLLER_PATH,
                List.of(
                    new JavaDeclarationCatalog.ParameterView(
                        0, "request", "OrderRequest", false, List.of())),
                List.of(),
                List.of(
                    new EntryCodeContext.Exit(
                        "RETURN",
                        "orderService.register(request.getOrderId(), request.getStatus())",
                        range(controllerSource))));
        EntryCodeContext.MethodCode service =
            method(
                serviceKey,
                "example.order.OrderService",
                "register",
                SERVICE_SOURCE,
                SERVICE_PATH,
                List.of(
                    new JavaDeclarationCatalog.ParameterView(
                        0, "orderId", "String", false, List.of()),
                    new JavaDeclarationCatalog.ParameterView(
                        1, "status", "Integer", false, List.of())),
                List.of(
                    new EntryCodeContext.Control("IF", "status == 0", range(SERVICE_SOURCE), null)),
                List.of(
                    new EntryCodeContext.Exit(
                        "THROW",
                        "new IllegalStateException(\"status is closed\")",
                        range(SERVICE_SOURCE)),
                    new EntryCodeContext.Exit(
                        "RETURN", "Response.accepted(record)", range(SERVICE_SOURCE))));
        EntryCodeContext.MethodCode mapper =
            method(
                mapperKey,
                "example.order.OrderMapper",
                "findById",
                MAPPER_SOURCE,
                MAPPER_PATH,
                List.of(
                    new JavaDeclarationCatalog.ParameterView(
                        0, "orderId", "String", false, List.of()),
                    new JavaDeclarationCatalog.ParameterView(
                        1, "status", "Integer", false, List.of())),
                List.of(),
                List.of());
        EntryCodeContext.CallSite serviceCall =
            new EntryCodeContext.CallSite(
                "call:step05-service:" + entry.entryId(),
                controller.methodKey(),
                "METHOD",
                range(controllerSource),
                range(controllerSource),
                "orderService.register(request.getOrderId(), request.getStatus())",
                "orderService",
                List.of(
                    new EntryCodeContext.ActualArgument(0, "request.getOrderId()"),
                    new EntryCodeContext.ActualArgument(1, "request.getStatus()")),
                List.of(),
                false,
                List.of(
                    new EntryCodeContext.CallTarget(
                        serviceKey,
                        List.of("DECLARATION"),
                        "example.order.OrderService#register",
                        List.of("ENGINE_BINDING"),
                        "BODY_INCLUDED",
                        null,
                        List.of(
                            new EntryCodeContext.ArgumentAssociation(List.of(0), 0, "POSITIONAL"),
                            new EntryCodeContext.ArgumentAssociation(
                                List.of(1), 1, "POSITIONAL")))),
                "LOCATED",
                null);
        EntryCodeContext.CallSite mapperCall =
            new EntryCodeContext.CallSite(
                "call:step05-mapper:" + entry.entryId(),
                service.methodKey(),
                "METHOD",
                range(SERVICE_SOURCE),
                range(SERVICE_SOURCE),
                "mapper.findById(orderId, status)",
                "mapper",
                List.of(
                    new EntryCodeContext.ActualArgument(0, "orderId"),
                    new EntryCodeContext.ActualArgument(1, "status")),
                List.of(0),
                false,
                List.of(
                    new EntryCodeContext.CallTarget(
                        mapperKey,
                        List.of("DECLARATION"),
                        "example.order.OrderMapper#findById",
                        List.of("ENGINE_BINDING"),
                        "DECLARATION_ONLY",
                        "mapper declaration has no Java body",
                        List.of(
                            new EntryCodeContext.ArgumentAssociation(List.of(0), 0, "POSITIONAL"),
                            new EntryCodeContext.ArgumentAssociation(List.of(1), 1, "POSITIONAL"))),
                    new EntryCodeContext.CallTarget(
                        null,
                        List.of("IMPLEMENTATION"),
                        "external mapper fallback",
                        List.of("CALL_HIERARCHY"),
                        "EXTERNAL",
                        "MULTIPLE_CANDIDATES_RETAINED",
                        List.of(
                            new EntryCodeContext.ArgumentAssociation(List.of(0), null, "UNKNOWN"),
                            new EntryCodeContext.ArgumentAssociation(
                                List.of(1), null, "UNKNOWN")))),
                "CANDIDATES",
                "two saved target candidates remain relevant");
        return new EntryCodeContext(
            EntryCodeContext.SCHEMA_VERSION,
            entry.entryId(),
            entry.methodKey(),
            List.of(controller, service, mapper),
            List.of(serviceCall, mapperCall),
            List.of(),
            List.of(
                new EntryCodeContext.Limitation(
                    "SAVED_STEP05_LIMITATION",
                    "the external mapper fallback remains unresolved",
                    List.of(serviceKey),
                    List.of(mapperCall.callKey()))),
            enhancements);
      }

      @Override
      public EngineDescriptor descriptor() {
        return new EngineDescriptor(
            "jdt", "persisted-step05-fixture", Map.of("fixture", "1"), "17", List.of());
      }

      @Override
      public void close() {}
    };
  }

  private static PersistenceMaterialIndex persistence(
      JavaCodeIndex index, ProgramGraphsReference navigation) {
    JavaCodeIndex.EntryCollection primary =
        index.entries().stream().filter(value -> isPrimary(value.seed())).findFirst().orElseThrow();
    String mapperKey = primary.context().methods().get(2).methodKey();
    String statementRef = "statement:" + XML_PATH + "#findById";
    PersistenceMaterialIndex.Resource resource =
        new PersistenceMaterialIndex.Resource(
            XML_PATH, "example.order.OrderMapper", XML_SOURCE, List.of());
    PersistenceMaterialIndex.Statement statement =
        new PersistenceMaterialIndex.Statement(
            statementRef,
            XML_PATH,
            "example.order.OrderMapper",
            "findById",
            "select",
            null,
            element(
                "select",
                Map.of("id", "findById", "resultMap", "orderResult"),
                text("SELECT "),
                element("include", Map.of("refid", "baseColumns")),
                text(" FROM orders "),
                element(
                    "where",
                    Map.of(),
                    element(
                        "if", Map.of("test", "orderId != null"), text("AND order_id = #{orderId}")),
                    element(
                        "if", Map.of("test", "status != null"), text("AND status = #{status}")))),
            List.of(
                new DependencyRef("INCLUDE", "baseColumns", "STATIC_RESOLVED"),
                new DependencyRef("RESULT_MAP", "orderResult", "STATIC_RESOLVED")));
    PersistenceMaterialIndex.JavaBinding binding =
        new PersistenceMaterialIndex.JavaBinding(
            "example.order.OrderMapper",
            mapperKey,
            "findById(java.lang.String,java.lang.Integer)",
            "EXACT",
            List.of(
                new PersistenceMaterialIndex.ParameterBinding(
                    0, "orderId", "String", List.of(), List.of("orderId"), List.of()),
                new PersistenceMaterialIndex.ParameterBinding(
                    1, "status", "Integer", List.of(), List.of("status"), List.of())),
            List.of(new PersistenceMaterialIndex.StatementRef(statementRef, null)),
            List.of());
    PersistenceMaterialIndex.SqlAnalysis sql =
        new PersistenceMaterialIndex.SqlAnalysis(
            statementRef,
            "SELECT order_id, status FROM orders WHERE order_id = ? AND status = ?",
            List.of("dynamic XML retained outside SQL AST"),
            new PersistenceMaterialIndex.SqlAstNode(
                "SELECT",
                null,
                Map.of(),
                List.of(
                    new PersistenceMaterialIndex.SqlAstNode(
                        "FROM", "orders", Map.of(), List.of()))),
            PersistenceMaterialIndex.SqlStatus.PARSED,
            null);
    return new PersistenceMaterialIndex(
        new PersistenceMaterialIndex.Header(
            PersistenceMaterialIndex.Status.ENABLED,
            index.snapshotId(),
            navigation,
            List.of(
                new PersistenceMaterialIndex.Tool("mybatis", "3.5.19"),
                new PersistenceMaterialIndex.Tool("jsqlparser", "5.3"))),
        List.of(resource),
        List.of(statement),
        List.of(binding),
        List.of(sql),
        List.of());
  }

  private static CodeReadingMaterialSet materialSet(
      ProgramGraphsPublicFixture fixture,
      JavaCodeIndex index,
      ProgramGraphsReference navigation,
      AnalysisStepPublicationReference persistencePublication,
      PersistenceMaterialIndex persistence) {
    List<CodeReadingMaterialSet.Packet> packets = new ArrayList<>();
    List<CodeReadingMaterialSet.EntryCoverage> coverage = new ArrayList<>();
    for (JavaCodeIndex.EntryCollection entry : index.entries()) {
      boolean primary = isPrimary(entry.seed());
      CodeReadingMaterialSet.PersistenceSelection selectedPersistence =
          primary
              ? new CodeReadingMaterialSet.PersistenceSelection(
                  persistence.resources(),
                  persistence.statements(),
                  persistence.bindings(),
                  persistence.sqlAnalyses(),
                  persistence.diagnostics())
              : new CodeReadingMaterialSet.PersistenceSelection(
                  List.of(), List.of(), List.of(), List.of(), List.of());
      List<CodeReadingMaterialSet.SourceReference> refs =
          primary
              ? List.of(
                  reference(
                      "S1",
                      "JAVA_METHOD",
                      entry.context().methods().get(0).methodKey(),
                      CONTROLLER_PATH),
                  reference(
                      "S2",
                      "JAVA_METHOD",
                      entry.context().methods().get(1).methodKey(),
                      SERVICE_PATH),
                  reference("S3", "MAPPER_RESOURCE", XML_PATH, XML_PATH))
              : List.of(
                  reference(
                      "S1",
                      "JAVA_METHOD",
                      entry.context().methods().get(0).methodKey(),
                      CONTROLLER_PATH),
                  reference(
                      "S2",
                      "JAVA_METHOD",
                      entry.context().methods().get(1).methodKey(),
                      SERVICE_PATH));
      CodeReadingMaterialSet.Packet provisional =
          new CodeReadingMaterialSet.Packet(
              "packet:step05:" + (primary ? "primary" : "secondary"),
              List.of(entry.seed()),
              entry.context().methods(),
              entry.context().calls().stream()
                  .map(call -> new CodeReadingMaterialSet.EntryCall(entry.seed().entryId(), call))
                  .toList(),
              selectedPersistence,
              refs,
              List.of(),
              List.of("SAVED_STEP05_LIMITATION"),
              0L);
      CodeReadingMaterialSet.Packet packet =
          new CodeReadingMaterialSet.Packet(
              provisional.packetId(),
              provisional.entries(),
              provisional.methods(),
              provisional.calls(),
              provisional.persistence(),
              provisional.sourceReferences(),
              provisional.unselectedUnits(),
              provisional.limitations(),
              CodeReadingMaterialMarkdown.renderPacket(provisional)
                  .getBytes(StandardCharsets.UTF_8)
                  .length);
      packets.add(packet);
      coverage.add(
          new CodeReadingMaterialSet.EntryCoverage(
              entry.seed().entryId(),
              List.of(packet.packetId()),
              CodeReadingMaterialSet.CoverageStatus.COLLECTED,
              List.of()));
    }
    return new CodeReadingMaterialSet(
        new CodeReadingMaterialSet.Header(
            fixture.sourceInventory(),
            navigation,
            persistencePublication,
            index.snapshotId(),
            new CodeReadingMaterialProfile(128_000L, 2)),
        packets,
        coverage);
  }

  private static EntryCodeContext.MethodCode method(
      String methodKey,
      String declaringType,
      String name,
      String source,
      String path,
      List<JavaDeclarationCatalog.ParameterView> parameters,
      List<EntryCodeContext.Control> controls,
      List<EntryCodeContext.Exit> exits) {
    return new EntryCodeContext.MethodCode(
        methodKey,
        "METHOD",
        declaringType,
        name,
        source,
        null,
        parameters,
        "Response",
        List.of("public"),
        List.of(),
        new EntryCodeContext.SourceSource(path, range(source), source),
        true,
        controls,
        exits);
  }

  private static CodeReadingMaterialSet.SourceReference reference(
      String sourceRef, String kind, String unitRef, String path) {
    return new CodeReadingMaterialSet.SourceReference(
        sourceRef, new CodeReadingMaterialSet.UnitLocation(kind, unitRef, path, 1, 1));
  }

  private static XmlNode element(String name, Map<String, String> attributes, XmlNode... children) {
    return new XmlNode(XmlNodeKind.ELEMENT, name, attributes, null, List.of(children));
  }

  private static XmlNode text(String value) {
    return new XmlNode(XmlNodeKind.TEXT, null, Map.of(), value, List.of());
  }

  private static SourceRange range(String source) {
    return new SourceRange(
        0, source.length(), 1, Math.max(1, Math.toIntExact(source.lines().count())));
  }

  private static boolean isPrimary(EntrySeed entry) {
    return entry.trigger().contains("/orders/approve");
  }

  private static Set<String> scalarTexts(JsonNode node) {
    Set<String> values = new HashSet<>();
    collectTexts(node, values);
    return values;
  }

  private static void collectTexts(JsonNode node, Set<String> values) {
    if (node.isTextual()) {
      values.add(node.asText());
    }
    node.elements().forEachRemaining(child -> collectTexts(child, values));
  }

  private static Set<String> fieldNames(JsonNode node) {
    Set<String> fields = new HashSet<>();
    collectFieldNames(node, fields);
    return fields;
  }

  private static void collectFieldNames(JsonNode node, Set<String> fields) {
    node.fieldNames().forEachRemaining(fields::add);
    node.elements().forEachRemaining(child -> collectFieldNames(child, fields));
  }

  private static final class RecordingProvider implements StructuredModelProvider {
    private final List<StructuredModelRequest> requests = new ArrayList<>();

    @Override
    public synchronized StructuredModelResponse generate(StructuredModelRequest request) {
      requests.add(request);
      String response =
          "ACTIVITY_REVIEW".equals(request.taskKind())
              ? "{\"activities\":[],\"unexplainedEntries\":[\"E1\"]}"
              : "{\"activities\":[]}";
      return new StructuredModelResponse(
          ImmutableBytes.copyOf(response.getBytes(StandardCharsets.UTF_8)),
          new ModelRuntimeIdentityV1("scripted", "step05-projector", "none", "none"));
    }

    private synchronized List<StructuredModelRequest> requests() {
      return List.copyOf(requests);
    }
  }
}
