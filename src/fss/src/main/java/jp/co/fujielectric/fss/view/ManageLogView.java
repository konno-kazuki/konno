package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.entity.Config;
import jp.co.fujielectric.fss.entity.SqlLog;
import jp.co.fujielectric.fss.entity.ViewLog;
import jp.co.fujielectric.fss.logic.FileDownload;
import jp.co.fujielectric.fss.service.SqlLogService;
import jp.co.fujielectric.fss.service.ViewLogService;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.FileUtil;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.context.RequestContext;

/**
 * トレース出力ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class ManageLogView extends ManageCommonView implements Serializable {

    @Getter
    @Setter
    private List<Config> logTypeList = new ArrayList<>();     //出力トレースリスト

    @Getter
    @Setter
    private Date dateFrom;              //(検索)期間（From）
    @Getter
    @Setter
    private Date dateTo;                //(検索)期間 (To)
    @Getter
    @Setter
    private String selectLog;           //(検索)出力トレース
//    @Getter
//    @Setter
//    private boolean isDownLoad = false; //ダウンロード可否
//    @Getter
//    @Setter
//    StreamedContent downloadFile;       //ダウンロードファイル
    @Getter

    private String placeholderVal;

    @Inject
    private SqlLogService sqlLogService;

    @Inject
    private ViewLogService viewLogService;

    //種別名
    protected String nameLogTypeSql;
    protected String nameLogTypeView;

//    //callbackParam
//    private final String callbackParam_Error = "isError";
//    private final String callbackParam_Download = "isDownLoad";
    //ログ種別
    private final String KEY_LOG_TYPE_SQL = "1";
    private final String KEY_LOG_TYPE_VIEW = "2";
    private final String DSP_LOG_TYPE_SQL = "dspLogTypeSql";
    private final String DSP_LOG_TYPE_VIEW = "dspLogTypeView";

    //カレンダーのプレースホルダ値
    private final String DSP_PLACEHOLDER_DATE = "dspPlaceholderDate";

    //コンストラクタ
    public ManageLogView() {
        funcId = "manageLog";
    }

    /**
     * 画面毎の初期化
     *
     */
    @Override
    public void initFunc() {

        LOG.debug("initFunc start");
        LOG.debug("onetimeId = " + commonBean.getOnetimeId());
        LOG.debug("userId = " + commonBean.getUserId());

        // ログ種別名
        nameLogTypeSql = getItemCaption(DSP_LOG_TYPE_SQL);
        nameLogTypeView = getItemCaption(DSP_LOG_TYPE_VIEW);

        // 出力トレースリスト
        logTypeList.add(createSelectItem(KEY_LOG_TYPE_SQL, nameLogTypeSql));
        logTypeList.add(createSelectItem(KEY_LOG_TYPE_VIEW, nameLogTypeView));

        // カレンダーのプレースホルダ値（"例）" + _strDate）
        Date _sysDate = DateUtil.getSysDate();
        DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.JAPAN);
        String _strDate = df.format(_sysDate);
        placeholderVal = itemHelper.findDispMessageStr(DSP_PLACEHOLDER_DATE, funcId, _strDate);

        // 終了
        LOG.debug("initFunc end");
    }

    /**
     * 出力トレース変更イベント
     */
    public void eventChangeOutType() {
        LOG.debug("eventChangeOutType:出力トレース＝" + selectLog);

        FacesContext context = FacesContext.getCurrentInstance();
        RequestContext req_context = RequestContext.getCurrentInstance();

        // 入力チェック(bret:true=OK,false=NG)
        boolean bDefault = false;   ///未選択時の初期値をセットしない
        boolean bret = checkInput(context, getSchDateFrom(bDefault), getSchDateTo(bDefault));
        if (!bret) {
            ///チェックエラーが見つかった場合
            req_context.addCallbackParam(callbackParam_Error, !bret);
        }
    }

//    /**
//     * CSV出力用文字列を取得
//     *
//     * @param str
//     *
//     * @return CSV出力用文字列
//     */
//    private String getCsvString(String str) {
//
//        // CSV出力用文字列
//        String _outputStr = "";
//        if (!StringUtils.isEmpty(str)) {
//            _outputStr = str;
//
//            ///ダブルコーテーション１つに対し、ダブルコーテーションが２つになるよう変換
//            {
//                _outputStr = _outputStr.replaceAll("\"", "\"\"");
//            }
//        }
//
//        //カンマを考慮し、ダブルコーテーションで囲む
//        _outputStr = "\"" + _outputStr + "\"";
//
//        //return
//        return _outputStr;
//    }
    /**
     * 日付入力パターン
     *
     * @return 日付入力パターン
     */
    public String getDateInputPattern() {
        return "yyyy/MM/dd";
    }

//    /**
//     * 日付出力パターン
//     *
//     * @return 日付出力パターン
//     */
//    public String getDateOutPattern() {
//        return "yyyy年MM月dd日（EEE）";
//    }
    /**
     * 検索用-対象期間(開始日)を取得
     *
     * @param bDefault 未選択時の初期値をセットするか(true=未選択時、2000年1月1日)
     * @return 検索用-対象期間(開始日)
     */
    private Date getSchDateFrom(boolean bDefault) {
        if (!bDefault && dateFrom == null) {
            return null;
        }

        Date _dateFrom = dateFrom;
        if (_dateFrom == null) {
            Calendar cal = Calendar.getInstance();
            cal.set(2000, 0, 1);    //2000年1月1日
            _dateFrom = cal.getTime();
        }
        return _dateFrom;
    }

    /**
     * 検索用-対象期間(終了日)を取得
     *
     * @param bDefault 未選択時の初期値をセットするか(true=未選択時、9999年12月31日)
     * @return 検索用-対象期間(終了日)
     */
    private Date getSchDateTo(boolean bDefault) {
        if (!bDefault && dateTo == null) {
            return null;
        }

        Date _dateTo = dateTo;
        if (_dateTo == null) {
            Calendar cal = Calendar.getInstance();
            cal.set(9999, 11, 31);    //9999年12月31日
            _dateTo = cal.getTime();
        }
        return _dateTo;
    }

    /**
     * sqlLog情報取得
     *
     * @param dateFrom (検索)期間-開始
     * @param dateTo (検索)期間-終了
     *
     * @return
     */
    private FileDownload findSqlLog(Date dateFrom, Date dateTo) {

        FileDownload fd = new FileDownload();

        //sqlLog情報取得
        List<SqlLog> logDatas = sqlLogService.findForOutput(dateFrom, dateTo);
        if (logDatas != null && logDatas.size() > 0) {

            //日付出力パターン
//            SimpleDateFormat sdfLong = new SimpleDateFormat(getDateOutPattern());
            //ヘッダ
            fd.addOneData("id");    ///"ID"という文言でCSV出力した場合、[破損警告]が出るので使用しないこと
            fd.addOneData("clsName");
            fd.addOneData("lstSize");
            fd.addOneData("methodName");
            fd.addOneData("onceId");
            fd.addOneData("param");
            fd.addOneData("status");
            fd.addOneData("tStamp");
            fd.addOneData("uId");

            fd.addNewLine();

            //データ
            for (SqlLog log : logDatas) {
                ///Id
                fd.addOneData(log.getId());
                ///clsName
                fd.addOneData(FileUtil.getCsvString(log.getClsName()));
                ///lstSize
                fd.addOneData(FileUtil.getCsvString(log.getLstSize()));
                ///methodName
                fd.addOneData(FileUtil.getCsvString(log.getMethodName()));
                ///onceId
                fd.addOneData(FileUtil.getCsvString(log.getOnceId()));
                ///param
                fd.addOneData(FileUtil.getCsvString(log.getParam()));
                ///status
                fd.addOneData(FileUtil.getCsvString(log.getStatus()));
                ///tStamp
                String _tStamp = "";
                if (log.getTStamp() != null) {
                    _tStamp = String.valueOf(log.getTStamp());///sdfLong.format(log.getTStamp());
                }
                fd.addOneData(FileUtil.getCsvString(_tStamp));
                ///uId
                fd.addOneData(FileUtil.getCsvString(log.getUId()));

                ///改行
                fd.addNewLine();
            }
        }

        //return
        return fd;
    }

    /**
     * viewLog情報取得
     *
     * @param dateFrom (検索)期間-開始
     * @param dateTo (検索)期間-終了
     *
     * @return
     */
    private FileDownload findViewLog(Date dateFrom, Date dateTo) {

        FileDownload fd = new FileDownload();

        //viewLog情報取得
        List<ViewLog> logDatas = viewLogService.findForOutput(dateFrom, dateTo);
        if (logDatas != null && logDatas.size() > 0) {

            //日付出力パターン
//            SimpleDateFormat sdfLong = new SimpleDateFormat(getDateOutPattern());
            //ヘッダ
            fd.addOneData("id");    ///"ID"という文言でCSV出力した場合、[破損警告]が出るので使用しないこと
            fd.addOneData("clsName");
            fd.addOneData("methodName");
            fd.addOneData("onceId");
            fd.addOneData("param");
            fd.addOneData("ret");
            fd.addOneData("status");
            fd.addOneData("tStamp");
            fd.addOneData("uId");

            fd.addNewLine();

            //データ
            for (ViewLog log : logDatas) {
                ///Id
                fd.addOneData(log.getId());
                ///clsName
                fd.addOneData(FileUtil.getCsvString(log.getClsName()));
                ///methodName
                fd.addOneData(FileUtil.getCsvString(log.getMethodName()));
                ///onceId
                fd.addOneData(FileUtil.getCsvString(log.getOnceId()));
                ///param
                fd.addOneData(FileUtil.getCsvString(log.getParam()));
                ///ret
                fd.addOneData(FileUtil.getCsvString(log.getRet()));
                ///status
                fd.addOneData(FileUtil.getCsvString(log.getStatus()));
                ///tStamp
                String _tStamp = "";
                if (log.getTStamp() != null) {
                    _tStamp = String.valueOf(log.getTStamp());///sdfLong.format(log.getTStamp());
                }
                fd.addOneData(FileUtil.getCsvString(_tStamp));
                ///uId
                fd.addOneData(FileUtil.getCsvString(log.getUId()));

                ///改行
                fd.addNewLine();
            }
        }

        //return
        return fd;
    }

    /**
     * 入力チェック
     *
     * @param context
     * @param _dateFrom
     * @param _dateTo
     *
     * @return チェック結果(true=OK,false=NG)
     */
    protected boolean checkInput(FacesContext context, Date _dateFrom, Date _dateTo) {
        boolean bret = true;
        String errMsg;
        String componentId;
        String itemName;

        String frmName = "inputForm";

        try {

            //エラーリストクリア
            errComponentIdList.clear();

            //---------------------------
            //出力トレース
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspLogType"); ///"出力トレース"
            componentId = frmName + ":" + "selectLog";
            if (StringUtils.isEmpty(selectLog)) {
                //errMsg = "出力トレースが選択されていません。";
                errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.SELECT_REQUIRED, funcId, itemName);
            }
            if (!errMsg.isEmpty()) {
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                errComponentIdList.add(componentId);
                bret = false;
            }

            //---------------------------
            //日付
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspTargetPeriod"); ///"対象期間"
            componentId = frmName + ":" + "calTo";
            if (_dateFrom != null && _dateTo != null) {
                if (_dateFrom.getTime() > _dateTo.getTime()) {
                    //errMsg = "開始日・終了日の前後関係を確認して下さい。";
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.DATE_FROM_TO_REVERSE, funcId);

                    ///チェックエラー
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                    errComponentIdList.add(componentId);
                    bret = false;
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("トレース出力チェック失敗。", ex);
            return false;
        }

        return bret;
    }

    /**
     * ＣＳＶ出力
     */
    @Override
    public void eventCsvOutput() {
        String errMsg;
        LOG.debug("eventCsvOutput start");

        String _itemName = getItemCaption("dspBtnCsvOutput");  ///"ＣＳＶ出力：　";
        String _summary = getFacesMessageSummary(_itemName);

        //ファイル名
        Date _date = new Date();
        String _fileName = "_" + new SimpleDateFormat("yyyyMMdd").format(_date) + ".csv";

        //ダウンロード可否
        boolean _isDownload = false;
        this.isDownLoad = _isDownload;

        //検索条件
        LOG.debug("出力トレース＝" + selectLog);
        boolean bDefault = true;    ///未選択時の初期値をセットする
        Date _dateFrom = getSchDateFrom(bDefault);
        Date _dateTo = getSchDateTo(bDefault);
        LOG.debug("検索期間＝" + dateFrom + "～" + dateTo + "(" + _dateFrom + "～" + _dateTo + ")");

        FacesContext context = FacesContext.getCurrentInstance();
        RequestContext req_context = RequestContext.getCurrentInstance();

        // 入力チェック(bret:true=OK,false=NG)
        boolean bret = checkInput(context, _dateFrom, _dateTo);
        if (!bret) {
            ///チェックエラーが見つかった場合
            req_context.addCallbackParam(callbackParam_Error, !bret);
            return;
        }

        //--------------------------------
        //ダウンロード
        //--------------------------------
        FileDownload fdData = null;
        String fileName = "";
        if (selectLog.equals(KEY_LOG_TYPE_SQL)) {
            ///SQLトレース
            fdData = findSqlLog(_dateFrom, _dateTo);
            fileName = nameLogTypeSql + _fileName;
        } else if (selectLog.equals(KEY_LOG_TYPE_VIEW)) {
            ///VIEWトレース
            fdData = findViewLog(_dateFrom, _dateTo);
            fileName = nameLogTypeView + _fileName;
        }
        if (fileName.isEmpty()) {
            fileName = _fileName;
        }

        //出力件数
        int outCnt = (fdData == null) ? -1 : fdData.getListCnt();
        if (outCnt == 0) {
            ///出力データ無し
            //"出力データが見つかりません。"
            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.OUTPUTDATA_NOT_EXIST, funcId);
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, _summary, errMsg));
        } else if (outCnt < 0) {
            ///例外発生
            //"ＣＳＶファイル作成に失敗しました。"
            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.CSV_FILE_CREATE_FAILED, funcId);
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, _summary, errMsg));
        }

        //ダウンロード準備
        if (fdData != null && outCnt > 0) {
            try {
                this.downloadFile = fdData.getDownloadFile(fileName);

                ///ダウンロード可能
                _isDownload = true;
//                context.addMessage(null,
//                    new FacesMessage(FacesMessage.SEVERITY_INFO, _summary, "ダウンロードの準備が整いました。"));

            } catch (Exception ex) {
                ex.printStackTrace();
                LOG.error("トレース出力ダウンロード準備失敗。", ex);
            }
        }

        //設定
        this.isDownLoad = _isDownload;  ///ダウンロード可否
        req_context.addCallbackParam(callbackParam_Download, this.isDownLoad);
    }
}
