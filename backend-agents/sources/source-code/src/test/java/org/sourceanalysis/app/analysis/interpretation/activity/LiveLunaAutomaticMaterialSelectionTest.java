package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

class LiveLunaAutomaticMaterialSelectionTest {

  private static final String USER_ACCOUNT_GROUP_MATERIAL_ID =
      "material:8be00d5562743218931b721c547d915076a08b7200bc06e415d1248c5ea663eb";
  private static final List<String> USER_ACCOUNT_GROUP_ENTRY_IDS =
      List.of(
          "entry:160b90d56d87df77d8ad02aadb61130b138af016d31edde06f30e517f4497ece",
          "entry:2d16e9a0ceb9b9555823ff3b091485941d4e26fb0b8e75b0314fdedd837722bc",
          "entry:2d573f55b3164ae226957503381cb4c22104e263ba0e36f316cd1ad609302439",
          "entry:3bc9f42e69961211dec7e48baeffbb0bd61a4c4be03280eb02894f86b6e9b144");
  private static final List<String> USER_ACCOUNT_GROUP_REFS =
      List.of("S487", "S722", "S731", "S898");

  @TempDir Path temporaryDirectory;

  @Test
  void selectsTheNamedAutomaticUserRegistrationPacketWithoutStartingAProvider() throws Exception {
    Path materials = temporaryDirectory.resolve("business-materials.jsonl");
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    Files.writeString(
        materials,
        String.join(
            System.lineSeparator(),
            material(canonicalJson, "material:depot", "HTTP POST /depotHead/batchSetStatus"),
            material(canonicalJson, "material:user-registration", "HTTP POST /user/registerUser"),
            material(canonicalJson, "material:user-login", "HTTP POST /user/login"),
            material(
                canonicalJson,
                "material:serial-number",
                List.of("entry:serial-list", "entry:serial-batch-add"),
                "HTTP POST /serialNumber/getEnableSerialNumberList\n入口 E2：HTTP POST /serialNumber/batAddSerialNumber"),
            material(
                canonicalJson,
                "material:account-balance",
                List.of("entry:statistics", "entry:balance"),
                "HTTP GET /account/getStatistics\n入口 E2：HTTP GET /account/listWithBalance")));

    BusinessMaterial selected =
        LiveLunaAutomaticMaterialIT.automaticMaterial(materials, "automatic-user-registration");

    assertThat(selected.materialId()).isEqualTo("material:user-registration");
    assertThat(selected.modelPacket().technicalObservations())
        .containsExactly("源码调用：userService.registerUser");

    BusinessMaterial login =
        LiveLunaAutomaticMaterialIT.automaticMaterial(materials, "automatic-user-login");

    assertThat(login.materialId()).isEqualTo("material:user-login");

    BusinessMaterial accountBalance;
    try {
      accountBalance =
          LiveLunaAutomaticMaterialIT.automaticMaterial(
              materials, "automatic-account-balance-group");
    } catch (IllegalArgumentException failure) {
      fail("a named multi-entry packet must be selectable", failure);
      return;
    }

    assertThat(accountBalance.materialId()).isEqualTo("material:account-balance");
    assertThat(accountBalance.entryIds()).containsExactly("entry:statistics", "entry:balance");

    BusinessMaterial serialNumber =
        LiveLunaAutomaticMaterialIT.automaticMaterial(materials, "automatic-serial-number");

    assertThat(serialNumber.materialId()).isEqualTo("material:serial-number");
    assertThat(serialNumber.entryIds())
        .containsExactly("entry:serial-list", "entry:serial-batch-add");
  }

  @Test
  void selectsOnlyTheExactFrozenFourEntryUserAccountGroup() throws Exception {
    Path materials = temporaryDirectory.resolve("business-materials.jsonl");
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    Files.writeString(materials, exactFourEntryMaterial(canonicalJson));

    BusinessMaterial selected =
        LiveLunaAutomaticMaterialIT.automaticMaterial(materials, "automatic-user-account-group");

    assertThat(selected.materialId()).isEqualTo(USER_ACCOUNT_GROUP_MATERIAL_ID);
    assertThat(selected.entryIds()).containsExactlyElementsOf(USER_ACCOUNT_GROUP_ENTRY_IDS);
    assertThat(selected.sourceRefs())
        .extracting(value -> value.ref())
        .containsExactlyElementsOf(USER_ACCOUNT_GROUP_REFS);
    assertThat(selected.modelPacket().allowlistedRefs())
        .extracting(value -> value.ref())
        .containsExactlyElementsOf(USER_ACCOUNT_GROUP_REFS);
  }

  private static String material(
      CanonicalJsonCodec canonicalJson, String materialId, String observation) {
    return material(canonicalJson, materialId, List.of("entry:one"), observation);
  }

  private static String material(
      CanonicalJsonCodec canonicalJson,
      String materialId,
      List<String> entryIds,
      String observation) {
    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("recordType", "BUSINESS_MATERIAL");
    material.put("materialId", materialId);
    entryIds.forEach(material.putArray("entryIds")::add);
    material.put("materialMode", "FLOW_PREFERRED");
    material.put("context", "已发现入口 " + observation + " 对应技术流程。");
    material.putArray("technicalObservations").add("源码调用：userService.registerUser");
    ObjectNode sourceReference = material.putArray("sourceRefs").addObject();
    sourceReference.put("ref", "S1");
    sourceReference.put("file", "Source.java");
    sourceReference.put("startLine", 1);
    sourceReference.put("endLine", 1);
    sourceReference.put("snippet", "source");
    material.putArray("flowRefs").add("flow:one");
    material.putArray("technicalProofRefs");
    material.putArray("limitations");
    ObjectNode packet = material.putObject("modelPacket");
    packet.put("context", "已发现 " + observation + "。请解释局部业务活动。");
    packet.putArray("technicalObservations").add("源码调用：userService.registerUser");
    ObjectNode allowed = packet.putArray("allowlistedRefs").addObject();
    allowed.put("ref", "S1");
    allowed.put("snippet", "source");
    packet.putArray("limitations");
    return new String(
        canonicalJson.encodeCanonical(material).copyToByteArray(), StandardCharsets.UTF_8);
  }

  private static String exactFourEntryMaterial(CanonicalJsonCodec canonicalJson) {
    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("recordType", "BUSINESS_MATERIAL");
    material.put("materialId", USER_ACCOUNT_GROUP_MATERIAL_ID);
    USER_ACCOUNT_GROUP_ENTRY_IDS.forEach(material.putArray("entryIds")::add);
    material.put("materialMode", "FLOW_PREFERRED");
    material.put("context", "本包包含四个已冻结用户账户 HTTP 入口。");
    material.putArray("technicalObservations").add("已定位四个入口处理方法。");
    ObjectNode packet = material.putObject("modelPacket");
    packet.put("context", "入口 E1、E2、E3、E4 分别对应四个已冻结用户账户入口。");
    packet.putArray("technicalObservations").add("仅解释每个入口的局部活动。");
    ArrayNode packetRefs = packet.putArray("allowlistedRefs");
    ArrayNode sourceRefs = material.putArray("sourceRefs");
    for (String ref : USER_ACCOUNT_GROUP_REFS) {
      packetRefs.addObject().put("ref", ref).put("snippet", "frozen snippet " + ref);
      sourceRefs
          .addObject()
          .put("ref", ref)
          .put("file", "UserController.java")
          .put("startLine", 1)
          .put("endLine", 1)
          .put("snippet", "frozen snippet " + ref);
    }
    packet.putArray("limitations");
    material.putArray("flowRefs");
    material.putArray("technicalProofRefs");
    material.putArray("limitations");
    return new String(
        canonicalJson.encodeCanonical(material).copyToByteArray(), StandardCharsets.UTF_8);
  }
}
