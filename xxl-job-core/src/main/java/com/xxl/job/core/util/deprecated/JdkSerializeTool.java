package com.xxl.job.core.util.deprecated;

import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deprecated Java object serialization utilities.
 *
 * <p>This utility class provided Java object serialization and deserialization using JDK's
 * ObjectOutputStream/ObjectInputStream. This approach has known security vulnerabilities and
 * performance issues.
 *
 * @deprecated This utility is deprecated and will be removed in a future version. Use JSON-based
 *     serialization (Gson, Jackson) for data exchange, or use the xxl-tool library's serialization
 *     utilities if JDK serialization is required. JDK serialization has security risks and should
 *     be avoided for untrusted data.
 * @author xuxueli 2020-04-12 0:14:00
 */
@Deprecated
public class JdkSerializeTool {
    private static Logger logger = LoggerFactory.getLogger(JdkSerializeTool.class);

    // ------------------------ serialize and unserialize ------------------------

    /**
     * Serialize object to byte array (for storage in systems that don't support direct object
     * storage like Redis)
     *
     * @param object
     * @return
     */
    public static byte[] serialize(Object object) {
        ObjectOutputStream oos = null;
        ByteArrayOutputStream baos = null;
        try {
            // serialize
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(object);
            byte[] bytes = baos.toByteArray();
            return bytes;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                oos.close();
                baos.close();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return null;
    }

    /**
     * Deserialize byte array to Object
     *
     * @param bytes
     * @return
     */
    public static <T> Object deserialize(byte[] bytes, Class<T> clazz) {
        ObjectInputStream ois = null;
        ByteArrayInputStream bais = null;
        try {
            // deserialize
            bais = new ByteArrayInputStream(bytes);
            ois = new ObjectInputStream(bais);
            return ois.readObject();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                ois.close();
                bais.close();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return null;
    }
}
