package org.sourceanalysis.app.analysis.code.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.fact.ProvenCodeFactsExecutor;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsExecution;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

class JavaCodeIndexPublicationSpecifierTest {

  @Test
  void publishesReopensNavigationOnlyStepAndSkipsStrictFactEnumeration(@TempDir Path temporary) {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(temporary.resolve("fixture"))) {
      String snapshotId = fixture.sourceReader().reopen(fixture.sourceInventory()).snapshotId();
      try (JavaCodeSession session = fakeSession(snapshotId)) {
        var graphs =
            new ProgramGraphsExecution(
                    fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
                .execute(
                    fixture.sourceInventory(),
                    fixture.applicationDiscovery(),
                    session,
                    fixture.artifactControls());

        var reopenedStep = fixture.stepArtifacts().reopen(graphs.publication());
        assertThat(reopenedStep.semanticPayloads())
            .singleElement()
            .satisfies(
                payload ->
                    assertThat(payload.descriptor().fileName()).isEqualTo("java-code-index.jsonl"));

        JavaCodeIndex index = new JavaCodeIndexReader(fixture.stepArtifacts()).reopen(graphs);
        assertThat(index.engine().engineId()).isEqualTo("jdt");
        assertThat(index.snapshotId()).isEqualTo(snapshotId);
        assertThat(index.entries()).hasSize(2).allMatch(value -> value.context() != null);
        assertThat(index.entries())
            .allSatisfy(
                entry -> {
                  assertThat(entry.context().entryId()).isEqualTo(entry.seed().entryId());
                  assertThat(entry.context().methods())
                      .extracting(EntryCodeContext.MethodCode::methodKey)
                      .containsExactly(entry.seed().methodKey());
                });

        var facts =
            new ProvenCodeFactsExecutor(
                    fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
                .execute(fixture.sourceInventory(), fixture.applicationDiscovery(), graphs);
        var reopenedFacts = fixture.stepArtifacts().reopen(facts.publication());
        assertThat(reopenedFacts.semanticPayloads())
            .singleElement()
            .satisfies(
                payload -> {
                  assertThat(payload.descriptor().fileName()).isEqualTo("fact-accounting.json");
                  JsonNode document =
                      new CanonicalJsonCodec().parseCanonical(payload.canonicalUtf8());
                  assertThat(document.path("schemaVersion").textValue())
                      .isEqualTo("proven-code-facts-fact-accounting-v4");
                  assertThat(document.path("availability").textValue()).isEqualTo("NOT_PRODUCED");
                  assertThat(document.path("reason").textValue()).isNotBlank();
                  assertThat(document.path("navigationInputRef").path("artifactId").textValue())
                      .isEqualTo(
                          reopenedStep.semanticPayloads().get(0).descriptor().artifactId().value());
                  assertThat(document.path("candidateFactCount").isNull()).isTrue();
                  assertThat(document.has("candidateDenominatorKeys")).isFalse();
                  assertThat(document.has("proofPackRef")).isFalse();
                });
      }
    }
  }

  @Test
  void preservesEntryLocalCallProjectionsWhenPhysicalCallIsShared(@TempDir Path temporary) {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(temporary.resolve("fixture"))) {
      String snapshotId = fixture.sourceReader().reopen(fixture.sourceInventory()).snapshotId();
      try (JavaCodeSession session = fakeSession(snapshotId, false)) {
        var graphs =
            new ProgramGraphsExecution(
                    fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
                .execute(
                    fixture.sourceInventory(),
                    fixture.applicationDiscovery(),
                    session,
                    fixture.artifactControls());

        JavaCodeIndex reopened = new JavaCodeIndexReader(fixture.stepArtifacts()).reopen(graphs);
        assertThat(reopened.entries()).hasSize(2);
        List<EntryCodeContext> contexts =
            reopened.entries().stream().map(JavaCodeIndex.EntryCollection::context).toList();
        List<EntryCodeContext> includedContexts =
            contexts.stream()
                .filter(
                    context ->
                        context.calls().stream()
                            .anyMatch(
                                call ->
                                    call.targets().stream()
                                        .anyMatch(
                                            target -> "BODY_INCLUDED".equals(target.expansion()))))
                .toList();
        List<EntryCodeContext> boundedContexts =
            contexts.stream()
                .filter(
                    context ->
                        context.calls().stream()
                            .anyMatch(
                                call ->
                                    call.targets().stream()
                                        .anyMatch(
                                            target -> "NOT_EXPANDED".equals(target.expansion()))))
                .toList();
        assertThat(includedContexts).singleElement();
        assertThat(boundedContexts).singleElement();
        assertThat(includedContexts.get(0).methods())
            .extracting(EntryCodeContext.MethodCode::methodKey)
            .contains("method:shared-target");
        assertThat(boundedContexts.get(0).methods())
            .extracting(EntryCodeContext.MethodCode::methodKey)
            .doesNotContain("method:shared-target");
        List<EntryCodeContext.CallSite> includedCalls =
            reopened.entries().stream()
                .map(JavaCodeIndex.EntryCollection::context)
                .flatMap(context -> context.calls().stream())
                .filter(
                    call ->
                        call.targets().stream()
                            .anyMatch(target -> "BODY_INCLUDED".equals(target.expansion())))
                .toList();
        List<EntryCodeContext.CallSite> boundedCalls =
            reopened.entries().stream()
                .map(JavaCodeIndex.EntryCollection::context)
                .flatMap(context -> context.calls().stream())
                .filter(
                    call ->
                        call.targets().stream()
                            .anyMatch(target -> "NOT_EXPANDED".equals(target.expansion())))
                .toList();
        assertThat(includedCalls).singleElement();
        assertThat(boundedCalls).singleElement();
        EntryCodeContext.CallSite includedCall = includedCalls.get(0);
        EntryCodeContext.CallSite boundedCall = boundedCalls.get(0);

        assertThat(includedCall.callKey()).isEqualTo("call:shared-physical");
        assertThat(boundedCall.callKey()).isEqualTo("call:shared-physical");
        assertThat(boundedCall.callerMethodKey()).isEqualTo(includedCall.callerMethodKey());
        assertThat(boundedCall.kind()).isEqualTo(includedCall.kind());
        assertThat(boundedCall.site()).isEqualTo(includedCall.site());
        assertThat(boundedCall.navigationSite()).isEqualTo(includedCall.navigationSite());
        assertThat(boundedCall.expression()).isEqualTo(includedCall.expression());
        assertThat(boundedCall.receiverExpression()).isEqualTo(includedCall.receiverExpression());
        assertThat(boundedCall.actualArguments()).isEqualTo(includedCall.actualArguments());
        assertThat(boundedCall.enclosingControlIndexes())
            .isEqualTo(includedCall.enclosingControlIndexes());
        assertThat(boundedCall.deferred()).isEqualTo(includedCall.deferred());
        assertThat(includedCall.resolution()).isEqualTo("LOCATED");
        assertThat(includedCall.resolutionDetail()).isNull();
        assertThat(boundedCall.resolution()).isEqualTo("CANDIDATES");
        assertThat(boundedCall.resolutionDetail()).isEqualTo("target collection was bounded");

        assertThat(includedCall.targets())
            .singleElement()
            .satisfies(
                target -> {
                  assertThat(target.methodKey()).isEqualTo("method:shared-target");
                  assertThat(target.roles()).containsExactly("DECLARATION");
                  assertThat(target.displayName()).isEqualTo("shared target");
                  assertThat(target.navigationKinds()).containsExactly("DEFINITION");
                  assertThat(target.expansion()).isEqualTo("BODY_INCLUDED");
                  assertThat(target.reason()).isNull();
                  assertThat(target.argumentAssociations())
                      .singleElement()
                      .satisfies(
                          association -> {
                            assertThat(association.actualOrdinals()).containsExactly(0);
                            assertThat(association.formalOrdinal()).isZero();
                            assertThat(association.kind()).isEqualTo("POSITIONAL");
                          });
                });
        assertThat(boundedCall.targets())
            .singleElement()
            .satisfies(
                target -> {
                  assertThat(target.methodKey()).isNull();
                  assertThat(target.roles()).containsExactly("DECLARATION");
                  assertThat(target.displayName()).isEqualTo("bounded shared target");
                  assertThat(target.navigationKinds()).containsExactly("ENGINE_BINDING");
                  assertThat(target.expansion()).isEqualTo("NOT_EXPANDED");
                  assertThat(target.reason()).isEqualTo("COLLECTION_LIMIT");
                  assertThat(target.argumentAssociations()).isEmpty();
                });
      }
    }
  }

  @Test
  void rejectsSharedPhysicalCallWhenSourceSyntaxConflicts(@TempDir Path temporary) {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(temporary.resolve("fixture"))) {
      String snapshotId = fixture.sourceReader().reopen(fixture.sourceInventory()).snapshotId();
      try (JavaCodeSession session = fakeSession(snapshotId, true)) {
        assertThatThrownBy(
                () ->
                    new ProgramGraphsExecution(
                            fixture.sourceReader(),
                            fixture.moduleArtifacts(),
                            fixture.stepArtifacts())
                        .execute(
                            fixture.sourceInventory(),
                            fixture.applicationDiscovery(),
                            session,
                            fixture.artifactControls()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("JAVA_CODE_INDEX_INVALID");
      }
    }
  }

  private static JavaCodeSession fakeSession(String snapshotId) {
    return fakeSession(snapshotId, false, false);
  }

  private static JavaCodeSession fakeSession(String snapshotId, boolean syntaxConflict) {
    return fakeSession(snapshotId, true, syntaxConflict);
  }

  private static JavaCodeSession fakeSession(
      String snapshotId, boolean sharedCallScenario, boolean syntaxConflict) {
    EntryCodeContext.TechnicalEnhancements enhancements =
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.NOT_PRODUCED,
            "STRICT_GRAPH_ENRICHMENT_NOT_REQUESTED_BY_TEST_JDT",
            List.of(),
            List.of(),
            null);
    int[] collectionCount = {0};
    return new JavaCodeSession() {
      @Override
      public JavaDeclarationCatalog catalog() {
        return new JavaDeclarationCatalog(
            snapshotId,
            List.of("src/main/java/com/example/OrderController.java"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of());
      }

      @Override
      public EntryCodeContext collect(EntrySeed entry) {
        if (!sharedCallScenario) {
          EntryCodeContext.SourceSource source =
              new EntryCodeContext.SourceSource(
                  "src/main/java/com/example/OrderController.java",
                  entry.methodRange(),
                  "public Object entry() { return service.call(); }");
          EntryCodeContext.MethodCode method =
              new EntryCodeContext.MethodCode(
                  entry.methodKey(),
                  "METHOD",
                  "com.example.OrderController",
                  "entry",
                  List.of(),
                  "Object",
                  source,
                  true);
          return new EntryCodeContext(
              EntryCodeContext.SCHEMA_VERSION,
              entry.entryId(),
              entry.methodKey(),
              List.of(method),
              List.of(),
              List.of(),
              List.of(),
              enhancements);
        }
        EntryCodeContext.MethodCode entryMethod =
            method(
                entry.methodKey(),
                "entry",
                entry.methodRange(),
                "public Object entry() { return service.call(status); }");
        EntryCodeContext.MethodCode sharedCaller =
            method(
                "method:shared-caller",
                "sharedCaller",
                new SourceRange(5, 50, 1, 1),
                "public Object sharedCaller(String status) { return service.call(status); }");
        boolean included = collectionCount[0] == 0;
        String expression =
            syntaxConflict && !included ? "service.otherCall(status)" : "service.call(status)";
        EntryCodeContext.CallTarget target =
            included
                ? new EntryCodeContext.CallTarget(
                    "method:shared-target",
                    List.of("DECLARATION"),
                    "shared target",
                    List.of("DEFINITION"),
                    "BODY_INCLUDED",
                    null,
                    List.of(new EntryCodeContext.ArgumentAssociation(List.of(0), 0, "POSITIONAL")))
                : new EntryCodeContext.CallTarget(
                    null,
                    List.of("DECLARATION"),
                    "bounded shared target",
                    List.of("ENGINE_BINDING"),
                    "NOT_EXPANDED",
                    "COLLECTION_LIMIT",
                    List.of());
        List<EntryCodeContext.MethodCode> methods =
            included
                ? List.of(
                    entryMethod,
                    sharedCaller,
                    method(
                        "method:shared-target",
                        "sharedTarget",
                        new SourceRange(60, 40, 1, 1),
                        "public Object sharedTarget(String status) { return status; }",
                        true))
                : List.of(entryMethod, sharedCaller);
        collectionCount[0]++;
        return new EntryCodeContext(
            EntryCodeContext.SCHEMA_VERSION,
            entry.entryId(),
            entry.methodKey(),
            methods,
            List.of(sharedCall(expression, target)),
            List.of(),
            List.of(),
            enhancements);
      }

      @Override
      public EngineDescriptor descriptor() {
        return new EngineDescriptor(
            "jdt", "test-adapter-v1", Map.of("jdtls", "1.61.0"), "17", List.of("METHODS"));
      }

      @Override
      public void close() {}
    };
  }

  private static EntryCodeContext.MethodCode method(
      String key, String name, SourceRange range, String text) {
    return method(key, name, range, text, false);
  }

  private static EntryCodeContext.MethodCode method(
      String key, String name, SourceRange range, String text, boolean statusParameter) {
    return new EntryCodeContext.MethodCode(
        key,
        "METHOD",
        "com.example.OrderController",
        name,
        statusParameter
            ? List.of(
                new JavaDeclarationCatalog.ParameterView(0, "status", "String", false, List.of()))
            : List.of(),
        "Object",
        new EntryCodeContext.SourceSource(
            "src/main/java/com/example/OrderController.java", range, text),
        true);
  }

  private static EntryCodeContext.CallSite sharedCall(
      String expression, EntryCodeContext.CallTarget target) {
    SourceRange site = new SourceRange(25, expression.length(), 1, 1);
    return new EntryCodeContext.CallSite(
        "call:shared-physical",
        "method:shared-caller",
        "METHOD",
        site,
        new SourceRange(33, 4, 1, 1),
        expression,
        "service",
        List.of(new EntryCodeContext.ActualArgument(0, "status")),
        List.of(),
        false,
        List.of(target),
        target.methodKey() == null ? "CANDIDATES" : "LOCATED",
        target.methodKey() == null ? "target collection was bounded" : null);
  }
}
