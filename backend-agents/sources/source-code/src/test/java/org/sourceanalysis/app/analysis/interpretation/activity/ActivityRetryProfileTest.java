package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ActivityRetryProfileTest {

  @Test
  void oneAttemptDisablesRetryAndStageOverrideDoesNotAffectDraft() {
    ActivityRetryProfile profile =
        new ActivityRetryProfile(1, 0, 0, 1.0, 0.0, Set.of("REQUEST_TIMEOUT"), Map.of("REVIEW", 3));

    assertThat(profile.maxAttempts("DRAFT")).isEqualTo(1);
    assertThat(profile.maxAttempts("REVIEW")).isEqualTo(3);
    assertThat(profile.isRetryable("REQUEST_TIMEOUT", true, true)).isTrue();
    assertThat(profile.isRetryable("OUTCOME_UNKNOWN", true, false)).isFalse();
  }

  @Test
  void backoffIsBoundedAndUnknownStagesAndReasonsAreRejected() {
    ActivityRetryProfile profile =
        new ActivityRetryProfile(4, 1000, 3000, 2.0, 0.0, Set.of("RATE_LIMITED"), Map.of());

    assertThat(profile.backoffMillis(1)).isEqualTo(1000);
    assertThat(profile.backoffMillis(2)).isEqualTo(2000);
    assertThat(profile.backoffMillis(3)).isEqualTo(3000);
    assertThatThrownBy(() -> profile.maxAttempts("UNKNOWN"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ActivityRetryProfile(2, 0, 0, 1, 0, Set.of("MADE_UP"), Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
