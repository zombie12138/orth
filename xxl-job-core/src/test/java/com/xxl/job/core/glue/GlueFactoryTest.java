package com.xxl.job.core.glue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.xxl.job.core.handler.IJobHandler;

/**
 * Tests for {@link GlueFactory}.
 *
 * <p>Covers: Groovy script loading, caching, error handling, instance creation.
 */
class GlueFactoryTest {

    @BeforeEach
    void setUp() {
        // Reset to default frameless factory
        GlueFactory.refreshInstance(0);
    }

    @AfterEach
    void tearDown() {
        // Reset to default frameless factory
        GlueFactory.refreshInstance(0);
    }

    // ==================== Singleton Tests ====================

    @Test
    void testGetInstance_shouldReturnSingleton() {
        // When
        GlueFactory instance1 = GlueFactory.getInstance();
        GlueFactory instance2 = GlueFactory.getInstance();

        // Then
        assertThat(instance1).isSameAs(instance2);
    }

    @Test
    void testRefreshInstance_withType0_shouldReturnFramelessFactory() {
        // When
        GlueFactory.refreshInstance(0);
        GlueFactory instance = GlueFactory.getInstance();

        // Then
        assertThat(instance).isInstanceOf(GlueFactory.class);
        assertThat(instance.getClass().getName()).isEqualTo("com.xxl.job.core.glue.GlueFactory");
    }

    @Test
    void testRefreshInstance_withType1_shouldReturnSpringFactory() {
        // When
        GlueFactory.refreshInstance(1);
        GlueFactory instance = GlueFactory.getInstance();

        // Then
        assertThat(instance.getClass().getName())
                .isEqualTo("com.xxl.job.core.glue.impl.SpringGlueFactory");
    }

    // ==================== Load New Instance Tests ====================

    @Test
    void testLoadNewInstance_withValidGroovyScript_shouldCreateHandler() throws Exception {
        // Given
        String groovyCode =
                "package test\n"
                        + "import com.xxl.job.core.handler.IJobHandler\n"
                        + "class TestHandler extends IJobHandler {\n"
                        + "    @Override\n"
                        + "    void execute() throws Exception {\n"
                        + "        // Test job\n"
                        + "    }\n"
                        + "}";

        GlueFactory factory = GlueFactory.getInstance();

        // When
        IJobHandler handler = factory.loadNewInstance(groovyCode);

        // Then
        assertThat(handler).isNotNull();
        assertThat(handler).isInstanceOf(IJobHandler.class);
    }

    @Test
    void testLoadNewInstance_withSameCodeTwice_shouldCreateDifferentInstances() throws Exception {
        // Given
        String groovyCode =
                "package test\n"
                        + "import com.xxl.job.core.handler.IJobHandler\n"
                        + "class TestHandler2 extends IJobHandler {\n"
                        + "    @Override\n"
                        + "    void execute() throws Exception {}\n"
                        + "}";

        GlueFactory factory = GlueFactory.getInstance();

        // When
        IJobHandler handler1 = factory.loadNewInstance(groovyCode);
        IJobHandler handler2 = factory.loadNewInstance(groovyCode);

        // Then - different instances but same class
        assertThat(handler1).isNotSameAs(handler2);
        assertThat(handler1.getClass()).isEqualTo(handler2.getClass());
    }

    @Test
    void testLoadNewInstance_withNullCode_shouldThrowException() {
        // Given
        GlueFactory factory = GlueFactory.getInstance();

        // When & Then
        assertThatThrownBy(() -> factory.loadNewInstance(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instance is null");
    }

    @Test
    void testLoadNewInstance_withEmptyCode_shouldThrowException() {
        // Given
        GlueFactory factory = GlueFactory.getInstance();

        // When & Then
        assertThatThrownBy(() -> factory.loadNewInstance("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instance is null");
    }

    @Test
    void testLoadNewInstance_withNonIJobHandler_shouldThrowException() {
        // Given - script that doesn't extend IJobHandler
        String invalidCode =
                "package test\n" + "class NotAHandler {\n" + "    void execute() {}\n" + "}";

        GlueFactory factory = GlueFactory.getInstance();

        // When & Then
        assertThatThrownBy(() -> factory.loadNewInstance(invalidCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot convert from instance");
    }

    @Test
    void testLoadNewInstance_withInvalidGroovySyntax_shouldThrowException() {
        // Given - invalid Groovy code
        String invalidCode = "this is not valid groovy code { { {";

        GlueFactory factory = GlueFactory.getInstance();

        // When & Then
        assertThatThrownBy(() -> factory.loadNewInstance(invalidCode))
                .isInstanceOf(Exception.class);
    }

    // ==================== Caching Tests ====================

    @Test
    void testCodeSourceCaching_shouldReuseCompiledClass() throws Exception {
        // Given - same code loaded multiple times
        String groovyCode =
                "package test\n"
                        + "import com.xxl.job.core.handler.IJobHandler\n"
                        + "class TestHandler3 extends IJobHandler {\n"
                        + "    @Override\n"
                        + "    void execute() throws Exception {}\n"
                        + "}";

        GlueFactory factory = GlueFactory.getInstance();

        // When
        IJobHandler handler1 = factory.loadNewInstance(groovyCode);
        IJobHandler handler2 = factory.loadNewInstance(groovyCode);
        IJobHandler handler3 = factory.loadNewInstance(groovyCode);

        // Then - same class (cached), different instances
        assertThat(handler1.getClass()).isEqualTo(handler2.getClass());
        assertThat(handler2.getClass()).isEqualTo(handler3.getClass());
        assertThat(handler1).isNotSameAs(handler2);
        assertThat(handler2).isNotSameAs(handler3);
    }

    @Test
    void testCodeSourceCaching_withDifferentCode_shouldCreateDifferentClasses() throws Exception {
        // Given - different Groovy code
        String code1 =
                "package test\n"
                        + "import com.xxl.job.core.handler.IJobHandler\n"
                        + "class Handler1 extends IJobHandler {\n"
                        + "    @Override\n"
                        + "    void execute() throws Exception {}\n"
                        + "}";

        String code2 =
                "package test\n"
                        + "import com.xxl.job.core.handler.IJobHandler\n"
                        + "class Handler2 extends IJobHandler {\n"
                        + "    @Override\n"
                        + "    void execute() throws Exception {}\n"
                        + "}";

        GlueFactory factory = GlueFactory.getInstance();

        // When
        IJobHandler handler1 = factory.loadNewInstance(code1);
        IJobHandler handler2 = factory.loadNewInstance(code2);

        // Then - different classes
        assertThat(handler1.getClass()).isNotEqualTo(handler2.getClass());
    }

    // ==================== Inject Service Test ====================

    @Test
    void testInjectService_shouldNotThrowException() {
        // Given
        GlueFactory factory = GlueFactory.getInstance();
        Object instance = new Object();

        // When & Then - should complete without error
        factory.injectService(instance);
    }
}
