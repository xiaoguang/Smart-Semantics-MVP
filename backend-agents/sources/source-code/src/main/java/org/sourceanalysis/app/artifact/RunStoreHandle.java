package org.sourceanalysis.app.artifact;

/** Opaque handle for the private filesystem publication engine shared by artifact stores. */
public interface RunStoreHandle extends AutoCloseable {

  @Override
  void close();
}
