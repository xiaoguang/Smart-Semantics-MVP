package org.sourceanalysis.app.adapter.cli;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.sourceanalysis.app.analysis.knowledge.DefaultBusinessProcessDiscovery;
import org.sourceanalysis.app.analysis.knowledge.ProcessDiscoveryRequest;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.analysis.material.publish.CodeReadingMaterialReader;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.runtime.AnalysisRunLifecycleState;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;

/**
 * Standalone acceptance tool for a fixed, complete Step05/Activity corpus.
 *
 * <p>It uses the same validated request and private model-job identity as the formal command. A
 * sample never publishes repository coverage or pretends to be a whole-repository result.
 */
final class Step05ProcessSampleDriver {
  private Step05ProcessSampleDriver() {}

  public static void main(String[] arguments) {
    try {
      run(parse(arguments));
    } catch (Exception failure) {
      if (failure instanceof SampleArgumentsException) {
        System.err.println("STEP05_SAMPLE_ARGUMENTS_INVALID");
      } else {
        System.err.println("STEP05_SAMPLE_FAILED:" + SourceAnalysisExecution.code(failure));
        failure.printStackTrace(System.err);
      }
      System.exit(2);
    }
  }

  private static Arguments parse(String[] values) {
    if (values == null || values.length < 5) {
      throw new SampleArgumentsException();
    }
    String mode = values[2];
    if (!List.of("catalog", "selected").contains(mode)) {
      throw new SampleArgumentsException();
    }
    Path config;
    Path output;
    AnalysisRunId activities;
    AnalysisRunId reuse;
    try {
      config = Path.of(values[0]);
      output = Path.of(values[4]);
      activities = AnalysisRunId.parse(values[1]);
      reuse = "-".equals(values[3]) ? null : AnalysisRunId.parse(values[3]);
    } catch (RuntimeException invalid) {
      throw new SampleArgumentsException();
    }
    if (!config.isAbsolute()
        || !output.isAbsolute()
        || output.getParent() == null
        || !Files.isDirectory(output.getParent())
        || Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
      throw new SampleArgumentsException();
    }
    Map<String, String> selected = new LinkedHashMap<>();
    for (int index = 5; index < values.length; index++) {
      String[] pair = values[index].split("=", 2);
      if (pair.length != 2
          || !pair[0].matches("[a-z][a-z0-9-]{0,39}")
          || pair[1].isBlank()
          || selected.putIfAbsent(pair[0], pair[1]) != null) {
        throw new SampleArgumentsException();
      }
    }
    if (("catalog".equals(mode) && (reuse != null || !selected.isEmpty()))
        || ("selected".equals(mode)
            && (reuse == null
                || selected.isEmpty()
                || selected.size() > 3
                || selected.values().stream().distinct().count() != selected.size()))) {
      throw new SampleArgumentsException();
    }
    return new Arguments(
        config,
        activities,
        mode,
        reuse,
        output,
        Collections.unmodifiableMap(new LinkedHashMap<>(selected)));
  }

  private static void run(Arguments arguments) throws Exception {
    RepositoryRunConfiguration configuration = RepositoryRunConfiguration.load(arguments.config());
    ModelJobsConfiguration modelJobs = configuration.requireModelJobsForExecution();
    RepositoryRunStateV4.SavedState state =
        RepositoryRunStateV4.load(configuration.stateFile(), configuration.canonicalJson());
    if (!state.materialProfile().equals(configuration.readingMaterialProfile())) {
      throw SourceAnalysisExecution.failure("MATERIALS_STATE_CONFIGURATION_MISMATCH");
    }
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      CanonicalAnalysisStepArtifactStore inputSteps =
          SourceAnalysisExecution.inputStepArtifacts(configuration, store);
      CanonicalModuleArtifactStore outputModules =
          SourceAnalysisExecution.moduleArtifacts(configuration, store);
      CodeReadingMaterialSet materials =
          new CodeReadingMaterialReader(inputSteps).reopen(state.readingMaterialCheckpoint());
      SourceAnalysisExecution.verifyConfiguredStep05Source(configuration, store, state);
      AnalysisRunReference running =
          SourceAnalysisExecution.startModelBatch(
              store,
              state.sourceRunId(),
              SourceAnalysisExecution.artifactReference(configuration.policyRegistry().reference()),
              null);
      System.out.println("allocatedModelBatchId=" + running.runId().value());
      System.out.flush();
      try {
        SourceAnalysisExecution.validateStep05ReuseBatch(
            store, modelJobs, state, running.runId(), arguments.reuse());
        ProcessDiscoveryRequest request =
            SourceAnalysisExecution.assembleStep05ProcessDiscoveryRequest(
                configuration,
                store,
                outputModules,
                inputSteps,
                state,
                materials,
                arguments.activityBatch(),
                running.runId(),
                null);
        SourceAnalysisExecution.validateProcessReuseActivity(
            modelJobs, request.activities().checkpoint(), arguments.reuse());
        SourceAnalysisExecution.validateStep05ProcessReuseReadingInputs(
            configuration, modelJobs, arguments.reuse(), request);
        SourceAnalysisExecution.writeStep05ProcessModelJobExecutionConfiguration(
            configuration, modelJobs, state, request, running.runId(), arguments.reuse());
        ModelJobExecutionConfiguration execution =
            SourceAnalysisExecution.modelJobExecutionConfiguration(
                modelJobs,
                running.runId(),
                arguments.reuse(),
                configuration.activityReadingProfile());
        DefaultBusinessProcessDiscovery discovery =
            DefaultBusinessProcessDiscovery.forExecution(execution);
        Object catalog = invoke(discovery, "discoverCatalogSample", request);
        ObjectNode manifest = manifest(arguments, request, running.runId(), catalog, execution);
        Files.createDirectory(arguments.output());
        if ("selected".equals(arguments.mode())) {
          exportSelected(arguments, discovery, catalog, manifest);
        }
        writeJson(arguments.output().resolve("sample-manifest.json"), manifest);
        RunStoreBootstrap.transitionAnalysisRun(
            store,
            running.runId(),
            AnalysisRunLifecycleState.RUNNING,
            AnalysisRunLifecycleState.FINISHED);
        System.out.println("completedModelBatchId=" + running.runId().value());
        System.out.println("sampleManifest=" + arguments.output().resolve("sample-manifest.json"));
      } catch (Exception failure) {
        SourceAnalysisExecution.markFailed(
            store,
            running.runId(),
            failure instanceof RuntimeException runtime
                ? runtime
                : new IllegalStateException("STEP05_SAMPLE_FAILED", failure));
        throw failure;
      }
    }
  }

  private static ObjectNode manifest(
      Arguments arguments,
      ProcessDiscoveryRequest request,
      AnalysisRunId runId,
      Object catalog,
      ModelJobExecutionConfiguration execution)
      throws Exception {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("schemaVersion", "step05-process-sample-manifest-v1");
    document.put("wholeRepositoryCompleted", false);
    document.put("mode", arguments.mode());
    document.put("modelBatchId", runId.value());
    document.put("activityModelBatchId", arguments.activityBatch().value());
    document.put("sourceRunId", request.codeReadingMaterialCheckpoint().address().runId().value());
    document.putNull("focusQuestion");
    document.put("activityCount", request.activities().reviewedActivities().size());
    Object selection = access(catalog, "selection");
    document.set("systemAssessment", ((ObjectNode) access(selection, "systemAssessment")).deepCopy());
    ArrayNode candidates = document.putArray("candidateTable");
    @SuppressWarnings("unchecked")
    List<Object> found = (List<Object>) access(access(catalog, "catalog"), "candidates");
    for (int ordinal = 0; ordinal < found.size(); ordinal++) {
      Object candidate = found.get(ordinal);
      ObjectNode row = ((ObjectNode) access(candidate, "toJson")).deepCopy();
      row.put("ordinal", ordinal);
      row.put("providerBinding", execution.binding("processGroup", ordinal).key());
      candidates.add(row);
    }
    document.putArray("selectedCandidates");
    return document;
  }

  private static void exportSelected(
      Arguments arguments,
      DefaultBusinessProcessDiscovery discovery,
      Object catalog,
      ObjectNode manifest)
      throws Exception {
    List<String> selectedIds = List.copyOf(arguments.selected().values());
    Object preview = invoke(discovery, "reconstructSelectedPreview", catalog, selectedIds);
    @SuppressWarnings("unchecked")
    List<Object> candidates = (List<Object>) access(preview, "candidates");
    @SuppressWarnings("unchecked")
    List<Object> sources = (List<Object>) access(preview, "sourceReferences");
    ArrayNode selected = (ArrayNode) manifest.path("selectedCandidates");
    List<String> index = new ArrayList<>();
    index.add("# 已完成的业务过程小样\n");
    index.add("仅包含下列已审候选；不是全仓业务过程发布。\n");
    for (Map.Entry<String, String> label : arguments.selected().entrySet()) {
      Object candidate =
          candidates.stream()
              .filter(
                  item -> {
                    try {
                      return label.getValue().equals(access(item, "candidateId"));
                    } catch (Exception failure) {
                      throw new IllegalStateException(failure);
                    }
                  })
              .findFirst()
              .orElseThrow(() -> new IllegalStateException("STEP05_SAMPLE_CANDIDATE_MISSING"));
      @SuppressWarnings("unchecked")
      List<Object> processes = (List<Object>) access(candidate, "processes");
      Path directory = arguments.output().resolve(label.getKey());
      Files.createDirectory(directory);
      write(
          directory.resolve("business-processes.md"),
          (String) render("BusinessProcessMarkdownRenderer", "renderPreview", processes, sources));
      write(
          directory.resolve("sources.md"),
          (String) render("SourcesMarkdownRenderer", "render", sources));
      ObjectNode row = selected.addObject();
      row.put("label", label.getKey());
      row.put("candidateId", label.getValue());
      row.put("ordinal", ((Number) access(candidate, "ordinal")).intValue());
      row.put("processFragments", processes.size());
      row.put("disposition", (String) access(candidate, "disposition"));
      row.put("reason", (String) access(candidate, "reason"));
      index.add(
          "- ["
              + label.getKey()
              + "]("
              + label.getKey()
              + "/business-processes.md)："
              + processes.size()
              + " 个片段。\n");
    }
    write(arguments.output().resolve("README.md"), String.join("", index));
  }

  private static Object render(String className, String methodName, Object... values)
      throws Exception {
    Class<?> owner = Class.forName("org.sourceanalysis.app.analysis.knowledge." + className);
    Method method =
        Arrays.stream(owner.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(methodName))
            .filter(candidate -> candidate.getParameterCount() == values.length)
            .findFirst()
            .orElseThrow();
    method.setAccessible(true);
    return invokeMethod(null, method, values);
  }

  private static Object invoke(Object receiver, String name, Object... values) throws Exception {
    Method method =
        Arrays.stream(receiver.getClass().getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(name))
            .filter(candidate -> candidate.getParameterCount() == values.length)
            .findFirst()
            .orElseThrow();
    method.setAccessible(true);
    return invokeMethod(receiver, method, values);
  }

  private static Object access(Object receiver, String name) throws Exception {
    Method method =
        Arrays.stream(receiver.getClass().getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(name))
            .filter(candidate -> candidate.getParameterCount() == 0)
            .findFirst()
            .orElse(null);
    if (method != null) {
      method.setAccessible(true);
      return invokeMethod(receiver, method);
    }
    Field field = receiver.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(receiver);
  }

  private static Object invokeMethod(Object receiver, Method method, Object... values)
      throws Exception {
    try {
      return method.invoke(receiver, values);
    } catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof Exception checked) {
        throw checked;
      }
      throw failure;
    }
  }

  private static void writeJson(Path path, ObjectNode document) throws Exception {
    Files.write(
        path,
        new org.sourceanalysis.app.artifact.CanonicalJsonCodec()
            .encodeCanonical(document)
            .copyToByteArray(),
        StandardOpenOption.CREATE_NEW);
  }

  private static void write(Path path, String value) throws Exception {
    Files.writeString(path, value, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
  }

  private record Arguments(
      Path config,
      AnalysisRunId activityBatch,
      String mode,
      AnalysisRunId reuse,
      Path output,
      Map<String, String> selected) {}

  private static final class SampleArgumentsException extends IllegalArgumentException {}
}
