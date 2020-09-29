/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.mail.internet.InternetAddress;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.data.CommonBean;
import jp.co.fujielectric.fss.data.CommonEnum;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.entity.ApproveInfo;
import jp.co.fujielectric.fss.entity.OnceUser;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.service.ApproveInfoService;
import jp.co.fujielectric.fss.service.OnceUserService;
import jp.co.fujielectric.fss.service.ReceiveFileService;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.service.SendInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.FileUtil;
import jp.co.fujielectric.fss.util.IdUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import lombok.Getter;

/**
 * ファイル送信機能用Logic
 */
@RequestScoped
//@ApplicationScoped
public class SendTransferLogic {

    @Inject
    protected CommonBean commonBean;
    @Inject
    protected AuthLogic authLogic;
    @Inject
    private MailManager mailManager;
    @Inject
    protected Logger LOG;

    @Inject
    private OnceUserService onceUserService;
    @Inject
    protected SendInfoService sendInfoService;
    @Inject
    protected ReceiveInfoService receiveInfoService;
    @Inject
    private SyncFilesHelper syncFilesService;
    @Inject
    private SanitizeHelper sanitizeHelper;
    @Inject
    protected ApproveInfoService approveInfoService;
    @Inject
    private ItemHelper itemHelper;
    @Inject 
    private DecryptFileLogic decryptFileLogic;
    @Inject
    private EntityManager em;

    @Inject
    private ReceiveFileService receiveFileService;
    
    /**
     * 承認処理フラグ
     * （承認処理中のエラーかどうかの判定用）
     */
    @Getter
    private boolean flgApproval = false;
    
//    /**
//     * パスワード解除実施有無（configから取得）
//     */
//    private boolean confPasswordFlg;
    
//    /**
//     * コンストラクタ
//     */
//    public SendTransferLogic() {
//        //ファイル交換でのパスワード解除実施有無をconfigから取得
//        Item item = itemHelper.find(Item.PASSWORD_UNLOCK_FLG, "sendTransfer");
//        confPasswordFlg = Boolean.valueOf(item.getValue());        
//    }

    /**
     * パスワード解除対象かどうかの判定(configでの設定、Internet/LGWANからの判定）
     * ※ここでTrue（パスワード解除対象）と判定されて場合のみ、その後の宛先毎のチェックcheckPasswordFlgByEnvelopeTo()を行うこととする。
     * @param sendInfo
     * @return
     */
    private boolean checkPasswordFlgByConfig(SendInfo sendInfo) {
        boolean flgPassword = false;
        if(!sendInfo.isSectionLgwan()){  //インターネットのみパスワード解除対象とする
            //ファイル交換でのパスワード解除実施有無をconfigから取得
            Item item = itemHelper.find(Item.PASSWORD_UNLOCK_FLG, "sendTransfer");
            boolean confPasswordFlg = Boolean.valueOf(item.getValue());
            if (confPasswordFlg){   //configでパスワード解除が有効の場合
                flgPassword = true;
            }
        }
        return flgPassword;
    }

    /**
     * パスワード解除対象かどうかの判定（宛先毎）
     * ※checkPasswordFlgByConfig() でconfigのpasswordUnlockFlg=true、sendInfo.sectionLgWan=false 
     * であることが事前にチェック済みであることを前提とする
     * @param sendInfo
     * @param envelopeTo
     * @return
     */
    private boolean checkPasswordFlgByEnvelopeTo(SendInfo sendInfo, InternetAddress envelopeTo) {
        boolean flgPassword = false;        
        if (mailManager.isMyDomain(sendInfo.getSendMailAddress())
                && (mailManager.isMyDomain(envelopeTo.getAddress()) || mailManager.isLgDomain(envelopeTo.getAddress()))) {
            // 送信元が内部ユーザ、且つ送信先が庁内[インターネット/LGWAN]、又は庁外（他団体）[インターネット/LGWAN]の場合
            flgPassword = true;
        } else if (!mailManager.isMyDomain(sendInfo.getSendMailAddress())
                && mailManager.isMyDomain(envelopeTo.getAddress())) {
            // 送信元が外部ユーザ、且つ送信先が庁内[インターネット/LGWAN]の場合
            flgPassword = true;
        }
        return flgPassword;
    }
    
    /**
     * 受信情報書込み
     *
     * @param sendInfo
     * @param envelopeTo
     * @param flgPassword 
     * @throws java.lang.Exception
     */
    private ReceiveInfo execCreateReceiveInfo(SendInfo sendInfo, InternetAddress envelopeTo, boolean flgPassword) throws Exception {
        String sendId = sendInfo.getId();
        
        // SandBlast対応区分取得
        CommonEnum.SandBlastKbn sandBlstKbn = sanitizeHelper.getSandBlastKbn(sendInfo);        
        
        //------------------------
        //受信情報(ReceiveInfo)書き込み
        //------------------------
        // 送信情報を受信情報にコピー  [v2.2.1]共通化
        ReceiveInfo receiveInfo = receiveInfoService.createReceiveInfo(sendInfo, envelopeTo, false, flgPassword, sandBlstKbn);
        LOG.debug("createReceiveInfo:id=" + receiveInfo.getId());

        //------------------------
        //受信ファイル情報(ReceiveInfo)書き込み
        //------------------------
        if (!flgPassword) {
            // パスワード解除無し
            createReceiveFiles(sendInfo, receiveInfo);
        }else{
            // パスワード解除有り
            decryptFileLogic.createReceiveFilesForPassowrd(receiveInfo);
        }
        // receiveInfoをＤＢ登録
        receiveInfoService.create(receiveInfo);            

        return receiveInfo;
    }

    /**
     * ReceiveFileの出力（パスワード解除有り用）
     * @param receiveInfo 
     */
    private void createReceiveFiles(SendInfo sendInfo, ReceiveInfo receiveInfo){
        boolean isLgwanAddress = (mailManager.isMyDomain(receiveInfo.getReceiveMailAddress())
                || mailManager.isLgDomain(receiveInfo.getReceiveMailAddress()));  


        //ReceiveFileの生成　[v2.2.1]共通化
        boolean flgReject = !(isLgwanAddress && !sendInfo.isSectionLgwan());    //無害化対象外かどうか
        receiveFileService.createReceiveFiles(receiveInfo, sendInfo.getSendFiles(), flgReject);
        
//        for (SendFile sendFile : sendInfo.getSendFiles()) {
//            String id = IdUtil.createUUID();
//            //String fileNameWithoutSuffix = FileUtil.getFNameWithoutSuffix(sendFile.getFileName());
//            ReceiveFile receiveFile = new ReceiveFile();
//            receiveFile.setId(id);
//            receiveFile.setReceiveInfo(receiveInfo);
//            receiveFile.setFileName(sendFile.getFileName());
//            receiveFile.setFileFormat(FileUtil.getSuffix(sendFile.getFileName()));
//            if (isLgwanAddress && !sendInfo.isSectionLgwan()) { // FIXME: 無害化対象の条件検討
//                // 無害化対象
//                receiveFile.setFilePath("");
//                receiveFile.setFileSize(0);
//                receiveFile.setTargetFlg(true);
//                receiveFile.setSanitizeFlg(false);
//            } else {
//                // 無害化対象外
//                receiveFile.setFilePath(sendFile.getFilePath());
//                receiveFile.setFileSize(sendFile.getFileSize());
//                receiveFile.setTargetFlg(true);
//                receiveFile.setSanitizeFlg(true);
//                receiveFile.setResult(ResultKbn.REJECTED.value);
//            }
//            receiveFile.setExcludeFlg(false);
//            receiveFile.setFileMessage("");
//            receiveFile.setReceiveFlg(false);       // 未受信
//            receiveFile.setReceiveTime(null);       // 〃
//            receiveInfo.getReceiveFiles().add(receiveFile);
//        }        
    }

    /**
     * ファイル送信情報書込み(即コミット）
     * @param sendInfo
     * @throws java.lang.Exception
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void writeSendInfo(SendInfo sendInfo) throws Exception {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        try{
            //サーバ設置場所(ture: LGWAN側, false: Internet側)
            boolean bSectionLgwan = CommonUtil.isSectionLgwan();
            sendInfo.setSectionLgwan(bSectionLgwan);

            // 送信情報・送信ファイル情報を同時にDB登録
            sendInfo.resetDate();       //更新日時をセット
            sendInfoService.edit(sendInfo);            
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));            
        }        
    }    
    
    /**
     * ファイル送信実行
     * @param sendId
     * @param onetimeId
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void execSendTransfer(String sendId, String onetimeId) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "sendInfoId:" + sendId, "onetimeId:" + onetimeId));
        try{           
            String funcId = "sendTransfer";
            SendInfo sendInfo = sendInfoService.find(sendId);
            
            // LGWANの場合に、ファイル同期処理の呼び出し
            if (sendInfo.isSectionLgwan()) {
//                syncFilesService.syncFiles(CommonUtil.getSetting("senddir") + sendInfo.getId());
                syncFilesService.syncFiles(CommonUtil.getFolderSend(sendInfo, false, false));
            }           
//            //TODO :::UT:::Start v2.1.13_A001
//            // 時間がかかる処理のテスト
//            if(sendInfo.getContent().contains("#UT#execSendTransfer Timer#"))
//                Thread.sleep(30000);    //30秒スリープ
//            //例外発生
//            if(sendInfo.getContent().contains("#UT#execSendTransfer Exception#"))
//                throw new RuntimeException("#UT# execSendTransferで例外");    //例外発生
//            //TODO :::UT:::End
            
            // 受信情報作成＆通知
            if (true) {
                //パスワードファイル存在チェック
                boolean hasPwFile = false;
                if(checkPasswordFlgByConfig(sendInfo)){
                    //パスワードチェックには時間がかかるため、
                    //configの設定およびInternetかどうかでパスワードチェック対象と判定された場合のみチェックする。[2.2.1]
                    hasPwFile = FileUtil.checkPw(CommonUtil.getFolderSend(sendInfo, false, false), LOG);  //例外発生しても内部でログ出力して処理継続
                }
                
                ///送信先アドレス情報
                InternetAddress[] envelopeToList = InternetAddress.parse(Optional.ofNullable(sendInfo.getToAddress()).orElse(""));
                if (envelopeToList != null){
                    List<ReceiveInfo> sanitizeReceiveInfoLst = new ArrayList<>();                    
                    for (InternetAddress envelopeTo : envelopeToList) {

                        //パスワード解除有無の判定
                        boolean flgPassword = false;
                        if(hasPwFile){
                            //事前にconfigの設定およびInternetかどうかでパスワードチェック対象かどうか判定されているため、ここでは宛先でのチェックのみ[2.2.1]
                            flgPassword = checkPasswordFlgByEnvelopeTo(sendInfo, envelopeTo);
                        }                    

                        //------------------------
                        //受信情報書き込み
                        //------------------------
                        ReceiveInfo receiveInfo = execCreateReceiveInfo(sendInfo, envelopeTo, flgPassword);
                        em.flush(); //DB反映（コミットではない）
                        
                        //------------------------
                        //パスワード解除通知/無害化/メール送信
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
                                sanitizeReceiveInfoLst.add(receiveInfo);    //後でまとめて同一トランザクションで無害化開始処理をするため、対象リストに追加する
                            } else {
                                //その他（LGWAN側、庁外向け）の場合
                                // 無害化処理はせずにメール送信
                                mailManager.sendMailDownLoad(receiveInfo);
                            }
                        }
                    }
                    //全ReceiveInfoに対してまとめて無害化開始処理をする
                    sanitizeHelper.start(funcId, sanitizeReceiveInfoLst);
                }
            }
        }catch(RuntimeException e){
            //RuntimeExceptionはそのままthrow
            throw(e);
        }catch(Exception e){
            //ExceptionはRuntimeExceptionをthrow (DBロールバックのため）
            throw new RuntimeException(e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "sendInfoId:" + sendId));            
        }
    }
        
    /**
     * 承認依頼送信実行
     *
     * @param sendId
     * @param mailAddressApprovals
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void execApprovalRequest( String sendId, String mailAddressApprovals) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "sendInfoId:" + sendId));
        try{
            flgApproval = false;
            SendInfo sendInfo = sendInfoService.find(sendId);
            java.util.Date requestTime = sendInfo.getSendTime();    ///依頼日時＝送信情報.送信日時
            
            // LGWANの場合に、ファイル同期処理の呼び出し
            if (sendInfo.isSectionLgwan()) {
//                syncFilesService.syncFiles(CommonUtil.getSetting("senddir") + sendInfo.getId());
                syncFilesService.syncFiles(CommonUtil.getFolderSend(sendInfo, false, false));
            }

//            //TODO :::UT:::Start v2.1.13_A001 
//            // 時間がかかる処理のテスト
//            if(sendInfo.getContent().contains("#UT#execSendTransfer Timer#"))
//                Thread.sleep(30000);    //30秒スリープ
//            //例外発生
//            if(sendInfo.getContent().contains("#UT#execSendTransfer Exception#"))
//                throw new RuntimeException("#UT# execApprovalRequest 送信処理で例外");    //例外発生
//            //TODO :::UT:::End 
                        
            // 受信情報を作成
            if (true) {
                //パスワードファイル存在チェック
                boolean hasPwFile = false;
                if(checkPasswordFlgByConfig(sendInfo)){
                    //パスワードチェックには時間がかかるため、
                    //configの設定およびInternetかどうかでパスワードチェック対象と判定された場合のみチェックする。[2.2.1]
                    hasPwFile = FileUtil.checkPw(CommonUtil.getFolderSend(sendInfo, false, false), LOG);  //例外発生しても内部でログ出力して処理継続
                }
                ///送信先アドレス情報
                InternetAddress[] envelopeToList = InternetAddress.parse(Optional.ofNullable(sendInfo.getToAddress()).orElse(""));
                if (envelopeToList != null) {
                    for (InternetAddress envelopeTo : envelopeToList) {
                        //パスワード解除有無の判定
                        boolean flgPassword = false;
                        if(hasPwFile){
                            //事前にconfigの設定およびInternetかどうかでパスワードチェック対象かどうか判定されているため、ここでは宛先でのチェックのみ[2.2.1]
                            flgPassword = checkPasswordFlgByEnvelopeTo(sendInfo, envelopeTo);
                        }
                        //------------------------
                        //受信情報書き込み
                        //------------------------
                        execCreateReceiveInfo(sendInfo, envelopeTo, flgPassword);
                    }
                }                
            }
            em.flush(); //DB反映（コミットではない）

            //承認情報を生成
            if (true) {
                flgApproval = true;
                
                ///承認者アドレス情報
                InternetAddress[] envelopeToList = InternetAddress.parse(Optional.ofNullable(mailAddressApprovals).orElse(""));

                if (envelopeToList != null) {
                    for (InternetAddress envelopeTo : envelopeToList) {

                        //------------------------
                        //承認情報書き込み
                        //------------------------
                        ApproveInfo approveInfo = new ApproveInfo();
                        approveInfo.setId(IdUtil.createUUID());                         ///ID＝uuid
                        approveInfo.setApprovedComment("");                             ///承認文章
                        approveInfo.setApproveId(sendInfo.getId());                     ///承認対象ID＝sendid
                        approveInfo.setApproveMailAddress(envelopeTo.getAddress());     ///承認メールアドレス
                        approveInfo.setApproveUserName(envelopeTo.getPersonal());       ///承認者名
                        approveInfo.setApprovedFlg(0);                                  ///承認フラグ＝0(初期)
                        approveInfo.setApprovedTime(null);                              ///承認日時＝null
                        approveInfo.setRequestTime(requestTime);                        ///依頼日時
                        approveInfoService.create(approveInfo);
                        
                        em.flush(); //DB反映（コミットではない） 
                        
                        /**
                         * 承認依頼メール送信
                         */
                        mailManager.sendMailApprovalRequest(sendInfo, approveInfo, mailAddressApprovals);
                    }
                }
//                //TODO :::UT:::Start v2.1.13_A001 
//                //例外発生
//                if(sendInfo.getContent().contains("#UT#execApprovalRequest Exception#"))
//                    throw new RuntimeException("#UT# execApprovalRequest 承認処理で例外");    //例外発生
//                //TODO :::UT:::End 
            }
        }catch(RuntimeException e){
            //RuntimeExceptionはそのままthrow
            throw(e);
        }catch(Exception e){
            //ExceptionはRuntimeExceptionをthrow (DBロールバックのため）
            throw new RuntimeException(e.getMessage(), e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "sendInfoId:" + sendId));
        }
    }

    /**
     * 送信処理（送信開始/承認依頼）エラー時処理
     * @param sendInfoId    送信情報ID
     * @param onetimeId     ワンタイムID
     * @param sendRequestId 送信依頼ID
     * @param flgApprovals  承認依頼処理エラーフラグ
     * 
     */
    @Transactional
    public void setSendTransferExecError(String sendInfoId, String onetimeId, String sendRequestId, boolean flgApprovals )
    {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "sendInfoId:" + sendInfoId));
        try{
            SendInfo sendInfo = sendInfoService.find(sendInfoId);
            //エラーの場合、sendInfo.warningFlg に0x0004ビットONの数値をセットする            
            int errFlg = SendInfo.SENDTRANSFER_FLAG;
            if(flgApprovals){
                //承認処理中の場合は承認処理中エラーのフラグを立てる
                errFlg = SendInfo.SENDTRANSFERAPPROVAL_FLAG;
            }
            sendInfo.updateWarningFlg(errFlg);
            sendInfo.resetDate();       //更新日時をセット
            sendInfoService.edit(sendInfo);
            
            // ワンタイムユーザ情報を元の状態（SendRequestIdがセットされた状態）に戻す更新
            if (!StringUtils.isBlank(onetimeId)) {
                // ワンタイムユーザの取得
                OnceUser onceUser = authLogic.findOnetimeUser(onetimeId);
                if(onceUser != null){
                    onceUser.setMailId(sendRequestId);      //送信依頼ID（sendRequestInfo.id)に戻す
                    onceUser.setTarget("sendTransfer");     //"sendTransfer"に戻し、ファイル送信画面が表示されるようにする
                    onceUserService.edit(onceUser);
                }
            }

//            //TODO :::UT:::Start v2.1.13_A001 
//            //例外発生
//            if(sendInfo.getContent().contains("#UT#setSendTransferExecError Exception#"))
//                throw new RuntimeException("#UT# setSendTransferExecErrorで例外");    //例外発生
//            //TODO :::UT:::End
            em.flush();
        }catch(Exception e){
            LOG.error("#!ファイル送信処理エラー時処理で例外発生。 msg:" + e.getMessage(),e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "sendInfoId:" + sendInfoId));            
        }
    }
    
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public String sendTransferTest(int sleep,int counter, String memo) {
        try{
            LOG.debug("##[UT]RESTFUL TEST## sendTransferTest Start. Sleep:{} Memo:{} Counter:{}",
                    sleep, memo, counter);
            Thread.sleep(sleep);
            
            LOG.debug("##[UT]RESTFUL TEST## sendTransferTest End.  Memo:{} Counter:{}", memo, counter);
            return ( memo + "(" + counter + ")");
        }catch(Exception e){
            LOG.error("###[UT]RESTFUL TEST## sendTransferTest !例外発生。 msg:" + e.getMessage(), e);
            return ("ERR:" + memo + "(" + counter + ")");
        }finally{
        }
    }

}
