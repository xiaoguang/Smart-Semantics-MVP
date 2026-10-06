package org.sourceanalysis.app.adapter.provider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Isolated child-JVM probe for bounding a large stdin write to a non-reading Codex process. */
public final class CodexSubscriptionDeadlineProbe {

  private static final int PROMPT_BYTES = 4 * 1024 * 1024;

  private CodexSubscriptionDeadlineProbe() {}

  public static void main(String[] arguments) throws Exception {
    if (arguments.length != 2) {
      throw new IllegalArgumentException("expected fake executable and outcome path");
    }
    Path executable = Path.of(arguments[0]);
    Path outcome = Path.of(arguments[1]);
    String prompt = "p".repeat(PROMPT_BYTES);
    ImmutableBytes schema =
        ImmutableBytes.copyOf("{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8));
    CodexSubscriptionProfile profile =
        new CodexSubscriptionProfile(executable, "gpt-5.6-terra", "xhigh", Duration.ofMillis(250));
    long started = System.nanoTime();
    try {
      new ProcessCodexSubscriptionCommand().execute(profile, prompt, schema);
      Files.writeString(outcome, "RETURNED", StandardCharsets.UTF_8);
      System.exit(2);
    } catch (StructuredModelProviderFailure failure) {
      long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
      Files.writeString(
          outcome,
          failure.reasonCode()
              + "\t"
              + failure.requestStarted()
              + "\t"
              + failure.requestEnded()
              + "\t"
              + failure.getMessage()
              + "\t"
              + elapsedMillis,
          StandardCharsets.UTF_8);
    }
  }
}
