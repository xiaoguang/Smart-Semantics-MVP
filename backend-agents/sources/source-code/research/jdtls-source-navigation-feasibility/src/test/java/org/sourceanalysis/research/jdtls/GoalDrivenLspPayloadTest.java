package org.sourceanalysis.research.jdtls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoalDrivenLspPayloadTest {

  @Test
  void unwrapsTheLsp4jEitherLocationPayloadBeforeResolvingItsTarget() throws Exception {
    JsonNode payload =
        new ObjectMapper()
            .readTree(
                "{\"left\":[{\"uri\":\"file:///projection/UserService.java\","
                    + "\"range\":{\"start\":{\"line\":607,\"character\":16},"
                    + "\"end\":{\"line\":607,\"character\":28}}}],\"right\":null}");

    Class<?> experiment = Class.forName("org.sourceanalysis.research.jdtls.GoalDrivenExperiment");
    Method locations = experiment.getDeclaredMethod("locationValues", JsonNode.class);
    locations.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<JsonNode> values = (List<JsonNode>) locations.invoke(null, payload);

    assertEquals(1, values.size());
    assertEquals("file:///projection/UserService.java", values.get(0).path("uri").asText());
    assertTrue(values.get(0).path("range").isObject());
  }
}
