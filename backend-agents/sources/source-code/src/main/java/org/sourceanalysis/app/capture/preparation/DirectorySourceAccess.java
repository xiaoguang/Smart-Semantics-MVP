package org.sourceanalysis.app.capture.preparation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** Narrow no-follow filesystem boundary for directory source preparation. */
interface DirectorySourceAccess {

  DirectoryStream<Path> openDirectory(Path directory) throws IOException;

  BasicFileAttributes readAttributes(Path path) throws IOException;

  InputStream openInput(Path path) throws IOException;
}
