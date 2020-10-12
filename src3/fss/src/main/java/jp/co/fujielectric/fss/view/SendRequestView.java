package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.internet.InternetAddress;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.SendRequestFileBean;
import jp.co.fujielectric.fss.entity.SendRequestInfo;
import jp.co.fujielectric.fss.entity.SendRequestTo;
//import jp.co.fujielectric.fss.logic.MailManager;
import jp.co.fujielectric.fss.logic.SendRequestLogic;
//import jp.co.fujielectric.fss.service.SendRequestInfoService;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.IdUtil;
import lombok.Getter;
import lombok.Setter;

/**
 * ファイル送信依頼ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class SendRequestView extends SendCommonView implements Serializable {

    @Inject
    private SendRequestFileBean sendRequestFileBean;

    @Inject
    private SendRequestLogic sendRequestLogic;

    /* 送信依頼 */
    @Getter
    @Setter
    private SendRequestInfo sendRequestInfo;

    /* 送信依頼宛先 */
    @Getter
    @Setter
    private InternetAddress sendMailTo;

    @Getter
    protected long sendMailToCharMax;      //メールアドレス文字数Max
    
    //dispMessage.itemkey
    /**
     * ファイル送信依頼処理に失敗しました。
     */
    private final String ERR_FILE_SEND_REQUEST = "errFileSendRequest";
    
    //コンストラクタ
    public SendRequestView() {
        funcId = "sendRequest";
    }

    /**
     * 画面区分毎の初期化
     *
     */
    @Override
    public void initFunc() {
        confUrl = "sendRequestConf";
        inputUrl = "sendRequest";

        //送信依頼先メールアドレス文字数Max
        sendMailToCharMax = addressMailCharMax;

        if (sendRequestFileBean.isContinueFlg()) {
            //--------------------------------
            // 編集画面⇔確認画面の遷移の場合
            //--------------------------------

            //変数の引継ぎ
            uuid = sendRequestFileBean.getUuid();
            sendRequestInfo = sendRequestFileBean.getSendRequestInfo();
            sendMailTo = sendRequestFileBean.getSendMailTo();
            mailToList = sendRequestFileBean.getReceiveMailToList();
        } else {
            //--------------------------------
            //ポータルからの遷移の場合
            //--------------------------------

            // 変数初期化
            //ID生成
            uuid = IdUtil.createUUID();

            sendRequestInfo = new SendRequestInfo();
            sendRequestInfo.setPassAuto(true);

            //保存期間初期値
            sendRequestInfo.setExpirationTime(expirationDefault);

            try {
                sendMailTo = new InternetAddress("", "");
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(SendRequestView.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        //テンポラリフォルダの生成
        // ファイル送信なし。（後からファイル送信ありになるかもしれないのでコメントアウト）
        //testFolderTemp = CommonUtil.getSetting("tempdir") + "/" + uuid;
        //送信機能共通の初期化
        super.initFunc();
    }

    /**
     * 入力チェック
     *
     * @return チェック結果
     */
    protected boolean checkInput() {
        boolean bret = true;

        //エラーリストクリア
        errMailList.clear();
        errComponentIdList.clear();

        //ドメインマスタ登録チェック
        if(!checkDomainMaster())
            return false;
        
        //備考、パスワード　nullを空文字、前後の空白削除
        sendRequestInfo.setContent(getTrimString(sendRequestInfo.getContent()));
        sendRequestInfo.setPassWord(getTrimString(sendRequestInfo.getPassWord()));

        //受取り先メールアドレスリストチェック（受取り先）
        if (checkInputMailList(getItemCaption("dspReceiveMailTo")) == false) {
            bret = false;
        }

        //送信先メールアドレスチェック（送信依頼先）
        if (checkInputMail(sendMailTo, getItemCaption("dspSendMailTo"), "") == false) {
            bret = false;
            errComponentIdList.add("dispForm:sendMailTo");
        }

        //パスワードチェック
        if (!sendRequestInfo.isPassAuto()) {
            if (checkInputPasswd(sendRequestInfo.getPassWord()) == false) {
//                sendRequestInfo.setPassWord("");
                bret = false;
            }
        }

        return bret;
    }

    /**
     * 有効期限
     *
     * @return
     */
    @Override
    public Date getExpirationTime() {
        return sendRequestInfo.getExpirationTime();
    }

    /**
     * 有効期限
     *
     * @param value
     */
    @Override
    public void setExpirationTime(Date value) {
        sendRequestInfo.setExpirationTime(value);
    }

    /**
     * ファイル送信実行後の遷移先画面取得
     *
     * @return 遷移先
     */
    public String getActionConf() {

        //入力チェック
        boolean bret = checkInput();

        if (!bret) {
            //入力チェックエラーはnullを返す
            return null;
        }

        //画面遷移時のBeanへのデータ受渡し
        setSendFileBean();

        //遷移先URLを返す
        return confUrl;
    }

    /**
     * ファイル送信実行後の遷移先画面取得
     *
     * @return 遷移先
     */
    public String getActionRev() {
        //画面遷移時のBeanへのデータ受渡し
        setSendFileBean();

        //遷移先URLを返す
        return inputUrl;
    }

    /**
     * ワンタイムID発行実行
     *
     * @param actionEvent
     */
    public void execAction(ActionEvent actionEvent) {
        FacesContext context = FacesContext.getCurrentInstance();

        String errItem = "";
        String errMsg = "";
        try {

            /**
             * 登録情報作成
             */
            // 送信依頼をセットする
            sendRequestInfo.setId(uuid);
            sendRequestInfo.setSendUserId(commonBean.getUserId());
            sendRequestInfo.setSendMailAddress(sendMailTo.getAddress());
            sendRequestInfo.setSendUserName(commonBean.getLoginName());
//        sendRequestInfo.setSubject(_subject);
//        sendRequestInfo.setSendTime(new Date());
            sendRequestInfo.setSendTime(DateUtil.getSysDateExcludeMillis());
            sendRequestInfo.setExpirationTime(DateUtil.getDateExcludeMillisExpirationTime(sendRequestInfo.getExpirationTime()));

            // 送信依頼受取先をセットする
            sendRequestInfo.getSendRequestTos().clear();
            for (InternetAddress receiveMailTo : mailToList) {
                SendRequestTo sendRequestTo = new SendRequestTo();

                sendRequestTo.setId(IdUtil.createUUID());
                sendRequestTo.setSendRequestInfo(sendRequestInfo);
                sendRequestTo.setReceiveMailAddress(receiveMailTo.getAddress());
                sendRequestTo.setReceiveUserName(receiveMailTo.getPersonal());

                // 送信依頼に追加
                sendRequestInfo.getSendRequestTos().add(sendRequestTo);
            }

            /**
             * DB登録、メール送信
             */
            sendRequestLogic.execSendRequest(sendRequestInfo);

            //結果
            isExecResultOK = true;
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("ファイル送信依頼処理失敗。", e);
            ///ファイル送信依頼処理に失敗しました。
            errItem = "";
            errMsg = itemHelper.findDispMessageStr(ERR_FILE_SEND_REQUEST, funcId);
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
        }
    }

    /**
     * 画面遷移時のBeanへのデータ受渡し
     *
     */
    protected void setSendFileBean() {
        sendRequestFileBean.setContinueFlg(true);
        sendRequestFileBean.setUuid(uuid);
        sendRequestFileBean.setSendMailTo(sendMailTo);
        sendRequestFileBean.setReceiveMailToList(mailToList);
        sendRequestFileBean.setSendRequestInfo(sendRequestInfo);
        sendRequestFileBean.setFileInfoList(null);
    }

    /**
     * ファイル送信実行後のポータル遷移
     *
     * @return 遷移先
     */
    public String getActionPortal() {
        if (isExecResultOK) {
            return "portal";
        }
        return "";
    }
    
    /**
     * 表示用-パス通知有無を取得
     *
     * @return 表示用-パス通知有無
     */
    public String getDspPasswordNotice() {
        return getDspPasswordNotice(sendRequestInfo.isPassAuto(), sendRequestInfo.isPassNotice());
    }
}
