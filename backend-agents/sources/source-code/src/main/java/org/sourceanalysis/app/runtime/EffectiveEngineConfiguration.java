package org.sourceanalysis.app.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The immutable engine configuration fixed once at the application composition root. */
public record EffectiveEngineConfiguration(String javaEngine, JdtConfiguration jdt) {

  public static final String JDT = "jdt";
  public static final String JAVAPARSER = "javaparser";

  public EffectiveEngineConfiguration {
    if (!JDT.equals(javaEngine) && !JAVAPARSER.equals(javaEngine)) {
      throw new IllegalArgumentException("Java engine must be jdt or javaparser");
    }
    if (JDT.equals(javaEngine)) {
      jdt = Objects.requireNonNull(jdt, "JDT configuration");
    } else if (jdt != null) {
      throw new IllegalArgumentException(
          "an unselected JavaParser engine must not open JDT tooling");
    }
  }

  /**
   * Local JDT distribution and tool-JDK values, eagerly canonicalized and validated even when
   * constructed directly rather than through the YAML loader.
   */
  public static final class JdtConfiguration {

    private static final Duration VERSION_PROBE_TIMEOUT = Duration.ofSeconds(5);
    private static final Pattern JAVA_MAJOR_VERSION =
        Pattern.compile(
            "(?:java|openjdk) version \\\"([0-9]+)(?:[.\\\"]|$)", Pattern.CASE_INSENSITIVE);

    private final Path installation;
    private final Path javaHome;
    private final Duration startupTimeout;
    private final Duration queryTimeout;
    private final Duration shutdownTimeout;
    private final String javaVersion;
    private final String distributionIdentity;

    public JdtConfiguration(
        Path installation,
        Path javaHome,
        Duration startupTimeout,
        Duration queryTimeout,
        Duration shutdownTimeout) {
      this.installation = canonicalDirectory(installation, "JDT installation");
      this.javaHome = canonicalDirectory(javaHome, "JDT Java home");
      this.startupTimeout = positive(startupTimeout, "JDT startup timeout");
      this.queryTimeout = positive(queryTimeout, "JDT query timeout");
      this.shutdownTimeout = positive(shutdownTimeout, "JDT shutdown timeout");
      DistributionComponents distribution = validateDistribution(this.installation);
      javaVersion = validateJavaHome(this.javaHome);
      distributionIdentity = distributionIdentity(distribution);
    }

    public Path installation() {
      return installation;
    }

    public Path javaHome() {
      return javaHome;
    }

    public Duration startupTimeout() {
      return startupTimeout;
    }

    public Duration queryTimeout() {
      return queryTimeout;
    }

    public Duration shutdownTimeout() {
      return shutdownTimeout;
    }

    public String javaVersion() {
      return javaVersion;
    }

    public String distributionIdentity() {
      return distributionIdentity;
    }

    /** The one validated Equinox launcher owned by this configured JDT distribution. */
    public Path launcherJar() {
      try {
        return launcherJars(installation).get(0);
      } catch (IOException failure) {
        throw new IllegalStateException("configured JDT launcher cannot be read", failure);
      }
    }

    /** The platform configuration directory verified with this distribution. */
    public Path platformConfiguration() {
      return installation.resolve(platformConfigurationName());
    }

    private static Path canonicalDirectory(Path value, String label) {
      try {
        Path path = Objects.requireNonNull(value, label);
        if (!path.isAbsolute()) {
          throw new IllegalArgumentException(label + " must be an absolute path");
        }
        Path canonical = path.toRealPath();
        if (!Files.isDirectory(canonical)) {
          throw new IllegalArgumentException(label + " must be a directory");
        }
        return canonical;
      } catch (IOException failure) {
        throw new IllegalArgumentException(label + " cannot be resolved", failure);
      }
    }

    private static Duration positive(Duration value, String label) {
      if (value == null || value.isZero() || value.isNegative()) {
        throw new IllegalArgumentException(label + " must be positive");
      }
      return value;
    }

    private static DistributionComponents validateDistribution(Path installation) {
      try {
        List<Path> launchers = launcherJars(installation);
        if (launchers.size() != 1) {
          throw new IllegalArgumentException(
              "JDT installation must contain exactly one Equinox launcher");
        }
        if (!Files.isDirectory(installation.resolve(platformConfigurationName()))) {
          throw new IllegalArgumentException(
              "JDT installation is missing its platform configuration directory");
        }
        Path languageServerCore = oneBundle(installation, "org.eclipse.jdt.ls.core_");
        Path jdtCore = oneBundle(installation, "org.eclipse.jdt.core_");
        return new DistributionComponents(launchers.get(0), languageServerCore, jdtCore);
      } catch (IOException failure) {
        throw new IllegalArgumentException("JDT installation cannot be inspected", failure);
      }
    }

    private static Path oneBundle(Path installation, String prefix) throws IOException {
      Path plugins = installation.resolve("plugins");
      List<Path> bundles;
      try (var paths = Files.list(plugins)) {
        bundles =
            paths
                .filter(path -> path.getFileName().toString().startsWith(prefix))
                .filter(path -> path.getFileName().toString().endsWith(".jar"))
                .sorted()
                .toList();
      }
      if (bundles.size() != 1) {
        throw new IllegalArgumentException(
            "JDT installation must contain exactly one " + prefix + " bundle");
      }
      return bundles.get(0);
    }

    private static List<Path> launcherJars(Path installation) throws IOException {
      Path plugins = installation.resolve("plugins");
      if (!Files.isDirectory(plugins)) {
        return List.of();
      }
      try (var paths = Files.list(plugins)) {
        return paths
            .filter(
                path -> path.getFileName().toString().startsWith("org.eclipse.equinox.launcher_"))
            .filter(path -> path.getFileName().toString().endsWith(".jar"))
            .sorted()
            .toList();
      }
    }

    private static String validateJavaHome(Path javaHome) {
      Path executable = javaHome.resolve("bin").resolve("java");
      if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
        throw new IllegalArgumentException("JDT Java home must contain an executable bin/java");
      }
      String versionOutput = javaVersion(executable);
      Matcher matcher = JAVA_MAJOR_VERSION.matcher(versionOutput);
      if (!matcher.find()) {
        throw new IllegalArgumentException("JDT Java version probe did not report a major version");
      }
      int major;
      try {
        major = Integer.parseInt(matcher.group(1));
      } catch (NumberFormatException invalid) {
        throw new IllegalArgumentException(
            "JDT Java version probe reported an invalid major version", invalid);
      }
      if (major < 21) {
        throw new IllegalArgumentException("JDT language server requires Java 21 or newer");
      }
      return versionOutput;
    }

    private static String javaVersion(Path executable) {
      Process process;
      try {
        process =
            new ProcessBuilder(executable.toString(), "-version").redirectErrorStream(true).start();
      } catch (IOException failure) {
        throw new IllegalArgumentException("JDT Java version probe could not start", failure);
      }
      try {
        if (!process.waitFor(VERSION_PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
          process.destroyForcibly();
          throw new IllegalArgumentException("JDT Java version probe timed out");
        }
        byte[] bytes = process.getInputStream().readNBytes(8 * 1024);
        if (process.exitValue() != 0) {
          throw new IllegalArgumentException("JDT Java version probe exited unsuccessfully");
        }
        return new String(bytes, StandardCharsets.UTF_8).strip();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        process.destroyForcibly();
        throw new IllegalArgumentException("JDT Java version probe was interrupted", interrupted);
      } catch (IOException failure) {
        process.destroyForcibly();
        throw new IllegalArgumentException(
            "JDT Java version probe output could not be read", failure);
      }
    }

    private static String distributionIdentity(DistributionComponents distribution) {
      try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Path component :
            List.of(
                distribution.launcher(),
                distribution.languageServerCore(),
                distribution.jdtCore())) {
          digest.update(component.getFileName().toString().getBytes(StandardCharsets.UTF_8));
          digest.update((byte) 0);
          try (var input = Files.newInputStream(component)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
              digest.update(buffer, 0, count);
            }
          }
          digest.update((byte) 0);
        }
        return "jdtls-distribution["
            + distribution.languageServerCore().getFileName()
            + ","
            + distribution.jdtCore().getFileName()
            + "]@"
            + java.util.HexFormat.of().formatHex(digest.digest());
      } catch (IOException failure) {
        throw new IllegalArgumentException("JDT distribution identity cannot be read", failure);
      } catch (java.security.NoSuchAlgorithmException impossible) {
        throw new IllegalStateException(impossible);
      }
    }

    private record DistributionComponents(Path launcher, Path languageServerCore, Path jdtCore) {}

    private static String platformConfigurationName() {
      String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
      if (os.contains("mac")) {
        return "config_mac";
      }
      if (os.contains("win")) {
        return "config_win";
      }
      return "config_linux";
    }
  }
}
