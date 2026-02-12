package com.xxl.job.core.util.deprecated;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

/**
 * Deprecated JSON serialization utilities using Gson.
 *
 * <p>This utility class provided JSON parsing and serialization functionality using Google's Gson
 * library. It included type-safe conversions for collections and custom types.
 *
 * @deprecated This utility is deprecated and will be removed in a future version. Use the xxl-tool
 *     library's GsonTool (com.xxl.tool.gson.GsonTool) instead, which provides the same
 *     functionality with better maintenance and updates. This class was originally copied from
 *     xxl-tool.
 * @author xuxueli 2020-04-11 20:56:31
 */
@Deprecated
public class GsonTool {

    private static Gson gson = null;

    static {
        gson =
                new GsonBuilder()
                        .setDateFormat("yyyy-MM-dd HH:mm:ss")
                        .disableHtmlEscaping()
                        .create();
    }

    /**
     * Convert Object to json
     *
     * <pre>
     *     String json = GsonTool.toJson(new Demo());
     * </pre>
     *
     * @param src
     * @return String
     */
    public static String toJson(Object src) {
        return gson.toJson(src);
    }

    /**
     * Convert json to specific class Object
     *
     * <pre>
     *     Demo demo = GsonTool.fromJson(json, Demo.class);
     * </pre>
     *
     * @param json
     * @param classOfT
     * @return
     */
    public static <T> T fromJson(String json, Class<T> classOfT) {
        return gson.fromJson(json, classOfT);
    }

    /**
     * Convert json to specific Type Object
     *
     * @param json
     * @param typeOfT
     * @return
     * @param <T>
     */
    public static <T> T fromJson(String json, Type typeOfT) {
        return gson.fromJson(json, typeOfT);
    }

    /**
     * Convert json to specific Type Object
     *
     * <pre>
     *     Response<Demo> response = GsonTool.fromJson(json, Response.class, Demo.class);
     * </pre>
     *
     * @param json
     * @param rawType
     * @param typeArguments
     * @return
     */
    public static <T> T fromJson(String json, Type rawType, Type... typeArguments) {
        Type type = TypeToken.getParameterized(rawType, typeArguments).getType();
        return gson.fromJson(json, type);
    }

    /**
     * Convert json to specific class ArrayList
     *
     * <pre>
     *     List<Demo> demoList = GsonTool.fromJsonList(json, Demo.class);
     * </pre>
     *
     * @param json
     * @param classOfT
     * @return
     */
    public static <T> ArrayList<T> fromJsonList(String json, Class<T> classOfT) {
        Type type = TypeToken.getParameterized(ArrayList.class, classOfT).getType();
        return gson.fromJson(json, type);
    }

    /**
     * Convert json to specific class HashMap
     *
     * <pre>
     *     HashMap<String, Demo> map = GsonTool.fromJsonMap(json, String.class, Demo.class);
     * </pre>
     *
     * @param json
     * @param keyClass
     * @param valueClass
     * @return
     * @param <K>
     * @param <V>
     */
    public static <K, V> HashMap<K, V> fromJsonMap(
            String json, Class<K> keyClass, Class<V> valueClass) {
        Type type = TypeToken.getParameterized(HashMap.class, keyClass, valueClass).getType();
        return gson.fromJson(json, type);
    }

    // ---------------------------------

    /**
     * Convert Object to JsonElement
     *
     * @param src
     * @return
     */
    public static JsonElement toJsonElement(Object src) {
        return gson.toJsonTree(src);
    }

    /**
     * Convert JsonElement to specific class Object
     *
     * @param json
     * @param classOfT
     * @return
     * @param <T>
     */
    public static <T> T fromJsonElement(JsonElement json, Class<T> classOfT) {
        return gson.fromJson(json, classOfT);
    }

    /**
     * Convert JsonElement to specific Type Object
     *
     * @param json
     * @param typeOfT
     * @return
     * @param <T>
     */
    public static <T> T fromJsonElement(JsonElement json, Type typeOfT) {
        return gson.fromJson(json, typeOfT);
    }

    /**
     * Convert JsonElement to specific Type Object
     *
     * @param json
     * @param rawType
     * @param typeArguments
     * @return
     * @param <T>
     */
    public static <T> T fromJsonElement(JsonElement json, Type rawType, Type... typeArguments) {
        Type typeOfT = TypeToken.getParameterized(rawType, typeArguments).getType();
        return gson.fromJson(json, typeOfT);
    }
}
