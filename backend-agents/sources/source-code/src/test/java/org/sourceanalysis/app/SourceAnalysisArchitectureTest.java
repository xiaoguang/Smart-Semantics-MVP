package org.sourceanalysis.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Guards the target Java project identity and its approved semantic package registry. */
class SourceAnalysisArchitectureTest {

  private static final Pattern PACKAGE_DECLARATION =
      Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

  private static final Set<String> REQUIRED_PACKAGE_ROOTS =
      Set.of(
          "org.sourceanalysis.app.analysis.inventory",
          "org.sourceanalysis.app.analysis.discovery",
          "org.sourceanalysis.app.analysis.code",
          "org.sourceanalysis.app.analysis.persistence",
          "org.sourceanalysis.app.analysis.material",
          "org.sourceanalysis.app.analysis.graph",
          "org.sourceanalysis.app.analysis.fact",
          "org.sourceanalysis.app.analysis.flow",
          "org.sourceanalysis.app.analysis.interpretation",
          "org.sourceanalysis.app.analysis.knowledge",
          "org.sourceanalysis.app.analysis.document",
          "org.sourceanalysis.app.capture.localgit",
          "org.sourceanalysis.app.artifact",
          "org.sourceanalysis.app.evidence",
          "org.sourceanalysis.app.runtime",
          "org.sourceanalysis.app.validation",
          "org.sourceanalysis.app.adapter.cli",
          "org.sourceanalysis.app.adapter.http",
          "org.sourceanalysis.app.adapter.provider");

  private static final Set<String> FORBIDDEN_WIRE_TOKENS =
      Set.of(
          "com.linguan.codemd",
          "github-code",
          "github-code-to-markdown",
          "stage01",
          "stage02",
          "stage03",
          "stage04",
          "stage-receipt.json",
          "Stage01",
          "Stage02",
          "Stage03",
          "Stage04",
          "target",
          "mvp");

  @Test
  void targetProjectUsesSemanticIdentityAndPackageRegistry() throws IOException {
    Path projectRoot = Path.of("").toAbsolutePath().normalize();
    Path productionRoot = projectRoot.resolve("src/main/java");
    Path testRoot = projectRoot.resolve("src/test/java");
    String pom = Files.readString(projectRoot.resolve("pom.xml"), StandardCharsets.UTF_8);
    List<String> productionPackages = javaPackages(productionRoot);
    List<String> testPackages = javaPackages(testRoot);
    Set<String> forbiddenTokens = forbiddenTokens(projectRoot, pom);

    assertAll(
        "semantic wire reset architecture",
        () ->
            assertThat(projectRoot.getFileName().toString())
                .as("the checked-out module directory")
                .isEqualTo("source-code"),
        () -> assertThat(pom).as("Maven groupId").contains("<groupId>org.sourceanalysis</groupId>"),
        () ->
            assertThat(pom)
                .as("Maven artifactId")
                .contains("<artifactId>source-code-analysis-agent</artifactId>"),
        () ->
            assertThat(pom)
                .as("Maven display name")
                .contains("<name>Source Code Analysis Agent</name>"),
        () ->
            assertThat(productionPackages)
                .as("every production class uses the approved application root")
                .allMatch(this::usesApprovedPackageRoot),
        () ->
            assertThat(testPackages)
                .as("every test class uses the approved application root")
                .allMatch(this::usesApprovedPackageRoot),
        () ->
            assertThat(productionPackages)
                .as("all semantic analysis and cross-cutting roots are present")
                .containsAll(REQUIRED_PACKAGE_ROOTS),
        () ->
            assertThat(forbiddenTokens)
                .as("pre-reset coordinates, directories, and wire names")
                .isEmpty());
  }

  private static List<String> javaPackages(Path sourceRoot) throws IOException {
    List<String> packages = new ArrayList<>();
    if (!Files.isDirectory(sourceRoot)) {
      return packages;
    }
    try (Stream<Path> paths = Files.walk(sourceRoot)) {
      paths
          .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
          .sorted()
          .forEach(
              path -> {
                try {
                  String source = Files.readString(path, StandardCharsets.UTF_8);
                  var matcher = PACKAGE_DECLARATION.matcher(source);
                  if (matcher.find()) {
                    packages.add(matcher.group(1));
                  }
                } catch (IOException exception) {
                  throw new ArchitectureTestIOException(path, exception);
                }
              });
    } catch (ArchitectureTestIOException exception) {
      throw exception.cause;
    }
    return packages;
  }

  @Test
  void scannerChecksTestPackagesAndFixturePathsWithoutScanningJavaStringLiterals(
      @TempDir Path temporaryDirectory) throws IOException {
    Path legacyTest =
        temporaryDirectory.resolve("legacy/src/test/java/com/linguan/codemd/OldTest.java");
    Files.createDirectories(legacyTest.getParent());
    Files.writeString(
        legacyTest, "package com.linguan.codemd;\nclass OldTest {}\n", StandardCharsets.UTF_8);
    Path legacyFixture =
        temporaryDirectory.resolve("legacy/src/test/resources/target/stage01/fixture.json");
    Files.createDirectories(legacyFixture.getParent());
    Files.writeString(legacyFixture, "{}\n", StandardCharsets.UTF_8);

    Set<String> legacyFindings = forbiddenTokens(temporaryDirectory.resolve("legacy"), "");

    assertThat(legacyFindings)
        .as("old test package and fixture directory are architectural violations")
        .contains("com.linguan.codemd", "target", "stage01");

    Path cleanTest =
        temporaryDirectory.resolve("clean/src/test/java/org/sourceanalysis/app/HarmlessTest.java");
    Files.createDirectories(cleanTest.getParent());
    Files.writeString(
        cleanTest,
        "package org.sourceanalysis.app;\n"
            + "class HarmlessTest { String value = \"com.linguan.codemd stage01 target\"; }\n",
        StandardCharsets.UTF_8);

    assertThat(forbiddenTokens(temporaryDirectory.resolve("clean"), ""))
        .as("legacy literals used inside Java code are not architecture paths or package names")
        .isEmpty();
  }

  @Test
  void scannerRejectsForbiddenJavaTreePathWithApprovedPackageDeclaration(
      @TempDir Path temporaryDirectory) throws IOException {
    Path legacyTest =
        temporaryDirectory.resolve("legacy/src/test/java/com/linguan/codemd/OldTest.java");
    Files.createDirectories(legacyTest.getParent());
    Files.writeString(
        legacyTest, "package org.sourceanalysis.app;\nclass OldTest {}\n", StandardCharsets.UTF_8);

    assertThat(forbiddenTokens(temporaryDirectory.resolve("legacy"), ""))
        .as(
            "legacy Java source tree path is an architecture violation even with a semantic package")
        .contains("com.linguan.codemd");
  }

  private boolean usesApprovedPackageRoot(String packageName) {
    return packageName.equals("org.sourceanalysis.app")
        || REQUIRED_PACKAGE_ROOTS.stream()
            .anyMatch(root -> packageName.equals(root) || packageName.startsWith(root + "."));
  }

  private static Set<String> forbiddenTokens(Path projectRoot, String pom) throws IOException {
    Set<String> found = new LinkedHashSet<>();
    String projectName = projectRoot.getFileName().toString();
    FORBIDDEN_WIRE_TOKENS.stream()
        .filter(token -> projectName.contains(token) || pom.contains(token))
        .forEach(found::add);

    scanJavaPackageDeclarations(projectRoot.resolve("src/main/java"), found);
    scanJavaPackageDeclarations(projectRoot.resolve("src/test/java"), found);
    scanFixturePaths(projectRoot.resolve("src/test/resources"), found);
    return found;
  }

  private static void scanJavaPackageDeclarations(Path sourceRoot, Set<String> found)
      throws IOException {
    if (!Files.isDirectory(sourceRoot)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(sourceRoot)) {
      paths
          .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
          .sorted()
          .forEach(
              path -> {
                try {
                  Path relativePath = sourceRoot.relativize(path);
                  Path relativePackagePath = relativePath.getParent();
                  if (relativePackagePath != null) {
                    recordForbiddenWireTokens(
                        relativePackagePath.toString().replace('\\', '.').replace('/', '.'), found);
                  }

                  String source = Files.readString(path, StandardCharsets.UTF_8);
                  var matcher = PACKAGE_DECLARATION.matcher(source);
                  if (matcher.find()) {
                    recordForbiddenWireTokens(matcher.group(1), found);
                  }
                } catch (IOException exception) {
                  throw new ArchitectureTestIOException(path, exception);
                }
              });
    } catch (ArchitectureTestIOException exception) {
      throw exception.cause;
    }
  }

  private static void recordForbiddenWireTokens(String candidate, Set<String> found) {
    FORBIDDEN_WIRE_TOKENS.stream().sorted().filter(candidate::contains).forEach(found::add);
  }

  private static void scanFixturePaths(Path fixtureRoot, Set<String> found) throws IOException {
    if (!Files.isDirectory(fixtureRoot)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(fixtureRoot)) {
      paths
          .map(fixtureRoot::relativize)
          .map(path -> path.toString().replace('\\', '/'))
          .forEach(
              relative ->
                  FORBIDDEN_WIRE_TOKENS.stream().filter(relative::contains).forEach(found::add));
    }
  }

  private static final class ArchitectureTestIOException extends RuntimeException {
    private final IOException cause;

    private ArchitectureTestIOException(Path path, IOException cause) {
      super("Cannot inspect architecture source " + path, cause);
      this.cause = cause;
    }
  }
}
