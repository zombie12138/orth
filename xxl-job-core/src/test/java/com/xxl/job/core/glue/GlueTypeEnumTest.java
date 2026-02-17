package com.xxl.job.core.glue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GlueTypeEnum}.
 *
 * <p>Covers: enum values, match method, getters.
 */
class GlueTypeEnumTest {

    @Test
    void testEnumValues_shouldHaveExpectedTypes() {
        // When
        GlueTypeEnum[] values = GlueTypeEnum.values();

        // Then
        assertThat(values).hasSize(8);
        assertThat(values)
                .containsExactly(
                        GlueTypeEnum.BEAN,
                        GlueTypeEnum.GLUE_GROOVY,
                        GlueTypeEnum.GLUE_SHELL,
                        GlueTypeEnum.GLUE_PYTHON,
                        GlueTypeEnum.GLUE_PYTHON2,
                        GlueTypeEnum.GLUE_NODEJS,
                        GlueTypeEnum.GLUE_POWERSHELL,
                        GlueTypeEnum.GLUE_PHP);
    }

    @Test
    void testBeanType_shouldNotBeScript() {
        // Then
        assertThat(GlueTypeEnum.BEAN.getDesc()).isEqualTo("BEAN");
        assertThat(GlueTypeEnum.BEAN.isScript()).isFalse();
        assertThat(GlueTypeEnum.BEAN.getCmd()).isNull();
        assertThat(GlueTypeEnum.BEAN.getSuffix()).isNull();
    }

    @Test
    void testGroovyType_shouldNotBeScript() {
        // Then
        assertThat(GlueTypeEnum.GLUE_GROOVY.getDesc()).isEqualTo("GLUE(Java)");
        assertThat(GlueTypeEnum.GLUE_GROOVY.isScript()).isFalse();
        assertThat(GlueTypeEnum.GLUE_GROOVY.getCmd()).isNull();
        assertThat(GlueTypeEnum.GLUE_GROOVY.getSuffix()).isNull();
    }

    @Test
    void testShellType_shouldBeScript() {
        // Then
        assertThat(GlueTypeEnum.GLUE_SHELL.getDesc()).isEqualTo("GLUE(Shell)");
        assertThat(GlueTypeEnum.GLUE_SHELL.isScript()).isTrue();
        assertThat(GlueTypeEnum.GLUE_SHELL.getCmd()).isEqualTo("bash");
        assertThat(GlueTypeEnum.GLUE_SHELL.getSuffix()).isEqualTo(".sh");
    }

    @Test
    void testPython3Type_shouldBeScript() {
        // Then
        assertThat(GlueTypeEnum.GLUE_PYTHON.getDesc()).isEqualTo("GLUE(Python3)");
        assertThat(GlueTypeEnum.GLUE_PYTHON.isScript()).isTrue();
        assertThat(GlueTypeEnum.GLUE_PYTHON.getCmd()).isEqualTo("python3");
        assertThat(GlueTypeEnum.GLUE_PYTHON.getSuffix()).isEqualTo(".py");
    }

    @Test
    void testPython2Type_shouldBeScript() {
        // Then
        assertThat(GlueTypeEnum.GLUE_PYTHON2.getDesc()).isEqualTo("GLUE(Python2)");
        assertThat(GlueTypeEnum.GLUE_PYTHON2.isScript()).isTrue();
        assertThat(GlueTypeEnum.GLUE_PYTHON2.getCmd()).isEqualTo("python");
        assertThat(GlueTypeEnum.GLUE_PYTHON2.getSuffix()).isEqualTo(".py");
    }

    @Test
    void testNodejsType_shouldBeScript() {
        // Then
        assertThat(GlueTypeEnum.GLUE_NODEJS.getDesc()).isEqualTo("GLUE(Nodejs)");
        assertThat(GlueTypeEnum.GLUE_NODEJS.isScript()).isTrue();
        assertThat(GlueTypeEnum.GLUE_NODEJS.getCmd()).isEqualTo("node");
        assertThat(GlueTypeEnum.GLUE_NODEJS.getSuffix()).isEqualTo(".js");
    }

    @Test
    void testPowershellType_shouldBeScript() {
        // Then
        assertThat(GlueTypeEnum.GLUE_POWERSHELL.getDesc()).isEqualTo("GLUE(PowerShell)");
        assertThat(GlueTypeEnum.GLUE_POWERSHELL.isScript()).isTrue();
        assertThat(GlueTypeEnum.GLUE_POWERSHELL.getCmd()).isEqualTo("powershell");
        assertThat(GlueTypeEnum.GLUE_POWERSHELL.getSuffix()).isEqualTo(".ps1");
    }

    @Test
    void testPhpType_shouldBeScript() {
        // Then
        assertThat(GlueTypeEnum.GLUE_PHP.getDesc()).isEqualTo("GLUE(PHP)");
        assertThat(GlueTypeEnum.GLUE_PHP.isScript()).isTrue();
        assertThat(GlueTypeEnum.GLUE_PHP.getCmd()).isEqualTo("php");
        assertThat(GlueTypeEnum.GLUE_PHP.getSuffix()).isEqualTo(".php");
    }

    @Test
    void testMatch_withValidName_shouldReturnMatchingEnum() {
        // When & Then
        assertThat(GlueTypeEnum.match("BEAN")).isEqualTo(GlueTypeEnum.BEAN);
        assertThat(GlueTypeEnum.match("GLUE_GROOVY")).isEqualTo(GlueTypeEnum.GLUE_GROOVY);
        assertThat(GlueTypeEnum.match("GLUE_SHELL")).isEqualTo(GlueTypeEnum.GLUE_SHELL);
        assertThat(GlueTypeEnum.match("GLUE_PYTHON")).isEqualTo(GlueTypeEnum.GLUE_PYTHON);
        assertThat(GlueTypeEnum.match("GLUE_PYTHON2")).isEqualTo(GlueTypeEnum.GLUE_PYTHON2);
        assertThat(GlueTypeEnum.match("GLUE_NODEJS")).isEqualTo(GlueTypeEnum.GLUE_NODEJS);
        assertThat(GlueTypeEnum.match("GLUE_POWERSHELL")).isEqualTo(GlueTypeEnum.GLUE_POWERSHELL);
        assertThat(GlueTypeEnum.match("GLUE_PHP")).isEqualTo(GlueTypeEnum.GLUE_PHP);
    }

    @Test
    void testMatch_withInvalidName_shouldReturnNull() {
        // When
        GlueTypeEnum result = GlueTypeEnum.match("INVALID_TYPE");

        // Then
        assertThat(result).isNull();
    }

    @Test
    void testMatch_withNullName_shouldReturnNull() {
        // When
        GlueTypeEnum result = GlueTypeEnum.match(null);

        // Then
        assertThat(result).isNull();
    }
}
