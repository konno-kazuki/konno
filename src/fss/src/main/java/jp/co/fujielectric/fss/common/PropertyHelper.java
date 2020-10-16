package jp.co.fujielectric.fss.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

/*
 * プロパティファイル・ヘルパークラス
 */
public class PropertyHelper {
    Properties settings = null;

    // コンストラクタ
    public PropertyHelper(String filePath) {
        try(InputStream inputStream = PropertyHelper.class.getResourceAsStream(filePath)) {
            settings = new Properties();
            settings.load(new InputStreamReader(inputStream, "UTF-8"));     //[V2.2.2]日本語対応
        } catch (IOException e) {
            settings = null;
            System.out.println("setting.properties Read Error!!! : " + e.getMessage());
        }
    }

    public String getProperty(String key) {
        String property = "";
        if (settings != null) property = settings.getProperty(key);
        return property;
    }
}
