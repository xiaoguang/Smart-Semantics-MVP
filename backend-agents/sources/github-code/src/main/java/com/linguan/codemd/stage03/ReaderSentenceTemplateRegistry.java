package com.linguan.codemd.stage03;

import java.util.List;

/** Optional frozen reader templates; generated code only uses typed slots. */
public record ReaderSentenceTemplateRegistry(String schemaVersion, String registryId, String sha256,
                                             List<ReaderSentenceTemplate> templates) {
    public ReaderSentenceTemplateRegistry {
        templates = templates == null ? null : List.copyOf(templates);
    }
}
