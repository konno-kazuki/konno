package jp.co.fujielectric.fss.util;
import java.io.File;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

/**
 * 検証ユーティリティ
 */
public class VerifyUtil {

    public static final String VERIFY_CPU = "CPU";
    public static final String VERIFY_CPU_PWUNLOCK = "CPU_PwUnlock";
    public static final int UT_STACKTRACE_NUM = 15;

    public static String setLoggingMsg(String key, String... args) {
        //メソッド名
        String methodName;
        String className;
        try {
            StackTraceElement elm = new Throwable().getStackTrace()[1];
            methodName = elm.getMethodName();
            className = elm.getClassName();
            //クラス名からパッケージ名部分を省く
            int index = className.lastIndexOf(".");
            if(index >= 0){
                className = className.substring(index+1);
            }
        } catch (Exception e) {
            methodName = "Unknown";
            className = "Unknown";
        }

        //メッセージ
        String msg = "###[" + key + "]methodName:" + className + "." + methodName + " |";
        for (String str : args) {
            msg += (" " + str);
        }

        return msg;
    }

    //----------------------------------------------------------------------
    //----------------------------------------------------------------------
    // 以下、UT用の実装
    //----------------------------------------------------------------------
    //----------------------------------------------------------------------

    //[UT用]
    /**
     * UT用ログメッセージ生成
     * @param log
     * @param key
     * @param flgOutputStackTrace
     * @param msgFormat
     * @param args
     */
    public static void outputUtLog(Logger log, String key, boolean flgOutputStackTrace, String msgFormat, Object... args) {
        try{
            if(!UT_MODE)
                return;

            if(StringUtils.isBlank(key)){
                key = "#UT#";
            }
            if(IsUTcancel())
                key += "(UTCancel)";    //一時的なキャンセルの場合もログは出力する.　"(UTCancel)"で判別可能とする。

            StackTraceElement[] st = new Throwable().getStackTrace();
            //クラス名、メソッド名
            String className = st[1].getClassName();
            String methodName = st[1].getMethodName();
            //メッセージ
            String msg = String.format(msgFormat, args);
            //StackTrace
            String stackTrace = "";
            if(flgOutputStackTrace){
                int stacknum = 0;
                for(int i=1; i< st.length; i++){
                    String trace = st[i].toString();
                    if(!trace.contains("jp.co.fujielectric.fss"))
                        continue;
                    if(trace.contains("$"))
                        continue;
                    stackTrace += String.format("\nStackTrace[%d]:%s", stacknum, st[i]);
                    if(++stacknum >= UT_STACKTRACE_NUM)
                        break;
                }
            }
            log.debug("##[{}] #method:{}.{} #message:{} ##{}", key, className, methodName, msg, stackTrace);
        }catch(Exception e)
        {
            log.error("##[UT] # UT OutputUtLog Exception:{}", e.getMessage(), e);
        }
    }

    //[UT用]
    /**
     * UTモード
     */
    public static boolean UT_MODE = CommonUtil.getSettingBool("utmode", false);
    private static final String UT_CANCELFILE = "utcancel";
    
    //[UT用]
    /**
     * UT用の設定値を取得（文字列）
     * @param contents  検査対象文字列
     * @param utKey     UT用処理かどうかのUT用判定文字（検査対照文字列に含まれているか）
     * @param argKey    UT用引数文字
     * @param defValue  設定されていない場合の値
     * @return
     * 例　contents="#UT-TEST#LOOP=3#SLEEP=100#", utKey="#UT-TEST#", argKey="LOOP" ⇒ "3"を返す
     */
    public static String getUTArgValue(String contents, String utKey, String argKey, String defValue) {
        try {
            if(!UT_MODE)
                return defValue;
            if(IsUTcancel())
                return defValue;
            if(StringUtils.isBlank(contents) || StringUtils.isBlank(utKey))
                return defValue;

            if(!contents.toUpperCase().contains(utKey.toUpperCase()))
                return defValue;
            String value = defValue;
            //'#'をセパレータとした引数の指定。
            String[] params = contents.split("#");
            for(String param: params){
                //指定した項目名+"="から始まるUT引数値を取得
                if(param.toUpperCase().startsWith(argKey.toUpperCase() + "=")){
                    value = param.substring(argKey.length()+1);
                    break;
                }
            }
            return value;
        }catch(Exception e){
            return defValue;
        }
    }

    //[UT用]
    /**
     * UT用の設定値を取得（数値）
     * @param contents  検査対象文字列
     * @param utKey     UT用処理かどうかのUT用判定文字（検査対照文字列に含まれているか）
     * @param argKey    UT用引数文字
     * @param defValue  設定されていない場合の値
     * @return
     * 例　contents="#UT-TEST#LOOP=3#SLEEP=100#", utKey="#UT-TEST#", argKey="LOOP" ⇒ 3を返す
     */
    public static int getUTArgValueInt(String contents, String utKey,  String argKey, int defValue) {
        int iValue = defValue;
        try {
            iValue = Integer.parseInt(getUTArgValue(contents, utKey, argKey, String.valueOf(defValue)));
        } catch (Exception e) {
        }
        return iValue;
    }

    //[UT用]
    /**
     * UT用のキーが含まれるか
     * @param contents  検査対象文字列
     * @param utKey     UT用処理かどうかのUT用判定文字（検査対照文字列に含まれているか）
     * @param argKey    UT用引数文字
     * @return
     * 例　contents="#UT-TEST#LOOP=3#EXP#", utKey="#UT-TEST#", argKey="EXP" ⇒ trueを返す
     */
    public static boolean chkUTKey(String contents, String utKey, String argKey) {
        try {
            if(!UT_MODE)
                return false;
            if(IsUTcancel())
                return false;
            if(StringUtils.isBlank(contents) || StringUtils.isBlank(utKey))
                return false;
            if(!contents.toUpperCase().contains(utKey.toUpperCase()))
                return false;
            if(StringUtils.isBlank(argKey))
                return true;    //引数文字がEmptyの場合はUT用処理かどうかだけを判定。
            //'#'をセパレータとした引数の指定。
            String[] params = contents.split("#");
            for(String param: params){
                //指定した項目名、または項目名+"="から始まる場合はTRUEを返す
                if(param.toUpperCase().startsWith(argKey.toUpperCase() + "=") || param.equalsIgnoreCase(argKey))
                    return true;
            }
        }catch(Exception e){
        }
        return false;
    }
    
    /**
     * 一時的なテストコードキャンセルの確認
     * @return 
     */
    public static boolean IsUTcancel(){
        //テストキャンセルの確認
        try {
            //tempフォルダにキャンセルファイル（ファイル名で判定）が存在すればTrueを返してキャンセルとする
            return new File(CommonUtil.getFolderTemp(), UT_CANCELFILE).exists();
        } catch (Exception e) {
            return false;
        }
    }
}
