package jp.co.fujielectric.fss.view;

import com.ocpsoft.pretty.faces.util.StringUtils;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.DataTableBean;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.util.DateUtil;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.context.RequestContext;
import org.primefaces.model.StreamedContent;

/**
 * 管理機能共通ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class ManageCommonView extends CommonView implements Serializable {

    @Getter
    @Setter
    protected DataTableBean dataTable;
    @Getter
    @Setter
    protected boolean isSelected;
    @Getter
    @Setter
    protected String mode = "";
    @Getter
    @Setter
    protected String confirmMessage = "";

    @Getter
    private String placeholderVal;          //プレースホルダ用の日付

    @Getter
    protected int masterRowsDefault;        //一覧表示件数
    @Getter
    protected String masterRowsTemplete;    //一覧表示件数選択肢
    @Getter
    protected boolean isDownLoad = false;           //ダウンロード可否
    @Getter
    StreamedContent downloadFile;                   //ダウンロードファイル

    //callbackParam
    protected final String callbackParam_Error = "isError";
    protected final String callbackParam_Download = "isDownLoad";

    //カレンダーのプレースホルダ値
    private final String DSP_PLACEHOLDER_DATE = "dspPlaceholderDate";
    
    //追加
    protected final String RECORDFLG_CREATE = "C";
    //更新
    protected final String RECORDFLG_UPDATE = "U";
    //削除
    protected final String RECORDFLG_DELETE = "D";
    
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
        Item item;

        //一覧表示件数
        item = itemHelper.find(Item.MASTER_ROWS_DEFAULT, funcId);
        masterRowsDefault = Integer.parseInt(item.getValue());

        //一覧表示件数選択肢
        item = itemHelper.find(Item.MASTER_ROWS_TEMPLATE, funcId);
        masterRowsTemplete = item.getValue();

        // カレンダーの選択有効範囲開始日
        Date _sysDate = DateUtil.getSysDate();
        // カレンダーのプレースホルダ値（"例）" + _strDate）
        DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.JAPAN);
        String _strDate = df.format(_sysDate);
        placeholderVal = itemHelper.findDispMessageStr(DSP_PLACEHOLDER_DATE, funcId, _strDate);
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

        // 初期値...確認メッセージ
        confirmMessage = itemHelper.findDispMessageStr(Item.ConfirmMsgItemKey.CONFIRM, funcId);

        // dataTable情報設定
        dataTable = new DataTableBean();
        dataTable.initManageConfig();
        dataTable.setRows(masterRowsDefault);                   //一覧表示件数
        dataTable.setRowsPerPageTemplate(masterRowsTemplete);   //一覧表示件数選択肢

        // 情報を取得
        getItemList();
        // 選択クリア
        clearSelected();

        // 終了
        LOG.debug("initFunc end");
    }

    /**
     * 処理モード：追加
     *
     * @return 処理モード：追加
     */
    public String MODE_ADD() {
        return "add";
    }

    /**
     * 処理モード：更新
     *
     * @return 処理モード：更新
     */
    public String MODE_UPDATE() {
        return "update";
    }

    /**
     * 処理モード：削除
     *
     * @return 処理モード：削除
     */
    public String MODE_DELETE() {
        return "delete";
    }

    /**
     * 管理情報を取得
     */
    protected void getItemList() {
        //--------------
        // 各画面でOverride実装
        //--------------
    }

    /**
     * rowSelectイベントからの選択情報
     *
     * @return
     */
    public String eventRowSelect() {
        //--------------
        // 各画面でOverride実装
        //--------------

        return "";
    }

    /**
     * pageイベントからの選択情報
     */
    public void eventPage() {
        //--------------
        // 各画面でOverride実装
        //--------------

    }

    /**
     * 選択クリア
     */
    protected void clearSelected() {
        //--------------
        // 各画面でOverride実装
        //--------------

    }

    /**
     * 選択解除
     */
    public void eventSelectClear() {

        // 選択クリア
        clearSelected();
//        // ユーザ情報を取得(先にclearSelectedRowDataを行っているので、rowStyleはクリアされているはず)
//        // (selectedRowDataをクリアした場合、リストに影響が出ないよう、ユーザ情報取得を実施)
//        getItemList();
    }

    /**
     * 追加
     *
     * @return 結果(0=成功、0以外＝失敗)
     */
    protected int exeAdd() {
        //--------------
        // 各画面でOverride実装
        //--------------

        return -1;
    }

    /**
     * 更新
     *
     * @return 結果(0=成功、0以外＝失敗)
     */
    protected int exeUpdate() {
        //--------------
        // 各画面でOverride実装
        //--------------

        return -1;
    }

    /**
     * 削除
     *
     * @return 結果(0=成功、0以外＝失敗)
     */
    protected int exeDelete() {
        //--------------
        // 各画面でOverride実装
        //--------------

        return -1;
    }

    /**
     * パスワード設定通知
     *
     * @return 結果(0=成功、0以外＝失敗)
     */
    protected int exePswdChg() {
        //--------------
        // 各画面でOverride実装
        //--------------

        return -1;
    }

    /**
     * 登録・更新・削除
     */
    public void eventExec() {
        LOG.debug("eventExec:" + mode);

        //処理実行
        String inf_msg = "";
        String err_msg = "";
        int ret = -1;
        if (mode.equals(MODE_ADD())) {
            ///登録
            inf_msg = itemHelper.findDispMessageStr(Item.InfMsgItemKey.SUCCESS_ADD, funcId);
            err_msg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.FAILED_ADD, funcId);
            ret = exeAdd();
        } else if (mode.equals(MODE_UPDATE())) {
            ///更新
            inf_msg = itemHelper.findDispMessageStr(Item.InfMsgItemKey.SUCCESS_UPDATE, funcId);
            err_msg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.FAILED_UPDATE, funcId);
            ret = exeUpdate();
        } else if (mode.equals(MODE_DELETE())) {
            ///削除
            inf_msg = itemHelper.findDispMessageStr(Item.InfMsgItemKey.SUCCESS_DELETE, funcId);
            err_msg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.FAILED_DELETE, funcId);
            ret = exeDelete();
        }

        //メッセージ
        String exe_title = "";
        FacesMessage msg;
        FacesContext context = FacesContext.getCurrentInstance();
        if (ret != 0) {
            ///処理に失敗した場合
            msg = new FacesMessage(FacesMessage.SEVERITY_ERROR, exe_title, err_msg);
        } else {
            ///処理に成功した場合
            msg = new FacesMessage(FacesMessage.SEVERITY_INFO, exe_title, inf_msg);
        }
        context.addMessage(null, msg);
        //RequestContext.getCurrentInstance().addCallbackParam("msg", msg.getSummary() + msg.getDetail());
    }

    /**
     * 入力チェックコントロール
     *
     * @param _mode 処理モード(ManageConfigBean.MODE_ADD,MODE_UPDATE,MODE_DELETE)
     */
    public void eventCheckInput(String _mode) {

        //入力チェック
        boolean bret = checkInput(_mode);

        //addCallbackParam
        RequestContext req_context = RequestContext.getCurrentInstance();
        req_context.addCallbackParam("isSuccess", bret);
        if (!bret) {
            ///チェックエラーが見つかった場合
        } else {
            //処理モード
            mode = _mode;

            //確認メッセージ
            confirmMessage = getConfirmMsg();
        }
    }

    /**
     * 確認メッセージを取得します
     *
     * @return 確認メッセージ
     */
    protected String getConfirmMsg() {

        String _confirmMsg;
        if (mode.equals(MODE_ADD())) {
            _confirmMsg = itemHelper.findDispMessageStr(Item.ConfirmMsgItemKey.CONFIRM_ADD, funcId);
        } else if (mode.equals(MODE_UPDATE())) {
            _confirmMsg = itemHelper.findDispMessageStr(Item.ConfirmMsgItemKey.CONFIRM_UPDATE, funcId);
        } else if (mode.equals(MODE_DELETE())) {
            _confirmMsg = itemHelper.findDispMessageStr(Item.ConfirmMsgItemKey.CONFIRM_DELETE, funcId);
        } else {
            _confirmMsg = itemHelper.findDispMessageStr(Item.ConfirmMsgItemKey.CONFIRM, funcId);
        }
        return _confirmMsg;
    }

    /**
     * 入力チェック
     *
     * @param _mode 処理モード(ManageConfigBean.MODE_ADD,MODE_UPDATE,MODE_DELETE)
     *
     * @return チェック結果
     */
    protected boolean checkInput(String _mode) {
        //--------------
        // 各画面でOverride実装
        //--------------

        return false;
    }

    /**
     * 前後空白を除去した文字列を取得
     *
     * @param data
     * @return 前後空白を除去した文字列（nullは""）
     */
    public String getTrimString(String data) {
        if (StringUtils.isBlank(data)) {
            return "";
        }
        return data.trim();
    }

    /**
     * ＣＳＶ出力
     */
    public void eventCsvOutput() {
        //--------------
        // 各画面でOverride実装
        //--------------
    }

    /**
     * ＣＳＶ取込チェック
     */
    public void eventCsvInputCheck() {
        //--------------
        // 各画面でOverride実装
        //--------------
    }

    /**
     * ＣＳＶ取込
     */
    public void eventCsvInput() {
        //--------------
        // 各画面でOverride実装
        //--------------
    }

    /**
     * ＣＳＶフォーマット
     */
    public void eventCsvFormat() {
        //--------------
        // 各画面でOverride実装
        //--------------
    }
    
    /**
     * デフォルト日付を取得
     * 
     * @param keyItem
     * @param strTime
     * 
     * @return
     */
    public String getDefaultDate(Item keyItem, String strTime) {
        
        String outDate = "";
        
        SimpleDateFormat _sdfShort = new SimpleDateFormat("yyyy/MM/dd");
        SimpleDateFormat _sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        
        String _value = "";
        Item item = itemHelper.find(keyItem, funcId);
        if (item!=null && item.getValue()!=null) { 
            _value = item.getValue();
        }
        
        String _strDateShort;
        String _date = _value.replace("/", "").replace("-", "").toLowerCase();
        if (_value.length()<8 || _date.contains("sysdate") || _date.contains("now")) {
            Date _sysDate = new Date(System.currentTimeMillis());
            _strDateShort = _sdfShort.format(_sysDate);
        } 
        else {
            _strDateShort = _date.substring(0, 4) + "/" + _date.substring(4, 6) + "/" + _date.substring(6, 8);
        }
        
        // Date型変換
        try {
            Date formatDate = _sdf.parse(_strDateShort + " " + strTime);
            outDate = _sdf.format(formatDate);

        } catch (ParseException ex) {
        }
        
        return outDate;
    }
}
