package jp.co.fujielectric.fss.view;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.CommonEnum.FileSanitizeResultKbn;
import jp.co.fujielectric.fss.data.DataTableBean;
import jp.co.fujielectric.fss.data.FileDownloadBean;
import jp.co.fujielectric.fss.data.FileInfoBean;
import jp.co.fujielectric.fss.data.HistoryBean;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.entity.CheckedFile;
import jp.co.fujielectric.fss.entity.DecryptFile;
import jp.co.fujielectric.fss.entity.OnceUser;
import jp.co.fujielectric.fss.entity.ReceiveFile;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendFile;
import jp.co.fujielectric.fss.logic.DeleteReasonFileLogic;
import jp.co.fujielectric.fss.logic.HistoryLogic;
import jp.co.fujielectric.fss.logic.MailManager;
import jp.co.fujielectric.fss.service.CheckedFileService;
import jp.co.fujielectric.fss.service.OnceUserService;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.context.RequestContext;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 * 受信履歴詳細ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class ReceiveHistoryDetailView extends HistoryCommonView implements Serializable {

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

    @Inject
    @Getter
    @Setter
    private HistoryBean receiveHistoryBean;
    @Inject
    private DataTableBean receiveHistoryDataTableBean;

    @Getter
    @Setter
    private List<FileInfoBean> fileInfoList;
    @Getter
    @Setter
    private FileInfoBean selectedRowData;
    @Getter
    @Setter
    private String downloadFileName;
    @Getter
    @Setter
    private StreamedContent file;
    @Getter
    @Setter
    private DataTableBean save_historyDataTable;
    @Getter
    @Setter
    private boolean loginFlg;
    @Getter
    @Setter
    private String dialogHeader;
    @Getter
    @Setter
    private String dialogMessage;

    private ReceiveInfo receiveInfo;

    @Inject
    private OnceUserService onceUserService;

    @Inject
    private ReceiveInfoService receiveInfoService;

    @Inject
    private CheckedFileService checkedFileService;

    @Getter
    private boolean nonSanitizeFlg = false;

    @Getter
    private boolean sendFileCheckFlg = false;

    @Getter
    @Setter
    private int optDownloadFileType = 0;

    private boolean expirationFlg = false;

    //コンストラクタ
    public ReceiveHistoryDetailView() {
        funcId = "receiveHistoryDetail";
    }

    /**
     * 画面毎の初期化
     *
     */
    @Override
    public void initFunc() {

        // 受信一覧から選択された受信情報ID
        String uid = (String) FacesContext
                .getCurrentInstance()
                .getExternalContext()
                .getFlash()
                .get("uid");
        if (!StringUtils.isEmpty(uid)) {
            receiveHistoryBean.setUid(uid);
            receiveHistoryDataTableBean = (DataTableBean) FacesContext
                    .getCurrentInstance()
                    .getExternalContext()
                    .getFlash()
                    .get("receiveHistoryDataTableBean");
        }

        String receiveInfoId = receiveHistoryBean.getUid();
        save_historyDataTable = new DataTableBean();
        if (StringUtils.isEmpty(receiveInfoId) && StringUtils.isEmpty(commonBean.getOnetimeId())) {
            // ※例外対応（ログアウト直後のinitなど）
            receiveInfo = new ReceiveInfo();
            loginFlg = false;
        } else if (StringUtils.isEmpty(receiveInfoId)) {
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
            receiveInfo = receiveInfoService.findWithRelationTables(receiveInfoId);

            // ふるまい検知ファイル情報を取得
            receiveInfo.setCheckedFiles(checkedFileService.findReceiveInfoId(receiveInfoId));

            loginFlg = true;
        }

        // ReceiveHistoryBeanを作成
        receiveHistoryBean = historyLogic.createReceiveHistoryBean(receiveInfo, sysDate);
        receiveHistoryBean.contentLineDispOn();

        // ダウンロードファイル種類の設定
        nonSanitizeFlg = "true".equalsIgnoreCase(itemHelper.find(Item.NONE_SANITIZE_FLG, funcId).getValue());
        // ※sendFileCheckFlgはSendInfoの情報を反映
//        sendFileCheckFlg = "true".equalsIgnoreCase(itemHelper.find(Item.SENDIFLE_CHECK_FLG, funcId).getValue());
        if (receiveInfo.getSendInfo() != null) {
            // [2017/04/25] 送信元がLGWAN側ではなく、ふるまい検知フラグがONの場合はtrue
            sendFileCheckFlg = receiveInfo.getSendInfo().isSendFileCheckFlg() && !receiveInfo.getSendInfo().isSectionLgwan();
        } else {
            sendFileCheckFlg = false;
        }
        optDownloadFileType = 0;            // 常に無害化済ファイルを初期値とする
//        if (!nonSanitizeFlg) {              // 種別選択の初期値
//            optDownloadFileType = 0;        // 無害化済ファイル（種別選択なし）
//        } else if (sendFileCheckFlg) {
//            optDownloadFileType = 2;        // ふるまい検知済ファイル
//        } else {
//            optDownloadFileType = 1;        // オリジナルファイル
//        }

        // ファイル一覧のロード
        loadFileInfoList();
        
        LOG.debug("initView end");
    }

    /**
     * ファイル一覧のロード
     *
     */
    public void loadFileInfoList() {
        // 受信ファイル情報の設定
        fileInfoList = new ArrayList<>();

        // 有効期限切れか
        expirationFlg = false;
        if (receiveHistoryBean.getStatus().equals(historyLogic.getDspStatusExpiration())) {
            expirationFlg = true;
        }

        // 受信ファイル情報の表示順序を維持しつつ、各種別のファイル一覧を取得する
        List<ReceiveFile> receiveFileList = receiveInfo.getReceiveFiles();
        for (ReceiveFile receiveFile : receiveFileList) {
            FileInfoBean info = null;

            if (CommonUtil.isSectionLgwan()) {
                // LGWAN側
                info = new FileInfoBean(receiveFile);                           // 無害化済ファイルを取得
            } else {
                // Internet側
                switch (optDownloadFileType) {                                  // ダウンロードファイル種別の切り替え
                    case 1:                                                         // オリジナルファイルを取得
                        for (SendFile sendFile : receiveInfo.getSendInfo().getSendFiles()) {
                            if (receiveFile.getFileName().equals(sendFile.getFileName())) {
                                info = new FileInfoBean(receiveFile, sendFile);
                                break;
                            }
                        }
                        break;
                    case 2:                                                         // ふるまい検知済ファイルを取得
                        // ふるまい検知ファイル情報を取得
                        if (receiveInfo.getSendInfo().isSendFileCheckFlg()) {
                            for (CheckedFile checkedFile : receiveInfo.getCheckedFiles()) {
                                if (receiveFile.getFileName().equals(checkedFile.getFileName())) {
                                    info = new FileInfoBean(receiveFile, checkedFile);
                                    break;
                                }
                            }
                        } else {
                            // ふるまい検知フラグがfalseの場合、オリジナルファイルの一覧をグレー表示する
                            for (SendFile sendFile : receiveInfo.getSendInfo().getSendFiles()) {
                                if (receiveFile.getFileName().equals(sendFile.getFileName())) {
                                    info = new FileInfoBean(receiveFile, sendFile);
                                    info.setSanitizeFlg(false);                     // 無害化処理中に設定
                                    break;
                                }
                            }
                        }
                        break;
//                case 0:
                    default:                                                        // 無害化済ファイルを取得
                        info = new FileInfoBean(receiveFile);
                }
            }

            if (info == null) {
                break;                                            // 対象ファイルが見つからない場合は無視（基本的にはないはず）
            }
            info.setCancelFlg(receiveInfo.getSendInfo().isCancelFlg());
            info.setExpirationFlg(expirationFlg);

            // パスワード解除状態
            refleshFileList(receiveFile, info);

            ///追加
            fileInfoList.add(info);
        }

        // チェックボックス全選択
        allCheck();
    }

    /**
     * キャンセル状態
     *
     * @return キャンセル状態
     */
    public boolean isStatusCancel() {
        if (receiveInfo.getSendInfo() == null) {
            return true;
        }
        return receiveInfo.getSendInfo().isCancelFlg();
    }

    /**
     * ダウンロード準備
     *
     * @return ダウンロード準備
     */
    public boolean isReadyDownload() {
        for (FileInfoBean fileInfo : fileInfoList) {
            if (fileInfo.isTargetFlg() && !fileInfo.isSanitizeFlg()) {
                return false;
            }
        }
        return true;
    }

    /**
     * エラーファイルを含んでいるか
     *
     * @return true=含んでいる
     */
    public boolean isIncludeErrFile() {
        for (FileInfoBean fileInfo : fileInfoList) {
            if (!fileInfo.isFileNormal()) {
                return true;
            }
        }
        return false;
    }

    /**
     * ファイル選択が行われているか
     *
     * @return true＝選択されたファイルが存在
     */
    public boolean isSelectedFiles() {
        for (FileInfoBean fileInfo : fileInfoList) {
            if (fileInfo.isChecked()) {
                return true;
            }
        }
        return false;
    }

    /**
     * ファイル選択
     */
    public void onRowSelect() {
        if (selectedRowData.isChecked()) {
            selectedRowData.setChecked(false);
        } else {
            selectedRowData.setChecked(true);
        }
    }

    /**
     * チェックボックス全選択
     */
    public void allCheck() {
        for (FileInfoBean fileInfo : fileInfoList) {
            if (fileInfo.isFileNormal()) {     // 正常ファイルのみチェック
                fileInfo.setChecked(true);
            }
        }
    }

    /**
     * （「ダウンロードする」クリック時）ファイルダウンロード
     */
    public void eventDownload() {
        LOG.debug("eventDownload start..." + receiveInfo.getId());

        //メッセージ用-FacesContext
        FacesContext context = FacesContext.getCurrentInstance();

        //チェックON-ファイル
        List<File> fileList = new ArrayList<>();
        for (FileInfoBean fileInfo : fileInfoList) {
            if (fileInfo.isChecked()) {
                LOG.debug("targetFile : " + fileInfo.getFilePath());
                File _file = new File(fileInfo.getFilePath());
                fileList.add(_file);
            }
        }

        //ファイルダウンロード
        String mailAddress = "";
        if(receiveInfo != null){
            //団体区分判定用のメールアドレスを取得
            //※X-Envelope-Org-To を優先
            mailAddress = MailManager.getAddressShort(
                    receiveInfo.getOriginalReceiveAddress(), receiveInfo.getReceiveMailAddress());
        }
        FileDownloadBean result = executeFileDownload(fileList, receiveHistoryBean.getSubject(), funcId,
                mailAddress); //[248対応（簡易版)] 団体区分判定用にメールアドレス引数追加
        FacesMessage facesMessage = result.getFacesMessage();
        FacesMessage facesMessage2 = result.getFacesMessage2();
        String suffix = result.getSuffix();
        InputStream inputStream = result.getInputStream();
        downloadFileName = result.getDownloadFileName();

        //後処理
        String err_title = "";
        String err_callbackParam = "downloadFailed";
        if (facesMessage != null) {
            ///エラーが見つかった場合
            context.addMessage(null, facesMessage);
            LOG.debug("エラー発生..." + facesMessage.getSummary() + " " + facesMessage.getDetail());
            RequestContext.getCurrentInstance().addCallbackParam(err_callbackParam, true);
        } else if (inputStream == null) {
            ///ダウンロード無し
            file = null;
        } else {
            ///"更新処理に失敗しました。";
            String err_update = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.DOWNLOAD_UPDATE_DB_FAILED, funcId);
            try {
                // 警告文がある場合は表示
                if (facesMessage2 != null) {
                    context.addMessage(null, facesMessage2);
                }
                ///(ダウンロード)更新
                //receiveInfo = historyLogic.downloadUpdate(fileInfoList, receiveInfo.getId());
                historyLogic.downloadUpdate(fileInfoList, receiveInfo.getId());     //最新のreceiveInfoを画面に反映させない（状況表示をそのまま）
                //保持しているReceiveInfoにダウンロード回数だけ反映させる。（状況等の表示を更新しないように）
                for(FileInfoBean bean:fileInfoList){
                    if(bean.getReceiveFile() != null){
                        bean.getReceiveFile().setDownloadCount(bean.getDownloadCount());
                    }
                }
                
                ///ダウンロードファイルにセット
                file = new DefaultStreamedContent(inputStream, "application/" + suffix, downloadFileName);
            } catch (Exception e) {
                e.printStackTrace();
                LOG.error("受信履歴詳細(ダウンロード)更新失敗。", e);
                facesMessage = new FacesMessage(FacesMessage.SEVERITY_ERROR, err_title, err_update);
                context.addMessage(null, facesMessage);
                LOG.error("エラー発生..." + facesMessage.getSummary() + " " + facesMessage.getDetail());
                RequestContext.getCurrentInstance().addCallbackParam(err_callbackParam, true);
            }
        }

        LOG.debug("eventDownload end");
    }

    /**
     * 本文表示切替
     *
     * @param actionEvent
     */
    public void chgContent(ActionEvent actionEvent) {
        if (receiveHistoryBean.isContentLineDisp()) {
            ///ライン表示→全表示
            receiveHistoryBean.contentLineDispOff();
        } else {
            ///全表示→ライン表示
            receiveHistoryBean.contentLineDispOn();
        }
    }

    /**
     * 戻るボタン
     *
     * @param actionEvent
     */
    public void actBack(ActionEvent actionEvent) {
        LOG.trace("actBack start");

        ///HistoryDataTableBean値の複製
        ///(save_historyDataTable→receiveHistoryDataTableBean：リクエスト＝詳細画面から戻る)
        historyLogic.cloneHistoryDataTable(save_historyDataTable, receiveHistoryDataTableBean, HistoryLogic.REQ_BACK_DETAIL);

        LOG.trace("actBack end");
    }

    /**
     * ダウンロードファイルの切替可否
     *
     * @return
     */
    public boolean isAllowOriginalFlg() {
        // ダウンロードファイル種類の選択可能(設定:nonSanitizeFlg=true) & ログインフラグON(ワンタイムではない) & LGWAN側ではない(インターネット側である) & 庁外ユーザではない(庁内ユーザである) & 有効期限切れでない
        return nonSanitizeFlg && loginFlg && !CommonUtil.isSectionLgwan() && !commonBean.getUserType().equals("external") && !expirationFlg;
    }

    /**
     * ファイル一覧の更新します。
     *
     * @param receiveInfo 受信情報
     */
    private FileInfoBean refleshFileList(ReceiveFile receiveFile, FileInfoBean fileInfoBean) {

        // メッセージ
        //---解除されました。
        String messageUnlocked = itemHelper.findDispMessageStr(INF_LIST_MESSAGE_UNLOCKED, funcId);
        //---保護されています。
        String messageProtected = itemHelper.findDispMessageStr(INF_LIST_MESSAGE_PROTECTED, funcId);

        switch (receiveFile.getDecryptFiles().size()) {     // ファイル数で処理分岐
            case 1:     // Zip以外の扱い
                DecryptFile decryptFile = receiveFile.getDecryptFiles().get(0);
//                fileInfoBean = new FileInfoBean(decryptFile);
                if (decryptFile.isPasswordFlg()) {
                    if (decryptFile.isDecryptFlg()) {
                        ///解除されました
                        fileInfoBean.setFileDecryptMessage(messageUnlocked);
                    } else {
                        ///保護されています
                        fileInfoBean.setFileDecryptMessage(messageProtected);
                    }
                } else {
                    ///"－"
                    fileInfoBean.setFileDecryptMessage("－");
                }
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
                    return fileInfoBean;    // 想定外
                }
                if (!decryptFileParent.isPasswordFlg() || decryptFileParent.isDecryptFlg()) {
                    // 子ファイルのパスワード付き件数を取得
                    int decryptFileCount = 0;
                    int passwordFileCount = 0;
                    for (DecryptFile _decryptFile : receiveFile.getDecryptFiles()) {
                        if (_decryptFile.isPasswordFlg() && _decryptFile.getParentId().length() > 0) {
                            passwordFileCount++;
                            if (_decryptFile.isDecryptFlg()) {
                                decryptFileCount++;
                            }
                        }
                    }
                    if (passwordFileCount == 0) {
                        fileInfoBean.setFileDecryptMessage("－");
                    } else if (passwordFileCount == decryptFileCount) {
                        ///---解除されました
                        fileInfoBean.setFileDecryptMessage(messageUnlocked);
                    } else {
                        ///---未解除のファイルがxxx個残っています
                        fileInfoBean.setFileDecryptMessage(
                                itemHelper.findDispMessageStr(
                                        INF_LIST_MESSAGE_LOCKEDCOUNT, funcId,
                                        String.valueOf(passwordFileCount - decryptFileCount)
                                )
                        );
                    }
                } else {
                    ///保護されています
                    fileInfoBean.setFileDecryptMessage(messageProtected);
                }
        }

        return fileInfoBean;
    }

    /**
     * パスワード解除画面に遷移
     *
     * @return 遷移先
     */
    public String actPasswordUnlock() {
        // 選択-uid
        FacesContext
                .getCurrentInstance()
                .getExternalContext()
                .getFlash()
                .put("uid", receiveHistoryBean.getUid());

        ///HistoryDataTableBean値の複製
        historyLogic.cloneHistoryDataTable(save_historyDataTable, receiveHistoryDataTableBean, HistoryLogic.REQ_TO_SENDTRANSFERPASSWORDUNLOCK);
        FacesContext
                .getCurrentInstance()
                .getExternalContext()
                .getFlash()
                .put("receiveHistoryDataTableBean", receiveHistoryDataTableBean);

        return "sendTransferPasswordUnlock";
    }

    /**
     * パスワード解除ボタンを非活性とするか（全ファイルがパスワード解除済みか？）
     *
     * @return true=非活性とする、false=活性とする
     */
    public boolean isDisabled() {
        if (receiveInfo.isPasswordUnlockWaitFlg()) {
            // "パスワード解除待ち"の場合活性
            return false;   //活性
        } else {
            //無害化処理中（receiveFile.sanitizeFlg == fasle)があれば非活性とする
            for (ReceiveFile receiveFile : receiveInfo.getReceiveFiles()) {
                if (!receiveFile.isSanitizeFlg()) {
                    return true;    //非活性
                }
            }
           
            //パスワード付きファイルがある（decryptFileがある）場合
            //無害化済みかどうか、パスワード解除済みかどうかに関わらず活性とする
            for (ReceiveFile receiveFile : receiveInfo.getReceiveFiles()) {
                if (receiveFile.getDecryptFiles().size() > 0) {
                    return false;   //活性                    
                }
            }
        }
        return true;    //非活性
    }

    /**
     * ファイル交換でのパスワード解除フラグ (Config値)
     *
     * @return true=有効
     */
    public boolean isConfigPasswordUnlockFlg() {
        //ファイル交換でのパスワード解除実施有無
        Item item = itemHelper.find(Item.PASSWORD_UNLOCK_FLG, "sendTransfer");
        return Boolean.valueOf(item.getValue());
    }

    /**
     * パスワード付ファイルの受信履歴か？
     *
     * @return true=パスワード付受信履歴
     */
    public boolean isReceiveInfoPassword() {
        boolean isDecryptFileInfo = false;
        for (ReceiveFile receiveFile : receiveInfo.getReceiveFiles()) {
            if (receiveFile.getDecryptFiles().size() > 0) {
                isDecryptFileInfo = true;
                break;
            }
        }
        return isDecryptFileInfo;
    }

    /**
     * [v2.2.3]
     * 無害化処理による削除理由画面の表示
     */
    public void actDeleteReason() {
        //ダイアログのヘッダー
        dialogHeader = "無害化処理による削除理由";

        //ダイアログのメッセージを取得
        StringBuilder builder = new StringBuilder();
        File delReasonFile = DeleteReasonFileLogic.getDeleteReasonFileRecv(receiveInfo);
        try (BufferedReader reader = new BufferedReader(new FileReader(delReasonFile))) {
            String str = reader.readLine();
            while (str != null) {
                builder.append(str + System.getProperty("line.separator"));
                str = reader.readLine();
            }
            dialogMessage = builder.toString();            
        } catch (Exception e) {
            dialogMessage = "削除理由ファイルの読み込みに失敗しました。";
        }
    }

    /**
     * [v2.2.3]
     * 指定したReceiveフォルダに無害化処理による削除理由.txtが存在するか
     *
     * @return ファイルが存在する場合、trueを返す
     */
    public boolean isExistsDeleteReasonFile() {
        try {
            //削除理由ファイルが存在チェック
            if(!DeleteReasonFileLogic.getDeleteReasonFileRecv(receiveInfo).exists()){
                return false;   //存在しない
            }
            //無害化処理中（receiveFile.sanitizeFlg == fasleが存在）であればボタンが非活性となるよう存在しない扱いとする [v2.2.3a]
            for (ReceiveFile receiveFile : receiveInfo.getReceiveFiles()) {
                if (!receiveFile.isSanitizeFlg()) {
                    return false;    //存在しない扱い
                }
            }
            return true;    //存在する
        } catch (Exception e) {
            //例外の場合は存在しないとみなす
            return false;   //存在しない
        }
    }

    /**
     * [v2.2.3]
     * ReceiveFilesに無害化処理により異常検出されたファイルが存在するか
     * 
     * @return 異常検出ファイルが存在する場合、trueを返す
     */
    public boolean hasSanitizeErrFiles() {
        boolean isErr = false;
        for (ReceiveFile recvfile : receiveInfo.getReceiveFiles()) {
            if (recvfile.getFileErrCode() != FileSanitizeResultKbn.SUCCESS.value) {
                isErr = true;
                break;
            }
        }
        return isErr;
    }
}
