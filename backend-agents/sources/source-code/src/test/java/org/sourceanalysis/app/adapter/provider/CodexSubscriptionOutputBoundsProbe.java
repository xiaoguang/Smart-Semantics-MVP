package org.sourceanalysis.app.adapter.provider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Isolated low-heap probe for a sparse response larger than the formal private-output bound. */
public final class CodexSubscriptionOutputBoundsProbe {

  private CodexSubscriptionOutputBoundsProbe() {}

  public static void main(String[] arguments) throws Exception {
    if (arguments.length != 2) {
      throw new IllegalArgumentException("expected fake executable and outcome path");
    }
    Path executable = Path.of(arguments[0]);
    Path outcome = Path.of(arguments[1]);
    CodexSubscriptionProfile profile =
        new CodexSubscriptionProfile(executable, "gpt-5.6-terra", "xhigh", Duration.ofSeconds(8));
    StructuredModelRequest request =
        new StructuredModelRequest(
            "oversized-output-task",
            "ONTOLOGY_OBJECT_EXTRACT",
            "Return the bounded probe response.",
            ImmutableBytes.copyOf("{}".getBytes(StandardCharsets.UTF_8)),
            ImmutableBytes.copyOf("{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8)),
            1_024,
            8);
    try {
      new CodexSubscriptionStructuredProvider(profile).generate(request);
      Files.writeString(outcome, "RETURNED", StandardCharsets.UTF_8);
    } catch (StructuredModelProviderFailure failure) {
      Files.writeString(
          outcome,
          "FAILURE\t"
              + failure.reasonCode()
              + "\t"
              + failure.requestStarted()
              + "\t"
              + failure.requestEnded()
              + "\t"
              + (failure.rawResponse().isEmpty() ? "NO_RAW" : "RAW_PRESENT"),
          StandardCharsets.UTF_8);
    } catch (OutOfMemoryError exhaustedHeap) {
      System.gc();
      Files.writeString(outcome, "OUT_OF_MEMORY", StandardCharsets.UTF_8);
    }
  }
}
