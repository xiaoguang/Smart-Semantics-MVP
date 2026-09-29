package org.sourceanalysis.app.capture.preparation;

import java.io.IOException;

/** Measures the version of the already trusted Git executable used for a fixed Git origin. */
@FunctionalInterface
interface TrustedGitVersionProbe {

  String measure() throws IOException;
}
