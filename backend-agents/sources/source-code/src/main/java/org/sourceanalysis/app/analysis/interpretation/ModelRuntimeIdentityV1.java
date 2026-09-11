package org.sourceanalysis.app.analysis.interpretation;

/** Materialized upstream runtime identity for one model task or completed Provider call. */
public record ModelRuntimeIdentityV1(
    String upstreamProvider, String model, String reasoningEffort, String sandbox) {

  public ModelRuntimeIdentityV1 {
    required(upstreamProvider, "upstream provider");
    required(model, "model");
    required(reasoningEffort, "reasoning effort");
    required(sandbox, "sandbox");
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
