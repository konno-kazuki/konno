package jp.co.fujielectric.fss.view;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.faces.application.FacesMessage;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.PersistenceException;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.FileDownloadBean;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.logic.HistoryLogic;
import jp.co.fujielectric.fss.util.FileUtil;
import jp.co.fujielectric.fss.util.TextUtil;
import jp.co.fujielectric.fss.util.ZipUtil;
import lombok.Getter;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.util.InternalZipConstants;
import org.apache.commons.lang3.StringUtils;

/**
 * 送信・受信履歴一覧ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class HistoryCommonView extends CommonView implements Serializable {

    @Inject
    protected HistoryLogic historyLogic;

    @Getter
    protected int historyRowsDefault;           //一覧表示件数
    @Getter
    protected String historyRowsTemplate;       //一覧表示件数選択肢

    //callbackParam
    protected final String callbackParam_Error = "isError";

    /**
     * ステータス
     */
    /** ステータス：有効期限切れ */
    protected final String DSP_STATUS_EXPIRATION = "dspStatusExpiration";
    /** ステータス：無害化処理中 */
    protected final String DSP_STATUS_SANITIZE = "dspStatusSanitize";
    /** ステータス：未受領 */
    protected final String DSP_STATUS_RECEIVE_NONE = "dspStatusReceiveNone";
    /** ステータス：受領 */
    protected final String DSP_STATUS_RECEIVE = "dspStatusReceive";
    /** ステータス：添付ファイル無 */
    protected final String DSP_STATUS_FILE_EMPTY = "dspStatusFileEmpty";
    /** ステータス：パスワード解除待ち */
    protected final String DSP_STATUS_DECRYPTING = "dspStatusDecrypting";
    /** ステータス：ダウンロード可能 */
    protected final String DSP_STATUS_DOWNLOAD_ABLE = "dspStatusDownloadAble";
    /** ステータス：ダウンロード完了 */
    protected final String DSP_STATUS_DOWNLOAD_COMPLETE = "dspStatusDownloadComplete";
    /** ステータス：取り消し */
    protected final String DSP_STATUS_CANCEL = "dspStatusCancel";
    /** ステータス：承認待ち */
    protected final String DSP_STATUS_APPROVAL = "dspStatusApproval";
    /** ステータス：却下済み */
    protected final String DSP_STATUS_REJECTED = "dspStatusRejected";
    /** ステータス：承認済み */
    protected final String DSP_STATUS_APPROVED = "dspStatusApproved";

    /** ステータス：送信中 */
    protected final String DSP_STATUS_SENDSTARTING = "dspStatusSendStarting";
    /** ステータス：送信処理失敗 */
    protected final String DSP_STATUS_SENDTRANSFER_ERR = "dspStatusSendTransferErr";
    /** ステータス：承認依頼処理失敗 */
    protected final String DSP_STATUS_APPROVEREQUEST_ERR = "dspStatusApproveRequestErr";
    
    //承認フラグ：初期
    protected final int APPROVEDFLG_INIT = 0;
    //承認フラグ：承認
    protected final int APPROVEDFLG_APPROVED = 1;
    //承認フラグ：却下
    protected final int APPROVEDFLG_REJECTED = -1;

    /**
     * マスタ設定値からの変数初期化
     *
     */
    @Override
    protected void initItems() {
        Item item;

        //一覧表示件数
        item = itemHelper.find(Item.HISTORY_ROWS_DEFAULT, funcId);
        historyRowsDefault = Integer.parseInt(item.getValue());

        //一覧表示件数選択肢
        item = itemHelper.find(Item.HISTORY_ROWS_TEMPLATE, funcId);
        historyRowsTemplate = item.getValue();

        //-----------------------------
        //historyLogicのメッセージ変数をセット
        //-----------------------------

        //---有効期限切れ
        historyLogic.setDspStatusExpiration(getDispMessageStr(DSP_STATUS_EXPIRATION));
        //---無害化処理中
        historyLogic.setDspStatusSanitize(getDispMessageStr(DSP_STATUS_SANITIZE));
        //---未受領
        historyLogic.setDspStatusReceiveNone(getDispMessageStr(DSP_STATUS_RECEIVE_NONE));
        //---受領
        historyLogic.setDspStatusReceive(getDispMessageStr(DSP_STATUS_RECEIVE));
        //---添付ファイル無
        historyLogic.setDspStatusFileEmpty(getDispMessageStr(DSP_STATUS_FILE_EMPTY));
        //---パスワード解除待ち
        historyLogic.setDspStatusDecrypting(getDispMessageStr(DSP_STATUS_DECRYPTING));
        //---ダウンロード可能
        historyLogic.setDspStatusDownloadAble(getDispMessageStr(DSP_STATUS_DOWNLOAD_ABLE));
        //---ダウンロード完了
        historyLogic.setDspStatusDownloadComplete(getDispMessageStr(DSP_STATUS_DOWNLOAD_COMPLETE));
        //---取消
        historyLogic.setDspStatusCancel(getDispMessageStr(DSP_STATUS_CANCEL));
        //---承認待ち
        historyLogic.setDspStatusApproval(getDispMessageStr(DSP_STATUS_APPROVAL));
        //---却下済み
        historyLogic.setDspStatusRejected(getDispMessageStr(DSP_STATUS_REJECTED));
        //---承認済み
        historyLogic.setDspStatusApproved(getDispMessageStr(DSP_STATUS_APPROVED));
        //---送信中
        historyLogic.setDspStatusSendStarting(getDispMessageStr(DSP_STATUS_SENDSTARTING));
        //---送信処理失敗
        historyLogic.setDspStatusSendTransferErr(getDispMessageStr(DSP_STATUS_SENDTRANSFER_ERR));
        //---承認依頼処理失敗
        historyLogic.setDspStatusApproveRequestErr(getDispMessageStr(DSP_STATUS_APPROVEREQUEST_ERR));

        //承認フラグをhistoryLogicにセット
        historyLogic.setApprovedflgApproved(APPROVEDFLG_APPROVED);
        historyLogic.setApprovedflgRejected(APPROVEDFLG_REJECTED);
    }
    private String getDispMessageStr(String key){
        return itemHelper.findDispMessageStr(key, funcId);
    }

    /**
     * Growl用テキスト
     * （growlへ表示するdetailは、文字長さにより自動で改行がされてしまうので、現在は結合のみ）
     *
     * @param address           メールアドレス
     * @param personal          名前
     * @param dateTime          日時
     * @param comment           通信欄
     *
     * @return Growl用テキスト
     *
     */
    protected String getGrowlText(String address, String personal, Date dateTime, String comment) {

        //名前、メールアドレス
        String text = getAddressText(address, personal);

        //日時
        if (dateTime!=null) {
            text = text
                + "<br>"
                + TextUtil.createDateText(dateTime);
        }

        //通信欄
        if (!StringUtils.isEmpty(comment)) {
            text = text
                + "<br>"
                + comment;
        }

        //return
        return text;
    }

    //[248対応（簡易版)]団体区分判定用にmailAddress引数を追加
    /**
     * ファイルダウンロード実行
     *
     * @param fileList
     * @param subject       件名
     * @param funcId
     * @param mailAddress   メールアドレス
     *
     * @return FileDownloadBean
     */
    public FileDownloadBean executeFileDownload(List<File> fileList, String subject, String funcId, String mailAddress) {

        FileDownloadBean result = new FileDownloadBean();

        //ファイルダウンロード準備(inputStreamセットなど）
        //"ダウンロードに失敗しました。";
        String err_download = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.DOWNLOAD_FILE_FAILED, funcId);

        //クリア
        FacesMessage facesMessage = null;
        FacesMessage facesMessage2 = null;
        String downloadFileName = "";
        String err_title = "";
        String suffix;
        InputStream inputStream = null;
        LOG.debug("targetFile Count : " + fileList.size());
        switch (fileList.size()) {
            case 0:     // ダウンロード無し
                downloadFileName = "";
                suffix = "";
                break;
            case 1:     // 単ファイルダウンロード
                File _file = fileList.get(0);
                suffix = FileUtil.getSuffix(_file.getName());
                //err_title = "単ファイルダウンロード：　";
                try {
                    //[2017/03/09]Shift_JISのダメ文字([\]を含む2byte文字)が文字化けするため、UTF-8でのダウンロードに変更
//                    downloadFileName = new String(_file.getName().getBytes("Shift_JIS"), "ISO-8859-1");
                    downloadFileName = URLEncoder.encode(_file.getName(), "UTF-8");
                    inputStream = new FileInputStream(_file);
                } catch (UnsupportedEncodingException | FileNotFoundException e) {
                    LOG.error(e.getMessage());
                    inputStream = null;
                    facesMessage = new FacesMessage(FacesMessage.SEVERITY_ERROR, err_title, err_download);
                }
                break;
            default:    // Zip圧縮ダウンロード
                suffix = "zip";

                // [2017/04/14]zip内文字コードをWin31Jとするかを判断
                boolean zipCharsetConvert = false;
                String charset = InternalZipConstants.CHARSET_UTF8;
                try {
                    Item item = itemHelper.find(Item.ZIP_CHARSET_CONVERT_INNER, funcId, mailAddress);   //[248対応（簡易版)]
                    zipCharsetConvert = item.getValue().equalsIgnoreCase("true");
                } catch (PersistenceException e) {
                    LOG.debug("設定値が取得出来ない:" + Item.ZIP_CHARSET_CONVERT_INNER, e);
                }
                if(zipCharsetConvert) {
                    // 変換出来ない文字が含まれていないか確認
                    boolean convertibleWin31J = true;
                    for(File file : fileList) {
                        if(!TextUtil.isValidWin31J(file.getName())) {
                            convertibleWin31J = false;
                        }
                    }
                    if(convertibleWin31J) {
                        charset = ZipUtil.CHARSET_csWindows31J;
                    } else {
                        // "ZIP内文字コードの変換に失敗しました。"
                        String warn_zipCharsetUnconverted = itemHelper.findDispMessageStr(Item.WarningMsgItemKey.WARNING_ZIP_CHARSET_UNCONVERTED, funcId);
                        facesMessage2 = new FacesMessage(FacesMessage.SEVERITY_WARN, "", warn_zipCharsetUnconverted);
                    }
                }

                //err_title = "Zip圧縮ダウンロード：　";
                try {
                    //[2017/03/09]Shift_JISのダメ文字([\]を含む2byte文字)が文字化けするため、UTF-8でのダウンロードに変更
                    // 件名 + .zip
//                    downloadFileName = new String((subject + ".zip").getBytes("Shift_JIS"), "ISO-8859-1");
                    downloadFileName = URLEncoder.encode((subject + ".zip"), "UTF-8");
                    byte[] zipFile = ZipUtil.createZipInMemory(fileList, null, charset);
                    inputStream = new ByteArrayInputStream(zipFile);
                } catch (ZipException | IOException | CloneNotSupportedException e) {
                    LOG.error(e.getMessage());
                    inputStream = null;
                    facesMessage = new FacesMessage(FacesMessage.SEVERITY_ERROR, err_title, err_download);
                }
        }

        //セット
        result.setSuffix(suffix);
        result.setInputStream(inputStream);
        result.setDownloadFileName(downloadFileName);
        if (facesMessage != null){result.setFacesMessage(facesMessage); }
        if (facesMessage2 != null){result.setFacesMessage2(facesMessage2); }

        //return
        return result;
    }
}
