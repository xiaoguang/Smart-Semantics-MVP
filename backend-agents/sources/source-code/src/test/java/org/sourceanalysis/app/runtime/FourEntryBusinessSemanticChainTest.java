package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.document.BusinessReportProfile;
import org.sourceanalysis.app.analysis.document.BusinessReportPublication;
import org.sourceanalysis.app.analysis.document.BusinessReportPublisher;
import org.sourceanalysis.app.analysis.document.PublishBusinessReportRequest;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplainer;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationProfile;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ExplainActivitiesRequest;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialMode;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.analysis.interpretation.material.ModelActivityPacket;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.analysis.knowledge.ExplainRepositoryProcessesRequest;
import org.sourceanalysis.app.analysis.knowledge.ProcessExplainer;
import org.sourceanalysis.app.analysis.knowledge.ProcessExplanationProfile;
import org.sourceanalysis.app.analysis.knowledge.RepositoryBusinessKnowledge;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.Sha256Digest;

/**
 * Exercises the active three-module business chain against the saved four-entry UserController
 * packet through the active Activity, Process, and Report Modules without replaying a historical
 * live-model candidate or claiming the persisted runtime executor is exercised here.
 *
 * <p>The four excerpts and short refs are the fixed input selected for the separately authorized
 * live validation. This test uses a deterministic provider only: it proves that a complete or
 * explicitly partial activity interpretation survives process reconstruction and report writing.
 */
class FourEntryBusinessSemanticChainTest {

  private static final String USER_CONTROLLER =
      "jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java";
  private static final String MATERIAL_ID =
      "material:8be00d5562743218931b721c547d915076a08b7200bc06e415d1248c5ea663eb";
  private static final List<EntryFixture> ENTRIES =
      List.of(
          new EntryFixture(
              "entry:160b90d56d87df77d8ad02aadb61130b138af016d31edde06f30e517f4497ece",
              "E1",
              "S487",
              "DELETE /user/delete",
              105,
              111,
              """
              @DeleteMapping(value = "/delete")
                  @ApiOperation(value = "删除")
                  public String deleteResource(@RequestParam("id") Long id, HttpServletRequest request)throws Exception {
                      Map<String, Object> objectMap = new HashMap<>();
                      int delete = userService.deleteUser(id, request);
                      return returnStr(objectMap, delete);
                  }
              """
                  .strip()),
          new EntryFixture(
              "entry:2d16e9a0ceb9b9555823ff3b091485941d4e26fb0b8e75b0314fdedd837722bc",
              "E2",
              "S722",
              "GET /user/getUserSession",
              195,
              213,
              """
              @GetMapping(value = "/getUserSession")
                  @ApiOperation(value = "获取用户信息")
                  public BaseResponseInfo getSessionUser(HttpServletRequest request)throws Exception {
                      BaseResponseInfo res = new BaseResponseInfo();
                      try {
                          Map<String, Object> data = new HashMap<>();
                          Long userId = Long.parseLong(redisService.getObjectFromSessionByKey(request,"userId").toString());
                          User user = userService.getUser(userId);
                          user.setPassword(null);
                          data.put("user", user);
                          res.code = 200;
                          res.data = data;
                      } catch(Exception e){
                          logger.error(e.getMessage(), e);
                          res.code = 500;
                          res.data = "获取session失败";
                      }
                      return res;
                  }
              """
                  .strip()),
          new EntryFixture(
              "entry:2d573f55b3164ae226957503381cb4c22104e263ba0e36f316cd1ad609302439",
              "E3",
              "S731",
              "POST /user/registerUser",
              357,
              367,
              """
              @PostMapping(value = "/registerUser")
                  @ApiOperation(value = "注册用户")
                  public Object registerUser(@RequestBody UserEx ue,
                                             HttpServletRequest request)throws Exception{
                      JSONObject result = ExceptionConstants.standardSuccess();
                      ue.setUsername(ue.getLoginName());
                      userService.validateCaptcha(ue.getCode(), ue.getUuid());
                      userService.checkLoginName(ue);
                      userService.registerUser(ue,manageRoleId,request);
                      return result;
                  }
              """
                  .strip()),
          new EntryFixture(
              "entry:3bc9f42e69961211dec7e48baeffbb0bd61a4c4be03280eb02894f86b6e9b144",
              "E4",
              "S898",
              "GET /user/logout",
              215,
              228,
              """
              @GetMapping(value = "/logout")
                  @ApiOperation(value = "退出")
                  public BaseResponseInfo logout(HttpServletRequest request, HttpServletResponse response)throws Exception {
                      BaseResponseInfo res = new BaseResponseInfo();
                      try {
                          redisService.deleteObjectBySession(request,"userId");
                          redisService.deleteObjectBySession(request,"clientIp");
                      } catch(Exception e){
                          res.code = 500;
                          res.data = "退出失败";
                      }
                      return res;
                  }
              """
                  .strip()));

  @Test
  void carriesAllFourReviewedUserActivitiesThroughIndependentProcessesAndNineSections() {
    ScriptedProvider provider = new ScriptedProvider(Mode.COMPLETE);

    BusinessMaterialBuildResult materials = fourEntryMaterials();
    ActivityExplanationResult activities = explainActivities(provider, materials);
    RepositoryBusinessKnowledge knowledge = explainProcesses(provider, activities, materials);
    BusinessReportPublication report = publishReport(provider, knowledge);

    assertThat(activities.coverage())
        .extracting(ActivityEntryCoverage::entryId)
        .containsExactlyElementsOf(ENTRIES.stream().map(EntryFixture::entryId).toList());
    assertThat(activities.coverage())
        .extracting(ActivityEntryCoverage::disposition)
        .containsOnly("ANALYZED");
    assertThat(activities.reviewedActivities())
        .extracting(value -> value.name())
        .containsExactlyInAnyOrder("处理用户删除", "获取当前会话用户信息", "登记用户", "清理当前会话");
    assertThat(knowledge.processes()).hasSize(4);
    assertThat(knowledge.processes())
        .allSatisfy(process -> assertThat(process.activityIds()).hasSize(1));
    assertThat(knowledge.processes())
        .extracting(value -> value.name())
        .containsExactlyInAnyOrder("用户删除处理", "会话用户信息读取", "用户登记", "会话退出处理");
    assertThat(headingCount(report.documentMarkdown())).isEqualTo(9);
    assertThat(report.documentMarkdown())
        .contains("## 4. 业务活动", "处理用户删除", "获取当前会话用户信息", "登记用户", "清理当前会话", "不被写成必然前后衔接");
    assertThat(provider.taskKinds())
        .containsExactly(
            "ACTIVITY_DRAFT",
            "ACTIVITY_REVIEW",
            "PROCESS_GROUP_DRAFT",
            "PROCESS_GROUP_REVIEW",
            "BUSINESS_REPORT_DRAFT",
            "BUSINESS_REPORT_REVIEW");
    assertThat(provider.activityReviewInput().path("actualDraft"))
        .isEqualTo(provider.activityDraftResponse());
    assertThat(texts(provider.activityReviewInput().path("missingEntryKeys")))
        .containsExactly("E3", "E4");
    assertThat(provider.reportDraftInput().path("activities"))
        .extracting(value -> value.path("name").asText())
        .containsExactlyInAnyOrder("处理用户删除", "获取当前会话用户信息", "登记用户", "清理当前会话");
    assertThat(provider.reportDraftInput().path("activities"))
        .flatExtracting(value -> texts(value.path("conditions")))
        .contains("验证码和登录名校验通过后才调用登记服务。");
    assertThat(provider.reportDraftInput().path("activities"))
        .flatExtracting(value -> texts(value.path("businessRules")))
        .contains("会话中的用户和客户端标识均被清除。");
  }

  @Test
  void carriesConcreteUnexplainedUserEntriesToKnowledgeAndChapterNineWithoutInventingActivities() {
    ScriptedProvider provider = new ScriptedProvider(Mode.PARTIAL);

    BusinessMaterialBuildResult materials = fourEntryMaterials();
    ActivityExplanationResult activities = explainActivities(provider, materials);
    RepositoryBusinessKnowledge knowledge = explainProcesses(provider, activities, materials);
    BusinessReportPublication report = publishReport(provider, knowledge);

    assertThat(texts(provider.activityReviewInput().path("missingEntryKeys")))
        .containsExactly("E3", "E4");
    assertThat(activities.unexplainedActivityEntries())
        .extracting(
            value -> value.entryId(), value -> value.entryKey(), value -> value.reasonCode())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                ENTRIES.get(2).entryId(), "E3", "MODEL_NOT_EXPLAINED"),
            org.assertj.core.groups.Tuple.tuple(
                ENTRIES.get(3).entryId(), "E4", "MODEL_NOT_EXPLAINED"));
    assertThat(knowledge.unexplainedActivityEntries())
        .containsExactlyElementsOf(activities.unexplainedActivityEntries());
    JsonNode unexplained = provider.reportDraftInput().path("unexplainedActivityEntries");
    assertThat(unexplained).hasSize(1);
    assertThat(texts(unexplained.get(0).path("unexplainedEntryKeys"))).containsExactly("E3", "E4");
    assertThat(unexplained.get(0).path("materialContext").asText())
        .contains("POST /user/registerUser", "GET /user/logout");
    assertThat(unexplained.get(0).path("reasonCode").asText()).isEqualTo("MODEL_NOT_EXPLAINED");
    assertThat(provider.reportDraftInput().path("activities"))
        .extracting(value -> value.path("name").asText())
        .containsExactlyInAnyOrder("处理用户删除", "获取当前会话用户信息");
    assertThat(sectionText(report, 2)).doesNotContain("登记", "退出");
    assertThat(sectionText(report, 4)).doesNotContain("登记", "退出");
    assertThat(sectionText(report, 5)).doesNotContain("登录名", "验证码");
    assertThat(sectionText(report, 8)).doesNotContain("登记", "验证码");
    assertThat(report.documentMarkdown())
        .contains("POST /user/registerUser", "GET /user/logout", "MODEL_NOT_EXPLAINED")
        .doesNotContain("登记用户已完成", "清理当前会话已完成");
    assertThat(headingCount(report.documentMarkdown())).isEqualTo(9);
  }

  private static ActivityExplanationResult explainActivities(
      StructuredModelProvider provider, BusinessMaterialBuildResult materials) {
    return new ActivityExplainer(provider)
        .explain(
            new ExplainActivitiesRequest(
                materials, new ActivityExplanationProfile(20_000, 12_000, 4, 24, 1_000), 1));
  }

  private static RepositoryBusinessKnowledge explainProcesses(
      StructuredModelProvider provider,
      ActivityExplanationResult activities,
      BusinessMaterialBuildResult materials) {
    return new ProcessExplainer(provider)
        .explain(
            new ExplainRepositoryProcessesRequest(
                activities,
                materials.materialSet(),
                new ProcessExplanationProfile(4, 1, 20_000, 12_000, 4, 24, 1_000, 0)));
  }

  private static BusinessReportPublication publishReport(
      StructuredModelProvider provider, RepositoryBusinessKnowledge knowledge) {
    return new BusinessReportPublisher(provider)
        .publish(
            new PublishBusinessReportRequest(
                knowledge,
                ENTRIES.stream().map(EntryFixture::sourceReference).toList(),
                new BusinessReportProfile(20_000, 12_000, 24, 1_000)));
  }

  private static BusinessMaterialBuildResult fourEntryMaterials() {
    List<SourceReference> refs = ENTRIES.stream().map(EntryFixture::sourceReference).toList();
    String context =
        """
        本包包含以下相关 HTTP 入口：
        入口 E1：已发现 HTTP 入口 HTTP DELETE /user/delete。
        入口 E2：已发现 HTTP 入口 HTTP GET /user/getUserSession。
        入口 E3：已发现 HTTP 入口 HTTP POST /user/registerUser。
        入口 E4：已发现 HTTP 入口 HTTP GET /user/logout。
        请分别解释每个入口的局部业务活动；只有片段明确支持时才说明它们之间的关系。
        """
            .strip();
    BusinessMaterial material =
        new BusinessMaterial(
            MATERIAL_ID,
            ENTRIES.stream().map(EntryFixture::entryId).toList(),
            BusinessMaterialMode.FLOW_PREFERRED,
            context,
            List.of(
                "已定位四个用户入口的 HTTP 方法、调用和返回路径。", "注册入口调用验证码校验、登录名校验和用户登记服务。", "会话读取和退出入口都使用当前会话上下文。"),
            refs,
            List.of(
                "flow:user-delete", "flow:user-session", "flow:user-register", "flow:user-logout"),
            List.of(),
            List.of(BusinessMaterial.SNIPPET_BUDGET_NOTICE),
            new ModelActivityPacket(
                context,
                List.of(
                    "E1 调用用户删除服务并包装返回。",
                    "E2 从会话读取用户标识，查询用户并清除密码字段；异常返回失败信息。",
                    "E3 规范登录名，校验验证码和登录名后调用用户登记服务。",
                    "E4 清除当前会话中的用户和客户端标识；异常返回退出失败。"),
                ENTRIES.stream()
                    .map(
                        entry ->
                            new ModelActivityPacket.AllowlistedReference(
                                entry.ref(), entry.snippet()))
                    .toList(),
                List.of("静态源码不能证明某次删除、登记或退出实际完成。")));
    List<BusinessMaterialEntryCoverage> coverage =
        ENTRIES.stream()
            .map(
                entry ->
                    new BusinessMaterialEntryCoverage(
                        entry.entryId(), "ANALYZED_MATERIAL", MATERIAL_ID, null))
            .toList();
    return new BusinessMaterialBuildResult(
        new BusinessMaterialSet(
            "business-material-set:user-four-entry", List.of(material), coverage),
        placeholderCheckpoint());
  }

  private static int headingCount(String markdown) {
    return (int) markdown.lines().filter(line -> line.startsWith("## ")).count();
  }

  private static String sectionText(BusinessReportPublication report, int sectionNumber) {
    return report.businessReport().sections().stream()
        .filter(section -> section.number() == sectionNumber)
        .flatMap(section -> section.paragraphs().stream())
        .map(value -> value.text())
        .findFirst()
        .orElseThrow();
  }

  private static List<String> texts(JsonNode values) {
    List<String> result = new ArrayList<>();
    values.forEach(value -> result.add(value.asText()));
    return result;
  }

  private static ModulePublicationReference placeholderCheckpoint() {
    String zeros = "0".repeat(64);
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(
            AnalysisRunId.parse("analysis-run:" + zeros),
            AnalysisStepKey.FLOW_INTERPRETATION,
            10,
            "business-material-builder"),
        ModuleArtifactRoot.parse("module-root:" + zeros),
        ModuleReceiptId.parse("module-receipt:" + zeros),
        Sha256Digest.parse(zeros));
  }

  private enum Mode {
    COMPLETE,
    PARTIAL
  }

  private record EntryFixture(
      String entryId,
      String localKey,
      String ref,
      String route,
      int startLine,
      int endLine,
      String snippet) {

    private SourceReference sourceReference() {
      return new SourceReference(ref, USER_CONTROLLER, startLine, endLine, snippet);
    }
  }

  private static final class ScriptedProvider implements StructuredModelProvider {
    private static final List<String> SECTION_TITLES =
        List.of("文档说明", "业务目标", "业务对象", "业务活动", "字段与维度", "对象关系", "指标口径", "示例问题", "待确认事项");

    private final Mode mode;
    private final CanonicalJsonCodec json = new CanonicalJsonCodec();
    private final List<String> taskKinds = new ArrayList<>();
    private JsonNode activityDraftResponse;
    private JsonNode activityReviewInput;
    private JsonNode reportDraftInput;

    private ScriptedProvider(Mode mode) {
      this.mode = mode;
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      taskKinds.add(request.taskKind());
      JsonNode input = json.parseCanonical(request.untrustedInputJson());
      JsonNode response =
          switch (request.taskKind()) {
            case "ACTIVITY_DRAFT" -> {
              JsonNode draft = activities(2, false);
              activityDraftResponse = draft;
              yield draft;
            }
            case "ACTIVITY_REVIEW" -> {
              activityReviewInput = input;
              yield activities(mode == Mode.PARTIAL ? 2 : 4, true);
            }
            case "PROCESS_GROUP_DRAFT", "PROCESS_GROUP_REVIEW" -> processes(input);
            case "BUSINESS_REPORT_DRAFT" -> {
              reportDraftInput = input;
              yield report(input);
            }
            case "BUSINESS_REPORT_REVIEW" -> report(input);
            default -> throw new AssertionError("unexpected task kind: " + request.taskKind());
          };
      return new StructuredModelResponse(
          json.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "four-entry-chain", "none", "none"));
    }

    private JsonNode activities(int entryCount, boolean reviewed) {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      ArrayNode activities = root.putArray("activities");
      ENTRIES.subList(0, entryCount).forEach(entry -> activity(activities.addObject(), entry));
      if (reviewed) {
        ArrayNode unexplained = root.putArray("unexplainedEntries");
        if (mode == Mode.PARTIAL) {
          unexplained.add("E3").add("E4");
        }
      }
      return root;
    }

    private static void activity(ObjectNode value, EntryFixture entry) {
      value.put("activityLocalId", "activity-" + entry.localKey());
      value.putArray("entryKeys").add(entry.localKey());
      value.put("name", activityName(entry.localKey()));
      value.put("businessPurpose", activityPurpose(entry.localKey()));
      value.putArray("participants");
      value.putArray("businessObjects").add("用户");
      value.putArray("triggerOrInput").add(entry.route());
      ArrayNode conditions = value.putArray("conditions");
      conditions.add(condition(entry.localKey()));
      value.putArray("activitySteps").add(activityStep(entry.localKey()));
      value.putArray("codeDefinedResults").add(codeResult(entry.localKey()));
      value.putArray("businessRules").add(rule(entry.localKey()));
      value.putArray("formulasOrMetrics");
      value.putArray("terms").add("用户");
      value.put("certainty", "DIRECT_CODE_BEHAVIOR");
      value.putArray("sourceRefs").add(entry.ref());
      value.putArray("questions").add("具体权限和运行时数据范围由何处定义？");
      value.putArray("scopeLimitations").add("静态源码不证明某次操作实际完成。");
    }

    private static JsonNode processes(JsonNode input) {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      ArrayNode values = root.putArray("processes");
      int index = 1;
      for (JsonNode activity : input.path("activities")) {
        ObjectNode process = values.addObject();
        process.put("processLocalId", "independent-process-" + index++);
        process.put("name", processName(activity.path("name").asText()));
        process.put("businessPurpose", activity.path("businessPurpose").asText());
        String activityId = activity.path("activityId").asText();
        process.putArray("activityIds").add(activityId);
        process
            .putArray("stages")
            .addObject()
            .put("order", 1)
            .put("activityId", activityId)
            .put("description", activity.path("name").asText());
        process.putArray("branches");
        process.putArray("sharedObjects").add("用户");
        process
            .putArray("codeDefinedResults")
            .addAll((ArrayNode) activity.path("codeDefinedResults"));
        process.put("certainty", "DIRECT_CODE_BEHAVIOR");
        process.putArray("sourceRefs").add(activity.path("sourceRefs").get(0).asText());
        process.putArray("confirmationNotes").add("未根据同一控制器文件推断业务先后顺序。");
      }
      root.putArray("unmatchedActivityIds");
      return root;
    }

    private JsonNode report(JsonNode input) {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      root.put("title", "用户账户入口业务说明");
      boolean partial = !input.path("unexplainedActivityEntries").isEmpty();
      List<String> activityNames = new ArrayList<>();
      input.path("activities").forEach(value -> activityNames.add(value.path("name").asText()));
      ArrayNode sections = root.putArray("sections");
      section(
          sections,
          1,
          "文档说明",
          "本文依据冻结源码说明已经形成业务解释的用户账户入口；未解释入口列入第9章。",
          refsFor(partial ? List.of("E1", "E2") : List.of("E1", "E2", "E3", "E4")));
      section(
          sections,
          2,
          "业务目标",
          partial ? "已形成解释的入口用于处理用户删除和读取当前会话用户信息。" : "系统提供用户登记、查询、删除和退出相关的入口处理。",
          refsFor(partial ? List.of("E1", "E2") : List.of("E1", "E2", "E3", "E4")));
      section(sections, 3, "业务对象", "已形成解释的代码片段围绕用户、当前会话和用户标识处理请求。", refsFor(List.of("E1", "E2")));
      section(
          sections,
          4,
          "业务活动",
          partial
              ? "本章只说明已经形成活动解释的" + String.join("、", activityNames) + "；未解释入口见第9章。"
              : "本章分别说明"
                  + String.join("、", activityNames)
                  + "；登记入口在调用登记服务前校验验证码和登录名，退出入口清除会话标识；它们不被写成必然前后衔接的用户生命周期。",
          refsFor(partial ? List.of("E1", "E2") : List.of("E1", "E2", "E3", "E4")));
      section(
          sections,
          5,
          "字段与维度",
          partial ? "用户标识和会话中的用户标识参与已解释片段的处理。" : "用户标识、登录名、验证码和会话中的用户标识参与片段中的处理。",
          refsFor(partial ? List.of("E1", "E2") : List.of("E1", "E2", "E3", "E4")));
      section(sections, 6, "对象关系", "会话读取到的用户标识被传给用户查询服务；其他关系以代码片段为限。", refsFor(List.of("E2")));
      section(sections, 7, "指标口径", "本次片段没有定义可作为经营指标的公式或口径。", refsFor(List.of("E1", "E2")));
      section(
          sections,
          8,
          "示例问题",
          partial ? "用户删除处理如何界定权限？会话读取异常时返回什么？" : "用户登记前执行了哪些校验？会话读取异常时返回什么？",
          refsFor(partial ? List.of("E1", "E2") : List.of("E2", "E3")));
      section(
          sections,
          9,
          "待确认事项",
          partial
              ? "POST /user/registerUser、GET /user/logout：MODEL_NOT_EXPLAINED，尚未形成活动解释。"
              : "权限范围、会话有效期和每次操作是否实际完成需要结合运行时配置确认。",
          refsFor(partial ? List.of("E3", "E4") : List.of("E1", "E2", "E3", "E4")));
      return root;
    }

    private static void section(
        ArrayNode sections, int number, String title, String text, List<String> refs) {
      ObjectNode section = sections.addObject();
      section.put("number", number);
      section.put("title", title);
      ArrayNode paragraphRefs =
          section.putArray("paragraphs").addObject().put("text", text).putArray("refs");
      refs.forEach(paragraphRefs::add);
      section.putArray("items");
    }

    private static List<String> refsFor(List<String> entryKeys) {
      return ENTRIES.stream()
          .filter(entry -> entryKeys.contains(entry.localKey()))
          .map(EntryFixture::ref)
          .toList();
    }

    private static String activityName(String key) {
      return switch (key) {
        case "E1" -> "处理用户删除";
        case "E2" -> "获取当前会话用户信息";
        case "E3" -> "登记用户";
        case "E4" -> "清理当前会话";
        default -> throw new AssertionError("unexpected entry key: " + key);
      };
    }

    private static String activityPurpose(String key) {
      return switch (key) {
        case "E1" -> "将用户标识交给用户服务处理，并将处理结果包装后返回。";
        case "E2" -> "根据当前会话中的用户标识读取用户信息，并在返回前清除密码字段。";
        case "E3" -> "校验用户登记输入后调用用户登记服务。";
        case "E4" -> "清除当前会话中保存的用户和客户端标识。";
        default -> throw new AssertionError("unexpected entry key: " + key);
      };
    }

    private static String condition(String key) {
      return switch (key) {
        case "E1" -> "入口接收用户标识。";
        case "E2" -> "当前会话能够提供用户标识时读取用户信息；异常路径返回失败信息。";
        case "E3" -> "验证码和登录名校验通过后才调用登记服务。";
        case "E4" -> "清除会话信息发生异常时返回退出失败。";
        default -> throw new AssertionError("unexpected entry key: " + key);
      };
    }

    private static String activityStep(String key) {
      return switch (key) {
        case "E1" -> "调用用户删除服务并包装返回。";
        case "E2" -> "读取会话用户标识、查询用户并清除密码字段。";
        case "E3" -> "规范登录名、校验验证码和登录名后登记用户。";
        case "E4" -> "删除会话中的用户和客户端标识。";
        default -> throw new AssertionError("unexpected entry key: " + key);
      };
    }

    private static String codeResult(String key) {
      return switch (key) {
        case "E1" -> "系统返回用户删除处理的包装结果。";
        case "E2" -> "正常路径返回去除密码的用户信息；异常路径返回失败信息。";
        case "E3" -> "系统调用用户登记服务并返回标准成功对象。";
        case "E4" -> "系统尝试清除会话标识并返回退出处理结果。";
        default -> throw new AssertionError("unexpected entry key: " + key);
      };
    }

    private static String rule(String key) {
      return switch (key) {
        case "E1" -> "删除范围和权限校验未在当前片段中证明。";
        case "E2" -> "返回用户信息前将密码字段置空。";
        case "E3" -> "验证码和登录名校验先于用户登记服务调用。";
        case "E4" -> "会话中的用户和客户端标识均被清除。";
        default -> throw new AssertionError("unexpected entry key: " + key);
      };
    }

    private static String processName(String activityName) {
      return switch (activityName) {
        case "处理用户删除" -> "用户删除处理";
        case "获取当前会话用户信息" -> "会话用户信息读取";
        case "登记用户" -> "用户登记";
        case "清理当前会话" -> "会话退出处理";
        default -> throw new AssertionError("unexpected activity: " + activityName);
      };
    }

    private List<String> taskKinds() {
      return List.copyOf(taskKinds);
    }

    private JsonNode activityDraftResponse() {
      return activityDraftResponse;
    }

    private JsonNode activityReviewInput() {
      return activityReviewInput;
    }

    private JsonNode reportDraftInput() {
      return reportDraftInput;
    }
  }
}
