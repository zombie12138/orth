package com.abyss.orth.core.glue;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.abyss.orth.core.glue.impl.SpringGlueFactory;
import com.abyss.orth.core.handler.IJobHandler;

import groovy.lang.GroovyClassLoader;

/**
 * GLUE (Groovy-based Live Update Environment) factory for dynamic job handler instantiation.
 *
 * <p>This factory compiles and caches Groovy source code into Java classes at runtime, enabling hot
 * deployment of job logic without restarting the executor.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Dynamic compilation of Groovy source code to Java classes
 *   <li>MD5-based class caching to avoid redundant compilation
 *   <li>Dependency injection support (Spring or frameless)
 *   <li>Prototype scope: each call to {@link #loadNewInstance} creates a new instance
 * </ul>
 *
 * <p>Factory types:
 *
 * <ul>
 *   <li>Type 0: Frameless factory (no dependency injection)
 *   <li>Type 1: Spring factory (injects Spring beans into GLUE instances)
 * </ul>
 *
 * @author xuxueli 2016-1-2 20:02:27
 * @see SpringGlueFactory
 */
public class GlueFactory {

    /** Singleton instance, type determined by {@link #refreshInstance(int)} */
    private static GlueFactory glueFactory = new GlueFactory();

    /**
     * Gets the current factory instance.
     *
     * @return singleton factory instance
     */
    public static GlueFactory getInstance() {
        return glueFactory;
    }

    /**
     * Refreshes the factory instance by type.
     *
     * <p>This method is called during executor initialization to set the appropriate factory type
     * based on the environment (Spring or frameless).
     *
     * @param type factory type (0=frameless, 1=Spring)
     */
    public static void refreshInstance(int type) {
        if (type == 0) {
            glueFactory = new GlueFactory();
        } else if (type == 1) {
            glueFactory = new SpringGlueFactory();
        }
    }

    /** Groovy class loader for dynamic code compilation */
    private final GroovyClassLoader groovyClassLoader = new GroovyClassLoader();

    /** MD5-keyed cache to avoid recompiling identical source code */
    private final ConcurrentMap<String, Class<?>> classCache = new ConcurrentHashMap<>();

    /**
     * Compiles Groovy source code and creates a new job handler instance.
     *
     * <p>Process:
     *
     * <ol>
     *   <li>Computes MD5 hash of source code
     *   <li>Checks cache for existing compiled class
     *   <li>Compiles source if not cached (cache miss)
     *   <li>Instantiates new object from class
     *   <li>Injects dependencies (if Spring factory)
     *   <li>Casts to {@link IJobHandler} and returns
     * </ol>
     *
     * @param codeSource Groovy source code string
     * @return new job handler instance
     * @throws IllegalArgumentException if source is null/empty, compilation fails, or instance is
     *     not an {@link IJobHandler}
     */
    public IJobHandler loadNewInstance(String codeSource) {
        if (codeSource == null || codeSource.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    ">>>>>>>>>>> orth-glue, loadNewInstance error, codeSource is null or empty");
        }

        Class<?> clazz = getCodeSourceClass(codeSource);
        if (clazz == null) {
            throw new IllegalArgumentException(
                    ">>>>>>>>>>> orth-glue, loadNewInstance error, failed to compile class");
        }

        Object instance;
        try {
            instance = clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    ">>>>>>>>>>> orth-glue, loadNewInstance error, failed to instantiate class", e);
        }

        if (!(instance instanceof IJobHandler)) {
            throw new IllegalArgumentException(
                    ">>>>>>>>>>> orth-glue, loadNewInstance error, cannot convert from instance["
                            + instance.getClass()
                            + "] to IJobHandler");
        }

        // Inject dependencies (Spring beans, etc.)
        this.injectService(instance);

        return (IJobHandler) instance;
    }

    /**
     * Compiles Groovy source code to a Java class with MD5-based caching.
     *
     * <p>The MD5 hash of the source code is used as the cache key. If the same source code is
     * compiled again, the cached class is returned to avoid redundant compilation.
     *
     * @param codeSource Groovy source code
     * @return compiled class or null if compilation fails
     */
    private Class<?> getCodeSourceClass(String codeSource) {
        try {
            // Compute MD5 hash of source code for cache key
            byte[] md5 = MessageDigest.getInstance("MD5").digest(codeSource.getBytes());
            String md5Str = new BigInteger(1, md5).toString(16);

            // Check cache first
            Class<?> clazz = classCache.get(md5Str);
            if (clazz == null) {
                // Cache miss: compile and cache
                clazz = groovyClassLoader.parseClass(codeSource);
                classCache.putIfAbsent(md5Str, clazz);
            }
            return clazz;
        } catch (Exception e) {
            // Fallback: attempt direct compilation without caching
            return groovyClassLoader.parseClass(codeSource);
        }
    }

    /**
     * Injects dependencies into a GLUE job handler instance.
     *
     * <p>Default implementation does nothing. Override in subclasses (e.g., {@link
     * SpringGlueFactory}) to provide dependency injection.
     *
     * @param instance GLUE job handler instance
     */
    public void injectService(Object instance) {
        // No-op in base factory; subclasses override for DI
    }
}
