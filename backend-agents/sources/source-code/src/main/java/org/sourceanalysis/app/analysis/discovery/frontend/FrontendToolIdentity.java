package org.sourceanalysis.app.analysis.discovery.frontend;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Path-free content identity for the fixed frontend syntax toolchain. */
public record FrontendToolIdentity(
    Sha256Digest nodeExecutableSha256,
    Sha256Digest frameworkHelperSha256,
    Sha256Digest frameworkLockfileSha256) {

  private static final String FRAMEWORK_HOME_PROPERTY = "sourceanalysis.framework.home";
  private static final Path HELPER_RELATIVE_PATH =
      Path.of("tools", "frontend-syntax-helper", "main.cjs");
  private static final Path LOCKFILE_RELATIVE_PATH =
      Path.of("tools", "frontend-syntax-helper", "package-lock.json");

  public FrontendToolIdentity {
    Objects.requireNonNull(nodeExecutableSha256, "Node executable digest");
    Objects.requireNonNull(frameworkHelperSha256, "frontend syntax helper digest");
    Objects.requireNonNull(frameworkLockfileSha256, "frontend syntax lockfile digest");
  }

  /** Reads only the supplied files and retains their content identities, never their host paths. */
  public static FrontendToolIdentity fromFiles(
      Path nodeExecutable, Path frameworkHelper, Path frameworkLockfile) {
    return new FrontendToolIdentity(
        digest(nodeExecutable), digest(frameworkHelper), digest(frameworkLockfile));
  }

  /** Reads the repository-owned frontend helper and lockfile from the private framework home. */
  public static FrontendToolIdentity fromFrameworkFiles(Path nodeExecutable) {
    return fromFiles(nodeExecutable, frameworkHelperScript(), frameworkLockfile());
  }

  /** Returns the fixed executable helper path for the production one-shot Node adapter. */
  public static Path frameworkHelperScript() {
    return frameworkFile(HELPER_RELATIVE_PATH);
  }

  private static Path frameworkLockfile() {
    return frameworkFile(LOCKFILE_RELATIVE_PATH);
  }

  private static Sha256Digest digest(Path file) {
    Objects.requireNonNull(file, "frontend tool identity file");
    try {
      if (!Files.isRegularFile(file)) {
        throw new IOException("not a regular file");
      }
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (var input = Files.newInputStream(file)) {
        byte[] buffer = new byte[16 * 1024];
        int count;
        while ((count = input.read(buffer)) >= 0) {
          digest.update(buffer, 0, count);
        }
      }
      return Sha256Digest.parse(HexFormat.of().formatHex(digest.digest()));
    } catch (IOException unavailable) {
      throw new IllegalArgumentException("frontend tool identity file is unavailable", unavailable);
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private static Path frameworkFile(Path relativePath) {
    for (Path home : frameworkHomes()) {
      Path candidate = home.resolve(relativePath).toAbsolutePath().normalize();
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("repository-owned frontend syntax helper is unavailable");
  }

  private static List<Path> frameworkHomes() {
    String configuredHome = System.getProperty(FRAMEWORK_HOME_PROPERTY);
    if (configuredHome != null && !configuredHome.isBlank()) {
      return List.of(Path.of(configuredHome).toAbsolutePath().normalize());
    }
    List<Path> candidates = new ArrayList<>();
    try {
      java.security.ProtectionDomain protectionDomain =
          FrontendToolIdentity.class.getProtectionDomain();
      java.security.CodeSource codeSource =
          protectionDomain == null ? null : protectionDomain.getCodeSource();
      if (codeSource != null && codeSource.getLocation() != null) {
        URI location = codeSource.getLocation().toURI();
        Path current = Path.of(location).toAbsolutePath().normalize();
        if (!Files.isDirectory(current)) {
          current = current.getParent();
        }
        for (int depth = 0; current != null && depth < 4; depth++) {
          candidates.add(current);
          current = current.getParent();
        }
      }
    } catch (Exception ignored) {
      // The explicit unavailable failure below remains stable and does not consult caller CWD.
    }
    return List.copyOf(candidates);
  }
}
