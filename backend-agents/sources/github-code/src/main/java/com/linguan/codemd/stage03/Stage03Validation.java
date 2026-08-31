package com.linguan.codemd.stage03;

import java.util.List;

/** Successful validation facts retained outside reader prose. */
public record Stage03Validation(List<String> completedChecks) {
    public Stage03Validation {
        completedChecks = List.copyOf(completedChecks);
    }
}
