package jp.co.fujielectric.fss.view;

import com.ocpsoft.pretty.faces.util.StringUtils;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.data.ManageNorticeBean;
import jp.co.fujielectric.fss.entity.Nortice;
import jp.co.fujielectric.fss.service.NorticeService;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.event.SelectEvent;

/**
 * お知らせビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class ManageNorticeView extends ManageCommonView implements Serializable {

    @Getter
    @Setter
    private ManageNorticeBean selectedRowDataTmp;
    @Getter
    @Setter
    private ManageNorticeBean selectedRowData;
    @Getter
    @Setter
    private List<ManageNorticeBean> manageNorticeList;

    //過去もOKに変更(2016.9.30)
//    @Getter
//    private Date fromMinDate;
//    @Getter
//    private Date toMinDate;
    @Getter
    protected long maxlenNorticeSubject;    //お知らせ件名の文字数Max
    @Getter
    protected long commentCharMax;          //お知らせ本文の文字数Max

    @Inject
    private NorticeService norticeService;

    //コンストラクタ
    public ManageNorticeView() {
        funcId = "manageNortice";
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
        Item item;

        super.initItems();

        //お知らせ件名の文字数Max
        item = itemHelper.find(Item.MAX_LEN_NORTICE_SUBJECT, funcId);
        maxlenNorticeSubject = Integer.parseInt(item.getValue());

        //お知らせ本文の文字数Max
        item = itemHelper.find(Item.COMMENT_CHAR_MAX, funcId);
        commentCharMax = Integer.parseInt(item.getValue());
    }

    /**
     * お知らせ情報を取得
     */
    @Override
    protected void getItemList() {

        manageNorticeList = new ArrayList<>();
        List<Nortice> datas = norticeService.findAll();
        for (Nortice nortice : datas) {
            ManageNorticeBean mng = new ManageNorticeBean();
            mng.setId(nortice.getId());
            mng.setSubject(nortice.getSubject());
            mng.setContent(nortice.getContent());
            mng.setStartTime(nortice.getStartTime());
            mng.setEndTime(nortice.getEndTime());

            // 選択
            if (selectedRowData != null && isSelected
                    && selectedRowData.getId() == nortice.getId()) {
                mng.setRowStyleSelect();
                mng.setChecked();
            }

            // 追加
            manageNorticeList.add(mng);
        }
    }

    /**
     * rowSelectイベントからの選択情報
     *
     * @return
     */
    @Override
    public String eventRowSelect() {

        LOG.debug("eventRowSelect start");

        // エラーありのコンポーネントIDのリストをクリア
        if (errComponentIdList != null) {
            errComponentIdList.clear();
        }

        // selectedRowDataが指定されていない場合
        if (selectedRowData == null) {
            ///選択クリア（本来、ここに来ることはない。万が一を考慮）
            clearSelected();
            return "";
        }

        // 選択行の情報を複写
        selectedRowData = selectedRowDataTmp.clone();

        LOG.debug("selectedRowData: " + selectedRowData.getId());
        isSelected = true;
        if (manageNorticeList != null) {
            for (ManageNorticeBean bean : manageNorticeList) {
                bean.setRowStyle("");
                bean.setCheckedOff();

                if (selectedRowData != null && selectedRowData.getId() == bean.getId()) {
                    bean.setRowStyleSelect();
                    bean.setChecked();
                }
            }
        }

        // 終了
        LOG.debug("eventRowSelect end");
        return "";
    }

    /**
     * pageイベントからの選択情報
     */
    @Override
    public void eventPage() {

        // エラーありのコンポーネントIDのリストをクリア
        if (errComponentIdList != null) {
            errComponentIdList.clear();
        }

        if (isSelected) {
            //TODO ページ変更時、選択情報は解除する？
        }
        if (selectedRowData == null) {
            selectedRowData = new ManageNorticeBean();
        }
    }

    /**
     * 選択クリア
     */
    @Override
    protected void clearSelected() {
        isSelected = false;
        if (selectedRowData == null) {
            selectedRowData = new ManageNorticeBean();
        }

        if (!errComponentIdList.isEmpty()) {
            errComponentIdList.clear();
        }

        selectedRowData.setSubject("");
        selectedRowData.setContent("");
        selectedRowData.setStartTime(null);
        selectedRowData.setEndTime(null);

        if (manageNorticeList != null) {
            for (ManageNorticeBean bean : manageNorticeList) {
                bean.setRowStyle("");
                bean.setCheckedOff();
            }
        }

//        //カレンダーの最短日を再セット
//        Date _sysDate = DateUtil.getSysDate();
//        this.fromMinDate = _sysDate;
//        this.toMinDate = _sysDate;
    }

    /**
     * 追加
     *
     * @return 結果(0=成功、0以外＝失敗)
     */
    @Override
    protected int exeAdd() {

        int ret = -1;

        try {
            // 掲載終了期限未定の場合
            if (selectedRowData.getEndTime() == null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                Date _endTime = sdf.parse("99991231 23:59:59");
                selectedRowData.setEndTime(_endTime);
            } else {
                //時刻を含まない日付
                SimpleDateFormat sdfShort = new SimpleDateFormat("yyyyMMdd");
                String dateStr = sdfShort.format(selectedRowData.getEndTime());

                // Date型変換
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                Date _endTime = sdf.parse(dateStr + " 23:59:59");
                selectedRowData.setEndTime(_endTime);
            }

            // insert
            Nortice nortice = new Nortice();
//            nortice.setId(selectedRowData.getId());               //ＩＤはＤＢ登録時に自動採番される
            nortice.setId(System.currentTimeMillis());
            nortice.setSubject(selectedRowData.getSubject());
            nortice.setContent(selectedRowData.getContent());
            nortice.setStartTime(selectedRowData.getStartTime());
            nortice.setEndTime(selectedRowData.getEndTime());
            norticeService.create(nortice);

            // 選択クリア
            clearSelected();
            // お知らせ情報を取得(先にclearSelectedRowDataを行っているので、rowStyleはクリアされているはず)
            // (selectedRowDataをクリアした場合、リストに影響が出ないよう、お知らせ情報取得を実施)
            getItemList();

            // 成功
            ret = 0;
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("お知らせ追加失敗。", ex);
        }

        //return
        return ret;
    }

    /**
     * 更新
     *
     * @return 結果(0=成功、0以外＝失敗)
     */
    @Override
    protected int exeUpdate() {

        int ret = -1;

        try {
            // 掲載終了期限未定の場合
            if (selectedRowData.getEndTime() == null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                Date _endTime = sdf.parse("99991231 23:59:59");
                selectedRowData.setEndTime(_endTime);
            } else {
                //時刻を含まない日付
                SimpleDateFormat sdfShort = new SimpleDateFormat("yyyyMMdd");
                String dateStr = sdfShort.format(selectedRowData.getEndTime());

                // Date型変換
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                Date _endTime = sdf.parse(dateStr + " 23:59:59");
                selectedRowData.setEndTime(_endTime);
            }

            // update
            Nortice nortice = new Nortice();
            nortice.setId(selectedRowData.getId());
            nortice.setSubject(selectedRowData.getSubject());
            nortice.setContent(selectedRowData.getContent());
            nortice.setStartTime(selectedRowData.getStartTime());
            nortice.setEndTime(selectedRowData.getEndTime());
            norticeService.edit(nortice);

            // ユーザ情報を取得
            getItemList();
            // 更新した状態に戻るよう、選択クリアは行わない...値確認
            LOG.debug("eventUpdate");
            LOG.debug("---isSelected=" + isSelected);
            LOG.debug("---selectedRowData=" + selectedRowData.getId() + " " + selectedRowData.getRowStyle());
            LOG.debug("---");

            // 成功
            ret = 0;
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("お知らせ更新失敗。", ex);
        }

        //return
        return ret;
    }

    /**
     * 削除
     *
     * @return 結果(0=成功、0以外＝失敗)
     */
    @Override
    protected int exeDelete() {
        int ret = -1;
        try {
            norticeService.remove(selectedRowData.getId());

            // 選択クリア
            clearSelected();
            // お知らせ情報を取得(先にclearSelectedRowDataを行っているので、rowStyleはクリアされているはず)
            // (selectedRowDataをクリアした場合、リストに影響が出ないよう、お知らせ情報取得を実施)
            getItemList();

            // 成功
            ret = 0;
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("お知らせ削除失敗。", ex);
        }

        //return
        return ret;
    }

    /**
     * 入力チェック
     *
     * @param _mode 処理モード(ManageIdBean.MODE_ADD,MODE_UPDATE,MODE_DELETE)
     *
     * @return チェック結果
     */
    @Override
    protected boolean checkInput(String _mode) {
        boolean bret = true;
        String errMsg;
        String componentId;
        String itemName;

        String frmName = "inputForm";

        try {
            FacesContext context = FacesContext.getCurrentInstance();

            //エラーリストクリア
            errComponentIdList.clear();

            //selectedRowData
            if (selectedRowData == null) {

                //errMsg = "想定しないエラーが発生しました。";
                errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.UNKNOWN, funcId);
                context.addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(""), errMsg));
                errComponentIdList.add(frmName + ":subjectInput");

                ///以降のチェックは必要なし
                return false;
            }

            //削除モードは移行のチェックなし
            if (_mode.equals(MODE_DELETE())) {
                return true;
            }

            //---------------------------
            //件名
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspSubject"); ///"件名"
            componentId = frmName + ":" + "subjectInput";
            if (StringUtils.isBlank(selectedRowData.getSubject())) {
                //errMsg = "件名が未入力です。";
                errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_REQUIRED, funcId, itemName);
            }
            if (!errMsg.isEmpty()) {
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                errComponentIdList.add(componentId);
                bret = false;
            }

            //---------------------------
            //本文
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspContent");    ///"本文"
            componentId = frmName + ":" + "contentInput";
            if (StringUtils.isBlank(selectedRowData.getContent())) {
                //errMsg = "本文が未入力です。";
                errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_REQUIRED, funcId, itemName);
            }
            if (!errMsg.isEmpty()) {
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                errComponentIdList.add(componentId);
                bret = false;
            }

            //---------------------------
            //開始日
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspStartDate");    ///"開始日"
            componentId = frmName + ":" + "startTimeInput";
            if (selectedRowData.getStartTime() == null) {
                //errMsg = "開始日が未入力です。";
                errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_REQUIRED, funcId, itemName);
//                    reqContext.addCallbackParam("startTimeInputError", true);
            }
            //過去日チェックは不要
//                if (selectedRowData.getStartTime() != null) {
//                    // 当日チェック
//                    DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.JAPAN);
//                    String _strStartTime = df.format(selectedRowData.getStartTime());
//                    String _strSysDate = df.format(DateUtil.getSysDate());
//                    if (!_strStartTime.equals(_strSysDate)) {
//                        // 過去日チェック
//                        if (selectedRowData.getStartTime().before(DateUtil.getDateExcludeTime(DateUtil.getSysDate()))) {
//                            //errMsg = "過去の日付が入力されています。本日を含め未来の日付を入力して下さい。";
//                            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.DATE_REQUIRED_FUTURE_DATE, funcId);
////                            reqContext.addCallbackParam("startTimeInputError", true);
//                        }
//                    }
//                }
            if (!errMsg.isEmpty()) {
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                errComponentIdList.add(componentId);
                bret = false;
            }

            //---------------------------
            //終了日
            //---------------------------
            //過去日チェックは不要
//            errItem = "終了日：　";
//            errMsg = "";
//            componentId = frmName + ":" + "endTimeInput";
//            if (!_mode.equals(MODE_DELETE())) {
//                if (selectedRowData.getEndTime() != null) {
//                    // 当日チェック
//                    DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.JAPAN);
//                    String _strEndTime = df.format(selectedRowData.getEndTime());
//                    String _strSysDate = df.format(DateUtil.getSysDate());
//                    if (!_strEndTime.equals(_strSysDate)) {
//                        // 過去日チェック
//                        if (selectedRowData.getEndTime().before(DateUtil.getDateExcludeTime(DateUtil.getSysDate()))) {
//                            //errMsg = "過去の日付が入力されています。本日を含め未来の日付を入力して下さい。";
//                            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.DATE_REQUIRED_FUTURE_DATE, funcId);
////                            reqContext.addCallbackParam("endTimeInputError", true);
//                        }
//                    }
//                }
//                if (!errMsg.isEmpty()) {
//                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
//                    errComponentIdList.add(componentId);
//                    bret = false;
//                }
//            }
            //---------------------------
            //開始日・終了日の前後関係
            //---------------------------
            errMsg = "";
            //itemName = getItemCaption("dspStartDate") + "・" + getItemCaption("dspEndDate");    ///"開始日・終了日"
            itemName = getItemCaption("dspPeriod");    ///"期間"
            componentId = frmName + ":" + "endTimeInput";
            if (selectedRowData.getStartTime() != null && selectedRowData.getEndTime() != null) {
                // 前後関係のチェック
                if (!checkFromTo()) {
                    //errMsg = "開始日・終了日の前後関係を確認して下さい。";
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.DATE_FROM_TO_REVERSE, funcId);
//                            reqContext.addCallbackParam("startTimeInputError", true);
//                            reqContext.addCallbackParam("endTimeInputError", true);
                }
            }
            if (!errMsg.isEmpty()) {
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                errComponentIdList.add(componentId);
                bret = false;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("お知らせチェック失敗。", ex);
            return false;
        }

        return bret;
    }

    /**
     * 開始日の入力チェック
     *
     * @param event イベント情報
     */
    public void fromDateSelect(SelectEvent event) {
        FacesContext context = FacesContext.getCurrentInstance();

        if (!checkFromTo()) {
            String errItem = getItemCaption("dspStartDate");    ///"開始日：　";
            //String errMsg = "終了日より未来の日付が選択されました。終了日を含め過去の日付を選択して下さい。";
            String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.FROMDATE_SELECT_REQUIRED_PASTDATE, funcId);
            context.addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(errItem), errMsg));
        }
    }

    /**
     * 終了日の入力チェック
     *
     * @param event イベント情報
     */
    public void toDateSelect(SelectEvent event) {
        FacesContext context = FacesContext.getCurrentInstance();

        if (!checkFromTo()) {
            String errItem = getItemCaption("dspEndDate");  ///"終了日：　";
            //String errMsg = "開始日より過去の日付が選択されました。開始日を含め未来の日付を選択して下さい。";
            String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.TODATE_SELECT_REQURED_FUTUREDATE, funcId);
            context.addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(errItem), errMsg));
        }
    }

    /**
     * 期間の前後チェック
     */
    private boolean checkFromTo() {
        if (selectedRowData.getStartTime() != null && selectedRowData.getEndTime() != null) {
            // 当日チェック
            DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.JAPAN);
            String _strStartTime = df.format(selectedRowData.getStartTime());
            String _strEndTime = df.format(selectedRowData.getEndTime());
            if (!_strStartTime.equals(_strEndTime)) {
                // 開始日が終了日よりも未来の場合
                if (selectedRowData.getStartTime().after(selectedRowData.getEndTime())) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 開始日の入力チェック（手入力用）
     *
     */
    public void fromDateInput() {
        FacesContext context = FacesContext.getCurrentInstance();

        // 入力チェック
        Date _inputDate = selectedRowData.getStartTime();
        if (_inputDate == null) {
            return;
        }

//        // 当日チェック
//        DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.JAPAN);
//        String _strInputDate = df.format(_inputDate);
//        String _strFromMinDate = df.format(DateUtil.getDateExcludeTime(DateUtil.getSysDate()));
//        if (_strInputDate.equals(_strFromMinDate)) {
//            return;
//        }
//        // 過去日チェック
//        if (_inputDate.before(DateUtil.getDateExcludeTime(DateUtil.getSysDate()))) {
//            String errItem = "開始日：　";
//            //String errMsg = "過去の日付が入力されました。本日を含め未来の日付を入力して下さい。";
//            String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.DATE_REQUIRED_FUTURE_DATE, funcId);
//            context.addMessage(null,
//                    new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
//        }
        // 開始日・終了日の前後関係チェック
        if (!checkFromTo()) {
            String errItem = getItemCaption("dspStartDate");    ///"開始日：　";
            //String errMsg = "開始日が終了日よりも未来が入力されました。終了日を含め過去を入力して下さい。";
            String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.FROMDATE_INPUT_REQUIRED_PASTDATE, funcId);
            context.addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(errItem), errMsg));
        }
    }

    /**
     * 終了日の入力チェック（手入力用）
     *
     */
    public void toDateInput() {
        FacesContext context = FacesContext.getCurrentInstance();

        // 入力チェック
        Date _inputDate = selectedRowData.getEndTime();
        if (_inputDate == null) {
            return;
        }

//        // 当日チェック
//        DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.JAPAN);
//        String _strInputDate = df.format(_inputDate);
//        String _strToMinDate = df.format(DateUtil.getDateExcludeTime(DateUtil.getSysDate()));
//        if (_strInputDate.equals(_strToMinDate)) {
//            return;
//        }
//        // 過去日チェック
//        if (_inputDate.before(DateUtil.getDateExcludeTime(DateUtil.getDateExcludeTime(DateUtil.getSysDate())))) {
//            String errItem = "終了日：　";
//            //String errMsg = "過去の日付が入力されました。本日を含め未来の日付を入力して下さい。";
//            String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.DATE_REQUIRED_FUTURE_DATE, funcId);
//            context.addMessage(null,
//                    new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
//        }
        // 開始日・終了日の前後関係チェック
        if (!checkFromTo()) {
            String errItem = getItemCaption("dspEndDate");  ///"終了日：　";
            //String errMsg = "終了日が開始日よりも過去が入力されました。開始日を含め未来を入力して下さい。";
            String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.TODATE_INPUT_REQURED_FUTUREDATE, funcId);
            context.addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(errItem), errMsg));
        }
    }

}
