package jp.co.fujielectric.fss.logic;

import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.entity.Config;
import jp.co.fujielectric.fss.entity.Define;
import jp.co.fujielectric.fss.entity.DefineImage;
import jp.co.fujielectric.fss.entity.DispMessage;
import jp.co.fujielectric.fss.entity.MailMessage;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

/**
 * Itemサービス
 */
@AppTrace
@ApplicationScoped
public class ItemHelper {
    @Inject
    private Logger LOG;

    @Inject
    private EntityManager em;

    @Inject
    private MailManager mailManager;

    @Inject
    private  ItemHelperOtherDomain itemHelperOtherDomain;
    
    //[248対応（簡易版）]
    /**
     * 団体区分（config,mailMessageで使用）
     */
    public enum OrganizationKbn {
        /**
         * 自団体
         */
        Inner(1),
        /**
         * 他団体(LG内)
         */
        OuterLgwan(2),
        /**
         * 外部事業者
         */
        Outer(3),
        /**
         * 共通
         */
        Common(0);
    
        // フィールドの定義
        public int kbn;

        // コンストラクタの定義
        private OrganizationKbn(int kbn) {
            this.kbn = kbn;
        }
    }
    
//    TODO: リージョンID毎の制御が出来ていないため、コメントアウト(自治体毎の文言を設定出来ない)
//    /**
//     * 取得した画面メッセージアイテムを保持する二重HashMap HashMap<itemKey, HashMap<funcId, itemValue>>
//     */
//    private final HashMap<String, HashMap<String, String>> dispMessageMap = new HashMap<>();

    /**
     * 汎用アイテム取得
     *
     * @param keyItem アイテム定数
     * @param funcId 機能ID
     * @return
     */
    public Item find(Item keyItem, String funcId) {
        switch (keyItem.getValue()) {
            case Item.DEFINE:
                return findDefine(keyItem.getKey());
            case Item.CONFIG:
                return findConfig(keyItem.getKey(), funcId);
            case Item.DISP_MESSAGE:
                return findDispMessage(keyItem.getKey(), funcId);
            case Item.MAIL_MESSAGE:
                return findMailMessage(keyItem.getKey(), funcId);
        }
        return null;
    }
 
    /**
     * 汎用アイテム取得（取得できなかった場合は指定された既定値を返す）
     * @param keyItem
     * @param funcId
     * @param defaultValue
     * @return 
     */
    public String findWithDefault(Item keyItem, String funcId, String defaultValue)
    {
        try {
            if(StringUtils.isBlank(funcId)){
                //funcIdが未指定（null/空文字）の場合、「0」とみなす
                funcId = Item.FUNC_COMMON;
            }
            return find(keyItem, funcId).getValue();            
        } catch (Exception e) {
            //取得できなかった場合は指定された既定値を返す
            LOG.warn("#!-- 指定されたアイテムをマスタから取得することができませんでした。[Type:{}, Key:{}, FuncId:{}, DefaultValue:{}", 
                    keyItem.getValue(), keyItem.getKey(), funcId, defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * 設定値を数値として取得（取得できなかった場合は指定された既定値を返す）
     * @param keyItem
     * @param funcId
     * @param defaultValue
     * @return 
     */
    public int findIntWithDefault(Item keyItem, String funcId, int defaultValue)
    {
        try {
            if(StringUtils.isBlank(funcId)){
                //funcIdが未指定（null/空文字）の場合、「0」とみなす
                funcId = Item.FUNC_COMMON;
            }
            return Integer.parseInt(find(keyItem, funcId).getValue());
        } catch (Exception e) {
            //取得できなかった場合は指定された既定値を返す
            LOG.warn("#!-- 指定されたアイテムをマスタから取得することができませんでした。[Type:{}, Key:{}, FuncId:{}, DefaultValue:{}", 
                    keyItem.getValue(), keyItem.getKey(), funcId, defaultValue);
            return defaultValue;
        }
    }
    
    //[248対応（簡易版）]
    /**
     * 汎用アイテム取得（
     *
     * @param keyItem アイテム定数
     * @param funcId 機能ID
     * @param receiveInfo 受信情報
     * @return
     */
    public Item find(Item keyItem, String funcId, ReceiveInfo receiveInfo) {
        String mailAddress = "";
        if(receiveInfo != null){
            //団体区分判定用のメールアドレスを取得
            //※X-Envelope-Org-To を優先
            mailAddress = MailManager.getAddressShort(
                    receiveInfo.getOriginalReceiveAddress(), receiveInfo.getReceiveMailAddress());
        }
        return find(keyItem, funcId, mailAddress);
    }    

    //[248対応（簡易版）]
    /**
     * 汎用アイテム取得（
     *
     * @param keyItem アイテム定数
     * @param funcId 機能ID
     * @param mailAddress 送信先メールアドレス
     * @return
     */
    public Item find(Item keyItem, String funcId, String mailAddress) {
        switch (keyItem.getValue()) {
            case Item.DEFINE:
                return findDefine(keyItem.getKey());
            case Item.CONFIG:
                return findConfig(keyItem.getKey(), funcId, mailAddress);
            case Item.DISP_MESSAGE:
                return findDispMessage(keyItem.getKey(), funcId);
            case Item.MAIL_MESSAGE:
                return findMailMessage(keyItem.getKey(), funcId);
        }
        return null;
    }

    /**
     * 定義アイテム取得
     *
     * @param key アイテムキー
     * @return
     */
    @Transactional
    public Item findDefine(String key) {
        try {
            Define define = em.find(Define.class, key);
            if (define != null) {
                Item item = new Item(define.getItemKey(), define.getItemValue());
                return item;
            }
            return null;
        } catch (NoResultException e) {
            LOG.debug("findDefine not found:key=" + key);
            throw e;
        }
    }

    /**
     * 定義アイテム　ItemValue文字列取得
     *
     * @param key アイテムキー
     * @return
     */
    @Transactional
    public String findDefineStr(String key) {
        Item item = findDefine(key);
        if (item == null) {
            return "";
        }
        if (StringUtils.isBlank(item.getValue())) {
            return "";
        }
        return item.getValue();
    }

    /**
     * 定義アイテム画像Base64文字列取得
     *
     * @param key アイテムキー
     * @return
     */
    @Transactional
    public String findDefineImageStr(String key) {
        try {
            DefineImage define = em.find(DefineImage.class, key);
            if (define != null) {
                return define.getItemValue();
            }
            return null;
        } catch (NoResultException e) {
            LOG.debug("findDefineImageStr not found:key=" + key);
            throw e;
        }
    }

    /**
     * configテーブルからのアイテム取得
     *
     * @param key アイテムキー
     * @param funcId 機能ID
     * @return
     */
    public Item findConfig(String key, String funcId) {
        try {
            return findConfig(em, key, funcId);
        } catch (NoResultException e) {
            LOG.debug("findConfig not found:key=" + key + ", funcId=" + funcId);
            throw e;
        } catch (Exception e) {
            throw e;
        }
    }

    /***
     * configテーブルからのアイテム取得
     * [v2.2.4]別DBに対する処理にも対応するため冗長とならないようstaticとして分離 
     * @param em
     * @param key
     * @param funcId
     * @return 
     */
    public static Item findConfig(EntityManager em, String key, String funcId) {
        Config config;
        TypedQuery<Config> tq = em.createNamedQuery("Config.findConfig", Config.class);
        try {
            tq.setParameter("itemKey", key);
            tq.setParameter("funcId", funcId);
            config = tq.getSingleResult();
            return new Item(config.getItemKey(), config.getItemValue());
        } catch (NoResultException e) {
            if(funcId.equals(Item.FUNC_COMMON))
                throw e;
            tq.setParameter("itemKey", key);
            tq.setParameter("funcId", Item.FUNC_COMMON);
            config = tq.getSingleResult();
            return new Item(config.getItemKey(), config.getItemValue());
        } catch (Exception e) {
            throw e;
        }
    }
    
    /**
     * configアイテム取得(団体区分判別あり） [v2.2.4]無害化パラメータ取得切替え対応
     * @param key
     * @param funcId
     * @param mailAddress   メールアドレス（団体区分判定用）
     * @return 
     */
    public Item findConfig(String key, String funcId, String mailAddress){
        try{
            Item retItem = null;
            
            //他ドメインDBからの取得対象項目かどうかの分岐
            if( ItemHelperOtherDomain.isConfigItemWithDomain(key)){
                //他ドメインDBからの取得対象項目、かつ mailAddressのドメインが他ドメインの場合
                if(!StringUtils.isBlank(mailAddress) && !mailManager.isMyDomain(mailAddress)){
                    //--------------------------
                    //自団体以外の場合
                    //--------------------------

                    //他ドメインの無害化サービス契約団体かどうか
                    String dbName = itemHelperOtherDomain.GetDatabaseName(mailAddress);
                    VerifyUtil.outputUtLog(LOG, "#UT#v2.2.4", false, "GetDatabaseName. mailAddress:%s, dbName:%s",
                            mailAddress, dbName);
                    if( !StringUtils.isBlank(dbName)){
                        //DomainMasterから指定メールアドレスのドメインに該当するデータベース名が取得できた場合
                        //他ドメインの無害化サービス契約団体と判定
                        try {
                            //他ドメイン用のDBからConfigテーブルのデータを取得する
                            retItem = itemHelperOtherDomain.findConfigFromOhterDB(key, funcId, dbName);
                            VerifyUtil.outputUtLog(LOG, "#UT#v2.2.4", false, "findConfigFromOhterDB. key:%s, funcId:%s, dbName:%s, retValue:%s",
                                    key, funcId, dbName,  (retItem == null ? "null":retItem.getValue()));
                        } catch (Exception e) {
                            LOG.error("#!他ドメインからのConfig情報取得に失敗しました。(dbname:{}, mailAddress:{}, key:{}, funcId:{}, exception:{})",
                                    dbName, mailAddress, key, funcId, e.toString());
                            throw e;
                        }
                    }else{
                        //非契約団体
                        //"_Outer"を付加したキーで検索
                        try {
                            retItem = findConfig(em, key + "_Outer", funcId);                    
                        } catch (Exception e) {
                            LOG.warn("#!Can not find config by Outer key!  Key:{}", key + "_Outer");
                        }
                    }
                }
            }else{
                //----------------------------------------------
                // 自団体以外に対して他ドメイン用DBのConfigテーブルから取得せず、自ドメインDBの「_Outer」項目から取得する
                //　対象： パスワード再付与フラグ（passwordReEncryptFlg）
                //----------------------------------------------
//                Item.PASSWORD_RE_ENCRYPT_FLG_INNER;
                //団体区分判定
                OrganizationKbn kbn = getOrganizationKbn(mailAddress);

                //248簡易仕様では、他団体（自団体以外）の場合は"_Outer"を付加したキーで検索
                //（例外の場合CommonとなるのでCommonの場合も自団体扱いとする
                switch(kbn){
                    case Inner:
                    case Common:
                        //自団体の場合の取得処理は後で。
                        break;
                    case OuterLgwan:
                    case Outer:
                        //自団体ではない場合、"_Outer"を付加したキーで検索
                        try{
                            retItem = findConfig(key + "_Outer", funcId);
                        }catch(NoResultException e){
                            LOG.debug("!Can not find config by Outer key!  Key:{}", key + "_Outer");
                            retItem = null;
                        }
                        break;
                }
                VerifyUtil.outputUtLog(LOG, "#UT#v2.2.5", false, "findConfig(自DB用)  key:%s, funcId:%s, mailAddress:%s, kbn:%s, retValue:%s",
                        key, funcId, mailAddress, kbn.name(), (retItem == null ? "inner":retItem.getValue()));                
            }
            //自団体の場合、非契約団体でOuterキーで取得できなかった場合、指定されたキーそのままで検索
            if(retItem == null){
                retItem = findConfig(em, key, funcId);
            }
            return retItem;            
        }catch(Exception e){
            LOG.error("!FindConfig Error! (key={}, funcId={}, mailAddress={}) exception:{}"
                    ,key, funcId, mailAddress, e.toString());
            throw e;
        }
    }    
    
    /**
     * 画面メッセージアイテム取得（汎用、置換え文字なし）
     *
     * @param key アイテムキー
     * @param funcId 機能ID
     * @return　メッセージItem
     */
    public Item findDispMessage(String key, String funcId) {
        return new Item(key, findDispMessageStr(key, funcId));
    }

    /**
     * 画面メッセージ文字列取得（汎用）
     *
     * @param key アイテムキー
     * @param funcId 機能ID
     * @return メッセージ文字列
     */
    public String findDispMessageStr(String key, String funcId) {
        DispMessage mailMessage;

//        //既に取得済みかどうか
//        if (dispMessageMap.containsKey(key)) {
//            if (dispMessageMap.get(key).containsKey(funcId)) {
//                return dispMessageMap.get(key).get(funcId);
//            }
//        } else {
//            dispMessageMap.put(key, new HashMap<>());
//        }

        TypedQuery<DispMessage> tq = em.createNamedQuery("DispMessage.findMessage", DispMessage.class);
        try {
            tq.setParameter("itemKey", key);
            tq.setParameter("funcId", funcId);
            mailMessage = tq.getSingleResult();
        } catch (NoResultException e) {
            try {
                tq.setParameter("itemKey", key);
                tq.setParameter("funcId", Item.FUNC_COMMON);
                mailMessage = tq.getSingleResult();
            } catch (NoResultException e2) {
                LOG.debug("findDispMessageStr not found:key=" + key + ", funcId=" + funcId);
                throw e2;
            }
        } catch (Exception e) {
            throw e;
        }

        String resString = mailMessage.getItemValue();
//        dispMessageMap.get(key).put(funcId, resString);
        return resString;
    }

    /**
     * 画面メッセージ文字列取得（汎用、メッセージマスタ文字列中の%x部を可変引数argsで置換えてメッセージを取得）
     *
     * @param key アイテムキー
     * @param funcId 機能ID
     * @param args 置換え文字（可変長引数）
     * @return メッセージ文字列
     */
    public String findDispMessageStr(String key, String funcId, Object... args) {
        String msg = findDispMessageStr(key, funcId);
        //System.out.println("msg="+msg+" →"+getDispMessage(msg));
        return getDispMessage(msg, args);
        //Formatter fm = new Formatter();
        //return fm.format(msg, args).toString();
    }

    /**
     * 画面メッセージ文字列取得（エラーメッセージ用、置換え文字無し）
     *
     * @param key アイテムキー
     * @param funcId 機能ID
     * @return メッセージ文字列
     */
    public String findDispMessageStr(Item.ErrMsgItemKey key, String funcId) {
        return findDispMessageStr(key.getString(), funcId);
    }

    /**
     * 画面メッセージ文字列取得（エラーメッセージ用、メッセージマスタ文字列中の%x部を可変引数argsで置換えてメッセージを取得）
     *
     * @param key アイテムキー
     * @param funcId 機能ID
     * @param args 置換え文字（可変長引数）
     * @return メッセージ文字列
     */
    public String findDispMessageStr(Item.ErrMsgItemKey key, String funcId, Object... args) {
        return findDispMessageStr(key.getString(), funcId, args);
    }

    /**
     * 画面メッセージ文字列取得（インフォメーションメッセージ用、置換え文字無し）
     *
     * @param key アイテムキー
     * @param funcId 機能ID
     * @return メッセージ文字列
     */
    public String findDispMessageStr(Item.InfMsgItemKey key, String funcId) {
        return findDispMessageStr(key.getString(), funcId);
    }

    /**
     * 画面メッセージ文字列取得（インフォメーションメッセージ用、メッセージマスタ文字列中の%x部を可変引数argsで置換えてメッセージを取得）
     *
     * @param key アイテムキー
     * @param funcId 機能ID
     * @param args 置換え文字（可変長引数）
     * @return メッセージ文字列
     */
    public String findDispMessageStr(Item.InfMsgItemKey key, String funcId, Object... args) {
        return findDispMessageStr(key.getString(), funcId, args);
    }



    /**
     * 画面メッセージ文字列取得（確認メッセージ用、置換え文字無し）
     *
     * @param key アイテムキー
     * @param funcId 機能ID
     * @return メッセージ文字列
     */
    public String findDispMessageStr(Item.ConfirmMsgItemKey key, String funcId) {
        return findDispMessageStr(key.getString(), funcId);
    }

    /**
     * 画面メッセージ文字列取得（確認メッセージ用、メッセージマスタ文字列中の%x部を可変引数argsで置換えてメッセージを取得）
     *
     * @param key アイテムキー
     * @param funcId 機能ID
     * @param args 置換え文字（可変長引数）
     * @return メッセージ文字列
     */
    public String findDispMessageStr(Item.ConfirmMsgItemKey key, String funcId, Object... args) {
        return findDispMessageStr(key.getString(), funcId, args);
    }

    /**
     * 画面メッセージ文字列取得（ワーニングメッセージ用、置換え文字無し）
     *
     * @param key アイテムキー
     * @param funcId 機能ID
     * @return メッセージ文字列
     */
    public String findDispMessageStr(Item.WarningMsgItemKey key, String funcId) {
        return findDispMessageStr(key.getString(), funcId);
    }

    /**
     * 画面メッセージ文字列取得（ワーニングメッセージ用、メッセージマスタ文字列中の%x部を可変引数argsで置換えてメッセージを取得）
     *
     * @param key アイテムキー
     * @param funcId 機能ID
     * @param args 置換え文字（可変長引数）
     * @return メッセージ文字列
     */
    public String findDispMessageStr(Item.WarningMsgItemKey key, String funcId, Object... args) {
        return findDispMessageStr(key.getString(), funcId, args);
    }

    /**
     * メッセージマスタ文字列中の%x部を可変引数argsで置換え
     *
     * @param msg メッセージ文字列
     * @param args 置換え文字（可変長引数）
     * @return メッセージ文字列
     */
    private String getDispMessage(String msg, Object... args) {
        Formatter fm = new Formatter();

        //Formatterを参考に書式数を算出
        String formatSpecifier = "%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])";
        Pattern fsPattern = Pattern.compile(formatSpecifier);
        Matcher m = fsPattern.matcher(msg);
        Map<Integer, String>arM = new HashMap<>();///キー:_cnt、値=書式
        int _cnt = 0;   ///書式数
        for (int i = 0, len = msg.length(); i < len; ) {
            if (m.find(i)) {
                if (m.start() != i) {
                    //System.out.println("...msg:"+msg.substring(i, m.start()));_cnt++;
                }
                //System.out.println("...[書式]:"+_cnt + "_" + msg.substring(m.start(), m.end()));
                arM.put(_cnt, msg.substring(m.start(), m.end()));
                _cnt++;
                i = m.end();

            } else {
                //System.out.println("...msg:"+msg.substring(i));
                break;
            }
        }
        //System.out.println("...msg="+msg+" args数="+args.length+" 書式数="+_cnt);

        //変換
        if (args.length>=_cnt) {
            return fm.format(msg, args).toString();
        }
        else {
            ///(新)args作成
            Object[]_args = new Object[_cnt];
            for (int _idx=0; _idx<_args.length; _idx++) {
                ///args値→_argsへ
                if (_idx<args.length) {
                    _args[_idx] = args[_idx];
                }
                ///クリア
                else {
                    String _m = arM.get(_idx);
                    _args[_idx] = "";
                }
            }

            ///retuen
            return fm.format(msg, _args).toString();
        }
    }

    /**
     * メールメッセージアイテム取得
     *
     * @param key アイテムキー
     * @param funcId 機能ID
     * @return
     */
    public Item findMailMessage(String key, String funcId) {
        try {
            return findMailMessageDirect(key, funcId);
        } catch (NoResultException e) {
            return findMailMessageDirect(key, Item.FUNC_COMMON);
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * メールメッセージアイテム取得
     *
     * @param key アイテムキー
     * @param funcId 機能ID
     * @return
     */
    public Item findMailMessageDirect(String key, String funcId) {
        MailMessage mailMessage;

        TypedQuery<MailMessage> tq = em.createNamedQuery("MailMessage.findMessage", MailMessage.class);
        try {
            tq.setParameter("itemKey", key);
            tq.setParameter("funcId", funcId);
            mailMessage = tq.getSingleResult();
            return new Item(mailMessage.getItemKey(), mailMessage.getItemValue());
        } catch (Exception e) {
            throw e;
        }
    }
   
    /**
     * 団体区分の取得
     * @param mailAddress 送信先メールアドレス
     * @return 団体区分
     */
    public OrganizationKbn getOrganizationKbn(String mailAddress)
    {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));        
        // 複数ドメインに対応した庁内判定
        try {            
            if(StringUtils.isBlank(mailAddress))
                return OrganizationKbn.Common;
            
            //自団体かどうか
            if(mailManager.isMyDomain(mailAddress)){
                //自団体
                return OrganizationKbn.Inner;
            }
            //他団体（LGWAN内）かどうか
            if(mailManager.isLgDomain(mailAddress)){
                //他団体（LGWAN内）
                return OrganizationKbn.OuterLgwan;
            }
            //それ以外（他団体）
            return OrganizationKbn.Outer;
        } catch (Exception e) {
            return OrganizationKbn.Common;
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }        
    }        
}
