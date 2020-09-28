package jp.co.fujielectric.fss.logic;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.enterprise.context.RequestScoped;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.data.CommonBean;
import jp.co.fujielectric.fss.data.DataTableBean;
import jp.co.fujielectric.fss.data.FileInfoBean;
import jp.co.fujielectric.fss.data.HistoryBean;
import jp.co.fujielectric.fss.entity.ApproveInfo;
import jp.co.fujielectric.fss.entity.OriginalFileLog;
import jp.co.fujielectric.fss.entity.ReceiveFile;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendFile;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.service.ApproveInfoService;
import jp.co.fujielectric.fss.service.OriginalFileLogService;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.service.SendInfoService;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.FileUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.primefaces.component.datatable.DataTable;

/**
 * 送信・受信履歴を管理するロジック
 */
@Named
@RequestScoped
@SuppressWarnings("serial")
public class HistoryLogic {

    public static final String REQ_INIT = "";
    /**
     * 初期表示
     */
    public static final String REQ_TO_DETAIL = "ToDetail";
    /**
     * 詳細画面へ遷移
     */
    public static final String REQ_BACK_DETAIL = "BackDetail";

    public static final String REQ_TO_SENDTRANSFERPASSWORDUNLOCK = "ToSendTransferPasswordUnlock";
    /**
     * パスワード解除画面へ遷移
     */
    public static final String REQ_BACK_SENDTRANSFERPASSWORDUNLOCK = "BackSendTransferPasswordUnlock";
    
    //承認フラグ：初期
    public static final int APPROVEDFLG_INIT = 0;
    //承認フラグ：承認
    public static final int APPROVEDFLG_APPROVED = 1;
    //承認フラグ：却下
    public static final int APPROVEDFLG_REJECTED = -1;
    
    /**
     * ステータス用表示用DispMessageキー 
     * [v2.2.4]HistoryCommonViewから移設（簡略化のため）
     */
    /** ステータス：有効期限切れ */
    private final String DSP_STATUS_EXPIRATION = "dspStatusExpiration";
    /** ステータス：無害化処理中 */
    private final String DSP_STATUS_SANITIZE = "dspStatusSanitize";
    /** ステータス：未受領 */
    private final String DSP_STATUS_RECEIVE_NONE = "dspStatusReceiveNone";
    /** ステータス：受領 */
    private final String DSP_STATUS_RECEIVE = "dspStatusReceive";
    /** ステータス：添付ファイル無 */
    private final String DSP_STATUS_FILE_EMPTY = "dspStatusFileEmpty";
    /** ステータス：パスワード解除待ち */
    private final String DSP_STATUS_DECRYPTING = "dspStatusDecrypting";
    /** ステータス：ダウンロード可能 */
    private final String DSP_STATUS_DOWNLOAD_ABLE = "dspStatusDownloadAble";
    /** ステータス：ダウンロード完了 */
    private final String DSP_STATUS_DOWNLOAD_COMPLETE = "dspStatusDownloadComplete";
    /** ステータス：取り消し */
    private final String DSP_STATUS_CANCEL = "dspStatusCancel";
    /** ステータス：承認待ち */
    private final String DSP_STATUS_APPROVAL = "dspStatusApproval";
    /** ステータス：却下済み */
    private final String DSP_STATUS_REJECTED = "dspStatusRejected";
    /** ステータス：承認済み */
    private final String DSP_STATUS_APPROVED = "dspStatusApproved";    
    /** ステータス：送信中 */
    private final String DSP_STATUS_SENDSTARTING = "dspStatusSendStarting";
    /** ステータス：送信処理失敗 */
    private final String DSP_STATUS_SENDTRANSFER_ERR = "dspStatusSendTransferErr";
    /** ステータス：承認依頼処理失敗 */
    private final String DSP_STATUS_APPROVEREQUEST_ERR = "dspStatusApproveRequestErr";
    
    /**
     * 履歴一覧へ遷移
     */

    @Inject
    private Logger LOG;

    @Inject
    private SendInfoService sendInfoService;

    @Inject
    private ReceiveInfoService receiveInfoService;

    @Inject
    private ApproveInfoService approveInfoService;

    @Inject
    private OriginalFileLogService originalFileLogService;

    @Inject
    private CommonBean commonBean;
    
    @Inject
    private ItemHelper itemHelper;
    
    @Getter
    private String dspStatusExpiration;       //ステータス：有効期限切れ
    @Getter
    private String dspStatusSanitize;         //ステータス：無害化処理中
    @Getter
    private String dspStatusReceiveNone;      //ステータス：未受領
    @Getter
    private String dspStatusReceive;          //ステータス：受領
    @Getter
    private String dspStatusFileEmpty;        //ステータス：添付ファイル無
    @Getter
    private String dspStatusDecrypting;       //ステータス：パスワード解除待ち
    @Getter
    private String dspStatusDownloadAble;     //ステータス：ダウンロード可能
    @Getter
    private String dspStatusDownloadComplete; //ステータス：ダウンロード完了
    @Getter
    private String dspStatusCancel;           //ステータス：取り消し
    @Getter
    private String dspStatusApproval;         //ステータス：承認待ち
    @Getter
    private String dspStatusRejected;         //ステータス：却下済み
    @Getter
    private String dspStatusApproved;         //ステータス：承認済み
    @Getter
    private String dspStatusSendStarting;     //ステータス：送信中
    @Getter
    private String dspStatusSendTransferErr;    //ステータス：送信処理失敗
    @Getter
    private String dspStatusApproveRequestErr;  //ステータス：承認依頼処理失敗
    
    /**
     * ステータス表示文字変数の初期化
     * [v2.2.4]HistoryCommonViewから移設（簡略化のため）
     * @param funcId
     */
    public void initDispStatus(String funcId)
    {
        //---有効期限切れ
        dspStatusExpiration = itemHelper.findDispMessageStr(DSP_STATUS_EXPIRATION, funcId);
        //---無害化処理中
        dspStatusSanitize = itemHelper.findDispMessageStr(DSP_STATUS_SANITIZE, funcId);
        //---未受領
        dspStatusReceiveNone = itemHelper.findDispMessageStr(DSP_STATUS_RECEIVE_NONE, funcId);
        //---受領
        dspStatusReceive = itemHelper.findDispMessageStr(DSP_STATUS_RECEIVE, funcId);
        //---添付ファイル無
        dspStatusFileEmpty = itemHelper.findDispMessageStr(DSP_STATUS_FILE_EMPTY, funcId);
        //---パスワード解除待ち
        dspStatusDecrypting = itemHelper.findDispMessageStr(DSP_STATUS_DECRYPTING, funcId);
        //---ダウンロード可能
        dspStatusDownloadAble = itemHelper.findDispMessageStr(DSP_STATUS_DOWNLOAD_ABLE, funcId);
        //---ダウンロード完了
        dspStatusDownloadComplete = itemHelper.findDispMessageStr(DSP_STATUS_DOWNLOAD_COMPLETE, funcId);
        //---取消
        dspStatusCancel = itemHelper.findDispMessageStr(DSP_STATUS_CANCEL, funcId);
        //---承認待ち
        dspStatusApproval = itemHelper.findDispMessageStr(DSP_STATUS_APPROVAL, funcId);
        //---却下済み
        dspStatusRejected = itemHelper.findDispMessageStr(DSP_STATUS_REJECTED, funcId);
        //---承認済み
        dspStatusApproved = itemHelper.findDispMessageStr(DSP_STATUS_APPROVED, funcId);
        //---送信中
        dspStatusSendStarting = itemHelper.findDispMessageStr(DSP_STATUS_SENDSTARTING, funcId);
        //---送信処理失敗
        dspStatusSendTransferErr = itemHelper.findDispMessageStr(DSP_STATUS_SENDTRANSFER_ERR, funcId);
        //---承認依頼処理失敗
        dspStatusApproveRequestErr = itemHelper.findDispMessageStr(DSP_STATUS_APPROVEREQUEST_ERR, funcId);
    }

    /**
     * （送信履歴）HistoryBean作成（承認情報、受信情報取得を含む）
     *
     * @param sendInfo 送信情報
     * @param sysDate システム日付
     *
     * @return （送信履歴）HistoryBean
     */
    public HistoryBean createSendHistoryBean(SendInfo sendInfo, Date sysDate) {

        HistoryBean historyBean = new HistoryBean();
        SimpleDateFormat sdfShort = new SimpleDateFormat("yyyy/MM/dd");
        SimpleDateFormat sdfLong = new SimpleDateFormat("yyyy年MM月dd日(E)");
        SimpleDateFormat sdfLongAddTime = new SimpleDateFormat("yyyy年MM月dd日(E) HH:mm");

        //sendInfo値→historyBean
        historyBean.setUid(sendInfo.getId());                       ///送信メールのユニークID
        historyBean.setSubject(sendInfo.getSubject());              ///件名
        historyBean.setContent(sendInfo.getContent());              ///本文
        if (sendInfo.getSendTime() != null) {
            ///登録日
            historyBean.setRegistDate(sendInfo.getSendTime());
            historyBean.setRegistDateShort(sdfShort.format(sendInfo.getSendTime()));
            historyBean.setRegistDateLong(sdfLong.format(sendInfo.getSendTime()));
            historyBean.setRegistDateLongAddTime(sdfLongAddTime.format(sendInfo.getSendTime()));
        }
        if (sendInfo.getExpirationTime() != null) {
            ///保存期限
            historyBean.setExpirationDate(sendInfo.getExpirationTime());
            historyBean.setExpirationDateShort(sdfShort.format(sendInfo.getExpirationTime()));
            historyBean.setExpirationDateLong(sdfLong.format(sendInfo.getExpirationTime()));
            historyBean.setExpirationDateLongAddTime(sdfLongAddTime.format(sendInfo.getExpirationTime()));
        }
        if (!StringUtils.isEmpty(sendInfo.getSendUserName())) {     // 送信元
            historyBean.setSender(sendInfo.getSendUserName());
        } else {
            historyBean.setSender(sendInfo.getSendMailAddress());
        }

        //送信ファイル
        int attach_num = 0;                                         ///添付ファイル数
        long fileSizeB = 0;                                         ///添付ファイルの合計サイズ(B)
        List<SendFile> sendFiles = sendInfo.getSendFiles();
        for (SendFile sendFile : sendFiles) {
            fileSizeB = fileSizeB + sendFile.getFileSize();
            attach_num++;
        }
        historyBean.setAttachNum(attach_num);                           ///添付ファイル数
        historyBean.setFileSize(fileSizeB);                             ///添付ファイルの合計サイズ
        historyBean.setFileSizeText(FileUtil.getSizeText(fileSizeB));   ///添付ファイルの合計サイズ（表示用）

        //sendInfo
        historyBean.setSendInfo(sendInfo);

        //承認済か（＝承認待ちフラグで判定）
        boolean isApproved = !sendInfo.isApprovalFlg();

        //状況
        historyBean.setStatus("");                                  ///状況(未受領、受領、無害化処理中、取り消し、有効期限切れ)
        historyBean.setReceiveCntText("");                          ///状況(受領人数)
        historyBean.setApproveCntText("");                          ///状況(承認人数)

        //送信情報IDから承認情報リストを取得
        List<ApproveInfo> approveInfos = findApproveInfoForSendInfoId(sendInfo.getId());
        historyBean.setApproveInfos(approveInfos);

        ///受信情報の取得
        List<ReceiveInfo> receiveInfos = findReceiveInfoForSendInfoId(sendInfo.getId());
        historyBean.setReceiveInfos(receiveInfos);
//        if (receiveInfos.size() < 1) {
//            ///状況をセットできない場合
//            historyBean.setSelected(true);              ///選択：可
//            historyBean.setSendCanceld(true);           ///送信取消：可
//        } else {
//            ///受信情報から送信履歴用-状況(等)をセット
//            historyBean = setSendStatus(historyBean);
//        }

        //承認情報、受信情報から、状況(等)をセット
        historyBean = setHistoryStatus(historyBean);

        //状況の上書き
        if (historyBean.getStatus().equals(dspStatusRejected)) {
            ///承認却下の場合、sendInfo.calcelFlg=true になっているので、
            ///状況上書きに注意
        } else if (sendInfo.isCancelFlg()) {
            ///取り消し済の場合
            historyBean.statusCancelSend(dspStatusCancel);
            //historyBean.setSendCanceld(false);        ///送信取消：不可
        } else if (sendInfo.getExpirationTime() != null
                && DateUtil.getDateExcludeTime(sendInfo.getExpirationTime()).getTime() < DateUtil.getDateExcludeTime(sysDate).getTime()) {
            ///保存期限＜システム日付の場合、有効期限が切れていると判定
            ///(時刻情報を省いて比較）
            historyBean.statusExpiration(dspStatusExpiration, isApproved);
        }else if(receiveInfos.isEmpty()){
            historyBean.setAsyncFlg(true);  //非同期処理中
            //受信情報なし（送信非同期処理が未完了）
            if(sendInfo.hasWarningFlg(SendInfo.SENDTRANSFER_FLAG)){
                //warningFlgに送信処理エラーフラグがたっている場合は送信処理中エラーとする
                //「送信処理失敗」
                historyBean.statusRejected(dspStatusSendTransferErr, 0);
            }else if(sendInfo.hasWarningFlg(SendInfo.SENDTRANSFERAPPROVAL_FLAG)){
                //warningFlgに承認依頼処理エラーフラグがたっている場合は承認依頼処理中エラーとする
                //「承認依頼処理失敗」
                historyBean.statusRejected(dspStatusApproveRequestErr, 0);
            }else{
                //非同期処理中
                //「送信処理中」
                historyBean.statusSanitize(dspStatusSendStarting);
            }
        }

        //return
        return historyBean;
    }

    /**
     * 承認情報、受信情報から、状況(等)をセット
     *
     * @param historyBean
     *
     * @return 状況(等)をセットしたHistoryBean
     */
    private HistoryBean setHistoryStatus(HistoryBean historyBean) {

        //----------------------------------
        //承認情報を検証
        //----------------------------------
        String statusApprove = "";
        int cntApproved = 0;        //承認済数
        int cntRejected = 0;        //却下済数
        if (historyBean.getApproveInfos() != null) {
            List<ApproveInfo> approveInfos = historyBean.getApproveInfos();

            ///承認状況(等)を取得
            String[] statusApproves = getStatusApprove(historyBean.getSendInfo(), approveInfos);
            if (statusApproves.length > 0) {
                statusApprove = statusApproves[0];
            }
            if (statusApproves.length > 1) {
                cntApproved = Integer.parseInt(statusApproves[1]);
            }
            if (statusApproves.length > 2) {
                cntRejected = Integer.parseInt(statusApproves[2]);
            }
        }

        //承認要の場合
        if (!StringUtils.isEmpty(statusApprove)) {
            if (statusApprove.equals(dspStatusCancel)) {
                ///状況＝取り消し⇒以降の処理は必要なし
                historyBean.statusCancelSend(dspStatusCancel);
                return historyBean;
            } else if (statusApprove.equals(dspStatusRejected)) {
                ///状況＝却下済み⇒以降の処理は必要なし
                historyBean.statusRejected(dspStatusRejected, cntRejected);
                return historyBean;
            } else if (statusApprove.equals(dspStatusApproval)) {
                ///状況＝承認待ち⇒以降の処理は必要なし
                historyBean.statusApprove(dspStatusApproval, cntApproved);
                return historyBean;
            } else {
                ///上記以外の場合、以降の処理にて、状況を設定
            }
        }

        //----------------------------------
        //受信情報を検証
        //----------------------------------
        int cntReceivedAll = 0;         //受信を要する数
        if (historyBean.getReceiveInfos() != null) {
            cntReceivedAll = historyBean.getReceiveInfos().size();
        }
        if (cntReceivedAll < 1) {
            ///状況をセットできない場合
            historyBean.setSelected(true);          ///選択：可
            historyBean.setSendCanceld(true);       ///送信取消：可
            ///return
            return historyBean;
        }

        int cntReceived = 0;            //受信済数
        boolean isSanitizing = true;    //無害化処理中か
        boolean isPasswordUnlockWaitAll = true;    //全パスワード解除待ち
        for (ReceiveInfo info : historyBean.getReceiveInfos()) {
            //パスワード解除チェック
            if(info.isPasswordUnlockWaitFlg()){
                //パスワード解除待ち
                continue;
            }
            //---------------------------------------
            //以降、パスワード解除待ち以外の場合の処理
            //---------------------------------------
            isPasswordUnlockWaitAll = false;    //パスワード解除待ちではない受信情報がある。
            
            ///(受信アドレス毎)受信ファイルを検証
            List<ReceiveFile> receiveFiles = info.getReceiveFiles();
            if(receiveFiles.isEmpty())
                continue;
            int receive_size = 0;   ///受信ファイル数
            int sanitize_size = 0;  //無害化中ファイル数
            for (ReceiveFile rcv_file : receiveFiles) {
                if (!rcv_file.isSanitizeFlg()) {
                    //無害化処理中ファイルあり。
                    sanitize_size++;
                }
                if (rcv_file.isReceiveFlg()) {
                    ///受信ファイル数計上
                    receive_size++;
                }
            }
            if(sanitize_size ==0){
                //無害化処理中が一つもない→受領待ち
                isSanitizing = false;
            }            
            if (receive_size > 0) {
                //一つでも受信していれば→受領済
                cntReceived++;
            }
        }

        //セット
        //ファイル交換でのパスワード解除実施有無
        if (isPasswordUnlockWaitAll) {
            ///状況＝パスワード解除待ち
            historyBean.statusDecrypting(dspStatusDecrypting);
        } else if (cntReceived > 0) {
            ///状況＝受領
            historyBean.statusReceive(dspStatusReceive, cntReceived);
        } else if (isSanitizing) {
            ///状況＝無害化処理中
            historyBean.statusSanitize(dspStatusSanitize);
        } else {
            ///状況＝未受領
            historyBean.statusReceiveNone(dspStatusReceiveNone);
        }

        //return
        return historyBean;
    }

//    /**
//     * 受信情報から送信履歴用-状況(等)をセット
//     *
//     * @param historyBean
//     *
//     * @return 受信情報をセットしたHistoryBean
//     */
//    public HistoryBean setSendStatus(HistoryBean historyBean) {
//
//        int cntReceived = 0;        //受信数
//        boolean isSanitizing = false;  //無害化処理中か
//
//        //受信情報を検証
//        for (ReceiveInfo info : historyBean.getReceiveInfos()) {
//            ///(受信アドレス毎)受信ファイルを検証
//            //String receiveMailAddress = info.getReceiveMailAddress();
//            List<ReceiveFile> receiveFiles = info.getReceiveFiles();
//            int receiveFiles_size = receiveFiles.size();
//            int receive_size = 0;   ///受信ファイル数
//            int sanitize_size = 0;  ///無害化済ファイル数
//            for (ReceiveFile rcv_file : receiveFiles) {
//                if (rcv_file.isReceiveFlg()) {
//                    ///受信ファイル数計上
//                    receive_size++;
//                }
//
//                if (rcv_file.isSanitizeFlg()) {
//                    ///無害化済ファイル数計上
//                    sanitize_size++;
//                }
//            }
//
//            ///一つでも無害化が済でないファイルが存在した場合、無害化中と判断
//            if (receiveFiles_size > 0 && receiveFiles_size != sanitize_size) {
//                isSanitizing = true;
//                break;
//            } ///全ファイルの受信フラグ＝false→未受領
//            else if (receiveFiles_size > 0 && receive_size == 0) {
//            } ///一つでも受信していれば→受領済
//            else if (receiveFiles_size > 0 && receive_size > 0) {
//                cntReceived++;
//            }
//        }
//
//        //セット
//        if (isSanitizing) {
//            ///状況＝無害化処理中
//            historyBean.statusSanitize(dspStatusSanitize);
//        } else if (cntReceived > 0) {
//            ///状況＝受領
//            historyBean.statusReceive(dspStatusReceive, cntReceived);
//        } else {
//            ///状況＝未受領
//            historyBean.statusReceiveNone(dspStatusReceiveNone);
//        }
//
//        return historyBean;
//    }
    /**
     * 送信情報IDから受信情報リストを取得
     *
     * @param sendInfo_id
     *
     * @return 受信情報
     */
    public List<ReceiveInfo> findReceiveInfoForSendInfoId(String sendInfo_id) {
        return receiveInfoService.findForSendInfoId(sendInfo_id);
    }

    /**
     * 送信情報IDから承認情報リストを取得
     *
     * @param sendInfo_id
     *
     * @return 承認情報
     */
    public List<ApproveInfo> findApproveInfoForSendInfoId(String sendInfo_id) {
        return approveInfoService.findForSendInfoId(sendInfo_id);
    }

    /**
     * 送信情報IDからGrowl用承認情報リストを取得
     *
     * @param sendInfo_id
     *
     * @return 承認情報
     */
    public List<ApproveInfo> findApproveInfoForGrowl(String sendInfo_id) {
        return approveInfoService.findForGrowl(sendInfo_id);
    }

    /**
     * （受信履歴）HistoryBean作成（送信情報、承認情報取得を含む）
     *
     * @param receiveInfo 受信情報
     * @param sysDate システム日付
     *
     * @return （受信履歴）HistoryBean
     */
    public HistoryBean createReceiveHistoryBean(ReceiveInfo receiveInfo, Date sysDate) {

        HistoryBean historyBean = new HistoryBean();
        SimpleDateFormat sdfShort = new SimpleDateFormat("yyyy/MM/dd");
        SimpleDateFormat sdfLong = new SimpleDateFormat("yyyy年MM月dd日(E)");
        SimpleDateFormat sdfLongAddTime = new SimpleDateFormat("yyyy年MM月dd日(E) HH:mm");

        //receiveInfo値→historyBean
        historyBean.setUid(receiveInfo.getId());        ///受信メールのユニークID
        if (receiveInfo.getSendTime() != null) {
            ///登録日
            historyBean.setRegistDate(receiveInfo.getSendTime());
            historyBean.setRegistDateShort(sdfShort.format(receiveInfo.getSendTime()));
            historyBean.setRegistDateLong(sdfLong.format(receiveInfo.getSendTime()));
            historyBean.setRegistDateLongAddTime(sdfLongAddTime.format(receiveInfo.getSendTime()));
        }

        //送信情報
        SendInfo sendInfo = receiveInfo.getSendInfo();
        if (sendInfo == null) {
            if (!StringUtils.isEmpty(receiveInfo.getSendInfoId())) {
                sendInfo = sendInfoService.find(receiveInfo.getSendInfoId());
                receiveInfo.setSendInfo(sendInfo);
            }
        }
        if (sendInfo != null) {
            historyBean.setSubject(sendInfo.getSubject());              ///件名
            historyBean.setContent(sendInfo.getContent());              ///本文
            if (!StringUtils.isEmpty(sendInfo.getSendUserName())) {     ///送信元
                historyBean.setSender(sendInfo.getSendUserName());
            } else {
                historyBean.setSender(sendInfo.getSendMailAddress());
            }
            if (sendInfo.getExpirationTime() != null) {
                ///保存期限
                historyBean.setExpirationDate(sendInfo.getExpirationTime());
                historyBean.setExpirationDateShort(sdfShort.format(sendInfo.getExpirationTime()));
                historyBean.setExpirationDateLong(sdfLong.format(sendInfo.getExpirationTime()));
                historyBean.setExpirationDateLongAddTime(sdfLongAddTime.format(sendInfo.getExpirationTime()));
            }
        }

        //sendInfo
        historyBean.setSendInfo(sendInfo);

        //承認情報
        if (sendInfo != null) {
            List<ApproveInfo> approveInfos = findApproveInfoForSendInfoId(sendInfo.getId());
            historyBean.setApproveInfos(approveInfos);
        }

        //受信ファイル
        int attach_num = 0;                             ///添付ファイル数
        long fileSizeB = 0;                             ///添付ファイルの合計サイズ(B)
        List<FileInfoBean> fileInfoList = new ArrayList<>();
        List<ReceiveFile> receiveFiles = receiveInfo.getReceiveFiles();
        for (ReceiveFile receiveFile : receiveFiles) {
            fileSizeB = fileSizeB + receiveFile.getFileSize();
            attach_num++;
            fileInfoList.add(new FileInfoBean(receiveFile));
        }
        historyBean.setAttachNum(attach_num);                           ///添付ファイル数
        historyBean.setFileSize(fileSizeB);                             ///添付ファイルの合計サイズ
        historyBean.setFileSizeText(FileUtil.getSizeText(fileSizeB));   ///添付ファイルの合計サイズ（表示用）

        ///受信ファイル情報から受信履歴用-状況(等)をセット
        historyBean.setStatus("");                      ///状況(未受領、受領、無害化処理中、取り消し、有効期限切れ)
        historyBean.setReceiveCntText("");              ///状況(受領人数)
        historyBean.setApproveCntText("");              ///状況(承認人数)
        historyBean = setReceiveStatus(historyBean, receiveInfo, fileInfoList, sysDate);

        //return
        return historyBean;
    }

    /**
     * （原本履歴）HistoryBean作成
     *
     * @param receiveInfo 受信情報
     * @param sysDate システム日付
     *
     * @return （原本履歴）HistoryBean
     */
    public HistoryBean createOriginalHistoryBean(ReceiveInfo receiveInfo, Date sysDate) {

        HistoryBean historyBean = new HistoryBean();
        SimpleDateFormat sdfShort = new SimpleDateFormat("yyyy/MM/dd");
        SimpleDateFormat sdfLong = new SimpleDateFormat("yyyy年MM月dd日(E)");
        SimpleDateFormat sdfLongAddTime = new SimpleDateFormat("yyyy年MM月dd日(E) HH:mm");

        // receiveInfo値→historyBean
        historyBean.setUid(receiveInfo.getId());        // 受信メールのユニークID
        
        
        // 登録日  [v2.2.5 sendTime→insertDateに変更]
        if (receiveInfo.getInsertDate() != null) {
            historyBean.setRegistDate(receiveInfo.getInsertDate());
            historyBean.setRegistDateShort(sdfShort.format(receiveInfo.getInsertDate()));
            historyBean.setRegistDateLong(sdfLong.format(receiveInfo.getInsertDate()));
            historyBean.setRegistDateLongAddTime(sdfLongAddTime.format(receiveInfo.getInsertDate()));
        }

        // 送信日 [v2.2.5 sendTimeを"送信日"として追加項目に表示]
        if (receiveInfo.getSendTime() != null) {
            historyBean.setSendDate(receiveInfo.getSendTime());
            historyBean.setSendDateShort(sdfShort.format(receiveInfo.getSendTime()));
            historyBean.setSendDateLong(sdfLong.format(receiveInfo.getSendTime()));
            historyBean.setSendDateLongAddTime(sdfLongAddTime.format(receiveInfo.getSendTime()));
        }
        
        // 送信情報
        SendInfo sendInfo = receiveInfo.getSendInfo();
        if (sendInfo == null) {
            if (!StringUtils.isEmpty(receiveInfo.getSendInfoId())) {
                sendInfo = sendInfoService.find(receiveInfo.getSendInfoId());
                receiveInfo.setSendInfo(sendInfo);
            }
        }
        if (sendInfo != null) {
            historyBean.setSubject(sendInfo.getSubject());              // 件名
            historyBean.setContent(sendInfo.getContent());              // 本文
            if (!StringUtils.isEmpty(sendInfo.getSendUserName())) {     // 送信元
                historyBean.setSender(sendInfo.getSendUserName());
            } else {
                historyBean.setSender(sendInfo.getSendMailAddress());
            }
            if (sendInfo.getExpirationTime() != null) {
                // 保存期限
                historyBean.setExpirationDate(sendInfo.getExpirationTime());
                historyBean.setExpirationDateShort(sdfShort.format(sendInfo.getExpirationTime()));
                historyBean.setExpirationDateLong(sdfLong.format(sendInfo.getExpirationTime()));
                historyBean.setExpirationDateLongAddTime(sdfLongAddTime.format(sendInfo.getExpirationTime()));
            }
        }

        // 原本ファイル
        int attach_num = 0;                             // 添付ファイル数
        long fileSizeB = 0;                             // 添付ファイルの合計サイズ(B)
        List<FileInfoBean> fileInfoList = new ArrayList<>();
        List<ReceiveFile> receiveFiles = receiveInfo.getReceiveFiles();
        for (ReceiveFile receiveFile : receiveFiles) {
            List<SendFile> sendFiles = receiveInfo.getSendInfo().getSendFiles();
            for (SendFile sendFile : sendFiles) {
                if (receiveFile.getFileName().equals(sendFile.getFileName())) {
                    // 添付ファイルの合計サイズは無害化前ファイル(sendFile)のサイズより算出
                    fileSizeB = fileSizeB + sendFile.getFileSize();
                    attach_num++;
                    fileInfoList.add(new FileInfoBean(receiveFile, sendFile));
                }
            }
        }
        historyBean.setAttachNum(attach_num);                           // 添付ファイル数
        historyBean.setFileSize(fileSizeB);                             // 添付ファイルの合計サイズ
        historyBean.setFileSizeText(FileUtil.getSizeText(fileSizeB));   // 添付ファイルの合計サイズ（表示用）

//        // 受信情報から送信先情報をセット
//        List<ReceiveInfo> receiveInfos = findReceiveInfoForSendInfoId(receiveInfo.getSendInfoId());
//        historyBean.setReceiveInfos(receiveInfos);

        // 受信ファイル情報から受信履歴用-状況(等)をセット
        historyBean.setStatus("");                      // 状況(未受領、受領、無害化処理中、取り消し、有効期限切れ)
        historyBean.setReceiveCntText("");              // 状況(受領人数)
        historyBean.setApproveCntText("");              // 状況(承認人数)
        historyBean = setReceiveStatus(historyBean, receiveInfo, fileInfoList, sysDate);

        return historyBean;
    }

    /**
     * 承認状況(等)を取得
     *
     * @param sendInfo
     * @param approveInfos
     *
     * @return 承認状況(等)データ群（0=承認状況、1=承認済数、2=却下済数）
     */
    private String[] getStatusApprove(SendInfo sendInfo, List<ApproveInfo> approveInfos) {

        String statusApprove = "";
        int cntApprovedAll = 0;     //承認を要する人数
        int cntApproved = 0;        //承認済数
        int cntRejected = 0;        //却下済数

        //人数計上
        if (approveInfos != null) {
            cntApprovedAll = approveInfos.size();  ///承認を要する人数

            for (ApproveInfo approveInfo : approveInfos) {
                if (approveInfo.getApprovedFlg() == APPROVEDFLG_APPROVED) {
                    ///承認済数を計上
                    cntApproved++;
                } else if (approveInfo.getApprovedFlg() == APPROVEDFLG_REJECTED) {
                    ///却下済数を計上
                    cntRejected++;
                }
            }
        }

        //ステータス
        if (cntApprovedAll > 0) {
            boolean bCancelFlg = sendInfo.isCancelFlg();
            boolean bApprovalFlg = sendInfo.isApprovalFlg();
            if (bCancelFlg && bApprovalFlg) {
                ///承認者-却下
                statusApprove = dspStatusRejected;
            } else if (bCancelFlg && !bApprovalFlg) {
                ///送信者-取り消し
                statusApprove = dspStatusCancel;
            } else if (!bCancelFlg && bApprovalFlg) {
                ///承認者待ち
                statusApprove = dspStatusApproval;
            } else {
                ///承認済
                statusApprove = dspStatusApproved;
            }
        }

        //statusApproves
        String[] statusApproves = new String[3];
        statusApproves[0] = statusApprove;
        statusApproves[1] = String.valueOf(cntApproved);
        statusApproves[2] = String.valueOf(cntRejected);

        return statusApproves;
    }

    /**
     * 受信ファイル情報から受信履歴用-状況(等)をセット
     *
     * @param historyBean
     * @param receiveInfo
     * @param fileInfoList
     * @param sysDate システム日付
     *
     * @return 受信情報をセットしたHistoryBean
     */
    public HistoryBean setReceiveStatus(HistoryBean historyBean,
            ReceiveInfo receiveInfo, List<FileInfoBean> fileInfoList, Date sysDate) {

        //承認情報
        String statusApprove = "";
        int cntApproved = 0;        //承認済数
        int cntRejected = 0;        //却下済数
        if (historyBean.getApproveInfos() != null) {
            List<ApproveInfo> approveInfos = historyBean.getApproveInfos();

            ///承認状況(等)を取得
            String[] statusApproves = getStatusApprove(historyBean.getSendInfo(), approveInfos);
            if (statusApproves.length > 0) {
                statusApprove = statusApproves[0];
            }
            if (statusApproves.length > 1) {
                cntApproved = Integer.parseInt(statusApproves[1]);
            }
            if (statusApproves.length > 2) {
                cntRejected = Integer.parseInt(statusApproves[2]);
            }
        }

        //承認要の場合
        if (!StringUtils.isEmpty(statusApprove)) {
            if (statusApprove.equals(dspStatusCancel)) {
                ///状況＝取り消し⇒以降の処理は必要なし
                historyBean.statusCancelReceive(dspStatusCancel);
                return historyBean;
            } else if (statusApprove.equals(dspStatusRejected)) {
                ///状況＝却下済み⇒以降の処理は必要なし
                historyBean.statusRejected(dspStatusRejected, cntRejected);
                return historyBean;
            } else if (statusApprove.equals(dspStatusApproval)) {
                ///状況＝承認待ち⇒以降の処理は必要なし
                historyBean.statusApprove(dspStatusApproval, cntApproved);
                return historyBean;
            } else {
                ///上記以外の場合、以降の処理にて、状況を設定
            }
        }

        //承認済か
        boolean isApproved = true;  ///受信履歴なので承認済と判断

        //送信情報
        SendInfo sendInfo = receiveInfo.getSendInfo();
        if (sendInfo != null && sendInfo.isCancelFlg()) {
            ///状況＝取り消し
            historyBean.statusCancelReceive(dspStatusCancel);
            return historyBean;
        }
        if (sendInfo != null && sendInfo.getExpirationTime() != null
                && DateUtil.getDateExcludeTime(sendInfo.getExpirationTime()).getTime() < DateUtil.getDateExcludeTime(sysDate).getTime()) {
            ///保存期限＜システム日付の場合、有効期限が切れていると判定
            ///(時刻を省いた日付で比較）
            historyBean.statusExpiration(dspStatusExpiration, isApproved);
            return historyBean;
        }

        //添付ファイル
        int file_count = 0;
        if (fileInfoList != null) {
            file_count = fileInfoList.size();
        }
        if (file_count < 1) {
            ///状況＝添付ファイル無
            historyBean.statusFileEmpty(dspStatusFileEmpty);
            return historyBean;
        }

//        //ファイル交換でのパスワード解除実施有無
//        Item item = itemHelper.find(Item.PASSWORD_UNLOCK_FLG, "sendTransfer");
//        boolean isConfigPasswordUnlockFlg = Boolean.valueOf(item.getValue());

        //セット
        boolean isDecrypting = false;
        boolean isSanitizing = false;
        boolean isDownloadComplete = true;
        for (FileInfoBean fileInfo : fileInfoList) {
            // パスワード付ファイルが一つでも存在、且つ、UploadGroupInfoが未作成の場合、パスワード解除待ち
//            if (isConfigPasswordUnlockFlg && receiveInfo.isPasswordUnlockWaitFlg()) {
            if (receiveInfo.isPasswordUnlockWaitFlg()) {
                isDecrypting = true;
            }
            ///対象で、且つ、isSanitizeFlg＝false(無害化がまだ）が一つでも存在したら、無害化処理中
            if (fileInfo.isTargetFlg() && !fileInfo.isSanitizeFlg()) {
                isSanitizing = true;
            }
            ///一つでもダウンロードがされていないファイルが存在する場合、ダウンロード完了ではない（ダウンロード可能ファイルに限る）
            if (!fileInfo.isReceiveFlg() && (fileInfo.isFileNormal())) {
                isDownloadComplete = false;
            }
        }

        if (isDecrypting) {
            ///状況＝パスワード解除待ち
            historyBean.statusDecrypting(dspStatusDecrypting);
        } else if (isSanitizing) {
            ///状況＝無害化処理中
            historyBean.statusSanitize(dspStatusSanitize);
        } else if (isDownloadComplete) {
            ///状況＝ダウンロード完了
            historyBean.statusDownloadComplete(dspStatusDownloadComplete);
        } else {
            ///状況＝ダウンロード可能
            historyBean.statusDownloadAble(dspStatusDownloadAble);
        }

        //return
        return historyBean;
    }

    /**
     * コンポーネント検索を行い、値を送信・受信履歴一覧[dataTable]表示データにセット （値設定）
     *
     * @param tableBean
     * @param findComponent
     */
    public void setHistoryDataTable(DataTableBean tableBean, String findComponent) {
        DataTable dtHoge = (DataTable) findComponent(findComponent);
        if (dtHoge != null) {
            int pg = dtHoge.getPage();

            ///一覧表示件数(コンボボックスで選択された数値）
            tableBean.setRows(dtHoge.getRows());
            if (pg >= 0) {
                ///カレントページ
                tableBean.setCurrentPage(dtHoge.getPage() + 1);

                ///最初のデータを示す属性(0～)
                int first = dtHoge.getPage() * dtHoge.getRows();
                tableBean.setFirst(first);

                ///ページナビゲーター、ページング表示内容
                String paginatorTemplate = dtHoge.getPaginatorTemplate();
                if (paginatorTemplate != null) {
                    tableBean.setPaginatorTemplate(paginatorTemplate);
                    tableBean.setPaginator(true);
                }
            }
        }
    }

    /**
     * コンポーネント検索
     *
     * @param componentId
     * @return UIComponent
     */
    public UIComponent findComponent(String componentId) {
        FacesContext currentInstance = FacesContext.getCurrentInstance();
        return currentInstance.getViewRoot().findComponent(componentId);
    }

    /**
     * HistoryDataTableBean値の複製
     *
     * @param base 複写元
     * @param to 複写先
     * @param req リクエスト
     */
    public void cloneHistoryDataTable(DataTableBean base, DataTableBean to, String req) {
        base.copyBean(to);
        to.setReq(req);
    }

    /**
     * ダウンロードログをDBに登録
     *
     * @param fileInfo
     */
    @Transactional
    private void InsertOriginalFileLog(FileInfoBean fileInfo) {
        OriginalFileLog originalFileLog = new OriginalFileLog();
        originalFileLog.setFileName(fileInfo.getFileName());
        originalFileLog.setOnceId(commonBean.getOnetimeId());
        originalFileLog.setSendFileId(fileInfo.getOriginalFileId());
        originalFileLog.setSendInfoId(fileInfo.getOriginalInfoId());
        originalFileLog.setTStamp(new Date());
        originalFileLog.setUId(commonBean.getUserId());
        // DBに登録
        originalFileLogService.create(originalFileLog);
    }

    /**
     * 更新処理
     *
     * @param fileInfoList
     * @param receiveInfoId
     * @return 最新のReceiveInfo
     */
    @Transactional
    public ReceiveInfo downloadUpdate(List<FileInfoBean> fileInfoList, String receiveInfoId) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "receiveInfoId:" + receiveInfoId));

        //システム日付（"yyyy/mm/dd h:mm:ss"）
        Date _sysDate = new Date();

        //最新のReceiveInfoを取得する（古い情報で上書きしないように）
        ReceiveInfo receiveInfo = receiveInfoService.findWithRelationTables(receiveInfoId);
        
        // 更新
        for (FileInfoBean fileInfo : fileInfoList) {
            if (fileInfo.isChecked()) {
                // 更新対象-ファイルID
                String _fileId = fileInfo.getFileId();

                //カウントアップ処理(ReceiveFileの更新）
                //ただし、受信履歴詳細画面から(メール無害化以外）のオリジナルファイル、ふるまい検知済ファイルの場合はカウントアップしない(v2.1.14b)
                if(receiveInfo.isAttachmentMailFlg() || !fileInfo.isOriginalFileFlg()){
                    // 更新ファイル情報
                    ReceiveFile receiveFile = null;
                    for (ReceiveFile _receiveFile : receiveInfo.getReceiveFiles()) {
                        if (_receiveFile.getId().equalsIgnoreCase(_fileId)) {
                            receiveFile = _receiveFile;
                            break;
                        }
                    }

                    if (receiveFile != null) {
                        ///受信フラグ、受信日時
                        receiveFile.setReceiveFlg(true);
                        receiveFile.setReceiveTime(_sysDate);

                        ///ダウンロード件数インクリメント
                        receiveFile.setDownloadCount(receiveFile.getDownloadCount() + 1);
                        receiveFile.resetDate();    //更新日付

                        ///受信ファイル情報更新
                        fileInfo.setReceiveFlg(receiveFile.isReceiveFlg());
                        fileInfo.setDownloadCount(receiveFile.getDownloadCount());
                    }
                }

                // 原本ファイルの場合、ダウンロードログをDBに登録
                if (fileInfo.isOriginalFileFlg()) {
                    InsertOriginalFileLog(fileInfo);
                }
            }
        }

        //受信ユーザID←ログインユーザID
        if (!StringUtils.isEmpty(commonBean.getUserId())) {
            receiveInfo.setReceiveUserId(commonBean.getUserId());
        }

        // 更新実行
        receiveInfo.resetDate();    //更新日付セット
        receiveInfoService.edit(receiveInfo);

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));            
        return receiveInfo;
    }

    /**
     * 更新処理
     *
     * @param fileInfoList
     */
    @Transactional
    public void downloadUpdate(List<FileInfoBean> fileInfoList) {
        LOG.trace("downloadUpdate start");
        // 更新
        for (FileInfoBean fileInfo : fileInfoList) {
            if (fileInfo.isChecked()) {
                // 原本ファイルの場合、ダウンロードログをDBに登録
                if (fileInfo.isOriginalFileFlg()) {
                    InsertOriginalFileLog(fileInfo);
                }
            }
        }
        LOG.trace("downloadUpdate end");
    }

    /**
     * 原本メール取得時ログ更新
     *
     * @param id
     */
    @Transactional
    public void downloadEml(String id) {
        FileInfoBean fileInfoBean = new FileInfoBean();

        fileInfoBean.setFileName(id + ".eml");
        fileInfoBean.setOriginalFileId("");
        fileInfoBean.setOriginalInfoId(id);

        InsertOriginalFileLog(fileInfoBean);
    }

    /**
     * （承認履歴）HistoryBean作成（送信・受信情報取得を含む） （この関数では、送信情報を取得）
     *
     * @param approveInfo 承認情報
     * @param sysDate システム日付
     *
     * @return （承認履歴）HistoryBean
     */
    public HistoryBean createApproveHistoryBean(ApproveInfo approveInfo, Date sysDate) {

        //送信情報ID
        //(承認情報.承認対象IDには送信情報IDが設定されている)
        String sendId = approveInfo.getApproveId();

        //送信情報の取得
        SendInfo sendInfo = sendInfoService.find(sendId);

        //（承認履歴）HistoryBean作成
        HistoryBean historyBean = createApproveHistoryBean(sendInfo, approveInfo, sysDate);

        //return
        return historyBean;
    }

    /**
     * （承認履歴）HistoryBean作成（送信・受信情報取得を含む）
     *
     * @param sendInfo 送信情報
     * @param approveInfo 承認情報
     * @param sysDate システム日付
     *
     * @return （承認履歴）HistoryBean
     */
    public HistoryBean createApproveHistoryBean(SendInfo sendInfo, ApproveInfo approveInfo, Date sysDate) {

        //（送信履歴）HistoryBean作成（受信情報取得を含む）
        HistoryBean historyBean = createSendHistoryBean(sendInfo, sysDate);

        //承認情報ID
        String approveId = approveInfo.getId();
        if (!StringUtils.isEmpty(approveId)) {
            historyBean.setApproveId(approveId);
        }

        //-----------------------------------------
        //承認履歴一覧としての、状態に変更
        //-----------------------------------------
        //承認済か（＝承認待ちフラグで判定）
        boolean isApproved = !sendInfo.isApprovalFlg();
        //ファイル送信実施済みの場合、または、自-承認が完了している場合
        if (isApproved || approveInfo.getApprovedFlg() == APPROVEDFLG_APPROVED) {
            ///「承認する」不可、disabledRow
            historyBean.setApproved(false);
            historyBean.setRowStyle(DataTableBean.ROWSTYLE_DISABLED);
        }

        //return
        return historyBean;
    }
}
