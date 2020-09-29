package jp.co.fujielectric.fss.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import jp.co.fujielectric.fss.common.PropertyHelper;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.entity.MailQueue;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.entity.UploadGroupInfo;
import jp.co.fujielectric.fss.logic.ItemHelper;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

/**
 * 共通ユーティリティ
 */
public class CommonUtil {

    // プロパティファイル・ヘルパークラス
    private static final PropertyHelper settingProperties;

    // コンストラクタ
    static {
        settingProperties = new PropertyHelper("/setting.properties");
    }

    // 設定ファイルの読み込み
    public static String getSetting(String key) {
        return settingProperties.getProperty(key);
    }

    /**
     * 設置値を取得し数値型で返す
     * @param key
     * @return 
     */
    public static int getSettingInt(String key) {
        String strVal = getSetting(key);
        return Integer.parseInt(strVal);
    }

    /**
     * 設置値を取得し数値型で返す(デフォルト値あり）
     * @param key キー
     * @param defaultValue 取得出来なかった場合のデフォルト値
     * @return 
     */
    public static int getSettingInt(String key, int defaultValue) {
        try {
            String strVal = getSetting(key);
            if(strVal != null)
                return Integer.parseInt(strVal);
            return defaultValue;
        } catch (Exception e) {
            //取得できなかった場合は指定されたデフォルト値を返す
            return defaultValue;
        }
    }
    
    /**
     * 設置値を取得しDouble型で返す(デフォルト値あり）
     * @param key キー
     * @param defaultValue 取得出来なかった場合のデフォルト値
     * @return 
     */
    public static double getSettingDouble(String key, double defaultValue) {
        try {
            String strVal = getSetting(key);
            if(strVal != null)
                return Double.parseDouble(strVal);
            return defaultValue;
        } catch (Exception e) {
            //取得できなかった場合は指定されたデフォルト値を返す
            return defaultValue;
        }
    }
    
    /**
     * 設置値を取得しboolean型で返す(デフォルト値あり）
     * @param key キー
     * @param defaultValue 取得出来なかった場合のデフォルト値
     * @return 
     */
    public static boolean getSettingBool(String key, boolean defaultValue) {
        try {
            String strVal = getSetting(key);
            if(strVal != null)
                return Boolean.valueOf(strVal);
            return defaultValue;
        } catch (Exception e) {
            //取得できなかった場合は指定されたデフォルト値を返す
            return defaultValue;
        }
    }    
    
    // ＡＰサーバ設置場所の判定
    // ture: LGWAN側, false: Internet側
    public static boolean isSectionLgwan() {
        switch (settingProperties.getProperty("section")) {
            case "lgwan":
                return true;
//            case "internet":
            default:
                return false;
        }
    }

    // Base64エンコード
    public static String encodeBase64(String src) {
        return Base64.encodeBase64URLSafeString(src.getBytes());
    }

    // Base64デコード
    public static String decodeBase64(String src) {
        byte[] decoded = Base64.decodeBase64(src);
        return new String(decoded);
    }

    /**
     * ローカルＵＲＬ文字列を生成
     *
     * @param regionId
     * @param isLgwanFlg
     * @return ＵＲＬ文字列
     */
    public static String createLocalUrl(String regionId, boolean isLgwanFlg) {
        return createLocalUrl(regionId, isLgwanFlg, true);
    }

    /**
     * ローカルＵＲＬ文字列を生成(RegionIdをBase64エンコードしない)
     *
     * @param regionId
     * @param isLgwanFlg
     * @param flgEncording regionIdをBase64エンコードするかどうか
     * @return ＵＲＬ文字列
     */
    public static String createLocalUrl(String regionId, boolean isLgwanFlg, boolean flgEncording) {
        StringBuilder sb = new StringBuilder();
        sb.append("http://");
        if (isLgwanFlg) {
            sb.append(getSetting("hostlgw"));
            sb.append("/");
        } else {
            sb.append(getSetting("hostint"));
            sb.append("/");
        }
        sb.append(getSetting("contextpath"));
        sb.append("/");
        if (flgEncording) {
            sb.append(encodeBase64(regionId));      // regionIdはBase64エンコード
        } else {
            sb.append(regionId);      // regionIdはBase64エンコード
        }
        sb.append("/");
        return sb.toString();
    }

    /**
     * ルートＵＲＬ文字列を生成
     *
     * @param regionId
     * @param isLgwanFlg
     * @return ＵＲＬ文字列
     */
    public static String createRootUrl(String regionId, boolean isLgwanFlg) {
        StringBuilder sb = new StringBuilder();
        if (isLgwanFlg) {
            sb.append(getSetting("domainlgw"));
            sb.append("/");
        } else {
            sb.append(getSetting("domainint"));
            sb.append("/");
        }
        sb.append(getSetting("contextpath"));
        sb.append("/");
        sb.append(encodeBase64(regionId));      // regionIdはBase64エンコード
        sb.append("/");
        return sb.toString();
    }

    /**
     * ワンタイムＵＲＬ文字列を生成
     *
     * @param regionId
     * @param onetimeId
     * @param isLgwanFlg
     * @return ＵＲＬ文字列
     */
    public static String createOnetimeUrl(String regionId, String onetimeId, boolean isLgwanFlg) {
        StringBuilder sb = new StringBuilder(createRootUrl(regionId, isLgwanFlg));
        sb.append(encodeBase64(onetimeId));
        sb.append("/");
        return sb.toString();
    }

    /**
     * サーバ間通信用ＵＲＬ文字列を生成
     *
     * @param regionId
     * @param isLgwanFlg
     * @return ＵＲＬ文字列
     */
    public static String createWebSocketUrl(String regionId, boolean isLgwanFlg) {
        String url = createLocalUrl(regionId, isLgwanFlg);  // 相手ＡＰ側のＵＲＬ
        url = url.replace("https:", "ws:");
        url = url.replace("http:", "ws:");

        StringBuilder sb = new StringBuilder(url);
        sb.append("/ws/syncfile/");                         // endPointは固定
        sb.append("/");
        return sb.toString();
    }

    /**
     * Votiro連携用ＵＲＬ文字列を生成
     *
     * @return ＵＲＬ文字列
     */
    public static String createVotiroUrl() {
        StringBuilder sb = new StringBuilder();
        sb.append(getSetting("votiro"));
        return sb.toString();
    }

    /**
     * パスワード文字数Max
     *
     * @param itemHelper ItemHelperクラスのインスタンス
     * @param funcId 機能ID
     * @return パスワード文字数Maxを返す
     */
    public static long getPasswordCharMax(ItemHelper itemHelper, String funcId) {
        //パスワード文字数Max
        Item item = itemHelper.find(Item.PASSWORD_CHAR_MAX, funcId);
        Long passwordCharMax = Long.parseLong(item.getValue());

        return passwordCharMax;
    }

    /**
     * パスワードの禁則文字使用を判定
     *
     * @param password パスワード
     * @return 禁則文字を使用している場合はfalseを返す
     */
    public static boolean isValidPassword(String password) {
        return password.matches("^[a-zA-Z0-9 -/:-@\\[-\\`\\{-\\~]+$");
    }

    /**
     * パスワードの文字数Minを判定
     *
     * @param password パスワード
     * @param passwordCharMin パスワードの文字数Min
     * @return パスワードの文字数Minより短い場合はtrueを返す
     */
    public static boolean isPasswordCharMin(String password, long passwordCharMin) {
        return password.length() < passwordCharMin;
    }

    /**
     * 文字数Max判定
     *
     * @param str 文字列
     * @param charMax 文字数Max
     * @return
     */
    public static boolean isCharMax(String str, long charMax) {
        return str.length() <= charMax;
    }

    /**
     * メールアドレスのチェック
     *
     * @param mailAddress
     * @return OK/NG
     */
    public static boolean isValidEmail(String mailAddress) {

        try {
            // メールアドレスがRFC822に準拠しているかどうかのチェック
//            InternetAddress check = new InternetAddress(mailAddress, true);
//            return check.getAddress().equals(mailAddress);
            InternetAddress check = new InternetAddress();
            check.setAddress(mailAddress);
            check.validate();
        } catch (AddressException ex) {
            return false;
        }
        return true;

//        String mailFormat = "^[a-zA-Z0-9!#$%&'_`/=~\\*\\+\\-\\?\\^\\{\\|\\}]+(\\.[a-zA-Z0-9!#$%&'_`/=~\\*\\+\\-\\?\\^\\{\\|\\}]+)*+(.*)@[a-zA-Z0-9][a-zA-Z0-9\\-]*(\\.[a-zA-Z0-9\\-]+)+$";
//        if (!mailAddress.matches(mailFormat)) {
//            return false;
//        }

        //以下のアドレスもはじきたい
        //---abc.xyz.netのように@がないメールアドレス
        //---@が二つ以上あるアドレス（abc@xyz@stu.ne.jp）
        //---abc@xyz..ne.jpのようにドメイン名にドットが連続するアドレス
        //--------------------
        //  \wはaからz，AからZ，0から9の数字，そしてアンダースコア(_)に一致するメタ文字です。
        //  ドット(.）は任意の一文字に一致するメタ文字ですが，ここでは\でエスケープしてドットそのものと一致するようにしています。
        //  ハイフン（-）もメールアドレスに使う文字です。
        //  [・・・]は[ ]の中のいずれかの文字にマッチを意味するので，@の前のユーザー名を構成する文字としてaからz，AからZ，0から9の数字，アンダースコア(_)，ドット(.)，ハイフン(-)が使えることになります。
        //  メタ文字「+」の前の文字が1個以上繰り返すことを意味します。
        //  前回説明したアスタリスク(*)は0個以上の繰り返しだったので省略が可能でしたが，メールアドレスのユーザー名を省略されては困るので，+を使います。
        //  最低でも1文字のユーザー名は必要なことになるのです。
        //  その後，アットマーク(@)がひとつ続き，(?:・・・・)で[\\w\\-]+\\.がグループ化されます。
        //  グループに+が続くので「xyz.」のようなグループが1個以上続くことを表現できます。
        //  abc@xyz.stu.ne.jpやabc@xyz.stu.vw.ne.jpにも対応できるのです。
        //--------------------
//        String ptnStr = "[\\w\\.\\-]+@(?:[\\w\\-]+\\.)+[\\w\\-]+";
//        java.util.regex.Pattern ptn = java.util.regex.Pattern.compile(ptnStr);
//        java.util.regex.Matcher mc = ptn.matcher(mailAddress);
//        if (mc.matches()) {
//        } else {
//            return false;
//        }

//        return true;
    }
    
    //[v2.2.1]
    /**
     * フォルダ区分
     */
    public static enum FolderKbn {
        /**
         * メールフォルダ
         */
        MAIL("maildir", true, true, false),
        /**
         * 送信フォルダ（NFS)
         */
        SEND("senddir", false, true, true),
        /**
         * パスワード解除フォルダ（NFS)
         */
        DECRYPT("decryptdir", false, true, true),
        /**
         * 受信フォルダ（NFS)
         */
        RECEIVE("receivedir", false, true, true),
        /**
         * 一時フォルダ（NFS)
         */
        TEMP("tempdir", false, false, true),
        /**
         * チケットフォルダ
         */
        TICKET("ticketDir", true, false, false),
        /**
         * ログフォルダ
         */
        LOG("logDir", false, false, false),
        /**
         * 送信フォルダ（Local)
         */
        LOCAL_SEND("local_senddir", false, false, true),
        /**
         * パスワード解除フォルダ（Local)
         */
        LOCAL_DECRYPT("local_decryptdir", false, false, true),
        /**
         * 受信フォルダ（Local）
         */
        LOCAL_RECEIVE("local_receivedir", false, false, true),
        /**
         * SandBlastフォルダ
         */
        SANDBLAST("sandblastdir", false, true, true),
        /**
         * Votiro受信フォルダ
         */
        VOTIRO("votirodir", false, true, true),
        /**
         * Votiroレポート情報フォルダ
         */
        VOTIRO_REPORT("votiro_reportdir", false, true, true);
  
        // フィールドの定義
        
        /**
         * setting.properties の項目名
         */
        public final String key;
        /**
         * サブフォルダを使用するか (例：mail,ticket等）
         */
        public final boolean flgUseSubFolder;
        /**
         * 日付サブフォルダを使用するか (例：mail,send,receive,等）
         */
        public final boolean flgUseDateFolder;
        /**
         * IDサブフォルダを使用するか (例：mail,send,receive,等）
         */
        public final boolean flgUseIdFolder;

        /**
         * コンストラクタ
         * @param key setting.propertiesの項目名
         * @param flgUseSubFolder   サブフォルダを使用するか
         * @param flgUseDateFolder  日付サブフォルダを使用するか
         * @param flgUseIdFolder IDサブフォルダを使用するか
         */
        private FolderKbn(String key, boolean flgUseSubFolder, boolean flgUseDateFolder, boolean flgUseIdFolder) {
            this.key = key;
            this.flgUseSubFolder = flgUseSubFolder;
            this.flgUseDateFolder = flgUseDateFolder;
            this.flgUseIdFolder = flgUseIdFolder;
        }
    }
    
    /**
     * 各フォルダ区分の設定値を取得
     * @param kbn
     * @return 
     */
    public static String getFolderSetting(FolderKbn kbn)
    {
        //setting.propertiesから取得
        String folder = CommonUtil.getSetting(kbn.key);
        return folder;
    }

    /**
     * フォルダパス取得
     * @param kbn       フォルダ区分
     * @param subDir    サブフォルダ文字列（サーブレットコード等）
     * @param dateDir   日付サブフォルダ文字列
     * @param idDir     IDサブフォルダ文字列
     * @return 
     */
    private static String getFolder(FolderKbn kbn, String subDir, String dateDir, String idDir){
        Path p = Paths.get(getFolderSetting(kbn));
        //サブフォルダ１（サーブレットコート等）
        if(kbn.flgUseSubFolder && !StringUtils.isEmpty(subDir)){
            p = Paths.get(p.toString(), subDir);
        }
        //サブフォルダ2（日付）
        if(kbn.flgUseDateFolder && !StringUtils.isEmpty(dateDir)){
            p = Paths.get(p.toString(), dateDir);
        }
        //サブフォルダ3（ID）
        if(kbn.flgUseIdFolder && !StringUtils.isEmpty(idDir)){
            p = Paths.get(p.toString(), idDir);
        }
        return p.toString();
    }

    /**
     * メールフォルダ（原本メール用)取得 (SendInfoから）
     * @param entity
     * @return 
     */
    public static String getFolderMail(SendInfo entity){
        //mailフォルダのサブフォルダ(servletCode)として"mailentrance"を指定
        return getFolder(FolderKbn.MAIL, MailQueue.SERVLET_CODE_MAILENTRANCE, entity.getProcDate(), null);
    }      

    /**
     * メールフォルダ（原本メール用)取得 (MailQueueから）
     * @param entity
     * @return 
     */
    public static String getFolderMail(MailQueue entity){
        //mailフォルダのサブフォルダ(servletCode)として"mailentrance"を指定
        return getFolder(FolderKbn.MAIL, MailQueue.SERVLET_CODE_MAILENTRANCE, entity.getMailDate(), null);
    }      

    /**
     * メールフォルダ（原本メール用)取得
     * @param mailDate
     * @return 
     */
    public static String getFolderMail(String mailDate){
        //mailフォルダのサブフォルダ(servletCode)として"mailentrance"を指定
        return getFolder(FolderKbn.MAIL, MailQueue.SERVLET_CODE_MAILENTRANCE, mailDate, null);
    }      

    /**
     * メールフォルダ（SandBlastリターンメール用)取得
     * @param mailDate
     * @return 
     */
    public static String getFolderMailSandBlast(String mailDate){
        //mailフォルダのサブフォルダ(servletCode)として"mailentrance"を指定
        return getFolder(FolderKbn.MAIL, MailQueue.SERVLET_CODE_VOTIROENTRANCE, mailDate, null);
    }      
    
    /**
     * 送信フォルダ（NFS/Local)取得 (SendInfoから)
     * @param entity
     * @param flgLocalTmp   Localかどうか(true:local_senddir / false: senddir)
     * @param isMailFile    Mailファイル本体かどうか(true:メールファイル / false:添付ファイル・送信ファイル)
     * @return 
     */
    public static String getFolderSend(SendInfo entity, boolean flgLocalTmp, boolean isMailFile){
        FolderKbn kbn = (flgLocalTmp ? FolderKbn.LOCAL_SEND : FolderKbn.SEND);
        String id = (isMailFile ? "" : entity.getId());
        return getFolder(kbn, null, entity.getProcDate(), id);
    }    

    /**
     * 送信フォルダ（NFS)取得 (UploadGroupInfoから）
     * @param entity
     * @param isMailFile    Mailファイル本体かどうか(true:メールファイル / false:添付ファイル・送信ファイル)
     * @return 
     */
    public static String getFolderSend(UploadGroupInfo entity, boolean isMailFile){
        String id = (isMailFile ? "" : entity.getSendInfoId());
        return getFolder(FolderKbn.SEND, null, entity.getProcDate(), id);
    }      
    
    /**
     * 受信フォルダ（NFS/Local)取得
     * @param entity
     * @param flgLocalTmp   Localかどうか(true:local_senddir / false: senddir)
     * @param isMailFile    Mailファイル本体かどうか(true:メールファイル / false:添付ファイル・送信ファイル)
     * @return 
     */
    public static String getFolderReceive(ReceiveInfo entity, boolean flgLocalTmp, boolean isMailFile){
        String procDate = null;
        FolderKbn kbn = (flgLocalTmp ? FolderKbn.LOCAL_RECEIVE : FolderKbn.RECEIVE);
        if( kbn.flgUseDateFolder){
            if(entity.getSendInfo() == null){
                throw new IllegalArgumentException("指定されたReceiveInfoに対してgetSendInfoがnullを返します。");
            }
            procDate = entity.getSendInfo().getProcDate();
        }
        String id = (isMailFile ? "" : entity.getId());     //メール本体の場合はIDサブフォルダを使用しない
        return getFolder(kbn, null, procDate, id);
    }    
    
    /**
     * パスワード解除フォルダ（NFS/Local)取得 (ReceiveInfoから）
     * @param entity
     * @param flgLocalTmp   Localかどうか(true:local_decryptdir / false: decryptdir)
     * @return 
     */
    public static String getFolderDecrypt(ReceiveInfo entity, boolean flgLocalTmp){
        String procDate = null;
        FolderKbn kbn = flgLocalTmp ? FolderKbn.LOCAL_DECRYPT : FolderKbn.DECRYPT;
        if( kbn.flgUseDateFolder){
            if(entity.getSendInfo() == null){
                throw new IllegalArgumentException("指定されたReceiveInfoに対してgetSendInfoがnullを返します。");
            }
            procDate = entity.getSendInfo().getProcDate();
        }
        return getFolder(kbn, null, procDate, entity.getId());
    }

    /**
     * パスワード解除フォルダ（NFS)取得 (UploadGroupInfoから）
     * @param entity
     * @return 
     */
    public static String getFolderDecrypt(UploadGroupInfo entity){
        return getFolder(FolderKbn.DECRYPT, null, entity.getProcDate(), entity.getMainId());
    }

    /**
     * Votiroフォルダ)取得 (UploadGroupInfoから）
     * @param entity
     * @return 
     */
    public static String getFolderVotiro(UploadGroupInfo entity){
        return getFolder(FolderKbn.VOTIRO, null, entity.getProcDate(), entity.getMainId());
    }

    /**
     * Votiroレポート情報フォルダ取得 (UploadGroupInfoから）
     * @param entity
     * @return
     */
    public static String getFolderVotiroReport(UploadGroupInfo entity){
        return getFolder(FolderKbn.VOTIRO_REPORT, null, entity.getProcDate(), entity.getMainId());
    }

    /**
     * SandBlast受信フォルダ（NFS)取得 (UploadGroupInfoから）
     * @param entity
     * @return 
     */
    public static String getFolderSandBlast(UploadGroupInfo entity){
        return getFolder(FolderKbn.SANDBLAST, null, entity.getProcDate(), entity.getMainId());
    }
    
    /**
     * テンポラリフォルダ取得
     * @return 
     */
    public static String getFolderTemp(){
        return getFolder(FolderKbn.TEMP, null, null, null);
    }

    /**
     * チケットフォルダ取得
     * @param ticketServletCode 対象サーブレットコード（原本メール：mailentrance, SandBlastリターンメール:votiroentrance)
     * @return 
     */
    public static String getFolderTicket(String ticketServletCode){
        return getFolder(FolderKbn.TICKET, ticketServletCode, null, null);
    }        
}
