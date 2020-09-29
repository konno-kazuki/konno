package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.data.DataTableBean;
import jp.co.fujielectric.fss.data.HistoryBean;
import jp.co.fujielectric.fss.logic.HistoryLogic;
import jp.co.fujielectric.fss.service.SendInfoService;
import lombok.Getter;
import lombok.Setter;

/**
 * 送信履歴一覧ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class SendHistoryView extends HistoryCommonView implements Serializable {

    @Inject
    private HistoryBean sendHistoryBean;
    @Inject
    private DataTableBean sendHistoryDataTableBean;

    @Getter
    @Setter
    private HistoryBean selectedRowData;
    @Getter
    @Setter
    private List<HistoryBean> sendHistoryList;
    @Getter
    @Setter
    private DataTableBean historyDataTable;

    @Inject
    private SendInfoService sendInfoService;

    //コンストラクタ
    public SendHistoryView() {
        funcId = "sendHistory";
    }

    /**
     * 画面毎の初期化
     *
     */
    @Override
    public void initFunc() {

        // 送信情報を取得
        sendHistoryList = new ArrayList<>();
        String sendUserid = commonBean.getUserId();
        String sendMailAddress = commonBean.getMailAddress();
        List<SendInfo> sendInfoDatas = sendInfoService.findForSendHistory(sendUserid, sendMailAddress);
        for (SendInfo sendInfo : sendInfoDatas) {
            ///（送信履歴）HistoryBean作成（受信情報取得を含む）
            HistoryBean sendHistory = historyLogic.createSendHistoryBean(sendInfo, sysDate);

            ///sendHistoryList追加
            sendHistoryList.add(sendHistory);
        }

        // dataTable情報設定
        historyDataTable = new DataTableBean();
        if (!sendHistoryDataTableBean.getReq().equals("")) {
            ///HistoryDataTableBean値の複製
            ///(sendHistoryDataTableBean→historyDataTable：リクエスト＝初期表示)
            historyLogic.cloneHistoryDataTable(sendHistoryDataTableBean, historyDataTable, HistoryLogic.REQ_INIT);
        } else {
            initDataTblBean(historyDataTable);
        }

        // TODO sortByへの設定がうまくいかない
        // （2016.08.18 sortByなしとする）
//        historyDataBean.setSortBy("registDate");                ///ソート項目
//        historyDataBean.sortOrderDesc();                        ///(sortByに対する)ソート＝降順
//
//        DataTable dtHoge = (DataTable)findComponent(":dispForm:sendHistoryTable");
//        if (dtHoge!=null) {
//            for (UIColumn u : dtHoge.getColumns()) {
//                if (u.getHeaderText().equals("サイズ")) {
//
//                    dtHoge.setSortColumn(u);
//                    //dtHoge.setSortBy(u);
//                    dtHoge.setSortOrder("ascending");
//
//                    break;
//                }
//            }
//        }
        // 終了
        LOG.debug("initView end");
    }

    /**
     * [dataTable]表示データにセット
     */
    private void initDataTblBean(DataTableBean dataTblBean) {
        dataTblBean.initSendHistory();
        dataTblBean.setRows(historyRowsDefault);   //一覧表示件数
        dataTblBean.setRowsPerPageTemplate(historyRowsTemplate);   //一覧表示件数選択肢
    }

    /**
     * rowSelectイベントからの選択情報
     *
     * @return 遷移先
     */
    public String eventRowSelect() {

        LOG.debug("eventRowSelect start");

        // 選択-uid
        sendHistoryBean.setUid(selectedRowData.getUid());
        LOG.debug("selectedRowData: " + selectedRowData.getUid());

        // コンポーネント検索を行い、値を送信・受信履歴一覧[dataTable]表示データにセット
        DataTableBean tmp = new DataTableBean();
        initDataTblBean(tmp);
        historyLogic.setHistoryDataTable(tmp, ":dispForm:sendHistoryTable");
        {
            ///HistoryDataTableBean値の複製
            ///(tmp→sendHistoryDataTableBean：リクエスト＝詳細画面への遷移)
            historyLogic.cloneHistoryDataTable(tmp, sendHistoryDataTableBean, HistoryLogic.REQ_TO_DETAIL);

            LOG.debug("一覧表示件数(コンボボックスで選択された数値）..." + sendHistoryDataTableBean.getRows());
            LOG.debug("最初のデータを示す属性(0～)..." + sendHistoryDataTableBean.getFirst());
            LOG.debug("カレントページ..." + sendHistoryDataTableBean.getCurrentPage());
        }

        // 終了
        LOG.debug("eventRowSelect end");
        return "sendHistoryDetail";
    }

//    /**
//     * ページ遷移
//     *
//     * @throws java.io.IOException
//     */
//    public void onRowSelect() throws IOException {
//        try {
//            if (selectedRowData.isCancel()) {
//                return;
//            }
//
//            FacesContext.getCurrentInstance().getExternalContext().dispatch("sendHistoryDetail");
//            //FacesContext.getCurrentInstance().getExternalContext().redirect("sendHistoryDetail");
//        } catch (Exception e) {
//            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error!", e.getMessage()));
//        }
//    }
}
