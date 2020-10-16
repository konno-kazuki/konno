package jp.co.fujielectric.fss.util;

import com.fasterxml.uuid.Generators;

/**
 * ＩＤユーティリティ
 */
public class IdUtil {
    public static String createUUID() {
        return Generators.timeBasedGenerator().generate().toString();
    }
}
