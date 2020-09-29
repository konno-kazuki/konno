package jp.co.fujielectric.fss.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/**
 * Json関連ユーティリティ
 */
public class JsonUtil {
    public static String fromObject(Object dataObject) {
        ObjectMapper mapper = new ObjectMapper();
        String jsonString = null;
        try {
            jsonString = mapper.writeValueAsString(dataObject);
        } catch (JsonProcessingException ex) {
            System.err.println(ex.getMessage());
        }
        return jsonString;
    }

    public static <T extends Object> T toObject(String jsonString, Class<T> valueType) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(jsonString, valueType);
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
            return null;
        }
    }
}
