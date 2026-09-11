package org.sourceanalysis.app.adapter.provider;

/** Shared transport seam for a model that accepts canonical structured input and returns JSON. */
public interface StructuredModelProvider {

  StructuredModelResponse generate(StructuredModelRequest request);
}
