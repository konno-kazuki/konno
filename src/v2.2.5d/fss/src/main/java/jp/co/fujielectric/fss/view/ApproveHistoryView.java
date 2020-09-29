package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.entity.ApproveInfo;
import jp.co.fujielectric.fss.service.ApproveInfoService;
import jp.co.fujielectric.fss.util.VerifyUtil;

/**
 * 承認履歴一覧ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class ApproveHistoryView extends HistoryCommonView implements Serializable {

    @Inject
    private ApproveInfoService approveInfoService;

    //コンストラクタ
    public ApproveHistoryView() {
        funcId = "approveHistory";
        detailViewName = "approveHistoryDetail";    //詳細画面View名
    }

    /**
     * 画面毎の初期化
     *
     */
    @Override
    public void initFunc() {
        //履歴検索用初期化
        initHistorySearch();
    }
    /**
     * 履歴一覧作成
     */
    @Override
    protected void createHistoryList(){
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "rowLimit:" + historyRowLimit));
        try {
            isLimitOver = false;
            historyBeanList = new ArrayList<>();

            // 承認情報を取得
            List<ApproveInfo> findDatas = approveInfoService.findForApproveHistory(
                    commonBean.getMailAddress()
                    );
            for (ApproveInfo approveInfo : findDatas) {
                ///（承認履歴）HistoryBean作成（送信・受信情報取得を含む）しリストに追加
                historyBeanList.add(historyLogic.createApproveHistoryBean(approveInfo, sysDate));
            }
        } catch (Exception e) {
            LOG.error("#! createHistoryList. Error:{}", e.toString(), e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }    
    }
    

    /**
     * 履歴行選択イベントの共通処理
     * @return 遷移先詳細画面View名
     */
    @Override
    public String eventHistoryRowSelect()
    {
        // 選択-uid（送信メールのユニークID＝送信情報.ID）
        injectHistoryBean.setUid(selectedRowBean.getUid());
        // (対象)承認情報ID
        injectHistoryBean.setApproveId(selectedRowBean.getApproveId());        
        
        //共通の履歴選択処理
        return super.eventHistoryRowSelect();
    }
}
