package jp.co.fujielectric.fss.view;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import javax.annotation.PreDestroy;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.persistence.NoResultException;
import javax.transaction.Transactional;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.client.InvocationCallback;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.CommonEnum;
import jp.co.fujielectric.fss.data.FileInfoBean;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.data.SendFileBean;
import jp.co.fujielectric.fss.entity.DestAddressBook;
import jp.co.fujielectric.fss.entity.OnceUser;
import jp.co.fujielectric.fss.entity.SendFile;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.entity.SendRequestInfo;
import jp.co.fujielectric.fss.entity.SendRequestTo;
import jp.co.fujielectric.fss.logic.AuthLogic;
import jp.co.fujielectric.fss.logic.CheckedFileLogic;
import jp.co.fujielectric.fss.logic.MailManager;
import jp.co.fujielectric.fss.logic.SanitizeHelper;
import jp.co.fujielectric.fss.logic.SendTransferLogic;
import jp.co.fujielectric.fss.logic.SyncFilesHelper;
import jp.co.fujielectric.fss.service.DestAddressBookService;
import jp.co.fujielectric.fss.service.OnceUserService;
import jp.co.fujielectric.fss.service.SendRequestInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.FileUtil;
import jp.co.fujielectric.fss.util.IdUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import jp.co.fujielectric.fss.util.ZipUtil;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.context.RequestContext;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.UploadedFile;

/**
 * ファイル送信（庁内）ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class SendTransferView extends SendCommonView implements Serializable {
    //TODO :::UT::: v2.2.3 SyncFile
    @Inject
    private SyncFilesHelper syncFilesService;

    @Inject
    protected AuthLogic authLogic;
    @Inject
    private SendRequestInfoService sendRequestService;
    @Inject
    protected SendFileBean sendFileBean;
    @Inject
    private SendTransferLogic sendTransferLogic;
    @Inject
    private DestAddressBookService destAddressBookService;
    @Inject
    private MailManager mailManager;
    @Inject
    private OnceUserService onceUserService;
    @Inject
    CheckedFileLogic checkedFileLogic;
    
    @Getter
    protected List<FileInfoBean> fileInfoList = new ArrayList<>();
    @Getter
    protected SendInfo sendInfo;
    @Getter
    protected String msg = "";
    @Getter
    @Setter
    protected UploadedFile upldFile;
    @Getter
    @Setter
    protected FileInfoBean selectedFile;
    @Getter
    protected long totalFSize = 0;
    @Getter
    protected long maxFSize = 0;            //最大ファイルサイズ
    @Getter
    protected long maxTotalFSize = 0;       //最大トータルファイルサイズ
    @Getter
    protected long filesCountLimit;         //ファイル数
    @Getter
    private final String DESTINATION_OTHER = "1";
    @Getter
    private final String DESTINATION_OWN = "2";

    @Getter
    @Setter
    private String destination;

    List<String> fileExcludeSufList = new ArrayList<>();    //送信不可拡張子リスト
    List<String> fileIncludeSufList = new ArrayList<>();    //送信可能拡張子リスト

    @Getter
    private boolean nonSanitizeFlg = false;
    @Getter
    private boolean passwordUnlockFlg = false;

    private String sendFolderTemp = "";     //テスト用フォルダ(Work)
    protected boolean flgUrlMove = false;   //入力画面と確認画面の画面遷移フラグ（Trueの場合に@PreDestroyでテンポラリフォルダを消さない）

    private final long DESTADDRESSBOOK_USECOUNT = 0; // 新規登録時におけるアドレス帳の使用回数

    @Getter
    protected boolean confApprovalRequest;  //(ファイル送信確認時)承認者依頼モードか

    //合議判定[承認全必要フラグ]
    @Getter
    private final boolean APPROVALS_REQUIRED_ALLFLG_ON = true;      //全員が承認したらファイルを送信
    @Getter
    private final boolean APPROVALS_REQUIRED_ALLFLG_OFF = false;    //１人が承認したらファイルを送信

    //dispMessage.itemkey
    /**
     * %sさんからファイルが届いています。
     */
    private final String INF_MESSAGE_GOT_MAIL = "infMessageGotMail";
    /**
     * 以下の依頼に関するファイルを送信します。...
     */
    private final String INF_CONTENT_HEADER = "infContentHeader";
    /**
     * ファイル送信処理に失敗しました。
     */
    private final String ERR_FILE_SEND_TRANSFER = "errFileSendTransfer";
    /**
     * ファイル送信情報生成処理に失敗しました。
     */
    private final String ERR_FILE_SEND_TRANSFER_CREATE = "errFileSendTransferCreate";
    /**
     * 承認依頼処理に失敗しました。
     */
    private final String ERR_APPROVAL_REQUEST = "errApprovalRequest";
    /**
     * 承認依頼情報生成処理に失敗しました。
     */
    private final String ERR_APPROVAL_REQUEST_CREATE = "errApprovalRequestCreate";

    /**
     * 承認者が存在しない状態で、通信欄を設定することはできません。
     */
    private final String ERR_INVALID_APPROVALS_COMMENT = "errInvalidApprovalsComment";
    /**
     * ファイル名が長すぎるファイルがあります。ファイル名は８０文字未満として下さい。
     */
    private final String ERR_FILE_SEND_TRANSFER_FILENAME_LONG = "errFileSendTransferFilenameLong";
    /**
     * ファイル名が長すぎるファイルを含むZIPファイルがあります。ファイル名は８０文字未満として下さい。
     */
    private final String ERR_FILE_SEND_TRANSFER_ZIPFILENAME_LONG = "errFileSendTransferZipFilenameLong";

    
    //コンストラクタ
    public SendTransferView() {
        funcId = "sendTransfer";
    }

    /**
     * 画面区分毎の初期化
     *
     */
    @Override
    public void initFunc() {

        //URL
        confUrl = "sendTransferConf";
        inputUrl = "sendTransfer";

        if (sendFileBean.isContinueFlg()) {
            //--------------------------------
            // 編集画面⇔確認画面の遷移の場合
            //--------------------------------

            //変数の引継ぎ
            uuid = sendFileBean.getUuid();
            sendInfo = sendFileBean.getSendInfo();
            fileInfoList = sendFileBean.getFileInfoList();
            //（編集画面かつ本人宛）以外の場合
            if (!(sendFileBean.getCurrentFuncId().equals(inputUrl) && sendFileBean.getDestination().equals(DESTINATION_OWN))) {
                mailToList = sendFileBean.getMailToList();
            }
            mailApproveList = sendFileBean.getMailApproveList();
        } else {
            //--------------------------------
            //ポータルからの遷移の場合
            //--------------------------------

            //ID生成
            uuid = IdUtil.createUUID();

            //変数初期化
            sendInfo = new SendInfo();
            sendInfo.setId(uuid);            
            sendInfo.setPassAuto(true);
            //保存期間初期値
            sendInfo.setExpirationTime(expirationDefault);

            // ワンタイムログイン
            if (!commonBean.isLoginFlg()) {
                // ワンタイムユーザの取得
                OnceUser onceUser = authLogic.findOnetimeUser(commonBean.getOnetimeId());
                SendRequestInfo sendRequestInfo = sendRequestService.find(onceUser.getMailId());

                // 変数初期化
                String content = itemHelper.findDispMessageStr(INF_CONTENT_HEADER, funcId, sendRequestInfo.getContent());
                sendInfo.setContent(content);

                // 宛先の複写
                mailToList = new ArrayList<>();
                for (SendRequestTo sendRequestTo : sendRequestInfo.getSendRequestTos()) {
                    try {
                        InternetAddress address = new InternetAddress();
                        address.setAddress(sendRequestTo.getReceiveMailAddress());
                        address.setPersonal(sendRequestTo.getReceiveUserName());

                        mailToList.add(address);
                    } catch (UnsupportedEncodingException ex) {
                        java.util.logging.Logger.getLogger(SendTransferView.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                //ワンタイムログインの場合、承認者指定可能モード＝不可(false)とする
                isApprovalsFlg = false;
            }
        }

        //庁外からのファイル送信の場合、承認者指定可能モード＝不可(false)とする
        if (!commonBean.isUserTypeInternalFlg()) {
            isApprovalsFlg = false;
        }

        //送信処理用テンポラリフォルダ(local_senddir)
//        sendFolderTemp = CommonUtil.getSetting("local_senddir") + "/" + uuid;
        sendFolderTemp = CommonUtil.getFolderSend(sendInfo, true, false);

        // 未無害化ファイルのダウンロードを許可するか
        try {
            nonSanitizeFlg = "true".equalsIgnoreCase(itemHelper.find(Item.NONE_SANITIZE_FLG, funcId).getValue());
            passwordUnlockFlg = "true".equalsIgnoreCase(itemHelper.find(Item.PASSWORD_UNLOCK_FLG, funcId).getValue());
        } catch(NoResultException e) {
            LOG.debug("nonSanitizeFlg=" + nonSanitizeFlg + "|" + "passwordUnlockFlg=" + passwordUnlockFlg);
        }

        //送信先情報選択初期値
        if (!StringUtils.isBlank(sendFileBean.getDestination())) {
            destination = sendFileBean.getDestination();
        } else {
            destination = DESTINATION_OTHER;
        }

        //承認依頼関連-初期値設定
        confApprovalRequest = false;
        if (isApprovalsFlg) {
            //合議判定は「１人が承認したらファイルを送信」
            //(ここでは何もしなくていよい）

            //(ファイル送信確認時)承認者依頼モードか
            if (mailApproveList == null || mailApproveList.isEmpty()) {
            } else if (getMailEffectiveSize(mailApproveList) > 0) {
                confApprovalRequest = true;
            }
        }

        //送信機能共通の初期化
        super.initFunc();
    }

    /**
     * マスタ設定値からの変数初期化
     *
     */
    @Override
    protected void initItems() {
        Item item;

        super.initItems();

        //最大サイズ
        item = itemHelper.find(Item.FILE_SIZE_LIMIT, funcId);
        maxFSize = Long.parseLong(item.getValue());

        //最大トータルサイズ
        item = itemHelper.find(Item.FILES_SIZE_LIMIT, funcId);
        maxTotalFSize = Long.parseLong(item.getValue());

        //ファイル数
        item = itemHelper.find(Item.FILES_COUNT_LIMIT, funcId);
        filesCountLimit = Long.parseLong(item.getValue());

        //送信不可ファイル拡張子リスト
        try {
            item = itemHelper.find(Item.FILE_EXCLUDE_SUFFIX_LIST, funcId);
            String fileExcludeSufLstTmp = item.getValue();
            //カンマで分割してList<String>に格納。小文字にする。
            fileExcludeSufList.clear();
            for (String suf : fileExcludeSufLstTmp.split(",")) {
                fileExcludeSufList.add(suf.toLowerCase());
            }
        } catch (Exception e) {
        }

        //送信可能ファイル拡張子リスト
        try {
            item = itemHelper.find(Item.FILE_INCLUDE_SUFFIX_LIST, funcId);
            String fileIncludeSufLstTmp = item.getValue();
            //カンマで分割してList<String>に格納。小文字にする。
            fileIncludeSufList.clear();
            for (String suf : fileIncludeSufLstTmp.split(",")) {
                fileIncludeSufList.add(suf.toLowerCase());
            }
        } catch (Exception e) {
        }
    }

    @PreDestroy
    public void destroy() {
        LOG.debug("SendTransferView Destroy.");
        if (!flgUrlMove) {
            //テンポラリフォルダ削除
            if (!sendFolderTemp.isEmpty()) {
                FileUtil.deleteFolder(sendFolderTemp, LOG);
            }
        }
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
     * 有効期限
     *
     * @return
     */
    @Override
    public Date getExpirationTime() {
        return sendInfo.getExpirationTime();
    }

    /**
     * 有効期限
     *
     * @param value
     */
    @Override
    public void setExpirationTime(Date value) {
        sendInfo.setExpirationTime(value);
    }

    /**
     * ファイルサイズ残表示
     *
     * @return
     */
    public String getFSizeRemain() {
        String retStr;
        long s = 0;

        //トータルサイズ計算
        for (FileInfoBean info : fileInfoList) {
            s += info.getSize();
        }
        //MBに変換に変換して小数点以下第1位まで(切上)
        BigDecimal bd = new BigDecimal(s / 1024.0 / 1024.0);
        bd = bd.setScale(1, BigDecimal.ROUND_UP);

        DecimalFormat df1 = new DecimalFormat("#,##0.0MB");

        retStr = "(" + df1.format(bd.doubleValue()) + " / " + df1.format(maxTotalFSize / 1024.0 / 1024.0) + ")";
        return retStr;
    }

    /**
     * ファイル追加
     *
     * @param event
     * @throws IOException
     */
    public void handleFileUpload(FileUploadEvent event) throws IOException {
        upldFile = event.getFile();

        
        //ファイル名
        String fname = event.getFile().getFileName();
        LOG.debug("ファイル送信画面 ファイル選択：{}", fname);        
        File objFile = new File(sendFolderTemp, fname); //保存先ファイル名（フルパス）
        
        //ファイル名文字数チェック【v2.1.13】
        FileInfoBean.FileErrorReason errReason = FileInfoBean.FileErrorReason.OK;
        
        //同名ファイルがすでにアップロードされてないかチェック
        FileInfoBean sameInfo = fileInfoList.stream().filter(info -> info.getFileName().equalsIgnoreCase(fname)).findFirst().orElse(null);
        
        if(fname.length() > FileUtil.MAX_FILENAME_LEN){
            //ファイル名文字数オーバー

            if (sameInfo != null) {
                //同名ファイルがすでに登録されていたら、すでにファイル名超過として処理済みなので、リスト追加処理等せずにそのまま抜ける
                return;
            }
            LOG.warn("#!ファイル送信画面で選択されたファイル名が長すぎます。（RegionId:{}, UserId:{}, OnetimeId:{}, ファイル名：{}）"
                    ,commonBean.getRegionId(), commonBean.getUserId(),  commonBean.getOnetimeId(), fname);            
            errReason = FileInfoBean.FileErrorReason.FileNameLengthOver;
        }else{
            if (sameInfo != null) {
                //同名ファイルがすでに登録されていたら、同名ファイルの場合は上書きなので削除する
                deleteItemAction(sameInfo);
            }

            //UploadedFile →　File保存
            try (InputStream is = upldFile.getInputstream()) {
    //            //TODO :::UT:::Start v2.1.12
    //            FileUtil.ut256ex(objFile.getPath(), true, LOG);   //模擬ウィルス隔離 対象ファイルは例外発生
    //            //TODO :::UT:::End

                //localsenddirに保存
                FileUtil.saveFile(is, objFile.getPath());                                
            } catch (Exception e) {
                //例外処理
                LOG.error("アップロードファイルの一時保存失敗。", e);
                errReason = FileInfoBean.FileErrorReason.Othre;
            }
            
            //ZIPファイルの場合、zip内ファイルのファイル名文字数オーバーもチェックする【v2.1.13】
            try {
                if(objFile.exists() && FileUtil.isZipFile(objFile)){
                    //zip内のファイル名文字数オーバーのファイル名リストを取得する
                    List<String> fnameLst = ZipUtil.getLengthOverFileNameList(objFile.getPath(), FileUtil.MAX_FILENAME_LEN, null);
                    if(fnameLst != null && fnameLst.size() > 0){
                        //zip内にファイル名文字数オーバーあり
                        errReason = FileInfoBean.FileErrorReason.FileNameLengthOverInZip;
                        //ログ出力
                        String files = "";
                        for(String f: fnameLst){
                            files += ("\n" +f);
                        }
                        LOG.warn("#!ファイル送信画面で選択されたZIPファイルにファイル名が長すぎるファイルが含まれます。"
                                +"（RegionId:{}, UserId:{}, OnetimeId:{}, ファイル名：{}）{}"
                            ,commonBean.getRegionId(), commonBean.getUserId(),  commonBean.getOnetimeId(), fname, files); 
                        //ファイル送信から除外するためファイルを削除する
                        objFile.delete();
                    }
                }
            } catch (Exception e) {
                LOG.error("#!ファイル送信で選択されたZIPファイル内ファイルの文字数オーバーチェックで例外発生しました。  File:" + fname + ", msg:" + e.getMessage(), e);
                //不正なファイルとみなしてファイルを削除する。（送信実行時にエラーが発生）
                try {
                    errReason = FileInfoBean.FileErrorReason.Othre; //その他エラー
                    if(objFile.exists())
                        objFile.delete();                    
                } catch (Exception e2) {
                    //ファイル削除の失敗は無視する
                }
            }
        }
        
        FileInfoBean info = new FileInfoBean(objFile);
        fileInfoList.add(info);
        //送信実行時にフィル名文字数オーバーかどうか判定できるようにFileInfoBean.fileErrorReasonに判定結果をセット【v2.1.13】
        info.setFileErrorReason(errReason);
        
        //ファイルチェック（LGWAN側であれば、全ファイル許可する）
        if (!CommonUtil.isSectionLgwan()) {
            chkFile(info);
        }            
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

        //送信先が本人宛の場合
        if (destination.equals(DESTINATION_OWN)) {
            try {
                //メールリスト初期化
                mailToList.clear();
                //送信先に本人宛を設定
                InternetAddress internetAddress = new InternetAddress();
                internetAddress.setAddress(commonBean.getMailAddress());
                internetAddress.setPersonal("");
                mailToList.add(internetAddress);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        //画面遷移時のBeanへのデータ受渡し
        setSendFileBean();

        //入力画面と確認画面の画面遷移フラグをTRUEに（Trueの場合に@PreDestroyでテンポラリフォルダを消さない）
        flgUrlMove = true;
        //現在の機能IDを設定
        sendFileBean.setCurrentFuncId(confUrl);

        //遷移先URLを返す
        return confUrl;
    }

    /**
     * ファイルチェック
     *
     * @param info
     * @return
     */
    protected boolean chkFile(FileInfoBean info) {
        //ファイルの送信可否チェックを行う
        boolean excludeFileCheck = true;
        boolean passwordFileCheck = true;
        if(this.nonSanitizeFlg) {
            excludeFileCheck = false;
            passwordFileCheck = false;
        } else if(this.passwordUnlockFlg) {
            passwordFileCheck = false;
        }

        if(excludeFileCheck) {
            //拡張子のチェック
            String suffix = FileUtil.getSuffix(info.getFileName()).toLowerCase();     //拡張子（比較のため小文字に統一）
            if (fileExcludeSufList.contains(suffix) || !fileIncludeSufList.contains(suffix)) {
                //"対象外ファイルのため送信されません。"
                info.setErrMsg(itemHelper.findDispMessageStr(Item.ErrMsgItemKey.FILE_ENABLE_SEND_EXCLUDE, funcId));
                // 以降のチェックは不要
                passwordFileCheck = false;
            }
        }
        if(passwordFileCheck && info.getSize() > 0) {
            //パスワード付かチェック
            if (FileUtil.checkPw(info.getFile(), false, LOG)) {
                //パスワード付のため送信されません。
                info.setErrMsg(itemHelper.findDispMessageStr(Item.ErrMsgItemKey.FILE_ENABLE_SEND_PASSWORD, funcId));
            }
        }

        //エラーの場合、削除する
        if (info.isError()) {
            info.setSize(0);
            info.getFile().delete();    //削除
            return false;
        }
        return true;
    }

    /**
     * 送信ファイル一覧からのファイル削除
     *
     * @param info
     * @return
     */
    public String deleteItemAction(FileInfoBean info) {
        info.getFile().delete();
        fileInfoList.remove(info);
        return "";
    }

    /**
     * 入力チェック
     *
     * @return チェック結果
     */
    protected boolean checkInput() {
        boolean bret = true;
        FacesContext context = FacesContext.getCurrentInstance();
       
        //エリーリストクリア
        errComponentIdList.clear();
        errMailList.clear();
        errMailApproveList.clear();

        //ドメインマスタ登録チェック
        if(!checkDomainMaster())
            return false;
        
        //備考、パスワード　nullを空文字、前後の空白削除
        sendInfo.setContent(getTrimString(sendInfo.getContent()));
        sendInfo.setApprovalsComment(getTrimString(sendInfo.getApprovalsComment()));
        sendInfo.setPassWord(getTrimString(sendInfo.getPassWord()));

        //メールアドレスリストチェック（送信先情報）
        //送信先が他人宛の場合
        if (destination.equals(DESTINATION_OTHER)) {
            if (checkInputMailList(getItemCaption("dspSendToInfo")) == false) {
                bret = false;
            }
        }

        //パスワードチェック
        if (!sendInfo.isPassAuto()) {
            if (checkInputPasswd(sendInfo.getPassWord()) == false) {
                bret = false;
            }
        }

        //ファイルチェック
        if (checkInputFiles() == false) {
            bret = false;
        }

        //タブタイトル
        String itemCaptionApproveInfo = getItemCaption("dspSendApproveInfo");

        //承認者メールアドレスリストチェック
        if (checkInputMailApproveList(itemCaptionApproveInfo) == false) {
            bret = false;
        }

        //承認者メールアドレスが入力されていない状態で、通信欄が入力されている場合、エラー
        int mailApproveListSize = getMailEffectiveSize(mailApproveList);
        if (mailApproveListSize < 1 && sendInfo.getApprovalsComment().length() > 0) {
            String errItem = "(" + itemCaptionApproveInfo + ")" + getFacesMessageSummary(getItemCaption("dspComment"));
            String errMsg = itemHelper.findDispMessageStr(ERR_INVALID_APPROVALS_COMMENT, funcId);
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
            errComponentIdList.add("dispForm:tabViewSend:approvalsComment");   //エラー表示のためコンポーネントIDをエラーリストに追加
            bret = false;
        }

        return bret;
    }

    /**
     * ファイル入力チェック
     *
     * @return チェック結果
     */
    protected boolean checkInputFiles() {
        boolean bret = true;
        String errMsg = "";
        String errItem;
        long fsize = 0;     //トータルサイズ
        long fnum = 0;      //ファイル数

        FacesContext context = FacesContext.getCurrentInstance();

        //トータルサイズ計算
        for (FileInfoBean info : fileInfoList) {
            if (!info.isError()) {
                fsize += info.getSize();
                fnum++;
            }
        }
        if (fnum == 0) {
            //"送信可能なファイルが一つも選択されていません。"
            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.FILES_REQUIRED, funcId);
        } else if (fnum > filesCountLimit) {
            //"送信可能なファイル数を超えています。（最大数：" + filesCountLimit + "）"
            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.FILES_COUNT_OVER, funcId, filesCountLimit);
        } else if (fsize > maxTotalFSize) {
            //"ファイルサイズがオーバーしています。ファイルを減らして下さい。"
            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.FILES_SIZE_OVER, funcId);
        }

        if (!errMsg.isEmpty()) {
            errItem = getFacesMessageSummary(getItemCaption("dspFile"));    ///"ファイル：　";
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    errItem, errMsg));
            errComponentIdList.add("dispForm:fileUploadArea");  //エラー表示のためコンポーネントIDをエラーリストに追加
            bret = false;
        }

        //ファイルエラーの画面（背景色）への反映はJavaScriptで行うので
        //ファイルエラーチェックの結果をCallbackParamで返す
        RequestContext reqContext = RequestContext.getCurrentInstance();
        reqContext.addCallbackParam("fileError", !bret);

        return bret;
    }

    /**
     * ファイル送信実行後の遷移先画面取得
     *
     * @return 遷移先
     */
    public String getActionRev() {
        //画面遷移時のBeanへのデータ受渡し
        setSendFileBean();

        //入力画面と確認画面の画面遷移フラグをTRUEに（Trueの場合に@PreDestroyでテンポラリフォルダを消さない）
        flgUrlMove = true;
        //現在の機能IDを設定
        sendFileBean.setCurrentFuncId(inputUrl);

        //遷移先URLを返す
        return inputUrl;
    }

    /**
     * 送信情報をセットする
     *
     * @param _subject 件名(件名が無ければ一括zipダウンロード時にファイル名が生成されないため、暫定で格納)
     */
    private OnceUser setExecActionSendInfo(String _subject) throws AddressException, UnsupportedEncodingException, FileNotFoundException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        OnceUser onceUser = null;
                
        // 送信情報をセットする
//        String sendId = uuid; // sendInfoのIDを保存
//        sendInfo.setId(sendId);

        // 通知省略フラグの設定(true:通知無、false:通知有)
        boolean noticeOmitFlg = false;

        if (commonBean.isLoginFlg()) {
            //---------------------------
            //通常ログインの場合
            //---------------------------
            sendInfo.setSendUserId(commonBean.getUserId());
            sendInfo.setSendUserName(commonBean.getLoginName());
            sendInfo.setSendMailAddress(commonBean.getMailAddress());

            InternetAddress fromAddr = new InternetAddress(commonBean.getMailAddress());
            fromAddr.setPersonal(commonBean.getLoginName());
            sendInfo.setFromAddress(fromAddr.toString());

            // 自分宛の場合、通知無に設定
            if (destination.equals(DESTINATION_OWN)) {
                noticeOmitFlg = true;
            }
        } else {
            //---------------------------
            //ワンタイムログインの場合
            //---------------------------

            //UserID,UserNameはセットなし
            sendInfo.setSendUserId("");
            sendInfo.setSendUserName("");

            // ワンタイムログインではcommonBeanからメールアドレスが取得できないためsendRequestInfoから取得
            // ワンタイムユーザの取得
            onceUser = onceUserService.findWithLock(commonBean.getOnetimeId());
            // MailIdから送信依頼情報取得
            SendRequestInfo sendRequestInfo = sendRequestService.find(onceUser.getMailId());
            //排他チェック（同じワンタイムURLで同時に複数のページを表示している可能性があるため）
            if(!onceUser.getTarget().equalsIgnoreCase("sendTransfer") || sendRequestInfo == null){
                //既に別セッションで送信実行している可能性があるので、エラーとする
                LOG.warn("#!ファイル送信排他エラー。既に送信処理が実行されている可能性があります。 OnetimeId:" + commonBean.getOnetimeId());
                throw new NoResultException("ファイル送信排他エラー");
            }
            //メールアドレス
            String mailAddr = sendRequestInfo.getSendMailAddress();

            sendInfo.setSendMailAddress(mailAddr);
            InternetAddress fromAddr = new InternetAddress(mailAddr);
            sendInfo.setFromAddress(fromAddr.toString());
        }
//        String mailAddressTo = "";
//        String separator = "";
//        for (InternetAddress ia : mailToList) {
//            if (StringUtils.isEmpty(ia.getPersonal())) {
//                mailAddressTo += separator + ia.getAddress();
//            } else {
//                mailAddressTo += separator + ia.toString();
//            }
//            separator = ", ";
//        }
        String mailAddressTo = mailManager.getMailAddressTo(mailToList);
        sendInfo.setReceiveAddresses(mailManager.getMailAddressTo(mailToList, true));
        sendInfo.setOriginalReceiveAddresses("");
        sendInfo.setOriginalSendAddress("");
        sendInfo.setToAddress(mailAddressTo);
        sendInfo.setCcAddress("");
        sendInfo.setSubject(_subject);    //件名が無ければ一括zipダウンロード時にファイル名が生成されないため、暫定で格納
        sendInfo.setSendTime(DateUtil.getSysDateExcludeMillis());
        sendInfo.setExpirationTime(DateUtil.getDateExcludeMillisExpirationTime(sendInfo.getExpirationTime()));
        sendInfo.setHistoryDisp(true);
        sendInfo.setAttachmentMailFlg(false);
        sendInfo.setNoticeOmitFlg(noticeOmitFlg);   // パス通知有無
        sendInfo.setProcDate(new SimpleDateFormat("yyyyMMdd").format(DateUtil.getSysDate()));  //処理日付(YYYYMMDD) [v2.2.1]

//        //TODO :::UT:::Start v2.1.12
//        FileUtil.ut256(sendFolderTemp,null, LOG);   //模擬ウィルス隔離
//        //TODO :::UT:::End
        // tempファイルフォルダを送信ファイルフォルダにコピー        
//        String toDir = CommonUtil.getSetting("senddir") + sendId;
        String toDir = CommonUtil.getFolderSend(sendInfo, false, false);
        FileUtil.copyFolder(sendFolderTemp, toDir, null);

        //送信ファイル情報リスト
        sendInfo.getSendFiles().clear();
        for (FileInfoBean info : fileInfoList) {
            if (info.isError()) {
                continue;
            }
            
            //送信ファイル情報
            SendFile sendFile = new SendFile();
            sendFile.setId(IdUtil.createUUID());
            sendFile.setSendInfo(sendInfo);
            sendFile.setFileName(info.getFileName());
            sendFile.setFileFormat(FileUtil.getSuffix(info.getFileName()));         // TODO: 保留（正確なファイルフォーマットの取得方法？）
            sendFile.setFileSize(info.getSize());
            sendFile.setFilePass("");
            File fInfoFile = new File(toDir, info.getFileName());   //senddir内の実ファイル
            if(!fInfoFile.exists()){
                //ファイルがコピーされていなかった場合はエラーとする（ウィルス等）
                throw new FileNotFoundException("#! Failed to copy sendFile from localSendDir to sendDir. File:" + fInfoFile.getPath());
            }
            sendFile.setFilePath(fInfoFile.getPath());
            sendFile.resetDate();       //作成日時・更新日時のセット　[v2.2.3]
            sendInfo.getSendFiles().add(sendFile);          // 送信情報クラスに追加
        }       
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));        
        return onceUser;
    }

    /**
     * ファイル送信実行
     *
     * @param actionEvent
     * @throws java.lang.Exception
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void execAction(ActionEvent actionEvent) throws Exception {
        FacesContext context = FacesContext.getCurrentInstance();
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        String errItem = "";
        String errMsg = "";
        OnceUser onceUser = null;

        //ファイル名文字数オーバーチェック【v2.1.13】
        try {
            List<String> errMsgs = getFileNameLengthOverMessages();
            if(errMsgs != null && !errMsgs.isEmpty()){
                //ファイル名文字数オーバーあり
                for(String eMsg: errMsgs){
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, eMsg));                    
                }
                return;
            }
        } catch (Exception e) {
            LOG.error("#!ファイル名文字数オーバーメッセージ取得に失敗しました。",e);
            //エラーメッセージ「ファイル送信処理に失敗しました。」
            errMsg = itemHelper.findDispMessageStr(ERR_FILE_SEND_TRANSFER, funcId);
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
            return;
        }
        
        try {

            // 件名生成
            String _subject = itemHelper.findDispMessageStr(INF_MESSAGE_GOT_MAIL, funcId, commonBean.getLoginName());

            // メッセージ
            ///ファイルが送信されました。画面を閉じてください。
            ///再度、同じＵＲＬにログインした場合は、送信状況が確認出来る画面が表示されます。
            msg = itemHelper.findDispMessageStr(Item.InfMsgItemKey.FILE_SEND_ONETIME_AFTER, funcId);

            // 送信情報をセットする
            onceUser = setExecActionSendInfo(_subject);

            sendInfo.setApprovalFlg(false);         ///承認待ちフラグ
            sendInfo.setApprovalsComment("");       ///承認文章
            sendInfo.setApprovalsDoneCount(-1);     ///承認済み回数
            sendInfo.setApprovalsRequiredCount(-1); ///承認必要回数

            // ふるまい検知の有無を判定 [v2.2.1]
            boolean sendFileCheckFlg = checkedFileLogic.checkUseCheckedFile();            
            sendInfo.setSendFileCheckFlg(sendFileCheckFlg);
        } catch (FileNotFoundException e){
            //ウィルス等でアップロードファイルをNFSフォルダにコピー出来なかった場合など
            LOG.error("ファイル送信情報生成処理に失敗しました。(FileIO)", e);
            ///ファイル送信処理に失敗しました。
            errMsg = itemHelper.findDispMessageStr(ERR_FILE_SEND_TRANSFER, funcId);
        } catch (NoResultException e) {
            //排他異常等でエラーとなった場合
            LOG.error("ファイル送信情報生成処理に失敗しました。(排他異常）", e);
            ///ファイル送信処理に失敗しました。
            errMsg = itemHelper.findDispMessageStr(ERR_FILE_SEND_TRANSFER, funcId);
        } catch (Exception e) {
            LOG.error("ファイル送信情報生成処理に失敗しました。", e);
            ///ファイル送信情報生成処理に失敗しました。
            errMsg = itemHelper.findDispMessageStr(ERR_FILE_SEND_TRANSFER_CREATE, funcId);
        }
        if(!errMsg.isEmpty()){
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
            return;            
        }

        String sendRequestId = "";  //送信依頼ID（onceUserテーブルにセットされている値）            
        String onetimeId="";        //ワンタイムID
        try {
            //DB更新（SendInfo)
            sendTransferLogic.writeSendInfo(sendInfo);

            // ワンタイムの場合、ワンタイムユーザ情報を更新
            if(onceUser != null){
                sendRequestId = onceUser.getMailId();
                onetimeId = onceUser.getOnetimeId();                
                //ワンタイムユーザテーブルの更新
                onceUser.setMailId(sendInfo.getId());       //代わりにSendInfo.IDをセットする
                onceUser.setTarget("sendHistoryDetail");    //"sendTransfer"から"sendHistoryDetail"に変更する
                onceUserService.edit(onceUser);
            }                
            
//            //TODO :::UT:::Start v2.1.14
//            // 時間がかかる処理のテスト
//            if(sendInfo.getContent().contains("#UT#execAction Timer#")){
//                try {
//                    LOG.debug("#UT# Sleep Start");
//                    Thread.sleep(15000);    //15秒スリープ
//                    LOG.debug("#UT# Sleep End");
//                } catch (InterruptedException ex) {}
//            }
//            //TODO :::UT:::End
            
//            //TODO :::UT:::Start v2.1.13_A001 例外発生
//            if(sendInfo.getContent().contains("#UT#RestCall Exception#"))
//                throw new RuntimeException("#UT# RestFul呼び出しで例外");    //例外発生
//            //TODO :::UT:::End
            
            //-----------------------------------------------------
            //無害化実行（ReceiveInfo書込み、無害化開始、メール送信等）
            //RESTによる非同期実行
            //-----------------------------------------------------
            //RESTに渡す引数はEmpty不可のため、代替に" "を渡す
            if(StringUtils.isBlank(onetimeId))
                onetimeId = " ";
            if(StringUtils.isBlank(sendRequestId))
                sendRequestId = " ";
            
            Client client = ClientBuilder.newClient();
            WebTarget target = client.target(CommonUtil.createLocalUrl(commonBean.getRegionId(), CommonUtil.isSectionLgwan()) + "webresources/sendtransfer");
            target = target.path("sendtransfer/{sendInfoId}/{onetimeId}/{sendRequestId}")
                    .resolveTemplate("sendInfoId", sendInfo.getId())
                    .resolveTemplate("onetimeId", CommonUtil.encodeBase64(onetimeId))
                    .resolveTemplate("sendRequestId", CommonUtil.encodeBase64(sendRequestId))
                    ;
            LOG.debug("SendTransfer Async target:{}", target.getUri());
//            Future<Response> f = target.request(MediaType.APPLICATION_JSON).async().get(Response.class);
            //[v2.1.14c1]
            Future<Integer> fRes = target.request(MediaType.APPLICATION_JSON).async().get(new InvocationCallback<Integer>() {
                //非同期処理が終わった時点で呼ばれるコールバックメソッド定義。
                //※非同期処理の結果が戻ってきた時点で既に画面が閉じてViewがDestroy状態になっていても
                //スレッドは残っているためこの処理は呼出される。close()することでスレッドが閉じる。
                @Override
                public void completed(Integer res) {
                    try {
                        client.close();
                        LOG.debug("#SendTransfer Async Completed:{}", res);
                    } catch (Exception e) {}
                }
                @Override
                public void failed(Throwable err) {
                    try {
                        client.close();                            
                        LOG.debug("#SendTransfer Async Failed.  msg:{}", err.getMessage());
                    } catch (Exception e) {}
                }
            });
            //[v2.1.14c2]
            try {
                //非同期呼出し結果を少し待つ(2秒）ことで、呼出失敗を検知する。
                Integer res = fRes.get(2000, TimeUnit.MILLISECONDS);                    
                //結果が返ってきた場合、短時間で非同期処理が終了した結果なので、そのまま抜ける（completed()でclose済み）
                LOG.debug("#SendTransfer Get Completed:{}", res );
            } catch (TimeoutException e) {
                //TimeoutExceptionの場合
                //非同期処理中で結果が返ってこない状態なのでそのまま抜ける.
            } catch (Exception  e) {
                //それ以外の例外： 呼出し失敗（URL異常の[javax.ws.rs.NotFoundException]等）なので、throwして失敗処理をする。
                try {
                    client.close(); //failed()でcloseしているはずだが、念の為closeする。                    
                } catch (Exception e2) {}
                throw e;
            }
            
            //結果
            isExecResultOK = true;
        } catch (Exception e) {
            LOG.error("ファイル送信処理失敗。", e);
            
            //エラー時処理を呼び出し、sendInfoが送信中から失敗になるように修正（例外はスローされない）
            sendTransferLogic.setSendTransferExecError(sendInfo.getId(), onetimeId, sendRequestId, false );
            
            ///ファイル送信処理に失敗しました。
            errMsg = itemHelper.findDispMessageStr(ERR_FILE_SEND_TRANSFER, funcId);
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
            return;
        }

        try {
            // アドレス帳登録・更新
            insertOrUpdateDestAddressBook();
        } catch (Exception e) {
            // 実害なし＆送信自体は成功しているのでログだけはいて正常終了とみなす
            LOG.error("アドレス帳登録・更新失敗。", e);
        }
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));        
    }

    /**
     * 承認依頼実行
     *
     * @param actionEvent
     * @throws java.lang.Exception
     */
    public void execActionApprovalRequest(ActionEvent actionEvent) throws Exception {
        FacesContext context = FacesContext.getCurrentInstance();

        String mailAddressApprovals = "";

        String errItem = "";
        String errMsg = "";
        
        //ファイル名文字数オーバーチェック【v2.1.13】
        try {
            List<String> errMsgs = getFileNameLengthOverMessages();
            if(errMsgs != null && !errMsgs.isEmpty()){
                //ファイル名文字数オーバーあり
                for(String eMsg: errMsgs){
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, eMsg));                    
                }
                return;
            }
        } catch (Exception e) {
            LOG.error("ファイル名文字数オーバーメッセージ取得に失敗しました。",e);
            ///承認依頼処理に失敗しました。
            errMsg = itemHelper.findDispMessageStr(ERR_APPROVAL_REQUEST, funcId);
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
            return;
        }
        
        try {
            // 件名生成
            //（件名はダウンロードファイル名になるので、承認依頼の場合でも、「%sさんからファイルが届いています。」）
            String _subject = itemHelper.findDispMessageStr(INF_MESSAGE_GOT_MAIL, funcId, commonBean.getLoginName());

            // 送信情報をセットする
            setExecActionSendInfo(_subject);

            // setExecActionSendInfoでセットされない項目値をセット
            String separator = "";
            boolean approvalFlg = false;        ///承認待ちフラグ
            int approvalsRequiredCount = 0;     ///承認必要回数
            int approvalsDoneCount = 0;         ///承認済み回数
            for (InternetAddress ia : mailApproveList) {
                ///(承認待ちフラグ)承認者の入力がある場合、true
                approvalFlg = true;
                ///承認必要回数
                approvalsRequiredCount++;

                if (StringUtils.isEmpty(ia.getPersonal())) {
                    ///承認者メールアドレス
                    mailAddressApprovals += separator + ia.getAddress();
                } else {
                    mailAddressApprovals += separator + ia.toString();
                }
                separator = ", ";
            }
            if (!sendInfo.isApprovalsRequiredAllFlg()) {
                ///承認全必要フラグ＝trueでない場合、approvalsRequiredCount＝1
                ///(「１人が承認したらファイルを送信」が選択されている場合)
                approvalsRequiredCount = 1;
            }
            sendInfo.setApprovalFlg(approvalFlg);                       ///承認待ちフラグ
            sendInfo.setApprovalsRequiredCount(approvalsRequiredCount); ///承認必要回数
            sendInfo.setApprovalsDoneCount(approvalsDoneCount);         ///承認済み回数

        } catch (FileNotFoundException e){
            LOG.error("承認依頼情報生成処理に失敗しました。(FileIO)", e);
            ///承認依頼処理に失敗しました。
            errMsg = itemHelper.findDispMessageStr(ERR_APPROVAL_REQUEST, funcId);
        } catch (Exception e) {
            LOG.error("承認依頼情報生成処理に失敗しました。", e);
            ///ファイル送信情報生成処理に失敗しました。
            errMsg = itemHelper.findDispMessageStr(ERR_APPROVAL_REQUEST_CREATE, funcId);
        }
        if(!errMsg.isEmpty()){
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
            return;            
        }
        
        try {
            //DB更新（SendInfo)
            sendTransferLogic.writeSendInfo(sendInfo);
            
            //ワンタイムログインは承認者なし（つまり、承認者ありということはワンタイムログインではない）なので
            //ワンタイムに対する処理は不要
            
//            //TODO :::UT:::Start v2.1.13_A001 例外発生
//            if(sendInfo.getContent().contains("#UT#RestCall Exception#"))
//                throw new RuntimeException("#UT# RestFul呼び出しで例外");    //例外発生
//            //TODO :::UT:::End
            
            //-----------------------------------------------------
            //承認依頼実行（ReceiveInfo書込み、承認依頼メール送信等）
            //RESTによる非同期実行
            //-----------------------------------------------------
            Client client = ClientBuilder.newClient();
            WebTarget target = client.target(CommonUtil.createLocalUrl(commonBean.getRegionId(), CommonUtil.isSectionLgwan()) + "webresources/sendtransfer");
            target = target.path("approvalRequest/{sendInfoId}/{mailAddressApprovals}")
                    .resolveTemplate("sendInfoId", sendInfo.getId())
                    .resolveTemplate("mailAddressApprovals", CommonUtil.encodeBase64(mailAddressApprovals));
            LOG.debug("SendTransferApprovalRequest Async target:{}", target.getUri());
            //非同期処理でも確実にCloseされるように修正[v2.1.14c]
            Future<Integer> fRes = target.request(MediaType.APPLICATION_JSON).async().get(new InvocationCallback<Integer>() {
                //非同期処理が終わった時点で呼ばれるコールバックメソッド定義。
                //※非同期処理の結果が戻ってきた時点で既に画面が閉じてViewがDestroy状態になっていても
                //スレッドは残っているためこの処理は呼出される。close()することでスレッドが閉じる。
                @Override
                public void completed(Integer res) {
                    try {
                        client.close();
                        LOG.debug("#SendTransferApprovalRequest Async Completed:{}", res);
                    } catch (Exception e) {}
                }
                @Override
                public void failed(Throwable err) {
                    try {
                        client.close();                            
                        LOG.debug("#SendTransferApprovalRequest Async Failed.  msg:{}", err.getMessage());
                    } catch (Exception e) {}
                }
            });
            //[v2.1.14c2]
            try {
                //非同期呼出し結果を少し待つ(2秒）ことで、呼出失敗を検知する。
                Integer res = fRes.get(2000, TimeUnit.MILLISECONDS);                    
                //結果が返ってきた場合、短時間で非同期処理が終了した結果なので、そのまま抜ける（completed()でclose済み）
                LOG.debug("#SendTransferApprovalRequest Get Completed:{}", res);
            } catch (TimeoutException e) {
                //TimeoutExceptionの場合
                //非同期処理中で結果が返ってこない状態なのでそのまま抜ける.
            } catch (Exception  e) {
                //それ以外の例外： 呼出し失敗（URL異常の[javax.ws.rs.NotFoundException]等）なので、throwして失敗処理をする。
                try {
                    client.close(); //failed()でcloseしているはずだが、念の為closeする。                    
                } catch (Exception e2) {}
                throw e;
            }
           
            //結果
            isExecResultOK = true;
        } catch (Exception e) {
            LOG.error("承認処理失敗。", e);
            
            //エラー時処理を呼び出し、sendInfoが送信中から失敗になるように修正（例外はスローされない）
            sendTransferLogic.setSendTransferExecError(sendInfo.getId(), "", "", true);
            
            ///承認依頼処理に失敗しました。
            errMsg = itemHelper.findDispMessageStr(ERR_APPROVAL_REQUEST, funcId);
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
            //return;
        }

        try {
            // アドレス帳登録・更新
            insertOrUpdateDestAddressBook();
        } catch (Exception e) {
            // 実害なし＆送信自体は成功しているのでログだけはいて正常終了とみなす
            LOG.error("アドレス帳登録・更新失敗。", e);
        }
    }

    /**
     * ワンタイムでファイル送信実行後の送信詳細遷移
     *
     * @return 遷移先
     */
    public String getActionSendHistoryDetail() {
        return "sendHistoryDetail";
    }

    /**
     * 画面遷移時のBeanへのデータ受渡し
     *
     */
    protected void setSendFileBean() {
        sendFileBean.setContinueFlg(true);
        sendFileBean.setUuid(uuid);
        sendFileBean.setSendInfo(sendInfo);
        sendFileBean.setMailToList(mailToList);
        sendFileBean.setFileInfoList(fileInfoList);
        sendFileBean.setDestination(destination);
        sendFileBean.setMailApproveList(mailApproveList);
    }

    /**
     * ファイル送信実行後のポータル遷移
     *
     * @return 遷移先
     */
    public String getActionPortal() {
        if (isExecResultOK && this.isLoginFlg()) {
            sendFileBean.clear();
            return "portal";
        }
        return "";
    }

    /**
     * ポータルに戻る遷移先画面取得
     *
     * @return 遷移先
     */
    public String getActionRevPortal() {
        return "portal";
    }

    /**
     * contextメッセージ追加（ファイルアップロードエラー）
     *
     */
    public void addUpldFileError() {
        FacesContext context = FacesContext.getCurrentInstance();

        //FacesContextからパラメータ（エラーファイル名）を取得
        String errFname = context.getExternalContext().getRequestParameterMap().get("errFileName");
        String[] errFnameLst = errFname.split(",", 0);

        //"ファイルアップロードに失敗しました。(%s)"
        //String err_upload = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.UPLOAD_FILE_FAILED, funcId);
        //String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.FILES_REQUIRED, funcId);
        String errItem = getFacesMessageSummary(getItemCaption("dspFile"));    ///"ファイル：　";
        for (String fname : errFnameLst) {
            //String errMsg = err_upload + "(" + fname + ")";
            String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.UPLOAD_FILE_FAILED, funcId, fname);
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    errItem, errMsg));
        }
    }

    /**
     * 表示用-パス通知有無を取得
     *
     * @return 表示用-パス通知有無
     */
    public String getDspPasswordNotice() {
        return getDspPasswordNotice(sendInfo.isPassAuto(), sendInfo.isPassNotice());
    }

    /**
     * アドレス帳登録・更新
     */
    public void insertOrUpdateDestAddressBook() {
        // 送信先が自分宛の場合、あるいはワンタイムログインでファイル送信を行う場合、アドレス帳登録・更新は行わない
        if (destination.equals(DESTINATION_OWN) || !commonBean.isLoginFlg()) {
            return;
        }
        List<DestAddressBook> destAddressInfoDatas = destAddressBookService.findDestAddressBookByUid(commonBean.getUserId());
        if (destAddressInfoDatas.isEmpty()) {
            for (InternetAddress ia : mailToList) {
                // 新規登録
                insertDestAddressBook(ia);
            }
        } else {
            // mailToListとDBを突き合わせる
            // 「ユーザID」「送信先メールアドレス」「送信先名称」の組み合わせにて
            //   1件も存在しない：新規登録
            //   存在する       ：使用回数を+1
            for (InternetAddress ia : mailToList) {
                boolean exist = false;
                for (DestAddressBook destAddressInfo : destAddressInfoDatas) {
                    if (existDestAddressBook(ia, destAddressInfo)) {
                        exist = true;
                        // 使用回数インクリメント
                        long useCount = destAddressInfo.getUseCount() + 1;
                        destAddressInfo.setUseCount(useCount);
                        // 更新
                        destAddressBookService.edit(destAddressInfo);
//                        break;
                    }
                }
                if (!exist) {
                    // 新規登録
                    insertDestAddressBook(ia);
                }
            }
        }
    }

    /**
     * アドレス帳登録
     *
     * @param ia
     */
    public void insertDestAddressBook(InternetAddress ia) {
        DestAddressBook destAddressBook = new DestAddressBook();
        destAddressBook.setId(IdUtil.createUUID());
        destAddressBook.setPersonalName(ia.getPersonal());
        destAddressBook.setToAddress(ia.getAddress());
        destAddressBook.setUid(commonBean.getUserId());
        destAddressBook.setUseCount(DESTADDRESSBOOK_USECOUNT);
        // 新規登録
        destAddressBookService.create(destAddressBook);
    }

    /**
     * DBとmailToListに同一の「送信先メールアドレス」・「送信先名称」が存在するかどうか判定
     *
     * @param ia
     * @param destAddressInfo
     * @return true:存在する / false:存在しない
     */
    public boolean existDestAddressBook(InternetAddress ia, DestAddressBook destAddressInfo) {
        return StringUtils.equals(ia.getAddress(), destAddressInfo.getToAddress()) && StringUtils.equals(ia.getPersonal(), destAddressInfo.getPersonalName());
    }

    /**
     * メールアドレス欄オートコンプリート
     *
     * @param inputAddress メールアドレス欄に入力された文字列(都度入ってくる)
     * @return addressList
     */
    public List<String> autoCompleteAddress(String inputAddress) {
        List<String> addressList = new ArrayList<>();

        FacesContext context = FacesContext.getCurrentInstance();
        // datalistの行インデックスを取得
        int rowIndex = context.getApplication().evaluateExpressionGet(context, "#{rowIndex}", int.class);

        String uid = commonBean.getUserId();
        String personal = mailToList.get(rowIndex).getPersonal();

        if (StringUtils.isEmpty(personal)) {
            // 「ユーザID」と「メールアドレス欄に入力された文字列」でメールアドレスを検索
            List<DestAddressBook> destAddressInfoDatas = destAddressBookService.findDestAddressBookByToAddressOnly(uid, inputAddress);
            for (DestAddressBook destAddressInfo : destAddressInfoDatas) {
                if (addressList.contains(destAddressInfo.getToAddress())) {
                    continue;
                }
                addressList.add(destAddressInfo.getToAddress());
            }
        } else {
            // 「ユーザID」と「宛先名称」と「メールアドレス欄に入力された文字列」でメールアドレスを検索
            List<DestAddressBook> destAddressInfoDatas = destAddressBookService.findDestAddressBookByToAddress(uid, personal, inputAddress);
            for (DestAddressBook destAddressInfo : destAddressInfoDatas) {
                if (addressList.contains(destAddressInfo.getToAddress())) {
                    continue;
                }
                addressList.add(destAddressInfo.getToAddress());
            }
        }
        return addressList;
    }

    /**
     * 送信先名称欄オートコンプリート
     *
     * @param inputPersonal 送信先名称欄に入力された文字列(都度入ってくる)
     * @return personalList
     */
    public List<String> autoCompletePersonalName(String inputPersonal) {
        List<String> personalList = new ArrayList<>();

        FacesContext context = FacesContext.getCurrentInstance();
        // datalistの行インデックスを取得
        int rowIndex = context.getApplication().evaluateExpressionGet(context, "#{rowIndex}", int.class);

        String uid = commonBean.getUserId();
        String toAddress = mailToList.get(rowIndex).getAddress();

        if (StringUtils.isEmpty(toAddress)) {
            // 「ユーザID」と「送信先名称欄に入力された文字列」で宛先名称を検索
            List<DestAddressBook> destAddressInfoDatas = destAddressBookService.findDestAddressBookByPersonalOnly(uid, inputPersonal);
            for (DestAddressBook destAddressInfo : destAddressInfoDatas) {
                if (personalList.contains(destAddressInfo.getPersonalName())) {
                    continue;
                }
                personalList.add(destAddressInfo.getPersonalName());
            }
        } else {
            // 「ユーザID」と「宛先メールアドレス」と「送信先名称欄に入力された文字列」で宛先名称を検索
            List<DestAddressBook> destAddressInfoDatas = destAddressBookService.findDestAddressBookByPersonal(uid, toAddress, inputPersonal);
            for (DestAddressBook destAddressInfo : destAddressInfoDatas) {
                if (personalList.contains(destAddressInfo.getPersonalName())) {
                    continue;
                }
                personalList.add(destAddressInfo.getPersonalName());
            }
        }
        return personalList;
    }

    /*
     * メール全件削除
     *
     */
    public void deleteAllMailToAction() {
        if (destination.equals(DESTINATION_OWN)) {
            mailToList.clear();
            if (mailToList.isEmpty()) {
                try {
                    mailToList.add(new InternetAddress("", ""));
                } catch (UnsupportedEncodingException ex) {
                    java.util.logging.Logger.getLogger(SendTransferView.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    /**
     * ＡＰサーバ設置場所の判定
     *
     * @return ture: LGWAN側, false: Internet側
     */
    public boolean isSectionLgwan() {
        return CommonUtil.isSectionLgwan();
    }

    /**
     * 承認者メールアドレスInputTextのclass（ノーマル/エラー）を返す
     *
     * @param addr
     * @return class名(ノーマル/エラー）
     */
    public String getMailApproveClassName(InternetAddress addr) {
        for (InternetAddress mApprove : errMailApproveList) {
            if (mApprove == addr) {
                //エラー対象
                return " ui-state-error";
            }
        }
        return "";
    }

    /**
     * アドレスから、承認者メールエラーリストindexを取得
     *
     * @param addr
     * @return
     */
    private int getMailApproveAddressIndex(InternetAddress addr) {
        int index = -1;
        for (int i = 0; i < mailApproveList.size(); i++) {
            if (mailApproveList.get(i) == addr) {
                index = i;
                break;
            }
        }
        return index;
    }

    /**
     * メールApprove削除
     *
     * @param approve
     */
    public void deleteMailApproveAction(InternetAddress approve) {
        int index = getMailApproveAddressIndex(approve);
        if (index >= 0) {
            mailApproveList.remove(index);
        }
        if (mailApproveList.isEmpty()) {
            try {
                mailApproveList.add(new InternetAddress("", ""));
            } catch (UnsupportedEncodingException ex) {
                java.util.logging.Logger.getLogger(SendCommonView.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * メールApprove追加
     *
     * @param approve
     */
    public void addMailApproveAction(InternetAddress approve) {
        try {
            //既定の承認者メールアドレス数を超えての追加はできない
            if (mailApproveList.size() >= approverAddressesCountLimit) {
                return;
            }

            int index = getMailApproveAddressIndex(approve);
            if (index >= 0) {
                mailApproveList.add(index + 1, new InternetAddress("", ""));
            } else {
                mailApproveList.add(new InternetAddress("", ""));
            }
        } catch (UnsupportedEncodingException ex) {
            java.util.logging.Logger.getLogger(SendCommonView.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * 承認者メールアドレスリスト入力チェック
     *
     * @param itemCaption
     * @return チェック結果
     */
    private boolean checkInputMailApproveList(String itemCaption) {
        boolean bret = true;

        //エラーリストクリア
        errMailApproveList.clear();

        String errMsg;
        String errItem;
        FacesContext context = FacesContext.getCurrentInstance();
        try {
            //承認者メールアドレスと名称が両方未入力は削除する
            for (int i = mailApproveList.size() - 1; i >= 0; i--) {
                InternetAddress ia = mailApproveList.get(i);
                ia.setAddress(ia.getAddress().trim());
                ia.setPersonal(ia.getPersonal().trim());

                if (ia.getAddress().isEmpty() && ia.getPersonal().isEmpty()) {
                    deleteMailApproveAction(ia);
                }
            }

            Integer index = 0;
            for (InternetAddress mailItem : mailApproveList) {
                index++;
                //承認者メールアドレス入力チェック
                //（必須チェック不要の為、checkInputMailは使用しない。）
                String address = getTrimString(mailItem.getAddress());
                String person = getTrimString(mailItem.getPersonal());
                if (address.isEmpty() && person.isEmpty()) {
                    ///未入力OKなので、何もしない
                    try {
                        // [2017/12/14-2018/01/15]承認者選択可能時、承認者必須フラグがあれば承認者入力チェックを行う
                        if ( isApprovalsFlg && itemHelper.find(Item.APPROVER_REQUIRED, funcId).getValue().equalsIgnoreCase("true") ) {
                            errItem = itemCaption + index + "：　";
                            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.MAIL_ADDRESS_REQUIRED, funcId);
                            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
                            bret = false;
                            errMailApproveList.add(mailItem);       //エラー表示のためエラーのInternetAddressをエラーリストに追加
                        }
                    } catch(NoResultException e) {}
                } else if (address.isEmpty() && !person.isEmpty()) {
                    ///アドレス未入力で、名前だけ入力されている場合、"メールアドレスが未入力です"
                    errItem = itemCaption + index + "：　";
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.MAIL_ADDRESS_REQUIRED, funcId);
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
                    bret = false;
                    errMailApproveList.add(mailItem);       //エラー表示のためエラーのInternetAddressをエラーリストに追加
                } else if (!CommonUtil.isValidEmail(address)) {
                    // "正しいメールアドレスではありません。";
                    errItem = itemCaption + index + "：　";
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.MAIL_ADDRESS_INVALID, funcId);
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
                    bret = false;
                    errMailApproveList.add(mailItem);       //エラー表示のためエラーのInternetAddressをエラーリストに追加
                } else if (!mailManager.isMyDomain(address)) {
                    // "庁内で利用しているメールアドレスを指定してください。"
                    errItem = itemCaption + index + "：　";
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_MAIL_DOMAIN_APPROVAL, funcId);
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
                    bret = false;
                    errMailApproveList.add(mailItem);       //エラー表示のためエラーのInternetAddressをエラーリストに追加
                } else if (address.equals(commonBean.getMailAddress())) {
                    // "送信者のメールアドレスは指定できません。"
                    errItem = itemCaption + index + "：　";
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_MAIL_ADDRESS_OWN, funcId);
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
                    bret = false;
                    errMailApproveList.add(mailItem);       //エラー表示のためエラーのInternetAddressをエラーリストに追加
                }
            }
        } catch (UnsupportedEncodingException ex) {
            java.util.logging.Logger.getLogger(SendCommonView.class.getName()).log(Level.SEVERE, null, ex);
        }

        return bret;
    }
       
    //【v2.1.13】
    /**
     * ファイル名文字数オーバー有無エラーメッセージ取得
     * @return 文字数超過ありの場合にエラーメッセージを返す。文字数超過なしの場合はnullを返す。
     */
    private List<String> getFileNameLengthOverMessages() {
        if(fileInfoList==null)
            return null;

        List<String> resMsgs = new ArrayList<>();

        //文字数超過ファイル名取得
        String errFnames = "";  //文字数オーバーファイル名（複数の場合は","+改行を先頭につけて連結）
        String errFnamesZip = ""; //文字数オーバーZIPファイル名（複数の場合は","+改行を先頭につけて連結）        
        for (FileInfoBean info : fileInfoList) {
//            //TODO :::UT:::Start v2.1.13_A005 例外発生
//            if(info.getFileName().contains("#UT#getFileNameLengthOverMessagesException#"))
//                throw new RuntimeException("#UT#ファイル名文字数オーバー有無エラーメッセージ取得");    //例外発生
//            //TODO :::UT:::End
            if(info.isError()){
                //対象外ファイルと判定されているものは送信しないので文字数オーバーエラーの対象外
                continue;
            }
            switch(info.getFileErrorReason()){
                case FileNameLengthOver:
                    //文字数超過ファイル
                    if(errFnames.length()>0){
                        errFnames += ",\n";
                    }
                    errFnames += info.getFileName();
                    break;
                case FileNameLengthOverInZip:
                    //文字数超過ファイル含むzipファイル
                    if(errFnamesZip.length()>0){
                        errFnamesZip += ",\n";
                    }
                    errFnamesZip += info.getFileName();
                    break;
                default:
            }
        }
        if(errFnames.length() > 0){
            //文字数超過ファイルあり
            //ログ出力
            LOG.error("#!ファイル名が長すぎるためファイル送信に失敗しました。 (RegionId:{}, UserId:{}, OnetimeId:{}, FileName：{})"
                ,commonBean.getRegionId(), commonBean.getUserId(),  commonBean.getOnetimeId(), errFnames);            
            //エラーメッセージ「ファイル名が長すぎるファイルがあります。ファイル名は８０文字未満として下さい。」
            resMsgs.add(itemHelper.findDispMessageStr(ERR_FILE_SEND_TRANSFER_FILENAME_LONG, funcId));            
        }
        if(errFnamesZip.length() > 0){
            //文字数超過ファイル含むzipファイルあり
            //ログ出力
            LOG.error("#!ファイル名が長すぎるファイルを含むZIPファイルがあるためファイル送信に失敗しました。 (RegionId:{}, UserId:{}, OnetimeId:{}, zipFileName：{})"
                ,commonBean.getRegionId(), commonBean.getUserId(),  commonBean.getOnetimeId(), errFnamesZip);
            //エラーメッセージ「ファイル名が長すぎるファイルを含むZIPファイルがあります。ファイル名は８０文字未満として下さい。」
            resMsgs.add(itemHelper.findDispMessageStr(ERR_FILE_SEND_TRANSFER_ZIPFILENAME_LONG, funcId));            
        }        
        return resMsgs;
    }

    //今後、同様のテストをする可能性を考慮して残しておく
    //[v2.1.14c2]    //非同期処理スレッド対応テスト用ドライバ
    ////呼出たいタイミングの箇所に以下を挿入して使用する
    //if(sendInfo.getContent().toUpperCase().contains("#ASYNCTEST#")){
    //    testRESTfulAsync(sendInfo.getContent());
    //    return false;
    //}
    // 使い方：ファイル送信画面の通信欄に"#asynctest#"を含む文字列を入力して実行。
    //         スペースで区切って引数を指定。
    //        ("L="ループ回数、"S="スリープ時間、"I="ループインターバル時間、"M="ログに付加される文字、"N="RESTful名(呼出失敗テスト用））
    //         例） #asynctest# S=300000 I=1000 L=5  (←5分(Sleep)の非同期処理を、1秒間隔で3回ループ実行）
    public boolean testRESTfulAsync(String contents){
        try {
            final String UTKEY = "#ASYNCTEST#";
            final String RESTNAME = "testAsyncS";

            //UT判定
            if(!VerifyUtil.chkUTKey(contents, UTKEY, null))
                return false;
            
            //ループカウント
            int loopCount = VerifyUtil.getUTArgValueInt(contents, UTKEY, "L", 1);  //Default:1
            //スリープ
            int sleepLen = VerifyUtil.getUTArgValueInt(contents, UTKEY, "S", 1000);    //Default:1000
            //インターバル
            int interval = VerifyUtil.getUTArgValueInt(contents, UTKEY, "I", 10);    //Default:10
            //メモ
            String memo = VerifyUtil.getUTArgValue(contents, UTKEY, "M", " ");
            //排他ID
            String exId = VerifyUtil.getUTArgValue(contents, UTKEY, "E", " ");
            //RESTful名
            String restName = VerifyUtil.getUTArgValue(contents, UTKEY, "N", RESTNAME);
            //非同期処理とするかどうか
            boolean flgAsync = !VerifyUtil.chkUTKey(contents, UTKEY, "NOASYNC");
            //直後にクローズするか
            boolean flgClose = VerifyUtil.chkUTKey(contents, UTKEY, "CLOSE");
            //直後にCancelするか
            boolean flgCancel = VerifyUtil.chkUTKey(contents, UTKEY, "CANCEL");

            LOG.debug("##[UT]RESTFUL TEST## START({}). 　Loop:{} Interval:{} Seep:{} Memo:[{}] ExID:{} Close:{}",
                    contents, loopCount, interval, sleepLen, memo, exId, flgClose);
            
            long sTime = new Date().getTime();
            String asyncId = "";
            for(int cnt=0; cnt<loopCount; cnt++){
                LOG.debug("##[UT]RESTFUL TEST## Count:{}", cnt+1);

                ClientBuilder cb = ClientBuilder.newBuilder()
//                    .property("jersey.config.client.connectTimeout", 5000)
//                    .property("jersey.config.client.readTimeout", 20000)
                      //＜検証結果＞
                      //・ClientBuilderに対するタイムアウトの指定は何も影響しなかった。
                        ;
                Client client = cb.build()  //タイムアウトをセットしてみる。⇒何も変化なし。
                        .property("http.connection.timeout", 5000) 
                        .property("http.receive.timeout", 8000);
//                        .property("jersey.config.client.connectTimeout", 10000)
//                        .property("jersey.config.client.readTimeout", 30000);
                            //＜検証結果＞
                            //・Clientに対するタイムアウトの指定は何も影響しなかった。
                asyncId = exId;
                if(StringUtils.isBlank(asyncId)){
                    asyncId = memo + String.valueOf(cnt+1);
                }

                WebTarget target = client.target(CommonUtil.createLocalUrl(commonBean.getRegionId(), CommonUtil.isSectionLgwan()) + "webresources/sendtransfer");
                target = target.path( restName + "/{sleep}/{counter}/{memo}/{asyncId}")
                        .resolveTemplate("sleep", sleepLen)
                        .resolveTemplate("counter", cnt+1)
                        .resolveTemplate("memo", CommonUtil.encodeBase64(memo))
                        .resolveTemplate("asyncId", CommonUtil.encodeBase64(asyncId))
                        ;
                if(flgAsync){
                    //非同期処理
                    Future<String> res = target.request(MediaType.APPLICATION_JSON).async().get(new InvocationCallback<String>() {
                       @Override
                       public void completed(String res) {
                           LOG.debug("##[UT]RESTFUL TEST## Async completed. res:{}", res);
                           try {
                               client.close();
                           } catch (Exception e) {
                               LOG.error("##[UT]RESTFUL TEST## Async Error(complete close)", e);
                           }
                       }
                       @Override
                       public void failed(Throwable e) {
                           //非同期処理呼出失敗時
                           LOG.debug("##[UT]RESTFUL TEST## Async failed.   [{}]", e.toString());
                           try {
                               client.close();
                           } catch (Exception ex) {
                               LOG.error("##[UT]RESTFUL TEST## Async Error(failed close)", ex);
                           }
                       }
                    });
                    try {
                        //非同期呼出し結果を少し待つ。
                        String result = res.get((long)interval, TimeUnit.MILLISECONDS);                    
                        //結果が返ってきた場合、短時間で非同期処理が終了した結果なので、そのまま抜ける
                        LOG.debug("##[UT]RESTFUL TEST## Async Get:{}", result);
                    } catch (TimeoutException e) {
                        //例外[java.util.concurrent.TimeoutException]: 非同期処理中で結果が返ってこない場合なので、そのまま抜ける
                        LOG.debug("##[UT]RESTFUL TEST## Async Processing.");
                    } catch (Exception  e) {
                        //それ以外の例外： 呼出し失敗（URL異常等は[javax.ws.rs.NotFoundException]）なので、失敗処理を呼び出す。
                        LOG.error("##[UT]RESTFUL TEST## Async Get Error.  exception:{}", e);
    //                    throw new FssException("非同期処理呼出しエラー");
                    }                    
                   //インターバル調整
                    while(new Date().getTime() - sTime < interval){
                        Thread.sleep(1);
                    }                
                    LOG.debug("##[UT]RESTFUL TEST## isDone:{} isCanceled:{} Memo:[{}] Count:{}", 
                               res.isDone(), res.isCancelled(),  memo, cnt+1);
                    if(flgCancel){
                        try {
                            LOG.debug("##[UT]RESTFUL TEST## Cancel. :{}",  res.cancel(true));
                            //＜検証結果＞
                            //・Cancelしても非同期処理やスレッドの状態に影響なし（引数true/false共）。⇒意味なし。
                        } catch (Exception e) {
                            LOG.error("##[UT]RESTFUL TEST##! res.Cancel Error.", e);
                        }
                    }                
                }else{
                    //同期処理の場合のテスト
//                    String resStr = target.request(MediaType.APPLICATION_JSON).get(String.class);
//                    LOG.debug("##[UT]RESTFUL TEST## Sync  Res:{}", resStr);
                    target.request(MediaType.APPLICATION_JSON).get(String.class);
                    LOG.debug("##[UT]RESTFUL TEST## Sync OK. ");
                   //インターバル調整
                    while(new Date().getTime() - sTime < interval){
                        Thread.sleep(1);
                    }                    
                   //＜検証結果＞
                   //・同期処理はスレッドが増えない。
                }
                sTime = new Date().getTime();
               if(flgClose){
                   LOG.debug("##[UT]RESTFUL TEST## Close."); 
                   client.close();
                   //＜検証結果＞
                   //・インターバルを短くして非同期処理が呼出される前にcloseすると、非同期処理は実行されない。スレッドは消える。⇒意味なし。
                   //・非同期処理が呼出されてからcloseしても非同期処理は継続されスレッドは消えない。非同期処理がその時点でリトライされてしまう。⇒二重に処理されてしまうので最悪のパターン。
               }
            }                
        } catch (Throwable e) {
            LOG.error("##[UT]RESTFUL TEST## !ERROR!", e);
        }
        LOG.debug("##[UT]RESTFUL TEST## END({}).", contents);
        return true;
    }    
}
