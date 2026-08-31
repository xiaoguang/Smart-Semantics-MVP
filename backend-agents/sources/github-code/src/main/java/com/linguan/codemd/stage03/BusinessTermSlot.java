package com.linguan.codemd.stage03;

/** A display value resolved from the frozen business-term registry. */
public record BusinessTermSlot(String slotKey, String meaningId, String value) implements ReaderSlot {
}
