package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** RED contracts for the Step 05 entry-evidence artifact query and Step 07 boundary. */
class TechnicalEntryEvidenceArtifactQueryRedContractTest {

  @Test
  void registersIndexEntryAndFrontendCoverageArtifactQueriesWithoutRemovingHistoricalQueries() {
    Set<String> names =
        Arrays.stream(TechnicalArtifactQueryKey.values())
            .map(Enum::name)
            .collect(java.util.stream.Collectors.toSet());

    assertThat(names)
        .contains(
            "ENTRY_EVIDENCE_INDEX",
            "ENTRY_EVIDENCE",
            "FRONTEND_EVIDENCE_COVERAGE",
            "CODE_READING_MATERIALS",
            "CODE_READING_MATERIALS_V2");
  }

  @Test
  void listsEntryEvidenceArtifactsAsDiscoverableTechnicalOutputs() {
    assertThat(Arrays.stream(TechnicalOutputArtifactKey.values()).map(Enum::name).toList())
        .as("inspect must expose the new index, per-entry, and frontend coverage groups")
        .contains(
            "ENTRY_EVIDENCE_INDEX",
            "ENTRY_EVIDENCE",
            "FRONTEND_EVIDENCE_COVERAGE",
            "CODE_READING_MATERIALS");
  }

  @Test
  void configuredArtifactCliRequiresEntryIdOnlyForEntryFiles() throws Exception {
    List<String> validEntryOptions =
        List.of(
            "--run",
            "analysis-run:" + "a".repeat(64),
            "--key",
            "ENTRY_EVIDENCE",
            "--entry-id",
            "entry:" + "f".repeat(64),
            "--max-bytes",
            "4096");
    assertThatCode(() -> parseConfiguredInvocation("artifact", validEntryOptions))
        .doesNotThrowAnyException();

    List<String> indexWithEntryId =
        List.of(
            "--run",
            "analysis-run:" + "a".repeat(64),
            "--key",
            "ENTRY_EVIDENCE_INDEX",
            "--entry-id",
            "entry:" + "f".repeat(64),
            "--max-bytes",
            "4096");
    assertThatThrownBy(() -> parseConfiguredInvocation("artifact", indexWithEntryId))
        .hasRootCauseInstanceOf(RuntimeException.class);
  }

  @Test
  void entryQueryCarriesTheCompleteEntryIdAndRejectsFilesystemPathInput() throws Exception {
    RecordComponent entryId =
        Arrays.stream(ArtifactQuery.class.getRecordComponents())
            .filter(component -> "entryId".equals(component.getName()))
            .findFirst()
            .orElse(null);
    assertThat(entryId)
        .as("an entry-evidence query must select by its complete entry identity")
        .isNotNull();
    assertThat(entryId.getType()).isEqualTo(String.class);

    Constructor<?> constructor =
        Arrays.stream(ArtifactQuery.class.getDeclaredConstructors())
            .filter(value -> value.getParameterCount() == 5)
            .filter(
                value ->
                    Arrays.stream(value.getParameterTypes()).filter(String.class::equals).count()
                        == 2)
            .findFirst()
            .orElse(null);
    assertThat(constructor)
        .as("the typed artifact query must have an entry-id-bearing construction seam")
        .isNotNull();

    Object queryKey = Enum.valueOf(TechnicalArtifactQueryKey.class, "ENTRY_EVIDENCE");
    String entryIdValue = "entry:" + "a".repeat(64);
    RecordComponent[] components = ArtifactQuery.class.getRecordComponents();
    Object[] validArguments =
        Arrays.stream(components)
            .map(
                component -> {
                  return switch (component.getName()) {
                    case "runId" -> "analysis-run:" + "b".repeat(64);
                    case "entryId" -> entryIdValue;
                    case "technicalArtifactQueryKey" -> queryKey;
                    case "businessOutputArtifactKey" -> null;
                    case "maxBytes" -> 4096;
                    default ->
                        throw new AssertionError(
                            "unexpected ArtifactQuery field " + component.getName());
                  };
                })
            .toArray();
    Object query = constructor.newInstance(validArguments);
    Method entryIdAccessor = ArtifactQuery.class.getMethod("entryId");
    assertThat(entryIdAccessor.invoke(query)).isEqualTo(entryIdValue);

    int entryIdIndex =
        Arrays.stream(components).map(RecordComponent::getName).toList().indexOf("entryId");
    validArguments[entryIdIndex] = "../entry-" + "a".repeat(64) + ".json";
    assertThatThrownBy(() -> constructor.newInstance(validArguments))
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void validatesEntryIdAccordingToTheSelectedArtifactAndKeepsHistoricalKeysReadable()
      throws Exception {
    Constructor<?> constructor = entryAwareConstructor();
    assertThat(constructor)
        .as("the typed query constructor must carry run ID and complete entry ID")
        .isNotNull();
    String entryId = "entry:" + "f".repeat(64);

    assertThatCode(() -> constructor.newInstance(queryArguments("ENTRY_EVIDENCE", entryId)))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> constructor.newInstance(queryArguments("ENTRY_EVIDENCE", null)))
        .hasCauseInstanceOf(IllegalArgumentException.class);

    for (String directoryKey : List.of("ENTRY_EVIDENCE_INDEX", "FRONTEND_EVIDENCE_COVERAGE")) {
      assertThatCode(() -> constructor.newInstance(queryArguments(directoryKey, null)))
          .doesNotThrowAnyException();
      assertThatThrownBy(() -> constructor.newInstance(queryArguments(directoryKey, entryId)))
          .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    for (String historicalKey :
        List.of("JAVA_CODE_INDEX", "PERSISTENCE_MATERIAL_INDEX", "CODE_READING_MATERIALS_V2")) {
      assertThatCode(() -> constructor.newInstance(queryArguments(historicalKey, null)))
          .doesNotThrowAnyException();
    }
  }

  @Test
  void technicalArtifactReaderReceivesEntryIdAtThePreProviderBoundary() {
    var reads =
        Arrays.stream(CompletedTechnicalArtifactReader.class.getMethods())
            .filter(method -> "read".equals(method.getName()))
            .toList();
    assertThat(reads)
        .as("artifact selection must pass the complete entryId to the saved-payload reader")
        .isNotEmpty();
    assertThat(
            reads.stream()
                .anyMatch(
                    method ->
                        Arrays.stream(method.getParameterTypes())
                            .anyMatch(type -> type == String.class || type == ArtifactQuery.class)))
        .as("the reader must receive entryId instead of accepting a filesystem path")
        .isTrue();
  }

  private static Constructor<?> entryAwareConstructor() {
    return Arrays.stream(ArtifactQuery.class.getDeclaredConstructors())
        .filter(value -> value.getParameterCount() == 5)
        .filter(
            value ->
                Arrays.stream(value.getParameterTypes()).filter(String.class::equals).count() == 2)
        .findFirst()
        .orElse(null);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Object[] queryArguments(String keyName, String entryId) {
    return Arrays.stream(ArtifactQuery.class.getRecordComponents())
        .map(
            component -> {
              return switch (component.getName()) {
                case "runId" -> "analysis-run:" + "b".repeat(64);
                case "entryId" -> entryId;
                case "technicalArtifactQueryKey" ->
                    Enum.valueOf(
                        (Class<? extends Enum>) component.getType().asSubclass(Enum.class),
                        keyName);
                case "businessOutputArtifactKey" -> null;
                case "maxBytes" -> 4096;
                default ->
                    throw new AssertionError(
                        "unexpected ArtifactQuery field " + component.getName());
              };
            })
        .toArray();
  }

  private static Object parseConfiguredInvocation(String operation, List<String> options)
      throws Exception {
    Class<?> invocationType =
        Class.forName(
            "org.sourceanalysis.app.adapter.cli.TechnicalAnalysisConfiguredRuntime$Invocation");
    Method parse = invocationType.getDeclaredMethod("parse", String.class, List.class);
    parse.setAccessible(true);
    Object invocation = parse.invoke(null, operation, options);
    Method required = invocationType.getDeclaredMethod("requireRequiredOptions");
    required.setAccessible(true);
    required.invoke(invocation);
    return invocation;
  }
}
