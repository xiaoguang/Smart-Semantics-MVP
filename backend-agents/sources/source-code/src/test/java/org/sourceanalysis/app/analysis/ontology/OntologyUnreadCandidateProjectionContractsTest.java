package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

final class OntologyUnreadCandidateProjectionContractsTest {
  @Test
  void allUnreadUsesQueriesAndLimitationsRoundTripWithoutTruncation() throws Exception {
    ArrayNode source = candidates(500);
    ObjectNode projected = project(source);
    assertThat(projected.path("unreadCandidates")).hasSize(2);
    assertThat(decode(projected, source)).isEqualTo(source);
    assertThat(source).hasSize(500);
    var json = new CanonicalJsonCodec();
    assertThat(json.encodeCanonical(projected).size())
        .isLessThan(json.encodeCanonical(source).size() / 2);
  }

  @Test
  void wrongPrivateDirectoryCannotBeSubstitutedForTheModelSummary() throws Exception {
    ArrayNode source = candidates(2);
    ObjectNode projected = project(source);
    ((ObjectNode) source.get(0)).put("unitRef", "U999");
    assertThatThrownBy(() -> decode(projected, source))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ONTOLOGY_UNREAD_CANDIDATES_INVALID");
  }

  private static ArrayNode candidates(int count) {
    ArrayNode rows = JsonNodeFactory.instance.arrayNode();
    for (int index = 0; index < count; index++) {
      ObjectNode row = rows.addObject();
      row.put("unitRef", "U" + (index + 1));
      row.put("entryRef", "E" + (index % 3 + 1));
      row.put("kind", index % 2 == 0 ? "JAVA_METHOD" : "FRONTEND_UNIT");
      row.put("matchKind", "LEXICAL_MATCH");
      row.put(
          "reason", "Same literal only; body unread and no field or business binding confirmed.");
      row.putArray("queries").add("originKey").add(index % 2 == 0 ? "id" : "number");
    }
    return rows;
  }

  private static ObjectNode project(ArrayNode source) throws Exception {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    invoke(
        "projectUnreadCandidates",
        new Class<?>[] {ObjectNode.class, JsonNode.class},
        result,
        source);
    return result;
  }

  private static JsonNode decode(ObjectNode model, JsonNode source) throws Exception {
    return (JsonNode)
        invoke(
            "decodeUnreadCandidates",
            new Class<?>[] {JsonNode.class, JsonNode.class},
            model,
            source);
  }

  private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
    Method method = OntologyModelProjection.class.getDeclaredMethod(name, types);
    method.setAccessible(true);
    try {
      return method.invoke(null, args);
    } catch (InvocationTargetException failed) {
      if (failed.getCause() instanceof Exception cause) throw cause;
      throw failed;
    }
  }
}
