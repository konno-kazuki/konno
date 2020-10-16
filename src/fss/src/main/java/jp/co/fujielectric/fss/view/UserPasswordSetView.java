/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.view;

import java.util.Arrays;
import java.util.List;
import javax.enterprise.context.RequestScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.data.ManagePasswordPolicy;
import jp.co.fujielectric.fss.entity.OnceUser;
import jp.co.fujielectric.fss.logic.AuthLogic;
import jp.co.fujielectric.fss.logic.PasswordPolicyLogic;
import jp.co.fujielectric.fss.service.OnceUserService;
import jp.co.fujielectric.fss.util.CommonUtil;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.context.RequestContext;

/**
 *
 * @author nakai
 */
@AppTrace
@ViewTrace
@Named
@RequestScoped
public class UserPasswordSetView extends CommonView {

    @Inject
    private AuthLogic authLogic;

    @Inject
    private OnceUserService onceUserService;

    @Inject
    private PasswordPolicyLogic passwordPolicyLogic;
    
    @Getter
    @Setter
    private String userId = "";

    @Getter
    @Setter
    private String message;

    @Getter
    @Setter
    private String passwordNew;

    @Getter
    @Setter
    private String passwordNewRe;

    @Getter
    private String confirmMessage = "";

//    @Getter
//    private String title = "";
//    @Getter
//    private String execBtnCaption = "";
    @Getter
    protected long passwordCharMin;         //パスワード文字数Min
    @Getter
    protected long passwordCharMax;         //パスワード文字数Max

    @Getter
    @Setter
    private boolean isExecDone = false;    //処理済みかどうか

    private String onetimeId = "";

    @Getter
    @Setter
    private String input_password = "";     //入力パスワード

    @Getter
    private String pswdPolicyMsg = "";      //パスワードポリシーメッセージ
    
    @Getter
    private boolean passwordResetFlg = false;   //ログイン画面からのパスワード変更かどうか
    
    //コンストラクタ
    public UserPasswordSetView() {
        funcId = "userPasswordSet";
    }

    /**
     * 画面区分毎の初期化
     *
     */
    @Override
    protected void initFunc() {

        if (!commonBean.isLoginFlg()) {
//            title = "ログインパスワード設定";
//            execBtnCaption = "パスワードを設定する";

            // ワンタイム情報からユーザーID取得
            if (commonBean.getOnetimeId() != null) {
                // ワンタイムユーザ情報からmailIdを取得し、ユーザIDにセット
                OnceUser onceUser = onceUserService.find(commonBean.getOnetimeId());
                if (onceUser != null) {
                    userId = onceUser.getMailId();
                    onetimeId = onceUser.getOnetimeId();
                }
            }else{
                userId = commonBean.getUserId();
                if(userId != null && !userId.isEmpty())
                    passwordResetFlg = true;
            }            
        } else {
            //ログイン後の処理（パスワード変更）
//            title = "ログインパスワード変更";
//            execBtnCaption = "パスワードを変更する";
            userId = commonBean.getUserId();
        }
    }

    /**
     * パスワードポリシーメッセージ生成
     */
    private String makePasswordPolicyMsg()
    {
        //パスワードポリシー表示対象
        List<Item.PasswordPolicyItemKey> dispKeyLst = Arrays.asList(
                Item.PasswordPolicyItemKey.SAMEID_PW_CHECK,     //ユーザID、パスワード同一不可
                Item.PasswordPolicyItemKey.MINLENGTH,           //パスワード最小文字数
                Item.PasswordPolicyItemKey.MINALP,     //パスワード英字最小文字数
                Item.PasswordPolicyItemKey.MINNUM,     //パスワード数字最小文字数
                Item.PasswordPolicyItemKey.MINMARK, //パスワード記号最小文字数
                Item.PasswordPolicyItemKey.MAXSAMECHAR,     //パスワード同一文字制限
                Item.PasswordPolicyItemKey.EXCLUDECHAR,     //パスワード除外文字
//                Item.PasswordPolicyItemKey.HISTORY_CHECK,   //パスワード履歴数（パスワード履歴チェックに合わせて表示する）
                Item.PasswordPolicyItemKey.HISTORY_CHECK,   //パスワード履歴チェック
                Item.PasswordPolicyItemKey.LIMIT_DAY,           //パスワード有効期限
                Item.PasswordPolicyItemKey.WARNLIMIT_DAY,       //パスワード期限警告
                Item.PasswordPolicyItemKey.LOGINLOCK,           //ログインロック試行回数
                Item.PasswordPolicyItemKey.LOCKCLEAR_TIME       //ログインロッククリア時間
        );

        //パスワードポリシー設定取得
        List<ManagePasswordPolicy> passwordPolicyList = passwordPolicyLogic.getPasswordPolicyItemList();
        
        //パスワードポリシー設定内容からメッセージを生成
        String msg = "";
        for(Item.PasswordPolicyItemKey dispKey: dispKeyLst ){
            ManagePasswordPolicy item = passwordPolicyLogic.getPasswordPolicyItem(passwordPolicyList, dispKey);
            if(!item.isChecked()){
                //無効のものは表示しない
                continue;
            }
            if(msg.length() > 0)
                msg += "、　";
            //項目名
            msg += item.getItemTitle();
            
            //パスワード履歴チェックのみ例外的に履歴数を別キー（パスワード履歴数）から取得して表示する
            if(dispKey == Item.PasswordPolicyItemKey.HISTORY_CHECK){
                item.setHasValue(true);
                //パスワード履歴数を取得
                ManagePasswordPolicy itemTmp = passwordPolicyLogic.getPasswordPolicyItem(passwordPolicyList, Item.PasswordPolicyItemKey.HISTORY);
                //設定値に履歴数を表示
                if(itemTmp.isChecked()){
                    item.setItemValue(itemTmp.getItemValue());
                }else{
                    //履歴数が無効の場合でも現在のパスワードとの差異チェックはするので履歴数に１を表示
                    item.setItemValue("1");
                }
                item.setItemUnit(itemTmp.getItemUnit());    //単位もパスワード履歴数のものを適用
            }
            
            //設定値
            if(item.isHasValue()){
                msg += ":";
                msg += item.getItemValue();
                //単位
                msg += item.getItemUnit();
            }            
        }
        return msg;
    }
    
    /**
     * パスワードポリシーメッセージ取得
     * @return パスワードポリシー説明文
     */
    private String getPasswordPolicyMsg()
    {
        //パスワードポリシー説明文をconfigから取得
        String msg = passwordPolicyLogic.getPasswordPolicyNote();
        //設定されていなければパスワードポリシー設定内容から動的に生成
        if(msg == null || msg.isEmpty())
            msg = makePasswordPolicyMsg();
        return msg;
    }
    
    /**
     * マスタ設定値からの変数初期化
     *
     */
    @Override
    protected void initItems() {
        //確認メッセージ
        confirmMessage = itemHelper.findDispMessageStr(Item.ConfirmMsgItemKey.CONFIRM_USER_PW_SET, funcId);

        //パスワード文字数Min
        Item item = itemHelper.find(Item.PASSWORD_CHAR_MIN, funcId);
        passwordCharMin = Long.parseLong(item.getValue());

        //パスワード文字数Max
        passwordCharMax = CommonUtil.getPasswordCharMax(itemHelper, funcId);

        //パスワードポリシーメッセージ
        pswdPolicyMsg = getPasswordPolicyMsg();
        LOG.debug("パスワードポリシーメッセージ：{}", pswdPolicyMsg);            
    }

    /**
     * ログイン方法
     *
     * @return true:通常 / false:ワンタイム
     */
    public boolean isLoginFlg() {
        return commonBean.isLoginFlg();
    }

    /**
     * 入力チェックコントロール
     */
    public void eventCheckInput() {

        //入力チェック
        boolean bret = checkInput();

        //addCallbackParam
        RequestContext req_context = RequestContext.getCurrentInstance();
        req_context.addCallbackParam("isSuccess", bret);
        if (!bret) {
            ///チェックエラーが見つかった場合
        }
        else {
            ///パスワードを保管
            input_password = passwordNew;
        }
    }

    /**
     * 入力チェック
     *
     * @return チェック結果
     */
    private boolean checkInput() {
        boolean bret = true;
        String errMsg;
        String componentId;
        String itemName;

        String frmName = "loginForm";

//        LOG.debug(userId + "," + "," + passwordNew + "," + passwordNewRe);

        FacesContext context = FacesContext.getCurrentInstance();
        try {

            //エラーリストクリア
            errComponentIdList.clear();

            //---------------------------
            //パスワード
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspPassword");
            componentId = frmName + ":" + "passwordNew";
            {
                ///必須
                if (passwordNew.isEmpty()) {
                    //errMsg = "パスワードが未入力です。";
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_REQUIRED, funcId, itemName);
                }
                else {                    
                    //パスワードチェック（パスワードポリシー対応）
                    errMsg = passwordPolicyLogic.chkPasswordAndGetErrMsg(userId, passwordNew);
                }

                if (!errMsg.isEmpty()) {
                    ///xhtmlにて、showDetail≠"true"なので、注意
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errMsg, ""));
                    errComponentIdList.add(componentId);
                    bret = false;
                }
            }

            //---------------------------
            //パスワード（再入力）
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspPasswordNewRe");
            componentId = frmName + ":" + "passwordNewRe";
            {
                ///必須
                if (passwordNewRe.isEmpty()) {
                    //errMsg = "パスワード（再入力）が未入力です。";
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_REQUIRED, funcId, itemName);
                }
                else {
                    /// パスワードの一致確認
                    if (!passwordNew.equals(passwordNewRe)) {
                        LOG.trace("新パスワード不一致");

                        //errMsg = "パスワードが一致していません。"
                        errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.LOGIN_PSWD_INVALID, funcId);
                    }
                }

                if (!errMsg.isEmpty()) {
                    ///xhtmlにて、showDetail≠"true"なので、注意
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errMsg, ""));
                    errComponentIdList.add(componentId);
                    bret = false;
                }
            }
            FacesContext.getCurrentInstance().renderResponse(); ///省略可能

        } catch (Exception ex) {
            LOG.error("ユーザパスワード設定チェック失敗。", ex);
            return false;
        }

        return bret;
    }

    /**
     * パスワード変更登録アクション
     *
     */
    public void pwCommit() {
        LOG.trace("パスワード設定");

        // パスワード変更処理
        boolean bret = authLogic.modifyBasicUserPassword(userId, input_password, onetimeId);
        if (!bret) {
            LOG.warn("パスワード設定失敗");

            // エラーの出力
            //"パスワードの設定に失敗しました。"
            String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.LOGIN_PSWD_SET_FAILED, funcId);
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    errMsg,
                    errMsg));
            FacesContext.getCurrentInstance().renderResponse();
            return;
        }
        if(passwordResetFlg){
            //ログイン処理
            authLogic.login(userId);
            passwordResetFlg = false;
        }

        //----------------------
        //パスワード設定成功
        //----------------------
        // パスワード変更完了連絡
        //"パスワードを変更しました。"
        String infMsg = itemHelper.findDispMessageStr(Item.InfMsgItemKey.LOGIN_PSWD_SET, funcId);
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
                FacesMessage.SEVERITY_INFO,
                infMsg,
                infMsg));

        isExecDone = true;
    }
}
