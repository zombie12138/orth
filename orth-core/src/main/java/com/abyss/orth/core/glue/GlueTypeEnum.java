package com.abyss.orth.core.glue;

import java.util.Arrays;

/**
 * Glue type enumeration for different job execution modes.
 *
 * <p>Supports Bean-based handlers, Groovy scripts, and various scripting languages including Shell,
 * Python, NodeJS, PowerShell, and PHP.
 */
public enum GlueTypeEnum {
    /** Bean mode: executes registered Java handler methods annotated with @OrthJob */
    BEAN("BEAN", false, null, null),

    /** Groovy mode: executes Java/Groovy code dynamically compiled at runtime */
    GLUE_GROOVY("GLUE(Java)", false, null, null),

    /** Shell script mode */
    GLUE_SHELL("GLUE(Shell)", true, "bash", ".sh"),

    /** Python 3 script mode */
    GLUE_PYTHON("GLUE(Python3)", true, "python3", ".py"),

    /** Python 2 script mode (legacy, deprecated) */
    GLUE_PYTHON2("GLUE(Python2)", true, "python", ".py"),

    /** Node.js script mode */
    GLUE_NODEJS("GLUE(Nodejs)", true, "node", ".js"),

    /** PowerShell script mode */
    GLUE_POWERSHELL("GLUE(PowerShell)", true, "powershell", ".ps1"),

    /** PHP script mode */
    GLUE_PHP("GLUE(PHP)", true, "php", ".php");

    private final String desc;
    private final boolean isScript;
    private final String cmd;
    private final String suffix;

    GlueTypeEnum(String desc, boolean isScript, String cmd, String suffix) {
        this.desc = desc;
        this.isScript = isScript;
        this.cmd = cmd;
        this.suffix = suffix;
    }

    public String getDesc() {
        return desc;
    }

    public boolean isScript() {
        return isScript;
    }

    public String getCmd() {
        return cmd;
    }

    public String getSuffix() {
        return suffix;
    }

    /**
     * Matches an enum constant by name.
     *
     * @param name the enum constant name to match
     * @return the matched enum constant, or null if not found
     */
    public static GlueTypeEnum match(String name) {
        if (name == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.name().equals(name))
                .findFirst()
                .orElse(null);
    }
}
