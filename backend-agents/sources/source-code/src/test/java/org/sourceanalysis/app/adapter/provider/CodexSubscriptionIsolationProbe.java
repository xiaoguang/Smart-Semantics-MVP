package org.sourceanalysis.app.adapter.provider;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/**
 * Child-JVM probe that gives the production process boundary a controlled inherited environment.
 */
public final class CodexSubscriptionIsolationProbe {

  private CodexSubscriptionIsolationProbe() {}

  public static void main(String[] arguments) throws Exception {
    if (arguments.length != 3) {
      throw new IllegalArgumentException("expected executable, Codex home, and marker");
    }
    Path executable = Path.of(arguments[0]);
    Path codexHome = Path.of(arguments[1]);
    Path marker = Path.of(arguments[2]);
    Constructor<CodexSubscriptionProfile> constructor =
        CodexSubscriptionProfile.class.getConstructor(
            Path.class, Path.class, String.class, String.class, Duration.class);
    CodexSubscriptionProfile profile =
        constructor.newInstance(
            executable, codexHome, "gpt-5.6-luna", "high", Duration.ofSeconds(5));
    ProcessCodexSubscriptionCommand command = new ProcessCodexSubscriptionCommand();
    command.preflight(profile);
    command.execute(
        profile,
        "structured output only",
        ImmutableBytes.copyOf("{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8)));
    Files.writeString(marker, "PASS", StandardCharsets.UTF_8);
  }
}
