package com.testbird.util.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class JsonTransfer {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonTransfer.class);
    private static final ObjectMapper sObjectMapper = new ObjectMapper();
    private static final TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
    };

    public static ObjectMapper getMapper() {
        return sObjectMapper;
    }

    public static String toJsonString(Object obj) {
        try {
            return sObjectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            LOGGER.error("JsonTransfer error", e);
        }
        return null;
    }

    public static String toJsonFormatString(Object obj) {
        try {
            return sObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            LOGGER.error("JsonTransfer error", e);
        }
        return null;
    }

    private static String transferForJson(String jsonStr) {
        StringBuffer sb = new StringBuffer ();
        for (int i=0; i< jsonStr.length(); i++) {
            char c = jsonStr.charAt(i);
            switch (c) {
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    public static byte[] toJsonBytes(Object obj) {
        try {
            return sObjectMapper.writeValueAsBytes(obj);
        } catch (Exception e) {
            LOGGER.error("JsonTransfer error", e);
        }
        return null;
    }

    public static <T> T toBean(String json, Class<T> objclass) {
        try {
            return sObjectMapper.readValue(json, objclass);
        } catch (Exception e) {
            LOGGER.error("JsonTransfer error", e);
        }
        return null;
    }

    public static <T> T toBean(byte[] json, Class<T> objclass) {
        try {
            return sObjectMapper.readValue(json, objclass);
        } catch (Exception e) {
            LOGGER.error("JsonTransfer error", e);
        }
        return null;
    }

    public static Map<String, Object> toMap(String json) {
        Map<String, Object> map = new HashMap<>();
        try {
            map = sObjectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            LOGGER.error("JsonTransfer ignore: {}", json);
        }
        return map;
    }

    public static boolean toJsonFile(File resultFile, Object obj) {
        try {
            sObjectMapper.writerWithDefaultPrettyPrinter().writeValue(resultFile, obj);
            return true;
        } catch (Exception e) {
            LOGGER.error("JsonTransfer error", e);
        }
        return false;
    }
}
