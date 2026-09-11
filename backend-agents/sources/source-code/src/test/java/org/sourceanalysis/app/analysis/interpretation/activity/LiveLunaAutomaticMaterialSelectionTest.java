package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

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
}
