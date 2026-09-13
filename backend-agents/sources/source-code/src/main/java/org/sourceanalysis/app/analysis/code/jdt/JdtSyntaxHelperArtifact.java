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
      java.security.ProtectionDomain protectionDomain =
          JdtSyntaxHelperArtifact.class.getProtectionDomain();
      java.security.CodeSource codeSource =
          protectionDomain == null ? null : protectionDomain.getCodeSource();
      if (codeSource != null && codeSource.getLocation() != null) {
        URI location = codeSource.getLocation().toURI();
        Path host = Path.of(location).toAbsolutePath().normalize();
        Path base = Files.isDirectory(host) ? host : host.getParent();
        if (base != null) {
          candidates.add(base.resolve(FILE_NAME));
          Path parent = base.getParent();
          if (parent != null) {
            candidates.add(parent.resolve("tools/jdt-syntax-helper/target").resolve(FILE_NAME));
          }
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
