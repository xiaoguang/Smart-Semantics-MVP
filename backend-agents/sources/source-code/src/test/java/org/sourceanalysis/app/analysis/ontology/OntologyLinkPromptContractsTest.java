package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The shipped prompts must carry the approved business-role rule, not a customer answer. */
final class OntologyLinkPromptContractsTest {
  @Test
  void extractDoesNotEquateSharedBackingWithOneBusinessEndpoint() throws IOException {
    String prompt = prompt("formal-link-v1.txt");

    assertThat(prompt)
        .contains("Sharing a Java class or database table")
        .contains("distinct business kinds")
        .contains("Do not split ordinary lifecycle states")
        .doesNotContain("DepotHead", "linkApply", "PurchaseOrder", "请购", "采购");
  }

  @Test
  void reviewChecksBusinessKindsAndAllReferencesWithoutForcingASampleGraph() throws IOException {
    String prompt = prompt("formal-link-review-v1.txt");

    assertThat(prompt)
        .contains("shared Java class or database table")
        .contains("distinct business kinds")
        .contains("all affected references")
        .doesNotContain("DepotHead", "linkApply", "PurchaseOrder", "请购", "采购");
  }

  private static String prompt(String name) throws IOException {
    try (var input = OntologyLinkPromptContractsTest.class.getResourceAsStream(name)) {
      assertThat(input).isNotNull();
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void analyticExtractAndReviewSpellOutTheSameLocalIdPrefixes() throws IOException {
    for (String resource : new String[] {"formal-analytic-v2.txt", "formal-review-v2.txt"}) {
      assertThat(prompt(resource))
          .contains("dimensions use D1/D2", "measures use V1/V2", "metrics use M1/M2")
          .contains("componentMeasureRefs")
          .contains("reviewedCatalog measure B")
          .doesNotContain("measures use M1", "metrics use V1");
    }
  }
}
