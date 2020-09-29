package jp.co.fujielectric.fss.logic;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.servlet.ServletException;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.data.CommonEnum.MailAnalyzeResultKbn;
import jp.co.fujielectric.fss.data.CommonEnum.ProcResultKbn;
import jp.co.fujielectric.fss.data.CommonEnum.SandBlastKbn;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.data.MailInfoBean;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendFile;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.exception.FssException;
import jp.co.fujielectric.fss.service.ReceiveFileService;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.service.SendInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.FileUtil;
import jp.co.fujielectric.fss.util.IdUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import jp.co.fujielectric.fss.util.ZipUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

/**
 *
 */
@RequestScoped
public class MailEntranceLogic {

    @Inject
    private Logger LOG;

    @Inject
    private MailManager mailManager;

    @Inject
    private SanitizeHelper sanitizeHelper;

    @Inject
    private ItemHelper itemHelper;

    @Inject
    private SendInfoService sendInfoService;

    @Inject
    private ReceiveInfoService receiveInfoService;

    @Inject
    private ReceiveFileService receiveFileService;
    
    @Inject 
    private DeleteReasonFileLogic deleteReasonFileLogic;
    
    /**
     * メール受信時処理
     * @param mailId
     * @param mailDate
     * @throws FssException 
     */
    @Transactional(value = Transactional.TxType.REQUIRES_NEW, rollbackOn = Exception.class)    //比検査例外でもRollbackするようにする。
    public void execMailEntrance(String mailId, String mailDate) throws FssException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "mailId:" + mailId));

        MailInfoBean mailInfoBean;
        SendInfo sendInfo;
        try {
            // メール情報の変換
            mailInfoBean = mailManager.convertMailInfoBean(mailId, 
                    mailDate,
                    CommonUtil.getFolderSetting(CommonUtil.FolderKbn.LOCAL_SEND),
                    true);   //[256対応] 出力先フォルダに一時保存先指定
            mailManager.mergeNorticeMessage(mailInfoBean);                      // メールチェック結果の反映
           
//            //TODO :::UT:::Start v2.1.12
//            FileUtil.ut256(CommonUtil.getSetting("local_senddir"),mailId,LOG);  //模擬ウィルス隔離
//            //TODO :::UT:::End
            
// [v2.2.2] createにて同IDの既存レコードがある場合に関連テーブル(ReceiveInfo,ReceiveFile)も含めて削除をするよう修正したので、事前のremoveは不要
//            if(sendInfoService.find(mailId) != null){
//                //メンテナンスによる同メールIDを再利用した処理を想定して、SendInfoがもし同IDで登録ずみなら削除しておく。
//                sendInfoService.remove(mailId);
//            }
            sendInfo = insertSendInfo(mailInfoBean);                            // 送信情報の登録
            sendInfo.setSectionLgwan(CommonUtil.isSectionLgwan());              // サーバ設置場所(ture: LGWAN側, false: Internet側)
            insertSendFiles(sendInfo);                                          // 送信ファイル情報の登録

            // 大容量ファイル判定
            Item item = itemHelper.find(Item.MAIL_SIZE_LIMIT, Item.FUNC_COMMON); // 機能ＩＤは共通
            long mailSizeLimit = Long.parseLong(item.getValue());
            if (mailInfoBean.getEmlFileSize() > mailSizeLimit) {                // emlファイルサイズが上限を超えている場合
                sendInfo.setLargeFlg(true);                                     // 大容量フラグをtrueに設定
            }

            // メール本体無害化フラグ取得
            item = itemHelper.find(Item.MAIL_SANITIZE_FLG, Item.FUNC_COMMON);   // 機能ＩＤは共通
            boolean mailSanitizeFlg = (item.getValue().equalsIgnoreCase("true"));

            // 送信先アドレス情報の取得
            InternetAddress[] envelopeToList;
            try {
                envelopeToList = mailInfoBean.getAddressEnvelopeTo();
            } catch (Exception e) {
                envelopeToList = InternetAddress.parseHeader(mailInfoBean.getHeaderEnvelopeTo(), false);

                int warningFlg = Integer.parseInt(sendInfo.getWarningFlg()) | SendInfo.ENVELOPE_TO_FLAG;
                sendInfo.setWarningFlg(Integer.toString(warningFlg));
            }

            // 送信情報・送信ファイル情報の永続化（sendInfoとsendFileを１トランザクションで登録）
            //[v2.2.2] メンテナンス時に同メールIDを再利用した場合も考慮して、create内に既存IDの存在チェック(および削除）を追加。
            //また、DBSyncで呼出されるRest側のcreate内にも、DB同期エラー後のリトライ時（LGWAN側のみ既存レコードありの状態）のエラー回避のため
            //既存IDの存在チェック(および削除）を追加。
            sendInfo.resetDate();       //登録日時、更新日時をセット
            sendInfoService.create(sendInfo);       // 送信情報・送信ファイル情報のDB登録（永続化）
            
            List<ReceiveInfo> receiveInfoLst = new ArrayList<>();

            String sendFolder = CommonUtil.getFolderSend(sendInfo, false, false);   //SENDフォルダ
            
            // 送信先アドレス分の受信情報作成
            if (sendInfo.getSendFiles().isEmpty()) {
                // 添付ファイル無し
                if (mailSanitizeFlg) {                                          // メール本体無害化判定
                    for (InternetAddress envelopeTo : envelopeToList) {         // 宛先分のReceiveInfoを作成
                        if (sendInfo.isLargeFlg()) {                            // emlファイルサイズが上限を超えている場合
                            mailManager.sendMailTextOnly(sendInfo, envelopeTo, "largeMailNofile"); // メール本文の先行送信
                        }

                        // 無害化処理の呼び出し
                        receiveInfoLst.add(createReceiveInfo(envelopeTo, sendInfo, mailSanitizeFlg));
                    }
                    sanitizeHelper.start("mailEntrance", receiveInfoLst);
                } else {
                    //メール送信をSendInfo単位（複数宛先）から送信先単位に変更 //[248対応（簡易版）]
                    sendInfo.setToAddress(mailInfoBean.getHeaderEnvelopeTo());  // ToにEnvelopeToを書き換え
                    for (InternetAddress envelopeTo : envelopeToList) {         // 宛先分のReceiveInfoを作成
                        ReceiveInfo receiveInfo = createReceiveInfo(envelopeTo, sendInfo, mailSanitizeFlg);
                        mailManager.sendMailSendFiles(receiveInfo);   // 添付ファイル無しなら、そのままメール転送 [248対応（簡易版）]宛先を複数から単体に
                    }
                }
            } else if ((Integer.parseInt(sendInfo.getWarningFlg()) & SendInfo.ENVELOPE_TO_FLAG) != SendInfo.ENVELOPE_TO_FLAG
                    && FileUtil.checkPw(sendFolder, LOG)) { // パスワード付きファイル有無判定
                // パスワード付添付ファイル処理
                for (InternetAddress envelopeTo : envelopeToList) {
                    if (sendInfo.isLargeFlg()) {                                // emlファイルサイズが上限を超えている場合
                        mailManager.sendMailTextOnly(sendInfo, envelopeTo, "largeMail");     // メール本文の先行送信
                    }

                    // パスワード解除通知
                    mailManager.sendMailPasswordInput(sendInfo, envelopeTo);
                }
            } else {               
                //添付ファイルのうち、zipファイルに対するzip内ファイルの文字数チェックおよびリネームをする【v2.1.13】
                renameZipIncludeFiles(sendInfo, sendFolder);
                
                // 通常処理
                // 宛先分のReceiveInfoを作成
                for (InternetAddress envelopeTo : envelopeToList) {
                    if (sendInfo.isLargeFlg()) {                                // emlファイルサイズが上限を超えている場合
                        mailManager.sendMailTextOnly(sendInfo, envelopeTo, "largeMail");     // メール本文の先行送信
                    }

                    // 無害化処理の呼び出し
                    receiveInfoLst.add( createReceiveInfo(envelopeTo, sendInfo, mailSanitizeFlg));
                }
                sanitizeHelper.start("mailEntrance", receiveInfoLst);                
            }            
        } catch (Throwable ex) {
            LOG.warn("### MailEntranceLogic Warning msg:" + ex.getMessage(), ex);
            if (ex instanceof FssException) {
                //FssExceptionはそのままスロー
                throw (FssException)ex;
            } else {
                //それ以外の例外はRuntimeExceptionをスロー
                //呼び出し元（サーブレット）でもエラー原因がわかるようにexを渡す
                throw new RuntimeException(ex);
            }            
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "mailId:" + mailId));
        }
    }

    //【v2.1.13】
    /**
     * 指定フォルダ内zipファイルの内部ファイルのリネーム（ファイル名長オーバー対応）
     * @param targetPath　対象フォルダ
     * @return 
     */
    private boolean renameZipIncludeFiles(SendInfo sendInfo, String targetPath) {
        File dir = new File(targetPath);
        File[] files = dir.listFiles();
        boolean hasRename = false;
        if(files == null) 
            return hasRename;
        for (File file : files) {
            if (!FileUtil.isZipFile(file) || ZipUtil.isEncrypted(file.getPath())){  
                // パスワードなしzipファイル以外は対象外
                continue;
            }
            try{
                //zip内ファイルのリネーム処理
                Map<File,File> resMap = ZipUtil.renameIncludeFilesByMaxlen(file.getPath(), null, FileUtil.MAX_FILENAME_LEN, LOG);
                if(resMap != null && resMap.size() > 0){
                    hasRename = true;
                    //リネームがあった場合はログ出力
                    for (Map.Entry<File,File> entry : resMap.entrySet()) {
                        //ログ出力
                        //"##### FileName  change.　ZIPファイル内のファイル名が長すぎるためリネームしました。
                        // （MailID:{メールID(SendInfoID)}, SendMailAddress:{送信メールアドレス}, ReceiveMailAddress:{受信メールアドレス},
                        //  ZipFileName:{zipファイル名}, BeforeFileName:{リネーム前ファイル名}, AfterFileName:{リネーム後ファイル名}）"
                        LOG.warn("##### FileName  change.　ZIPファイル内のファイル名が長すぎるためリネームしました。"
                                +"（MailID:{}, SendMailAddress:{}, ReceiveMailAddress:{}, ZipFileName:{}, BeforeFileName:{}, AfterFileName:{}）",
                                sendInfo.getId(),    //メールID
                                sendInfo.getSendMailAddress(),    //送信メールアドレス
                                sendInfo.getReceiveAddresses(),    //受信メールアドレス
                                file.getPath(),      //zipファイル名
                                entry.getValue().getPath(),     //リネーム前ファイル名
                                entry.getKey().getPath()        //リネーム後ファイル名
                        );
                    }
                }                
            }catch(Exception e){
                //失敗した場合、エラーログだけ出力して継続する。
                LOG.error("#!ZIPファイルのリネーム失敗。 fname:" + file.getPath() + ", msg:" + e.getMessage(), e);
            }
        }
        return hasRename;        
    }
   
    @Transactional
    public void execMailEntranceError(String mailId, String mailDate) throws MessagingException, IOException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "mailId:" + mailId));
        try{
            // 原本を取得可能となるように、sendinfo,receiveinfoは作成を行う。
            MailInfoBean mailInfoBean;
            SendInfo sendInfo = null;

            // メールの解析処理
            mailInfoBean = mailManager.convertErrorMailInfoBean(mailId, mailDate);        
            // sendinfoを作成
            sendInfo = insertSendInfo(mailInfoBean); // 送信情報の登録
            sendInfo.setSectionLgwan(CommonUtil.isSectionLgwan());              // サーバ設置場所(ture: LGWAN側, false: Internet側)
            // 送信先アドレス情報の取得
            InternetAddress[] envelopeToList;
            try {
                envelopeToList = mailInfoBean.getAddressEnvelopeTo();
            } catch (Exception e) {
                envelopeToList = InternetAddress.parseHeader(mailInfoBean.getHeaderEnvelopeTo(), false);

                int warningFlg = Integer.parseInt(sendInfo.getWarningFlg()) | SendInfo.ENVELOPE_TO_FLAG;
                sendInfo.setWarningFlg(Integer.toString(warningFlg));
            }

            sendInfo.resetDate();       //更新日時をセット
            sendInfoService.create(sendInfo);                                     // 送信情報・送信ファイル情報を同時にDB登録（sendInfoとsendFileを１トランザクションで登録）

            // 宛先分のReceiveInfoを作成
            for (InternetAddress envelopeTo : envelopeToList) {
                createReceiveInfo(envelopeTo, sendInfo, false);

                // エラーメールの送信
                // 複数宛先への送信から宛先毎の送信に変更　[248対応（簡易版）]
                mailInfoBean.setHeaderEnvelopeTo(envelopeTo.getAddress());
//                mailManager.sendErrorMail(mailInfoBean, CommonUtil.getSetting("maildir") + mailId + ".eml");
                File mailFile = new File(CommonUtil.getFolderMail(mailDate), mailId + ".eml");
                mailManager.sendErrorMail(mailInfoBean, mailFile.getPath());
            }

            //エラーメール送信ログ
            LOG.warn("##### MailFault. (mail entrance error, {})", mailId);
        } catch (Throwable ex) {
            LOG.warn("### MailEntranceLogic Exception msg:" + ex.getMessage(), ex);
            throw new RuntimeException();
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "mailId:" + mailId));
        }
    }

    //[v2.2.3]
    /**
     * メール解析エラーによるエラーメール送信（削除理由ファイル添付）
     * @param mailId
     * @param mailDate
     * @param errCode
     * @throws MessagingException
     * @throws IOException 
     */
    @Transactional(value = Transactional.TxType.REQUIRES_NEW, rollbackOn = Exception.class)    //比検査例外でもRollbackするようにする。
    public void execMailEntranceAnalyzeError(String mailId, String mailDate, int errCode) throws MessagingException, IOException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "mailId:" + mailId));
        try{
            // 原本を取得可能となるように、sendinfo,receiveinfoは作成を行う。
            MailInfoBean mailInfoBean;
            SendInfo sendInfo;

            // メールの解析処理
            mailInfoBean = mailManager.convertErrorMailInfoBean(mailId, mailDate);
            // sendinfoを作成
            sendInfo = insertSendInfo(mailInfoBean); // 送信情報の登録
            sendInfo.setSectionLgwan(CommonUtil.isSectionLgwan());              // サーバ設置場所(ture: LGWAN側, false: Internet側)
            // 送信先アドレス情報の取得
            InternetAddress[] envelopeToList;
            try {
                envelopeToList = mailInfoBean.getAddressEnvelopeTo();
            } catch (Exception e) {
                envelopeToList = InternetAddress.parseHeader(mailInfoBean.getHeaderEnvelopeTo(), false);

                int warningFlg = Integer.parseInt(sendInfo.getWarningFlg()) | SendInfo.ENVELOPE_TO_FLAG;
                sendInfo.setWarningFlg(Integer.toString(warningFlg));
            }
            sendInfo.setErrInfo(ProcResultKbn.MAILANALYSIS_ERROR.value);        //１：メール解析失敗による異常  [v2.2.3]
            sendInfo.setErrDetails(errCode);                                    //メール解析失敗区分    [v2.2.3]
            sendInfo.resetDate();       //更新日時をセット
            sendInfoService.create(sendInfo);                                     // 送信情報・送信ファイル情報を同時にDB登録（sendInfoとsendFileを１トランザクションで登録）

            // 削除理由ファイル作成
            File delReasonFile = null;
            String delReasonFname = null;
            try {
                delReasonFile = deleteReasonFileLogic.createDeleteReasonFileSend(sendInfo, errCode);
                //削除理由ファイル名（メール添付時用）を取得
                delReasonFname = itemHelper.findWithDefault(Item.DELETEREASON_ATTACHMENTFILE, null, DeleteReasonFileLogic.DELETEREASON_FILENAME);
            } catch (Exception e) {
                //削除理由ファイルの生成の失敗が原因でエラーとならないよう
                //例外発生時にも処理を続行する
                LOG.error("#! 削除理由ファイルの生成に失敗しました。[SendInfoId:{}, errCode:{}, Exception:{}]",
                        sendInfo.getId(), errCode, e.toString(), e);
            }

            // 宛先分のReceiveInfoを作成
            for (InternetAddress envelopeTo : envelopeToList) {
                ReceiveInfo receiveInfo = createReceiveInfo(envelopeTo, sendInfo, false);

                // エラーメールの送信
                // 複数宛先への送信から宛先毎の送信に変更　[248対応（簡易版）]
                mailInfoBean.setHeaderEnvelopeTo(envelopeTo.getAddress());
                File mailFile = new File(CommonUtil.getFolderMail(mailDate), mailId + ".eml");

                // 削除理由ファイル付エラーメール送信
                mailManager.sendErrorMailWithAttachment(mailInfoBean, mailFile.getPath(), receiveInfo, delReasonFile, delReasonFname);
            }

            //エラーメール送信ログ
            LOG.warn("##### MailFault. (mail entrance error, {})", mailId);
        } catch (Throwable ex) {
            LOG.warn("### MailEntranceLogic execMailEntranceAnalyzeError Exception. msg:" + ex.getMessage(), ex);
            throw new RuntimeException();
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "mailId:" + mailId));
        }
    }

    /**
     * 受信情報の登録を行います。<br>
     * 正常に登録した場合、登録した受信情報を返します。
     *
     * @param receiveAddress 受信したアドレス
     * @param sendInfo 送信情報
     * @return 受信情報
     * @throws MessagingException
     * @throws IOException
     */
    @Transactional
    private ReceiveInfo createReceiveInfo(InternetAddress receiveAddress, SendInfo sendInfo, boolean mailSanitizeFlg) throws MessagingException, IOException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        // SandBlast対応区分取得
        SandBlastKbn sandBlstKbn = sanitizeHelper.getSandBlastKbn(sendInfo);
        
        // 受信情報の作成
        ReceiveInfo receiveInfo = receiveInfoService.createReceiveInfo(
                sendInfo, receiveAddress, mailSanitizeFlg, 
                false,  //パスワードフラグはfalse（パスワード有りの場合はパスワード解除画面でReceiveInfoが生成される）
                sandBlstKbn 
        );
        receiveFileService.createReceiveFiles(receiveInfo, sendInfo.getSendFiles(), false);

        //TODO :::UT:::Start v2.2.2 DB同期エラー偽装
        if(VerifyUtil.UT_MODE){
            try {
                //送信先メールアドレスにUT用文字列がある場合にスリープし、その間にDB同期エラー偽装(LGWAN側サーバのサスペンド等）が出来るようにする
                int utSleep =VerifyUtil.getUTArgValueInt(receiveInfo.getReceiveMailAddress(), "#UT_DBSYNC#", "S", 0); //スリープ(秒）
                if(utSleep > 0){
                    VerifyUtil.outputUtLog(LOG, "#UT_DBSYNC#", false, "Start. --ReceiveAddress:%s,  --Sleep:%d", receiveInfo.getReceiveMailAddress(), utSleep);
                    Thread.sleep(utSleep*1000);
                    VerifyUtil.outputUtLog(LOG, "#UT_DBSYNC#", false, "Stop.  --ReceiveAddress:%s,  --Sleep:%d", receiveInfo.getReceiveMailAddress(), utSleep);
                }                
            } catch (Exception e) {}
        }
        //TODO :::UT:::End v2.2.2 DB同期エラー偽装*/
        
        // receiveInfoをＤＢ登録
        receiveInfoService.create(receiveInfo);            

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));

        return receiveInfo;
    }

    // 送信情報の登録
    private SendInfo insertSendInfo(MailInfoBean mailInfoBean) throws MessagingException, IOException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        SendInfo sendInfo = new SendInfo();
        sendInfo.setId(mailInfoBean.getSendInfoId());
        sendInfo.setSendUserId("");                                             // 送信者IDは空白

        try {
            InternetAddress ia = mailInfoBean.getAddressEnvelopeFrom()[0];          // EnvelopeToから取得
            sendInfo.setSendMailAddress(ia.getAddress());
            if (StringUtils.isEmpty(ia.getPersonal())) {                            // 名前が入ってない場合はメールアドレスを設定
                sendInfo.setSendUserName(ia.getAddress());
            } else {
                sendInfo.setSendUserName(ia.getPersonal());
            }
        } catch (AddressException ae) {
            sendInfo.setSendMailAddress(mailInfoBean.getHeaderEnvelopeFrom());
            sendInfo.setSendUserName(mailInfoBean.getHeaderEnvelopeFrom());

            int warningFlg = Integer.parseInt(sendInfo.getWarningFlg()) | SendInfo.ENVELOPE_FROM_FLAG;
            sendInfo.setWarningFlg(Integer.toString(warningFlg));
        }

        sendInfo.setReceiveAddresses(mailInfoBean.getHeaderEnvelopeTo());
        sendInfo.setOriginalReceiveAddresses(mailInfoBean.getHeaderEnvelopeToOrg());
        sendInfo.setOriginalSendAddress(mailInfoBean.getHeaderEnvelopeFromOrg());
        sendInfo.setMessageId(mailInfoBean.getMessageId());
        sendInfo.setSendTime(mailInfoBean.getSentDate());
        sendInfo.setToAddress(mailInfoBean.getHeaderTo());
        sendInfo.setCcAddress(mailInfoBean.getHeaderCc());
        sendInfo.setFromAddress(mailInfoBean.getHeaderFrom());
        sendInfo.setSubject(mailInfoBean.getSubject());
        sendInfo.setContent(mailInfoBean.getText());
        sendInfo.setProcDate(mailInfoBean.getMailDate());   //処理日付[v2.2.1]

        Item item = itemHelper.find(Item.EXPIRATION_DEFAULT, Item.FUNC_COMMON); // 機能ＩＤは共通
        // [2017/02/20] 期限最終日を有効にするため、23:59:59の時刻情報を設定する
//        sendInfo.setExpirationTime(DateUtil.getDateExcludeTime(DateUtil.addDays(DateUtil.getSysDate(), item.getValue())));
        sendInfo.setExpirationTime(DateUtil.getDateExcludeMillisExpirationTime(DateUtil.addDays(DateUtil.getSysDate(), item.getValue())));

        sendInfo.setPassAuto(true);
        sendInfo.setPassNotice(true);
        sendInfo.setPassWord("");
        sendInfo.setCancelFlg(false);
        sendInfo.setHistoryDisp(false);                                         // セキュリティ便の履歴には表示しない
        sendInfo.setAttachmentMailFlg(true);                                    // mailEntranceからはメール添付

        sendInfo.setApprovalFlg(false);
        sendInfo.setApprovalsRequiredAllFlg(false);
        sendInfo.setApprovalsDoneCount(0);
        sendInfo.setApprovalsRequiredCount(0);
        
//        sendInfoService.create(sendInfo);                                     // ここではDB登録しない
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));

        return sendInfo;
    }

    // 送信ファイル情報の登録
    private void insertSendFiles(SendInfo sendInfo) throws FileNotFoundException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "sendInfoId:" + sendInfo.getId()));

        File dirTmp = new File(CommonUtil.getFolderSend(sendInfo, true, false));    //一時送信フォルダ + ID
        File dirSend = new File(CommonUtil.getFolderSend(sendInfo, false, false));        //送信フォルダ + ID
        //一時フォルダを使用しているか
        boolean flgUseTmpDir = (dirTmp.compareTo(dirSend) != 0);
        try{
            if(flgUseTmpDir){
                //--------------------------------
                //一時フォルダを使用している場合
                //--------------------------------
                FileUtil.deleteFolder(dirSend.getPath(), LOG);   //事前に移動先を削除
                //メールファイルを一時フォルダから送信用フォルダに移動させる
                File mailFileTmp = new File(dirTmp.getParent(), sendInfo.getId() + ".eml");
                File mailFile = new File(dirSend.getParent(), mailFileTmp.getName());
                if(!FileUtil.moveFile(mailFileTmp.getPath(), mailFile.getPath(), LOG)){
                    throw new FileNotFoundException("#! Failed to move mailFile from localSendDir to sendDir. From:" + mailFileTmp.getPath() + " To:" + mailFile.getPath());
                }
                //一時フォルダを保存フォルダに移動
                if(dirTmp.exists()){
                    if(!FileUtil.moveFolder(dirTmp.getPath(), dirSend.getPath(), LOG)){
                        //ファイル移動に失敗した場合はエラーとする（ウィルス等）
                        throw new FileNotFoundException("#! Failed to move sendDir from localSendDir to sendDir. From:" + dirTmp.getPath() + " To:" + dirSend.getPath());
                    }
                }
            }

            File[] files = dirSend.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.isDirectory()) {                            // ディレクトリは存在しないが保険として判定（再帰的な読込も不要）
                        SendFile sendFile = new SendFile();
                        sendFile.setId(IdUtil.createUUID());
                        sendFile.setSendInfo(sendInfo);
                        sendFile.setFileName(file.getName());
                        sendFile.setFilePath(file.getPath());
                        sendFile.setFileFormat(FileUtil.getSuffix(file.getName()));
                        sendFile.setFileSize(file.length());
                        sendFile.setFilePass("");
    //                    sendFileService.create(sendFile);                         // ここではDB登録しない
                        sendFile.resetDate();       //作成日時・更新日時のセット[v2.2.3]
                        sendInfo.getSendFiles().add(sendFile);                      // 送信情報クラスに追加
                    }
                }
            }
        }finally{
            if(flgUseTmpDir){
                //一時フォルダの削除
                FileUtil.deleteFolder(dirTmp.getPath(), LOG);
            }
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "sendInfoId:" + sendInfo.getId()));
        }
    }
}
