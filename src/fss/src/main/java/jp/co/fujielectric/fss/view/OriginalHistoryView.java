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
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.util.VerifyUtil;

/**
 * 原本履歴一覧ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class OriginalHistoryView extends HistoryCommonView implements Serializable {

    @Inject
    private ReceiveInfoService receiveInfoService;

    // コンストラクタ
    public OriginalHistoryView() {
        funcId = "originalHistory";
        detailViewName = "originalHistoryDetail";   //詳細画面View名
        listViewName = "originalHistory";            //一覧画面View名
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
            
            // 原本情報を取得
            List<ReceiveInfo> findDatas = receiveInfoService.findForOriginalHistory(
                    commonBean.getUserId(),
                    commonBean.getMailAddress(),
                    dataTableBean.getSearchTimeFrom(), 
                    HistoryCommonView.getSearchToDate(dataTableBean.getSearchTimeTo()),
                    historyRowLimit +1
                    );
            VerifyUtil.outputUtLog(LOG, "#UT#v2.2.4", false, "findForOriginalHistory.(Userid:%s, MailAddress:%s, from:%s, to:%s, rowLimit:%d) resultSize:%d",
                    commonBean.getUserId(), commonBean.getMailAddress(), dataTableBean.getSearchTimeFrom(), dataTableBean.getSearchTimeTo(), historyRowLimit, findDatas.size());
            for (ReceiveInfo receiveInfo : findDatas) {
                //履歴表示最大件数を超える場合は件数オーバーメッセージを表示するためのフラグをTrueにして抜ける
                if(historyBeanList.size() >= historyRowLimit){
                    isLimitOver = true;
                    break;
                }                
                //HistoryBeanを作成してリストに追加
                historyBeanList.add(historyLogic.createOriginalHistoryBean(receiveInfo, sysDate));
            }            
        } catch (Exception e) {
            LOG.error("#! createHistoryList. Error:{}", e.toString(), e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }
}
