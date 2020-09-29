package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.data.DataTableBean;
import jp.co.fujielectric.fss.data.HistoryBean;
import jp.co.fujielectric.fss.logic.HistoryLogic;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import lombok.Getter;
import lombok.Setter;

/**
 * 原本履歴一覧ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class OriginalHistoryView extends HistoryCommonView implements Serializable {

    @Inject
    private HistoryBean originalHistoryBean;
    @Inject
    private DataTableBean originalHistoryDataTableBean;

    @Getter
    @Setter
    private HistoryBean selectedRowData;
    @Getter
    @Setter
    private List<HistoryBean> originalHistoryList;
    @Getter
    @Setter
    private DataTableBean historyDataTable;

    @Inject
    private ReceiveInfoService receiveInfoService;

    // コンストラクタ
    public OriginalHistoryView() {
        funcId = "originalHistory";
    }
    
    /**
     * 画面毎の初期化
     *
     */
    @Override
    public void initFunc() {

        // 原本情報を取得
        originalHistoryList = new ArrayList<>();
        String receiveUserid = commonBean.getUserId();
        String receiveMailAddress = commonBean.getMailAddress();
        List<ReceiveInfo> receiveInfoDatas = receiveInfoService.findForOriginalHistory(receiveUserid, receiveMailAddress);
        for (ReceiveInfo receiveInfo : receiveInfoDatas) {
            //（原本履歴）HistoryBean作成
            HistoryBean originalHistory = historyLogic.createOriginalHistoryBean(receiveInfo, sysDate);
            // originalHistoryList追加
            originalHistoryList.add(originalHistory);
        }

        // dataTable情報設定
        historyDataTable = new DataTableBean();
        if (!originalHistoryDataTableBean.getReq().equals("")) {
            // HistoryDataTableBean値の複製
            // (originalHistoryDataTableBean→historyDataTable：リクエスト＝初期表示)
            historyLogic.cloneHistoryDataTable(originalHistoryDataTableBean, historyDataTable, HistoryLogic.REQ_INIT);
        } else {
            initDataTblBean(historyDataTable);
        }

        // TODO sortByへの設定がうまくいかない
        // （2016.08.18 sortByなしとする）
        // 終了
        LOG.debug("initView end");
    }

    /**
     * [dataTable]表示データにセット
     */
    private void initDataTblBean(DataTableBean dataTblBean) {
        dataTblBean.initReceiveHistory();
        dataTblBean.setRows(historyRowsDefault);   // 一覧表示件数
        dataTblBean.setRowsPerPageTemplate(historyRowsTemplate);   // 一覧表示件数選択肢
    }

    /**
     * rowSelectイベントからの選択情報
     *
     * @return 遷移先
     */
    public String eventRowSelect() {

        LOG.debug("eventRowSelect start");

        // 選択-uid
        originalHistoryBean.setUid(selectedRowData.getUid());
        LOG.debug("selectedRowData: " + selectedRowData.getUid());

        // コンポーネント検索を行い、値を送信・受信履歴一覧[dataTable]表示データにセット
        DataTableBean tmp = new DataTableBean();
        initDataTblBean(tmp);
        historyLogic.setHistoryDataTable(tmp, ":dispForm:originalHistoryTable");
        {
            // HistoryDataTableBean値の複製
            // (tmp→originalHistoryDataTableBean：リクエスト＝詳細画面への遷移)
            historyLogic.cloneHistoryDataTable(tmp, originalHistoryDataTableBean, HistoryLogic.REQ_TO_DETAIL);

            LOG.debug("一覧表示件数(コンボボックスで選択された数値）..." + originalHistoryDataTableBean.getRows());
            LOG.debug("最初のデータを示す属性(0～)..." + originalHistoryDataTableBean.getFirst());
            LOG.debug("カレントページ..." + originalHistoryDataTableBean.getCurrentPage());
        }

        // 終了
        LOG.debug("eventRowSelect end");
        return "originalHistoryDetail";
    }
}