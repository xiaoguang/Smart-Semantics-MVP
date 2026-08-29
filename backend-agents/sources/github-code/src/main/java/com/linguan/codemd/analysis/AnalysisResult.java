package com.linguan.codemd.analysis;

import com.linguan.codemd.discovery.SourceLocator;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Canonical, deterministic Phase 2 facts and analysis gaps. */
public record AnalysisResult(List<CodeFact> codeFacts,
                             List<ConditionFact> conditionFacts,
                             List<Gap> gaps) {
    public AnalysisResult {
        codeFacts = ordered(codeFacts, Comparator.comparing(CodeFact::kind)
                .thenComparing(CodeFact::subject)
                .thenComparing(CodeFact::object)
                .thenComparing(CodeFact::resolution)
                .thenComparing(fact -> fact.sourceLocator().relativePath())
                .thenComparingInt(fact -> fact.sourceLocator().startLine())
                .thenComparing(CodeFact::factId));
        conditionFacts = ordered(conditionFacts, Comparator.comparing(ConditionFact::expression)
                .thenComparing(fact -> fact.sourceLocator().relativePath())
                .thenComparingInt(fact -> fact.sourceLocator().startLine())
                .thenComparing(ConditionFact::factId));
        gaps = ordered(gaps, Comparator.comparing(Gap::code)
                .thenComparing(Gap::subject)
                .thenComparing(gap -> gap.sourceLocator().relativePath())
                .thenComparingInt(gap -> gap.sourceLocator().startLine()));
    }

    private static <T> List<T> ordered(List<T> values, Comparator<? super T> comparator) {
        Objects.requireNonNull(values, "values");
        return values.stream().map(value -> Objects.requireNonNull(value, "analysis value"))
                .distinct().sorted(comparator).toList();
    }
}
