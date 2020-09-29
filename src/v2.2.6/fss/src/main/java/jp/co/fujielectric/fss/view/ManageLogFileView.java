package jp.co.fujielectric.fss.view;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Named;
import javax.faces.view.ViewScoped;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.LogFileBean;
import jp.co.fujielectric.fss.data.DataTableBean;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.CommonUtil.FolderKbn;
import jp.co.fujielectric.fss.util.FileUtil;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.context.RequestContext;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 * ログファイル管理ビュー
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
@SuppressWarnings("serial")
public class ManageLogFileView extends CommonView implements Serializable {

    @Getter
    @Setter
    protected List<LogFileBean> fileInfoList;

    @Getter
    @Setter
    protected LogFileBean selectedRowData;

    protected String downloadFileName;
    @Getter
    @Setter
    protected StreamedContent file;

    @Getter
    @Setter
    protected DataTableBean historyDataTable;
    @Getter
    protected int historyRowsDefault;           //一覧表示件数
    @Getter
    protected String historyRowsTemplate;       //一覧表示件数選択肢

    public ManageLogFileView() {
        funcId = "manageLogFile";
    }

    /**
     * 画面毎の初期化
     *
     */
    @Override
    public void initFunc() {
        LOG.debug("initView start");

        // 共有ファイルフォルダのパス
//        String path = CommonUtil.getSetting("logDir") + commonBean.getRegionId() + "/";
        File dir = new File(CommonUtil.getFolderSetting(FolderKbn.LOG), commonBean.getRegionId());

        fileInfoList = new ArrayList<>();
        File[] files = dir.listFiles();

        if (files == null) {
            throw new RuntimeException("ログフォルダが設定されていません");
        }

        for (File file : files) {
            LogFileBean info = new LogFileBean(file);
            if (!info.isDirectory()) {
                fileInfoList.add(info);
            }
        }

        Collections.sort(fileInfoList, new Comparator<LogFileBean>() {
            @Override
            public int compare(LogFileBean o1, LogFileBean o2) {
                return o2.getFileName().compareTo(o1.getFileName());
            }
        });

        // dataTable情報設定
        historyDataTable = new DataTableBean();
        historyDataTable.initCommonDirectory();
        initDataTblBean(historyDataTable);

        LOG.debug("initView end");
    }

    /**
     * [dataTable]表示データにセット
     */
    private void initDataTblBean(DataTableBean dataTblBean) {
        dataTblBean.setRows(historyRowsDefault);                    //一覧表示件数
        dataTblBean.setRowsPerPageTemplate(historyRowsTemplate);    //一覧表示件数選択肢
    }

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
    }

    /**
     * ファイル選択
     */
    public void onRowSelect() {
        // ダウンロード処理
        LOG.debug("eventDownload start");

        //メッセージ用-FacesContext
        FacesContext context = FacesContext.getCurrentInstance();
        FacesMessage facesMessage = null;

        //ファイルダウンロード準備(inputStreamセットなど）
        //"ダウンロードに失敗しました。";
        String err_download = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.DOWNLOAD_FILE_FAILED, funcId);
        String err_title = "";
        String err_callbackParam = "downloadFailed";

        // ファイル読み込み
        String suffix;
        InputStream inputStream = null;
        {
            File _file = new File(selectedRowData.getFilePath());;
            suffix = FileUtil.getSuffix(_file.getName());
            try {
                downloadFileName = new String(_file.getName().getBytes("Shift_JIS"), "ISO-8859-1");
                inputStream = new FileInputStream(_file);

            } catch (UnsupportedEncodingException | FileNotFoundException e) {
                LOG.error(e.getMessage());
                inputStream = null;
                facesMessage = new FacesMessage(FacesMessage.SEVERITY_ERROR, err_title, err_download);
            }
        }

        //後処理
        if (facesMessage != null) {
            ///エラーが見つかった場合
            context.addMessage(null, facesMessage);
            LOG.debug("エラー発生..." + facesMessage.getSummary() + " " + facesMessage.getDetail());
            RequestContext.getCurrentInstance().addCallbackParam(err_callbackParam, true);
        } else if (inputStream == null) {
            ///ダウンロード無し
            file = null;
        } else {
            //ダウンロードファイルにセット
            file = new DefaultStreamedContent(inputStream, "application/" + suffix, downloadFileName);
        }

        LOG.debug("eventDownload end");
    }

}
