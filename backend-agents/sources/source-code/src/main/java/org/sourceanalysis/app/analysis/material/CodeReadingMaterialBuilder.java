package org.sourceanalysis.app.analysis.material;

/** Organizes already-read Java and optional persistence material without navigation or parsing. */
public interface CodeReadingMaterialBuilder {

  CodeReadingMaterialSet build(CodeReadingMaterialRequest request);
}
