package org.sourceanalysis.app.analysis.code;

/** One snapshot-bound selected-engine session. */
public interface JavaCodeSession extends AutoCloseable {

  JavaDeclarationCatalog catalog();

  EntryCodeContext collect(EntrySeed entry);

  EngineDescriptor descriptor();

  @Override
  void close();
}
