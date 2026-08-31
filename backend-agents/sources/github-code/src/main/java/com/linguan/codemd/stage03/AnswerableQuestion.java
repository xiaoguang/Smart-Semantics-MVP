package com.linguan.codemd.stage03;

import java.util.List;

/** A reader question with nonempty linked answer knowledge. */
public record AnswerableQuestion(String questionId, String text, List<String> answerItemIds) {
    public AnswerableQuestion {
        answerItemIds = List.copyOf(answerItemIds);
    }
}
