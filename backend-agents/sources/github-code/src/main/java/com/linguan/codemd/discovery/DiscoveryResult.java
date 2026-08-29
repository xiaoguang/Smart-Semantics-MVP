package com.linguan.codemd.discovery;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Canonical, deterministically ordered Phase 1 source-discovery output. */
public record DiscoveryResult(List<HttpRoute> httpRoutes,
                              List<DirectCallEdge> directCallEdges,
                              List<MapperBinding> mapperBindings,
                              List<SqlUpdateFact> sqlUpdateFacts,
                              List<DiscoveryGap> gaps) {
    public DiscoveryResult {
        httpRoutes = ordered(httpRoutes, Comparator.comparing(HttpRoute::httpMethod)
                .thenComparing(HttpRoute::path)
                .thenComparing(route -> route.controllerMethodLocator().relativePath())
                .thenComparingInt(route -> route.controllerMethodLocator().startLine()));
        directCallEdges = ordered(directCallEdges, Comparator.comparing(DirectCallEdge::caller)
                .thenComparing(DirectCallEdge::callee));
        mapperBindings = ordered(mapperBindings, Comparator.comparing(MapperBinding::mapperType)
                .thenComparing(MapperBinding::methodName)
                .thenComparing(MapperBinding::xmlPath)
                .thenComparing(MapperBinding::statementId));
        sqlUpdateFacts = ordered(sqlUpdateFacts, Comparator.comparing(SqlUpdateFact::mapperMethod)
                .thenComparing(SqlUpdateFact::table)
                .thenComparing(SqlUpdateFact::field)
                .thenComparing(SqlUpdateFact::value));
        gaps = ordered(gaps, Comparator.comparing(DiscoveryGap::code));
    }

    private static <T> List<T> ordered(List<T> values, Comparator<? super T> comparator) {
        Objects.requireNonNull(values, "values");
        return values.stream().map(value -> Objects.requireNonNull(value, "discovery value"))
                .distinct().sorted(comparator).toList();
    }
}
