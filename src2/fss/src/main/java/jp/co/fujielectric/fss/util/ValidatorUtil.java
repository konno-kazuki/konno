package jp.co.fujielectric.fss.util;

import org.apache.commons.lang3.StringUtils;

/**
 * 検証ユーティリティ
 */
public class ValidatorUtil {
    private static final String[] escapeList = {"*","+",".","?","{","}","(",")","[","]","^","$","-","|","/"};

    /**
     * 正規表現のエスケープ文字変換<br>
     * ※エスケープ済みが含まれる場合はＮＧ！！！
     *
     * @param regex
     * @return OK/NG
     */
    public static String convertRegex(String regex) {
        for (String escape : escapeList) {
            regex = regex.replace(escape, "\\" + escape);
        }
        return regex;
    }

    /**
     * 禁則文字チェック［正規表現指定］
     *
     * @param target
     * @param regex
     * @return OK/NG
     */
    public static boolean isValidRegex(String target, String regex) {
        if (StringUtils.isEmpty(target)) return true;
        if (StringUtils.isEmpty(regex)) return true;
        return target.matches(regex);
    }

    /**
     * 禁則文字チェック［正規表現＋除外文字群指定］
     *
     * @param target
     * @param regex
     * @param exclude
     * @return OK/NG
     */
    public static boolean isValidRegex(String target, String regex, String exclude) {
        if (StringUtils.isEmpty(target)) return true;
        if (isValidRegex(target, regex)) {
            if (StringUtils.isEmpty(exclude)) return true;
            return target.matches("[^" + convertRegex(exclude) + "]+$");
        } else {
            return false;
        }
    }

    /**
     * 禁則文字チェック［JisX0208］
     *
     * @param target
     * @return OK/NG
     */
    public static boolean isValidJisX2080(String target) {
        boolean rc = false;
        String sjis;
        try {
            sjis = new String(target.getBytes("SJIS"), "SJIS");
            rc = target.equals(sjis);
        } catch (Exception ex) {
            rc = false;
        }
        return rc;

        // SJISのコード順として定義することはできなかったため正規表現は一旦保留
// ・01～08区：記号、英数字、かな
// ・16～47区：JIS第1水準漢字
// ・48～84区：JIS第2水準漢字
// ※「凜[7425]」「熙[7426]」はJIS X 0208-1990で追加された
//        return userName.matches("^[　-〓∈-∩∧-∃∠-∬Å-¶◯-◯０-９Ａ-Ｚａ-ｚぁ-んァ-ヶΑ-Ωα-ωА-Яа-я─-╂亜-腕弌-滌漾-瑤凜-熙a-zA-Z0-9｡-ﾟ !-/:-@\\[-\\`\\{-\\~]+$");
    }
}
