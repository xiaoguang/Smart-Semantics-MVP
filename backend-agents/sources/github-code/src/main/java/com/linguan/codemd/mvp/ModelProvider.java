package com.linguan.codemd.mvp;

/**
 * A narrow seam for a recorded or scripted structured-response provider.
 * Implementations must not read source files or return Markdown.
 */
@FunctionalInterface
public interface ModelProvider {
    String execute(ModelTask task);
}
