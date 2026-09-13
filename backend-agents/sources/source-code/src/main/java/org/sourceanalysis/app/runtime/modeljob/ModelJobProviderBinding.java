package org.sourceanalysis.app.runtime.modeljob;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;

/** One explicit Provider/account binding and its shared in-flight job limit. */
public final class ModelJobProviderBinding {

  private final String key;
  private final String quotaScope;
  private final int maxConcurrentJobs;
  private final List<StructuredModelProvider> providerClients;
  private final ModelRuntimeIdentityV1 expectedRuntimeIdentity;

  public ModelJobProviderBinding(
      String key,
      String quotaScope,
      int maxConcurrentJobs,
      StructuredModelProvider provider,
      ModelRuntimeIdentityV1 expectedRuntimeIdentity) {
    this(key, quotaScope, maxConcurrentJobs, List.of(provider), expectedRuntimeIdentity);
  }

  public ModelJobProviderBinding(
      String key,
      String quotaScope,
      int maxConcurrentJobs,
      List<StructuredModelProvider> providerClients,
      ModelRuntimeIdentityV1 expectedRuntimeIdentity) {
    required(key, "provider key");
    required(quotaScope, "quota scope");
    if (maxConcurrentJobs < 1) {
      throw new IllegalArgumentException("provider job concurrency must be positive");
    }
    Objects.requireNonNull(providerClients, "structured model provider clients");
    if (providerClients.isEmpty() || providerClients.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("structured model provider clients are required");
    }
    this.key = key;
    this.quotaScope = quotaScope;
    this.maxConcurrentJobs = maxConcurrentJobs;
    this.providerClients = List.copyOf(providerClients);
    this.expectedRuntimeIdentity = expectedRuntimeIdentity;
    // Direct in-memory test seams may leave identity unchecked. Formal execution validates it.
  }

  public String key() {
    return key;
  }

  public String quotaScope() {
    return quotaScope;
  }

  public int maxConcurrentJobs() {
    return maxConcurrentJobs;
  }

  public StructuredModelProvider provider() {
    return providerClients.get(0);
  }

  public ModelRuntimeIdentityV1 expectedRuntimeIdentity() {
    return expectedRuntimeIdentity;
  }

  /** Fixes one whole DRAFT-to-REVIEW job to one configured credential client. */
  public ModelJobProviderBinding forJobOrdinal(int ordinal) {
    if (ordinal < 0) {
      throw new IllegalArgumentException("model job ordinal must not be negative");
    }
    StructuredModelProvider selected = providerClients.get(ordinal % providerClients.size());
    if (providerClients.size() == 1 && selected == providerClients.get(0)) {
      return this;
    }
    return new ModelJobProviderBinding(
        key, quotaScope, maxConcurrentJobs, selected, expectedRuntimeIdentity);
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
