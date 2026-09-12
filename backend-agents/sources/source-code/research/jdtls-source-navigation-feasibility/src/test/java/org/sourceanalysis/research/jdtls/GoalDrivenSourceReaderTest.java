package org.sourceanalysis.research.jdtls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class GoalDrivenSourceReaderTest {

  @Test
  void expandsAJdtAnchorToTheCompleteMethodWithFormalsAndLexicalCalls() throws Exception {
    String source =
        "package sample;\n"
            + "class Registration {\n"
            + "  @Deprecated\n"
            + "  String register(String login, int... roleIds) {\n"
            + "    if (login.isBlank()) { throw new IllegalArgumentException(); }\n"
            + "    save(login, roleIds);\n"
            + "    return login;\n"
            + "  }\n"
            + "}\n";

    Class<?> reader = Class.forName("org.sourceanalysis.research.jdtls.GoalDrivenSourceReader");
    Method read = reader.getDeclaredMethod("read", String.class, String.class, int.class, int.class);
    JsonNode result = (JsonNode) read.invoke(null, source, "sample/Registration.java", 3, 12);

    assertEquals("sample/Registration.java", result.path("path").asText());
    assertEquals("register", result.path("name").asText());
    assertEquals(3, result.path("range").path("startLine").asInt());
    assertEquals(8, result.path("range").path("endLine").asInt());
    assertEquals("login", result.path("formalParameters").get(0).path("name").asText());
    assertEquals("roleIds", result.path("formalParameters").get(1).path("name").asText());
    assertTrue(result.path("formalParameters").get(1).path("varArgs").asBoolean());
    assertEquals(3, result.path("calls").size());
    JsonNode save =
        java.util.stream.StreamSupport.stream(result.path("calls").spliterator(), false)
            .filter(call -> call.path("text").asText().startsWith("save("))
            .findFirst()
            .orElseThrow();
    assertEquals(6, save.path("navigationRange").path("startLine").asInt());
    assertEquals(5, save.path("navigationRange").path("startColumn").asInt());
    assertEquals(8, save.path("navigationRange").path("endColumn").asInt());
    assertTrue(result.path("snippet").asText().contains("if (login.isBlank())"));
    assertTrue(result.path("snippet").asText().contains("return login;"));
  }

  @Test
  void expandsAnApprovedEntryLineSpanWithoutNeedingAJdtDocumentSymbol() throws Exception {
    String source =
        "class Controller {\n"
            + "  /** documentation */\n"
            + "  String entry(String id) {\n"
            + "    return service(id);\n"
            + "  }\n"
            + "}\n";

    Class<?> reader = Class.forName("org.sourceanalysis.research.jdtls.GoalDrivenSourceReader");
    Method read =
        reader.getDeclaredMethod("readIntersecting", String.class, String.class, int.class, int.class);
    JsonNode result = (JsonNode) read.invoke(null, source, "Controller.java", 1, 4);

    assertEquals("entry", result.path("name").asText());
    assertEquals(3, result.path("range").path("startLine").asInt());
    assertEquals(5, result.path("range").path("endLine").asInt());
    assertTrue(result.path("snippet").asText().contains("return service(id);"));
  }

  @Test
  void expandsAJdtFullRangeAnchorThatStartsInTheMethodJavadoc() throws Exception {
    String source =
        "class Service {\n"
            + "  /** JDT full method ranges may include this documentation. */\n"
            + "  String validate(String code) {\n"
            + "    return code.trim();\n"
            + "  }\n"
            + "}\n";

    Class<?> reader = Class.forName("org.sourceanalysis.research.jdtls.GoalDrivenSourceReader");
    Method read = reader.getDeclaredMethod("read", String.class, String.class, int.class, int.class);
    JsonNode result =
        assertDoesNotThrow(
            () -> (JsonNode) read.invoke(null, source, "Service.java", 1, 2));

    assertEquals("validate", result.path("name").asText());
    assertTrue(result.path("snippet").asText().contains("return code.trim();"));
  }
}
