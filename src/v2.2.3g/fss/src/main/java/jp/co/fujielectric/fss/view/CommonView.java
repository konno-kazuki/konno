package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
//import java.util.Formatter;
import java.util.List;
import javax.annotation.PostConstruct;
//import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.mail.internet.InternetAddress;
//import javax.inject.Named;
//import jp.co.fujielectric.fss.common.AppTrace;
//import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.entity.Config;
import jp.co.fujielectric.fss.data.CommonBean;
import jp.co.fujielectric.fss.logic.ItemHelper;
import jp.co.fujielectric.fss.util.TextUtil;
import org.apache.logging.log4j.Logger;
import lombok.Getter;

/**
 * 共通ビュークラス (BackingBean)
 */
//@AppTrace
//@ViewTrace
//@Named
//@ViewScoped
public class CommonView implements Serializable {

    @Inject
    protected CommonBean commonBean;

    @Inject
    protected Logger LOG;

    @Inject
    protected ItemHelper itemHelper;

    //機能ID
    @Getter
    protected String funcId;
    //システム日付
    protected Date sysDate;

    //エラーありのコンポーネントIDのリスト
    protected List<String> errComponentIdList = new ArrayList<>();

    /**
     * 初期化
     *
     */
    @PostConstruct
    public void init() {

        //システム日付（"yyyy/mm/dd h:mm:ss"）
        sysDate = new Date();

        //マスタ設定値からの変数初期化
        initItems();

        //画面毎の初期化
        initFunc();
    }

    /**
     * 画面毎の初期化
     *
     */
    protected void initFunc() {
        //-------------------------------
        // 各画面毎のViewで実装する。
        //-------------------------------
    }

    /**
     * マスタ設定値からの変数初期化
     *
     */
    protected void initItems() {
        //-------------------------------
        // 各画面毎のViewで実装する。
        //-------------------------------
    }

    /**
     * 選択アイテム作成
     *
     * @param itemKey
     * @param itemValue
     * @return 選択アイテム
     */
    public Config createSelectItem(String itemKey, String itemValue) {
        Config item = new Config();
        item.setItemKey(itemKey);
        item.setItemValue(itemValue);
        return item;
    }

    /**
     * class（ノーマル/エラー）を返す
     *
     * @param normalClass
     * @param componentId
     * @return class名(ノーマル/エラー）
     */
    public String getClassName(String normalClass, String componentId) {
        if (errComponentIdList.contains(componentId)) {
            //エラー対象
            // ※エラー用のクラス名を　"{ノーマル時クラス名}.err" でCSS登録しておくこと。
            return normalClass + " ui-state-error";
        }
        return normalClass;
    }

    /**
     * FacesMessage用Summary取得
     *
     * @param item
     * @return
     */
    public String getFacesMessageSummary(String item) {
        return item + "：　";
    }

    /**
     * 画面の各コントロールのキャプション取得
     *
     * @param key
     * @return
     */
    public String getItemCaption(String key) {
        //return getItemCaption(key, "");
        return itemHelper.findDispMessageStr(key, funcId);
    }

    /**
     * 画面の各コントロールのキャプション取得
     *
     * @param key
     * @param param_1
     * @return
     */
    public String getItemCaption(String key, String param_1) {

        String str;
        if (param_1.isEmpty()) {
            str = itemHelper.findDispMessageStr(key, funcId, "");
        } else {
            str = itemHelper.findDispMessageStr(key, funcId, itemHelper.findDispMessageStr(param_1, funcId));
        }
        return str;
    }

    /**
     * DefineテーブルからItemValue文字列を取得
     *
     * @param key
     * @return
     */
    public String getDefineValue(String key) {
        return itemHelper.findDefineStr(key);
    }

    /**
     * Defineテーブルから画像（Base64エンコード）を取得
     *
     * @param key
     * @return
     */
    public String getDefineImage(String key) {
        return itemHelper.findDefineImageStr(key);
    }
    
    /**
     * 画面表示用アドレステキストを取得
     *
     * @param ia            InternetAddress
     * 
     * @return
     */
    public String getAddressText(InternetAddress ia) {
        return TextUtil.createAddressText(ia);
    }
    
    /**
     * 画面表示用アドレステキストを取得
     *
     * @param address       アドレス
     * @param personal      名前
     * 
     * @return
     */
    public String getAddressText(String address, String personal) {
        return TextUtil.createAddressText(address, personal);
    }
    
    /**
     * メールアドレスと名称、いずれかが入力されている有効なメールリスト数
     * 
     * @param mailList メールリスト
     * 
     * @return 有効なメールリスト数
     */
    public int getMailEffectiveSize(List<InternetAddress> mailList) {
        int mailSize = 0;
        for (InternetAddress ia : mailList) {
            if (ia.getAddress().isEmpty() && ia.getPersonal().isEmpty()) { continue; }
            mailSize++;
        }
        return mailSize;
    }
}
