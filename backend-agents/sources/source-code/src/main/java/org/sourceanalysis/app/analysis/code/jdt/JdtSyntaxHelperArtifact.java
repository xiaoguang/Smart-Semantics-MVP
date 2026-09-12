package org.sourceanalysis.app.analysis.code.jdt;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.sourceanalysis.app.analysis.code.CodeEngineException;

/** Locates the separately built same-release JDT Core helper without consulting customer source. */
final class JdtSyntaxHelperArtifact {

  private static final String FILE_NAME = "source-code-analysis-jdt-syntax-helper.jar";

  private JdtSyntaxHelperArtifact() {}

  static Path locate() {
    List<Path> candidates = new ArrayList<>();
    candidates.add(Path.of("tools", "jdt-syntax-helper", "target", FILE_NAME));
    try {
      URI codeSource =
          JdtSyntaxHelperArtifact.class.getProtectionDomain().getCodeSource().getLocation().toURI();
      Path host = Path.of(codeSource).toAbsolutePath().normalize();
      Path base = Files.isDirectory(host) ? host : host.getParent();
      if (base != null) {
        candidates.add(base.resolve(FILE_NAME));
        if (base.getParent() != null) {
          candidates.add(
              base.getParent().resolve("tools/jdt-syntax-helper/target").resolve(FILE_NAME));
        }
      }
    } catch (Exception ignored) {
      // The explicit failure below remains stable and does not hide a usable candidate.
    }
    for (Path candidate : candidates) {
      Path absolute = candidate.toAbsolutePath().normalize();
      if (Files.isRegularFile(absolute)) {
        return absolute;
      }
    }
    throw new CodeEngineException(
        CodeEngineException.JDT_TOOL_UNAVAILABLE,
        "the same-release JDT syntax helper artifact is unavailable");
  }
}
