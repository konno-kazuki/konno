package jp.co.fujielectric.fss.view;

import java.io.Serializable;
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
import jp.co.fujielectric.fss.data.FileInfoBean;
import jp.co.fujielectric.fss.data.PasswordUnlockBean;
import jp.co.fujielectric.fss.entity.DecryptFile;
import jp.co.fujielectric.fss.entity.OnceUser;
import jp.co.fujielectric.fss.entity.ReceiveFile;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.service.OnceUserService;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.context.RequestContext;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNode;

/**
 * パスワード解除ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class PasswordUnlockView extends CommonView implements Serializable {

    @Inject
    private ReceiveInfoService receiveInfoService;

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
     * パスワードが解除されていないファイルが残っています。もう一度解除する場合は、［戻る］ボタンを押してください。［次へ］ボタンを押すと無害化されたメールが後ほど届きます。
     */
    private final String INF_DIALOG_TEXT_UNLOCKED = "infDialogTextUnlocked";
    /**
     * 添付ファイルのパスワード解除が完了しました。［次へ］ボタンを押すと無害化されたメールが後ほど届きます。
     */
    private final String INF_DIALOG_TEXT_UNLOCKEDALL = "infDialogTextUnlockedAll";
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
    public PasswordUnlockView() {
        funcId = "passwordUnlock";
    }

    /**
     * 初期処理
     */
    public void initView() {
        LOG.info("PasswordUnlockView.init start");

        // ワンタイムユーザ情報からsendIdを取得し、メール管理クラスにセット
        OnceUser onceUser = onceUserService.find(commonBean.getOnetimeId());
        if (onceUser == null) {         // 想定外
            LOG.error("PasswordUnlockView.init DataNotFound(OnceUser)");
            return;
        }

        // 送信情報を受信情報にコピー（受信情報の取得）
        Client client = null;
        ReceiveInfo receiveInfo;
        try {
            client = ClientBuilder.newClient();
            WebTarget target = client.target(CommonUtil.createLocalUrl(commonBean.getRegionId(), false) + "webresources/passwordunlock")
                    .path("init/{envelopeTo}/{sendInfoId}")
                    .resolveTemplate("envelopeTo", CommonUtil.encodeBase64(onceUser.getMailAddress()))
                    .resolveTemplate("sendInfoId", onceUser.getMailId());
            PasswordUnlockBean passwordUnlockBean = target.request(MediaType.APPLICATION_JSON).get(PasswordUnlockBean.class);
            if (passwordUnlockBean == null) {       // 想定外
                LOG.error("PasswordUnlockView.init DataNotFound(rasswordUnlockBean)");
                return;
            }
            receiveInfo = passwordUnlockBean.getReceiveInfo();
            receiveInfoId = receiveInfo.getId();
        } finally {
            if(client != null)
                client.close();            
        }       

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

        LOG.info("PasswordUnlockView.init end");
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
        LOG.info("PasswordUnlockView.refleshFileList start");

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

        LOG.info("PasswordUnlockView.refleshFileList end");
    }

    /**
     * パスワード解除処理を行います。
     */
    public void unlockAction() {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "BEGIN"));
        // パスワード入力有無判定
        // ※必須チェックなし
//        if (StringUtils.isEmpty(password)) {
//            ///---パスワード
//            String itemName = getItemCaption("dspPassword");
//
//            FacesContext context = FacesContext.getCurrentInstance();
//            String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.PASSWORD_REQUIRED, "0");
//            context.addMessage(null,
//                    new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
//            return;
//        }
        Client client = null;
        try {
            client = ClientBuilder.newClient();
            WebTarget target = client.target(CommonUtil.createLocalUrl(commonBean.getRegionId(), false) + "webresources/passwordunlock");
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
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "IN", "target:" + target.getUri()));
            PasswordUnlockBean passwordUnlockBean = target.request(MediaType.APPLICATION_JSON).get(PasswordUnlockBean.class);
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "OUT", "target:" + target.getUri()));
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
                ///---
                ///"パスワードが解除されていないファイルが残っています。\n" +
                ///"もう一度解除する場合は、［戻る］ボタンを押してください。\n" +
                ///"［次へ］ボタンを押すと無害化されたメールが後ほど届きます。";
                dialogMessage = itemHelper.findDispMessageStr(INF_DIALOG_TEXT_UNLOCKED, funcId);
            } else {
                // パスワード解除残なし
                dialogClosable = false;
                ///---
                ///"添付ファイルのパスワード解除が完了しました。\n" +
                ///"［次へ］ボタンを押すと無害化されたメールが後ほど届きます。";
                dialogMessage = itemHelper.findDispMessageStr(INF_DIALOG_TEXT_UNLOCKEDALL, funcId);
            }
            RequestContext req_context = RequestContext.getCurrentInstance();
            req_context.addCallbackParam("showDialog", true);
        } finally {
            if(client != null)
                client.close();            
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "END"));
        }
    }

    /**
     * 無害化処理を呼び出します。
     */
    public void sanitizeAction() {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "BEGIN"));
        Client client = null;
        try {
            // 無害化開始処理を呼出す
            client = ClientBuilder.newClient();
            WebTarget target = client.target(CommonUtil.createLocalUrl(commonBean.getRegionId(), false) + "webresources/passwordunlock")
                    .path("sanitize/{receiveInfoId}")
                    .resolveTemplate("receiveInfoId", receiveInfoId);
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "IN", "target:" + target.getUri()));
            PasswordUnlockBean passwordUnlockBean = target.request(MediaType.APPLICATION_JSON).get(PasswordUnlockBean.class);
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "OUT", "target:" + target.getUri()));
            if (passwordUnlockBean == null) {       // 想定外
                LOG.error("PasswordUnlockView.sanitizeAction DataNotFound(passwordUnlockBean)");
                throw new RuntimeException("PasswordUnlockView.sanitizeAction DataNotFound(passwordUnlockBean)");
            }

            //以下、無害化開始処理に移設[v2.2.3a] 
//            ReceiveInfo receiveInfo = receiveInfoService.find(receiveInfoId);
//            receiveInfo.setPasswordUnlockWaitFlg(false);
//            receiveInfo.resetDate();    //更新日付セット
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
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "END"));
        }
    }
}
