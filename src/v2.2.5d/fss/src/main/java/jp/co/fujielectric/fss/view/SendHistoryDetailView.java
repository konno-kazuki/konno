package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.faces.application.FacesMessage;
import javax.faces.event.ActionEvent;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.DataTableBean;
import jp.co.fujielectric.fss.data.FileInfoBean;
import jp.co.fujielectric.fss.data.HistoryBean;
import jp.co.fujielectric.fss.entity.OnceUser;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendFile;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.logic.HistoryLogic;
import jp.co.fujielectric.fss.service.OnceUserService;
import jp.co.fujielectric.fss.service.SendInfoService;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import javax.faces.context.FacesContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import jp.co.fujielectric.fss.entity.ApproveInfo;
import jp.co.fujielectric.fss.util.CommonUtil;
import org.primefaces.context.RequestContext;

/**
 * ファイル送信履歴詳細ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class SendHistoryDetailView extends HistoryCommonView implements Serializable {

    @Getter
    @Setter
    private List<ApproveInfo> approveInfoList;
    @Getter
    @Setter
    private List<ReceiveInfo> receiveInfoList;
    @Getter
    @Setter
    private List<FileInfoBean> fileInfoList;
    @Getter
    @Setter
    private DataTableBean save_historyDataTable;

    @Inject
    private SendInfoService sendInfoService;

    @Inject
    private OnceUserService onceUserService;

    @Getter
    private boolean dispMessage = false;    //メッセージ表示フラグ（Growlとの区別に必要）

    /**
     * ファイル送信取り消し処理に失敗しました。
     */
    private final String ERR_SEND_CANCELED = "errSendCanceled";

    //コンストラクタ
    public SendHistoryDetailView() {
        funcId = "sendHistoryDetail";
    }

    /**
     * 画面毎の初期化
     *
     */
    @Override
    public void initFunc() {

        SendInfo sendInfo;

        // 送信一覧から選択された送信情報ID
        String sendInfoId = injectHistoryBean.getUid();
        save_historyDataTable = new DataTableBean();
        if ( StringUtils.isEmpty(sendInfoId) && StringUtils.isEmpty(commonBean.getOnetimeId()) ) {
            // ※例外対応（ログアウト直後のinitなど）
            sendInfo = new SendInfo();
        } else if (StringUtils.isEmpty(sendInfoId)) {
            // ワンタイムユーザ情報からsendIdを取得し、メール管理クラスにセット
            OnceUser ou = onceUserService.find(commonBean.getOnetimeId());
            if (ou == null) {
                return;     // 想定外
            }
            sendInfo = sendInfoService.find(ou.getMailId());
        } else {
            // dataTable情報設定
            if (!injectDataTableBean.getReq().equals("")) {
                ///HistoryDataTableBean値の複製
                ///(injectDataTableBean→save_historyDataTable：リクエスト＝初期表示)
                historyLogic.cloneHistoryDataTable(injectDataTableBean, save_historyDataTable, HistoryLogic.REQ_INIT);
            }
            // 送信情報を取得
            sendInfo = sendInfoService.find(sendInfoId);
        }

        //injectHistoryBeanを作成
        injectHistoryBean = historyLogic.createSendHistoryBean(sendInfo, sysDate);
        injectHistoryBean.contentLineDispOn();

        //送信先情報（＝受信情報）
        if (injectHistoryBean.getReceiveInfos() == null) {
            receiveInfoList = historyLogic.findReceiveInfoForSendInfoId(sendInfo.getId());
        } else {
            receiveInfoList = injectHistoryBean.getReceiveInfos();
        }

        //ファイル情報
        List<SendFile> sendFiles = sendInfo.getSendFiles();
        fileInfoList = new ArrayList<>();
        for (SendFile env : sendFiles) {
            FileInfoBean info = new FileInfoBean();
            info.setFileId(env.getId());
            info.setFileName(env.getFileName());
            info.setSize(env.getFileSize());
            ///fileInfoListへ追加
            fileInfoList.add(info);
        }

        //一覧表示用承認者情報（[ID]昇順にて取得）
        //(承認者情報存在判定に必要）
        approveInfoList = historyLogic.findApproveInfoForSendInfoId(sendInfo.getId());

        //Growl用承認者情報（[承認文章]が指定されている情報。[承認日付]昇順にて取得）
        List<ApproveInfo> approveGrowlList = historyLogic.findApproveInfoForGrowl(sendInfo.getId());

        //Growl作成（送信者や、他承認者の情報をGrowlへ）
        createGrowl(approveGrowlList);

        LOG.debug("initView end");
    }

    /**
     * ログインフラグを取得
     *
     * @return ログインフラグ
     */
    public boolean isLoginFlg() {
        return commonBean.isLoginFlg();
    }

    /**
     * 本文表示切替
     *
     * @param actionEvent
     */
    public void chgContent(ActionEvent actionEvent) {
        if (injectHistoryBean.isContentLineDisp()) {
            ///ライン表示→全表示
            injectHistoryBean.contentLineDispOff();
        } else {
            ///全表示→ライン表示
            injectHistoryBean.contentLineDispOn();
        }
    }

    /**
     * 承認者-本文表示切替
     *
     * @param actionEvent
     */
    public void chgApproveContent(ActionEvent actionEvent) {
        if (injectHistoryBean.isApproveContentLineDisp()) {
            ///ライン表示→全表示
            injectHistoryBean.approveContentLineDispOff();
        } else {
            ///全表示→ライン表示
            injectHistoryBean.approveContentLineDispOn();
        }
    }

    /**
     * 承認者情報が存在するか
     *
     * @return ture: 存在する, false: 存在しない
     */
    public boolean isExistApprove() {
        if (approveInfoList==null || approveInfoList.size()<1) {return false;}
        return true;
    }

    /**
     * Growl作成（承認者の情報をGrowlへ）
     *
     * @pamam approveGrowlList  Growl用承認者情報
     *
     */
    private void createGrowl(List<ApproveInfo> approveGrowlList) {

        FacesContext context = FacesContext.getCurrentInstance();
        String summary = "";
        String detail;

        //承認者情報
        if (approveGrowlList != null) {
            for (ApproveInfo info : approveGrowlList) {

                ///approveGrowlListは、通信欄の入力がないものは含まれなていない
                ///通信欄の入力がない場合、continue（表示する必要なし）
                ///if (StringUtils.isEmpty(info.getApprovedComment())) {
                ///    continue;
                ///}

                ///Growlテキスト
                detail = getGrowlText(
                    info.getApproveMailAddress(),   ///承認メールアドレス
                    info.getApproveUserName(),      ///承認者名
                    info.getApprovedTime(),         ///承認日時
                    info.getApprovedComment()       ///(承認者入力)承認文章
                );

                ///addMessage
                if (info.getApprovedFlg() == HistoryLogic.APPROVEDFLG_APPROVED) {
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, summary, detail));
                } else if (info.getApprovedFlg() == HistoryLogic.APPROVEDFLG_REJECTED) {
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, summary, detail));
                }
            }
        }
    }

    /**
     * 取り消しボタン
     *
     * @param actionEvent
     */
    public void actSendCancel(ActionEvent actionEvent) {
        LOG.debug("actSendCancel start");
        Client client = null;
        try {
            FacesContext context = FacesContext.getCurrentInstance();
            RequestContext req_context = RequestContext.getCurrentInstance();

            String errMsg;
            String itemName;
            boolean isError = false;

            //選択-送信履歴情報
            injectHistoryBean = (HistoryBean) actionEvent.getComponent().getAttributes().get("selectedRowData");

            /**
             * 取り消し処理
             */
            String sendInfoId = injectHistoryBean.getUid();
            client = ClientBuilder.newClient();
            WebTarget target = client.target(CommonUtil.createLocalUrl(commonBean.getRegionId(), false) + "webresources/approvetransfer")
                    .path("sendTransferCanceled/{sendInfoId}")
                    .resolveTemplate("sendInfoId", sendInfoId);
            String result = target.request(MediaType.APPLICATION_JSON).get(String.class);
            if (!StringUtils.isEmpty(result)) {
                LOG.error("(送信履歴詳細)ファイル送信取り消し処理に失敗しました。..."+result);
                itemName = "";
                errMsg = itemHelper.findDispMessageStr(ERR_SEND_CANCELED, funcId);
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, itemName, errMsg));
                isError = true;
            }

            //メッセージ表示（Growlとの区別に必要）
            if (isError){ dispMessage = true; }

            //callbackParamにセット
            req_context.addCallbackParam(callbackParam_Error, isError);
        } finally {
            if(client != null)
                client.close();            
            LOG.debug("actSendCancel end");
        }
    }

    /**
     * 取消後の画面遷移
     *
     * @return 遷移先
     */
    public String actSendAction() {

        //HistoryDataTableBean値の複製
        //(save_historyDataTable→injectDataTableBean：リクエスト＝詳細画面から戻る)
        historyLogic.cloneHistoryDataTable(save_historyDataTable, injectDataTableBean, HistoryLogic.REQ_BACK_DETAIL);

        //遷移先
        String _ret = "sendHistory";
        if (!this.isLoginFlg()) {
            _ret = "sendHistoryDetail";
        }
        return _ret;
    }

    /**
     * 戻るボタン
     *
     * @param actionEvent
     */
    public void actBack(ActionEvent actionEvent) {
        LOG.debug("actBack start");

        //HistoryDataTableBean値の複製
        //(save_historyDataTable→injectDataTableBean：リクエスト＝詳細画面から戻る)
        historyLogic.cloneHistoryDataTable(save_historyDataTable, injectDataTableBean, HistoryLogic.REQ_BACK_DETAIL);

        LOG.debug("actBack end");
    }
    
    /**
     * 受信情報があるかどうか
     * @return 
     */
    public boolean hasReceiveInfo(){
        return (receiveInfoList != null && !receiveInfoList.isEmpty());
    }

    /**
     * 承認者情報があるかどうか
     * @return 
     */
    public boolean hasApproveInfo(){
        return (approveInfoList != null && !approveInfoList.isEmpty());
    }
    
}
