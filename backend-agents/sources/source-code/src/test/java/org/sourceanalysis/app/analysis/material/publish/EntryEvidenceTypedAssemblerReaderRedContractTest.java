package org.sourceanalysis.app.analysis.material.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * RED contract for the Step 05 typed entry-evidence boundary.
 *
 * <p>The historical {@code CodeReadingMaterialSet}/{@code CodeReadingMaterialPublisher} path is a
 * packet producer and must remain readable as history. New entry evidence needs a distinct typed
 * set, assembler, and reader so that one entry cannot be silently merged into a neighbouring packet
 * or reduced to a method-key list. The canonical-store RED contract checks the persisted fields;
 * this test keeps the public seam honest before the implementation exists.
 */
class EntryEvidenceTypedAssemblerReaderRedContractTest {

  private static final String MATERIAL_PACKAGE = "org.sourceanalysis.app.analysis.material";
  private static final String PUBLISH_PACKAGE = MATERIAL_PACKAGE + ".publish";

  @Test
  void introducesADistinctTypedEntryEvidenceSetWithBothDenominators() {
    Class<?> set = required(MATERIAL_PACKAGE + ".EntryEvidenceSet");

    assertThat(set.isRecord())
        .as("entry evidence must be an immutable typed view, not the historical packet DTO")
        .isTrue();
    assertThat(
            Arrays.stream(set.getRecordComponents()).map(component -> component.getName()).toList())
        .as("the typed view must keep backend and frontend denominators independently")
        .contains("entries", "frontendCoverage");
  }

  @Test
  void typedEntriesExposeTheCompleteJavaPersistenceAndFrontendClosure() {
    Class<?> set = required(MATERIAL_PACKAGE + ".EntryEvidenceSet");
    Class<?> entry = listElementType(set, "entries");
    assertThat(entry.isRecord()).isTrue();
    assertThat(componentNames(entry))
        .as("each entry file must be self-contained rather than a packet of references")
        .contains("entryId", "assemblyStatus", "java", "persistence", "limitations");

    Class<?> java = componentType(entry, "java");
    assertThat(componentNames(java))
        .as("Java closure must keep complete methods, calls, supporting sources and enhancements")
        .contains("methods", "calls", "supportingSources", "technicalEnhancements");

    Class<?> persistence = componentType(entry, "persistence");
    assertThat(componentNames(persistence))
        .as("persistence closure must keep bindings, XML resources/statements and SQL analyses")
        .contains("bindings", "statements", "resources", "sqlAnalyses");

    Class<?> frontendCoverage = listElementType(set, "frontendCoverage");
    assertThat(componentNames(frontendCoverage))
        .as("frontend denominator must retain each request disposition and complete unmatched data")
        .contains("requestId", "resolution");
  }

  @Test
  void assemblerAcceptsOnlyAlreadyReadTypedInputs() {
    Class<?> assembler = required(MATERIAL_PACKAGE + ".EntryEvidenceAssembler");
    List<Method> assembleMethods =
        Arrays.stream(assembler.getMethods())
            .filter(method -> method.getName().equals("assemble"))
            .toList();

    assertThat(assembleMethods)
        .as("Step05 must expose a typed assembler rather than routing through the packet builder")
        .isNotEmpty();
    assertThat(
            assembleMethods.stream()
                .anyMatch(
                    method ->
                        method.getReturnType().getSimpleName().equals("EntryEvidenceSet")
                            && Arrays.stream(method.getParameterTypes())
                                .map(Class::getName)
                                .noneMatch(
                                    type ->
                                        type.contains("java.nio.file.Path")
                                            || type.contains("Node")
                                            || type.contains("Jdt")
                                            || type.contains("Provider")
                                            || type.contains("Parser"))))
        .as("R4 must consume saved typed material, not restart tools or read paths")
        .isTrue();
  }

  @Test
  void readerSelectsAnEntryByIdentityAndReturnsTheTypedViewWithoutAFilesystemPath() {
    Class<?> set = required(MATERIAL_PACKAGE + ".EntryEvidenceSet");
    Class<?> reader = required(PUBLISH_PACKAGE + ".EntryEvidenceReader");
    List<Method> readMethods =
        Arrays.stream(reader.getMethods())
            .filter(
                method ->
                    method.getName().startsWith("read") || method.getName().startsWith("reopen"))
            .toList();

    assertThat(readMethods)
        .as("Step05 needs a reader for the new self-contained files")
        .isNotEmpty();
    assertThat(
            readMethods.stream()
                .anyMatch(
                    method -> {
                      List<String> parameterTypes =
                          Arrays.stream(method.getParameterTypes()).map(Class::getName).toList();
                      return (method.getReturnType().equals(set)
                              || method.getReturnType().getSimpleName().contains("EntryEvidence")
                              || method.getReturnType().getSimpleName().equals("EntryDocument"))
                          && parameterTypes.stream()
                              .anyMatch(
                                  type ->
                                      type.equals(String.class.getName())
                                          || type.endsWith("EntryId")
                                          || type.endsWith("ArtifactQuery"))
                          && parameterTypes.stream()
                              .noneMatch(type -> type.equals("java.nio.file.Path"));
                    }))
        .as("reader selection is by full entry identity, never by a caller path")
        .isTrue();
  }

  private static List<String> componentNames(Class<?> recordType) {
    assertThat(recordType.isRecord()).as("typed entry-evidence members must be records").isTrue();
    return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName).toList();
  }

  private static Class<?> componentType(Class<?> recordType, String name) {
    return Arrays.stream(recordType.getRecordComponents())
        .filter(component -> component.getName().equals(name))
        .map(RecordComponent::getType)
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing typed entry-evidence component: " + name));
  }

  private static Class<?> listElementType(Class<?> recordType, String name) {
    RecordComponent component =
        Arrays.stream(recordType.getRecordComponents())
            .filter(value -> value.getName().equals(name))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("missing typed entry-evidence component: " + name));
    Type genericType = component.getGenericType();
    assertThat(genericType).isInstanceOf(ParameterizedType.class);
    Type elementType = ((ParameterizedType) genericType).getActualTypeArguments()[0];
    assertThat(elementType).isInstanceOf(Class.class);
    return (Class<?>) elementType;
  }

  private static Class<?> required(String name) {
    try {
      return Class.forName(
          name, false, EntryEvidenceTypedAssemblerReaderRedContractTest.class.getClassLoader());
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("missing typed Step05 entry-evidence seam: " + name, missing);
    }
  }
}
