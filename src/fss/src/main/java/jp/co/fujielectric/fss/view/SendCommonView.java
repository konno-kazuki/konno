package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.internet.InternetAddress;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
//import jp.co.fujielectric.fss.data.CommonBean;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.logic.MailManager;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.DateUtil;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * ファイル送信共通ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class SendCommonView extends CommonView implements Serializable {

    @Getter
    protected List<InternetAddress> mailToList;
    @Getter
    protected List<InternetAddress> mailApproveList;
    @Getter
    protected Date minDate; //保存期間（From）
    @Getter
    protected Date maxDate; //保存期間 (To)
    @Getter
    protected long addressesCountLimit;     //メールアドレス数
    @Getter
    protected long addressMailCharMax;      //メールアドレス文字数Max
    @Getter
    protected long addressNameCharMax;      //メール名称文字数Max
    @Getter
    protected long commentCharMax;          //コメント最大文字数
    @Getter
    protected int passwordCharDefault;     //パスワード文字数
    @Getter
    protected long passwordCharMin;         //パスワード文字数Min
    @Getter
    protected long passwordCharMax;         //パスワード文字数Max
    @Getter
    protected boolean internalTransferFlg = false;     //庁内ファイル交換フラグ
    @Getter
    protected boolean externalTransferFlg = false;     //セキュリティ便フラグ

    @Getter
    protected boolean isApprovalsFlg;       //承認者指定可能モード
    @Getter
    protected long approverAddressesCountLimit; //承認者メールアドレス数

    @Getter
    protected boolean isExecResultOK = false;

    /**
     * マスタデータに異常があります。（RejectDomain）
     */
    private final String ERR_REJECTDOAMIN_MASTER = "errRejectDomainMaster";
    /**
     * マスタデータに異常があります。（LgwanDomain）
     */
    private final String ERR_LGWANDOAMIN_MASTER = "errLgwanDomainMaster";    
    
    protected Date expirationDefault;       //保存期間初期値

    protected String uuid;

    //エラーありのメールアドレス入力のリスト
    protected List<InternetAddress> errMailList = new ArrayList<>();
    //エラーありの承認者メールアドレス入力のリスト
    protected List<InternetAddress> errMailApproveList = new ArrayList<>();

    protected String confUrl;   //確認画面URL
    protected String inputUrl;   //入力画面URL

    @Inject
    private MailManager mailManager;
//    @Inject
//    protected CommonBean commonBean;
    @Inject
    protected ManageIdView manageIdView;

    /**
     * 画面区分毎の初期化
     *
     */
    @Override
    protected void initFunc() {
        //メールアドレスリストが空の場合の処理
        if (mailToList == null || mailToList.isEmpty()) {
            mailToList = new ArrayList<>();
            try {
                mailToList.add(new InternetAddress("", ""));  //MailToには空の１件を登録
            } catch (UnsupportedEncodingException ex) {
                java.util.logging.Logger.getLogger(SendCommonView.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        //承認者メールアドレスリストが空の場合の処理
        if (mailApproveList == null || mailApproveList.isEmpty()) {
            mailApproveList = new ArrayList<>();
            try {
                mailApproveList.add(new InternetAddress("", ""));  //MailApproveには空の１件を登録
            } catch (UnsupportedEncodingException ex) {
                java.util.logging.Logger.getLogger(SendCommonView.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * マスタ設定値からの変数初期化
     *
     */
    @Override
    protected void initItems() {
        Item item;
        Date date = new Date(); //現在日付

        //保存期間初期値
        item = itemHelper.find(Item.EXPIRATION_DEFAULT, funcId);
        expirationDefault = DateUtil.addDays(date, item.getValue());

        //保存期間選択可能開始日
        item = itemHelper.find(Item.EXPIRATION_MIN, funcId);
        minDate = DateUtil.addDays(date, item.getValue());

        //保存期間選択可能終了日
        item = itemHelper.find(Item.EXPIRATION_MAX, funcId);
        maxDate = DateUtil.addDays(date, item.getValue());

        //メールアドレス数
        item = itemHelper.find(Item.ADDRESSES_COUNT_LIMIT, funcId);
        addressesCountLimit = Long.parseLong(item.getValue());

        //メールアドレス
        item = itemHelper.find(Item.ADDRESS_MAIL_CHAR_MAX, funcId);
        addressMailCharMax = Long.parseLong(item.getValue());

        //メール名称
        item = itemHelper.find(Item.ADDRESS_NAME_CHAR_MAX, funcId);
        addressNameCharMax = Long.parseLong(item.getValue());

        //コメント最大文字数
        item = itemHelper.find(Item.COMMENT_CHAR_MAX, funcId);
        commentCharMax = Long.parseLong(item.getValue());

        //パスワード文字数Min
        item = itemHelper.find(Item.PASSWORD_CHAR_MIN, funcId);
        passwordCharMin = Long.parseLong(item.getValue());

        //パスワード文字数
        item = itemHelper.find(Item.PASSWORD_CHAR_DEFAULT, funcId);
        passwordCharDefault = Integer.parseInt(item.getValue());

        //パスワード文字数Max
//        item = itemHelper.find(Item.PASSWORD_CHAR_MAX, funcId);
//        passwordCharMax = Long.parseLong(item.getValue());
        passwordCharMax = CommonUtil.getPasswordCharMax(itemHelper, funcId);

        //庁内ファイル交換フラグ
        item = itemHelper.find(Item.INTERNAL_TRANSFER_FLG, funcId);
        internalTransferFlg = "true".equals(item.getValue().toLowerCase());

        //セキュリティ便フラグ
        item = itemHelper.find(Item.EXTERNAL_TRANSFER_FLG, funcId);
        externalTransferFlg = "true".equals(item.getValue().toLowerCase());

        //承認者指定可能モード
        boolean _approvalsFlg = false;
        try {
            item = itemHelper.find(Item.APPROVALS_FLG, funcId);
            if (item.getValue().toLowerCase().equals("true")) {
                _approvalsFlg = true;
            }
        } catch (Exception e) { }
        isApprovalsFlg = _approvalsFlg;

        //承認者メールアドレス数
        item = itemHelper.find(Item.APPROVER_ADDRESSES_COUNT_LIMIT, funcId);
        approverAddressesCountLimit = Long.parseLong(item.getValue());
    }


    /**
     * キー値からメッセージを取得
     *
     * @param key
     *
     * @return
     */
    public String getMessageByKey(String key) {
        String msg = "";

        switch (key) {
            case "passwordCharDefault":
                ///"（" + passwordCharDefault + "文字のﾊﾟｽﾜｰﾄﾞを自動生成します。）"
                if (passwordCharDefault>0) {
                    msg = itemHelper.findDispMessageStr("dspPasswordAutoMemo", funcId, passwordCharDefault);
                } else {
                    msg = itemHelper.findDispMessageStr("dspPasswordAutoMemo", funcId, "xx");
                }
                break;
        }

        return msg;
    }

    /**
     * 保存期間
     *
     * @return
     */
    public String getDiffDate() {
        Date now = new Date();

        SimpleDateFormat _sdf = new SimpleDateFormat("yyyy/MM/dd");
        String _strDateFrom = _sdf.format(now);
        String _strDateTo = _sdf.format(getExpirationTime());

        Date dateFrom = null;
        Date dateTo = null;
        try {
            dateFrom = _sdf.parse(_strDateFrom);
            dateTo = _sdf.parse(_strDateTo);
        } catch (ParseException e) {
        }

//        long fromDateTime = now.getTime();
//        long toDateTime = getExpirationTime().getTime();
        long fromDateTime = dateFrom.getTime();
        long toDateTime = dateTo.getTime();

        // 経過ミリ秒÷(1000ミリ秒×60秒×60分×24時間)。端数切り捨て。
        int diffDays = (int) Math.ceil((double) (toDateTime - fromDateTime) / (double) (1000 * 60 * 60 * 24));

        // TODO ロジック見直し必要
//        diffDays--;
        String diffDayeStr;
        if (diffDays == 0) {
            diffDayeStr = "当日";
        } else {
            diffDayeStr = diffDays + "日";
        }

        return "(" + diffDayeStr + ")";
    }

    /**
     * 有効期限
     *
     * @return
     */
    public Date getExpirationTime() {
        // 継承クラスで実装する
        return null;
    }

    /**
     * 有効期限
     *
     * @param value
     */
    public void setExpirationTime(Date value) {
        // 継承クラスで実装する
    }

    /**
     * メールTo削除
     *
     * @param to
     */
    public void deleteMailToAction(InternetAddress to) {
        int index = getMailAddressIndex(to);
        if (index >= 0) {
            mailToList.remove(index);
        }
        if (mailToList.isEmpty()) {
            try {
                mailToList.add(new InternetAddress("", ""));
            } catch (UnsupportedEncodingException ex) {
                java.util.logging.Logger.getLogger(SendCommonView.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * メールTo追加
     *
     * @param to
     */
    public void addMailToAction(InternetAddress to) {
        try {
            //既定のメールアドレス数を超えての追加はできない
            if (mailToList.size() >= addressesCountLimit) {
                return;
            }

            int index = getMailAddressIndex(to);
            if (index >= 0) {
                mailToList.add(index + 1, new InternetAddress("", ""));
            } else {
                mailToList.add(new InternetAddress("", ""));
            }
        } catch (UnsupportedEncodingException ex) {
            java.util.logging.Logger.getLogger(SendCommonView.class.getName()).log(Level.SEVERE, null, ex);
        }
    }


    /**
     * アドレスから、メールエラーリストindexを取得
     *
     * @param addr
     * @return
     */
    public int getMailAddressIndex(InternetAddress addr) {
        int index = -1;
        for (int i = 0; i < mailToList.size(); i++) {
            if (mailToList.get(i) == addr) {
                index = i;
                break;
            }
        }
        return index;
    }

    /**
     * メールアドレスリスト入力チェック
     *
     * @param itemCaption
     * @return チェック結果
     */
    protected boolean checkInputMailList(String itemCaption) {
        boolean bret = true;

        //エラーリストクリア
        errMailList.clear();

        try {
            //メールアドレスと名称が両方未入力は削除する
            for (int i = mailToList.size() - 1; i >= 0; i--) {
                InternetAddress ia = mailToList.get(i);
                ia.setAddress(StringUtils.trim(ia.getAddress()));
                ia.setPersonal(StringUtils.trim(ia.getPersonal()));

                if (StringUtils.isEmpty(ia.getAddress()) && StringUtils.isEmpty(ia.getPersonal())) {
                    deleteMailToAction(ia);
                }
            }

            Integer index = 0;
            for (InternetAddress mto : mailToList) {
                index++;
                //メールアドレス入力チェック
                if (checkInputMail(mto, itemCaption, index.toString()) == false) {
                    bret = false;
                    errMailList.add(mto);       //エラー表示のためエラーのInternetAddressをエラーリストに追加
                }
            }
        } catch (UnsupportedEncodingException ex) {
            java.util.logging.Logger.getLogger(SendCommonView.class.getName()).log(Level.SEVERE, null, ex);
        }

        return bret;
    }

    /**
     * メールアドレスリスト入力チェック
     *
     * @param mailItem
     * @param itemCaption
     * @param index
     * @return チェック結果
     */
    protected boolean checkInputMail(InternetAddress mailItem, String itemCaption, String index) {
        boolean bret = true;
        String errMsg = "";
        String errItem;

        FacesContext context = FacesContext.getCurrentInstance();

        String address = getTrimString(mailItem.getAddress());
        mailItem.setAddress(address);
        boolean isSendInner = mailManager.isMyDomain(address);      //庁内/庁外判定

        if (address.isEmpty()) {
            // "メールアドレスが未入力です。";
            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.MAIL_ADDRESS_REQUIRED, funcId);
        } else if (!CommonUtil.isValidEmail(address)) {
            // "正しいメールアドレスではありません。";
            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.MAIL_ADDRESS_INVALID, funcId);
        }else{
            //ファイル送信画面の場合
            if (funcId.equals("sendTransfer")) {
                //ログインユーザ（ワンタイムユーザ以外）の場合
                if (commonBean.isLoginFlg()){                    
                    
                    if(mailManager.isRejectDomain(address)){
                        //送信不可ドメインの場合
                        // "府、省、庁とのファイル交換はできません。";
                        errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_REJECT_DOMAIN, funcId);
                    }
                    //庁内の場合
                    else if (isSendInner) {
                        //庁内ファイル交換フラグ(ON)かつ内部ユーザ、またはセキュリティ便フラグ(ON)かつ外部ユーザ
                        //の上記以外の場合
                        if ( !((internalTransferFlg && commonBean.isUserTypeInternalFlg()) ||
                               (externalTransferFlg && !commonBean.isUserTypeInternalFlg())) ){
                            // "庁内へファイルを送信することはできません。外部で利用しているメールアドレスを指定してください。";
                            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_INTERNAL, funcId);
                        }

                    //庁外の場合
                    }else{
                        //セキュリティ便フラグ(ON)かつ内部ユーザ
                        //の上記以外の場合
                        if ( !(externalTransferFlg && commonBean.isUserTypeInternalFlg())) {
                            // "外部へファイルを送信することはできません。庁内で利用しているメールアドレスを指定してください。";
                            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_EXTERNAL, funcId);
                        }
                    }
                }

            //ファイル送信依頼画面の場合
            }else if (funcId.equals("sendRequest")) {
                //送信依頼先の場合
                if (itemCaption.equals(getItemCaption("dspSendMailTo"))){
                    //庁外以外(庁内)の場合
                    if (isSendInner) {
                        // "庁内へ送信を依頼することはできません。外部で利用しているメールアドレスを指定してください。";
                        errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_INTERNAL, funcId);
                    }else if(mailManager.isRejectDomain(address)){
                        //送信不可ドメインの場合
                        // "府、省、庁とのファイル交換はできません。";
                        errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_REJECT_DOMAIN, funcId);
                    }                                    
                }
                //受取り先の場合
                else if (itemCaption.equals(getItemCaption("dspReceiveMailTo"))) {
                    //庁内以外(庁外)の場合
                    if (!isSendInner) {
                        // "外部へファイルを送信することはできません。庁内で利用しているメールアドレスを指定してください。";
                        errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_EXTERNAL, funcId);
                    }
                }
            }
        }

        if (!errMsg.isEmpty()) {
            errItem = itemCaption + index + "：　";
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
            bret = false;
        }

        return bret;
    }

    /**
     * パスワード入力チェック
     *
     * @param pswd
     * @return チェック結果
     */
    protected boolean checkInputPasswd(String pswd) {
        boolean bret = true;
        String errMsg = "";
        String errItem;

        FacesContext context = FacesContext.getCurrentInstance();

        if (pswd == null) {
            pswd = "";
        }
        if (pswd.isEmpty()) {
            //"パスワードが未入力です。"
            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.PASSWORD_REQUIRED, funcId);
//        } else if (pswd.length() < passwordCharMin) {
        } else if (CommonUtil.isPasswordCharMin(pswd, passwordCharMin)) {
            //"パスワードの文字数が短すぎます。 [文字数：" + passwordCharMin + "～" + passwordCharMax + "]"
            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.PASSWORD_LENGTH_SHORT, funcId, passwordCharMin, passwordCharMax);
        } else if (!CommonUtil.isValidPassword(pswd)) {
            //"パスワードには半角英数文字のみ入力してください。"
            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.PASSWORD_INVALID, funcId);
        }
        if (!errMsg.isEmpty()) {
            errItem = getFacesMessageSummary(getItemCaption("dspPassword"));    ///"パスワード：　"
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
            errComponentIdList.add("dispForm:passwordInput");   //エラー表示のためコンポーネントIDをエラーリストに追加
            bret = false;
        }
        return bret;
    }

    /**
     * メールアドレスInputTextのclass（ノーマル/エラー）を返す
     *
     * @param addr
     * @return class名(ノーマル/エラー）
     */
    public String getMailClassName(InternetAddress addr) {
        for (InternetAddress mto : errMailList) {
            if (mto == addr) {
                //エラー対象
                return " ui-state-error";
            }
        }
        return "";
    }

    /**
     * InputTextのclass（ノーマル/エラー）を返す
     *
     * @param componentId
     * @return class名(ノーマル/エラー）
     */
    public String getClassName(String componentId) {
        if (errComponentIdList.contains(componentId)) {
            //エラー対象
            // ※エラー用のクラス名を　"{ノーマル時クラス名}.ui-state-error" でCSS登録しておくこと。
            return " ui-state-error";
        }
        return "";
    }

    protected String getTrimString(String str) {
        return (str == null ? "" : str.trim());
    }

    /**
     * 表示用-パス通知有無を取得
     *
     * @param isPassAuto    パス自動有無
     * @param isPassNotice  パス通知有無
     *
     * @return 表示用-パス通知有無
     */
    public String getDspPasswordNotice(boolean isPassAuto, boolean isPassNotice) {
        String _dspPasswordNotice;
        if (isPassAuto) {
            ///通知：有り
            _dspPasswordNotice = getItemCaption("dspPasswordNoticeOn");
        } else {
            if (isPassNotice) {
                ///通知：有り
                _dspPasswordNotice = getItemCaption("dspPasswordNoticeOn");
            } else {
                ///通知：無し
                _dspPasswordNotice = getItemCaption("dspPasswordNoticeOff");
            }
        }

        return _dspPasswordNotice;
    }
    
    /**
     * ドメインマスタ登録チェック
     * @return 
     */
    protected boolean checkDomainMaster(){
        boolean bret = true;
        FacesContext context = FacesContext.getCurrentInstance();

        //ドメインマスタの登録チェック
        try{
            mailManager.isRejectDomain(""); //送信不可ドメインマスタチェック            
        }catch(Exception e){
            String errMsg = itemHelper.findDispMessageStr(ERR_REJECTDOAMIN_MASTER, funcId);
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "", errMsg));
            bret = false;            
        }
        try{
            mailManager.isLgDomain("");     //LGWANドメインマスタチェック
        }catch(Exception e){
            String errMsg = itemHelper.findDispMessageStr(ERR_LGWANDOAMIN_MASTER, funcId);
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "", errMsg));
            bret = false;            
        }
        return bret;
    }
}
