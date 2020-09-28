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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import javax.faces.application.FacesMessage;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.DataTableBean;
import jp.co.fujielectric.fss.data.FileDownloadBean;
import jp.co.fujielectric.fss.data.HistoryBean;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.logic.HistoryLogic;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.FileUtil;
import jp.co.fujielectric.fss.util.TextUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import jp.co.fujielectric.fss.util.ZipUtil;
import lombok.Getter;
import lombok.Setter;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.util.InternalZipConstants;
import org.apache.commons.lang3.StringUtils;

/**
 * 履歴一覧（送信・受信・メール原本）ビュー基底クラス (BackingBean)
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
    
    /** 履歴一覧用Bean [v2.2.4]From-To検索条件追加による共通化  **/    
    @Getter
    @Setter
    @Inject
    protected HistoryBean injectHistoryBean;
    @Inject
    protected DataTableBean injectDataTableBean;
    @Getter
    @Setter
    protected HistoryBean selectedRowBean;
    @Getter
    @Setter
    protected DataTableBean dataTableBean;
    @Getter
    @Setter
    protected List<HistoryBean> historyBeanList;
    /**
     * 詳細画面View名
     */
    protected String detailViewName = "";
    /**
     * 一覧画面View名
     */
    protected String listViewName = "";
    
    //callbackParam
    protected final String callbackParam_Error = "isError";

    /**
     * 検索結果が表示件数オーバーかどうか
     */
    @Getter
    protected Boolean isLimitOver = false;
    /**
     * 表示件数オーバー時のメッセージ
     */
    @Getter
    protected String limitOverMsg = "";
    /**
     * 履歴表示最大件数
     */
    protected int historyRowLimit = 0;

    /**
     * 履歴表示最大件数デフォルト値
     */
    private static final int HISTORY_ROW_LIMIT_DEFAULT = 500;   //500件

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
       
        //historyLogicのメッセージ変数をセット
        historyLogic.initDispStatus(funcId);
    }
   
    /**
     * 一覧用Bean[dataTable]の初期化
     * @param dataTblBean
     */
    protected void initDataTblBean(DataTableBean dataTblBean) {
        dataTblBean.initHistory();
        dataTblBean.setRows(historyRowsDefault);   //一覧表示件数
        dataTblBean.setRowsPerPageTemplate(historyRowsTemplate);   //一覧表示件数選択肢
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
                    VerifyUtil.outputUtLog(LOG, "#UT_v2.2.4#", false, "zipCharsetConvert:%b, mailAddress:%s", zipCharsetConvert, mailAddress);
                } catch (Exception e) {
                    LOG.warn("configパラメータの取得に失敗したためZIP内文字コード変換をスキップします。（Key:{}, FuncId:{}, Exception:{}）", Item.ZIP_CHARSET_CONVERT_INNER, funcId, e.toString());
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
    
    /** 以降、履歴画面(送信履歴、受信履歴、メール原本）にFrom-Toの検索条件を追加して共通化 [v2.2.4] **/

    /**
     * 時刻選択用リスト（時）
     */
    @Getter
    protected List<String> hoursLst;
    /**
     * 時刻選択用リスト（分）
     */
    @Getter
    protected List<String> minutesLst;
    
    /* 画面の検索期間用変数（From,Toをそれぞれ日付(年月日)と時、分に分けて管理） */
    
    /**
     * 検索期間From（日付）
     */
    @Setter
    @Getter
    protected Date searchTimeFromDate = new Date();
    /**
     * 検索期間To（日付）
     */
    @Setter
    @Getter
    protected Date searchTimeToDate = new Date();
    /**
     * 検索期間From（時）
     */
    @Getter
    @Setter
    protected String searchTimeFromHour;
    /**
     * 検索期間From（分）
     */
    @Getter
    @Setter
    protected String searchTimeFromMinutes;
    /**
     * 検索期間To（時）
     */
    @Getter
    @Setter
    protected String searchTimeToHour;
    /**
     * 検索期間To（分）
     */
    @Getter
    @Setter
    protected String searchTimeToMinutes;

    /**
     * 履歴検索（期間条件あり）初期化
     */
    protected void initHistorySearch()
    {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));        
        try {
            //履歴表示最大件数の設定値を取得
            historyRowLimit = itemHelper.findIntWithDefault(Item.HISTORY_ROW_MAX, Item.FUNC_COMMON, HISTORY_ROW_LIMIT_DEFAULT);       
            //メッセージ取得
            String msg = itemHelper.findDispMessageStr("errHistoryLimitOver", funcId);
            if(msg.contains("%d")){
                //念の為メッセージ中に数値置換え文字列"%d"が含まれているかチェックし、含まれていれば件数に置換える
                limitOverMsg = String.format(msg, historyRowLimit);
            }

            //画面の時刻（時/分）選択肢表示用のリスト生成
            hoursLst = new ArrayList<>();
            for(int i = 0; i < 24; i++) {
                hoursLst.add(String.format("%02d", i));
            }
            minutesLst = new ArrayList<>();
            for(int i = 0; i < 60; i++) {
                minutesLst.add(String.format("%02d", i));
            }        

            //履歴一覧用Beanの初期化
            dataTableBean = initHistoryDataTableBean(injectDataTableBean);

            //日付検索条件用変数にセット
            searchTimeFromDate = dataTableBean.getSearchTimeFrom();
            searchTimeToDate = dataTableBean.getSearchTimeTo();
            //時刻はそれぞれ分解して変数にセットする
            Calendar cal = Calendar.getInstance();
            cal.setTime(dataTableBean.getSearchTimeFrom());
            searchTimeFromHour = String.format("%02d", cal.get(Calendar.HOUR_OF_DAY));
            searchTimeFromMinutes = String.format("%02d", cal.get(Calendar.MINUTE));
            cal.setTime(dataTableBean.getSearchTimeTo());
            searchTimeToHour = String.format("%02d", cal.get(Calendar.HOUR_OF_DAY));
            searchTimeToMinutes = String.format("%02d", cal.get(Calendar.MINUTE));        
        
            //履歴一覧作成
            createHistoryList();                   
        } catch (Exception e) {
            LOG.error("#! 履歴検索（期間条件あり）初期化に失敗しました。 Err:{}", e.toString());
            throw e;    //処理を継続できないため例外をスローする
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));            
        }
    }
    
    /**
     * 履歴一覧用Beanの初期化
     * @param injectDataTable
     * @return 
     */    
    private DataTableBean initHistoryDataTableBean(DataTableBean injectDataTable)
    {
        // dataTable情報設定
        DataTableBean bean = new DataTableBean();
        if (injectDataTable != null &&  !StringUtils.isBlank(injectDataTable.getReq())) {
            //詳細画面から戻ってきた場合
            //(injectDataTableBean→bean：リクエスト＝初期表示)
            historyLogic.cloneHistoryDataTable(injectDataTable, bean, HistoryLogic.REQ_INIT);
        } else {
            //初期表示の場合
            initDataTblBean(bean);
            
            //検索条件日時(From,To)の初期化
            initSearchTime(bean);
        }
        return bean;
    }
    
    /**
     * 検索条件日時(From,To)の初期化
     * @param dataTblBean
     */
    private void initSearchTime(DataTableBean dataTblBean)
    {
        //履歴検索期間初期値(日)取得
        String searchDays = itemHelper.findWithDefault(Item.HISTORY_SEARCH_DAYS_DEFAULT, Item.FUNC_COMMON, "0d");
        searchDays = "-" + searchDays;

        //From 保存期間の開始日の0:00
        dataTblBean.setSearchTimeFrom( DateUtil.getDateExcludeTime(DateUtil.addDays(sysDate, searchDays)));
        
        //To 当日の23:59
        Calendar calTo = Calendar.getInstance();
        calTo.set(Calendar.HOUR_OF_DAY, 23);
        calTo.set(Calendar.MINUTE, 59);
        calTo.set(Calendar.SECOND, 0);
        calTo.set(Calendar.MILLISECOND, 0);
        dataTblBean.setSearchTimeTo(calTo.getTime());
        
        VerifyUtil.outputUtLog(LOG, "#UT#v2.2.4", false, "searchDays:%s, From:%s, To:%s", searchDays, dataTblBean.getSearchTimeFrom().toString(), dataTblBean.getSearchTimeTo().toString());
    }

    /**
     * 検索に使用するTo日付を取得する
     * @param srcDate
     * @return 
     */
    protected static Date getSearchToDate(Date srcDate)
    {
        //指定された日時（最小単位=分）に対して、BETWEENでその分台のデータも対象となるよう
        //+1分-1ミリ秒の日時情報を返す
        Calendar cal = Calendar.getInstance();
        cal.setTime(srcDate);
        //念のため、秒・ミリ秒をクリアする
        cal.clear(Calendar.SECOND);
        cal.clear(Calendar.MILLISECOND);
        //+1分する
        cal.add(Calendar.MINUTE, 1);
        //-1mm秒する
        cal.add(Calendar.MILLISECOND, -1);
        return cal.getTime();
    }
    
    /**
     * 履歴行選択イベントの共通処理
     * @return 遷移先詳細画面View名
     */
    public String eventHistoryRowSelect()
    {
        try {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
            // 選択-uid
            injectHistoryBean.setUid(selectedRowBean.getUid());
            LOG.debug("selectedRowBean: " + selectedRowBean.getUid());

            // コンポーネント検索を行い、値を送信・受信履歴一覧[dataTable]表示データにセット
            historyLogic.setHistoryDataTable(dataTableBean, ":dispForm:historyDataTable");
            {
                ///HistoryDataTableBean値の複製
                ///(tmp→injectDataTableBean：リクエスト＝詳細画面への遷移)
                historyLogic.cloneHistoryDataTable(dataTableBean, injectDataTableBean, HistoryLogic.REQ_TO_DETAIL);
                LOG.debug("検索条件.From:{}, To:{}", injectDataTableBean.getSearchTimeFrom(), injectDataTableBean.getSearchTimeTo());
                LOG.debug("一覧表示件数(コンボボックスで選択された数値）..." + injectDataTableBean.getRows());
                LOG.debug("最初のデータを示す属性(0～)..." + injectDataTableBean.getFirst());
                LOG.debug("カレントページ..." + injectDataTableBean.getCurrentPage());
            }
            return detailViewName;  //詳細画面View名を返す
        } catch (Exception e) {
            LOG.error("#! eventRowSelect. Error:{}",e.toString(), e);
            return "";            
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));            
        }
    }

    /**
     * 検索イベントの共通処理
     * @return 
     */
    public String eventSearchHistory() {
        try {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

            //検索実行時は先頭ページ表示で再表示する[v2.2.4c]
            dataTableBean.setFirst(0);
            dataTableBean.setCurrentPage(1);
            dataTableBean.setSearchTimeFrom(getMeargeDate(searchTimeFromDate, searchTimeFromHour, searchTimeFromMinutes));
            dataTableBean.setSearchTimeTo(getMeargeDate(searchTimeToDate, searchTimeToHour, searchTimeToMinutes));

            ///HistoryDataTableBean値の複製
            ///(tmp→injectDataTableBean：リクエスト＝詳細画面への遷移)
            historyLogic.cloneHistoryDataTable(dataTableBean, injectDataTableBean, HistoryLogic.REQ_TO_DETAIL);
            
            return listViewName;  //一覧画面View名を返す
        } catch (Exception e) {
            LOG.error("#! eventSearchHistory. Error:{}",e.toString(), e);
            return "";            
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));            
        }
    }
    
    /**
     * Date変数（日付部と時刻部）のマージ
     * @param dateDay
     * @param hour
     * @param minute
     * @return 
     */
    private Date getMeargeDate(Date dateDay, String hour, String minute)
    {
        //日付部
        Calendar cal = Calendar.getInstance();
        cal.setTime(dateDay);
        //日付に時刻をマージ
        cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hour));
        cal.set(Calendar.MINUTE, Integer.parseInt(minute));
        return cal.getTime();
    }
    
    /**
     * 履歴一覧生成
     * ※実装は継承先の各画面のViewにて
     */
    protected void createHistoryList(){}
}
