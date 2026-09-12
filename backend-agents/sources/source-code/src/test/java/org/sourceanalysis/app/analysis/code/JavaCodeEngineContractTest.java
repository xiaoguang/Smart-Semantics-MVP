package org.sourceanalysis.app.analysis.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Contract RED tests for the review findings that the first Task 1 GREEN missed. */
class JavaCodeEngineContractTest {

  @TempDir Path temporaryDirectory;

  @Test
  void verifiedProjectMustConsumeVerifiedSourceTextCapabilityInsteadOfAnArbitraryPath() {
    Class<?> project = requireType("org.sourceanalysis.app.analysis.code.VerifiedJavaProject");
    boolean acceptsVerifiedText =
        Arrays.stream(project.getDeclaredConstructors())
                .anyMatch(JavaCodeEngineContractTest::acceptsVerifiedSourceText)
            || Arrays.stream(project.getDeclaredMethods())
                .anyMatch(JavaCodeEngineContractTest::returnsProjectAndAcceptsVerifiedSourceText);
    assertThat(acceptsVerifiedText)
        .as("VerifiedJavaProject must be built from VerifiedSourceTextSet")
        .isTrue();

    boolean exposesPathAsPublicSnapshotInput =
        Arrays.stream(project.getDeclaredConstructors())
            .anyMatch(
                constructor ->
                    Modifier.isPublic(constructor.getModifiers())
                        && Arrays.asList(constructor.getParameterTypes()).contains(Path.class));
    assertThat(exposesPathAsPublicSnapshotInput)
        .as("arbitrary snapshot-directory constructors bypass verified source capability")
        .isFalse();
  }

  @Test
  void projectsExactVerifiedRegularFileBytesAndIgnoresSymlinkDirectoryAndUnlistedFiles()
      throws Exception {
    SourceFixture fixture = sourceFixture("class Example { int value() { return 1; } }\n");
    Object project = ContractReflection.verifiedProject(fixture);
    Path destination = temporaryDirectory.resolve("projection");

    ContractReflection.projectSourcesInto(project, destination);

    Path admitted = destination.resolve(fixture.admittedDocument().path());
    assertThat(Files.readAllBytes(admitted))
        .isEqualTo(fixture.admittedDocument().rawUtf8().copyToByteArray());
    assertThat(Files.exists(destination.resolve(fixture.unlistedPath()))).isFalse();
    assertThat(Files.exists(destination.resolve(fixture.directoryPath()))).isFalse();
    assertThat(Files.exists(destination.resolve(fixture.symlinkPath()))).isFalse();
  }

  @Test
  void projectFingerprintIsDerivedFromVerifiedContentsRatherThanCallerSuppliedText()
      throws Exception {
    SourceFixture first = sourceFixture("class Example { int value() { return 1; } }\n");
    SourceFixture second = sourceFixture("class Example { int value() { return 2; } }\n");

    Object firstProject = ContractReflection.verifiedProject(first);
    Object secondProject = ContractReflection.verifiedProject(second);
    String firstFingerprint = ContractReflection.stringProperty(firstProject, "fingerprint");
    String secondFingerprint = ContractReflection.stringProperty(secondProject, "fingerprint");

    assertThat(firstFingerprint).isNotEqualTo("caller-controlled-fingerprint");
    assertThat(secondFingerprint).isNotEqualTo("caller-controlled-fingerprint");
    assertThat(firstFingerprint).isNotEqualTo(secondFingerprint);
  }

  @Test
  void jdtClientRequiresDistinctMetadataProjectRootAndLanguageServerDataDirectory() {
    Class<?> client =
        requireType("org.sourceanalysis.app.analysis.code.jdt.JdtLanguageServerClient");
    boolean hasDistinctRoots =
        Arrays.stream(client.getDeclaredMethods())
            .filter(method -> method.getName().equals("start"))
            .anyMatch(JavaCodeEngineContractTest::hasProjectAndDataDirectories);
    assertThat(hasDistinctRoots)
        .as("JDT startup must receive separate metadata project root and LS data directory")
        .isTrue();
  }

  @Test
  void jdtStartupHasReadinessAndBoundedDeclarationProbeBeforeSessionReturns() {
    Class<?> client =
        requireType("org.sourceanalysis.app.analysis.code.jdt.JdtLanguageServerClient");
    boolean readiness =
        Arrays.stream(client.getDeclaredMethods())
            .anyMatch(method -> containsAny(method.getName(), "ready", "initializ"));
    boolean declarationProbe =
        Arrays.stream(client.getDeclaredMethods())
            .anyMatch(method -> containsAny(method.getName(), "probe", "declaration"));

    assertThat(readiness).as("JDT startup must wait for readiness").isTrue();
    assertThat(declarationProbe)
        .as("JDT startup must run a bounded declaration probe before open returns")
        .isTrue();
  }

  @Test
  void realAndFakeStartsShareOnePackagePrivateProcessIsolationBoundary() {
    Class<?> isolation =
        requireType("org.sourceanalysis.app.analysis.code.jdt.JdtProcessIsolation");
    assertThat(Modifier.isPublic(isolation.getModifiers()))
        .as("process/isolation seam must stay package-private")
        .isFalse();

    Class<?> client =
        requireType("org.sourceanalysis.app.analysis.code.jdt.JdtLanguageServerClient");
    boolean clientReceivesIsolation =
        Arrays.stream(client.getDeclaredConstructors())
            .anyMatch(
                constructor -> Arrays.asList(constructor.getParameterTypes()).contains(isolation));
    assertThat(clientReceivesIsolation)
        .as("real JDT client and fake process tests must share the isolation seam")
        .isTrue();

    Class<?> session = requireType("org.sourceanalysis.app.analysis.code.jdt.JdtProjectSession");
    boolean packagePrivateOpen =
        Arrays.stream(session.getDeclaredMethods())
            .anyMatch(
                method ->
                    method.getName().equals("open")
                        && Arrays.asList(method.getParameterTypes()).contains(isolation)
                        && !Modifier.isPublic(method.getModifiers()));
    assertThat(packagePrivateOpen)
        .as("session fake and real paths must use the package-private isolation seam")
        .isTrue();

    boolean publicNoLsBypass =
        Arrays.stream(session.getDeclaredMethods())
            .anyMatch(
                method ->
                    method.getName().equals("open")
                        && Modifier.isPublic(method.getModifiers())
                        && Arrays.asList(method.getParameterTypes()).contains(Path.class));
    assertThat(publicNoLsBypass)
        .as("public open(project, marker) must not bypass JDT LS")
        .isFalse();
  }

  @Test
  void declarationCatalogExposesTheNeutralTypedFieldsNeededByDiscovery() {
    assertRecordComponents(
        "org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog",
        "snapshotId",
        "files",
        "types",
        "methods",
        "annotations",
        "fields",
        "fileDiagnostics");
  }

  @Test
  void entryContextExposesSchemaMethodsCallsSourcesAndTechnicalEnhancements() {
    assertRecordComponents(
        "org.sourceanalysis.app.analysis.code.EntryCodeContext",
        "schemaVersion",
        "entryId",
        "entryMethodKey",
        "methods",
        "calls",
        "supportingSources",
        "limitations",
        "technicalEnhancements");
  }

  @Test
  void nonEmptyEntryContextMustContainItsEntryMethod() throws Exception {
    Class<?> context = requireType("org.sourceanalysis.app.analysis.code.EntryCodeContext");
    Constructor<?> constructor =
        Arrays.stream(context.getDeclaredConstructors())
            .filter(value -> value.getParameterCount() >= 5)
            .findFirst()
            .orElseGet(
                () -> {
                  fail("EntryCodeContext must expose a typed construction seam");
                  return null;
                });
    Object[] arguments = ContractReflection.contextArguments(constructor, false);
    Throwable failure = null;
    try {
      constructor.setAccessible(true);
      constructor.newInstance(arguments);
    } catch (InvocationTargetException expected) {
      failure = expected.getCause();
    }
    assertThat(failure)
        .as("a non-empty collected context without entry method must be rejected")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void descriptorMustCarryAtLeastOneToolVersion() throws Exception {
    Class<?> descriptor = requireType("org.sourceanalysis.app.analysis.code.EngineDescriptor");
    Constructor<?> constructor = descriptor.getDeclaredConstructors()[0];
    Object[] arguments =
        new Object[] {"jdt", "jdt-session-v1", Map.of(), "17", List.of("JDT_LANGUAGE_SERVER")};
    Throwable failure = null;
    try {
      constructor.newInstance(arguments);
    } catch (InvocationTargetException expected) {
      failure = expected.getCause();
    }
    assertThat(failure)
        .as("descriptor must disclose concrete tool versions")
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void assertRecordComponents(String className, String... required) {
    Class<?> type = requireType(className);
    assertThat(type.isRecord()).as(className + " must remain a neutral record").isTrue();
    Set<String> actual =
        Arrays.stream(type.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(java.util.stream.Collectors.toSet());
    assertThat(actual).containsAll(List.of(required));
  }

  private SourceFixture sourceFixture(String source) throws Exception {
    Path snapshotRoot =
        Files.createDirectories(
            temporaryDirectory.resolve("snapshot-" + digest(source).substring(0, 8)));
    Path sourceRoot = Files.createDirectories(snapshotRoot.resolve("src/main/java/com/example"));
    Path admitted = sourceRoot.resolve("Example.java");
    Files.writeString(admitted, source, StandardCharsets.UTF_8);
    Path unlisted = sourceRoot.resolve("Unlisted.java");
    Files.writeString(unlisted, "class Unlisted {}\n", StandardCharsets.UTF_8);
    Path directory = sourceRoot.resolve("DirectoryEntry");
    Files.createDirectories(directory);
    Files.writeString(
        directory.resolve("Nested.java"), "class Nested {}\n", StandardCharsets.UTF_8);
    Path symlink = sourceRoot.resolve("Link.java");
    Files.createSymbolicLink(symlink, admitted);
    VerifiedSourceTextDocument document =
        document("src/main/java/com/example/Example.java", source);
    VerifiedSourceTextSet texts =
        new VerifiedSourceTextSet(
            "snapshot:" + digest(source + snapshotRoot),
            "COMPLETE_CAPTURE",
            true,
            reference("capability-profile", source),
            reference("source-inventory", source),
            reference("verified-snapshot", source),
            controls(source),
            List.of(document));
    return new SourceFixture(
        snapshotRoot,
        sourceRoot,
        Files.createFile(snapshotRoot.resolve("approved-dependency.jar")),
        texts,
        document,
        "src/main/java/com/example/Unlisted.java",
        "src/main/java/com/example/DirectoryEntry",
        "src/main/java/com/example/Link.java");
  }

  private static VerifiedSourceTextDocument document(String path, String text) {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
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

  private static boolean acceptsVerifiedSourceText(Constructor<?> constructor) {
    return Arrays.asList(constructor.getParameterTypes()).contains(VerifiedSourceTextSet.class);
  }

  private static boolean returnsProjectAndAcceptsVerifiedSourceText(Method method) {
    return method
            .getReturnType()
            .getName()
            .equals("org.sourceanalysis.app.analysis.code.VerifiedJavaProject")
        && Arrays.asList(method.getParameterTypes()).contains(VerifiedSourceTextSet.class);
  }

  private static boolean hasProjectAndDataDirectories(Method method) {
    long paths =
        Arrays.stream(method.getParameters())
            .filter(parameter -> parameter.getType().equals(Path.class))
            .count();
    if (paths < 2) {
      return false;
    }
    boolean projectRoot = false;
    boolean dataDirectory = false;
    for (Parameter parameter : method.getParameters()) {
      String name = parameter.getName().toLowerCase(Locale.ROOT);
      projectRoot |= name.contains("project") || name.contains("root");
      dataDirectory |= name.contains("data") || name.contains("workspace");
    }
    return projectRoot && dataDirectory;
  }

  private static boolean containsAny(String value, String... tokens) {
    String lower = value.toLowerCase(Locale.ROOT);
    return Arrays.stream(tokens).anyMatch(lower::contains);
  }

  private static Class<?> requireType(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException missing) {
      fail("missing production type: " + name, missing);
      return null;
    }
  }

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private record SourceFixture(
      Path snapshotRoot,
      Path sourceRoot,
      Path classpathEntry,
      VerifiedSourceTextSet texts,
      VerifiedSourceTextDocument admittedDocument,
      String unlistedPath,
      String directoryPath,
      String symlinkPath) {}

  private static final class ContractReflection {

    private ContractReflection() {}

    static Object verifiedProject(SourceFixture fixture) throws Exception {
      Class<?> projectType =
          requireType("org.sourceanalysis.app.analysis.code.VerifiedJavaProject");
      List<Object> candidates = new ArrayList<>();
      for (Method method : projectType.getDeclaredMethods()) {
        if (Modifier.isStatic(method.getModifiers())
            && method.getReturnType().equals(projectType)
            && Arrays.asList(method.getParameterTypes()).contains(VerifiedSourceTextSet.class)) {
          try {
            method.setAccessible(true);
            candidates.add(method.invoke(null, projectArguments(method, fixture)));
          } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            // Try the record constructor below.
          }
        }
      }
      for (Constructor<?> constructor : projectType.getDeclaredConstructors()) {
        if (!acceptsVerifiedSourceText(constructor)) {
          continue;
        }
        try {
          constructor.setAccessible(true);
          candidates.add(constructor.newInstance(projectArguments(constructor, fixture)));
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
          // Continue to another constructor shape.
        }
      }
      assertThat(candidates)
          .as("VerifiedJavaProject must be constructible from VerifiedSourceTextSet")
          .isNotEmpty();
      return candidates.get(0);
    }

    static void projectSourcesInto(Object project, Path destination) throws Exception {
      Method method =
          Arrays.stream(project.getClass().getMethods())
              .filter(value -> value.getName().equals("projectSourcesInto"))
              .filter(
                  value ->
                      value.getParameterCount() == 1
                          && value.getParameterTypes()[0].equals(Path.class))
              .findFirst()
              .orElseGet(
                  () -> {
                    fail("verified project must expose source projection");
                    return null;
                  });
      try {
        method.invoke(project, destination);
      } catch (InvocationTargetException failure) {
        rethrow(failure.getCause());
      }
    }

    static String stringProperty(Object target, String name) throws Exception {
      Method method = target.getClass().getMethod(name);
      Object value = method.invoke(target);
      assertThat(value).isNotNull();
      return value.toString();
    }

    static Object[] contextArguments(Constructor<?> constructor, boolean containsEntryMethod) {
      Object[] arguments = new Object[constructor.getParameterCount()];
      int strings = 0;
      for (int index = 0; index < arguments.length; index++) {
        Class<?> type = constructor.getParameterTypes()[index];
        String name = constructor.getParameters()[index].getName().toLowerCase(Locale.ROOT);
        if (type.equals(String.class)) {
          arguments[index] =
              switch (strings++) {
                case 0 -> "entry-code-context-v1";
                case 1 -> "entry:example";
                default -> "method:entry";
              };
        } else if (Collection.class.isAssignableFrom(type) || type.equals(Set.class)) {
          arguments[index] =
              name.contains("method")
                  ? (containsEntryMethod ? List.of("method:entry") : List.of("method:other"))
                  : List.of();
        } else if (type.equals(Map.class)) {
          arguments[index] = Map.of();
        } else {
          arguments[index] = null;
        }
      }
      return arguments;
    }

    private static Object[] projectArguments(Executable executable, SourceFixture fixture) {
      Object[] arguments = new Object[executable.getParameterCount()];
      int strings = 0;
      for (int index = 0; index < arguments.length; index++) {
        Class<?> type = executable.getParameterTypes()[index];
        Parameter parameter = executable.getParameters()[index];
        String name = parameter.getName().toLowerCase(Locale.ROOT);
        if (type.equals(VerifiedSourceTextSet.class)) {
          arguments[index] = fixture.texts();
        } else if (type.equals(Path.class)) {
          arguments[index] =
              name.contains("class") || name.contains("depend")
                  ? fixture.classpathEntry()
                  : name.contains("source") || name.contains("root")
                      ? fixture.sourceRoot()
                      : fixture.snapshotRoot();
        } else if (type.equals(String.class)) {
          arguments[index] =
              name.contains("finger")
                  ? "caller-controlled-fingerprint"
                  : name.contains("level") || name.contains("language")
                      ? "17"
                      : strings++ == 0 ? fixture.texts().snapshotId() : "caller-text";
        } else if (Collection.class.isAssignableFrom(type) || type.equals(Set.class)) {
          arguments[index] =
              collectionArgument(executable.getGenericParameterTypes()[index], name, fixture);
        } else if (type.equals(Map.class)) {
          arguments[index] = Map.of();
        } else if (type.equals(boolean.class) || type.equals(Boolean.class)) {
          arguments[index] = true;
        } else if (type.equals(long.class) || type.equals(Long.class)) {
          arguments[index] = (long) fixture.admittedDocument().sizeBytes();
        } else {
          arguments[index] = null;
        }
      }
      return arguments;
    }

    private static Object collectionArgument(Type genericType, String name, SourceFixture fixture) {
      if (name.contains("root")) {
        return List.of("src/main/java");
      }
      if (name.contains("class") || name.contains("depend")) {
        return List.of(fixture.classpathEntry());
      }
      if (genericType instanceof ParameterizedType parameterizedType
          && parameterizedType.getActualTypeArguments().length == 1) {
        String component = parameterizedType.getActualTypeArguments()[0].getTypeName();
        if (component.contains("VerifiedSourceTextDocument")) {
          return fixture.texts().documents();
        }
        if (component.contains("Path")) {
          return List.of("src/main/java");
        }
      }
      return List.of(fixture.admittedDocument().path());
    }

    private static void rethrow(Throwable failure) throws Exception {
      if (failure instanceof Exception exception) {
        throw exception;
      }
      if (failure instanceof Error error) {
        throw error;
      }
      throw new AssertionError(failure);
    }
  }
}
