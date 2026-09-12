package org.sourceanalysis.app.analysis.code;

/** An internal selected-engine seam; it never accepts an arbitrary filesystem path. */
public interface JavaCodeEngine {

  JavaCodeSession open(VerifiedJavaProject project);
}
