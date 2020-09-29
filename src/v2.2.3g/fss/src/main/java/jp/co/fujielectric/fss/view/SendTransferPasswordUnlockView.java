package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.internet.InternetAddress;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.DataTableBean;
import jp.co.fujielectric.fss.data.FileInfoBean;
import jp.co.fujielectric.fss.data.HistoryBean;
import jp.co.fujielectric.fss.data.PasswordUnlockBean;
import jp.co.fujielectric.fss.entity.DecryptFile;
import jp.co.fujielectric.fss.entity.OnceUser;
import jp.co.fujielectric.fss.entity.ReceiveFile;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.logic.HistoryLogic;
import jp.co.fujielectric.fss.service.OnceUserService;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.context.RequestContext;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNode;

/**
 * ファイル送信のパスワード解除ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class SendTransferPasswordUnlockView extends HistoryCommonView implements Serializable {

    @Inject
    @Getter
    @Setter
    private HistoryBean receiveHistoryBean;
    @Getter
    @Setter
    private DataTableBean save_historyDataTable;
    @Getter
    @Setter
    private boolean loginFlg;

    private ReceiveInfo receiveInfo;
    @Inject
    private ReceiveInfoService receiveInfoService;
    @Inject
    private DataTableBean receiveHistoryDataTableBean;

    @Getter
    @Setter
    private TreeNode fileTreeNode = new DefaultTreeNode();

    // 画面表示メンバ
    @Getter
    @Setter
    private String receiveInfoId;
    @Getter
    @Setter
    private String password;
    @Getter
    @Setter
    private String addressFrom;
    @Getter
    @Setter
    private String addressTo;
    @Getter
    @Setter
    private String addressCc;
    @Getter
    @Setter
    private String subject;
    @Getter
    @Setter
    private String mailText;

    @Getter
    @Setter
    private boolean dialogClosable;
    @Getter
    @Setter
    private String dialogHeader;
    @Getter
    @Setter
    private String dialogMessage;

    @Getter
    protected long passwordCharMax;             //パスワード文字数Max

    @Inject
    OnceUserService onceUserService;

    //dispMessage.itemkey
    /**
     * パスワードを解除しました。
     */
    private final String INF_DIALOG_TITLE_UNLOCED = "infDialogTitleUnloced";
    /**
     * 手続きが完了しました。
     */
    private final String INF_DIALOG_TITLE_COMPLETE = "infDialogTitleComplete";
    /**
     * 失敗しました。
     */
    private final String INF_DIALOG_TITLE_FAILED = "infDialogTitleFailed";
    /**
     * パスワードが解除されていないファイルが残っています。[閉じる]ボタンを押してください。
     */
    private final String INF_DIALOG_TEXT_UNLOCKED = "infDialogTextUnlocked";
    /**
     * ファイルのパスワード解除が完了しました。[閉じる]ボタンを押してください。
     */
    private final String INF_DIALOG_TEXT_UNLOCKEDALL = "infDialogTextUnlockedAll";
    /**
     * パスワードが解除されていないファイルが残っています。 もう一度解除する場合は、［パスワード入力を続ける］ボタンを押してください。 パスワードが解除されていないファイルが残った状態で先に進みたい場合は、[他のパスワードを解除しない]ボタンを押してください。（後ほどダウンロード用ＵＲＬ通知メールとダウンロード用パスワード通知メールが送られます。）
     */
    private final String INF_DIALOG_TEXT_SANITIZED_UNLOCKED = "infDialogTextSanitizedUnlocked";
    /**
     * ファイルのパスワード解除が完了しました。［次へ］ボタンを押すとダウンロード用ＵＲＬ通知メールとダウンロード用パスワード通知メールが後ほど送られます。
     */
    private final String INF_DIALOG_TEXT_SANITIZED_UNLOCKEDALL = "infDialogTextSanitizedUnlockedAll";
    /**
     * 手続きが完了しました。画面を閉じてください。
     */
    private final String INF_DIALOG_TEXT_COMPLETE = "infDialogTextComplete";
    /**
     * 失敗しました。
     */
    private final String INF_DIALOG_TEXTFAILED = "infDialogTextFailed";
    /**
     * 解除されました
     */
    private final String INF_LIST_MESSAGE_UNLOCKED = "infListMessageUnlocked";
    /**
     * 保護されています
     */
    private final String INF_LIST_MESSAGE_PROTECTED = "infListMessageProtected";
    /**
     * 未解除のファイルが%s個残っています
     */
    private final String INF_LIST_MESSAGE_LOCKEDCOUNT = "infListMessageLockedCount";

    //コンストラクタ
    public SendTransferPasswordUnlockView() {
        funcId = "sendTransferPasswordUnlock";
    }

    /**
     * 初期処理
     */
    @Override
    public void initFunc() {
        // 受信詳細から選択された受信情報ID
        String uid = (String) FacesContext
                .getCurrentInstance()
                .getExternalContext()
                .getFlash()
                .get("uid");
        receiveHistoryBean.setUid(uid);

        receiveHistoryDataTableBean = (DataTableBean) FacesContext
                .getCurrentInstance()
                .getExternalContext()
                .getFlash()
                .get("receiveHistoryDataTableBean");

        save_historyDataTable = new DataTableBean();
        if (StringUtils.isEmpty(receiveHistoryBean.getUid()) && StringUtils.isEmpty(commonBean.getOnetimeId())) {
            // ※例外対応（ログアウト直後のinitなど）
            receiveInfo = new ReceiveInfo();
            loginFlg = false;
        } else if (StringUtils.isEmpty(receiveHistoryBean.getUid())) {
            // ワンタイムユーザ情報からsendIdを取得し、メール管理クラスにセット
            OnceUser ou = onceUserService.find(commonBean.getOnetimeId());
            if (ou == null) {
                return;     // 想定外
            }
            // 受信情報を取得
            receiveInfo = receiveInfoService.findWithRelationTables(ou.getMailId());

            loginFlg = false;
        } else {
            // dataTable情報設定
            if (!receiveHistoryDataTableBean.getReq().equals("")) {
                ///HistoryDataTableBean値の複製
                ///(receiveHistoryDataTableBean→save_historyDataTable：リクエスト＝初期表示)
                historyLogic.cloneHistoryDataTable(receiveHistoryDataTableBean, save_historyDataTable, HistoryLogic.REQ_INIT);
            }
            // 受信情報を取得
            receiveInfo = receiveInfoService.findWithRelationTables(receiveHistoryBean.getUid());

            loginFlg = true;
        }

        // ReceiveHistoryBeanを作成
        receiveHistoryBean = historyLogic.createReceiveHistoryBean(receiveInfo, sysDate);
        receiveHistoryBean.contentLineDispOn();

        initView();

        LOG.debug("initView end");
    }

    public void initView() {
        LOG.debug("SendTransferPasswordUnlockView.init start");

        receiveInfoId = receiveInfo.getId();
        // 画面非表示項目
        // 送信情報を画面項目に設定
        SendInfo sendInfo = receiveInfo.getSendInfo();
        try {
            InternetAddress[] iaList = InternetAddress.parse(sendInfo.getFromAddress());
            String[] addressList = new String[iaList.length];
            int i = 0;
            for (InternetAddress internetAddress : iaList) {
                addressList[i] = internetAddress.toUnicodeString();
            }
            addressFrom = StringUtils.join(addressList, ',');
        } catch (Exception ex) {
            ex.printStackTrace();
            addressFrom = sendInfo.getFromAddress();
        }
        try {
            InternetAddress[] iaList = InternetAddress.parse(sendInfo.getToAddress());
            String[] addressList = new String[iaList.length];
            int i = 0;
            for (InternetAddress internetAddress : iaList) {
                addressList[i] = internetAddress.toUnicodeString();
            }
            addressTo = StringUtils.join(addressList, ',');
        } catch (Exception ex) {
            ex.printStackTrace();
            addressTo = sendInfo.getToAddress();
        }
        try {
            InternetAddress[] iaList = InternetAddress.parse(sendInfo.getCcAddress());
            String[] addressList = new String[iaList.length];
            int i = 0;
            for (InternetAddress internetAddress : iaList) {
                addressList[i] = internetAddress.toUnicodeString();
            }
            addressCc = StringUtils.join(addressList, ',');
        } catch (Exception ex) {
            ex.printStackTrace();
            addressCc = sendInfo.getCcAddress();
        }
        subject = sendInfo.getSubject();
        mailText = sendInfo.getContent();

        // ファイル一覧の更新
        refleshFileList(receiveInfo);

        LOG.debug("SendTransferPasswordUnlockView.init end");
    }

    /**
     * マスタ設定値からの変数初期化
     *
     */
    @Override
    protected void initItems() {
        //パスワード文字数Max
        passwordCharMax = CommonUtil.getPasswordCharMax(itemHelper, funcId);
    }

    /**
     * ファイル一覧の更新します。
     *
     * @param receiveInfo 受信情報
     */
    private void refleshFileList(ReceiveInfo receiveInfo) {
        LOG.debug("SendTransferPasswordUnlockView.refleshFileList start");

        // メッセージ
        //---解除されました。
        String messageUnlocked = itemHelper.findDispMessageStr(INF_LIST_MESSAGE_UNLOCKED, funcId);
        //---保護されています。
        String messageProtected = itemHelper.findDispMessageStr(INF_LIST_MESSAGE_PROTECTED, funcId);

        // 受信ファイル一覧をループ
        FileInfoBean fileInfoBean;
        fileTreeNode = new DefaultTreeNode();
        for (ReceiveFile receiveFile : receiveInfo.getReceiveFiles()) {
            switch (receiveFile.getDecryptFiles().size()) {     // ファイル数で処理分岐
                case 1:     // Zip以外の扱い
                    DecryptFile decryptFile = receiveFile.getDecryptFiles().get(0);
                    fileInfoBean = new FileInfoBean(decryptFile);
                    if (decryptFile.isPasswordFlg()) {
                        if (decryptFile.isDecryptFlg()) {
                            ///解除されました
                            fileInfoBean.setFileMessage(messageUnlocked);
                        } else {
                            ///保護されています
                            fileInfoBean.setFileMessage(messageProtected);
                        }
                    } else {
                        ///"－"
                        fileInfoBean.setFileMessage("－");
                    }
                    fileTreeNode.getChildren().add(new DefaultTreeNode(fileInfoBean));
                    break;
                case 0:     // 無効
                    break;
                default:    // Zip（子供ファイルを含んでいる）
                    // 親Zipの検索
                    DecryptFile decryptFileParent = null;
                    for (DecryptFile _decryptFile : receiveFile.getDecryptFiles()) {
                        if (StringUtils.isEmpty(_decryptFile.getParentId())) {
                            decryptFileParent = _decryptFile;
                            break;
                        }
                    }
                    if (decryptFileParent == null) {
                        continue;    // 想定外
                    }
                    fileInfoBean = new FileInfoBean(decryptFileParent);
                    TreeNode zipTreeNode = new DefaultTreeNode(fileInfoBean);
                    if (!decryptFileParent.isPasswordFlg() || decryptFileParent.isDecryptFlg()) {
                        // 子ファイルのパスワード付き件数を取得
                        int decryptFileCount = 0;
                        int passwordFileCount = 0;
                        for (DecryptFile _decryptFile : receiveFile.getDecryptFiles()) {
                            if (_decryptFile.isPasswordFlg() && _decryptFile.getParentId().length() > 0) {
                                passwordFileCount++;
                                if (_decryptFile.isDecryptFlg()) {
                                    decryptFileCount++;
                                } else {
                                    // パスワード付きの内包ファイルを一覧に追加
                                    FileInfoBean fileInfoBeanChild = new FileInfoBean(_decryptFile);
                                    fileInfoBeanChild.setFileMessage(messageProtected);
                                    zipTreeNode.getChildren().add(new DefaultTreeNode(fileInfoBeanChild));
                                }
                            }
                        }
                        if (passwordFileCount == 0) {
                            fileInfoBean.setFileMessage("－");
                        } else if (passwordFileCount == decryptFileCount) {
                            ///---解除されました
                            fileInfoBean.setFileMessage(messageUnlocked);
                        } else {
                            ///---未解除のファイルがxxx個残っています
                            fileInfoBean.setFileMessage(
                                    itemHelper.findDispMessageStr(
                                            INF_LIST_MESSAGE_LOCKEDCOUNT, funcId,
                                            String.valueOf(passwordFileCount - decryptFileCount)
                                    )
                            );
                        }
                    } else {
                        ///保護されています
                        fileInfoBean.setFileMessage(messageProtected);
                    }
                    zipTreeNode.setExpanded(true);  // 初期状態で内包ファイルを一覧表示
                    fileTreeNode.getChildren().add(zipTreeNode);
            }
        }

        LOG.debug("SendTransferPasswordUnlockView.refleshFileList end");
    }

    /**
     * パスワード解除処理を行います。
     */
    public void unlockAction() {
        Client client = null;
        try {
            client = ClientBuilder.newClient();
            WebTarget target = client.target(CommonUtil.createLocalUrl(commonBean.getRegionId(), false) + "webresources/sendtransferpasswordunlock");
            if (!StringUtils.isEmpty(password)) {
                // パスワードをサーバに通知し、パスワード解除結果を取得
                target = target.path("unlock/{receiveInfoId}/{password}")
                        .resolveTemplate("receiveInfoId", receiveInfoId)
                        .resolveTemplate("password", CommonUtil.encodeBase64(password));
            } else {
                // 最新の受信情報を取得
                target = target.path("get/{receiveInfoId}")
                        .resolveTemplate("receiveInfoId", receiveInfoId);
            }
            PasswordUnlockBean passwordUnlockBean = target.request(MediaType.APPLICATION_JSON).get(PasswordUnlockBean.class);
            ReceiveInfo receiveInfo = passwordUnlockBean.getReceiveInfo();

            // ファイル一覧の更新
            refleshFileList(receiveInfo);

            // パスワード解除の残件を取得
            int unlockFileCount = 0;
            for (ReceiveFile receiveFile : receiveInfo.getReceiveFiles()) {
                for (DecryptFile decryptFile : receiveFile.getDecryptFiles()) {
                    if (decryptFile.isPasswordFlg() && !decryptFile.isDecryptFlg()) {
                        unlockFileCount++;
                    }
                }
            }

            // ダイアログ表示設定
            if (passwordUnlockBean.isUnlocked()) {
                dialogHeader = itemHelper.findDispMessageStr(INF_DIALOG_TITLE_UNLOCED, funcId);
            } else {
                dialogHeader = itemHelper.findDispMessageStr(INF_DIALOG_TITLE_FAILED, funcId);
            }
            if (unlockFileCount > 0) {
                // パスワード解除残あり
                dialogClosable = true;
                String key = loginFlg ? INF_DIALOG_TEXT_UNLOCKED : INF_DIALOG_TEXT_SANITIZED_UNLOCKED;
                dialogMessage = itemHelper.findDispMessageStr(key, funcId);
            } else {
                // パスワード解除残なし
                dialogClosable = false;
                String key = loginFlg ? INF_DIALOG_TEXT_UNLOCKEDALL : INF_DIALOG_TEXT_SANITIZED_UNLOCKEDALL;
                dialogMessage = itemHelper.findDispMessageStr(key, funcId);
            }
            RequestContext req_context = RequestContext.getCurrentInstance();
            req_context.addCallbackParam("showDialog", true);
            req_context.addCallbackParam("loginFlg", loginFlg);
        } finally {
            if(client != null)
                client.close();            
        }        
    }

    /**
     * 無害化処理を呼び出します。
     */
    public void sanitizeAction() {
        Client client = null;
        try {
            // 無害化開始処理を呼出す
            client = ClientBuilder.newClient();
            WebTarget target = client.target(CommonUtil.createLocalUrl(commonBean.getRegionId(), false) + "webresources/sendtransferpasswordunlock")
                    .path("sanitize/{receiveInfoId}")
                    .resolveTemplate("receiveInfoId", receiveInfoId);
            PasswordUnlockBean passwordUnlockBean = target.request(MediaType.APPLICATION_JSON).get(PasswordUnlockBean.class);
            if (passwordUnlockBean == null) {       // 想定外
                LOG.error("SendTransferPasswordUnlockView.sanitizeAction DataNotFound(passwordUnlockBean)");
                throw new RuntimeException("SendTransferPasswordUnlockView.sanitizeAction DataNotFound(passwordUnlockBean)");
            }

            //以下、無害化開始処理に移設[v2.2.3a] 
//            receiveInfo = receiveInfoService.find(receiveInfoId);   //最新情報取得
//            receiveInfo.setPasswordUnlockWaitFlg(false);
//            receiveInfo.resetDate();    //日付更新
//            receiveInfoService.edit(receiveInfo);

            // ダイアログ表示設定
            dialogClosable = false;
            ///---手続きが完了しました。
            dialogHeader = itemHelper.findDispMessageStr(INF_DIALOG_TITLE_COMPLETE, funcId);
            ///---手続きが完了しました。\n画面を閉じてください。
            dialogMessage = itemHelper.findDispMessageStr(INF_DIALOG_TEXT_COMPLETE, funcId);
            RequestContext req_context = RequestContext.getCurrentInstance();
            req_context.addCallbackParam("showDialog", true);
        } finally {
            if(client != null)
                client.close();            
        }
    }

    /**
     * ファイル送信実行後のポータル遷移
     *
     * @return 遷移先
     */
    public String getActionConfReceiveHistory() {
        ///HistoryDataTableBean値の複製
        ///(save_historyDataTable→receiveHistoryDataTableBean：リクエスト＝パスワード解除画面から戻る)
        historyLogic.cloneHistoryDataTable(save_historyDataTable, receiveHistoryDataTableBean, HistoryLogic.REQ_BACK_SENDTRANSFERPASSWORDUNLOCK);

        return "receiveHistory";
    }

    /**
     * 戻るボタン
     *
     * @return 遷移先
     */
    public String actBack() {
        LOG.trace("actBack start");

        // 選択-uid
        FacesContext
                .getCurrentInstance()
                .getExternalContext()
                .getFlash()
                .put("uid", receiveHistoryBean.getUid());
        ///HistoryDataTableBean値の複製
        ///(save_historyDataTable→receiveHistoryDataTableBean：リクエスト＝パスワード解除画面から戻る)
        historyLogic.cloneHistoryDataTable(save_historyDataTable, receiveHistoryDataTableBean, HistoryLogic.REQ_BACK_SENDTRANSFERPASSWORDUNLOCK);
        FacesContext
                .getCurrentInstance()
                .getExternalContext()
                .getFlash()
                .put("receiveHistoryDataTableBean", receiveHistoryDataTableBean);

        LOG.trace("actBack end");

        return "receiveHistoryDetail";
    }
}
