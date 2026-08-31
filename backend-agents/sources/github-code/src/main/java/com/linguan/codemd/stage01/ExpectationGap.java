package com.linguan.codemd.stage01;

import java.util.List;

/** One bounded absence observation produced only by the frozen expectation profile. */
public record ExpectationGap(String gapId, String expectationId, String triggerRuleId,
                             List<SearchedScope> searchedScope, List<String> observationNodeIds,
                             AbsenceEvidence absenceEvidence, String reasonCode,
                             String questionTemplateKey) {
    public ExpectationGap {
        searchedScope = List.copyOf(searchedScope);
        observationNodeIds = List.copyOf(observationNodeIds);
    }
}
