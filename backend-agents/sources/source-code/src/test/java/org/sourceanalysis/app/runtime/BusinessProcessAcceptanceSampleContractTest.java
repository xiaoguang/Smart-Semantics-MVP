package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;

/** RED contract for selecting real model-discovered Step07 candidates from a complete catalog. */
class BusinessProcessAcceptanceSampleContractTest {

  private static final ModelRuntimeIdentityV1 IDENTITY =
      new ModelRuntimeIdentityV1("scripted", "fixture-model", "high", "read-only");

  @TempDir Path temporaryDirectory;

  @Test
  void runnerExposesPackageInternalExecutionSeamForACompleteCandidatePair() {
    Class<?> runner = requiredRunner();

    Method execution =
        Arrays.stream(runner.getDeclaredMethods())
            .filter(method -> method.getName().equals("runCandidateSample"))
            .findFirst()
            .orElseGet(
                () -> {
                  fail("PROCESS_ACCEPTANCE_SAMPLE_EXECUTION_NOT_IMPLEMENTED");
                  return null;
                });

    assertThat(execution).isNotNull();
    assertThat(Modifier.isPublic(execution.getModifiers()))
        .as("the acceptance runner is an internal test seam, not a second public API")
        .isFalse();
  }

  @Test
  void selectionPreservesFullCatalogOrdinalAndNormalProviderBinding() throws Exception {
    Class<?> runner = requiredRunner();
    Method selector;
    try {
      selector =
          runner.getDeclaredMethod(
              "selectCandidates", JsonNode.class, List.class, ModelJobExecutionConfiguration.class);
    } catch (NoSuchMethodException missing) {
      fail("PROCESS_ACCEPTANCE_SAMPLE_SELECTION_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable", missing);
    }
    selector.setAccessible(true);

    Object result = invoke(selector, completeCatalog(43), List.of("candidate-17", "candidate-42"), execution());
    assertThat(result).isInstanceOf(List.class);
    List<?> selected = (List<?>) result;
    assertThat(selected).hasSize(2);

    assertThat(selected)
        .extracting(
            value -> property(value, "candidateId"),
            value -> property(value, "catalogOrdinal"),
            value -> property(value, "providerBindingKey"))
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("candidate-17", 17, "api"),
            org.assertj.core.groups.Tuple.tuple("candidate-42", 42, "pro"));
  }

  @Test
  void selectionUsesCandidatesFromTheCompleteCatalogWithoutFixtureDomainNames() throws Exception {
    Class<?> runner = requiredRunner();
    Method selector;
    try {
      selector =
          runner.getDeclaredMethod(
              "selectCandidates", JsonNode.class, List.class, ModelJobExecutionConfiguration.class);
    } catch (NoSuchMethodException missing) {
      fail("PROCESS_ACCEPTANCE_SAMPLE_SELECTION_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable", missing);
    }
    selector.setAccessible(true);

    ObjectNode catalog = completeCatalog(10);
    List<?> selected =
        (List<?>)
            invoke(
                selector,
                catalog,
                List.of("candidate-3", "candidate-9"),
                execution());
    assertThat(selected)
        .extracting(value -> property(value, "candidateId"))
        .containsExactly("candidate-3", "candidate-9");
    assertThat(catalog.toString()).doesNotContain("销售", "采购", "库存", "管伊佳");
  }

  @Test
  void sampleExecutionSavesCompletePairsThatFormalRunCanExplicitlyReuse() throws Exception {
    Class<?> runner = requiredRunner();
    Method execution = requiredMethod(runner, "runCandidateSample");
    assertThat(Modifier.isPublic(execution.getModifiers()))
        .as("the acceptance runner is an internal test seam, not a second public API")
        .isFalse();

    // The actual acceptance seam owns the formal corpus/material inputs. This test only supplies
    // two IDs selected after reviewing the full catalog and a fresh configured model batch.
    Object result =
        invoke(
            execution,
            completeCatalog(43),
            List.of("candidate-17", "candidate-42"),
            execution());
    assertThat(result).isNotNull();
    assertThat(property(result, "selectedCandidateIds"))
        .as("the sample result keeps the selected model-discovered IDs")
        .isEqualTo(List.of("candidate-17", "candidate-42"));
    assertThat(property(result, "savedReviewedPairJobKeys"))
        .as("the sample must persist a complete pair per selected candidate")
        .isEqualTo(List.of("candidate-17", "candidate-42"));
    assertThat((Map<?, ?>) property(result, "upstreamCallCounts"))
        .as("acceptance sample starts from existing material and does not run source/activity work")
        .isEqualTo(Map.of("jdt", 0, "businessMaterialBuilder", 0, "activityExplainer", 0, "step08", 0));
  }

  private static Class<?> requiredRunner() {
    try {
      return Class.forName("org.sourceanalysis.app.runtime.BusinessProcessAcceptanceRunner");
    } catch (ClassNotFoundException missing) {
      fail("PROCESS_ACCEPTANCE_SAMPLE_RUNNER_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable", missing);
    }
  }

  private static Method requiredMethod(Class<?> type, String name) {
    return Arrays.stream(type.getDeclaredMethods())
        .filter(method -> method.getName().equals(name))
        .findFirst()
        .map(
            method -> {
              method.setAccessible(true);
              return method;
            })
        .orElseGet(
            () -> {
              fail("PROCESS_ACCEPTANCE_SAMPLE_" + name.toUpperCase() + "_NOT_IMPLEMENTED");
              return null;
            });
  }

  private static Object invoke(Method method, Object... arguments) throws Exception {
    try {
      return method.invoke(null, arguments);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new AssertionError(cause);
    }
  }

  private static Object property(Object value, String name) {
    try {
      Method accessor = value.getClass().getDeclaredMethod(name);
      accessor.setAccessible(true);
      return accessor.invoke(value);
    } catch (ReflectiveOperationException failure) {
      fail("PROCESS_ACCEPTANCE_SAMPLE_SELECTION_PROPERTY_MISSING:" + name, failure);
      throw new AssertionError("unreachable", failure);
    }
  }

  private static ObjectNode completeCatalog(int size) {
    ObjectNode catalog = JsonNodeFactory.instance.objectNode();
    ArrayNode candidates = catalog.putArray("candidateProcesses");
    for (int ordinal = 0; ordinal < size; ordinal++) {
      candidates.addObject().put("candidateId", "candidate-" + ordinal);
    }
    return catalog;
  }

  private ModelJobExecutionConfiguration execution() throws Exception {
    StructuredModelProvider provider =
        request -> {
          throw new AssertionError("selection contract must not invoke a model provider");
        };
    ModelJobProviderBinding pro =
        new ModelJobProviderBinding("pro", "pro-account", 2, provider, IDENTITY);
    ModelJobProviderBinding api =
        new ModelJobProviderBinding("api", "api-account", 2, provider, IDENTITY);
    return new ModelJobExecutionConfiguration(
        4,
        Map.of("pro", pro, "api", api),
        Map.of(
            "activity", List.of("pro"),
            "processGroup", List.of("pro", "api"),
            "repositorySummary", List.of("pro"),
            "report", List.of("pro")),
        Files.createDirectories(temporaryDirectory.resolve("journal")),
        AnalysisRunId.parse("analysis-run:" + "b".repeat(64)));
  }
}
