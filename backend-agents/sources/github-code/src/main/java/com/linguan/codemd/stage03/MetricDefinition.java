package com.linguan.codemd.stage03;

import java.util.List;

/** A formula or aggregate only when the atoms prove it. */
public record MetricDefinition(String metricId, String display, List<String> basisAtomIds) {
    public MetricDefinition {
        basisAtomIds = List.copyOf(basisAtomIds);
    }
}
