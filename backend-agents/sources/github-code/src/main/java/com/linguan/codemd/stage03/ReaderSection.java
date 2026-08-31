package com.linguan.codemd.stage03;

import java.util.List;

/** One of the exactly nine ordered Chinese reader sections. */
public record ReaderSection(String sectionKey, String heading, List<ReaderItem> items) {
    public ReaderSection {
        items = List.copyOf(items);
    }
}
