package org.sourceanalysis.app.analysis.code;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;

/**
 * Reads one named external compilation-input JSON file into the existing JDT environment model.
 *
 * <p>The file is evidence of an already completed external build preparation. This reader may
 * deserialize a named Maven-produced effective POM solely to compare its already evaluated build
 * settings with the handoff. It never resolves or evaluates a Maven model, scans a repository,
 * downloads an artifact, or launches Maven.
 */
final class JavaCompilationInputReader {

  private static final String SCHEMA_VERSION = "java-compilation-input-v1";
  private static final ObjectMapper JSON = new ObjectMapper();

  JavaCompilationEnvironmentResult read(
      Path compilationInput,
      SelectedSourceBasis selectedSource,
      VerifiedSourceTextSet sourceTexts) {
    Objects.requireNonNull(compilationInput, "compilation input");
    Objects.requireNonNull(selectedSource, "selected source basis");
    Objects.requireNonNull(sourceTexts, "verified source texts");
    List<JavaCompilationEnvironmentProblem> problems = new ArrayList<>();
    ObjectNode input = readInput(compilationInput, problems);
    if (input == null) {
      return blocked(problems);
    }

    if (!sameSelectedSource(input, selectedSource, sourceTexts, problems)) {
      return blocked(problems);
    }
    Map<String, BuildFileEvidence> buildFiles = verifiedBuildFiles(input, sourceTexts, problems);
    if (buildFiles == null) {
      return blocked(problems);
    }
    if (!successfulExport(input, problems)) {
      return blocked(problems);
    }

    ArrayNode configuredModules = array(input, "modules", problems);
    if (configuredModules == null || configuredModules.isEmpty()) {
      if (configuredModules != null) {
        problems.add(
            problem(
                "JAVA_COMPILATION_MODULES_INVALID", "external compilation input has no modules"));
      }
      return blocked(problems);
    }
    List<ParsedModule> modules = new ArrayList<>();
    for (JsonNode configuredModule : configuredModules) {
      parseModule(configuredModule, buildFiles, problems).ifPresent(modules::add);
    }
    if (!problems.isEmpty()) {
      return blocked(problems);
    }
    if (!matchesMavenSourceModuleDependencies(modules, problems)) {
      return blocked(problems);
    }
    return new JavaCompilationEnvironmentComposer()
        .compose(sourceTexts, modules.stream().map(ParsedModule::module).toList());
  }

  /**
   * Reads the formal v2 handoff directly from parsed configuration values.
   *
   * <p>Unlike the historical file-based v1 reader, this route has no caller-declared semantic
   * fields to compare. It binds each selected project POM to R0, then derives the source root,
   * compiler setting, source-module edges, target JDK version, and execution environment from the
   * named Maven outputs and JDK installation.
   */
  JavaCompilationEnvironmentResult read(
      JavaReadinessPreparation.CompilationInput compilationInput,
      SelectedSourceBasis selectedSource,
      VerifiedSourceTextSet sourceTexts) {
    Objects.requireNonNull(compilationInput, "external compilation input");
    Objects.requireNonNull(selectedSource, "selected source basis");
    Objects.requireNonNull(sourceTexts, "verified source texts");
    List<JavaCompilationEnvironmentProblem> problems = new ArrayList<>();
    if (!sameSelectedSource(selectedSource, sourceTexts, problems)) {
      return blocked(problems);
    }

    List<V2ParsedModule> modules = new ArrayList<>();
    for (JavaReadinessPreparation.ModuleInput module : compilationInput.modules()) {
      parseV2Module(module, compilationInput.projectDirectory(), sourceTexts, problems)
          .ifPresent(modules::add);
    }
    if (!problems.isEmpty()) {
      return blocked(problems);
    }

    List<JavaCompilationInputModule> derived = deriveSourceModuleDependencies(modules, problems);
    if (!problems.isEmpty()) {
      return blocked(problems);
    }
    return new JavaCompilationEnvironmentComposer().compose(sourceTexts, derived);
  }

  private static boolean sameSelectedSource(
      SelectedSourceBasis selectedSource,
      VerifiedSourceTextSet sourceTexts,
      List<JavaCompilationEnvironmentProblem> problems) {
    if (selectedSource.snapshotId().value().equals(sourceTexts.snapshotId())) {
      return true;
    }
    problems.add(
        problem(
            "JAVA_COMPILATION_INPUT_SOURCE_MISMATCH",
            "selected source basis and verified source text set do not share an R0 snapshot"));
    return false;
  }

  private static ObjectNode readInput(
      Path compilationInput, List<JavaCompilationEnvironmentProblem> problems) {
    try {
      JsonNode parsed = JSON.readTree(Files.readAllBytes(compilationInput));
      if (!(parsed instanceof ObjectNode input)) {
        throw new IllegalArgumentException("external compilation input must be a JSON object");
      }
      if (!SCHEMA_VERSION.equals(text(input, "schemaVersion"))) {
        throw new IllegalArgumentException("external compilation input schema is unsupported");
      }
      return input;
    } catch (IOException | IllegalArgumentException invalid) {
      problems.add(
          problem(
              "JAVA_COMPILATION_INPUT_INVALID", "named external compilation input cannot be read"));
      return null;
    }
  }

  private static boolean sameSelectedSource(
      ObjectNode input,
      SelectedSourceBasis selectedSource,
      VerifiedSourceTextSet sourceTexts,
      List<JavaCompilationEnvironmentProblem> problems) {
    boolean same = true;
    if (!selectedSource.snapshotId().value().equals(sourceTexts.snapshotId())) {
      problems.add(
          problem(
              "JAVA_COMPILATION_INPUT_SOURCE_MISMATCH",
              "selected source basis and verified source text set do not share an R0 snapshot"));
      same = false;
    }
    if (!selectedSource
        .preparedSource()
        .publication()
        .address()
        .runId()
        .value()
        .equals(optionalText(input, "sourcePreparationRunId"))) {
      problems.add(
          problem(
              "JAVA_COMPILATION_INPUT_SOURCE_MISMATCH",
              "external compilation input names another source-preparation run"));
      same = false;
    }
    if (!sourceTexts.snapshotId().equals(optionalText(input, "sourceVersionId"))) {
      problems.add(
          problem(
              "JAVA_COMPILATION_INPUT_SOURCE_MISMATCH",
              "external compilation input names another source snapshot"));
      same = false;
    }
    if (!selectedSource
        .effectiveScopeDigest()
        .value()
        .equals(optionalText(input, "effectiveScopeDigest"))) {
      problems.add(
          problem(
              "JAVA_COMPILATION_INPUT_SCOPE_MISMATCH",
              "external compilation input names another effective source scope"));
      same = false;
    }
    return same;
  }

  private static Map<String, BuildFileEvidence> verifiedBuildFiles(
      ObjectNode input,
      VerifiedSourceTextSet sourceTexts,
      List<JavaCompilationEnvironmentProblem> problems) {
    ArrayNode declared = array(input, "buildFiles", problems);
    if (declared == null) {
      return null;
    }
    if (declared.isEmpty()) {
      problems.add(
          problem(
              "JAVA_COMPILATION_INPUT_BUILD_MISMATCH",
              "external compilation input must name at least one verified build file"));
      return null;
    }
    Map<String, VerifiedSourceTextDocument> verified = new LinkedHashMap<>();
    for (VerifiedSourceTextDocument document : sourceTexts.documents()) {
      verified.put(document.path(), document);
    }
    Map<String, BuildFileEvidence> declaredByModule = new LinkedHashMap<>();
    boolean same = true;
    for (JsonNode value : declared) {
      if (!(value instanceof ObjectNode buildFile)) {
        problems.add(
            problem("JAVA_COMPILATION_INPUT_BUILD_MISMATCH", "build-file declaration is invalid"));
        same = false;
        continue;
      }
      try {
        String modulePath = snapshotRelativePath(text(buildFile, "modulePath"));
        String path = snapshotRelativePath(text(buildFile, "path"));
        String sha256 = text(buildFile, "sha256");
        VerifiedSourceTextDocument document = verified.get(path);
        if (document == null
            || !document.sha256().value().equals(sha256)
            || !modulePomPath(modulePath).equals(path)
            || declaredByModule.putIfAbsent(modulePath, new BuildFileEvidence(path, sha256))
                != null) {
          throw new IllegalArgumentException("build file is not an exact verified R0 document");
        }
      } catch (IllegalArgumentException invalid) {
        problems.add(
            problem(
                "JAVA_COMPILATION_INPUT_BUILD_MISMATCH",
                "build-file path or digest is not an exact verified R0 document"));
        same = false;
      }
    }
    return same ? Map.copyOf(declaredByModule) : null;
  }

  private static boolean successfulExport(
      ObjectNode input, List<JavaCompilationEnvironmentProblem> problems) {
    JsonNode export = input.get("export");
    if (!(export instanceof ObjectNode exportObject)
        || !"SUCCEEDED".equals(optionalText(exportObject, "status"))) {
      problems.add(
          problem(
              "JAVA_COMPILATION_INPUT_EXPORT_FAILED",
              "external compilation input does not record a successful classpath export"));
      return false;
    }
    return true;
  }

  private static java.util.Optional<ParsedModule> parseModule(
      JsonNode configuredModule,
      Map<String, BuildFileEvidence> buildFiles,
      List<JavaCompilationEnvironmentProblem> problems) {
    if (!(configuredModule instanceof ObjectNode module)) {
      problems.add(
          problem("JAVA_COMPILATION_INPUT_INVALID", "external module declaration is invalid"));
      return java.util.Optional.empty();
    }
    String modulePath = null;
    try {
      modulePath = snapshotRelativePath(text(module, "modulePath"));
      List<String> sourceRoots = sourceRoots(module, modulePath);
      JavaCompilationTarget target = compilationTarget(module);
      java.util.Optional<MavenProjectSettings> mavenSettings =
          mavenEvaluatedProjectSettings(
              module, modulePath, sourceRoots, target, buildFiles.get(modulePath), problems);
      if (mavenSettings.isEmpty()) {
        return java.util.Optional.empty();
      }
      ClasspathExport classpath = classpath(module, modulePath, problems);
      JavaModuleTargetPlatform platform = targetPlatform(module, modulePath, problems);
      List<SourceModuleDependency> edges = sourceModuleDependencies(module);
      if (classpath == null || platform == null) {
        return java.util.Optional.empty();
      }
      return java.util.Optional.of(
          new ParsedModule(
              new JavaCompilationInputModule(
                  modulePath,
                  sourceRoots,
                  classpath.entries(),
                  classpath.sha256(),
                  platform,
                  target,
                  edges),
              mavenSettings.orElseThrow()));
    } catch (IllegalArgumentException invalid) {
      problems.add(
          problem(
              "JAVA_COMPILATION_INPUT_INVALID",
              modulePath,
              "external module declaration is incomplete or malformed"));
      return java.util.Optional.empty();
    }
  }

  private static java.util.Optional<V2ParsedModule> parseV2Module(
      JavaReadinessPreparation.ModuleInput configuredModule,
      Path projectDirectory,
      VerifiedSourceTextSet sourceTexts,
      List<JavaCompilationEnvironmentProblem> problems) {
    String modulePath = null;
    try {
      modulePath = snapshotRelativePath(configuredModule.modulePath());
      Path moduleProjectDirectory = moduleProjectDirectory(projectDirectory, modulePath);
      byte[] projectPom = verifiedProjectPom(moduleProjectDirectory, modulePath, sourceTexts);
      byte[] effectivePom = readV2EffectivePom(configuredModule.effectivePomFile());
      Model projectPomModel = mavenModel(projectPom, "project POM");
      Model effectivePomModel = mavenModel(effectivePom, "effective POM");
      requireSameProjectIdentity(projectPomModel, effectivePomModel);
      List<String> sourceRoots =
          List.of(evaluatedSourceRoot(effectivePomModel, moduleProjectDirectory, modulePath));
      JavaCompilationTarget target = evaluatedV2CompilerTarget(effectivePomModel);
      ClasspathExport classpath =
          classpath(
              configuredModule.classpathFile(),
              configuredModule.classpathSeparator(),
              modulePath,
              problems);
      JavaModuleTargetPlatform platform =
          targetPlatform(configuredModule.targetJavaHome(), modulePath, problems);
      if (classpath == null || platform == null) {
        return java.util.Optional.empty();
      }
      return java.util.Optional.of(
          new V2ParsedModule(
              modulePath,
              sourceRoots,
              classpath,
              platform,
              target,
              new MavenProjectSettings(
                  mavenCoordinates(effectivePomModel, "effective-POM project"),
                  directDependencies(effectivePomModel))));
    } catch (IOException | IllegalArgumentException invalid) {
      projectSettingsProblem(
          modulePath == null ? "unknown" : modulePath, problems, invalid.getMessage());
      return java.util.Optional.empty();
    }
  }

  private static Path moduleProjectDirectory(Path projectDirectory, String modulePath) {
    Path moduleDirectory =
        ".".equals(modulePath)
            ? projectDirectory
            : projectDirectory.resolve(modulePath).normalize();
    if (!moduleDirectory.startsWith(projectDirectory)) {
      throw new IllegalArgumentException("the Maven module path is outside the project directory");
    }
    if (!Files.isDirectory(moduleDirectory)) {
      throw new IllegalArgumentException("the Maven module project directory is unavailable");
    }
    return moduleDirectory;
  }

  private static byte[] verifiedProjectPom(
      Path moduleProjectDirectory, String modulePath, VerifiedSourceTextSet sourceTexts)
      throws IOException {
    byte[] projectPom = readV2ProjectPom(moduleProjectDirectory.resolve("pom.xml"));
    VerifiedSourceTextDocument verifiedPom =
        sourceTexts.documents().stream()
            .filter(document -> modulePomPath(modulePath).equals(document.path()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "the selected Maven module has no exact R0 project POM"));
    if (!verifiedPom.sha256().value().equals(sha256(projectPom))) {
      throw new IllegalArgumentException(
          "the Maven project POM does not match the exact R0 project POM");
    }
    return projectPom;
  }

  private static byte[] readV2ProjectPom(Path projectPom) throws IOException {
    return readV2MavenProjectSettingsFile(projectPom, "project POM");
  }

  private static byte[] readV2EffectivePom(Path effectivePom) throws IOException {
    return readV2MavenProjectSettingsFile(effectivePom, "effective POM");
  }

  private static byte[] readV2MavenProjectSettingsFile(Path file, String label) throws IOException {
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("the named Maven " + label + " is unavailable: " + file);
    }
    try {
      return Files.readAllBytes(file);
    } catch (IOException unavailable) {
      throw new IllegalArgumentException(
          "the named Maven " + label + " cannot be read: " + file, unavailable);
    }
  }

  private static java.util.Optional<MavenProjectSettings> mavenEvaluatedProjectSettings(
      ObjectNode module,
      String modulePath,
      List<String> sourceRoots,
      JavaCompilationTarget declaredTarget,
      BuildFileEvidence buildFile,
      List<JavaCompilationEnvironmentProblem> problems) {
    if (buildFile == null) {
      projectSettingsProblem(
          modulePath, problems, "no exact R0 Maven POM is declared for this module");
      return java.util.Optional.empty();
    }
    try {
      Path projectDirectory = absoluteDirectory(text(module, "mavenProjectDirectory"));
      byte[] projectPom = readRegularFile(projectDirectory.resolve("pom.xml"), "project POM");
      if (!buildFile.sha256().equals(sha256(projectPom))) {
        throw new IllegalArgumentException("the Maven project POM is not the named R0 build file");
      }

      Path effectivePomFile = absolutePath(text(module, "effectivePomFile"));
      byte[] effectivePom = readRegularFile(effectivePomFile, "effective POM");
      if (!text(module, "effectivePomSha256").equals(sha256(effectivePom))) {
        throw new IllegalArgumentException("the Maven effective POM digest does not match");
      }

      Model projectPomModel = mavenModel(projectPom, "project POM");
      Model effectivePomModel = mavenModel(effectivePom, "effective POM");
      requireSameProjectIdentity(projectPomModel, effectivePomModel);
      String evaluatedSourceRoot =
          evaluatedSourceRoot(effectivePomModel, projectDirectory, modulePath);
      if (sourceRoots.size() != 1 || !sourceRoots.get(0).equals(evaluatedSourceRoot)) {
        throw new IllegalArgumentException(
            "the Maven evaluated sourceDirectory conflicts with the module source root");
      }
      JavaCompilationTarget evaluatedTarget = evaluatedCompilerTarget(effectivePomModel);
      if (!declaredTarget.equals(evaluatedTarget)) {
        throw new IllegalArgumentException(
            "the Maven evaluated compiler level conflicts with the module release or source/target");
      }
      return java.util.Optional.of(
          new MavenProjectSettings(
              mavenCoordinates(effectivePomModel, "effective-POM project"),
              directDependencies(effectivePomModel)));
    } catch (IOException | IllegalArgumentException invalid) {
      projectSettingsProblem(modulePath, problems, invalid.getMessage());
      return java.util.Optional.empty();
    }
  }

  private static void projectSettingsProblem(
      String modulePath, List<JavaCompilationEnvironmentProblem> problems, String reason) {
    problems.add(
        problem(
            "JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED",
            modulePath,
            "Maven evaluated project settings cannot be verified: " + reason));
  }

  private static boolean matchesMavenSourceModuleDependencies(
      List<ParsedModule> modules, List<JavaCompilationEnvironmentProblem> problems) {
    Map<MavenCoordinates, List<ParsedModule>> modulesByCoordinates = new LinkedHashMap<>();
    Map<String, ParsedModule> modulesByPath = new LinkedHashMap<>();
    for (ParsedModule module : modules) {
      modulesByCoordinates
          .computeIfAbsent(module.mavenSettings().coordinates(), ignored -> new ArrayList<>())
          .add(module);
      modulesByPath.putIfAbsent(module.module().modulePath(), module);
    }
    if (!uniqueSelectedModuleCoordinates(modulesByCoordinates, problems)) {
      return false;
    }
    Map<MavenCoordinates, ParsedModule> selectedByCoordinates = new LinkedHashMap<>();
    for (Map.Entry<MavenCoordinates, List<ParsedModule>> entry : modulesByCoordinates.entrySet()) {
      selectedByCoordinates.put(entry.getKey(), entry.getValue().get(0));
    }
    boolean matches = true;
    for (ParsedModule module : modules) {
      if (!matchesMavenSourceModuleDependencies(
          module, modulesByPath, selectedByCoordinates, problems)) {
        matches = false;
      }
    }
    return matches;
  }

  private static boolean uniqueSelectedModuleCoordinates(
      Map<MavenCoordinates, List<ParsedModule>> modulesByCoordinates,
      List<JavaCompilationEnvironmentProblem> problems) {
    boolean unique = true;
    for (Map.Entry<MavenCoordinates, List<ParsedModule>> entry : modulesByCoordinates.entrySet()) {
      if (entry.getValue().size() > 1) {
        for (ParsedModule module : entry.getValue()) {
          projectSettingsProblem(
              module.module().modulePath(),
              problems,
              "the Maven effective-POM project coordinate "
                  + entry.getKey()
                  + " is not unique among selected modules");
        }
        unique = false;
      }
    }
    return unique;
  }

  private static boolean matchesMavenSourceModuleDependencies(
      ParsedModule module,
      Map<String, ParsedModule> modulesByPath,
      Map<MavenCoordinates, ParsedModule> selectedByCoordinates,
      List<JavaCompilationEnvironmentProblem> problems) {
    Set<String> declared = new LinkedHashSet<>();
    boolean matches = true;
    for (SourceModuleDependency edge : module.module().sourceModuleDependencies()) {
      if (!modulesByPath.containsKey(edge.modulePath()) || !declared.add(edge.modulePath())) {
        projectSettingsProblem(
            module.module().modulePath(),
            problems,
            "the JSON source-module edges are not a unique set of selected Maven projects");
        matches = false;
      }
    }
    Set<String> effective = new LinkedHashSet<>();
    for (MavenDirectDependency dependency : module.mavenSettings().directDependencies()) {
      ParsedModule target = selectedByCoordinates.get(dependency.coordinates());
      if (target == null) {
        continue;
      }
      if (target == module) {
        projectSettingsProblem(
            module.module().modulePath(),
            problems,
            "the effective POM directly depends on its own selected Maven project coordinate");
        matches = false;
        continue;
      }
      if (!dependency.isSupportedSourceModuleDependency()) {
        projectSettingsProblem(
            module.module().modulePath(),
            problems,
            "the effective-POM direct dependency on selected module "
                + target.module().modulePath()
                + " is not a supported compile-relevant normal JAR dependency");
        matches = false;
        continue;
      }
      if (!effective.add(target.module().modulePath())) {
        projectSettingsProblem(
            module.module().modulePath(),
            problems,
            "the effective POM repeats a direct dependency on selected module "
                + target.module().modulePath());
        matches = false;
      }
    }
    if (!declared.equals(effective)) {
      projectSettingsProblem(
          module.module().modulePath(),
          problems,
          "the JSON source-module edges do not exactly match direct Maven effective-POM dependencies");
      matches = false;
    }
    return matches;
  }

  private static List<JavaCompilationInputModule> deriveSourceModuleDependencies(
      List<V2ParsedModule> modules, List<JavaCompilationEnvironmentProblem> problems) {
    Map<MavenCoordinates, List<V2ParsedModule>> modulesByCoordinates = new LinkedHashMap<>();
    for (V2ParsedModule module : modules) {
      modulesByCoordinates
          .computeIfAbsent(module.mavenSettings().coordinates(), ignored -> new ArrayList<>())
          .add(module);
    }
    Map<MavenCoordinates, V2ParsedModule> selectedByCoordinates = new LinkedHashMap<>();
    for (Map.Entry<MavenCoordinates, List<V2ParsedModule>> entry :
        modulesByCoordinates.entrySet()) {
      if (entry.getValue().size() != 1) {
        for (V2ParsedModule module : entry.getValue()) {
          projectSettingsProblem(
              module.modulePath(),
              problems,
              "the Maven effective-POM project coordinate "
                  + entry.getKey()
                  + " is not unique among selected modules");
        }
      } else {
        selectedByCoordinates.put(entry.getKey(), entry.getValue().get(0));
      }
    }
    if (!problems.isEmpty()) {
      return List.of();
    }

    List<JavaCompilationInputModule> derived = new ArrayList<>();
    for (V2ParsedModule module : modules) {
      List<SourceModuleDependency> edges = new ArrayList<>();
      Set<String> targets = new LinkedHashSet<>();
      for (MavenDirectDependency dependency : module.mavenSettings().directDependencies()) {
        V2ParsedModule target = selectedByCoordinates.get(dependency.coordinates());
        if (target == null) {
          continue;
        }
        if (target == module) {
          projectSettingsProblem(
              module.modulePath(),
              problems,
              "the effective POM directly depends on its own selected Maven project coordinate");
          continue;
        }
        if (!dependency.isSupportedSourceModuleDependency()) {
          projectSettingsProblem(
              module.modulePath(),
              problems,
              "the effective-POM direct dependency on selected module "
                  + target.modulePath()
                  + " is not a supported compile-relevant normal JAR dependency");
          continue;
        }
        if (!targets.add(target.modulePath())) {
          projectSettingsProblem(
              module.modulePath(),
              problems,
              "the effective POM repeats a direct dependency on selected module "
                  + target.modulePath());
          continue;
        }
        edges.add(new SourceModuleDependency(target.modulePath(), "SOURCE_MODULE"));
      }
      derived.add(
          new JavaCompilationInputModule(
              module.modulePath(),
              module.sourceRoots(),
              module.classpath().entries(),
              module.classpath().sha256(),
              module.targetPlatform(),
              module.compilationTarget(),
              edges));
    }
    return List.copyOf(derived);
  }

  private static MavenCoordinates mavenCoordinates(Model model, String label) {
    return new MavenCoordinates(
        requiredMavenValue(model.getGroupId(), label + " groupId"),
        requiredMavenValue(model.getArtifactId(), label + " artifactId"),
        requiredMavenValue(model.getVersion(), label + " version"));
  }

  private static List<MavenDirectDependency> directDependencies(Model effectivePom) {
    List<MavenDirectDependency> dependencies = new ArrayList<>();
    for (Dependency dependency : effectivePom.getDependencies()) {
      if (dependency == null) {
        throw new IllegalArgumentException(
            "the Maven effective POM has an invalid direct dependency");
      }
      dependencies.add(
          new MavenDirectDependency(
              new MavenCoordinates(
                  requiredMavenValue(dependency.getGroupId(), "effective-POM dependency groupId"),
                  requiredMavenValue(
                      dependency.getArtifactId(), "effective-POM dependency artifactId"),
                  requiredMavenValue(dependency.getVersion(), "effective-POM dependency version")),
              defaultedMavenValue(dependency.getScope(), "compile"),
              defaultedMavenValue(dependency.getType(), "jar"),
              nullableMavenValue(dependency.getClassifier()),
              defaultedMavenValue(dependency.getOptional(), "false")));
    }
    return List.copyOf(dependencies);
  }

  private static String defaultedMavenValue(String value, String defaultValue) {
    return value == null || value.isBlank()
        ? defaultValue
        : requiredMavenValue(value, "effective-POM dependency setting");
  }

  private static String nullableMavenValue(String value) {
    return value == null || value.isBlank()
        ? null
        : requiredMavenValue(value, "effective-POM dependency classifier");
  }

  private static Path absoluteDirectory(String value) {
    Path directory = absolutePath(value);
    if (!Files.isDirectory(directory)) {
      throw new IllegalArgumentException("the Maven project directory is unavailable");
    }
    return directory;
  }

  private static byte[] readRegularFile(Path file, String label) throws IOException {
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("the named Maven " + label + " is unavailable: " + file);
    }
    return Files.readAllBytes(file);
  }

  private static Model mavenModel(byte[] bytes, String label) {
    try {
      return new MavenXpp3Reader()
          .read(new StringReader(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception invalid) {
      throw new IllegalArgumentException("the named Maven " + label + " is not valid XML", invalid);
    }
  }

  private static void requireSameProjectIdentity(Model projectPom, Model effectivePom) {
    requireSameMavenValue(
        "artifactId",
        requiredMavenValue(projectPom.getArtifactId(), "project artifactId"),
        requiredMavenValue(effectivePom.getArtifactId(), "effective-POM artifactId"));
    requireSameDeclaredMavenValue("groupId", projectPom.getGroupId(), effectivePom.getGroupId());
    requireSameDeclaredMavenValue("version", projectPom.getVersion(), effectivePom.getVersion());
  }

  private static void requireSameDeclaredMavenValue(
      String field, String projectValue, String effectiveValue) {
    if (projectValue == null || projectValue.isBlank()) {
      return;
    }
    requireSameMavenValue(
        field,
        requiredMavenValue(projectValue, "project " + field),
        requiredMavenValue(effectiveValue, "effective-POM " + field));
  }

  private static void requireSameMavenValue(
      String field, String projectValue, String effectiveValue) {
    if (!projectValue.equals(effectiveValue)) {
      throw new IllegalArgumentException(
          "the Maven effective POM " + field + " does not identify the named project POM");
    }
  }

  private static String evaluatedSourceRoot(
      Model effectivePom, Path projectDirectory, String modulePath) {
    Build build = effectivePom.getBuild();
    if (build == null) {
      throw new IllegalArgumentException("the Maven effective POM has no build settings");
    }
    Path sourceDirectory =
        absolutePath(requiredMavenValue(build.getSourceDirectory(), "sourceDirectory"));
    if (!sourceDirectory.startsWith(projectDirectory)) {
      throw new IllegalArgumentException(
          "the Maven evaluated sourceDirectory is outside the named project directory");
    }
    Path relativeSourceDirectory = projectDirectory.relativize(sourceDirectory);
    String snapshotSourceRoot =
        ".".equals(modulePath)
            ? relativeSourceDirectory.toString()
            : Path.of(modulePath).resolve(relativeSourceDirectory).toString();
    return snapshotRelativePath(snapshotSourceRoot);
  }

  private static JavaCompilationTarget evaluatedCompilerTarget(Model effectivePom) {
    return evaluatedCompilerTarget(effectivePom, false);
  }

  private static JavaCompilationTarget evaluatedV2CompilerTarget(Model effectivePom) {
    return evaluatedCompilerTarget(effectivePom, true);
  }

  private static JavaCompilationTarget evaluatedCompilerTarget(
      Model effectivePom, boolean allowEquivalentExecutionSettings) {
    Build build = effectivePom.getBuild();
    if (build == null) {
      throw new IllegalArgumentException("the Maven effective POM has no build settings");
    }
    List<Plugin> compilerPlugins =
        build.getPlugins().stream()
            .filter(
                plugin ->
                    "org.apache.maven.plugins".equals(plugin.getGroupId())
                        && "maven-compiler-plugin".equals(plugin.getArtifactId()))
            .toList();
    if (compilerPlugins.size() != 1) {
      throw new IllegalArgumentException(
          "the Maven effective POM must contain exactly one maven-compiler-plugin setting");
    }
    Plugin compiler = compilerPlugins.get(0);
    if (!(compiler.getConfiguration() instanceof Xpp3Dom configuration)) {
      throw new IllegalArgumentException("the Maven compiler-plugin configuration is unavailable");
    }
    if (!allowEquivalentExecutionSettings
        && compiler.getExecutions().stream()
            .anyMatch(JavaCompilationInputReader::hasCompilerLevel)) {
      throw new IllegalArgumentException(
          "the Maven compiler-plugin execution settings are ambiguous for this handoff");
    }
    JavaCompilationTarget topLevelTarget = compilerTarget(configuration);
    for (org.apache.maven.model.PluginExecution execution : compiler.getExecutions()) {
      if (!hasCompilerLevel(execution)) {
        continue;
      }
      JavaCompilationTarget executionTarget =
          compilerTarget((Xpp3Dom) execution.getConfiguration());
      if (!topLevelTarget.equals(executionTarget)) {
        throw new IllegalArgumentException(
            "the Maven compiler-plugin execution settings differ from its top-level configuration");
      }
    }
    return topLevelTarget;
  }

  private static JavaCompilationTarget compilerTarget(Xpp3Dom configuration) {
    String release = uniqueCompilerSetting(configuration, "release");
    String source = uniqueCompilerSetting(configuration, "source");
    String target = uniqueCompilerSetting(configuration, "target");
    if (release != null && (source != null || target != null)) {
      throw new IllegalArgumentException(
          "the Maven compiler-plugin mixes release with source or target");
    }
    if (release == null && (source == null || target == null)) {
      throw new IllegalArgumentException(
          "the Maven compiler-plugin has no complete release or source/target setting");
    }
    return new JavaCompilationTarget(release, source, target);
  }

  private static boolean hasCompilerLevel(org.apache.maven.model.PluginExecution execution) {
    if (!(execution.getConfiguration() instanceof Xpp3Dom configuration)) {
      return false;
    }
    return configuration.getChildren("release").length > 0
        || configuration.getChildren("source").length > 0
        || configuration.getChildren("target").length > 0;
  }

  private static String uniqueCompilerSetting(Xpp3Dom configuration, String name) {
    Xpp3Dom[] settings = configuration.getChildren(name);
    if (settings.length > 1) {
      throw new IllegalArgumentException(
          "the Maven compiler-plugin " + name + " setting is repeated");
    }
    return settings.length == 0
        ? null
        : requiredMavenValue(settings[0].getValue(), "compiler-plugin " + name);
  }

  private static String requiredMavenValue(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("the Maven " + label + " is missing");
    }
    String normalized = value.trim();
    if (normalized.contains("${")) {
      throw new IllegalArgumentException("the Maven " + label + " remains unevaluated");
    }
    return normalized;
  }

  private static List<String> sourceRoots(ObjectNode module, String modulePath) {
    ArrayNode configuredRoots = requiredArray(module, "sourceRoots");
    if (configuredRoots.isEmpty()) {
      throw new IllegalArgumentException("module source roots are required");
    }
    List<String> roots = new ArrayList<>();
    for (JsonNode root : configuredRoots) {
      String value = snapshotRelativePath(root.asText(null));
      if (!(modulePath.equals(".")
          || value.equals(modulePath)
          || value.startsWith(modulePath + "/"))) {
        throw new IllegalArgumentException("module source root is outside its module path");
      }
      roots.add(value);
    }
    if (new LinkedHashSet<>(roots).size() != roots.size()) {
      throw new IllegalArgumentException("module source roots are duplicated");
    }
    return List.copyOf(roots);
  }

  private static ClasspathExport classpath(
      ObjectNode module, String modulePath, List<JavaCompilationEnvironmentProblem> problems) {
    final Path classpathFile;
    try {
      classpathFile = absolutePath(text(module, "classpathFile"));
    } catch (IllegalArgumentException invalid) {
      problems.add(
          problem(
              "JAVA_COMPILATION_INPUT_INVALID",
              modulePath,
              "module classpath file path is invalid"));
      return null;
    }
    if (!Files.isRegularFile(classpathFile)) {
      problems.add(
          problem(
              "JAVA_COMPILATION_INPUT_FILE_MISSING",
              modulePath,
              "module classpath export file is unavailable"));
      return null;
    }
    final String separator;
    try {
      separator = classpathSeparator(text(module, "classpathSeparator"));
      String content = Files.readString(classpathFile);
      if (content.isBlank()) {
        return new ClasspathExport(
            List.of(), sha256(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      }
      List<Path> entries = new ArrayList<>();
      for (String entry : content.split(Pattern.quote(separator), -1)) {
        Path path = absolutePath(entry);
        if (!Files.isRegularFile(path)) {
          problems.add(
              problem(
                  "JAVA_COMPILATION_INPUT_FILE_MISSING",
                  modulePath,
                  "module classpath export names an unavailable dependency"));
          continue;
        }
        entries.add(path);
      }
      return new ClasspathExport(
          List.copyOf(entries), sha256(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (IOException | IllegalArgumentException invalid) {
      problems.add(
          problem(
              "JAVA_COMPILATION_INPUT_INVALID",
              modulePath,
              "module classpath export cannot be read with its declared separator"));
      return null;
    }
  }

  private static ClasspathExport classpath(
      Path classpathFile,
      String configuredSeparator,
      String modulePath,
      List<JavaCompilationEnvironmentProblem> problems) {
    if (!Files.isRegularFile(classpathFile)) {
      problems.add(
          problem(
              "JAVA_COMPILATION_INPUT_FILE_MISSING",
              modulePath,
              "module classpath export file is unavailable"));
      return null;
    }
    try {
      String separator = classpathSeparator(configuredSeparator);
      String content = Files.readString(classpathFile);
      if (content.isBlank()) {
        return new ClasspathExport(
            List.of(), sha256(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      }
      List<Path> entries = new ArrayList<>();
      for (String entry : content.split(Pattern.quote(separator), -1)) {
        Path path = absolutePath(entry);
        if (!Files.isRegularFile(path)) {
          problems.add(
              problem(
                  "JAVA_COMPILATION_INPUT_FILE_MISSING",
                  modulePath,
                  "module classpath export names an unavailable dependency"));
          continue;
        }
        entries.add(path);
      }
      return new ClasspathExport(
          List.copyOf(entries), sha256(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (IOException | IllegalArgumentException invalid) {
      problems.add(
          problem(
              "JAVA_COMPILATION_INPUT_INVALID",
              modulePath,
              "module classpath export cannot be read with its declared separator"));
      return null;
    }
  }

  private static JavaModuleTargetPlatform targetPlatform(
      ObjectNode module, String modulePath, List<JavaCompilationEnvironmentProblem> problems) {
    try {
      return new JavaModuleTargetPlatform(
          modulePath,
          text(module, "executionEnvironmentName"),
          absolutePath(text(module, "targetJdkHome")),
          text(module, "targetJdkVersion"));
    } catch (IllegalArgumentException invalid) {
      problems.add(
          problem(
              "JAVA_TARGET_PLATFORM_INVALID", modulePath, "module target JDK cannot be verified"));
      return null;
    }
  }

  private static JavaModuleTargetPlatform targetPlatform(
      Path targetJavaHome, String modulePath, List<JavaCompilationEnvironmentProblem> problems) {
    try {
      return JavaModuleTargetPlatform.fromTargetJdkHome(modulePath, targetJavaHome);
    } catch (IllegalArgumentException invalid) {
      problems.add(
          problem(
              "JAVA_TARGET_PLATFORM_INVALID", modulePath, "module target JDK cannot be verified"));
      return null;
    }
  }

  private static JavaCompilationTarget compilationTarget(ObjectNode module) {
    String release = optionalText(module, "release");
    String source = optionalText(module, "source");
    String target = optionalText(module, "target");
    return new JavaCompilationTarget(release, source, target);
  }

  private static List<SourceModuleDependency> sourceModuleDependencies(ObjectNode module) {
    JsonNode configuredEdges = module.get("sourceModuleDependencies");
    if (configuredEdges == null) {
      return List.of();
    }
    if (!(configuredEdges instanceof ArrayNode edges)) {
      throw new IllegalArgumentException("module source edges are invalid");
    }
    List<SourceModuleDependency> dependencies = new ArrayList<>();
    for (JsonNode configuredEdge : edges) {
      if (!(configuredEdge instanceof ObjectNode edge)) {
        throw new IllegalArgumentException("module source edge is invalid");
      }
      dependencies.add(
          new SourceModuleDependency(
              snapshotRelativePath(text(edge, "modulePath")), text(edge, "kind")));
    }
    return List.copyOf(dependencies);
  }

  private static ArrayNode array(
      ObjectNode parent, String field, List<JavaCompilationEnvironmentProblem> problems) {
    JsonNode value = parent.get(field);
    if (value instanceof ArrayNode array) {
      return array;
    }
    problems.add(problem("JAVA_COMPILATION_INPUT_INVALID", field + " must be an array"));
    return null;
  }

  private static ArrayNode requiredArray(ObjectNode parent, String field) {
    JsonNode value = parent.get(field);
    if (!(value instanceof ArrayNode array)) {
      throw new IllegalArgumentException(field + " must be an array");
    }
    return array;
  }

  private static String text(ObjectNode parent, String field) {
    String value = optionalText(parent, field);
    if (value == null) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value;
  }

  private static String optionalText(ObjectNode parent, String field) {
    JsonNode value = parent.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      return null;
    }
    return value.textValue();
  }

  private static String snapshotRelativePath(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("snapshot-relative path is required");
    }
    try {
      Path path = Path.of(value).normalize();
      if (path.isAbsolute() || path.startsWith("..")) {
        throw new IllegalArgumentException("snapshot-relative path is outside R0");
      }
      String normalized = path.toString().replace('\\', '/');
      return normalized.isEmpty() || ".".equals(normalized) ? "." : normalized;
    } catch (InvalidPathException invalid) {
      throw new IllegalArgumentException("snapshot-relative path is invalid", invalid);
    }
  }

  private static Path absolutePath(String value) {
    try {
      Path path = Path.of(value).normalize();
      if (!path.isAbsolute()) {
        throw new IllegalArgumentException("external path must be absolute");
      }
      return path;
    } catch (InvalidPathException invalid) {
      throw new IllegalArgumentException("external path is invalid", invalid);
    }
  }

  private static String modulePomPath(String modulePath) {
    return ".".equals(modulePath) ? "pom.xml" : modulePath + "/pom.xml";
  }

  private static String classpathSeparator(String value) {
    if (value == null || value.length() != 1) {
      throw new IllegalArgumentException("classpath separator must be one character");
    }
    return value;
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static JavaCompilationEnvironmentResult blocked(
      List<JavaCompilationEnvironmentProblem> problems) {
    return new JavaCompilationEnvironmentResult(
        JavaCompilationEnvironmentStatus.BLOCKED, null, List.copyOf(problems));
  }

  private static JavaCompilationEnvironmentProblem problem(String code, String detail) {
    return new JavaCompilationEnvironmentProblem(code, null, detail);
  }

  private static JavaCompilationEnvironmentProblem problem(
      String code, String modulePath, String detail) {
    return new JavaCompilationEnvironmentProblem(code, modulePath, detail);
  }

  private record BuildFileEvidence(String path, String sha256) {}

  private record ClasspathExport(List<Path> entries, String sha256) {}

  private record ParsedModule(
      JavaCompilationInputModule module, MavenProjectSettings mavenSettings) {}

  private record V2ParsedModule(
      String modulePath,
      List<String> sourceRoots,
      ClasspathExport classpath,
      JavaModuleTargetPlatform targetPlatform,
      JavaCompilationTarget compilationTarget,
      MavenProjectSettings mavenSettings) {

    private V2ParsedModule {
      modulePath = Objects.requireNonNull(modulePath, "Maven module path");
      sourceRoots = List.copyOf(Objects.requireNonNull(sourceRoots, "Maven source roots"));
      classpath = Objects.requireNonNull(classpath, "Maven classpath export");
      targetPlatform = Objects.requireNonNull(targetPlatform, "Maven target platform");
      compilationTarget = Objects.requireNonNull(compilationTarget, "Maven compilation target");
      mavenSettings = Objects.requireNonNull(mavenSettings, "Maven project settings");
    }
  }

  private record MavenProjectSettings(
      MavenCoordinates coordinates, List<MavenDirectDependency> directDependencies) {

    private MavenProjectSettings {
      coordinates = Objects.requireNonNull(coordinates, "Maven project coordinates");
      directDependencies =
          List.copyOf(Objects.requireNonNull(directDependencies, "Maven dependencies"));
    }
  }

  private record MavenCoordinates(String groupId, String artifactId, String version) {

    private MavenCoordinates {
      groupId = Objects.requireNonNull(groupId, "Maven groupId");
      artifactId = Objects.requireNonNull(artifactId, "Maven artifactId");
      version = Objects.requireNonNull(version, "Maven version");
    }
  }

  private record MavenDirectDependency(
      MavenCoordinates coordinates, String scope, String type, String classifier, String optional) {

    private boolean isSupportedSourceModuleDependency() {
      return ("compile".equals(scope) || "provided".equals(scope))
          && "jar".equals(type)
          && classifier == null
          && "false".equalsIgnoreCase(optional);
    }
  }
}
