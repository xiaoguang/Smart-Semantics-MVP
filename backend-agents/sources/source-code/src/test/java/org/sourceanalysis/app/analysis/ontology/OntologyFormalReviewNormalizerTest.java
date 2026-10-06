package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

final class OntologyFormalReviewNormalizerTest {
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void actionReviewCorrectsUnambiguousRulePrefixInStructuralReferencesOnly() {
    ImmutableBytes raw =
        bytes(
            """
            {"definitions":{"operations":[],"rules":[{"localId":"D1","definition":"D1 remains literal prose"}]},
             "unresolved":[{"knownDefinitionRefs":[],"relatedLocalDefinitionRefs":["D1"]}],
             "corrections":[{"targetLocalId":"D1","changeKind":"ADDED"}]}
            """);
    JsonNode draft =
        json.parseCanonical(bytes("{\"definitions\":{\"operations\":[],\"rules\":[]}}"));

    OntologyFormalReviewNormalizer.Result result =
        OntologyFormalReviewNormalizer.normalize(raw, draft);
    JsonNode canonical = json.parseCanonical(result.canonical());

    assertThat(canonical.path("definitions").path("rules").get(0).path("localId").asText())
        .isEqualTo("R1");
    assertThat(
            canonical.path("unresolved").get(0).path("relatedLocalDefinitionRefs").get(0).asText())
        .isEqualTo("R1");
    assertThat(canonical.path("corrections").get(0).path("targetLocalId").asText()).isEqualTo("R1");
    assertThat(canonical.path("definitions").path("rules").get(0).path("definition").asText())
        .isEqualTo("D1 remains literal prose");
    assertThat(
            json.parseCanonical(raw)
                .path("definitions")
                .path("rules")
                .get(0)
                .path("localId")
                .asText())
        .isEqualTo("D1");
  }

  @Test
  void analyticReviewCorrectsUnambiguousMeasurePrefixAndComponentReferences() {
    ImmutableBytes raw =
        bytes(
            """
            {"definitions":{"dimensions":[],"measures":[{"localId":"L1","description":"L1 remains literal prose"}],
             "metrics":[{"localId":"M1","componentMeasureRefs":["L1"]}]},
             "unresolved":[{"knownDefinitionRefs":[],"relatedLocalDefinitionRefs":["L1"]}],
             "corrections":[{"targetLocalId":"L1","changeKind":"CHANGED"}]}
            """);
    JsonNode draft =
        json.parseCanonical(
            bytes(
                """
                {"definitions":{"dimensions":[],"measures":[{"localId":"V1","description":"before"}],"metrics":[]}}
                """));

    OntologyFormalReviewNormalizer.Result result =
        OntologyFormalReviewNormalizer.normalize(raw, draft);
    JsonNode canonical = json.parseCanonical(result.canonical());

    assertThat(canonical.path("definitions").path("measures").get(0).path("localId").asText())
        .isEqualTo("V1");
    assertThat(
            canonical
                .path("definitions")
                .path("metrics")
                .get(0)
                .path("componentMeasureRefs")
                .get(0)
                .asText())
        .isEqualTo("V1");
    assertThat(
            canonical.path("unresolved").get(0).path("relatedLocalDefinitionRefs").get(0).asText())
        .isEqualTo("V1");
    assertThat(canonical.path("corrections").findValuesAsText("targetLocalId")).contains("V1");
    assertThat(canonical.path("definitions").path("measures").get(0).path("description").asText())
        .isEqualTo("L1 remains literal prose");
  }

  @Test
  void analyticReviewDoesNotMergeConflictingMeasureIds() {
    ImmutableBytes raw =
        bytes(
            """
            {"definitions":{"dimensions":[],"measures":[{"localId":"L1"},{"localId":"V1"}],"metrics":[]},
             "unresolved":[],"corrections":[]}
            """);
    JsonNode draft =
        json.parseCanonical(
            bytes("{\"definitions\":{\"dimensions\":[],\"measures\":[],\"metrics\":[]}}"));

    OntologyFormalReviewNormalizer.Result result =
        OntologyFormalReviewNormalizer.normalize(raw, draft);

    assertThat(
            json.parseCanonical(result.canonical())
                .path("definitions")
                .path("measures")
                .get(0)
                .path("localId")
                .asText())
        .isEqualTo("L1");
    assertThat(result.events()).noneMatch(event -> event.code().equals("MEASURE_LOCAL_ID"));
  }

  @Test
  void reviewComputesCorrectionsFromRecognizableDefinitionsInInvalidDuplicateDraft() {
    ImmutableBytes raw =
        bytes(
            """
            {"definitions":{"operations":[{"localId":"A1","description":"after"}],
             "rules":[{"localId":"R1","description":"rule"}]},
             "unresolved":[],"corrections":[{"targetLocalId":"R1","changeKind":"CHANGED"}]}
            """);
    JsonNode draft =
        json.parseCanonical(
            bytes(
                """
                {"definitions":{"operations":[{"localId":"A1","description":"before"},
                  {"localId":"R1","description":"mistaken operation"}],
                  "rules":[{"localId":"R1","description":"rule"}]}}
                """));

    OntologyFormalReviewNormalizer.Result result =
        OntologyFormalReviewNormalizer.normalize(raw, draft);
    JsonNode canonical = json.parseCanonical(result.canonical());

    assertThat(canonical.path("corrections").size()).isEqualTo(2);
    assertThat(canonical.path("corrections").findValuesAsText("targetLocalId"))
        .containsExactly("A1", "R1");
    assertThat(canonical.path("definitions").path("operations").get(0).path("description").asText())
        .isEqualTo("after");
    assertThat(result.events()).anyMatch(event -> event.code().equals("CORRECTION_DIFF"));
  }

  @Test
  void reviewKeepsUnresolvedTextButDropsReferencesToDefinitionsItDeleted() {
    ImmutableBytes raw =
        bytes(
            """
            {"definitions":{"objects":[{"localId":"O1"}]},
             "unresolved":[{"description":"Permission behavior remains unknown","knownDefinitionRefs":[],
               "relatedLocalDefinitionRefs":["O2","O42"]}],"corrections":[]}
            """);
    JsonNode draft =
        json.parseCanonical(
            bytes(
                """
                {"definitions":{"objects":[{"localId":"O1"},{"localId":"O2"}]}}
                """));

    OntologyFormalReviewNormalizer.Result result =
        OntologyFormalReviewNormalizer.normalize(raw, draft);
    JsonNode canonical = json.parseCanonical(result.canonical());

    assertThat(canonical.path("unresolved").get(0).path("description").asText())
        .isEqualTo("Permission behavior remains unknown");
    assertThat(canonical.path("unresolved").get(0).path("relatedLocalDefinitionRefs").size())
        .isEqualTo(1);
    assertThat(
            canonical.path("unresolved").get(0).path("relatedLocalDefinitionRefs").get(0).asText())
        .isEqualTo("O42");
    assertThat(result.events()).anyMatch(event -> event.code().equals("DELETED_LOCAL_REF"));

    OntologyFormalReviewNormalizer.Result historical =
        OntologyFormalReviewNormalizer.normalizeV3(raw, draft, null);
    assertThat(
            json.parseCanonical(historical.canonical())
                .path("unresolved")
                .get(0)
                .path("relatedLocalDefinitionRefs")
                .size())
        .isEqualTo(2);
  }

  @Test
  void analyticReviewDropsOnlyDimensionIdsRedundantWithMetricDimensionRefsFromPropertyKeys() {
    ImmutableBytes raw =
        bytes(
            """
            {"definitions":{"dimensions":[{"localId":"D1"},{"localId":"D2"}],"measures":[],
             "metrics":[{"localId":"M1","dimensionRefs":["D1"],
               "grain":{"description":"period grain","keyRefs":["D1","B1.P1","D2"]}}]},
             "unresolved":[],"corrections":[]}
            """);
    JsonNode draft =
        json.parseCanonical(
            bytes(
                """
                {"definitions":{"dimensions":[{"localId":"D1"},{"localId":"D2"}],"measures":[],"metrics":[]}}
                """));

    OntologyFormalReviewNormalizer.Result result =
        OntologyFormalReviewNormalizer.normalize(raw, draft);
    JsonNode metric =
        json.parseCanonical(result.canonical()).path("definitions").path("metrics").get(0);

    assertThat(metric.path("dimensionRefs").get(0).asText()).isEqualTo("D1");
    assertThat(metric.path("grain").path("keyRefs").size()).isEqualTo(2);
    assertThat(metric.path("grain").path("keyRefs").get(0).asText()).isEqualTo("B1.P1");
    assertThat(metric.path("grain").path("keyRefs").get(1).asText()).isEqualTo("D2");
    assertThat(metric.path("grain").path("description").asText()).isEqualTo("period grain");
    assertThat(result.events()).anyMatch(event -> event.code().equals("DIMENSION_GRAIN_REF"));
  }

  private ImmutableBytes bytes(String value) {
    return json.encodeCanonical(
        json.parseStrictJson(
            ImmutableBytes.copyOf(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
  }
}
