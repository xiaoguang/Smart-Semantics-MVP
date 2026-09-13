package org.sourceanalysis.app.runtime.modeljob;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.AnalysisRunId;

/** Validated, immutable routing and two-level concurrency values for one analysis run. */
public record ModelJobExecutionConfiguration(
    int maxConcurrentJobs,
    Map<String, ModelJobProviderBinding> providers,
    Map<String, List<String>> routing,
    Path journalDirectory,
    AnalysisRunId runId) {

  private static final Set<String> PHASES =
      Set.of("activity", "processGroup", "repositorySummary", "report");

  public ModelJobExecutionConfiguration {
    if (maxConcurrentJobs < 1) {
      throw new IllegalArgumentException("global job concurrency must be positive");
    }
    providers = immutableProviders(providers);
    routing = immutableRoutes(routing, providers.keySet());
    journalDirectory = Objects.requireNonNull(journalDirectory, "model job journal directory");
    runId = Objects.requireNonNull(runId, "analysis run ID");
  }

  @Override
  public Map<String, ModelJobProviderBinding> providers() {
    return Map.copyOf(providers);
  }

  @Override
  public Map<String, List<String>> routing() {
    return Map.copyOf(routing);
  }

  /** Returns the provider fixed for the stable zero-based ordinal in one phase. */
  public ModelJobProviderBinding binding(String phase, int ordinal) {
    if (ordinal < 0) {
      throw new IllegalArgumentException("model job ordinal must not be negative");
    }
    List<String> route = routing.get(phase);
    if (route == null) {
      throw new IllegalArgumentException("unknown model job phase");
    }
    return providers.get(route.get(ordinal % route.size()));
  }

  /** Returns the stable provider route for one phase. */
  public List<ModelJobProviderBinding> providerRoute(String phase) {
    List<String> route = routing.get(phase);
    if (route == null) {
      throw new IllegalArgumentException("unknown model job phase");
    }
    return route.stream().map(providers::get).toList();
  }

  private static Map<String, ModelJobProviderBinding> immutableProviders(
      Map<String, ModelJobProviderBinding> configured) {
    Objects.requireNonNull(configured, "model job providers");
    if (configured.isEmpty()) {
      throw new IllegalArgumentException("model job providers are required");
    }
    Map<String, ModelJobProviderBinding> copy = new LinkedHashMap<>();
    Set<String> quotaScopes = new LinkedHashSet<>();
    configured.forEach(
        (key, provider) -> {
          if (key == null
              || provider == null
              || !key.equals(provider.key())
              || provider.expectedRuntimeIdentity() == null
              || !quotaScopes.add(provider.quotaScope())) {
            throw new IllegalArgumentException("model job provider configuration is invalid");
          }
          copy.put(key, provider);
        });
    return Map.copyOf(copy);
  }

  private static Map<String, List<String>> immutableRoutes(
      Map<String, List<String>> configured, Set<String> providerKeys) {
    Objects.requireNonNull(configured, "model job routing");
    if (!configured.keySet().equals(PHASES)) {
      throw new IllegalArgumentException("model job routing phases are invalid");
    }
    Map<String, List<String>> copy = new LinkedHashMap<>();
    for (String phase : List.of("activity", "processGroup", "repositorySummary", "report")) {
      List<String> route = List.copyOf(configured.get(phase));
      if (route.isEmpty()
          || route.stream().anyMatch(key -> !providerKeys.contains(key))
          || new LinkedHashSet<>(route).size() != route.size()) {
        throw new IllegalArgumentException("model job route is invalid");
      }
      copy.put(phase, route);
    }
    return Map.copyOf(copy);
  }
}
