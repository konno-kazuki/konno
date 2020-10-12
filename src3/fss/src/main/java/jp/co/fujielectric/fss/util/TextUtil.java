package jp.co.fujielectric.fss.util;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import javax.mail.internet.InternetAddress;
import org.apache.commons.lang3.StringUtils;

/**
 * テキストユーティリティ
 */
public class TextUtil {

    private static final String CHARSET_csWindows31J = "csWindows31J";

    //デフォルト結合文字
    public static final String DEFAULT_CONCATSTR = " ";

    /**
     * 表示用アドレステキスト作成
     *
     * @param address
     * @return 表示用アドレステキスト
     */
    public static String createAddressText(String address) {
        return createAddressText(address, "", DEFAULT_CONCATSTR);
    }

    /**
     * 表示用アドレステキスト作成
     *
     * @param address       アドレス
     * @param personal      名前
     *
     * @return 表示用アドレステキスト
     */
    public static String createAddressText(String address, String personal) {
        return createAddressText(address, personal, DEFAULT_CONCATSTR);
    }

    /**
     * 表示用アドレステキスト作成
     *
     * @param ia            InternetAddress
     *
     * @return 表示用アドレステキスト
     */
    public static String createAddressText(InternetAddress ia) {
        return createAddressText(ia.getAddress(), ia.getPersonal(), DEFAULT_CONCATSTR);
    }

    /**
     * 表示用アドレステキスト作成
     *
     * @param address       アドレス
     * @param personal      名前
     * @param concatStr     結合文字列
     *
     * @return 表示用アドレステキスト
     */
    public static String createAddressText(String address, String personal, String concatStr) {

        String addressText;
        if (StringUtils.isEmpty(personal)) {
            addressText = address;
        }
        else {
            addressText = personal + concatStr + "<" + address + ">";
//            try {
//                InternetAddress ia = new InternetAddress(address, personal);
//                addressText = ia.toUnicodeString();
//            } catch (Exception ex) {
//                //addressText = "\"" + personal + "\"" + concatStr + "<" + address + ">";
//                addressText = address;
//            }
        }

        return addressText;
    }

    /**
     * 表示用日時テキスト作成
     *
     * @param dt
     * @return 表示用アドレステキスト
     */
    public static String createDateText(java.util.Date dt) {
        if (dt == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy/MM/dd[HH:mm:ss]").format(dt);
    }

    /**
     * 文字列が Windows-31J に適合するかの確認
     * @param inStr 検査対象文字列
     * @return Windows-31J に適合する場合は true、適合しない場合は false
     */
    public static boolean isValidWin31J(String inStr) {
        // 入力チェック
        if (inStr == null || "".equals(inStr)) {
                return true;
        }

        // Windows-31J のバイト配列に変換し、逆変換
        try {
            String reConverted = new String(inStr.getBytes(CHARSET_csWindows31J), CHARSET_csWindows31J);

            // 文字コード比較結果を返す
            return isValidCharCode(inStr, reConverted);
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    /**
     * 文字コード内容の一致チェック<br>
     * ・文字チェック用メソッドで使用
     * @param inStr 文字列１
     * @param reConvertedString 文字列２
     * @return 一致する場合は true、一致しない場合は false
     */
    private static boolean isValidCharCode(String inStr, String reConvertedString) {
        // 入力チェック
        if (inStr == null || "".equals(inStr)) {
                return true;
        }
        if (reConvertedString == null || "".equals(reConvertedString)) {
                return true;
        }

        // 一致チェック
        boolean result = true;
        int len = inStr.length();
        int j = 0;

        for (int i = 0; i < len; i++, j++) {
                if (inStr.codePointAt(i) != reConvertedString.codePointAt(j)) {
                        if (Character.charCount(inStr.codePointAt(i)) == 2) {
                                result = false;
                                i++;

                        } else {
                                result = false;
                        }
                } else {
                }
        }

        return result;
    }
}
