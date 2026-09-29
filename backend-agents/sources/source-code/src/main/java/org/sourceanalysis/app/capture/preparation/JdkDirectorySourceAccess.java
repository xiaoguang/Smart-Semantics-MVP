package org.sourceanalysis.app.capture.preparation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** JDK-backed directory access that never follows a final symlink for attributes or bytes. */
final class JdkDirectorySourceAccess implements DirectorySourceAccess {

  @Override
  public DirectoryStream<Path> openDirectory(Path directory) throws IOException {
    return Files.newDirectoryStream(directory);
  }

  @Override
  public BasicFileAttributes readAttributes(Path path) throws IOException {
    return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
  }

  @Override
  public InputStream openInput(Path path) throws IOException {
    return Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
  }
}
