package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.DataTableBean;
import jp.co.fujielectric.fss.data.HistoryBean;
import jp.co.fujielectric.fss.entity.ApproveInfo;
import jp.co.fujielectric.fss.logic.HistoryLogic;
import jp.co.fujielectric.fss.service.ApproveInfoService;
import lombok.Getter;
import lombok.Setter;

/**
 * 承認履歴一覧ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class ApproveHistoryView extends HistoryCommonView implements Serializable {

    @Inject
    private HistoryBean approveHistoryBean;
    @Inject
    private DataTableBean approveHistoryDataTableBean;

    @Getter
    @Setter
    private HistoryBean selectedRowData;
    @Getter
    @Setter
    private List<HistoryBean> approveHistoryList;
    @Getter
    @Setter
    private DataTableBean historyDataTable;

    @Inject
    private ApproveInfoService approveInfoService;

    //コンストラクタ
    public ApproveHistoryView() {
        funcId = "approveHistory";
    }

    /**
     * 画面毎の初期化
     *
     */
    @Override
    public void initFunc() {

        // 承認情報を取得
        approveHistoryList = new ArrayList<>();
        String approveMailAddress = commonBean.getMailAddress();
        List<ApproveInfo> approveInfoDatas = approveInfoService.findForApproveHistory(approveMailAddress);
        for (ApproveInfo approveInfo : approveInfoDatas) {
            ///（承認履歴）HistoryBean作成（送信・受信情報取得を含む）
            HistoryBean approveHistory = historyLogic.createApproveHistoryBean(approveInfo, sysDate);

            //approveHistoryList追加
            approveHistoryList.add(approveHistory);
        }

        // dataTable情報設定
        historyDataTable = new DataTableBean();
        if (!approveHistoryDataTableBean.getReq().equals("")) {
            ///HistoryDataTableBean値の複製
            ///(approveHistoryDataTableBean→historyDataTable：リクエスト＝初期表示)
            historyLogic.cloneHistoryDataTable(approveHistoryDataTableBean, historyDataTable, HistoryLogic.REQ_INIT);
        } else {
            initDataTblBean(historyDataTable);
        }

        // 終了
        LOG.debug("initView end");
    }

    /**
     * [dataTable]表示データにセット
     */
    private void initDataTblBean(DataTableBean dataTblBean) {
        dataTblBean.initApproveHistory();
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

        // 選択-uid（送信メールのユニークID＝送信情報.ID）
        approveHistoryBean.setUid(selectedRowData.getUid());
        // (対象)承認情報ID
        approveHistoryBean.setApproveId(selectedRowData.getApproveId());

        // コンポーネント検索を行い、値を承認履歴一覧[dataTable]表示データにセット
        DataTableBean tmp = new DataTableBean();
        initDataTblBean(tmp);
        historyLogic.setHistoryDataTable(tmp, ":dispForm:approveHistoryTable");
        {
            ///HistoryDataTableBean値の複製
            ///(tmp→approveHistoryDataTableBean：リクエスト＝詳細画面への遷移)
            historyLogic.cloneHistoryDataTable(tmp, approveHistoryDataTableBean, HistoryLogic.REQ_TO_DETAIL);

            LOG.debug("一覧表示件数(コンボボックスで選択された数値）..." + approveHistoryDataTableBean.getRows());
            LOG.debug("最初のデータを示す属性(0～)..." + approveHistoryDataTableBean.getFirst());
            LOG.debug("カレントページ..." + approveHistoryDataTableBean.getCurrentPage());
        }

        // 終了
        LOG.debug("eventRowSelect end");
        return "approveHistoryDetail";
    }
}
