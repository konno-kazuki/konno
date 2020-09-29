package jp.co.fujielectric.fss.logic;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.ejb.Singleton;
import javax.inject.Inject;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.data.CommonBean;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.entity.ApproveInfo;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.service.ApproveInfoService;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.service.SendInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import org.apache.logging.log4j.Logger;

/**
 * 承認・却下用ファイル送信機能用Logic
 */
//@ApplicationScoped
@Singleton
public class SendTransferApprovedLogic {

    @Inject
    protected CommonBean commonBean;
    @Inject
    protected AuthLogic authLogic;
    @Inject
    protected Logger LOG;
    @Inject
    private EntityManager em;

    @Inject
    protected SendInfoService sendInfoService;
    @Inject
    protected ReceiveInfoService receiveInfoService;
    @Inject
    protected ApproveInfoService approveInfoService;
    @Inject
    protected MailManager mailManager;
    @Inject
    protected SanitizeHelper sanitizeHelper;

    /**
     * 承認時処理（DB更新）
     *
     * @param sendInfoId 送信情報ＩＤ
     * @param approveId 承認履歴情報ＩＤ
     * @param approvedComment 承認文章
     * @throws javax.mail.internet.AddressException
     */
    @Transactional
    public void execSendTransferApproved(String sendInfoId, String approveId, String approvedComment) throws AddressException {
        String result;
        Date sysDate = new Date();          //システム日付（"yyyy/mm/dd h:mm:ss"）
        String funcId = "approveTransfer";

        //----------------------------------
        //(対象)承認情報
        //----------------------------------
        ApproveInfo approveInfo = approveInfoService.find(approveId);
        approveInfo.setApprovedComment(CommonUtil.decodeBase64(approvedComment)); ///承認文章
        approveInfo.setApprovedFlg(1);                          ///承認フラグ＝1(承認)
        approveInfo.setApprovedTime(sysDate);                   ///承認日時

        //----------------------------------
        //(対象)送信情報
        //----------------------------------
        SendInfo sendInfo = sendInfoService.findLocking(sendInfoId);

        //（排他）直前チェック
        if (sendInfo == null) {
            ///送信情報が取得できない場合、例外
            result = Item.ErrMsgItemKey.ERR_EXCLUSION.getString();
            throw new RuntimeException(result);
            //return Response.ok(result).build();
        }

        if (sendInfo.isCancelFlg()) {
            ///取消済の場合、エラー（基本「却下」ボタン押下不可...ここでも以降の処理は行わない）
            result = Item.ErrMsgItemKey.ERR_EXCLUSION_SEND_CANCELED.getString();
            throw new RuntimeException(result);
            //return Response.ok(result).build();
        }

        //送信情報：[承認]値セット
        int doneCount = sendInfo.getApprovalsDoneCount();
        doneCount++;    ///今回承認するので、インクリメント
        sendInfo.setApprovalsDoneCount(doneCount);              ///承認済み回数

        //承認待ちで、且つ、承認必要回数＝承認済み回数の場合、今回の承認で、承認済みに変更
        //  ※承認待ち：取り消しフラグ＝false、承認待ちフラグ＝true
        boolean bSendTransfer = false;                          ///ファイル送信を行うか
        if (!sendInfo.isCancelFlg() && sendInfo.isApprovalFlg()
                && sendInfo.getApprovalsDoneCount() == sendInfo.getApprovalsRequiredCount()) {
            sendInfo.setApprovalFlg(false);                     ///承認待ちフラグ＝false
            bSendTransfer = true;
        }
        //LOG.debug("....bSendTransfer="+bSendTransfer+" 承認文章＝"+CommonUtil.decodeBase64(approvedComment));

        //----------------------------------
        //承認情報更新
        //----------------------------------
        approveInfoService.edit(approveInfo);

        //----------------------------------
        //送信情報更新
        //----------------------------------
        sendInfo.resetDate();       //更新日時をセット
        sendInfoService.edit(sendInfo);

        //----------------------------------
        //作成済-受信情報をもとに、無害化 / メール送信
        //----------------------------------
        if (bSendTransfer) {
            //(対象)作成済受信情報
            List<ReceiveInfo> receiveInfoList = receiveInfoService.findForSendInfoId(sendInfoId);
            List<ReceiveInfo> sanitizeReceiveInfoLst = new ArrayList<>();
            if (receiveInfoList != null) {
                for (ReceiveInfo receiveInfo : receiveInfoList) {

                    if (receiveInfo.getSendInfo() == null) {
                        receiveInfo.setSendInfo(sendInfo);
                    }

                    //receiveInfoからenvelopeToを取得
                    InternetAddress envelopeTo = new InternetAddress(receiveInfo.getReceiveMailAddress());
                            
                    //ファイル交換でのパスワード解除実施有無
                    boolean flgPassword = receiveInfo.isPasswordUnlockWaitFlg();
                    
                    //------------------------
                    //パスワード解除通知/無害化/メール送信
                    //※SendTransferLogic.execSendTransfer()内の当該処理と同じ処理
                    //------------------------
                    if (flgPassword) {
                        //#パスワード解除通知あり
                        
                        /**
                         * パスワード解除通知（「自分へ送る」自は送信しない）
                         */
                        if (!sendInfo.isNoticeOmitFlg()) {
                            mailManager.sendMailPasswordInput(receiveInfo, envelopeTo);
                        }
                    }else{
                        //#パスワード解除通知なし

                        //庁内/庁外判定
                        boolean isLgwanAddress = (mailManager.isMyDomain(envelopeTo.getAddress()) 
                                || mailManager.isLgDomain(envelopeTo.getAddress()));

                        /**
                         * 無害化 / メール送信
                         */
                        if (isLgwanAddress && !sendInfo.isSectionLgwan()) { // FIXME: 無害化対象の条件検討
                            //庁内向けもしくは庁外(LG)向け、かつインターネット側の場合
                            // 無害化処理の呼び出し
                            //LOG.debug("------***<execSanitize>庁内：無害化");
                            sanitizeReceiveInfoLst.add(receiveInfo);
//                            sanitizeHelper.start(funcId, receiveInfo);
                            //LOG.debug("------***<execSanitize>庁内：無害化...OK receiveInfoId="+receiveInfo.getId());
                        } else {
                            //庁外向けの場合
                            // 無害化処理はせずにメール送信
                            //LOG.debug("------***<execSanitize>庁外：無害化処理はせずにメール送信");
                            mailManager.sendMailDownLoad(receiveInfo);
                            //LOG.debug("------***<execSanitize>庁外：無害化処理はせずにメール送信...OK receiveInfoId="+receiveInfo.getId());
                        }
                    }
                }
            }
            //全ReceiveInfoに対してまとめて無害化開始処理をする
            sanitizeHelper.start(funcId, sanitizeReceiveInfoLst);
        }

        // ここでＤＢ処理終了
        em.flush();
    }

    /**
     * 承認却下時処理（DB更新、ファイル送信実行など）
     *
     * @param sendInfoId 送信情報ＩＤ
     * @param approveId 承認履歴情報ＩＤ
     * @param approvedComment 承認文章
     */
    @Transactional
    public void execSendTransferApprovedRejected(String sendInfoId, String approveId, String approvedComment) {
        String result;
        Date sysDate = new Date();          //システム日付（"yyyy/mm/dd h:mm:ss"）

        //却下メール対象リスト
        List<InternetAddress> mailToList = new ArrayList<>();

        //----------------------------------
        //(対象)承認情報
        //----------------------------------
        //送信情報IDから承認情報リストを取得
        //int cntApproved = 0;
        ApproveInfo approveInfo = null;
        List<ApproveInfo> targetApproveInfoList = approveInfoService.findForSendInfoId(sendInfoId);
        if (targetApproveInfoList != null) {
            for (ApproveInfo info : targetApproveInfoList) {
                //if (info.getApprovedFlg()==approvedflgApproved) {
                //    cntApproved++;  ///承認済数
                //}

                if (info.getId().equals(approveId)) {
                    approveInfo = info;
                    continue;
                }
                ///他承認者を却下メール対象リストへ
                try {
                    mailToList.add(new InternetAddress(info.getApproveMailAddress(), info.getApproveUserName()));
                } catch (Exception e) {
                    result = e.getMessage();
                    throw new RuntimeException(result);
                }
            }
        }

        //（排他）直前チェック
        if (approveInfo == null) {
            ///承認情報が取得できない場合、例外
            result = Item.ErrMsgItemKey.ERR_EXCLUSION.getString();
            throw new RuntimeException(result);
        }

        //----------------------------------
        //承認情報：[却下]値セット
        //----------------------------------
        approveInfo.setApprovedComment(CommonUtil.decodeBase64(approvedComment)); ///承認文章
        approveInfo.setApprovedFlg(-1);                     ///承認フラグ＝-1(却下)
        approveInfo.setApprovedTime(sysDate);               ///承認日時

        //----------------------------------
        //(対象)送信情報
        //----------------------------------
        SendInfo sendInfo = sendInfoService.findLocking(sendInfoId);

        //（排他）直前チェック
        if (sendInfo == null) {
            ///送信情報が取得できない場合、例外
            result = Item.ErrMsgItemKey.ERR_EXCLUSION.getString();
            //LOG.error("<err>result="+result);
            throw new RuntimeException(result);
        }

        if (sendInfo.isCancelFlg()) {
            ///取消済の場合、例外（基本「却下」ボタン押下不可なので、送信情報更新は行わない）
            result = Item.ErrMsgItemKey.ERR_EXCLUSION_SEND_CANCELED.getString();
            //LOG.error("<err>result="+result);
            throw new RuntimeException(result);
        }

        //承認済か（＝承認待ちフラグで判定）
        if (!sendInfo.isCancelFlg() && !sendInfo.isApprovalFlg()) {
            ///承認済みの場合、例外（承認済後に後追い却下は不可）
            result = Item.ErrMsgItemKey.ERR_EXCLUSION_APPROVED.getString();
            //LOG.error("<err>result="+result);
            throw new RuntimeException(result);
        }

        //送信元を却下メール対象リストへ
        try {
            mailToList.add(new InternetAddress(sendInfo.getSendMailAddress(), sendInfo.getSendUserName()));
        } catch (Exception e) {
            result = e.getMessage();
            throw new RuntimeException(result);
        }

        //----------------------------------
        //送信情報：[却下]値セット
        //----------------------------------
        sendInfo.setCancelFlg(true);                        ///取り消しフラグ＝true

        //----------------------------------
        //承認情報更新
        //----------------------------------
        //LOG.error("<...>承認情報更新");
        approveInfoService.edit(approveInfo);

        //----------------------------------
        //送信情報：更新
        //----------------------------------
        //LOG.error("<...>送信情報：更新");
        sendInfo.resetDate();       //更新日時をセット
        sendInfoService.edit(sendInfo);

        //----------------------------------
        //メール送信
        //----------------------------------
        String mailAddressTo = mailManager.getMailAddressTo(mailToList);
        for (InternetAddress iaToAddress : mailToList) {
            mailManager.sendMailApprovalRejected(iaToAddress, approveInfo, mailAddressTo);
        }

        // ここでＤＢ処理終了
        em.flush();
    }

    /**
     * ファイル送信取り消し処理（DB更新）
     *
     * @param sendInfoId 送信情報ＩＤ
     */
    @Transactional
    public void execSendTransferCanceled(String sendInfoId) {
        String result;

        //----------------------------------
        //(対象)送信情報
        //----------------------------------
        SendInfo sendInfo = sendInfoService.findLocking(sendInfoId);

        //（排他）直前チェック
        if (sendInfo == null) {
            ///送信情報が取得できない場合、例外
            result = Item.ErrMsgItemKey.ERR_EXCLUSION.getString();
            //LOG.error("<err>result="+result);
            throw new RuntimeException(result);
        }

        //----------------------------------
        //送信情報：[取り消し]値セット
        //----------------------------------
        sendInfo.setCancelFlg(true);                        ///取り消しフラグ＝true
        sendInfo.setApprovalFlg(false);                     ///承認待ちフラグ＝false

        //----------------------------------
        //送信情報：更新
        //----------------------------------
        //LOG.error("<...>送信情報：更新");
        sendInfo.resetDate();       //更新日時をセット
        sendInfoService.edit(sendInfo);

        // ここでＤＢ処理終了
        em.flush();
    }
}
