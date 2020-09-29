package jp.co.fujielectric.fss.view;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.logic.FileDownload;
import jp.co.fujielectric.fss.logic.ManageIdBulkLogic;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.FileUtil;
import jp.co.fujielectric.fss.util.IdUtil;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.context.RequestContext;
import org.primefaces.event.FileUploadEvent;

/**
 * 管理メニュー：ユーザ管理
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class ManageIdBulkView extends ManageCommonView implements Serializable {

    private int successCount;                       //(登録成功ユーザID)結果件数
    private int failureCount;                       //(登録失敗ユーザID)結果件数
    private Map<Integer, List<String>> mapsErr;

    @Getter
    @Setter
    private String selectedFilePath;
    @Getter
    @Setter
    private String selectedFileName;

    @Inject
    private ManageIdBulkLogic manageIdBulkLogic;

    //コンストラクタ
    public ManageIdBulkView() {
        funcId = "manageIdBulk";
    }

    @PostConstruct
    @Override
    public void init() {
        super.init();
    }

    /**
     * マスタ設定値からの変数初期化
     *
     */
    @Override
    protected void initItems() {
        super.initItems();
    }

    /**
     * ファイル選択
     *
     * @param event
     * @exception IOException
     */
    public void handleFileUpload(FileUploadEvent event) throws IOException {

        //upldFile = event.getFile();
        String fname = event.getFile().getFileName();

        //UploadedFile →　File保存
//        String folder = CommonUtil.getSetting("tempdir") + "/" + IdUtil.createUUID();
        File folder = new File(CommonUtil.getFolderTemp(),IdUtil.createUUID());
        File objFile = new File(folder, fname);
        selectedFilePath = "";
        selectedFileName = "";

        try (InputStream is = event.getFile().getInputstream()) {
            FileUtil.saveFile(is, objFile.getPath());
            selectedFilePath = objFile.getPath();
            selectedFileName = objFile.getName();
        } catch (IOException e) {
            //例外処理
            e.printStackTrace();

            FacesContext context = FacesContext.getCurrentInstance();
            String _itemName = getItemCaption("dspFailure");  ///"失敗：　"
            String _summary = getFacesMessageSummary("取込" + _itemName);

            ///アップロードに失敗しました。
            String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.UPLOAD_FILE_FAILED, funcId);
            context.addMessage("", new FacesMessage(FacesMessage.SEVERITY_ERROR, _summary, errMsg));

            throw e;
        }
    }

    /**
     * クリア
     */
    public void eventClear() {
        LOG.debug("eventClear start");

        selectedFilePath = "";
        selectedFileName = "";
        successCount = -1;
        manageIdBulkLogic.setSuccessCount(-1);
        failureCount = -1;
        manageIdBulkLogic.setFailureCount(-1);
        mapsErr = new HashMap<>();
        manageIdBulkLogic.setMapsErr(new HashMap<>());

        LOG.debug("eventClear end");
    }

    /**
     * エラー内容出力可否
     *
     * @return
     */
    public boolean isErrOutput() {

        if (failureCount > 0) {
            return true;
        }
        return false;
    }

    /**
     * (登録成功ユーザID)結果件数出力情報を取得
     *
     * @return (登録成功ユーザID)結果件数出力情報
     */
    public String outSuccessCount() {
        String out_count = "";
        if (!StringUtils.isEmpty(selectedFilePath) && successCount != -1) {
            out_count = getItemCaption("dspResultsCount") + String.valueOf(successCount);
        }
        return out_count;
    }

    /**
     * (登録失敗ユーザID)結果件数出力情報を取得
     *
     * @return (登録失敗ユーザID)結果件数出力情報
     */
    public String outFailureCount() {
        String out_count = "";
        if (!StringUtils.isEmpty(selectedFilePath) && failureCount != -1) {
            out_count = getItemCaption("dspResultsCount") + String.valueOf(failureCount);
        }
        return out_count;
    }

    /**
     * エラー内容出力
     */
    public void eventErrOutput() {
        LOG.trace("eventErrOutput Start:mapsErr.size=" + mapsErr.size());
        if (mapsErr == null || mapsErr.size() < 1) {
            return;
        }

        RequestContext req_context = RequestContext.getCurrentInstance();
        FileDownload fd = new FileDownload();

        //ダウンロード可否
        boolean _isDownload = false;
        this.isDownLoad = _isDownload;

        //ファイル名
        Date _date = new Date();
        String _fileName = "_" + new SimpleDateFormat("yyyyMMdd").format(_date) + ".csv";
        String fileName = getItemCaption("nameBulkFile") + _fileName;
        if (fileName.isEmpty()) {
            fileName = _fileName;
        }

        //ダウンロード準備
        try {
            //-------------------------------
            //ヘッダ
            //-------------------------------
            for (Map.Entry<Integer, String> h : manageIdBulkLogic.getCSV_MAP().entrySet()) {
                fd.addOneData(h.getValue());
            }
            ///改行
            fd.addNewLine();

            //-------------------------------
            //職員毎エラー内容
            //-------------------------------
            for (int key = 0; key < mapsErr.size(); key++) {
                ///キー値は登録順のはず(0～)
                List<String> datas = mapsErr.get(key);
                ///内容リスト
                for (int j = 0; j < datas.size(); j++) {
                    String _s = datas.get(j);
                    if (j > 0) {
                        _s = FileUtil.getCsvString(_s);
                    }
                    fd.addOneData(_s);
                }

                ///改行
                fd.addNewLine();
            }

            ///downloadFileセット
            this.downloadFile = fd.getDownloadFile(fileName);

            ///ダウンロード可能
            _isDownload = true;

        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("エラー内容出力ダウンロード準備失敗。", ex);
        }

        this.isDownLoad = _isDownload;  ///ダウンロード可否
        req_context.addCallbackParam(callbackParam_Download, this.isDownLoad);
    }

    /**
     * ＣＳＶ取込
     */
    @Override
    public void eventCsvInput() {
        LOG.trace("eventCsvInput Start");

        // [2017/03/13] トランザクション管理のため、logicに処理を移した
        FacesContext context = FacesContext.getCurrentInstance();
        manageIdBulkLogic.initItems();
        try {
            boolean check = manageIdBulkLogic.eventCsvInput(selectedFilePath);
            if (check) {
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, getFacesMessageSummary(manageIdBulkLogic.getSummary()), manageIdBulkLogic.getMessage()));
            } else {
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(manageIdBulkLogic.getSummary()), manageIdBulkLogic.getMessage()));
            }
        } catch (Exception e) {
            e.printStackTrace();
            //一括登録失敗(dspBtnBulkInput=一括登録,dspFailure=失敗)
            //取込処理中に不正なエラーが発生したため、登録処理をキャンセルしました。
            String summary = getItemCaption("dspBtnBulkInput") + getItemCaption("dspFailure");
            String message = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_BULK_INPUT_ALL_CANCEL, funcId);
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(summary), message));
        }

        // リクエストスコープから必要な値を退避
        successCount = manageIdBulkLogic.getSuccessCount();
        failureCount = manageIdBulkLogic.getFailureCount();
        mapsErr = manageIdBulkLogic.getMapsErr();

        LOG.trace("eventCsvInput End" + DateUtil.getSysDate());
    }
}
