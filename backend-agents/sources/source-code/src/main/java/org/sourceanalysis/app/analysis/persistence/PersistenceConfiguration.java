package org.sourceanalysis.app.analysis.persistence;

import java.util.List;
import java.util.Objects;

/** Explicit, closed persistence plugins selected at the application composition root. */
public record PersistenceConfiguration(List<Plugin> plugins) {

  public PersistenceConfiguration {
    plugins = List.copyOf(Objects.requireNonNull(plugins, "persistence plugins"));
    long mybatisPlugins =
        plugins.stream().filter(plugin -> "mybatis".equals(plugin.type())).count();
    if (mybatisPlugins > 1) {
      throw new IllegalArgumentException("MyBatis persistence plugin may be configured once");
    }
  }

  /** Selects no persistence adapter, so the analyzer returns a HEADER-only disabled result. */
  public static PersistenceConfiguration disabled() {
    return new PersistenceConfiguration(List.of());
  }

  public boolean enabled() {
    return !plugins.isEmpty();
  }

  /**
   * One closed first-version plugin selection. Future adapters require an explicit contract change.
   */
  public record Plugin(String type, String sqlParser) {

    public Plugin {
      if (!"mybatis".equals(type) || !"jsqlparser".equals(sqlParser)) {
        throw new IllegalArgumentException("only the mybatis/jsqlparser plugin is supported");
      }
    }
  }
}
