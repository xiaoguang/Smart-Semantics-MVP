package com.linguan.codemd.stage03;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Closed, per-Flow Round-2 instruction material.  It names only already
 * archived findings and reader locations; it cannot carry review prose or new
 * evidence.
 */
public record FlowImprovementOverlay(String schemaVersion, String flowSliceId, List<Directive> directives) {
    public FlowImprovementOverlay {
        require("flow-improvement-overlay-v1".equals(schemaVersion));
        require(digestId(flowSliceId, "flow-slice:"));
        require(directives != null && !directives.isEmpty());
        List<Directive> normalized = new ArrayList<>();
        Set<String> findingIds = new HashSet<>();
        for (Directive directive : directives) {
            require(directive != null && findingIds.add(directive.findingId()));
            normalized.add(directive);
        }
        normalized.sort(Comparator.comparing(Directive::findingId));
        directives = List.copyOf(normalized);
    }

    /** One finite correction instruction for an exact archived finding. */
    public record Directive(String findingId, String findingCode, String permittedCorrection,
                            List<String> readerItemKeys, List<Integer> sectionNumbers) {
        public Directive {
            require(digestId(findingId, "finding:"));
            require(findingCode != null && findingCode.matches("[A-Z][A-Z0-9_]{0,127}"));
            require(Set.of("NARROW_OR_DROP", "SELECT_FROZEN_TERM", "REORDER_OR_REPHRASE")
                    .contains(permittedCorrection));
            readerItemKeys = sortedUniqueReaderKeys(readerItemKeys);
            sectionNumbers = sortedUniqueSections(sectionNumbers);
            require(!readerItemKeys.isEmpty() || !sectionNumbers.isEmpty());
        }
    }

    private static List<String> sortedUniqueReaderKeys(List<String> values) {
        require(values != null);
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            require(value != null && value.startsWith("reader:") && value.length() > "reader:".length()
                    && value.chars().noneMatch(Character::isWhitespace) && seen.add(value));
            result.add(value);
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    private static List<Integer> sortedUniqueSections(List<Integer> values) {
        require(values != null);
        List<Integer> result = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Integer value : values) {
            require(value != null && value >= 1 && value <= 9 && seen.add(value));
            result.add(value);
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    private static boolean digestId(String value, String prefix) {
        return value != null && value.matches(java.util.regex.Pattern.quote(prefix) + "[0-9a-f]{64}");
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("invalid flow improvement overlay");
        }
    }
}
