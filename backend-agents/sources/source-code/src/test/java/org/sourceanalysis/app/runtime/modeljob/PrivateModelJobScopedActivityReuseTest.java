package org.sourceanalysis.app.runtime.modeljob;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.AnalysisRunId;

class PrivateModelJobScopedActivityReuseTest {

  @Test
  void reopensOnlyCompleteScopedActivityForTheSameInputAndModel(@TempDir Path temporary) {
    AnalysisRunId run = AnalysisRunId.parse("analysis-run:" + "a".repeat(64));
    PrivateModelJobResultStore store = new PrivateModelJobResultStore(temporary, run, "activity");
    ModelRuntimeIdentityV1 runtime =
        new ModelRuntimeIdentityV1("scripted", "scope-model", "xhigh", "read-only");
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("schemaVersion", "activity-packet-result-v1");
    result.put("status", "COMPLETED");
    result.put("inputFingerprint", "b".repeat(64));
    result.put("quotaScope", "pro-account");
    result.put("providerBindingKey", "pro");
    result.put("pipeline", "activity-reading-plan-slices-v1");
    result.put("readingPlan", "decision-result.json");
    ObjectNode identity = result.putObject("runtimeIdentity");
    identity.put("upstreamProvider", runtime.upstreamProvider());
    identity.put("model", runtime.model());
    identity.put("reasoningEffort", runtime.reasoningEffort());
    identity.put("sandbox", runtime.sandbox());
    result.putArray("reviewedActivities");
    result.putArray("coverage");
    result.putArray("unexplainedActivityEntries");
    store.write("c".repeat(64), result);

    assertThat(store.readCompletedActivity("c".repeat(64), "b".repeat(64), "pro-account", runtime))
        .isPresent();
    assertThat(store.readCompletedActivity("c".repeat(64), "d".repeat(64), "pro-account", runtime))
        .isEmpty();
    assertThat(store.readCompleted("c".repeat(64), "b".repeat(64), "pro-account", runtime))
        .isEmpty();
  }
}
