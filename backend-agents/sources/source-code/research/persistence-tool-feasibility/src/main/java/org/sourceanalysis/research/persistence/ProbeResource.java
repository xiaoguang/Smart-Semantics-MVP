package org.sourceanalysis.research.persistence;

/** Original frozen XML plus the namespace observed through the safe DOM/MyBatis reader. */
public record ProbeResource(
    String resourcePath, String rawSource, String namespace, String parseStatus) {}
