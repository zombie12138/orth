package com.xxl.job.core.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExecutorBlockStrategyEnum}.
 *
 * <p>Covers: enum values, match method, getters/setters.
 */
class ExecutorBlockStrategyEnumTest {

    @Test
    void testEnumValues_shouldHaveExpectedStrategies() {
        // When
        ExecutorBlockStrategyEnum[] values = ExecutorBlockStrategyEnum.values();

        // Then
        assertThat(values).hasSize(3);
        assertThat(values)
                .containsExactly(
                        ExecutorBlockStrategyEnum.SERIAL_EXECUTION,
                        ExecutorBlockStrategyEnum.DISCARD_LATER,
                        ExecutorBlockStrategyEnum.COVER_EARLY);
    }

    @Test
    void testGetTitle_shouldReturnCorrectTitles() {
        // Then
        assertThat(ExecutorBlockStrategyEnum.SERIAL_EXECUTION.getTitle())
                .isEqualTo("Serial execution");
        assertThat(ExecutorBlockStrategyEnum.DISCARD_LATER.getTitle()).isEqualTo("Discard Later");
        assertThat(ExecutorBlockStrategyEnum.COVER_EARLY.getTitle()).isEqualTo("Cover Early");
    }

    @Test
    void testSetTitle_shouldUpdateTitle() {
        // Given
        ExecutorBlockStrategyEnum strategy = ExecutorBlockStrategyEnum.SERIAL_EXECUTION;
        String originalTitle = strategy.getTitle();

        // When
        strategy.setTitle("New Title");

        // Then
        assertThat(strategy.getTitle()).isEqualTo("New Title");

        // Cleanup - restore original title
        strategy.setTitle(originalTitle);
    }

    @Test
    void testMatch_withValidName_shouldReturnMatchingEnum() {
        // When
        ExecutorBlockStrategyEnum result =
                ExecutorBlockStrategyEnum.match(
                        "SERIAL_EXECUTION", ExecutorBlockStrategyEnum.DISCARD_LATER);

        // Then
        assertThat(result).isEqualTo(ExecutorBlockStrategyEnum.SERIAL_EXECUTION);
    }

    @Test
    void testMatch_withInvalidName_shouldReturnDefault() {
        // When
        ExecutorBlockStrategyEnum result =
                ExecutorBlockStrategyEnum.match(
                        "INVALID_STRATEGY", ExecutorBlockStrategyEnum.COVER_EARLY);

        // Then
        assertThat(result).isEqualTo(ExecutorBlockStrategyEnum.COVER_EARLY);
    }

    @Test
    void testMatch_withNullName_shouldReturnDefault() {
        // When
        ExecutorBlockStrategyEnum result =
                ExecutorBlockStrategyEnum.match(null, ExecutorBlockStrategyEnum.SERIAL_EXECUTION);

        // Then
        assertThat(result).isEqualTo(ExecutorBlockStrategyEnum.SERIAL_EXECUTION);
    }

    @Test
    void testMatch_withAllEnumNames_shouldReturnCorrectEnum() {
        // When & Then
        assertThat(
                        ExecutorBlockStrategyEnum.match(
                                "SERIAL_EXECUTION", ExecutorBlockStrategyEnum.DISCARD_LATER))
                .isEqualTo(ExecutorBlockStrategyEnum.SERIAL_EXECUTION);

        assertThat(
                        ExecutorBlockStrategyEnum.match(
                                "DISCARD_LATER", ExecutorBlockStrategyEnum.SERIAL_EXECUTION))
                .isEqualTo(ExecutorBlockStrategyEnum.DISCARD_LATER);

        assertThat(
                        ExecutorBlockStrategyEnum.match(
                                "COVER_EARLY", ExecutorBlockStrategyEnum.SERIAL_EXECUTION))
                .isEqualTo(ExecutorBlockStrategyEnum.COVER_EARLY);
    }
}
