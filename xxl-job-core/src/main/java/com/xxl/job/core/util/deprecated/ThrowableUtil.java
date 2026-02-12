package com.xxl.job.core.util.deprecated;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Deprecated exception stack trace formatting utilities.
 *
 * <p>This utility class provided a simple method to convert exception stack traces into string
 * format for logging or display purposes.
 *
 * @deprecated This utility is deprecated and will be removed in a future version. Use the xxl-tool
 *     library's exception utilities (com.xxl.tool.exception.ExceptionUtil) instead, or use Apache
 *     Commons Lang3 ExceptionUtils.getStackTrace() for more comprehensive exception handling.
 * @author xuxueli 2018-10-20 20:07:26
 */
@Deprecated
public class ThrowableUtil {

    /**
     * parse error to string
     *
     * @param e
     * @return
     */
    public static String toString(Throwable e) {
        StringWriter stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        String errorMsg = stringWriter.toString();
        return errorMsg;
    }
}
