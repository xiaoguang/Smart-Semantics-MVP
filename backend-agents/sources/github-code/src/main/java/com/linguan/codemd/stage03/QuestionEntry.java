package com.linguan.codemd.stage03;

import java.util.List;

/** One bounded unresolved-evidence question template. */
public record QuestionEntry(String questionKey, List<String> allowedGapReasonCodes,
                            String readerTemplateKey) {
    public QuestionEntry {
        allowedGapReasonCodes = allowedGapReasonCodes == null ? null : List.copyOf(allowedGapReasonCodes);
    }
}
