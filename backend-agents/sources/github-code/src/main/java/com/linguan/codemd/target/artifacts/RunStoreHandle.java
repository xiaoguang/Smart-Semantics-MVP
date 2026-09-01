package com.linguan.codemd.target.artifacts;

/** Opaque owner of the private filesystem publication engine. */
public interface RunStoreHandle extends AutoCloseable {
    @Override
    void close();
}
