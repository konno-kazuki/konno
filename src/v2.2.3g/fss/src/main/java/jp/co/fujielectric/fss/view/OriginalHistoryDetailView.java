package jp.co.fujielectric.fss.view;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.NoResultException;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.entity.OnceUser;
import jp.co.fujielectric.fss.entity.ReceiveFile;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendFile;
import jp.co.fujielectric.fss.data.DataTableBean;
import jp.co.fujielectric.fss.data.FileDownloadBean;
import jp.co.fujielectric.fss.data.FileInfoBean;
import jp.co.fujielectric.fss.data.HistoryBean;
import jp.co.fujielectric.fss.logic.HistoryLogic;
import jp.co.fujielectric.fss.logic.MailManager;
import jp.co.fujielectric.fss.service.OnceUserService;
import jp.co.fujielectric.fss.util.ZipUtil;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.FileUtil;
import lombok.Getter;
import lombok.Setter;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.context.RequestContext;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 * 原本履歴詳細ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class OriginalHistoryDetailView extends HistoryCommonView implements Serializable {

    @Inject
    @Getter
    @Setter
    private HistoryBean originalHistoryBean;
    @Inject
    private DataTableBean originalHistoryDataTableBean;

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

    private ReceiveInfo receiveInfo;

    @Inject
    private OnceUserService onceUserService;

    @Inject
    private ReceiveInfoService receiveInfoService;

    // コンストラクタ
    public OriginalHistoryDetailView() {
        funcId = "originalHistoryDetail";
    }

    /**
     * 画面毎の初期化
     *
     */
    @Override
    public void initFunc() {

        // 原本一覧から選択された原本情報ID
        String originalInfoId = originalHistoryBean.getUid();
        save_historyDataTable = new DataTableBean();
        if ( StringUtils.isEmpty(originalInfoId) && StringUtils.isEmpty(commonBean.getOnetimeId()) ) {
            // ※例外対応（ログアウト直後のinitなど）
            receiveInfo = new ReceiveInfo();
            loginFlg = false;
        } else if (StringUtils.isEmpty(originalInfoId)) {
            // 本処理に入ることは想定外
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
            if (!originalHistoryDataTableBean.getReq().equals("")) {
                // HistoryDataTableBean値の複製
                // (originalHistoryDataTableBean→save_historyDataTable：リクエスト＝初期表示)
                historyLogic.cloneHistoryDataTable(originalHistoryDataTableBean, save_historyDataTable, HistoryLogic.REQ_INIT);
            }
            // 受信情報を取得
            receiveInfo = receiveInfoService.findWithRelationTables(originalInfoId);
            loginFlg = true;
        }

        // OriginalHistoryBeanを作成
        originalHistoryBean = historyLogic.createOriginalHistoryBean(receiveInfo, sysDate);
        originalHistoryBean.contentLineDispOn();

        // 有効期限切れか
        boolean _expirationFlg = false;
        //LOG.debug("---status="+receiveHistoryBean.getStatus()+" isDownload="+receiveHistoryBean.isDownload());
        if (originalHistoryBean.getStatus().equals( historyLogic.getDspStatusExpiration())) {
            _expirationFlg = true;
        }

        // 原本ファイル情報の設定
        fileInfoList = new ArrayList<>();
        List<ReceiveFile> receiveFileList = receiveInfo.getReceiveFiles();
        for (ReceiveFile receiveFile : receiveFileList) {
            FileInfoBean info = new FileInfoBean(receiveFile);
            // 無害化前のファイル(sendFile)から実体を取得
            // 原本履歴一覧・原本履歴詳細の状況欄を状況に応じて変化させるため、receiveFlgのみreceiveFileから取得
            try {
                List<SendFile> sendFiles = receiveInfo.getSendInfo().getSendFiles();
                for (SendFile sendFile : sendFiles) {
                    if (receiveFile.getFileName().equals(sendFile.getFileName())) {
                        info = new FileInfoBean(receiveFile, sendFile);
                    }
                }
            } catch (NoResultException e) {
                // 無害化無しフラグが取得出来なければ無視する
                e.printStackTrace();
            }

            info.setCancelFlg(receiveInfo.getSendInfo().isCancelFlg());
            info.setExpirationFlg(_expirationFlg);
            //LOG.debug("---info="+info.getFileName()+" isFileNormal="+info.isFileNormal());

            // 追加
            fileInfoList.add(info);
        }

        // チェックボックス全選択
        allCheck();

        LOG.debug("initView end");
    }

    public boolean isStatusCancel() {
        if (receiveInfo.getSendInfo() == null) {
            return true;
        }
        return receiveInfo.getSendInfo().isCancelFlg();
    }

    public boolean isReadyDownload() {
        for (FileInfoBean fileInfo : fileInfoList) {
            if (fileInfo.isTargetFlg() && !fileInfo.isSanitizeFlg()) {
                return false;
            }
        }
        return true;
    }

    public boolean isIncludeErrFile() {
        for (FileInfoBean fileInfo : fileInfoList) {
            if (!fileInfo.isFileNormal()) {
                return true;
            }
        }
        return false;
    }

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

        // チェックON-ファイル
        List<File> fileList = new ArrayList<>();
        for (FileInfoBean fileInfo : fileInfoList) {
            if (fileInfo.isChecked()) {
                LOG.debug("targetFile : " + fileInfo.getFilePath());
                File _file = new File(fileInfo.getFilePath());
                fileList.add(_file);
            }
        }

        // メッセージ用-FacesContext
        FacesContext context = FacesContext.getCurrentInstance();
//        FacesMessage facesMessage = null;

//        // ファイルダウンロード準備(inputStreamセットなど）
//        // "ダウンロードに失敗しました。";
//        String err_download = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.DOWNLOAD_FILE_FAILED, funcId);
        // "更新処理に失敗しました。";
        String err_update = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.DOWNLOAD_UPDATE_DB_FAILED, funcId);
        String err_title = "";
        String err_callbackParam = "downloadFailed";
//
//        // クリア
//        String suffix;
//        InputStream inputStream = null;
//        LOG.debug("targetFile Count : " + fileList.size());
//        switch (fileList.size()) {
//            case 0:     // ダウンロード無し
//                downloadFileName = "";
//                suffix = "";
//                break;
//            case 1:     // 単ファイルダウンロード
//                File _file = fileList.get(0);
//                suffix = FileUtil.getSuffix(_file.getName());
//                //err_title = "単ファイルダウンロード：　";
//                try {
//                    downloadFileName = new String(_file.getName().getBytes("Shift_JIS"), "ISO-8859-1");
//                    inputStream = new FileInputStream(_file);
//                } catch (UnsupportedEncodingException | FileNotFoundException e) {
//                    LOG.error(e.getMessage());
//                    inputStream = null;
//                    facesMessage = new FacesMessage(FacesMessage.SEVERITY_ERROR, err_title, err_download);
//                }
//                break;
//            default:    // Zip圧縮ダウンロード
//                suffix = "zip";
//                //err_title = "Zip圧縮ダウンロード：　";
//                try {
//                    downloadFileName = new String((originalHistoryBean.getSubject() + ".zip").getBytes("Shift_JIS"), "ISO-8859-1");  // 件名 + .zip
//                    byte[] zipFile = ZipUtil.createZipInMemory(fileList);
//                    inputStream = new ByteArrayInputStream(zipFile);
//                } catch (ZipException | IOException | CloneNotSupportedException e) {
//                    LOG.error(e.getMessage());
//                    inputStream = null;
//                    facesMessage = new FacesMessage(FacesMessage.SEVERITY_ERROR, err_title, err_download);
//                }
//        }

        //ファイルダウンロード
        String mailAddress = "";
        if(receiveInfo != null){
            //団体区分判定用のメールアドレスを取得
            //※X-Envelope-Org-To を優先
            mailAddress = MailManager.getAddressShort(
                    receiveInfo.getOriginalReceiveAddress(), receiveInfo.getReceiveMailAddress());
        }
        FileDownloadBean result = executeFileDownload(fileList, originalHistoryBean.getSubject(), funcId,
                mailAddress); //[248対応（簡易版)] 団体区分判定用にメールアドレス引数追加
        FacesMessage facesMessage = result.getFacesMessage();
        FacesMessage facesMessage2 = result.getFacesMessage2();
        String suffix = result.getSuffix();
        InputStream inputStream = result.getInputStream();
        downloadFileName = result.getDownloadFileName();
        
        //2018.10.25 ファイル名にタブ文字が含まれるとブラウザによってはダウンロードで異常が発生する（IEで確認）
        //ファイル名からタブ文字を削除することで回避できる。　
        //※回避が必要な場合は以下のコメントを解除すること。
//        {
//            downloadFileName = downloadFileName.replaceAll("%09", "");
//            LOG.debug("# eventDownload. downloadFileName:[{}], suffix:[{}]", downloadFileName, suffix);
//        }
        
        // 後処理
        if (facesMessage != null) {
            // エラーが見つかった場合
            context.addMessage(null, facesMessage);
            LOG.debug("エラー発生..." + facesMessage.getSummary() + " " + facesMessage.getDetail());
            RequestContext.getCurrentInstance().addCallbackParam(err_callbackParam, true);
        } else if (inputStream == null) {
            // ダウンロード無し
            file = null;
        } else {
            // 更新処理
            try{
                // 警告文がある場合は表示
                if(facesMessage2 != null) {
                    context.addMessage(null, facesMessage2);
                }
                receiveInfo = historyLogic.downloadUpdate(fileInfoList, receiveInfo.getId());
                // ダウンロードファイルにセット
                file = new DefaultStreamedContent(inputStream, "application/" + suffix, downloadFileName);
            } catch (Exception e) {
                e.printStackTrace();
                LOG.error("原本履歴詳細(ダウンロード)更新失敗。", e);
                facesMessage = new FacesMessage(FacesMessage.SEVERITY_ERROR, err_title, err_update);
                context.addMessage(null, facesMessage);
                LOG.debug("エラー発生..." + facesMessage.getSummary() + " " + facesMessage.getDetail());
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
        if (originalHistoryBean.isContentLineDisp()) {
            // ライン表示→全表示
            originalHistoryBean.contentLineDispOff();
        } else {
            // 全表示→ライン表示
            originalHistoryBean.contentLineDispOn();
        }
    }

    /**
     * 戻るボタン
     *
     * @param actionEvent
     */
    public void actBack(ActionEvent actionEvent) {
        LOG.trace("actBack start");

        // HistoryDataTableBean値の複製
        // (save_historyDataTable→originalHistoryDataTableBean：リクエスト＝詳細画面から戻る)
        historyLogic.cloneHistoryDataTable(save_historyDataTable, originalHistoryDataTableBean, HistoryLogic.REQ_BACK_DETAIL);

        LOG.trace("actBack end");
    }

    /**
     * メール原本取得
     */
    public void eventDownloadEml() {

        // メッセージ用-FacesContext
        FacesContext context = FacesContext.getCurrentInstance();
        FacesMessage facesMessage = null;

        // ファイルダウンロード準備(inputStreamセットなど）
        // "ダウンロードに失敗しました。";
        String err_download = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.DOWNLOAD_FILE_FAILED, funcId);
        String err_title = "";
        String err_callbackParam = "downloadFailed";

        try {
            String id = receiveInfo.getSendInfoId();;
//            File _file = new File(CommonUtil.getSetting("maildir") + id + ".eml");
            File _file = new File(CommonUtil.getFolderMail(receiveInfo.getSendInfo()), id + ".eml");
            InputStream inputStream = new FileInputStream(_file);
            file = new DefaultStreamedContent(inputStream, "application/octet-stream", id + ".eml");

            historyLogic.downloadEml(id);
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("原本履歴メール原本取得失敗。", e);
            facesMessage = new FacesMessage(FacesMessage.SEVERITY_ERROR, err_title, err_download);
            context.addMessage(null, facesMessage);
            RequestContext.getCurrentInstance().addCallbackParam(err_callbackParam, true);
        }
    }
}
