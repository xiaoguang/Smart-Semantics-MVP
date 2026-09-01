package com.linguan.codemd.target.stage02.mappercatalog;

import java.util.List;

/** A source-only Java/XML candidate grouping; it deliberately is not a call binding. */
public record MapperCatalogEntry(
        String catalogEntryId,
        String javaInterfaceFqn,
        List<String> javaMethodCandidates,
        String xmlResourcePath,
        String xmlNamespace,
        List<String> xmlStatementCandidates,
        String bindingState) {
    public MapperCatalogEntry {
        if (catalogEntryId == null
                || !catalogEntryId.matches("mapper-catalog-entry:[0-9a-f]{64}")
                || javaInterfaceFqn == null
                || javaInterfaceFqn.isBlank()
                || javaMethodCandidates == null
                || javaMethodCandidates.isEmpty()
                || xmlResourcePath == null
                || xmlResourcePath.isBlank()
                || xmlNamespace == null
                || xmlNamespace.isBlank()
                || xmlStatementCandidates == null
                || xmlStatementCandidates.isEmpty()
                || !"CANDIDATE_NOT_YET_BOUND".equals(bindingState)) {
            throw new IllegalArgumentException("mapper catalog entry is invalid");
        }
        javaMethodCandidates = ordered(javaMethodCandidates, "Java method");
        xmlStatementCandidates = ordered(xmlStatementCandidates, "XML statement");
    }

    private static List<String> ordered(List<String> values, String subject) {
        String prior = null;
        for (String value : values) {
            if (value == null || value.isBlank() || (prior != null && prior.compareTo(value) >= 0)) {
                throw new IllegalArgumentException(subject + " candidates must be sorted and unique");
            }
            prior = value;
        }
        return List.copyOf(values);
    }
}
