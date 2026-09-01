package com.linguan.codemd.target.stage02.applicationprofile;

import com.linguan.codemd.target.contracts.SourceExcerptV1;
import java.util.List;

/** One deterministically discovered static Spring MVC HTTP entry; not yet a business flow. */
public record HttpEntryPoint(
        String entryId,
        String kind,
        String protocol,
        String method,
        String route,
        List<String> routeParts,
        String handlerFqn,
        List<String> parameterNames,
        List<SourceExcerptV1> routeSourceExcerpts) {
    public HttpEntryPoint {
        if (entryId == null
                || !entryId.matches("entry:[0-9a-f]{64}")
                || !"SPRING_MVC_HTTP".equals(kind)
                || !"HTTP".equals(protocol)
                || method == null
                || !method.matches("[A-Z]+")
                || route == null || !route.startsWith("/") || routeParts == null || routeParts.isEmpty()
                || handlerFqn == null || handlerFqn.isBlank() || parameterNames == null || routeSourceExcerpts == null
                || routeSourceExcerpts.isEmpty()) {
            throw new ApplicationProfileException("ENTRY_ROUTE_INVALID", "HTTP entry fields are invalid");
        }
        routeParts = List.copyOf(routeParts);
        parameterNames = List.copyOf(parameterNames);
        routeSourceExcerpts = List.copyOf(routeSourceExcerpts);
    }
}
