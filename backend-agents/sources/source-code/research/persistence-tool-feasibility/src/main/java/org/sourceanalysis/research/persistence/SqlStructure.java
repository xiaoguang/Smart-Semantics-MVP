package org.sourceanalysis.research.persistence;

import java.util.List;

/** Bounded AST/DOM projection for reading; it is never a bound or executable SQL statement. */
public record SqlStructure(
    String statementType,
    List<String> projections,
    List<String> tables,
    List<JoinProjection> joins,
    String where,
    List<String> groupBy,
    List<DynamicCondition> dynamicConditions,
    List<ConditionalPair> conditionalPairs,
    List<TemplateToken> templateTokens) {
  public SqlStructure {
    projections = List.copyOf(projections);
    tables = List.copyOf(tables);
    joins = List.copyOf(joins);
    groupBy = List.copyOf(groupBy);
    dynamicConditions = List.copyOf(dynamicConditions);
    conditionalPairs = List.copyOf(conditionalPairs);
    templateTokens = List.copyOf(templateTokens);
  }
}
