package org.sourceanalysis.research.persistence;

/** Corresponding conditional column and value fragments retained from Mapper DOM structure. */
public record ConditionalPair(String expression, String columnFragment, String valueFragment) {}
