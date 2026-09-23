package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

class ActivityRetryYamlConfigurationTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void loadsActivityRetryAndKeepsItInTheNonSecretConfiguration() throws Exception {
    ObjectNode config = config();
    config.set(
        "activityRetry",
        JSON.readTree(
            """
            {"maxAttempts":1,"initialBackoffMillis":0,"maxBackoffMillis":0,
             "multiplier":1,"jitterRatio":0,"retryableReasons":["REQUEST_TIMEOUT"],
             "stageOverrides":{"REVIEW":{"maxAttempts":2}}}
            """));

    ModelJobsConfiguration loaded = ModelJobsConfiguration.load(config, new CanonicalJsonCodec());

    assertThat(loaded.activityRetry().maxAttempts("DRAFT")).isEqualTo(1);
    assertThat(loaded.activityRetry().maxAttempts("REVIEW")).isEqualTo(2);
    assertThat(
            loaded.normalizedNonSecretDocument().path("activityRetry").path("maxAttempts").asInt())
        .isEqualTo(1);
  }

  @Test
  void rejectsUnknownRetryStageBeforeExecution() throws Exception {
    ObjectNode config = config();
    config.set(
        "activityRetry",
        JSON.readTree("{" + "\"stageOverrides\":{\"WRITE\":{\"maxAttempts\":2}}}"));

    assertThatThrownBy(() -> ModelJobsConfiguration.load(config, new CanonicalJsonCodec()))
        .isInstanceOf(RuntimeException.class);
  }

  private static ObjectNode config() throws Exception {
    return (ObjectNode)
        JSON.readTree(
            """
            {"providers":{"pro":{"kind":"codexSubscription","quotaScope":"pro-account",
              "model":"gpt-5.6-terra","reasoningEffort":"xhigh",
              "auth":{"mode":"chatgpt","codexHomeEnv":"SOURCE_ANALYSIS_PRO_HOME"}}},
             "routing":{"activity":["pro"],"processGroup":["pro"],
               "repositorySummary":["pro"],"report":["pro"]}}
            """);
  }
}
