package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.HistoryBean;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.service.SendInfoService;
import jp.co.fujielectric.fss.util.VerifyUtil;

/**
 * ファイル受信送信履歴一覧ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class ReceiveHistoryView extends HistoryCommonView implements Serializable {

    @Inject
    private SendInfoService sendInfoService;    
    @Inject
    private ReceiveInfoService receiveInfoService;
    
    //コンストラクタ
    public ReceiveHistoryView() {
        funcId = "receiveHistory";
        detailViewName = "receiveHistoryDetail";    //詳細画面View名
        listViewName = "receiveHistory";            //一覧画面View名
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

            // 受信情報を取得
            LOG.debug("#UT#initFunc findForReceiveHistory Start.");        
            List<ReceiveInfo> findDatas = receiveInfoService.findForReceiveHistory(
                    commonBean.getUserId(),
                    commonBean.getMailAddress(),
                    dataTableBean.getSearchTimeFrom(), 
                    HistoryCommonView.getSearchToDate(dataTableBean.getSearchTimeTo())
                    );
            VerifyUtil.outputUtLog(LOG, "#UT#v2.2.4", false, "findForReceiveHistory.(Userid:%s, MailAddress:%s, from:%s, to:%s, rowLimit:%d) resultSize:%d",
                    commonBean.getUserId(), commonBean.getMailAddress(), dataTableBean.getSearchTimeFrom(), dataTableBean.getSearchTimeTo(), historyRowLimit, findDatas.size());
            for (ReceiveInfo receiveInfo : findDatas) {
                ///（受信履歴）HistoryBean作成

                // 送信情報の取得
                SendInfo sinfo = sendInfoService.find(receiveInfo.getSendInfoId());
                if(sinfo == null)
                    continue;   //紐づくSendInfoが取得できないことは有り得ないが念のため取得できない場合は無視する。
                receiveInfo.setSendInfo(sinfo);            

                //HistoryBeanを作成
                HistoryBean receiveHistory = historyLogic.createReceiveHistoryBean(receiveInfo, sysDate);

                ///"承認待ち","却下済み"の場合、receiveHistoryListへ追加しない
                ///(一覧表示しない）
                if (receiveHistory.getStatus().equals(historyLogic.getDspStatusApproval())
                        || receiveHistory.getStatus().equals(historyLogic.getDspStatusRejected())) {
                    continue;
                }
    //            if(receiveInfo.getSendInfo().isApprovalFlg() || receiveInfo.getSendInfo().isCancelFlg()){
    //                //承認待ちは対象外
    //                continue;
    //            }

                //履歴表示最大件数を超える場合は件数オーバーメッセージを表示するためのフラグをTrueにして抜ける
                if(historyBeanList.size() >= historyRowLimit){
                    isLimitOver = true;
                    break;
                }
                ///receiveHistoryList追加
                historyBeanList.add(receiveHistory);
            }            
        } catch (Exception e) {
            LOG.error("#! createHistoryList. Error:{}", e.toString(), e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }
}
