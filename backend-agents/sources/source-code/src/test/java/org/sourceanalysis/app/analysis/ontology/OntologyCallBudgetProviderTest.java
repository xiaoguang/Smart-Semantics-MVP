package org.sourceanalysis.app.analysis.ontology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

final class OntologyCallBudgetProviderTest {
  @Test
  void stopsBeforeDispatchingARequestBeyondTheNamedLimit() {
    AtomicInteger delegateCalls = new AtomicInteger();
    OntologyCallBudgetProvider provider =
        new OntologyCallBudgetProvider(
            request -> {
              delegateCalls.incrementAndGet();
              return new StructuredModelResponse(
                  new CanonicalJsonCodec()
                      .canonicalizeStrictJson(
                          ImmutableBytes.copyOf("{}".getBytes(StandardCharsets.UTF_8))),
                  new ModelRuntimeIdentityV1("SCRIPTED", "scripted", "none", "test"));
            },
            2);
    StructuredModelRequest request =
        new StructuredModelRequest(
            "sample",
            "ONTOLOGY_SURVEY",
            "test",
            new CanonicalJsonCodec()
                .canonicalizeStrictJson(
                    ImmutableBytes.copyOf("{}".getBytes(StandardCharsets.UTF_8))),
            new CanonicalJsonCodec()
                .canonicalizeStrictJson(
                    ImmutableBytes.copyOf("{}".getBytes(StandardCharsets.UTF_8))),
            1024);

    provider.generate(request);
    provider.generate(request);
    OntologyCallBudgetProvider.DispatchLimitExceeded exceeded =
        assertThrows(
            OntologyCallBudgetProvider.DispatchLimitExceeded.class,
            () -> provider.generate(request));
    assertEquals(OntologyCallBudgetProvider.DispatchLimitExceeded.CODE, exceeded.getMessage());
    assertEquals(2, delegateCalls.get());
    assertEquals(2, provider.dispatchedCalls());
  }
}
