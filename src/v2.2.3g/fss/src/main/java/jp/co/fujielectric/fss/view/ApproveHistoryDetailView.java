package jp.co.fujielectric.fss.view;

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.DataTableBean;
import jp.co.fujielectric.fss.data.FileDownloadBean;
import jp.co.fujielectric.fss.data.FileInfoBean;
import jp.co.fujielectric.fss.data.HistoryBean;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.entity.ApproveInfo;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendFile;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.logic.HistoryLogic;
import jp.co.fujielectric.fss.service.ApproveInfoService;
import jp.co.fujielectric.fss.service.SendInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.context.RequestContext;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 * 承認履歴詳細ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class ApproveHistoryDetailView extends HistoryCommonView implements Serializable {

    @Inject
    @Getter
    @Setter
    private HistoryBean approveHistoryBean;
    @Inject
    private DataTableBean approveHistoryDataTableBean;

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
    private FileInfoBean selectedFileData;
    @Getter
    @Setter
    private DataTableBean save_historyDataTable;
    @Getter
    @Setter
    private boolean loginFlg;
    @Getter
    @Setter
    private String downloadFileName;
    @Getter
    @Setter
    private StreamedContent file;
    @Getter
    @Setter
    private SendInfo sendInfo;

    @Getter
    @Setter
    private ApproveInfo approveInfo;

    @Inject
    private ApproveInfoService approveInfoService;

    @Inject
    private SendInfoService sendInfoService;

    @Getter
    private boolean dispMessage = false;        //メッセージ表示フラグ（Growlや他メッセージとの区別に必要）

    @Getter
    private boolean dispMessageFile = false;    //(ファイルリスト)メッセージ表示フラグ（Growlや他メッセージとの区別に必要）

    /**
     * 承認時処理に失敗しました。
     */
    private final String ERR_APPROVED = "errApproved";

    /**
     * 承認却下時処理に失敗しました。
     */
    private final String ERR_APPROVED_REJECTED = "errApprovedRejected";

    //コンストラクタ
    public ApproveHistoryDetailView() {
        funcId = "approveHistoryDetail";
    }

    /**
     * 画面毎の初期化
     *
     */
    @Override
    public void initFunc() {

        //dataTable情報設定
        save_historyDataTable = new DataTableBean();

        //承認一覧から選択された送信情報ID、承認情報ID
        String sendInfoId = approveHistoryBean.getUid();
        String approveInfoId = approveHistoryBean.getApproveId();
        if ( StringUtils.isEmpty(sendInfoId) && StringUtils.isEmpty(approveInfoId) ) {
            // ※例外対応（ログアウト直後のinitなど）
            sendInfo = new SendInfo();
            approveInfo = new ApproveInfo();
            loginFlg = false;
        }
        else {

            if (!approveHistoryDataTableBean.getReq().equals("")) {
                ///HistoryDataTableBean値の複製
                ///(sendHistoryDataTableBean→save_historyDataTable：リクエスト＝初期表示)
                historyLogic.cloneHistoryDataTable(approveHistoryDataTableBean, save_historyDataTable, HistoryLogic.REQ_INIT);
            }

            //送信情報
            if (StringUtils.isEmpty(sendInfoId)) {
                sendInfo = new SendInfo();
            } else {
                sendInfo = sendInfoService.find(sendInfoId);
            }

            //承認情報
            if (StringUtils.isEmpty(approveInfoId)) {
                approveInfo = new ApproveInfo();
            } else {
                approveInfo = approveInfoService.find(approveInfoId);
            }

            loginFlg = true;
        }

        //SendHistoryBeanを作成
        approveHistoryBean = historyLogic.createApproveHistoryBean(sendInfo, approveInfo, sysDate);
        approveHistoryBean.contentLineDispOn();

        //送信先情報（＝受信情報）
        if (approveHistoryBean.getReceiveInfos() == null) {
            receiveInfoList = historyLogic.findReceiveInfoForSendInfoId(sendInfo.getId());
        } else {
            receiveInfoList = approveHistoryBean.getReceiveInfos();
        }

        //ファイル一覧のロード
        loadFileInfoList();

        //承認者情報
        if (approveHistoryBean.getApproveInfos() == null) {
            ///一覧表示用承認者情報（[ID]昇順にて取得）
            approveInfoList = historyLogic.findApproveInfoForSendInfoId(sendInfo.getId());
        } else {
            approveInfoList = approveHistoryBean.getApproveInfos();
        }

        //Growl用承認者情報（[承認文章]が指定されている情報。[承認日付]昇順にて取得）
        List<ApproveInfo> approveGrowlList = historyLogic.findApproveInfoForGrowl(sendInfo.getId());

        //Growl作成（送信者や、他承認者の情報をGrowlへ）
        createGrowl(approveGrowlList);

        LOG.debug("initView end");
    }

//    /**
//     * ログインフラグを取得
//     *
//     * @return ログインフラグ
//     */
//    public boolean isLoginFlg() {
//        return commonBean.isLoginFlg();
//    }

    /**
     * ファイル一覧のロード
     */
     public void loadFileInfoList() {

        fileInfoList = new ArrayList<>();

        List<SendFile> sendFileList = sendInfo.getSendFiles();
        for (SendFile sendFile : sendFileList) {
            FileInfoBean info = new FileInfoBean();

            info.setFileId(sendFile.getId());
            info.setFileName(sendFile.getFileName());
            info.setSize(sendFile.getFileSize());
            info.setFilePath(sendFile.getFilePath());

            if (sendInfo.isCancelFlg()) {
                info.setCancelFlg(true);
            }

            ///fileInfoListへ追加
            fileInfoList.add(info);
        }

        // チェックボックス全選択
        allCheck();
     }

    /**
     * 本文表示切替
     *
     * @param actionEvent
     */
    public void chgContent(ActionEvent actionEvent) {
        if (approveHistoryBean.isContentLineDisp()) {
            ///ライン表示→全表示
            approveHistoryBean.contentLineDispOff();
        } else {
            ///全表示→ライン表示
            approveHistoryBean.contentLineDispOn();
        }
    }

    /**
     * 承認者-本文表示切替
     *
     * @param actionEvent
     */
    public void chgApproveContent(ActionEvent actionEvent) {
        if (approveHistoryBean.isApproveContentLineDisp()) {
            ///ライン表示→全表示
            approveHistoryBean.approveContentLineDispOff();
        } else {
            ///全表示→ライン表示
            approveHistoryBean.approveContentLineDispOn();
        }
    }

//    /**
//     * 承認・却下ボタン表示
//     *
//     * @return 表示可否
//     */
//    public boolean isApprovedRejectedDispFlg() {
//
//        if (approveHistoryBean.isApproved() || approveHistoryBean.isRejected()) {
//            return true;
//        }
//        return false;
//    }

    /**
     * Growl作成（送信者や、他承認者の情報をGrowlへ）
     *
     * @pamam approveGrowlList  Growl用承認者情報
     *
     */
    private void createGrowl(List<ApproveInfo> approveGrowlList) {

        FacesContext context = FacesContext.getCurrentInstance();
        String summary = "";
        String detail;

        //送信者情報
        //SendInfo sendInfo = approveHistoryBean.getSendInfo();
        if (sendInfo != null) {
            ///通信欄の入力がある場合
            if (!StringUtils.isEmpty(sendInfo.getApprovalsComment())) {
                ///Growlテキスト
                detail = getGrowlText(
                    sendInfo.getSendMailAddress(),  ///送信メールアドレス
                    sendInfo.getSendUserName(),     ///送信者名
                    sendInfo.getSendTime(),         ///送信日時
                    sendInfo.getApprovalsComment()  ///(送信者入力)承認文章
                );
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, summary, detail));
            }
        }

        //他承認者情報
        if (approveGrowlList != null) {
            for (ApproveInfo info : approveGrowlList) {
                ///対象承認者データの場合、continue
                if (info.getId().equals(approveHistoryBean.getApproveId())) {
                    continue;
                }

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
                if (info.getApprovedFlg() == APPROVEDFLG_APPROVED) {
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, summary, detail));
                } else if (info.getApprovedFlg() == APPROVEDFLG_REJECTED) {
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, summary, detail));
                }
            }
        }
    }

    /**
     * ダウンロード可能か
     * @return true=ダウンロード可能
     */
    public boolean isDownloadPossible() {

        //送られたファイルの環境と、現環境が同じか
        boolean bSameSection = false;
        boolean sendSectionLgwan = sendInfo.isSectionLgwan();
        boolean currentSectionLgwan = CommonUtil.isSectionLgwan();
        if (sendSectionLgwan==currentSectionLgwan) { bSameSection = true; }

        //sendInfo.取消フラグ=false
        //且つ、送られたファイルの環境と、現環境が同じである場合、ダウンロード可能
        //(インターネット側から送られたファイルは、インターネット側で、ＬＧＷＡＮ側から送られたファイルは、ＬＧＷＡＮ側でのみ、ダウンロード可能)
        if (!sendInfo.isCancelFlg() && bSameSection) {
            return true;
        }
        return false;
    }

    /**
     * ファイル選択が行われているか
     * @return true＝選択されたファイルが存在
     */
    public boolean isSelectedFiles() {
        for (FileInfoBean fileInfo : fileInfoList) {
            if (fileInfo.isChecked()) {
                return true;
            }
        }
        return false;
    }

    /**
     * ファイル選択
     */
    public void onRowSelect() {
        if (selectedFileData.isChecked()) {
            selectedFileData.setChecked(false);
        } else {
            selectedFileData.setChecked(true);
        }
    }

    /**
     * チェックボックス全選択
     */
    public void allCheck() {
        if (isDownloadPossible()) {
            for (FileInfoBean fileInfo : fileInfoList) {
                fileInfo.setChecked(true);
            }
        }
    }

    /**
     * 承認・却下後の画面遷移
     *
     * @return 遷移先
     */
    public String actApproveAction() {

        //HistoryDataTableBean値の複製
        //(save_historyDataTable→approveHistoryDataTableBean：リクエスト＝詳細画面から戻る)
        historyLogic.cloneHistoryDataTable(save_historyDataTable, approveHistoryDataTableBean, HistoryLogic.REQ_BACK_DETAIL);

        //遷移先
        String _ret = "approveHistory";
        if (!this.isLoginFlg()) {
            _ret = "approveHistoryDetail";
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
        //(save_historyDataTable→approveHistoryDataTableBean：リクエスト＝詳細画面から戻る)
        historyLogic.cloneHistoryDataTable(save_historyDataTable, approveHistoryDataTableBean, HistoryLogic.REQ_BACK_DETAIL);

        LOG.debug("actBack end");
    }

    /**
     * 承認ボタン
     *
     * @param actionEvent
     */
    public void actApproved(ActionEvent actionEvent) {
        LOG.debug("actApproved start");

        FacesContext context = FacesContext.getCurrentInstance();
        RequestContext req_context = RequestContext.getCurrentInstance();

        String errMsg;
        String itemName;
        boolean isError = false;

        //選択-承認履歴情報
        approveHistoryBean = (HistoryBean) actionEvent.getComponent().getAttributes().get("selectedRowData");

        /**
         * 承認時処理
         */
        String sendInfoId = approveHistoryBean.getUid();
        String approveId = approveHistoryBean.getApproveId();
        Client client = null;
        try {
            client = ClientBuilder.newClient();
            WebTarget target;
            if (StringUtils.isEmpty(approveInfo.getApprovedComment())) {
                target = client.target(CommonUtil.createLocalUrl(commonBean.getRegionId(), false) + "webresources/approvetransfer")
                    .path("sendTransferApproved/{sendInfoId}/{approveId}")
                    .resolveTemplate("sendInfoId", sendInfoId)
                    .resolveTemplate("approveId", approveId);
            } else {
                target = client.target(CommonUtil.createLocalUrl(commonBean.getRegionId(), false) + "webresources/approvetransfer")
                    .path("sendTransferApproved/{sendInfoId}/{approveId}/{approvedComment}")
                    .resolveTemplate("sendInfoId", sendInfoId)
                    .resolveTemplate("approveId", approveId)
                    .resolveTemplate("approvedComment", CommonUtil.encodeBase64(approveInfo.getApprovedComment()));
            }
            String result = target.request(MediaType.APPLICATION_JSON).get(String.class);
            if (!StringUtils.isEmpty(result)) {
                LOG.error("(承認履歴詳細)承認却下時処理に失敗しました。..."+result);
                itemName = "";
                if (result.equals(Item.ErrMsgItemKey.ERR_EXCLUSION_SEND_CANCELED.getString()) ||
                    result.equals(Item.ErrMsgItemKey.ERR_EXCLUSION_APPROVED.getString())) {
                    errMsg = itemHelper.findDispMessageStr(result, funcId, "承認");
                }
                else {
                    errMsg = itemHelper.findDispMessageStr(ERR_APPROVED, funcId);
                }
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, itemName, errMsg));
                isError = true;
            }
        } finally {
            if(client != null)
                client.close();            
        }
        //メッセージ表示（Growlとの区別に必要）
        if (isError){ setDispMessageOnly(); }

        //callbackParamにセット
        req_context.addCallbackParam(callbackParam_Error, isError);
        LOG.debug("actApproved end");
    }

    /**
     * 承認却下ボタン
     *
     * @param actionEvent
     */
    public void actApprovedRejected(ActionEvent actionEvent) {
        LOG.debug("actApprovedRejected start");

        FacesContext context = FacesContext.getCurrentInstance();
        RequestContext req_context = RequestContext.getCurrentInstance();

        String errMsg;
        String itemName;
        boolean isError = false;

        //選択-承認履歴情報
        approveHistoryBean = (HistoryBean) actionEvent.getComponent().getAttributes().get("selectedRowData");

        /**
         * 却下時処理
         */
        String sendInfoId = approveHistoryBean.getUid();
        String approveId = approveHistoryBean.getApproveId();
        Client client = null;
        try {
            client = ClientBuilder.newClient();
            WebTarget target;
            if (StringUtils.isEmpty(approveInfo.getApprovedComment())) {
                target = client.target(CommonUtil.createLocalUrl(commonBean.getRegionId(), false) + "webresources/approvetransfer")
                    .path("sendTransferApprovedRejected/{sendInfoId}/{approveId}")
                    .resolveTemplate("sendInfoId", sendInfoId)
                    .resolveTemplate("approveId", approveId);
            }
            else {
                target = client.target(CommonUtil.createLocalUrl(commonBean.getRegionId(), false) + "webresources/approvetransfer")
                    .path("sendTransferApprovedRejected/{sendInfoId}/{approveId}/{approvedComment}")
                    .resolveTemplate("sendInfoId", sendInfoId)
                    .resolveTemplate("approveId", approveId)
                    .resolveTemplate("approvedComment", CommonUtil.encodeBase64(approveInfo.getApprovedComment()));
            }
            String result = target.request(MediaType.APPLICATION_JSON).get(String.class);
            if (!StringUtils.isEmpty(result)) {
                LOG.error("(承認履歴詳細)承認却下時処理に失敗しました。..."+result);
                itemName = "";
                if (result.equals(Item.ErrMsgItemKey.ERR_EXCLUSION_SEND_CANCELED.getString()) ||
                    result.equals(Item.ErrMsgItemKey.ERR_EXCLUSION_APPROVED.getString())) {
                    errMsg = itemHelper.findDispMessageStr(result, funcId, "承認却下");
                }
                else {
                    errMsg = itemHelper.findDispMessageStr(ERR_APPROVED_REJECTED, funcId);
                }
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, itemName, errMsg));
                isError = true;
            }
        } finally {
            if(client != null)
                client.close();            
        }

        //メッセージ表示（Growlとの区別に必要）
        if (isError){ setDispMessageOnly(); }

        //callbackParamにセット
        req_context.addCallbackParam(callbackParam_Error, isError);
        LOG.debug("actApprovedRejected end");
    }

    /**
     * （「ダウンロードする」クリック時）ファイルダウンロード
     */
    public void eventDownload() {
        LOG.debug("eventDownload start...");

        //メッセージ用-FacesContext
        FacesContext context = FacesContext.getCurrentInstance();

        //チェックON-ファイル
        List<File> fileList = new ArrayList<>();
        for (FileInfoBean fileInfo : fileInfoList) {
            if (fileInfo.isChecked()) {
                LOG.debug("targetFile : " + fileInfo.getFilePath());
                File _file = new File(fileInfo.getFilePath());
                fileList.add(_file);
            }
        }

        //ファイルダウンロード
        String mailAddress = (approveInfo != null ? approveInfo.getApproveMailAddress():"");
        FileDownloadBean result = executeFileDownload(fileList, approveHistoryBean.getSendInfo().getSubject(), funcId,
                mailAddress); //[248対応（簡易版)] 団体区分判定用にメールアドレス引数追加
        FacesMessage facesMessage = result.getFacesMessage();
        String suffix = result.getSuffix();
        InputStream inputStream = result.getInputStream();
        downloadFileName = result.getDownloadFileName();

        //後処理
        String err_title = "";
        String err_callbackParam = "downloadFailed";
        if (facesMessage != null) {
            ///エラーが見つかった場合
            context.addMessage(null, facesMessage); setDispMessageFileOnly();
            LOG.debug("エラー発生..." + facesMessage.getSummary() + " " + facesMessage.getDetail());
            RequestContext.getCurrentInstance().addCallbackParam(err_callbackParam, true);
        } else if (inputStream == null) {
            ///ダウンロード無し
            file = null;
        } else {
            ///"ダウンロードに失敗しました。";
            String err_download = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.DOWNLOAD_FILE_FAILED, funcId);
            try{
                ///ダウンロードファイルにセット
                file = new DefaultStreamedContent(inputStream, "application/" + suffix, downloadFileName);
            } catch (Exception e) {
                e.printStackTrace();
                LOG.error("承認履歴詳細(ダウンロード)失敗。", e);
                facesMessage = new FacesMessage(FacesMessage.SEVERITY_ERROR, err_title, err_download);
                context.addMessage(null, facesMessage); setDispMessageFileOnly();
                LOG.error("エラー発生..." + facesMessage.getSummary() + " " + facesMessage.getDetail());
                RequestContext.getCurrentInstance().addCallbackParam(err_callbackParam, true);
            }
        }

        LOG.debug("eventDownload end");
    }

    /**
     * (ファイルリスト)メッセージのみ表示（Growlや他メッセージとの区別に必要）
     */
    private void setDispMessageFileOnly() {
        dispMessage = false;
        dispMessageFile = true;
    }

    /**
     * メッセージのみ表示（Growlや他メッセージとの区別に必要）
     */
    private void setDispMessageOnly() {
        dispMessage = true;
        dispMessageFile = false;
    }
}
