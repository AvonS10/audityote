package io.muzoo.ssc.controlmap.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the demo-account allowlist normalization: matching is case-insensitive and
 * trim-tolerant, blank entries (including the empty string bound from an unset env var) are dropped,
 * and an empty list locks nothing.
 */
class DemoAccountPolicyTest {

    @Test
    void matchesListedEmailsCaseInsensitivelyAndTrimmed() {
        DemoAccountPolicy policy = new DemoAccountPolicy(List.of("  Demo@Lock.Test ", "two@lock.test"));
        assertThat(policy.isLocked("demo@lock.test")).isTrue();
        assertThat(policy.isLocked("DEMO@LOCK.TEST")).isTrue();
        assertThat(policy.isLocked("  demo@lock.test  ")).isTrue();
        assertThat(policy.isLocked("two@lock.test")).isTrue();
        assertThat(policy.isLocked("someone-else@lock.test")).isFalse();
        assertThat(policy.isLocked(null)).isFalse();
    }

    @Test
    void emptyOrBlankConfigLocksNothing() {
        assertThat(new DemoAccountPolicy(List.of()).isLocked("anyone@lock.test")).isFalse();
        // An unset ${DEMO_LOCKED_EMAILS:} binds as a single empty string — it must not lock anyone.
        assertThat(new DemoAccountPolicy(List.of("")).isLocked("anyone@lock.test")).isFalse();
    }
}
