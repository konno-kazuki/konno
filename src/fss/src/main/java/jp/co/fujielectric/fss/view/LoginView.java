/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.view;

import javax.enterprise.context.RequestScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.entity.BasicUser;
import jp.co.fujielectric.fss.entity.UserTypePermission;
import jp.co.fujielectric.fss.exception.FssLoginException;
import jp.co.fujielectric.fss.logic.AuthLogic;
import jp.co.fujielectric.fss.logic.MailManager;
import jp.co.fujielectric.fss.logic.PasswordPolicyLogic;
import jp.co.fujielectric.fss.service.BasicUserService;
import jp.co.fujielectric.fss.service.UserTypePermissionService;
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
public class LoginView extends CommonView {

    @Inject
    private AuthLogic authLogic;
    @Inject
    private BasicUserService basicUserService;
    @Inject
    private MailManager mailManager;

    @Inject
    private UserTypePermissionService userTypePermissionService;

    @Inject
    private PasswordPolicyLogic passwordPolicyLogic;

    @Getter
    @Setter
    private String userId;
    @Getter
    @Setter
    private String password;
    @Getter
    @Setter
    private String message;
    @Getter
    @Setter
    private String mailAddress;

    @Getter
    protected long passwordCharMax;     //パスワード文字数Max
    @Getter
    protected long addressMailCharMax;  // メールアドレスアドレスの文字数Max

    /**
     * 警告メッセージ（ダイアログ表示用）
     */
    @Getter
    private String warnMsg = "";    //警告メッセージ

    /**
     * 遷移先
     */
    @Getter
    private String actionTo = "portal";    //遷移先

    @Getter
    private boolean passwordResetFlg = false;   //パスワードリセット要否

    @Getter
    private boolean loginLockFlg = false;       //ログインロックフラグ

    /**
     * ログイン権限有無
     */
    @Getter
    private boolean hasPermission;

    /**
     * お知らせ表示切替
     * true:ログインパネルの下に表示（件名のみリンク表示) 旧タイプ
     * false:ログインパネルの右に表示（件名・本文を表示）[v2.2.4]
     */
    @Getter
    private boolean isNorticeShowBottom = false;
    
    // dispMessage.itemkey
    private final String ERR_USERID_REQUIRED = "errUserIdRequired";

    public LoginView() {
        funcId = "login";
//        passwordCharMax = CommonUtil.getPasswordCharMax(funcId);
    }

    /**
     * マスタ設定値からの変数初期化
     *
     */
    @Override
    protected void initItems() {
        passwordCharMax = CommonUtil.getPasswordCharMax(itemHelper, funcId);
        Item item;
        item = itemHelper.find(Item.ADDRESS_MAIL_CHAR_MAX, funcId);
        addressMailCharMax = Integer.parseInt(item.getValue());

        //usertypepermissionから設定取得（権限がない場合は表示しない）
        UserTypePermission utp = userTypePermissionService.findByUnique("login", "", CommonUtil.getSetting("section"));
        hasPermission = (utp != null);
    }

    public boolean login() {
        boolean bRet = false;
        actionTo = "";  //遷移先
        warnMsg = "";   //警告メッセージ
        passwordResetFlg = false;   //パスワードリセット要否
        loginLockFlg = false;       //ログインロックフラグ
        try {
            if(authLogic.loginCheck(userId, password)) {
                // ユーザ情報の取得
                BasicUser user = basicUserService.find(userId);

                //パスワードリセットチェック処理（パスワードポリシー対応）
                warnMsg = passwordPolicyLogic.chkPasswordResetAndGetMsg(user);
                if(!warnMsg.isEmpty()){
                    //パスワードリセットが必要
                    passwordResetFlg = true;
                    authLogic.login(user, false);
                    LOG.debug("ログイン　要パスワードリセット　userId:{}", userId);

                    //遷移先をパスワード設定画面へ
                    actionTo = "userPasswordSet";
                }else{
                    //ログイン成功
                    authLogic.login(user, true);
                    LOG.trace("ログイン成功");

                    //パスワード警告期限チェック
                    warnMsg = passwordPolicyLogic.chkPasswordWarnLimitAndGetMsg(user);

                    // 遷移先をポータルへ
                    actionTo = "portal";
                }
                bRet = true;
            }
        } catch(FssLoginException e) {
            LOG.trace("ログイン失敗");
            String errMsg = e.getLoginErrMsg();

            // エラーの出力
            if(e.getCode() == null) {
                LOG.error("ログイン失敗", e);
            }else switch(e.getCode()) {
                case ALREADY_LOGGED_IN:
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.LOGIN_ALREADY, funcId);
                    break;
                case NOTHING_USER:
                case NOTHING_PASSWORD:
                case UNMATCH_PASWORD:
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.LOGIN_ID_PSWD_WRONG, funcId);
                    break;
                case EARLY_ACCESS:
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.LOGIN_EARLY, funcId);
                    break;
                case EXPIRED_ACCESS:
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.LOGIN_EXPIRED, funcId);
                    break;
                case LOGIN_LOCK:
                    //ログインロック中（パスワードポリシー対応）
                    if(errMsg.isEmpty())
                        errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_LOGIN_LOCK, funcId);
                    break;
                case PW_LOGIN_LOCKED:
                    //ログインロックされた（パスワードポリシー対応）
                    if(errMsg.isEmpty())
                        errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_PW_LOGINLOCK, funcId);
                    if(errMsg.isEmpty()){
                        //ログインロック時のエラーメッセージが登録されていない場合はパスワード不一致のメッセージを表示
                        errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.LOGIN_ID_PSWD_WRONG, funcId);
                    }else{
                        //ログインロック時のエラーメッセージが登録されているので、ロックされたことを警告ダイアログで表示する
                        loginLockFlg = true;
                        warnMsg = errMsg;
                    }
                    break;
                default:
                    LOG.warn("Unexpected route case", e);
                    break;
            }
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    errMsg,
                    errMsg));
            FacesContext.getCurrentInstance().renderResponse();
        }
        //リクエスト結果設定
        RequestContext.getCurrentInstance().addCallbackParam("isSuccess", bRet);    //成功かどうか
        RequestContext.getCurrentInstance().addCallbackParam("isWarn", !warnMsg.isEmpty()); //警告有無

        LOG.debug("ログイン loginId:{}, isSuccess:{}, actionTo:{}, warnMsg:{}", userId, bRet, actionTo, warnMsg );

        return bRet;
    }

    /**
     * ユーザIDチェック
     */
    public void checkInputUserId() {
        FacesContext context = FacesContext.getCurrentInstance();
        boolean uidEmpty;
        mailAddress = ""; // 初期化

        // エラーリストクリア
        errComponentIdList.clear();
        String errMsg = "";
        String componentId = "loginForm" + ":" + "userId";

        if (userId.isEmpty()) {
            uidEmpty = true;
            // "ユーザIDを入力してください";
            errMsg = itemHelper.findDispMessageStr(ERR_USERID_REQUIRED, funcId);
        } else {
            uidEmpty = false;
        }
        if (!errMsg.isEmpty()) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errMsg, errMsg));
            errComponentIdList.add(componentId);
        }
        RequestContext req_context = RequestContext.getCurrentInstance();
        req_context.addCallbackParam("isError", uidEmpty);
    }

    /**
     * 本人性確認実施
     */
    public void execAction(){
        String errMsg = "";
        RequestContext req_context = RequestContext.getCurrentInstance();
        // 本人確認
        if (!authLogic.checkBasicUserMailAddress(userId, mailAddress)){
            req_context.addCallbackParam("authSuccess", false);
            req_context.addCallbackParam("sendPwNotice", false);
            return;
        }

        // ユーザ情報の取得
        BasicUser user = basicUserService.find(userId);

        int ret;
        // パスワード設定通知
        try {
            //　メール送信
            mailManager.sendMailPasswdSet(user);
            // 成功
            ret = 0;
        } catch (Exception ex) {
            ret = -1;
        }

        if (ret != 0) {
            // 処理に失敗した場合
            req_context.addCallbackParam("authSuccess", true);
            req_context.addCallbackParam("sendPwNotice", false);

            FacesContext context = FacesContext.getCurrentInstance();
            // "パスワード設定通知送信に失敗しました。";
            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.FAILED_NORTICE_PW_SEND, funcId);
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errMsg, errMsg));
        } else {
            // 処理に成功した場合
            req_context.addCallbackParam("authSuccess", true);
            req_context.addCallbackParam("sendPwNotice", true);
        }
    }

    /**
     * 遷移先取得
     * @return
     */
    public String getActionFunc()
    {
        //警告ありの場合は画面遷移させないので空文字を返す
        if(!warnMsg.isEmpty())
            return "";
        //遷移先を返す
        return actionTo;
    }
}
