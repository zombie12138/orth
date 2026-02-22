package com.abyss.orth.core.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Const}.
 *
 * <p>Covers: constants values.
 */
class ConstTest {

    @Test
    void testAccessTokenConstant() {
        assertThat(Const.ORTH_ACCESS_TOKEN).isEqualTo("ORTH-ACCESS-TOKEN");
    }

    @Test
    void testBeatTimeout() {
        assertThat(Const.BEAT_TIMEOUT).isEqualTo(30);
    }

    @Test
    void testDeadTimeout_shouldBeThreeTimesBeatTimeout() {
        assertThat(Const.DEAD_TIMEOUT).isEqualTo(90);
        assertThat(Const.DEAD_TIMEOUT).isEqualTo(Const.BEAT_TIMEOUT * 3);
    }
}
