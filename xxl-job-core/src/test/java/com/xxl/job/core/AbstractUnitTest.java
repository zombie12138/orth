package com.xxl.job.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Abstract base class for unit tests in xxl-job-core.
 *
 * <p>Provides common setup and teardown for unit tests using Mockito.
 *
 * <p>Usage: Extend this class for unit tests that require mocking.
 *
 * <pre>{@code
 * @ExtendWith(MockitoExtension.class)
 * class MyServiceTest extends AbstractUnitTest {
 *     @Mock
 *     private MyDependency dependency;
 *
 *     @InjectMocks
 *     private MyService service;
 *
 *     @Test
 *     void testMyMethod() {
 *         // test code
 *     }
 * }
 * }</pre>
 */
@ExtendWith(MockitoExtension.class)
public abstract class AbstractUnitTest {

    @BeforeEach
    public void setUp() {
        // Common setup for all unit tests
        // Override in subclasses if needed
    }

    @AfterEach
    public void tearDown() {
        // Common cleanup for all unit tests
        // Override in subclasses if needed
    }
}
