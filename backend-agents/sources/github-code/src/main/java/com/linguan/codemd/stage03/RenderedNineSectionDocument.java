package com.linguan.codemd.stage03;

/** Byte-stable Markdown output of the typed nine-section plan. */
public record RenderedNineSectionDocument(String rendererProfileId, String markdownSha256,
                                          String markdown) {
}
