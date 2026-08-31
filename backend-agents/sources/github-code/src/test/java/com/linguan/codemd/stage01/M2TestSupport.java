package com.linguan.codemd.stage01;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test-only assertions for the public Stage 01 M2 result graph.
 *
 * <p>The graph node classes are deliberately not coupled into this helper.
 * The contract is exercised through the public record accessors and the
 * stable field names from the stage document, while the M2 implementation is
 * free to keep its graph records package-private.</p>
 */
final class M2TestSupport {
    private M2TestSupport() {
    }

    static RepositoryUnderstanding understand(Path snapshotRoot) {
        return new Stage01Analyzer().understand(Stage01Fixtures.request(snapshotRoot));
    }

    static RepositoryUnderstanding understand(FrozenRepositoryRequest request) {
        return new Stage01Analyzer().understand(request);
    }

    static Object model(RepositoryUnderstanding understanding) {
        return member(understanding, "repositoryModel");
    }

    static Object report(RepositoryUnderstanding understanding) {
        return member(understanding, "capabilityReport");
    }

    static List<?> entries(Object model) {
        return list(model, "entries");
    }

    static List<?> nodes(Object model) {
        return list(model, "nodes");
    }

    static List<?> edges(Object model) {
        return list(model, "edges");
    }

    static List<?> controlFlows(Object model) {
        return list(model, "controlFlows");
    }

    static List<?> sites(RepositoryUnderstanding understanding) {
        return list(report(understanding), "sites");
    }

    static String text(Object target, String accessor) {
        Object value = member(target, accessor);
        return value == null ? null : value.toString();
    }

    static int number(Object target, String accessor) {
        Object value = member(target, accessor);
        assertTrue(value instanceof Number,
                () -> accessor + " must be numeric but was " + value);
        return ((Number) value).intValue();
    }

    static String locator(Object target) {
        Object locator = member(target, "locator");
        return text(locator, "path") + ":" + number(locator, "startLine");
    }

    static String locatorWithEnd(Object target) {
        Object locator = member(target, "locator");
        int startLine = number(locator, "startLine");
        int endLine = number(locator, "endLine");
        return text(locator, "path") + ":" + startLine
                + (startLine == endLine ? "" : "-" + endLine);
    }

    static Map<String, Object> nodesById(Object model) {
        Map<String, Object> result = new HashMap<>();
        for (Object node : nodes(model)) {
            result.put(text(node, "nodeId"), node);
        }
        return result;
    }

    static Set<String> exactCallBindings(Object model) {
        Map<String, Object> nodesById = nodesById(model);
        Set<String> result = new LinkedHashSet<>();
        for (Object edge : edges(model)) {
            if (!"CALL_TARGET".equals(text(edge, "kind"))
                    || !"EXACT".equals(text(edge, "resolution"))) {
                continue;
            }
            Object from = nodesById.get(text(edge, "fromNodeId"));
            Object to = nodesById.get(text(edge, "toNodeId"));
            assertNotNull(from, "CALL_TARGET source node must exist");
            assertNotNull(to, "CALL_TARGET target node must exist");
            result.add(locator(from) + " -> " + locator(to));
        }
        return result;
    }

    static Set<String> exactMapperMethodBindings(Object model) {
        Map<String, Object> nodesById = nodesById(model);
        Set<String> result = new LinkedHashSet<>();
        for (Object edge : edges(model)) {
            if (!"METHOD_STATEMENT".equals(text(edge, "kind"))
                    || !"EXACT".equals(text(edge, "resolution"))) {
                continue;
            }
            Object from = nodesById.get(text(edge, "fromNodeId"));
            Object to = nodesById.get(text(edge, "toNodeId"));
            assertNotNull(from, "METHOD_STATEMENT source node must exist");
            assertNotNull(to, "METHOD_STATEMENT target node must exist");
            result.add(locator(from) + " -> " + locator(to));
        }
        return result;
    }

    static Set<String> exactStatementSqlBindings(Object model, String path, int startLine) {
        Map<String, Object> nodesById = nodesById(model);
        Set<String> result = new LinkedHashSet<>();
        for (Object edge : edges(model)) {
            if (!"STATEMENT_SQL_FRAGMENT".equals(text(edge, "kind"))
                    || !"EXACT".equals(text(edge, "resolution"))) {
                continue;
            }
            Object from = nodesById.get(text(edge, "fromNodeId"));
            Object to = nodesById.get(text(edge, "toNodeId"));
            assertNotNull(from, "STATEMENT_SQL_FRAGMENT source node must exist");
            assertNotNull(to, "STATEMENT_SQL_FRAGMENT target node must exist");
            if (locator(from).equals(path + ":" + startLine)) {
                result.add(locator(from) + " -> " + locator(to));
            }
        }
        return result;
    }

    static Set<String> nodeKindsAt(Object model, String path, int firstLine, int lastLine) {
        Set<String> result = new LinkedHashSet<>();
        for (Object node : nodes(model)) {
            Object locator = member(node, "locator");
            String nodePath = text(locator, "path");
            int startLine = number(locator, "startLine");
            int endLine = number(locator, "endLine");
            if (path.equals(nodePath) && startLine <= lastLine && endLine >= firstLine) {
                result.add(text(node, "kind"));
            }
        }
        return result;
    }

    static Set<String> terminalLocators(Object model) {
        Map<String, Object> nodesById = nodesById(model);
        Set<String> result = new LinkedHashSet<>();
        for (Object flow : controlFlows(model)) {
            for (Object nodeId : list(flow, "terminalNodeIds")) {
                Object node = nodesById.get(nodeId.toString());
                assertNotNull(node, "CFG terminal node must exist");
                result.add(locator(node));
            }
        }
        return result;
    }

    static List<?> sitesAt(RepositoryUnderstanding understanding, String path, int line) {
        List<Object> result = new ArrayList<>();
        for (Object site : sites(understanding)) {
            Object locator = member(site, "locator");
            if (path.equals(text(locator, "path"))
                    && number(locator, "startLine") <= line
                    && number(locator, "endLine") >= line) {
                result.add(site);
            }
        }
        return result;
    }

    static void assertCoverageEquation(RepositoryUnderstanding understanding) {
        Object coverage = member(report(understanding), "coverage");
        int reachable = number(coverage, "reachableSemanticSites");
        int supported = number(coverage, "supportedSemanticSites");
        int unsupported = number(coverage, "unsupportedReachableSites");
        int ambiguous = number(coverage, "ambiguousReachableSites");
        int overLimit = number(coverage, "overLimitReachableSites");
        assertEquals(reachable, supported + unsupported + ambiguous + overLimit,
                "capability coverage must partition every reachable site");
        assertEquals(reachable, sites(understanding).size(),
                "every reachable semantic site must remain in the report");
        for (Object site : sites(understanding)) {
            String disposition = text(site, "disposition");
            assertNotNull(disposition, "every site must have a disposition");
            String reason = text(site, "reasonCode");
            if ("SUPPORTED".equals(disposition)) {
                assertEquals(null, reason, "supported sites must not carry a reason code");
            } else {
                assertNotNull(reason,
                        () -> "non-supported site has no reason: " + locatorWithEnd(site));
            }
        }
    }

    static void assertDynamicGap(RepositoryUnderstanding understanding, String path,
                                 int statementLine) {
        List<?> dynamicSites = sitesAt(understanding, path, statementLine);
        assertFalse(dynamicSites.isEmpty(), "dynamic mapper statement must be a capability site");
        assertTrue(dynamicSites.stream().anyMatch(site -> {
            String disposition = text(site, "disposition");
            String reason = text(site, "reasonCode");
            return "UNSUPPORTED".equals(disposition)
                    && reason != null && reason.toUpperCase().contains("DYNAMIC");
        }), "dynamic mapper SQL must be explicit UNSUPPORTED/DYNAMIC gap");
    }

    static void assertAmbiguousGap(RepositoryUnderstanding understanding, String path,
                                   int line) {
        List<?> ambiguousSites = sitesAt(understanding, path, line);
        assertTrue(ambiguousSites.stream().anyMatch(site -> {
            String disposition = text(site, "disposition");
            String reason = text(site, "reasonCode");
            return "AMBIGUOUS".equals(disposition)
                    && reason != null && reason.toUpperCase().contains("AMBIGUOUS");
        }), "ambiguous call must remain an explicit AMBIGUOUS site");
    }

    static void assertSiteDisposition(RepositoryUnderstanding understanding, String kind,
                                      String path, int line, String disposition,
                                      String reasonCode) {
        List<?> matching = sites(understanding).stream()
                .filter(site -> kind.equals(text(site, "kind")))
                .filter(site -> {
                    Object locator = member(site, "locator");
                    return path.equals(text(locator, "path"))
                            && number(locator, "startLine") <= line
                            && number(locator, "endLine") >= line;
                })
                .toList();
        assertTrue(matching.stream().anyMatch(site -> disposition.equals(text(site, "disposition"))
                        && reasonCode.equals(text(site, "reasonCode"))),
                () -> "expected " + disposition + "/" + reasonCode + " at " + path + ":" + line
                        + " but found " + matching);
    }

    static void assertOverLimitSite(RepositoryUnderstanding understanding, String kind,
                                    String path, int line, String reasonToken) {
        List<?> matching = sites(understanding).stream()
                .filter(site -> kind.equals(text(site, "kind")))
                .filter(site -> {
                    Object locator = member(site, "locator");
                    return path.equals(text(locator, "path"))
                            && number(locator, "startLine") <= line
                            && number(locator, "endLine") >= line;
                })
                .toList();
        assertTrue(matching.stream().anyMatch(site -> {
            String reason = text(site, "reasonCode");
            return "OVER_LIMIT".equals(text(site, "disposition"))
                    && reason != null && reason.toUpperCase().contains(reasonToken);
        }), () -> "expected OVER_LIMIT/" + reasonToken + " at " + path + ":" + line
                + " but found " + matching);
    }

    static void assertUnresolvedCallGap(RepositoryUnderstanding understanding, String path,
                                        int line, String reasonToken) {
        List<?> matching = sitesAt(understanding, path, line);
        assertTrue(matching.stream().anyMatch(site -> {
            String disposition = text(site, "disposition");
            String reason = text(site, "reasonCode");
            return ("UNSUPPORTED".equals(disposition) || "AMBIGUOUS".equals(disposition))
                    && reason != null && reason.toUpperCase().contains(reasonToken);
        }), () -> "expected unsupported/ambiguous " + reasonToken + " gap at "
                + path + ":" + line + " but found " + matching);
    }

    private static List<?> list(Object target, String accessor) {
        Object value = member(target, accessor);
        assertTrue(value instanceof List,
                () -> accessor + " must be a list but was " + value);
        return (List<?>) value;
    }

    static Object member(Object target, String accessor) {
        assertNotNull(target, "cannot read " + accessor + " from null");
        try {
            Method method;
            try {
                method = target.getClass().getMethod(accessor);
            } catch (NoSuchMethodException unavailable) {
                method = target.getClass().getDeclaredMethod(accessor);
            }
            method.setAccessible(true);
            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException failure) {
            throw new AssertionError("missing M2 accessor " + accessor + " on "
                    + target.getClass().getName(), failure);
        } catch (InvocationTargetException failure) {
            throw new AssertionError("M2 accessor " + accessor + " failed", failure.getCause());
        }
    }
}
