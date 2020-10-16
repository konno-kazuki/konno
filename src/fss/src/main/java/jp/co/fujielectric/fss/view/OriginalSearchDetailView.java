package jp.co.fujielectric.fss.view;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendFile;
import jp.co.fujielectric.fss.data.DataTableBean;
import jp.co.fujielectric.fss.data.FileInfoBean;
import jp.co.fujielectric.fss.data.OriginalSearchBean;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.util.ZipUtil;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.service.SendInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.FileUtil;
import lombok.Getter;
import lombok.Setter;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.context.RequestContext;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 * 原本検索詳細ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class OriginalSearchDetailView extends HistoryCommonView implements Serializable {
    // <<詳細画面とのデータ連携用>>
    @Inject private DataTableBean originalSearchDataTableBean;
    @Inject private OriginalSearchBean originalSearchConditionBean;

//    @Getter @Setter private DataTableBean dataTableBean = null;                 // 検索結果リスト設定
    @Getter @Setter private OriginalSearchBean searchBean = null;               // 原本検索条件

    @Getter @Setter private List<FileInfoBean> fileInfoList;                    // 添付ファイル情報
    @Getter @Setter private List<ReceiveInfo> receiveInfoList;                  // 送信先情報
    @Getter @Setter private boolean isContentLine = true;                       // 通信欄の１行表示フラグ
    @Getter @Setter private FileInfoBean selectedRowData;                       // 選択ファイル情報
    @Getter @Setter private StreamedContent file;                               // ※ファイルダウンロード用

    @Getter private String subject;                                             // 件名
    @Getter private String content;                                             // 通信欄
    @Getter private String registDate;                                          // 登録日時
    @Getter private String expirationDate;                                      // 保存期限
    @Getter private String sender;                                              // 送信元

    @Inject
    private ReceiveInfoService receiveInfoService;

    @Inject
    private SendInfoService sendInfoService;

    // コンストラクタ
    public OriginalSearchDetailView() {
        funcId = "originalSearchDetail";
    }

    /**
     * 画面毎の初期化
     */
    @Override
    public void initFunc() {
        if (originalSearchConditionBean.isReady()) {                            // 原本検索画面⇔原本検索詳細画面遷移の場合
            // 詳細画面とのデータ連携用の引継ぎ
            dataTableBean = new DataTableBean();
            originalSearchDataTableBean.copyBean(dataTableBean);
            searchBean = new OriginalSearchBean();
            originalSearchConditionBean.copyBean(searchBean);

            // 原本メール情報の取得
            SimpleDateFormat sdfLong = new SimpleDateFormat("yyyy年MM月dd日(E)");
            SimpleDateFormat sdfLongAddTime = new SimpleDateFormat("yyyy年MM月dd日(E) HH:mm");
            String sendInfoId = searchBean.getSelectedRowData().getSendinfoId();
            SendInfo sendInfo = sendInfoService.find(sendInfoId);
            subject = sendInfo.getSubject();                                    // 件名
            content = sendInfo.getContent();                                    // 通信欄
            registDate = sdfLongAddTime.format(sendInfo.getSendTime());         // 登録日時
            expirationDate = sdfLong.format(sendInfo.getExpirationTime());      // 保存期限
            if (!StringUtils.isEmpty(sendInfo.getSendUserName())) {             // 送信元
                sender = sendInfo.getSendUserName();
            } else {
                sender = sendInfo.getSendMailAddress();
            }

            // 送信先情報の取得
            receiveInfoList = receiveInfoService.findForSendInfoId(sendInfoId);

            // 原本ファイル一覧のロード
            loadFileInfoList(sendInfo);
        } else {                                                                // その他はエラーとする
            throw new RuntimeException("OriginalSearchDetailView:initFunc notReady!");
        }
    }

    /**
     * 原本ファイル一覧のロード
     */
    private void loadFileInfoList(SendInfo sendInfo) {
        // 原本ファイル情報の設定
        fileInfoList = new ArrayList<>();

        // 有効期限切れ判定
        boolean _expirationFlg = (DateUtil.getDateExcludeTime(sendInfo.getExpirationTime()).getTime() < DateUtil.getDateExcludeTime(sysDate).getTime());

        // ファイル情報
        for (SendFile sendFile : sendInfo.getSendFiles()) {
            FileInfoBean fileInfoBean = new FileInfoBean(sendFile);
            fileInfoBean.setCancelFlg(sendInfo.isCancelFlg());
            fileInfoBean.setExpirationFlg(_expirationFlg);
            fileInfoList.add(fileInfoBean);
        }

        // チェックボックス全選択
        allCheck();
    }

    /**
     * エラーファイルを含んでいるか
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
        FacesMessage facesMessage = null;

        // ファイルダウンロード準備(inputStreamセットなど）
        // "ダウンロードに失敗しました。";
        String err_download = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.DOWNLOAD_FILE_FAILED, funcId);
        // "更新処理に失敗しました。";
        String err_update = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.DOWNLOAD_UPDATE_DB_FAILED, funcId);
        String err_title = "";
        String err_callbackParam = "downloadFailed";

        // クリア
        String suffix = "";
        InputStream inputStream = null;
        LOG.debug("targetFile Count : " + fileList.size());
        String downloadFileName = "";
        switch (fileList.size()) {
            case 0:     // ダウンロード無し
                break;
            case 1:     // 単ファイルダウンロード
                File _file = fileList.get(0);
                suffix = FileUtil.getSuffix(_file.getName());
                //err_title = "単ファイルダウンロード：　";
                try {
                    downloadFileName = new String(_file.getName().getBytes("Shift_JIS"), "ISO-8859-1");
                    inputStream = new FileInputStream(_file);
                } catch (UnsupportedEncodingException | FileNotFoundException e) {
                    LOG.error(e.getMessage());
                    inputStream = null;
                    facesMessage = new FacesMessage(FacesMessage.SEVERITY_ERROR, err_title, err_download);
                }
                break;
            default:    // Zip圧縮ダウンロード
                suffix = "zip";
                //err_title = "Zip圧縮ダウンロード：　";
                try {
                    downloadFileName = new String((subject+ ".zip").getBytes("Shift_JIS"), "ISO-8859-1");  // 件名 + .zip
                    byte[] zipFile = ZipUtil.createZipInMemory(fileList);
                    inputStream = new ByteArrayInputStream(zipFile);
                } catch (ZipException | IOException | CloneNotSupportedException e) {
                    LOG.error(e.getMessage());
                    inputStream = null;
                    facesMessage = new FacesMessage(FacesMessage.SEVERITY_ERROR, err_title, err_download);
                }
        }

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
            try {
                historyLogic.downloadUpdate(fileInfoList);
                // ダウンロードファイルにセット
                file = new DefaultStreamedContent(inputStream, "application/" + suffix, downloadFileName);
            } catch (Exception e) {
                e.printStackTrace();
                LOG.error("原本検索詳細(ダウンロード)更新失敗。", e);
                facesMessage = new FacesMessage(FacesMessage.SEVERITY_ERROR, err_title, err_update);
                context.addMessage(null, facesMessage);
                LOG.debug("エラー発生..." + facesMessage.getSummary() + " " + facesMessage.getDetail());
                RequestContext.getCurrentInstance().addCallbackParam(err_callbackParam, true);
            }
        }
    }

    /**
     * 本文表示切替
     *
     * @param actionEvent
     */
    public void chgContent(ActionEvent actionEvent) {
        isContentLine = !isContentLine;
    }

    /**
     * 戻るボタン
     *
     * @param actionEvent
     */
    public void actBack(ActionEvent actionEvent) {
        // 詳細画面とのデータ連携用の引継ぎ
        dataTableBean.copyBean(originalSearchDataTableBean);
        searchBean.copyBean(originalSearchConditionBean);
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
            String sendInfoId = searchBean.getSelectedRowData().getSendinfoId();
//            File _file = new File(CommonUtil.getSetting("maildir") + sendInfoId + ".eml");
            String procDate = searchBean.getSelectedRowData().getProcDate();    //[v2.2.1]
            File _file = new File(CommonUtil.getFolderMail(procDate), sendInfoId + ".eml");
            InputStream inputStream = new FileInputStream(_file);
            file = new DefaultStreamedContent(inputStream, "application/octet-stream", sendInfoId + ".eml");
            historyLogic.downloadEml(sendInfoId);
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("原本履歴メール原本取得失敗。", e);
            facesMessage = new FacesMessage(FacesMessage.SEVERITY_ERROR, err_title, err_download);
            context.addMessage(null, facesMessage);
            RequestContext.getCurrentInstance().addCallbackParam(err_callbackParam, true);
        }
    }
}
