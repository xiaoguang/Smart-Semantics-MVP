package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.code.VerifiedJavaProject;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** RED contract tests for strict YAML engine selection and effective tool paths. */
class EngineConfigurationLoaderTest {

  @TempDir Path temporaryDirectory;

  @Test
  void acceptsOnlyTheCaseSensitiveJdtValue() throws Exception {
    ToolFixture tools = executableJdtFixture();

    Object jdt =
        EngineTestReflection.loadYaml(jdtYaml("jdt", tools.installation(), tools.javaHome()));
    assertThat(EngineTestReflection.stringProperty(jdt, "javaEngine", "engine")).isEqualTo("jdt");

    Path unselectedInstallation = temporaryDirectory.resolve("missing-jdt-installation");
    Path unselectedJavaHome = temporaryDirectory.resolve("missing-jdk");
    Throwable failure =
        EngineTestReflection.loadFailure(
            jdtYaml("javaparser", unselectedInstallation, unselectedJavaHome));
    assertThat(failure).isNotNull();
    assertThat(EngineTestReflection.codeOf(failure)).isEqualTo("ENGINE_CONFIGURATION_INVALID");
    assertThat(Files.exists(unselectedInstallation)).isFalse();
    assertThat(Files.exists(unselectedJavaHome)).isFalse();
  }

  @Test
  void rejectsAMissingEngineValueBeforeAnyToolPathIsOpened() throws Exception {
    assertConfigurationInvalid(
        yaml(
            "sourceAnalysis:\n"
                + "  jdt:\n"
                + "    installation: '/missing/install'\n"
                + "    javaHome: '/missing/jdk'\n"));
  }

  @Test
  void rejectsUnknownEngineValuesInsteadOfTreatingThemAsAuto() throws Exception {
    assertConfigurationInvalid(
        jdtYaml(
            "auto",
            temporaryDirectory.resolve("missing-install"),
            temporaryDirectory.resolve("missing-jdk")));
  }

  @Test
  void rejectsDuplicateEngineKeys() throws Exception {
    assertConfigurationInvalid(
        yaml("sourceAnalysis:\n" + "  javaEngine: jdt\n" + "  javaEngine: javaparser\n"));
  }

  @Test
  void rejectsUnknownRootAndNestedConfigurationKeys() throws Exception {
    assertConfigurationInvalid(
        yaml("unexpected: true\nsourceAnalysis:\n  javaEngine: javaparser\n"));
    assertConfigurationInvalid(
        yaml("sourceAnalysis:\n  javaEngine: javaparser\n  unexpected: true\n"));
  }

  @Test
  void requiresBothJdtToolPathsWhenJdtIsSelected() throws Exception {
    ToolFixture tools = executableJdtFixture();

    assertConfigurationInvalid(
        jdtYaml("jdt", temporaryDirectory.resolve("missing-install"), tools.javaHome()));
    assertConfigurationInvalid(
        yaml(
            "sourceAnalysis:\n"
                + "  javaEngine: jdt\n"
                + "  jdt:\n"
                + "    installation: '"
                + quote(tools.installation().toString())
                + "'\n"));
  }

  @Test
  void requiresTheConfiguredJavaExecutableToBePresentAndExecutable() throws Exception {
    Path installation = Files.createDirectories(temporaryDirectory.resolve("jdtls"));
    Path javaHome = Files.createDirectories(temporaryDirectory.resolve("jdk"));
    Files.createDirectories(javaHome.resolve("bin"));
    Path java = Files.writeString(javaHome.resolve("bin/java"), "not-an-executable-java\n");
    assertThat(Files.isExecutable(java)).isFalse();

    assertConfigurationInvalid(jdtYaml("jdt", installation, javaHome));
  }

  @Test
  void rejectsJdtInstallationWithoutExactlyOneEquinoxLauncher() throws Exception {
    ToolFixture tools = executableJdtFixture();
    Files.delete(tools.installation().resolve("plugins/org.eclipse.equinox.launcher_1.0.jar"));

    assertConfigurationInvalid(jdtYaml("jdt", tools.installation(), tools.javaHome()));
  }

  @Test
  void rejectsJdtInstallationWithoutThePlatformConfigurationDirectory() throws Exception {
    ToolFixture tools = executableJdtFixture();
    String platform =
        System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")
            ? "config_mac"
            : System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "config_win"
                : "config_linux";
    deleteRecursively(tools.installation().resolve(platform));

    assertConfigurationInvalid(jdtYaml("jdt", tools.installation(), tools.javaHome()));
  }

  @Test
  void rejectsJdtJavaHomeWhoseBoundedVersionProbeIsBelowJava21() throws Exception {
    ToolFixture tools = executableJdtFixture();
    Path java = tools.javaHome().resolve("bin/java");
    Files.writeString(
        java,
        "#!/bin/sh\n"
            + "if [ \"$1\" = \"-version\" ]; then\n"
            + "  echo 'openjdk version \"17.0.12\"' >&2\n"
            + "  exit 0\n"
            + "fi\n"
            + "exit 0\n");
    assertThat(java.toFile().setExecutable(true, false)).isTrue();

    assertConfigurationInvalid(jdtYaml("jdt", tools.installation(), tools.javaHome()));
  }

  @Test
  void directJdtConfigurationConstructionCannotBypassCanonicalPathValidation() throws Exception {
    Class<?> configuration =
        EngineTestReflection.requireType(
            "org.sourceanalysis.app.runtime.EffectiveEngineConfiguration$JdtConfiguration");
    var constructor = configuration.getDeclaredConstructors()[0];
    Throwable failure = null;
    try {
      constructor.newInstance(
          Path.of("relative-installation"),
          Path.of("relative-java-home"),
          java.time.Duration.ofSeconds(1),
          java.time.Duration.ofSeconds(1),
          java.time.Duration.ofSeconds(1));
    } catch (java.lang.reflect.InvocationTargetException expected) {
      failure = expected.getCause();
    }
    assertThat(failure)
        .as("direct JDT configuration construction must enforce selected-path validation")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void canonicalizesSelectedJdtInstallationAndJavaHomeToAbsolutePaths() throws Exception {
    ToolFixture tools = executableJdtFixture();
    Files.createDirectories(temporaryDirectory.resolve("tools/aliases"));
    Path installationAlias = temporaryDirectory.resolve("tools/aliases/../jdtls");
    Path javaHomeAlias = temporaryDirectory.resolve("tools/aliases/../jdk");
    Object configuration =
        EngineTestReflection.loadYaml(jdtYaml("jdt", installationAlias, javaHomeAlias));

    Object jdt = EngineTestReflection.property(configuration, "jdt");
    assertThat(EngineTestReflection.pathProperty(jdt, "installation"))
        .isEqualTo(tools.installation().toRealPath());
    assertThat(EngineTestReflection.pathProperty(jdt, "javaHome"))
        .isEqualTo(tools.javaHome().toRealPath());
  }

  @Test
  void distributionIdentityChangesWhenJdtCorePluginContentChanges() throws Exception {
    ToolFixture tools = executableJdtFixture();
    Path corePlugin =
        Files.writeString(
            tools.installation().resolve("plugins/org.eclipse.jdt.ls.core_1.0.jar"), "core-v1\n");

    Object first =
        EngineTestReflection.loadYaml(jdtYaml("jdt", tools.installation(), tools.javaHome()));
    Files.writeString(corePlugin, "core-v2\n");
    Object second =
        EngineTestReflection.loadYaml(jdtYaml("jdt", tools.installation(), tools.javaHome()));

    String firstIdentity =
        EngineTestReflection.stringProperty(
            EngineTestReflection.property(first, "jdt"), "distributionIdentity");
    String secondIdentity =
        EngineTestReflection.stringProperty(
            EngineTestReflection.property(second, "jdt"), "distributionIdentity");
    assertThat(secondIdentity)
        .as("JDT distribution identity must bind the core plugin bytes, not only Equinox")
        .isNotEqualTo(firstIdentity);
  }

  @Test
  void doesNotOpenUnselectedJdtPaths() throws Exception {
    Path missingInstallation = temporaryDirectory.resolve("never-opened-installation");
    Path missingJavaHome = temporaryDirectory.resolve("never-opened-jdk");

    Throwable failure =
        EngineTestReflection.loadFailure(
            jdtYaml("javaparser", missingInstallation, missingJavaHome));

    assertThat(failure).isNotNull();
    assertThat(EngineTestReflection.codeOf(failure)).isEqualTo("ENGINE_CONFIGURATION_INVALID");
    assertThat(Files.exists(missingInstallation)).isFalse();
    assertThat(Files.exists(missingJavaHome)).isFalse();
  }

  private void assertConfigurationInvalid(byte[] yaml) throws Exception {
    Throwable failure = EngineTestReflection.loadFailure(yaml);
    assertThat(failure).as("invalid configuration must fail before engine startup").isNotNull();
    assertThat(EngineTestReflection.codeOf(failure)).isEqualTo("ENGINE_CONFIGURATION_INVALID");
  }

  private ToolFixture executableJdtFixture() throws IOException {
    Path installation = Files.createDirectories(temporaryDirectory.resolve("tools/jdtls"));
    Path javaHome = Files.createDirectories(temporaryDirectory.resolve("tools/jdk"));
    Path java = Files.createDirectories(javaHome.resolve("bin")).resolve("java");
    Files.createDirectories(installation.resolve("plugins"));
    Files.writeString(
        installation.resolve("plugins/org.eclipse.equinox.launcher_1.0.jar"), "launcher\n");
    Files.writeString(
        installation.resolve("plugins/org.eclipse.jdt.ls.core_1.0.jar"), "jdt-ls-core\n");
    Files.writeString(installation.resolve("plugins/org.eclipse.jdt.core_1.0.jar"), "jdt-core\n");
    Files.createDirectories(installation.resolve("config_linux"));
    Files.createDirectories(installation.resolve("config_mac"));
    Files.createDirectories(installation.resolve("config_win"));
    Files.writeString(
        java,
        "#!/bin/sh\n"
            + "if [ \"$1\" = \"-version\" ]; then\n"
            + "  echo 'openjdk version \"21.0.8\"' >&2\n"
            + "  exit 0\n"
            + "fi\n"
            + "exit 0\n");
    try {
      Files.setPosixFilePermissions(
          java,
          EnumSet.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE));
    } catch (UnsupportedOperationException ignored) {
      assertThat(java.toFile().setExecutable(true, false)).isTrue();
    }
    assertThat(Files.isExecutable(java)).isTrue();
    return new ToolFixture(installation, javaHome);
  }

  private static void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      paths
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException failure) {
                  throw new RuntimeException(failure);
                }
              });
    } catch (RuntimeException failure) {
      if (failure.getCause() instanceof IOException ioFailure) {
        throw ioFailure;
      }
      throw failure;
    }
  }

  private static byte[] jdtYaml(String engine, Path installation, Path javaHome) {
    return yaml(
        "sourceAnalysis:\n"
            + "  javaEngine: "
            + engine
            + "\n"
            + "  jdt:\n"
            + "    installation: '"
            + quote(installation.toString())
            + "'\n"
            + "    javaHome: '"
            + quote(javaHome.toString())
            + "'\n");
  }

  private static byte[] yaml(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static String quote(String value) {
    return value.replace("'", "''");
  }

  private record ToolFixture(Path installation, Path javaHome) {}
}

/**
 * Test-only reflection boundary that keeps RED tests compilable before Task 1 production types
 * exist.
 */
final class EngineTestReflection {

  private static final String LOADER = "org.sourceanalysis.app.runtime.EngineConfigurationLoader";

  private EngineTestReflection() {}

  static Object loadYaml(byte[] yaml) throws Exception {
    Class<?> loaderType = requireType(LOADER);
    Method method = requireYamlMethod(loaderType);
    Object receiver = Modifier.isStatic(method.getModifiers()) ? null : newInstance(loaderType);
    return invoke(method, receiver, yaml);
  }

  static Throwable loadFailure(byte[] yaml) throws Exception {
    Class<?> loaderType = requireType(LOADER);
    Method method = requireYamlMethod(loaderType);
    Object receiver = Modifier.isStatic(method.getModifiers()) ? null : newInstance(loaderType);
    try {
      invoke(method, receiver, yaml);
      return null;
    } catch (Throwable failure) {
      return unwrap(failure);
    }
  }

  static Object factoryCreate(Object configuration) throws Exception {
    Class<?> factoryType = requireType("org.sourceanalysis.app.runtime.JavaCodeEngineFactory");
    Method selected = null;
    for (Method method : factoryType.getMethods()) {
      if (!List.of("create", "build", "engine").contains(method.getName())
          || method.getParameterCount() != 1
          || !method.getParameterTypes()[0].isAssignableFrom(configuration.getClass())) {
        continue;
      }
      selected = method;
      break;
    }
    assertThat(selected).as("factory must expose a one-argument creation seam").isNotNull();
    Object receiver = Modifier.isStatic(selected.getModifiers()) ? null : newInstance(factoryType);
    return invoke(selected, receiver, configuration);
  }

  static Object verifiedProject(
      Path snapshotRoot, Path sourceRoot, Path classpathEntry, String sourcePath) throws Exception {
    String source = Files.readString(snapshotRoot.resolve(sourcePath));
    VerifiedSourceTextDocument document = document(sourcePath, source);
    String seed = snapshotRoot + ":" + sourcePath + ":" + document.sha256().value();
    VerifiedSourceTextSet sourceTexts =
        new VerifiedSourceTextSet(
            "snapshot:" + digest(seed),
            "COMPLETE_CAPTURE",
            true,
            reference("capability-profile", seed),
            reference("source-inventory", seed),
            reference("verified-snapshot", seed),
            controls(seed),
            List.of(document));
    String relativeSourceRoot = snapshotRoot.relativize(sourceRoot).toString().replace('\\', '/');
    return VerifiedJavaProject.fromVerifiedSourceTextSet(
        sourceTexts, List.of(relativeSourceRoot), List.of(classpathEntry), "17");
  }

  static Throwable openFailure(Object engine, Object project) throws Exception {
    Method selected = null;
    for (Method method : engine.getClass().getMethods()) {
      if (method.getName().equals("open")
          && method.getParameterCount() == 1
          && method.getParameterTypes()[0].isAssignableFrom(project.getClass())) {
        selected = method;
        break;
      }
    }
    assertThat(selected).as("engine must expose JavaCodeEngine.open(project)").isNotNull();
    try {
      selected.invoke(engine, project);
      return null;
    } catch (InvocationTargetException failure) {
      return unwrap(failure);
    }
  }

  static Object property(Object target, String name) throws Exception {
    assertThat(target).as("production object must be present").isNotNull();
    String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    for (String candidate : List.of(name, "get" + capitalized, "is" + capitalized)) {
      try {
        Method method = target.getClass().getMethod(candidate);
        return method.invoke(target);
      } catch (NoSuchMethodException ignored) {
        // Try the next record/bean spelling.
      }
    }
    try {
      Field field = target.getClass().getDeclaredField(name);
      field.setAccessible(true);
      return field.get(target);
    } catch (NoSuchFieldException missing) {
      fail("missing configuration property: " + name, missing);
      return null;
    }
  }

  static String stringProperty(Object target, String... names) throws Exception {
    for (String name : names) {
      try {
        Object value = property(target, name);
        if (value != null) {
          return value instanceof Enum<?> enumValue
              ? enumValue.name().toLowerCase(Locale.ROOT)
              : value.toString();
        }
      } catch (AssertionError ignored) {
        // Try an alternate accessor name before failing the contract assertion below.
      }
    }
    fail("missing property: " + String.join("/", names));
    return null;
  }

  static Path pathProperty(Object target, String name) throws Exception {
    Object value = property(target, name);
    assertThat(value).as("path property must be populated").isNotNull();
    return value instanceof Path path ? path : Path.of(value.toString());
  }

  static String codeOf(Throwable failure) {
    Throwable current = unwrap(failure);
    for (String accessor : List.of("code", "errorCode", "getCode", "getErrorCode")) {
      try {
        Method method = current.getClass().getMethod(accessor);
        Object value = method.invoke(current);
        if (value != null) {
          return value instanceof Enum<?> enumValue ? enumValue.name() : value.toString();
        }
      } catch (ReflectiveOperationException ignored) {
        // Fall through to the diagnostic message.
      }
    }
    String message = current.getMessage();
    if (message != null) {
      for (String candidate :
          List.of(
              "ENGINE_CONFIGURATION_INVALID",
              "ENGINE_NOT_INTEGRATED",
              "TOOL_UNAVAILABLE",
              "INDEX_FAILED",
              "SOURCE_INVALID")) {
        if (message.contains(candidate)) {
          return candidate;
        }
      }
    }
    return null;
  }

  static Class<?> requireType(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException missing) {
      fail("missing production type: " + name, missing);
      return null;
    }
  }

  private static Method requireYamlMethod(Class<?> type) {
    for (Method method : type.getMethods()) {
      if (List.of("load", "parse", "fromYaml").contains(method.getName())
          && method.getParameterCount() == 1
          && method.getParameterTypes()[0].equals(byte[].class)) {
        return method;
      }
    }
    fail("EngineConfigurationLoader must accept real YAML bytes");
    return null;
  }

  private static VerifiedSourceTextDocument document(String path, String source) {
    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
    return new VerifiedSourceTextDocument(
        new ArtifactId("file:" + digest(path)),
        path,
        "100644",
        "text/plain",
        bytes.length,
        new Sha256Digest(digest(bytes)),
        ImmutableBytes.copyOf(bytes));
  }

  private static ArtifactReference reference(String prefix, String seed) {
    return new ArtifactReference(
        new ArtifactId(prefix + ":" + digest(seed + prefix)),
        new Sha256Digest(digest(seed + "-sha")));
  }

  private static ArtifactControls controls(String seed) {
    return new ArtifactControls(
        new Sha256Digest(digest(seed + "-toolchain")),
        new Sha256Digest(digest(seed + "-profile")),
        new Sha256Digest(digest(seed + "-schema")),
        null,
        new ArtifactPolicyRegistryReference(
            new ArtifactId("artifact-policy-registry:" + digest(seed + "-registry")),
            new Sha256Digest(digest(seed + "-registry-sha"))));
  }

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static Object newInstance(Class<?> type) throws Exception {
    return type.getDeclaredConstructor().newInstance();
  }

  private static Object invoke(Method method, Object receiver, Object argument) throws Exception {
    try {
      return method.invoke(receiver, argument);
    } catch (InvocationTargetException failure) {
      throw (failure.getCause() instanceof Exception exception) ? exception : failure;
    }
  }

  private static Throwable unwrap(Throwable failure) {
    Throwable current = failure;
    while (current instanceof InvocationTargetException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }
}
