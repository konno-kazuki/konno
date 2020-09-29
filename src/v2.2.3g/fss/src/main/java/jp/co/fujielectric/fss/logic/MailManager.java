package jp.co.fujielectric.fss.logic;

import com.sun.mail.smtp.SMTPAddressFailedException;
import com.sun.mail.smtp.SMTPSendFailedException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimePart;
import javax.mail.internet.MimeUtility;
import javax.persistence.NoResultException;
import jp.co.fujielectric.fss.data.CommonBean;
import jp.co.fujielectric.fss.data.CommonEnum.FileSanitizeResultKbn;
import jp.co.fujielectric.fss.data.CommonEnum.MailAnalyzeResultKbn;
import jp.co.fujielectric.fss.data.FssMimeMessage;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.data.MailInfoBean;
import jp.co.fujielectric.fss.data.ManageIdBean;
import jp.co.fujielectric.fss.entity.ApproveInfo;
import jp.co.fujielectric.fss.entity.BasicUser;
import jp.co.fujielectric.fss.entity.DecryptFile;
import jp.co.fujielectric.fss.entity.LgwanDomain;
import jp.co.fujielectric.fss.entity.OnceUser;
import jp.co.fujielectric.fss.entity.ReceiveFile;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.RejectDomain;
import jp.co.fujielectric.fss.entity.SendFile;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.entity.SendRequestInfo;
import jp.co.fujielectric.fss.exception.FssException;
import jp.co.fujielectric.fss.service.BasicUserService;
import jp.co.fujielectric.fss.service.OnceUserService;
import jp.co.fujielectric.fss.service.RejectDomainService;
import jp.co.fujielectric.fss.service.LgwanDomainService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.FileUtil;
import static jp.co.fujielectric.fss.util.FileUtil.saveFileWithRenameMaxLen;
import jp.co.fujielectric.fss.util.IdUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

/**
 * メール管理クラス
 */
@RequestScoped
public class MailManager {

    private static final String ENVELOPE_TO = "X-Envelope-To";
    private static final String ENVELOPE_FROM = "X-Envelope-From";
    private static final String ENVELOPE_TO_ORG = "X-Envelope-Org-To";
    private static final String ENVELOPE_FROM_ORG = "X-Envelope-Org-From";

    private static final int MULTIPART_MAX_LEVEL = 2;   //処理対象マルチパート最大階層
   
    @Inject
    private Logger LOG;

    @Inject
    private CommonBean commonBean;

    @Inject
    private ItemHelper itemHelper;

    @Inject
    private OnceUserService onceUserService;

    @Inject
    private BasicUserService basicUserService;

    @Inject
    private RejectDomainService rejectDomainService;

    @Inject
    private LgwanDomainService lgwanDomainService;

    /**
     * mailAddressToテキストを作成
     *
     * @param mailToList
     *
     * @return mailAddressToテキスト
     */
    public String getMailAddressTo(List<InternetAddress> mailToList) {
        return getMailAddressTo(mailToList, false);
    }

    /**
     * mailAddressToテキストを作成
     *
     * @param mailToList
     * @param addressOnly
     *
     * @return mailAddressToテキスト
     */
    public String getMailAddressTo(List<InternetAddress> mailToList, boolean addressOnly) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        String mailAddressTo = "";
        String separator = "";
        for (InternetAddress ia : mailToList) {
            if (addressOnly || StringUtils.isEmpty(ia.getPersonal())) {
                mailAddressTo += separator + ia.getAddress();
            } else {
                mailAddressTo += separator + ia.toString();
            }
            separator = ", ";
        }
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        return mailAddressTo;
    }

    /**
     * メール情報の変換（eml ⇒ MimeMessage ⇒ MailInfoBean）
     * @param mailId
     * @param mailDate
     * @param saveDir
     * @param flgMailEntrance   //メールエントランスからの呼び出しかどうか（Trueの場合は添付ファイルの文字数チェック対象とする）【v2.1.13】
     * @return
     * @throws FssException
     */
    public MailInfoBean convertMailInfoBean(
            String mailId, String mailDate, String saveDir, boolean flgMailEntrance) throws FssException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "mailId:" + mailId));
        LOG.debug("### convertMailInfoBean(" + mailId + ") Start ###");
        MailAnalyzeResultKbn errDetail = MailAnalyzeResultKbn.ORGMAIL_READ_ERROR;
        try {
            //ファイル文字数制限値
            //MailEntranceのときだけチェックする
            int maxLen = (flgMailEntrance ? FileUtil.MAX_FILENAME_LEN: 0);
            
//            String mailPath = CommonUtil.getSetting("maildir") + mailId + ".eml";
            File mailFile = new File(CommonUtil.getFolderMail(mailDate), mailId + ".eml");

            long emlFileSize = mailFile.length();
            //---------メモリ情報DEBUG出力--------------
            LOG.debug("  ---FreeMemory:" + Runtime.getRuntime().freeMemory()
                    + "  ---EMailFileSize:" + emlFileSize);
            //-----------------------------------------

            MailInfoBean mailInfoBean = new MailInfoBean();
            mailInfoBean.setSendInfoId(mailId);                                 // 送信情報ＩＤを設定
            mailInfoBean.setEmlFileSize(emlFileSize);                           // Emlファイルサイズを設定
            mailInfoBean.setMailDate(mailDate);                                 // メール日付[v2.2.1]            
            try (InputStream inputStream = new FileInputStream(mailFile)) {
                Session session = Session.getInstance(new java.util.Properties());
                FssMimeMessage mimeMessage = new FssMimeMessage(session, inputStream);  // （注意）メールファイルのサイズが大きいとメモリ不足で異常終了(OutOfMemorryError)
                //ヘッダーのチェック&変換（AppleMail対応） （※例外発生なし）
                errDetail = MailAnalyzeResultKbn.HEADER_OTHER_ERROR;    //以降の例外発生はヘッダー解析異常とみなす
                checkMailHeader(mimeMessage);
                mimeMessage.setOriginalMessageId(mimeMessage.getMessageID());       // 元メールのMessageIDを退避
                analyzeMimeMessage(mailInfoBean, mimeMessage);                      // メール情報の解析・取得
                errDetail = MailAnalyzeResultKbn.BODY_ERROR;            //以降の例外発生はメール本文解析異常とみなす
                mailInfoBean.setNoticeCode(checkMimeMessage(mimeMessage));          // メール対応チェック
                errDetail = MailAnalyzeResultKbn.ATTACHMENT_ERROR;      //以降の例外発生は添付ファイル解析異常とみなす
                savaAttachment(mimeMessage, mailId, saveDir, mailInfoBean, maxLen); // 添付ファイルの保存
                // ※savaAttachmentではmimeMessageを編集しています！
            }
            //TODO :::UT:::Start v2.2.1 例外発生テスト
            if(VerifyUtil.UT_MODE){
                //件名にUT用文字列が含まれていれば例外発生（#UT_MAILCONV#） #ER=x#でerrDetailを指定可能
                int utDetail = VerifyUtil.getUTArgValueInt(mailInfoBean.getSubject(), "#UT_MAILCONV#", "ER", -1);
                if(utDetail >= 0){
                    errDetail = MailAnalyzeResultKbn.getMailAnalyzeResultKbn(utDetail); //エラー詳細コード
                    throw new RuntimeException("UT_メール解析例外テスト");
                }
            }
            //TODO :::UT:::End v2.2.1  例外発生テスト*/                
            LOG.debug("### convertMailInfoBean() End ###");
            return mailInfoBean;
        }catch (Throwable e) { //Exception以外にError(OutofMemorryError等）にも対応できるようThrowableをCatch）
            if(e instanceof FssException && ((FssException)e).getCode() != 0){
                //検査結果がセットされた検査済み例外をキャッチした場合
                errDetail = MailAnalyzeResultKbn.getMailAnalyzeResultKbn(((FssException)e).getCode());
                LOG.error("#!メール情報の変換で例外発生  (メール解析エラー詳細:{},  ErrMsg:{})", errDetail, e.getMessage());
                throw (FssException)e;  //そのままFssExceptionをスロー
            }
            //それ以外の非検査例外をキャッチした場合
            LOG.error("#!メール情報の変換で非検査例外発生  (メール解析エラー詳細:{},  Exception:{})", errDetail, e.toString());
            throw new FssException(errDetail.value, e.getMessage(), e); //検査済み例外としてFssExceptionをスロー
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "mailId:" + mailId));
        }
    }

    // メール情報の解析・取得（MimeMessage ⇒ MailInfoBean）
    private void analyzeMimeMessage(MailInfoBean mailInfoBean, FssMimeMessage mimeMessage) throws MessagingException, IOException, FssException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        MailAnalyzeResultKbn errDetail = MailAnalyzeResultKbn.HEADER_OTHER_ERROR;   //メールヘッダ解析異常
        try {
            mailInfoBean.setMessageId(mimeMessage.getMessageID());
            mailInfoBean.setSubject(mimeMessage.getSubject());
            mailInfoBean.setHeaderFrom(getMailHeader(mimeMessage, "From"));
            mailInfoBean.setHeaderTo(getMailHeader(mimeMessage, "To"));
            mailInfoBean.setHeaderCc(getMailHeader(mimeMessage, "Cc"));
            mailInfoBean.setHeaderBcc(getMailHeader(mimeMessage, "Bcc"));
            mailInfoBean.setHeaderEnvelopeFrom(getMailHeader(mimeMessage, ENVELOPE_FROM));
            mailInfoBean.setHeaderEnvelopeTo(getMailHeader(mimeMessage, ENVELOPE_TO));
            mailInfoBean.setHeaderEnvelopeFromOrg(getMailHeader(mimeMessage, ENVELOPE_FROM_ORG));
            mailInfoBean.setHeaderEnvelopeToOrg(getMailHeader(mimeMessage, ENVELOPE_TO_ORG));
            mailInfoBean.setSentDate(mimeMessage.getSentDate());

            // メールヘッダー削除
            mimeMessage.removeHeader(ENVELOPE_TO);
            mimeMessage.removeHeader(ENVELOPE_FROM);

            // 以下、本文の解析・取得
            errDetail = MailAnalyzeResultKbn.BODY_ERROR;    //メール本文解析異常
            if (mimeMessage.getContent() instanceof Multipart) {
                MimeMultipart multiPartOriginal = (MimeMultipart) mimeMessage.getContent();

                // 最初のtext/plainを本文とする
                String text = "";
                for (int indexPart = 0; indexPart < multiPartOriginal.getCount(); indexPart++) {
                    BodyPart part = multiPartOriginal.getBodyPart(indexPart);

                    if (!StringUtils.isEmpty(text)) {                               // 最初の本文のみ有効
                        break;
                    } else if (part.getContentType().toLowerCase().contains("multipart")) {
                        // 本文のみ、さらなるMultipartも探す（現状は２階層まで⇒再帰構造は不要）
                        MimeMultipart _multiPart = (MimeMultipart) part.getContent();
                        for (int _indexPart = 0; _indexPart < _multiPart.getCount(); _indexPart++) {
                            Part _part = _multiPart.getBodyPart(_indexPart);
                            if (_part.getContentType().toLowerCase().contains("text/plain")) {
                                text = _part.getContent().toString();
                                break;
                            }
                        }
                    } else if (isAttachmentPart(part)) {
                        // Do not process.
                    } else if (part.getContentType().toLowerCase().contains("text/plain")) {
                        text = part.getContent().toString();
                        break;
                    }
                }
                mailInfoBean.setText(text);
            } else if (mimeMessage.getContentType().toLowerCase().contains("text/plain")) {
                // シングルパート：本文
                mailInfoBean.setText(mimeMessage.getContent().toString());
            } else {
                // 本文は無し
                mailInfoBean.setText("");
            }
        } catch (Throwable e) {
            throw new FssException(errDetail.value, e.getMessage(), e);
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }

    private String getMailHeader(Part part, String headerName) {
        // ヘッダー情報をそのまま返す
        try {
            String[] hValues = part.getHeader(headerName);   //ヘッダー名を指定してヘッダー設定値(複数)を取得
            if(hValues == null || hValues.length == 0)
                return "";
            return hValues[0];     //ヘッダー設定値を返す
        } catch (Exception ex) {
            return "";
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }

    // メール対応チェック
    private String checkMimeMessage(FssMimeMessage mimeMessage) throws MessagingException, IOException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        try{
            Object objContent = mimeMessage.getContent();
            if (objContent instanceof Multipart) {
                MimeMultipart multiPart = (MimeMultipart) objContent;

                //[2017/02/23] 不明パートを検知するための対策
                boolean textFlg = false;
                String unknownCode = "nortice_unknown_part";

                for (int indexPart = 0; indexPart < multiPart.getCount(); indexPart++) {
                    BodyPart part = multiPart.getBodyPart(indexPart);

                    if (part.getContentType().toLowerCase().contains("multipart")) {
                        // さらなるMultipartも探す（現状は２階層まで⇒再帰構造は不要）
                        MimeMultipart _multiPart = (MimeMultipart) part.getContent();
                        for (int _indexPart = 0; _indexPart < _multiPart.getCount(); _indexPart++) {
                            BodyPart _part = _multiPart.getBodyPart(_indexPart);
                            if (_part.getContentType().toLowerCase().contains("text/plain") && !textFlg) {
                                textFlg = true;
                            } else {
                                return unknownCode;
                            }
                        }
                    } else if (isAttachmentPart(part)){
                        // Do not process.
                    } else if (part.getContentType().toLowerCase().contains("text/plain")) {
                        if (!textFlg) {
                            textFlg = true;
                        } else {
                            return unknownCode;
                        }
                    } else {
                        return unknownCode;
                    }
                }
            } else if (mimeMessage.getContentType().toLowerCase().contains("text/html")) {
                //[2017/02/17]シングルパートのHTMLだった場合、本文が消されている旨を連絡。
                return "nortice_nonsupport_part";
            }
            return "";
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }

    // メールチェック結果の反映
    public void mergeNorticeMessage(MailInfoBean mailInfoBean) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        try{
            if (StringUtils.isEmpty(mailInfoBean.getNoticeCode())) {
                return;
            }

            // メール本体無害化フラグ取得
            Item item = itemHelper.find(Item.MAIL_SANITIZE_FLG, Item.FUNC_COMMON);  // 機能ＩＤは共通
            boolean mailSanitizeFlg = (item.getValue().equalsIgnoreCase("true"));
            if (mailSanitizeFlg) {
                return;                                            // メール本体無害化の場合、正常返却
            }
            String noticeMessage = itemHelper.findMailMessage(mailInfoBean.getNoticeCode(), Item.FUNC_COMMON).getValue();
            if (StringUtils.isEmpty(noticeMessage)) {
                return;
            }
            mailInfoBean.setText(noticeMessage + mailInfoBean.getText());
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }
    
    /**
     * 添付ファイルと添付除外emlの保存
     * @param mimeMessage
     * @param mailId
     * @param saveDir
     * @param mailInfoBean
     * @param maxLen    添付ファイル最大文字数【v2.1.13】
     * @throws MessagingException
     * @throws IOException 
     */
    private void savaAttachment(MimeMessage mimeMessage, String mailId, String saveDir, MailInfoBean mailInfoBean, int maxLen)
            throws MessagingException, IOException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "mailId:" + mailId));
        // 保存先を削除する
        String savePath = saveDir + mailId;
        FileUtil.deleteFiles(new File(savePath + ".eml"), false);
        FileUtil.deleteFolder(savePath, LOG);

        String msg = "";
        Object objContent = mimeMessage.getContent();
        if (objContent instanceof Multipart) {
            MimeMultipart multiPart = (MimeMultipart) objContent;

            for (int indexPart = multiPart.getCount()-1; indexPart >= 0 ; indexPart--) {
                BodyPart part = multiPart.getBodyPart(indexPart);
                if (isAttachmentPart(part)) {
                    //添付ファイルの場合は分離（保存してマルチパートから削除）
//                    FileUtil.saveFileWithRename(part.getInputStream(), savePath + File.separatorChar + getDecodeFileName(part), FileUtil.MAX_FILENAME_LEN, LOG);
                    saveAttachmentFileWithRename(mailInfoBean, savePath, part, maxLen);
                    multiPart.removeBodyPart(indexPart);
                }
            }
//            mimeMessage.saveChanges();                                      // メール情報の変更内容を更新
        } else if (mimeMessage.getContentType().toLowerCase().contains("text/plain")) {
        } else if (mimeMessage.getContentType().toLowerCase().contains("text/html")) {
        } else {
            // シングルパート：添付ファイルのみ対処
            if(isAttachmentPart(mimeMessage)) {
                //添付ファイルの場合は分離保存（保存してテキストパートに置換え）
//                FileUtil.saveFileWithRename(mimeMessage.getInputStream(),
//                            savePath + File.separatorChar + getDecodeFileName(mimeMessage), FileUtil.MAX_FILENAME_LEN, LOG);
                saveAttachmentFileWithRename(mailInfoBean, savePath, mimeMessage, maxLen);
                //空のテキストパートとする。
                mimeMessage.setDisposition(null);
                mimeMessage.setText("");
//                mimeMessage.saveChanges();                                      // メール情報の変更内容を更新
            }
        }

        //ContentsのcharsetはWindows-31JからUTF-8に変換する（AppleMail対応）
        try{
            partConvertCharset(mimeMessage, "UTF-8", true);
            mimeMessage.saveChanges();                                      // メール情報の変更内容を更新
        }catch(IOException | MessagingException e) {
            LOG.warn("savaAttachment partConvertCharset Warning", e);
        }

        // 添付ファイルを除くメール情報をemlファイルに保存
        try(OutputStream os1 = FileUtil.getFileOutputStream(savePath +".eml")){
            mimeMessage.writeTo(os1);
        }

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "mailId:" + mailId));
    }


    //【v2.1.13】
    /**
     * 添付ファイル保存処理
     * @param mailInfoBean
     * @param savePath
     * @param part
     * @param maxLen    ファイル名最大文字数
     * @throws MessagingException
     * @throws IOException 
     */
    private void saveAttachmentFileWithRename(MailInfoBean mailInfoBean, String savePath, Part part, int maxLen) throws MessagingException, IOException
    {
        try {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
            
            //出力先ファイルパス
            Path dstPath = Paths.get(savePath, getDecodeFileName(part));
            boolean flgTooLong = false;    //ファイル名短縮リネームかどうか
            //添付ファイルを保存（同名ファイルがあれば連番付けて保存）v2.1.13前の保存処理
            Path dstPathNew = FileUtil.saveFileWithRename(part.getInputStream(), dstPath.toString(), maxLen);
            if(dstPathNew == null){
                //文字数制限を超えるため保存出来なかった場合は短縮リネーム処理で保存 【v2.1.13】
                dstPathNew = saveFileWithRenameMaxLen(part.getInputStream(), dstPath.toString(), maxLen);
                flgTooLong = true;
            }
            if(dstPath.compareTo(dstPathNew) != 0){
                //ファイル名変更があった場合、ログを出力する
                //区分：Warn
                //メッセージ：
                //「##### FileName  change.　ファイル名が長すぎるためリネームしました。　
                // （MailID:{メールID(SendInfoID)}, SendMailAddress:{送信メールアドレス}, ReceiveMailAddress:{受信メールアドレス}	
                // , BeforeFileName:{リネーム前ファイル名}, AfterFileName:{リネーム後ファイル名}）」	
                //ログ出力
                String msg = "";
                if(flgTooLong){
                    //短縮リネームの場合
                    msg = "ファイル名が長すぎるためリネームしました。　";
                }else{
                    //同名ファイルありによるリネームの場合
                    msg = "ファイル名が重複するためリネームしました。　";
                }
                String sendMailAddress = mailInfoBean.getHeaderEnvelopeFrom();  //送信メールアドレス（デコードなし）
                String receiveMailAddress = mailInfoBean.getHeaderEnvelopeTo(); //受信メールアドレス（デコードなし）
                try{
                    sendMailAddress = FssMimeMessage.decodeMimeString(mailInfoBean.getHeaderEnvelopeFrom());    //送信メールアドレス（デコードあり）
                    receiveMailAddress = FssMimeMessage.decodeMimeString(mailInfoBean.getHeaderEnvelopeTo());   //受信メールアドレス（デコードあり）
                }catch(Exception e){
                    //デコード失敗は無視する
                }
                LOG.warn("##### FileName  change.　{} (MailID:{}, SendMailAddress:{}, ReceiveMailAddress:{}, BeforeFileName:{}, AfterFileName:{})"
                        ,msg, mailInfoBean.getSendInfoId(), sendMailAddress, receiveMailAddress, dstPath.getFileName(), dstPathNew.getFileName());
            }
        } catch (IOException | MessagingException e) {
            LOG.error("#!添付ファイルの分離保存処理で例外発生しました。 msg:" + e.getMessage());
            //この処理での例外発生はそのままスローして呼出元で対応する
            throw e;
        } finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));            
        }
    }
    
    // BodyPartからデコードしたファイル名を取得
    private String getDecodeFileName(Part part) throws MessagingException{
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        String fileName;

        //ファイル名が設定されていない場合に解析エラーとするため例外をスローする
        //※解析エラーとせずこのメソッド下部の処理で固定ファイル名となるようにする場合は、このif文のスコープを削除すること
        if(part.getFileName() == null){
            LOG.warn("#!添付ファイルパート(Content-Disposition: attachment)にファイル名の指定（Content-Type:name= / Content-Disposition: filename=）がありません");
            throw new MessagingException("#!添付ファイルパート(Content-Disposition: attachment)にファイル名の指定（Content-Type:name= / Content-Disposition: filename=）がありません");
        }
        
        // [2016/12/26] エンコードされたファイル名に対応（デコード失敗時は元の名称）
        // [2017/02/21] 連続したMimeEncode指定はライブラリが読み込めないため、半角スペースを挿入
        // [2017/05/11] MimeUtilityで変換できないascii＋RFC2231の対応を含み関数化
        fileName = decodeMimeFileName(part.getFileName());
        if (fileName == null) {
            // [2017/01/18] fileName無しの場合、メール形式かその他ファイルとして固定ファイル名を指定する。
            if (part.getContentType().toLowerCase().contains("message/rfc822")) {
                fileName = "message.eml";
            } else {
                fileName = "file";
            }
        }
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        return fileName;
    }

    // MimeUtilityで対応できない形式のファイル名をデコードする
    private String decodeMimeFileName(String mimeFileName) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        mimeFileName = FssMimeMessage.decodeMimeString(mimeFileName);
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        return mimeFileName;
    }

    // 基本の通知メール情報の取得
    private MailInfoBean getBaseNorticeInfo(String funcId) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        // メールメッセージマスタから各種文言取得
        String subject = itemHelper.findMailMessage("nortice_subject", funcId).getValue();
        String headerFrom = itemHelper.findMailMessage("nortice_headerFrom", funcId).getValue();
        String text = itemHelper.findMailMessage("nortice_text", funcId).getValue();

        // メール情報クラスの生成
        MailInfoBean mailInfoBean = new MailInfoBean();
        mailInfoBean.setSubject(subject);
        try {
            InternetAddress internetAddress = new InternetAddress(headerFrom);
            internetAddress.setPersonal(internetAddress.getPersonal());
            mailInfoBean.setHeaderFrom(internetAddress.toString());
        } catch (AddressException | UnsupportedEncodingException ex) {
        }
        mailInfoBean.setText(text);

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        return mailInfoBean;
    }

    // メール情報クラスのヘッダ情報調整
    private void editMailHeaderFromSendInfo(MailInfoBean mailInfoBean, SendInfo sendInfo, String funcId) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        // 元メールアドレス情報をコピー
        mailInfoBean.setHeaderEnvelopeToOrg(sendInfo.getOriginalReceiveAddresses());
        mailInfoBean.setHeaderEnvelopeFromOrg(sendInfo.getOriginalSendAddress());

        // [2017/11/16] Fromを送信元メールアドレスとする調整。
        // [2017/11/30] マスタ設定が有効な場合のみとする。
        boolean originalHeaderFromFlg = false;
        // メール無害化であること
        if(sendInfo.isAttachmentMailFlg()) {
            // 元メールアドレス情報が両方とも入っている場合、ヘッダFromを元メールの送信アドレスで上書きする
            if (!StringUtils.isEmpty(sendInfo.getOriginalReceiveAddresses())
                    && !StringUtils.isEmpty(sendInfo.getOriginalSendAddress())) {
                originalHeaderFromFlg = true;
            } else {
                //フラグが有効であれば同様に送信アドレスをFromとする
                try {
                    if(itemHelper.find(Item.ORIGINAL_HEADER_FROM_PASSWORD_UNLOCK, funcId).getValue().equalsIgnoreCase("true")) {
                        originalHeaderFromFlg = true;
                    }
                } catch(NoResultException e) {}
            }
        }

        if(originalHeaderFromFlg) {
            mailInfoBean.setHeaderFrom(sendInfo.getFromAddress());
            mailInfoBean.setHeaderEnvelopeFrom(sendInfo.getSendMailAddress());
        }else{
            mailInfoBean.setHeaderEnvelopeFrom(
                    getAddressShort(mailInfoBean.getHeaderFrom(), mailInfoBean.getHeaderFrom()));
        }
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }

    // メール情報クラスのヘッダ情報調整
    private void editMailHeaderFromReceiveInfo(MailInfoBean mailInfoBean, ReceiveInfo receiveInfo) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        SendInfo sendInfo = receiveInfo.getSendInfo();

        // 元メールアドレス情報をコピー
        mailInfoBean.setHeaderEnvelopeToOrg(receiveInfo.getOriginalReceiveAddress());
        mailInfoBean.setHeaderEnvelopeFromOrg(sendInfo.getOriginalSendAddress());

        // 元メールアドレス情報が両方とも入っている場合、ヘッダFromを元メールの送信アドレスで上書きする
        if (!StringUtils.isEmpty(receiveInfo.getOriginalReceiveAddress())
                && !StringUtils.isEmpty(sendInfo.getOriginalSendAddress())) {
            mailInfoBean.setHeaderFrom(sendInfo.getFromAddress());
        }
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }

    // メールサーバ接続
    private Session connectMail() {
        return connectMail(CommonUtil.getSetting("smtpport_out"));              // デフォルトは外向けポートとする
    }

    // メールサーバ接続
    private Session connectMail(String smtpPort) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        Properties props = new Properties();
        props.put("mail.smtp.host", CommonUtil.getSetting("smtp"));
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.connectiontimeout", "100000");
        props.put("mail.smtp.timeout", "100000");
//        props.put("mail.debug", "true");      // 添付付きメールだとログが大きすぎるのでコメント解除時は注意！！！

        // (認証関連)
        // ※内部サーバのため認証は行わない
//        props.put("mail.smtp.auth", "true");
//        props.put("mail.smtp.starttls.enable", "true");
//        Session session = Session.getInstance(props, new javax.mail.Authenticator() {
//            @Override
//            protected PasswordAuthentication getPasswordAuthentication() {
//                return new PasswordAuthentication("secloud", "fujibunsho");
//                // 認証情報を直接指定
//            }
//        });
//        return Session.getDefaultInstance(props, null);
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        return Session.getInstance(props, null);
    }

    /**
     * メール送信※旧処理（エンコードあり）<br>
     * （元emlのヘッダを利用しない）
     *
     * @param session
     * @param mailInfoBean
     */
    private void sendMail(Session session, MailInfoBean mailInfoBean, Multipart multipart) {
        // [2017/08/23]エンコード要否を指定する引数(flgEncord)を追加したオーバーロードメソッド追加に伴い
        // エンコード有りとして、当該メソッドを呼出すだけに変更
        sendMail(session, mailInfoBean, multipart, true);
    }

    /**
     * メール送信※旧処理（エンコード要否指定あり）<br>
     * （元emlのヘッダを利用しない）
     *
     * @param session
     * @param mailInfoBean
     * @param multipart
     * @param flgEncord エンコード要否指定
     */
    private void sendMail(Session session, MailInfoBean mailInfoBean, Multipart multipart, boolean flgEncord) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        // [2017/08/23]エンコード要否を指定する引数(flgEncord)を追加

        // [2017/02/15]テキストエンコードをUTF-8指定とする。
        String charset = "UTF-8";

        try {
            //MimeMessageを取得 [v2.1.11]共通化
            FssMimeMessage mimeMessage = getMimeMessageForSend(session, mailInfoBean);

            // 元メールアドレス情報がある場合、ヘッダに埋め込む
            if (!StringUtils.isEmpty(mailInfoBean.getHeaderEnvelopeToOrg())) {
                mimeMessage.setHeader(ENVELOPE_TO_ORG, mailInfoBean.getHeaderEnvelopeToOrg());
            }
            if (!StringUtils.isEmpty(mailInfoBean.getHeaderEnvelopeFromOrg())) {
                mimeMessage.setHeader(ENVELOPE_FROM_ORG, mailInfoBean.getHeaderEnvelopeFromOrg());
            }
            
            //X-Envelope-Org-From,X-Envelope-Org-Toがあるメールかどうか[v2.2.3]
            boolean hasEnvelopeOrg = (!StringUtils.isEmpty(mailInfoBean.getHeaderEnvelopeToOrg())
                    && !StringUtils.isEmpty(mailInfoBean.getHeaderEnvelopeFromOrg()));
            
            try {
                // X-Envelope-Org-From,X-Envelope-Org-To がある場合、X-Envelope-Org-ToをToに埋め込む[v2.2.3]
                if (hasEnvelopeOrg) {
                    mimeMessage.setRecipients(Message.RecipientType.TO, mailInfoBean.getAddressEnvelopeToOrg());
                }else{
                    mimeMessage.setRecipients(Message.RecipientType.TO, mailInfoBean.getAddressTo());
                }
                mimeMessage.setRecipients(Message.RecipientType.CC, mailInfoBean.getAddressCc());
                mimeMessage.setRecipients(Message.RecipientType.BCC, mailInfoBean.getAddressBcc());
            } catch (Exception e) {
                // ヘッダCc/Bccを削除
                mimeMessage.removeHeader("Cc");     // ヘッダCc削除
                mimeMessage.removeHeader("Bcc");    // ヘッダBcc削除

                // X-Envelope-Org-From,X-Envelope-Org-To がある場合、X-Envelope-Org-ToをToに埋め込む[v2.2.3]
                if (hasEnvelopeOrg) {
                    mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parseHeader(mailInfoBean.getHeaderEnvelopeToOrg(), false));                    
                }else{
                    mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parseHeader(mailInfoBean.getHeaderEnvelopeTo(), false));
                }
            }
            LOG.debug("#SendMail Header-To:{}", (mimeMessage.getHeader("To") != null && mimeMessage.getHeader("To").length > 0 ? mimeMessage.getHeader("To")[0] : null));

            mimeMessage.setSubject(mailInfoBean.getSubject(), charset);         // 件名
            try {
                mimeMessage.setFrom(mailInfoBean.getAddressFrom()[0]);              // 送信元
            } catch (Exception e) {
                mimeMessage.setFrom(mailInfoBean.getHeaderFrom());              // 送信元
            }
            mimeMessage.setSentDate(mailInfoBean.getSentDate());                // 送信時間
            if (multipart != null) {
                Multipart mp = multipart;
                MimeBodyPart mbp = new MimeBodyPart();
                mbp.setText(mailInfoBean.getText(), charset);
                mp.addBodyPart(mbp, 0);
                mimeMessage.setContent(multipart);                              // 本文＋添付ファイル
            } else if (mailInfoBean.getText() != null) {                           // 本文
                mimeMessage.setText(mailInfoBean.getText(), charset);
            } else {
                mimeMessage.setText("", charset);
            }

//sendMailExec()を呼出すその他メソッド内でも同じ処理をしており冗長なため、共通化できる処理をsendMailExec()に移動 [248対応（簡易版）]
//            // [2017/08/23]エンコード要否を指定する引数(flgEncord)を追加
//            if (flgEncord) {
//                // [2017/03/07]V2.1エンコード変換対応
//                mailConvertCharset(mimeMessage);
//            }
//
//            InternetAddress[] ia;
//            try {
//                ia = mailInfoBean.getAddressEnvelopeTo();
//            } catch (Exception e) {
//                ia = InternetAddress.parseHeader(mailInfoBean.getHeaderEnvelopeTo(), false);
//            }
//
//            try {
//                sendMailExec(mimeMessage, ia);    // メール送信実行 [248対応（簡易版）]
//            } catch (SMTPSendFailedException e) {
//                LOG.error("### SMTPSendFailedException: SendInfoId:" + mailInfoBean.getSendInfoId()
//                        + " ReceiveInfoId:" + mailInfoBean.getReceiveInfoId()
//                        + " HeaderEnvelopeFrom:" + mailInfoBean.getHeaderEnvelopeFrom()
//                        + " HeaderEnvelopeTo:" + mailInfoBean.getHeaderEnvelopeTo());
//                throw e;
//            }
            // メール送信実行
            sendMailExec(mimeMessage, mailInfoBean, flgEncord);    // [248対応（簡易版）] 冗長処理を共通化のためsendMailExec内に移動。引数を追加。
        } catch (Exception ex) {
            LOG.error("sendMail error:", ex);
            throw new RuntimeException("sendMail error:" + ex.getMessage());
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }

    /**
     * メール送信<br>
     * （元emlからヘッダを転記する）
     *
     * @param session
     * @param mailInfoBean
     * @param emlPath
     */
    private void sendMail(Session session, MailInfoBean mailInfoBean, String emlPath, Multipart multipart) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        // [2017/02/15]テキストエンコードをUTF-8指定とする。
        String charset = "UTF-8";

        // 添付ファイル無しemlファイルをMimeMessageに変換
        try {
            //MimeMessageを取得 [v2.1.11]共通化
            FssMimeMessage mimeMessage = getMimeMessageForSend(session, mailInfoBean, emlPath);

            // 現バージョンでは元メールのマルチパートをクリアする
            // ※テキストのみでもマルチパートで構成する
            MimeMultipart multipartNew = new MimeMultipart("mixed");
            mimeMessage.setContent(multipartNew);

            // 添付ファイルをmultiPartに追加
            if (multipart != null) {
                for (int indexPart = 0; indexPart < multipart.getCount(); indexPart++) {
                    multipartNew.addBodyPart(multipart.getBodyPart(indexPart));
                }
            }

            // 本文を追加
            if (mailInfoBean.getText() != null) {
                MimeBodyPart mimeBodyPart = new MimeBodyPart();
                mimeBodyPart.setText(mailInfoBean.getText(), charset);
                multipartNew.addBodyPart(mimeBodyPart, 0);
            }

            mimeMessage.saveChanges();

//sendMailExec()を呼出すその他メソッド内でも同じ処理をしており冗長なため、共通化できる処理をsendMailExec()に移動 [248対応（簡易版）]
//            // [2017/03/07]V2.1エンコード変換対応
//            mailConvertCharset(mimeMessage);
//
//            InternetAddress[] ia;
//            try {
//                ia = mailInfoBean.getAddressEnvelopeTo();
//            } catch (Exception e) {
//                ia = InternetAddress.parseHeader(mailInfoBean.getHeaderEnvelopeTo(), false);
//            }
//
//            try {
//
//                sendMailExec(mimeMessage, ia);    // メール送信実行
//            } catch (SMTPSendFailedException e) {
//                LOG.error("### SMTPSendFailedException: SendInfoId:" + mailInfoBean.getSendInfoId()
//                        + " ReceiveInfoId:" + mailInfoBean.getReceiveInfoId()
//                        + " HeaderEnvelopeFrom:" + mailInfoBean.getHeaderEnvelopeFrom()
//                        + " HeaderEnvelopeTo:" + mailInfoBean.getHeaderEnvelopeTo());
//                throw e;
//            }

            // メール送信実行
            sendMailExec(mimeMessage, mailInfoBean, true);    // [248対応（簡易版）] 冗長処理を共通化のためsendMailExec内に移動。引数を追加。

        } catch (Exception ex) {
            LOG.error("sendMail error:", ex);
            throw new RuntimeException("sendMailSendFiles error:" + ex.getMessage());
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }

    /**
     * メール送信<br>
     * （メール本体無害化用）
     *
     * @param session
     * @param mailInfoBean
     * @param emlPath
     * @param multipart
     */
    private void sendSanitizedMail(Session session, MailInfoBean mailInfoBean, String emlPath, Multipart multipart) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        // 無害化済みemlファイルをMimeMessageに変換
        try {
            //MimeMessageを取得 [v2.1.11]共通化
            FssMimeMessage mimeMessage = getMimeMessageForSend(session, mailInfoBean, emlPath);

            Object objContent = mimeMessage.getContent();
            if (objContent instanceof Multipart) {
                // [マルチパートの場合]
                //  ①本文があれば、該当BodyPartの本文を加工
                //  ②本文がなければ、本文BodyPartを追加
                //  最後に添付ファイルがあればマルチパートに追加
                MimeMultipart multiPartOrg = (MimeMultipart) mimeMessage.getContent();

                // 最初のtext/plainを本文とする
                MimeBodyPart partText = null;
                for (int indexPart = 0; indexPart < multiPartOrg.getCount(); indexPart++) {
                    MimeBodyPart part = (MimeBodyPart) multiPartOrg.getBodyPart(indexPart);
                    // multipartの宣言内に「text/plain」の文字があると、本文と誤認するケースがあるため、先にmultipartを確認。
                    if (partText != null) {
                        break;
                    } else if (part.getContentType().toLowerCase().contains("multipart")) {
                        // 本文のみ、さらなるMultipartも探す（現状は２階層まで⇒再帰構造は不要）
                        MimeMultipart _multiPart = (MimeMultipart) part.getContent();
                        for (int _indexPart = 0; _indexPart < _multiPart.getCount(); _indexPart++) {
                            MimeBodyPart _part = (MimeBodyPart) _multiPart.getBodyPart(_indexPart);
                            if (_part.getContentType().toLowerCase().contains("text/plain")) {
                                partText = _part;
                                break;
                            }
                        }
                    } else if (part.getContentType().toLowerCase().contains("text/plain")) {
                        partText = part;
                        break;
                    }
                }

                if (mailInfoBean.getText() != null && partText != null && partText.getContent() != null) {
                    partText.setText(mailInfoBean.getText() + partText.getContent().toString());
                }

                // 添付ファイルをmultiPartに追加
                if (multipart != null) {
                    for (int indexPart = 0; indexPart < multipart.getCount(); indexPart++) {
                        multiPartOrg.addBodyPart(multipart.getBodyPart(indexPart));
                    }
                }

            } else {
                // [シングルパートの場合]
                //  ①本文があれば、取り出してから加工
                //  ②本文がなければ、加工文を追加
                //  最後に添付ファイルがあればマルチパートに追加
                String text = "";
                if (mailInfoBean.getText() != null) {
                    text = mailInfoBean.getText() + mimeMessage.getContent().toString();
                }

                // 添付ファイルの有無で構成メールを分岐
                if (multipart != null) {
                    // [マルチパートを構成する]
                    // マルチパートを構成して本文パートを追加
                    MimeMultipart multipartNew = new MimeMultipart("mixed");
                    mimeMessage.setContent(multipartNew);
                    MimeBodyPart bodyPart = new MimeBodyPart();
                    bodyPart.setText(text);
                    multipartNew.addBodyPart(bodyPart);

                    // 添付ファイルをmultiPartに追加
                    for (int indexPart = 0; indexPart < multipart.getCount(); indexPart++) {
                        multipartNew.addBodyPart(multipart.getBodyPart(indexPart));
                    }
                } else {
                    // 本文を追加
                    mimeMessage.setText(text);
                }
            }
            mimeMessage.saveChanges();

//sendMailExec()を呼出すその他メソッド内でも同じ処理をしており冗長なため、共通化できる処理をsendMailExec()に移動 [248対応（簡易版）]
//            // [2017/03/07]V2.1エンコード変換対応
//            mailConvertCharset(mimeMessage);
//
//            InternetAddress[] ia;
//            try {
//                ia = mailInfoBean.getAddressEnvelopeTo();
//            } catch (Exception e) {
//                ia = InternetAddress.parseHeader(mailInfoBean.getHeaderEnvelopeTo(), false);
//            }
//
//            try {
//                sendMailExec(mimeMessage, mailInfoBean, true);    // メール送信実行[248対応（簡易版）]
//            } catch (SMTPSendFailedException e) {
//                LOG.error("### SMTPSendFailedException: SendInfoId:" + mailInfoBean.getSendInfoId()
//                        + " ReceiveInfoId:" + mailInfoBean.getReceiveInfoId()
//                        + " HeaderEnvelopeFrom:" + mailInfoBean.getHeaderEnvelopeFrom()
//                        + " HeaderEnvelopeTo:" + mailInfoBean.getHeaderEnvelopeTo());
//                throw e;
//            }

            // メール送信実行
            sendMailExec(mimeMessage, mailInfoBean, true);    // [248対応（簡易版）] 冗長処理を共通化のためsendMailExec内に移動。引数を追加。

        } catch (Exception ex) {
            LOG.error("sendMail error:", ex);
            throw new RuntimeException("sendSanitizedMail error:" + ex.getMessage());
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }

    /**
     * エラーメール変換処理 （最低限の情報のみ取得し、
     *
     * @param mailId
     * @param mailDate
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public MailInfoBean convertErrorMailInfoBean(String mailId, String mailDate) throws MessagingException, IOException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "mailId:" + mailId, "mailDate:" + mailDate));
//        String mailPath = CommonUtil.getSetting("maildir") + mailId + ".eml";
        File mailFile = new File(CommonUtil.getFolderMail(mailDate), mailId + ".eml");
        MailInfoBean mailInfoBean;
        try (InputStream inputStream = new FileInputStream(mailFile)) {
            Session session = Session.getInstance(new java.util.Properties());
            FssMimeMessage mimeMessage = new FssMimeMessage(session, inputStream);  // （注意）メールファイルのサイズが大きいとメモリ不足で異常終了するがCatchできない
            //ヘッダーのチェック&変換（AppleMail対応）
            checkMailHeaderWithoutContents(mimeMessage); //本文、マルチパートは対象外

            mimeMessage.setOriginalMessageId(mimeMessage.getMessageID());       // 元メールのMessageIDを退避
            mailInfoBean = new MailInfoBean();
            mailInfoBean.setSendInfoId(mailId);                                 // 送信情報ＩＤを設定

            mailInfoBean.setSentDate(mimeMessage.getSentDate());
            mailInfoBean.setMessageId(mimeMessage.getMessageID());
            mailInfoBean.setHeaderEnvelopeFrom(getMailHeader(mimeMessage, ENVELOPE_FROM));
            mailInfoBean.setHeaderEnvelopeTo(getMailHeader(mimeMessage, ENVELOPE_TO));
            mailInfoBean.setHeaderEnvelopeFromOrg(getMailHeader(mimeMessage, ENVELOPE_FROM_ORG));
            mailInfoBean.setHeaderEnvelopeToOrg(getMailHeader(mimeMessage, ENVELOPE_TO_ORG));

            // [Ver.2.1.6_227 エラー時にも件名をセットする
            mailInfoBean.setSubject(mimeMessage.getSubject());

            String noticeMessage = "";
            try{
                noticeMessage = itemHelper.findMailMessage("nortice_mail_analyze_error", Item.FUNC_COMMON).getValue();
            }catch(Exception e){
            }
            if (StringUtils.isEmpty(noticeMessage)) {                           // マスタ取得エラー時はこの値とする
                noticeMessage = "無害化処理が対応できていない部分がメールの中に含まれていました。\n"
                        + "メール本文及びファイルが削除された可能性があります。\n"
                        + "お手数ですが、メールの送信者に、異なるメール形式で送信していただくよう連絡をお願いいたします。";
            }
            mailInfoBean.setText(noticeMessage);
            mailInfoBean.setMailDate(mailDate); //[v2.2.1]
        }

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "mailId:" + mailId));
        return mailInfoBean;
    }

    /**
     * エラーメール情報の生成処理（emlが読込めない場合、最低限の情報のみセットする）
     * @param mailId
     * @param mailDate
     * @return
     * @throws MessagingException
     * @throws IOException 
     */
    public MailInfoBean createErrorMailInfoBean(String mailId, String mailDate) throws MessagingException, IOException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "mailId:" + mailId, "mailDate:" + mailDate));

        MailInfoBean mailInfoBean = new MailInfoBean();

        mailInfoBean.setSendInfoId(mailId);                                 // 送信情報ＩＤを設定

        String noticeMessage = "";
        try{
            noticeMessage = itemHelper.findMailMessage("nortice_mail_analyze_error", Item.FUNC_COMMON).getValue();
        }catch(Exception e){
        }
        if (StringUtils.isEmpty(noticeMessage)) {                           // マスタ取得エラー時はこの値とする
            noticeMessage = "無害化処理が対応できていない部分がメールの中に含まれていました。\n"
                    + "メール本文及びファイルが削除された可能性があります。\n"
                    + "お手数ですが、メールの送信者に、異なるメール形式で送信していただくよう連絡をお願いいたします。";
        }
        mailInfoBean.setText(noticeMessage);
        mailInfoBean.setMailDate(mailDate); //[v2.2.1]
        
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "mailId:" + mailId));
        return mailInfoBean;
    }

    public void sendErrorMail(MailInfoBean mailInfoBean, String emlPath)
            throws AddressException, FileNotFoundException, MessagingException, IOException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        try{
            Session session = this.connectMail();

            //MimeMessageを取得 [v2.1.11]共通化
            FssMimeMessage mimeMessage = getMimeMessageForSend(session, mailInfoBean, emlPath);
            //ヘッダーのチェック&変換（AppleMail対応）
            checkMailHeaderWithoutContents(mimeMessage);

            // メールヘッダー削除
            mimeMessage.removeHeader(ENVELOPE_TO);
            mimeMessage.removeHeader(ENVELOPE_FROM);

            // 元メールの本文（添付含む）を削除し、エラーメールに差し替える。
            mimeMessage.setText(mailInfoBean.getText());

            mimeMessage.saveChanges();

//sendMailExec()を呼出すその他メソッド内でも同じ処理をしており冗長なため、共通化できる処理をsendMailExec()に移動 [248対応（簡易版）]
//        // [2017/03/07]V2.1エンコード変換対応
//        mailConvertCharset(mimeMessage);
//
//        InternetAddress[] ia;
//        try {
//            ia = mailInfoBean.getAddressEnvelopeTo();
//        } catch (Exception e) {
//            ia = InternetAddress.parseHeader(mailInfoBean.getHeaderEnvelopeTo(), false);
//        }
//
//        try {
//            sendMailExec(mimeMessage, mailInfoBean, true);    // メール送信実行[248対応（簡易版）]
//        } catch (SMTPSendFailedException e) {
//            LOG.error("### SMTPSendFailedException: SendInfoId:" + mailInfoBean.getSendInfoId()
//                    + " ReceiveInfoId:" + mailInfoBean.getReceiveInfoId()
//                    + " HeaderEnvelopeFrom:" + mailInfoBean.getHeaderEnvelopeFrom()
//                    + " HeaderEnvelopeTo:" + mailInfoBean.getHeaderEnvelopeTo());
//            throw e;
//        }

            // メール送信実行
            sendMailExec(mimeMessage, mailInfoBean, true);    // [248対応（簡易版）] 冗長処理を共通化のためsendMailExec内に移動。引数を追加。
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }


    }

    /**
     * 添付ファイル付エラーメール送信
     * @param mailInfoBean
     * @param emlPath
     * @param receiveInfo
     * @param attachmentFile
     * @param attachmentFileName
     * @throws AddressException
     * @throws FileNotFoundException
     * @throws MessagingException
     * @throws IOException
     */
    public void sendErrorMailWithAttachment(MailInfoBean mailInfoBean, String emlPath, ReceiveInfo receiveInfo, File attachmentFile, String attachmentFileName)
            throws AddressException, FileNotFoundException, MessagingException, IOException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        try{
            Session session = this.connectMail();

            //添付ファイルのHashMapを生成
            Map<String, String> fileMap = new HashMap<>();
            fileMap.put(attachmentFileName, attachmentFile.getPath());   //Key=添付ファイル名、Value=実ファイルのフルパスでMapに追加

            //MimeMessageを取得 [v2.1.11]共通化
            FssMimeMessage mimeMessage = getMimeMessageForSend(session, mailInfoBean, emlPath);
            //ヘッダーのチェック&変換（AppleMail対応）
            checkMailHeaderWithoutContents(mimeMessage);

            // メールヘッダー削除
            mimeMessage.removeHeader(ENVELOPE_TO);
            mimeMessage.removeHeader(ENVELOPE_FROM);

            // 元メールの本文（添付含む）を削除し、エラーメールに差し替える。
            String text = "";
            if (mailInfoBean.getText() != null) {
                text = mailInfoBean.getText();
            }

            // [マルチパートを構成する]
            Multipart multipart = getMultiPartFromFileList(fileMap, true, receiveInfo);     //[248対応（簡易版）]
            // マルチパートを構成して本文パートを追加
            MimeMultipart multipartNew = new MimeMultipart("mixed");
            mimeMessage.setContent(multipartNew);
            MimeBodyPart bodyPart = new MimeBodyPart();
            bodyPart.setText(text);
            multipartNew.addBodyPart(bodyPart);

            // 添付ファイルをmultiPartに追加
            for (int indexPart = 0; indexPart < multipart.getCount(); indexPart++) {
                multipartNew.addBodyPart(multipart.getBodyPart(indexPart));
            }

            mimeMessage.saveChanges();

            // メール送信実行
            sendMailExec(mimeMessage, mailInfoBean, true);    // [248対応（簡易版）] 冗長処理を共通化のためsendMailExec内に移動。引数を追加。
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }

//    /**
//     * エラーメール送信
//     * @param mailid
//     */
//    public void sendMailError(String mailid) {
//        //[2017/02/23] メール処理がキャンセルされる場合に送るエラーメール
//        String charset = "UTF-8";
//
//        String noticeMessage = itemHelper.findMailMessage("nortice_mail_analyze_error", Item.FUNC_COMMON).getValue();
//        if (StringUtils.isEmpty(noticeMessage)) {                           // マスタ取得エラー時はこの値とする
//            noticeMessage = "無害化処理が対応できていない部分がメールの中に含まれていました。\n" +
//                            "メール本文及びファイルが削除された可能性があります。\n" +
//                            "お手数ですが、メールの送信者に、異なるメール形式で送信していただくよう連絡をお願いいたします。";
//        }
//
//        // 元メールを元にmimemessageへ変換
//        try {
//
//            String emlPath = CommonUtil.getSetting("maildir") + mailid + ".eml";
//
//            InputStream inputStream = new FileInputStream(emlPath);
//            FssMimeMessage mimeMessage = new FssMimeMessage(connectMail(), inputStream);
//
//            InternetAddress[] ia_to = InternetAddress.parse(Optional.ofNullable(getMailHeader(mimeMessage, ENVELOPE_TO)).orElse(""));
//
//            mimeMessage.setOriginalMessageId(mimeMessage.getMessageID());      // 元のMessageIdで上書き
//            mimeMessage.setText(noticeMessage, charset);
//            mimeMessage.saveChanges();
//
//            // [2017/03/07]V2.1エンコード変換対応
//            mailConvertCharset(mimeMessage);
//
//            sendMailExec(mimeMessage, ia_to);     // メール送信実行
//        } catch (Exception ex) {
//            LOG.error(ex.getMessage());
//            ex.printStackTrace();
//            throw new RuntimeException("sendMailSendFiles error:" + ex.getMessage());
//        }
//    }

    //[248対応（簡易版）]このメソッド呼出し側処理にあった冗長処理をこのメソッド内に移動するため引数追加（mailInfoBean,flgEncord)
    /**
     * メール送信実行
     * @param mimeMessage
     * @param mailInfoBean
     * @param flgEncord     //エンコード実施指定
     * @throws MessagingException
     */
    private void sendMailExec(MimeMessage mimeMessage, MailInfoBean mailInfoBean, boolean flgEncord) throws MessagingException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        try {
            //メール送信先取得とエンコード　//[248対応（簡易版）]呼出し側で行っていた処理を共通化
            InternetAddress[] envelopeTo;
            try {
                envelopeTo = mailInfoBean.getAddressEnvelopeTo();
            } catch (Exception e) {
                envelopeTo = InternetAddress.parseHeader(mailInfoBean.getHeaderEnvelopeTo(), false);
            }
            if (flgEncord){
                //団体区分判定用のメールアドレスを取得
                //※X-Envelope-Org-Toがある場合はX-Envelope-Org-Toを優先とする
                String toAddress = "";
                if(envelopeTo != null && envelopeTo.length > 0){
                    toAddress = envelopeTo[0].getAddress();
                }
                toAddress = MailManager.getAddressShort(    //X-Envelope-To-Orgは1つだけが前提
                    mailInfoBean.getHeaderEnvelopeToOrg(), toAddress);

                if(!StringUtils.isBlank(toAddress)) {
                    // [2017/03/07]V2.1エンコード変換対応
                    mailConvertCharset(mimeMessage, toAddress); //[248対応（簡易版）]メールアドレス引数追加（団体区分判別用）
                }
            }

            //メール送信実行
            Transport.send(mimeMessage, envelopeTo);                            // 送り先をEnvelopeToに設定
        } catch (SendFailedException el) {
            if (el.getNextException() instanceof SMTPAddressFailedException) {
                // 自ドメインで管理していないメールアドレスであった場合に、エラーが発生するのを防ぐ
                LOG.warn("sendMail warn:Unmanaged MailAddress. msg: " + el.getMessage());
            } else {
                // 上記以外はエラーとして処理する。

                //[248対応（簡易版）]SMTPSendFailedExceptionに対する例外処理も呼出し側で冗長だったのでここに移動
                if(el instanceof SMTPSendFailedException){
                    LOG.error("### SMTPSendFailedException: SendInfoId:" + mailInfoBean.getSendInfoId()
                            + " ReceiveInfoId:" + mailInfoBean.getReceiveInfoId()
                            + " HeaderEnvelopeFrom:" + mailInfoBean.getHeaderEnvelopeFrom()
                            + " HeaderEnvelopeTo:" + mailInfoBean.getHeaderEnvelopeTo());
                }
                throw el;
            }
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }

    // ワンタイムユーザ情報の登録
    private OnceUser insertOnceUser(String mailAddress, String target, String infoId, String password, Date expirationTime) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        String id = IdUtil.createUUID();
        OnceUser ou = new OnceUser();
        ou.setOnetimeId(id);
        ou.setPassword(password);
        ou.setTarget(target);
        ou.setMailId(infoId);
        ou.setMailAddress(mailAddress);
        ou.setExpirationTime(expirationTime);
        onceUserService.create(ou);
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        return ou;
    }

    //[248対応（簡易版）] 共通化、団体区分判定用に引数追加(receiveInfo)
    /**
     * fileListより添付ファイルMultiPartを作成する（エンコードあり）
     * @param receiveInfo
     * @return
     */
    private Multipart getMultiPartFromFileList(ReceiveInfo receiveInfo) {
        List<String> fileList = new ArrayList<>();
        for (ReceiveFile receiveFile : receiveInfo.getReceiveFiles()) {
            fileList.add(receiveFile.getFilePath());
        }
        LOG.debug("getMultiPartFromFileList()  ReceiveFiles Count:{}", fileList.size());

        if (fileList.isEmpty()) {
            return null;
        }

        //実ファイル名をそのまま添付ファイル名として使用するようにHashMapを生成
        Map<String, String> fileMap = new HashMap<>();
        for(String fpath: fileList){
            File fl = new File(fpath);
            fileMap.put(fl.getName(), fpath);   //Key=添付ファイル名、Value=実ファイルのフルパスでMapに追加
        }
        //[v2.2.3]
        //削除理由ファイルを添付ファイルに追加（実ファイル名と添付ファイル名が異なる）
        File deleteReasonFile = DeleteReasonFileLogic.getDeleteReasonFileRecv(receiveInfo); ///削除理由ファイルを取得
        if (deleteReasonFile.exists()) {
            //削除理由ファイル名（メール添付時用）を取得
            String fname = itemHelper.findWithDefault(Item.DELETEREASON_ATTACHMENTFILE, null, DeleteReasonFileLogic.DELETEREASON_FILENAME);
            //削除理由ファイルと同名ファイルが既に添付ファイルとして存在している場合、連番を付けて上書きされないようにする。
            {
                String fnameOrg = fname;
                String suffix = "";
                int index = fname.lastIndexOf(".");
                if(index > 0){
                    fnameOrg = fname.substring(0, index);
                    suffix = fname.substring(index);
                }
                int seqNo = 1;
                while(fileMap.containsKey(fname)){
                    fname = fnameOrg + "(" + (++seqNo) + ")" + suffix;
                }
            }
            fileMap.put(fname, deleteReasonFile.getPath());
        }

        return getMultiPartFromFileList(fileMap, true, receiveInfo);     //[248対応（簡易版）]
    }

    //
    //[248対応（簡易版）] 団体区分判定用に引数追加(receiveInfo)
    /**
     * 対象ファイルのリストより添付ファイルMultiPartを作成する
     * @param fileList HashMap<String,String>　Key:添付ファイル名として使用する名称,　Value:実ファイルパス
     * @param flgEncord
     * @param receiveInfo 受信情報
     * @return
     */
    private Multipart getMultiPartFromFileList(Map<String, String> fileList, boolean flgEncord, ReceiveInfo receiveInfo) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        try {
            if (fileList == null || fileList.isEmpty()) {
                LOG.debug("getMultiPartFromFileList() -- FileList is Null or Empty!");
                return null;
            }
            Multipart mmp = new MimeMultipart("mixed");
            for (Map.Entry<String, String> entry : fileList.entrySet()) {
                try {
                    BodyPart bodyPart = getBodyPartFromFile(entry.getKey(), entry.getValue(), flgEncord, receiveInfo);   //[248対応（簡易版）]
                    if (bodyPart != null) {
                        mmp.addBodyPart(bodyPart);
                    }
                } catch (Exception ex) {
                    LOG.warn("添付ファイルからMultiPart生成に失敗しました。[fname:" + entry.getValue() + ", encode:" + flgEncord + "]", ex);
                }
            }
            if (mmp != null && mmp.getCount() == 0) {
                mmp = null;
            }
            return mmp;
        }catch(Exception ex){
            LOG.error("getMultiPartFromFileList Error!", ex);
            return null;
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }

    //[248対応（簡易版）] 団体区分判定用に引数追加(receiveInfo)
    /**
     * 単一のファイルよりBodyPartを作成
     * @param fname ファイル名
     * @param path　実ファイル（フルパス）
     * @param flgEncord エンコード有無
     * @param receiveInfo 受信情報
     * @return  BodyParts
     */
    private BodyPart getBodyPartFromFile(String fname, String path, boolean flgEncord, ReceiveInfo receiveInfo) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        LOG.debug("-getBodyPartFromFile(fname:{}, path:{}, flgEncord:{}", fname, path, flgEncord);

        if (StringUtils.isEmpty(path)) {
            return null;
        }

        // [2017/08/23] エンコード要否の引数を追加

//        [2017/02/15]テキストエンコードをUTF-8指定とする。※将来的に切り替え可能とする想定。
//        final String charset = "iso-2022-jp";
//        final String encoding = "7bit";
        String charset = "UTF-8";
        // [2017/03/07]テキストエンコードを設定により切替可能とする。
        // ※設定されていない場合は不要な変換を行わず、必要な際はUTF-8のBase64を利用する。
        boolean convertFlg = false;
        String encoding = "B";

        try {
            // [2017/08/23] エンコード要否を引数で指定するように修正
            if (flgEncord) {
                Item _convert = itemHelper.find(Item.MAIL_CONVERT_FLG_INNER, Item.FUNC_COMMON, receiveInfo);        //[248対応（簡易版）]
                Item _charset = itemHelper.find(Item.MAIL_CONVERT_CHARSET_INNER, Item.FUNC_COMMON, receiveInfo);    //[248対応（簡易版）]
                Item _encoding = itemHelper.find(Item.MAIL_CONVERT_ENCODING_INNER, Item.FUNC_COMMON, receiveInfo);  //[248対応（簡易版）]
                if (_convert != null && _charset != null && _encoding != null
                        && _convert.getValue() != null && _charset.getValue() != null && _encoding.getValue() != null) {
                    MimeUtility.encodeText("エンコード確認", _charset.getValue(), _encoding.getValue());
                    convertFlg = _convert.getValue().equalsIgnoreCase("true");
                    if (convertFlg) {
                        charset = _charset.getValue();
                        encoding = _encoding.getValue();
                    }
                }
            }
        } catch (UnsupportedEncodingException | NoResultException e) {
            LOG.debug("設定値が取得出来ない or エンコードの指定に失敗するため、変換を行わない");
        }

        BodyPart body = null;
        try {
            File fp = new File(path);

            if (!fp.canRead() || !fp.isFile()) {
                LOG.warn("--getBodyPartFromFile() Invalid File! [path:{}]", path);
                return null;
            }
            body = new MimeBodyPart();
            FileDataSource fds = new FileDataSource(fp);
            body.setDataHandler(new DataHandler(fds));
            // [2016/12/26] ファイル名をエンコード（テスト用のためコメント）
//                body.setFileName(MimeUtility.encodeText(fp.getName()));
            // [2017/02/10] 様々なエンコード方法を検証
            // [2017/03/06] 任意エンコード指定
            //body.setFileName(MimeUtility.encodeText(fp.getName(), "iso-2022-jp", "B"));

            if (convertFlg) {
                //body.setFileName(MimeUtility.encodeText(fname, charset, encoding));
                // [2018/04/11]コンバート指定時はRFC2047で格納
                body.setHeader("Content-Disposition", MimeBodyPart.ATTACHMENT + ";\n filename=\"" + MimeUtility.encodeText(fname, charset, encoding).replace(" ", "\n ") + "\"");
            } else {
                body.setFileName(fname);
                body.setDisposition(MimeBodyPart.ATTACHMENT);
            }

            // Content-Typeを手動で設定(outlook対応としてnameに指定キャラセットのBase64変換文字列を格納)
            body.setHeader("Content-Type", "application/octet-stream;\n name=\"" + MimeUtility.encodeText(fname, charset, "B").replace(" ", "\n ") + "\"");

            //[2017/02/28] Content-Transfer-Encodingにbase64を設定(添付ファイルは必ずbase64とする)
            body.setHeader("Content-Transfer-Encoding", "base64");

//                // ヘッダーの設定状況を確認
//                Enumeration<Header> en = body.getAllHeaders();
//                System.out.println("!AllHeaders!");
//                en = body.getAllHeaders();
//                while(en.hasMoreElements()) {
//                    Header header = en.nextElement();
//                    System.out.println(header.getName());
//                    System.out.println(header.getValue());
//                }
        } catch (Exception ex) {
            LOG.error(" BodyPart Create Error! (fName:" + fname + ", path:" + path + ", flgEncord:" + flgEncord + ")", ex);
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
        return body;
    }

    /**
     * メールアドレスが庁内かどうかを判定
     *
     * @param mailAddress メールアドレス
     * @return 庁内判定結果
     */
    public boolean isMyDomain(String mailAddress) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        // 複数ドメインに対応した庁内判定
        try {
            Item item = itemHelper.find(Item.REGION_DOMAIN, null);
            List<String> domains = Arrays.asList(item.getValue().split(","));
            String domain = mailAddress.substring(mailAddress.indexOf("@") + 1);
            return domains.contains(domain);
        } catch (Exception e) {
            return false;
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }

//   /**
//     * メールアドレスが庁外(LG)かどうかを判定
//     * configからLGWanアドレスを取得する古方法
//     *
//     * @param mailAddress メールアドレス
//     * @return 庁外(LG)判定結果
//     */
//    public boolean isLgDomain_Old(String mailAddress) {
//        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
//        // 複数ドメインに対応した庁外(LG)判定
//        try {
//            Item item = itemHelper.find(Item.LGWAN_DOMAIN, null);
//            List<String> lgwanDomains = Arrays.asList(item.getValue().split(","));
//            String domain = mailAddress.substring(mailAddress.indexOf("@") + 1);
//            for (String lgwanDomain : lgwanDomains) {
//                StringBuilder sb = new StringBuilder();
//                sb.append("^");
//                sb.append(
//                        StringUtils.replace(StringUtils.replace(StringUtils.replace(StringUtils.replace(StringUtils.replace(StringUtils.replace(StringUtils.replace(StringUtils.replace(StringUtils.replace(StringUtils.replace(StringUtils.replace(StringUtils.replace(StringUtils.replace(StringUtils.replace(StringUtils.replace(
//                                lgwanDomain, "\\", "\\\\") // 置換後の'\'を変換しないよう、最初に行うこと！
//                                , "+", "\\+"), ".", "\\."), "?", "\\?"), "{", "\\{"), "}", "\\}"), "(", "\\("), ")", "\\)"), "[", "\\["), "]", "\\]"), "^", "\\^"), "$", "\\$"), "-", "\\-"), "|", "\\|"), "*", ".*") // 置換後の'.'を変換されないよう、最後に行うこと！
//                );
//                sb.append("$");
//                String s = sb.toString();
//                if (Pattern.compile(s).matcher(domain).find()) {
//                    return true;
//                }
//            }
//        } catch (PersistenceException e) {
//            // データが取得できない場合は必ずfalse
//            return false;
//        } catch (Exception e) {
//            return false;
//        } finally {
//            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
//        }
//    }

    /**
     * メールアドレスがLGWANドメインかどうかを判定
     *
     * @param mailAddress メールアドレス
     * @return LGWAN判定結果（True:LGWanドメイン）
     */
    public boolean isLgDomain(String mailAddress) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        try {
            //LgwanDomainマスタテーブルのデータを全て取得
            List<LgwanDomain> itemLst = lgwanDomainService.findAll();
            //Map<id,domain>に変換
            Map<String, String> masterMap = itemLst.stream().collect(
                    Collectors.toMap(s -> s.getId(), s -> Optional.ofNullable(s.getDomain()).orElse("")));
            //該当するものがあるか判定
            return isMatchDomain(mailAddress, masterMap);
        }catch(Exception e){
            String emsg = "#! [LgwanDomain] " + e.getMessage();
            LOG.error(emsg, e);
            throw new RuntimeException(emsg);
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }

    /**
     * メールアドレスが送信不可ドメインかどうかを判定
     *
     * @param mailAddress メールアドレス
     * @return 送信不可判定結果（True:送信不可）
     */
    public boolean isRejectDomain(String mailAddress) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        try {
            //RejectDomainマスタテーブルのデータを全て取得
            List<RejectDomain> itemLst = rejectDomainService.findAll();
            //Map<id, domain>に変換
            Map<String, String> masterMap = itemLst.stream().collect(
                    Collectors.toMap(s -> s.getId(), s -> Optional.ofNullable(s.getDomain()).orElse("")));
            //判定
            return isMatchDomain(mailAddress, masterMap);
        }catch(Exception e){
            String emsg = "#! [RejectDomain] " + e.getMessage();
            LOG.error(emsg, e);
            throw new RuntimeException(emsg);
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }

    /**
     * メールアドレスが対象ドメインリストに含まれるか判定する
     * @param mailAddress   //メールアドレス
     * @param masterMap     //対象ドメインMap<id,domain(正規表現）>
     * @return
     */
    public static boolean isMatchDomain(String mailAddress, Map<String, String> masterMap)
    {
        //メールアドレスからドメインを取得
        //※メールアドレスがEmptyでもチェック実行する（空文字メールアドレスを渡してマスタの全設定値に対して正規表現として問題無いかチェックするため）
        String mailDomain = "";
        if(!StringUtils.isBlank(mailAddress))
            mailDomain = mailAddress.substring(mailAddress.indexOf("@") + 1).toLowerCase();
        for (Map.Entry<String, String> entry : masterMap.entrySet()) {
            String id = entry.getKey();
            String domain = entry.getValue();
            if(StringUtils.isBlank(domain))
                continue;
            domain = domain.trim().toLowerCase();
            //----------------------------------------
            // 正規表現で判定する
            //----------------------------------------
            try{
                if (Pattern.compile(domain).matcher(mailDomain).find()) {
                    return true;    //ドメインに合致する
                }
            }catch(Exception e)
            {
                //マスタ登録ミスがわかるようにエラーメッセージをスローする
                throw new RuntimeException("Domain Master Error. [id:" + id + ", domain:" + domain  + "](" + e.getMessage() + ")", e);
            }
        }
        return false;
    }

    //[248対応（簡易版)]　団体区分判定用にenvelopeToAddress引数追加
    /**
     * メール文字コード変換処理
     *
     * @param mimeMessage
     * @param envelopeToAddress 送信先メールアドレス
     */
    private void mailConvertCharset(MimeMessage mimeMessage, String envelopeToAddress) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        try {
            String charset = null;
            Session session = Session.getInstance(new java.util.Properties());

            // 変換フラグ、変換キャラセットの取得
            Item _convert = itemHelper.find(Item.MAIL_CONVERT_FLG_INNER, Item.FUNC_COMMON, envelopeToAddress);      //[248対応（簡易版)]
            Item _charset = itemHelper.find(Item.MAIL_CONVERT_CHARSET_INNER, Item.FUNC_COMMON, envelopeToAddress);  //[248対応（簡易版)]
            if (_convert != null && _charset != null
                    && _convert.getValue() != null && _charset.getValue() != null) {
                if (_convert.getValue().equalsIgnoreCase("true")) {
                    FssMimeMessage _mimeMessage = new FssMimeMessage(session);
                    _mimeMessage.setText("キャラセット確認", _charset.getValue());
                    charset = _charset.getValue();
                    // 取得値、コンバート結果に問題が無ければ、ヘッダ、パートの変換
                    headerConvertCharset(mimeMessage, charset);
                    partConvertCharset(mimeMessage, charset, false);
                    mimeMessage.saveChanges();
                }
            }
        } catch (UnsupportedEncodingException | NoResultException e) {
            LOG.debug("設定値が取得出来ない or エンコードの指定に失敗するため、変換を行わない");
        } catch (Exception e) {
            e.printStackTrace();
            // 問題が発生した際は現状のまま送信を行う。
        }

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }

    /**
     * 特定ヘッダのキャラセット変更
     *
     * @param mimeMessage
     * @param charset
     * @throws MessagingException
     * @throws UnsupportedEncodingException
     */
    public void headerConvertCharset(MimeMessage mimeMessage, String charset) throws MessagingException, UnsupportedEncodingException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        // Toヘッダの変換
        try {
            InternetAddress[] ia_to = InternetAddress.parse(Optional.ofNullable(getMailHeader(mimeMessage, "To")).orElse(""));
            for (InternetAddress ia : ia_to) {
                ia.setPersonal(ia.getPersonal(), charset);
            }
            mimeMessage.setRecipients(Message.RecipientType.TO, ia_to);
        } catch (AddressException ae) {
            LOG.warn("'To' header will not be encoded. msg: " + ae.getMessage());
        }
        // Ccヘッダの変換
        try {
            InternetAddress[] ia_cc = InternetAddress.parse(Optional.ofNullable(getMailHeader(mimeMessage, "Cc")).orElse(""));
            for (InternetAddress ia : ia_cc) {
                ia.setPersonal(ia.getPersonal(), charset);
            }
            mimeMessage.setRecipients(Message.RecipientType.CC, ia_cc);
        } catch (AddressException ae) {
            LOG.warn("'Cc' header will not be encoded. msg: " + ae.getMessage());
        }
        // Fromヘッダの変換
        try {
            InternetAddress[] ia_from = InternetAddress.parse(Optional.ofNullable(getMailHeader(mimeMessage, "From")).orElse(""));
            for (InternetAddress ia : ia_from) {
                ia.setPersonal(ia.getPersonal(), charset);
                mimeMessage.setFrom(ia);
                break;
            }
        } catch (AddressException ae) {
            LOG.warn("'From' header will not be encoded. msg: " + ae.getMessage());
        }
        // Subjectヘッダの変換
        mimeMessage.setSubject(mimeMessage.getSubject(), charset);

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }

    /**
     * パート内を再起的にキャラセット変更 （添付ではないtext/plain,text/htmlのみ）
     *
     * @param part
     * @param charset
     * @param flgAppleMail
     * @return 変換有無
     * @throws MessagingException
     * @throws IOException
     */
    public boolean partConvertCharset(MimePart part, String charset, boolean flgAppleMail) throws MessagingException, IOException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        try{
            if (part == null) {
                return false;
            }
            boolean convFlg = false;
            Object object = part.getContent();
            if (object instanceof Multipart) {
                // マルチパートの場合
                // →パート毎に再帰呼び出し
                Multipart multipart = (Multipart) object;
                for (int indexPart = 0; indexPart < multipart.getCount(); indexPart++) {
                    BodyPart bodyPart = multipart.getBodyPart(indexPart);
                    if (bodyPart instanceof MimeBodyPart) {
                        MimeBodyPart mimeBodyPart = (MimeBodyPart) bodyPart;
                        convFlg = partConvertCharset(mimeBodyPart, charset, flgAppleMail);
                    }
                }
            } else{
                // 添付ファイルで無く、text/plainまたはtext/html であればエンコード変換
                if (part.getDisposition() != null && part.getDisposition().toLowerCase().contains(Part.ATTACHMENT)){
                    //添付ファイルの場合は対象外
                    return false;
                }
                boolean isHtml;
                if( part.getContentType().toLowerCase().contains("text/plain")){
                    isHtml = false;
                }else if( part.getContentType().toLowerCase().contains("text/html")){
                    isHtml = true;
                }else{
                    //text/plain、text/html以外は対象外
                    return false;
                }
                convFlg = true;
                if(flgAppleMail){
                    //AppleMail対応用の変換の場合、"charset=Wndows-31J"の場合のみ対象とする（前提：cp932⇒Windows-31J置換済み）
                    String contType = part.getContentType();
                    if(contType == null || !contType.toUpperCase().contains("WINDOWS-31J")){
                        convFlg = false;
                    }
                }
                if(convFlg){
                    if(isHtml){
                        //htmlの場合
                        //※part.setText(object.toString(), charset, "html")としてsubtypeに"html"を指定しても
                        //text/htmlとしてセットされないので、setContentを使う。
                        part.setContent(object.toString(), "text/html; charset=" + charset);
                    }else{
                        part.setText(object.toString(), charset);
                    }
                }
            }
            return convFlg;
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }

    // ============================
    // 以下、通知メール送信の呼び出し
    // ============================
    // 本文のみメール送信（大容量ファイル時の先行送信用）
    public void sendMailTextOnly(SendInfo sendInfo, InternetAddress envelopeTo, String funcId) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        // メール情報クラスの生成
        MailInfoBean mailInfoBean = getBaseNorticeInfo(funcId);
        mailInfoBean.setMessageId(sendInfo.getMessageId());
        mailInfoBean.setSendInfoId(sendInfo.getId());
//        mailInfoBean.setSubject(sendInfo.getSubject());
//        mailInfoBean.setHeaderFrom(sendInfo.getFromAddress());
        mailInfoBean.setHeaderTo(sendInfo.getToAddress());
        mailInfoBean.setHeaderCc(sendInfo.getCcAddress());
        mailInfoBean.setHeaderEnvelopeFrom(sendInfo.getSendMailAddress());
        mailInfoBean.setHeaderEnvelopeTo(envelopeTo.getAddress());
//        mailInfoBean.setText(sendInfo.getContent());
        mailInfoBean.setSentDate(sendInfo.getSendTime());                       // 元メールの送信時刻を設定

        this.editMailHeaderFromSendInfo(mailInfoBean, sendInfo, funcId);        // メール情報クラスのヘッダ情報調整

        // 生成したメール情報に文字挿入
        mailInfoBean.setToSubjectSubject(sendInfo.getSubject());                // [件名]件名
        mailInfoBean.setToTextSendText(sendInfo.getContent());                  // [本文]送信メール本文

        // 添付ファイル無しemlファイルのフルパスを取得
        File eml = new File(CommonUtil.getFolderSend(sendInfo, false, true),
                mailInfoBean.getSendInfoId() + ".eml");

        // 添付ファイルを設定せずにメール本文のみを送信
        // emlファイルの存在有無で新旧処理を分岐
        if (eml.exists()) {
            // 添付ファイル無しemlファイルを指定してメール送信
            sendMail(this.connectMail(), mailInfoBean, eml.getPath(), null);
        } else {
            // 旧方式のメール送信
            sendMail(this.connectMail(), mailInfoBean, null);
        }

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }

    // 本文のみメール送信（メール本体無害化）
    // ※メール本体無害化専用
    public void sendMailTextOnlyForMailSanitized(ReceiveInfo receiveInfo) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        // メール情報クラスの生成
        // ※sendSanitizedMailに必要な値のみ設定
        SendInfo sendInfo = receiveInfo.getSendInfo();
        MailInfoBean mailInfoBean = getMailInfoBase(receiveInfo);
        mailInfoBean.setText("");

        boolean result = false;
        try {
            // 添付ファイル無しemlファイルのフルパスを取得
//            String emlPath = CommonUtil.getSetting("receivedir") + receiveInfo.getId() + ".eml";
            File emlFile = new File(CommonUtil.getFolderReceive(receiveInfo, false, true),
                    receiveInfo.getId() + ".eml");
            // メールファイルが存在した場合のメール送信処理
            if(emlFile.exists()){
                sendSanitizedMail(this.connectMail(), mailInfoBean, emlFile.getPath(), null);
                result = true;
            }
        } catch (Exception ex) {
            result = false;
        }
        if(!result){
            // 旧方式のメール送信

            // メール本体無害化ＮＧメッセージ
            String sanitizeFiles = itemHelper.findMailMessage("sanitize_files", Item.FUNC_COMMON).getValue();
            String mailSanitizedMessage = itemHelper.findMailMessage("mail_sanitized_ng", Item.FUNC_COMMON).getValue();
            mailInfoBean.setText(sanitizeFiles + sendInfo.getContent());
            mailInfoBean.setToTextMailSanitizedMessage(mailSanitizedMessage);
            mailInfoBean.setToTextDeletedFileNameList(new ArrayList<>(), "", "", "", "", "");
            mailInfoBean.setToTextSanitizedFileNameList(new ArrayList<>(), "", "", "", "", "");
            mailInfoBean.setToTextErroredFileNameList(new ArrayList<>(), "", "", "", "", "");
            mailInfoBean.setToTextDecryptedFileNameList(new ArrayList<>(), "", "", "", "", "");
            mailInfoBean.setToTextNoGoodFileNameList(new ArrayList<>(), "", "", "", "", "");
            mailInfoBean.setToTextZipCharsetUnconvertedFileNameList(new ArrayList<>(), "", "", "", "", "");

            // 添付ファイル無しemlファイルのフルパスを取得
            File eml = new File(CommonUtil.getFolderSend(sendInfo, false, true),
                    mailInfoBean.getSendInfoId() + ".eml");

            // 添付ファイルを設定せずにメール本文のみを送信
            // emlファイルの存在有無で新旧処理を分岐
            if (eml.exists()) {
                // 添付ファイル無しemlファイルを指定してメール送信
                sendMail(this.connectMail(), mailInfoBean, eml.getPath(), null);
            } else {
                // 旧方式のメール送信
                sendMail(this.connectMail(), mailInfoBean, null);
            }
        }

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }

    // 本文(+ダウンロードURL付加)のメール送信
    // ※無害化後のメールサイズチェック超過
    public void sendMailTextWithURL(ReceiveInfo receiveInfo) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        String funcId = "sendInner";

        // 送信情報の取得
        SendInfo sendInfo = receiveInfo.getSendInfo();

        // メール情報クラスの生成
        // ※sendSanitizedMailに必要な値のみ設定
        MailInfoBean mailInfoBean = getMailInfoBase(receiveInfo);

        // 無害化お知らせヘッダーの出力（何れかのリストが有効なら出力）
        String sanitizeFiles = itemHelper.findMailMessage("sanitize_files", funcId + "SanitizedLargeURL").getValue();

        // 無害化お知らせをメール本文の先頭に出力
        mailInfoBean.setText(sanitizeFiles);
        mailInfoBean.setToTextExpirationTime(sendInfo.getExpirationTime());     // [本文]期限
        // ワンタイムパスワード生成、ユーザ情報の登録とメール情報へのセット [v2.1.11]共通化
        setMailInfoOnetimePassword(mailInfoBean, funcId, true, true, "", true,
                receiveInfo.getReceiveMailAddress(), "receiveHistoryDetail",
                receiveInfo.getId(), sendInfo.getExpirationTime());

        Session session = this.connectMail();   //接続
        String content = mailInfoBean.getText();    //無害化お知らせメッセージ(ダウンロードURL)
        boolean result = false;

        if (receiveInfo.isMailSanitizedFlg()){
            //メール本体無害化済みの場合の処理（無害化済みメールを利用する）
            try {
                // 添付ファイル無しemlファイルのフルパスを取得
//                String emlPath = CommonUtil.getSetting("receivedir") + receiveInfo.getId() + ".eml";
                File emlFile = new File(CommonUtil.getFolderReceive(receiveInfo, false, true),
                        receiveInfo.getId() + ".eml");
                // メールファイルが存在した場合のメール送信処理
                if(emlFile.exists()){
                    sendSanitizedMail(session, mailInfoBean, emlFile.getPath(), null);
                    result = true;
                }
            } catch (Exception ex) {
                result = false;
            }
        }
        if(!result){
            //メール無害化非対応の場合のメール送信
            MailInfoBean mailInfoBean2 = getMailInfoBase(receiveInfo);
            //無害化お知らせメッセージ(ダウンロードURL)をメール本文の先頭に出力
            mailInfoBean2.setText(content + sendInfo.getContent());
            //メール送信
            sendMail(session, mailInfoBean2, null);
        }

        // パスワード通知を送信
        if (sendInfo.isPassAuto() || sendInfo.isPassNotice()) {
            //[v2.1.11]共通化 ファイルリストは出力しない
            sendMailPasswordNotice(session, sendInfo, receiveInfo, funcId, "SanitizedLargePassword",
                    mailInfoBean.getPasswordInt(), mailInfoBean.getPasswordLgw(), false);
        }
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }

    //[248対応（簡易版）] sendInfo単位ではなく宛先単位でのメール送信に変更し、envelopeTo引数を追加
    /**
     * 無害化無しファイルのメール転送（送信ファイル）
     * @param receiveInfo
     */
    public void sendMailSendFiles(ReceiveInfo receiveInfo ) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        MailInfoBean mailInfoBean = getMailInfoBase(receiveInfo);

        // 添付ファイル無しemlファイルのフルパスを取得
        File eml = new File(CommonUtil.getFolderSend(receiveInfo.getSendInfo(), false, true),
                mailInfoBean.getSendInfoId() + ".eml");
        // emlファイルの存在有無で新旧処理を分岐
        if (eml.exists()) {
            // 添付ファイル無しemlファイルを指定してメール送信

            //添付ファイルなしのケースしかこのメソッドは呼ばれないので添付ファイルの処理部分をコメント。[248対応（簡易版）]
//            List<String> fileList = new ArrayList<>();
//            for (SendFile sendFile : sendInfo.getSendFiles()) {
//                fileList.add(sendFile.getFilePath());
//            }
//            sendMail(this.connectMail(), mailInfoBean, emlPath, this.getMultiPartFromFileList(fileList, false));

            sendMail(this.connectMail(), mailInfoBean, eml.getPath(), null);
        } else {
            // 旧方式のメール送信
//            sendMailSendFiles(this.connectMail(), mailInfoBean, sendInfo);    //無駄な経由呼出しのため変更
            sendMail(this.connectMail(), mailInfoBean, null);
        }

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }

//[248対応（簡易版）]不使用のためコメント
//    // 無害化無しファイルのメール転送（送信ファイル）
//    public void sendMailSendFiles(MailInfoBean mailInfoBean, SendInfo sendInfo) {
//        sendMailSendFiles(this.connectMail(), mailInfoBean, sendInfo);
//    }
//
//    // 無害化無しファイルのメール転送（送信ファイル）
//    public void sendMailSendFiles(Session session, MailInfoBean mailInfoBean, SendInfo sendInfo) {
//        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
//
//        //添付ファイルなしのケースしかこのメソッドは呼ばれないので添付ファイルの処理部分をコメント。[248対応（簡易版）]
//        List<String> fileList = new ArrayList<>();
//        for (SendFile sendFile : sendInfo.getSendFiles()) {
//            fileList.add(sendFile.getFilePath());
//        }
//        sendMail(session, mailInfoBean, this.getMultiPartFromFileList(fileList));
//        sendMail(session, mailInfoBean, null);
//
//        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
//    }

    // 無害化済みファイルのメール転送（受信ファイル）
    // ※メール本体無害化専用
    public void sendMailReceiveFilesForMailSanitized(ReceiveInfo receiveInfo) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        // メール情報クラスの生成
        // ※sendSanitizedMailに必要な値のみ設定
        MailInfoBean mailInfoBean = getMailInfoBase(receiveInfo);
        mailInfoBean.setText("");

        //mailInfoBeanの編集（ファイル処理状況の反映）　//処理の共通化[v2.1.11]
        editMailInfoBeanByFileStatus(receiveInfo, mailInfoBean, false);

        // 添付ファイル無しemlファイルのフルパスを取得
//        String emlPath = CommonUtil.getSetting("receivedir") + receiveInfo.getId() + ".eml";
        File emlFile = new File(CommonUtil.getFolderReceive(receiveInfo, false, true),
                receiveInfo.getId() + ".eml");       
        try {
            sendSanitizedMail(this.connectMail(), mailInfoBean, emlFile.getPath(), this.getMultiPartFromFileList(receiveInfo)); //[248対応（簡易版）]
        } catch (Exception ex) {
            // 旧方式のメール送信
            sendMailReceiveFiles(receiveInfo);
        }

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }

    // [v2.1.11]共通化 無駄な中継メソッド削除
    /**
     * 無害化済みファイルのメール転送（受信ファイル）
     * @param receiveInfo
     */
    public void sendMailReceiveFiles(ReceiveInfo receiveInfo) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        //ベースメール情報生成 [v2.1.11]共通化
        MailInfoBean mailInfoBean = getMailInfoBase(receiveInfo);

        //mailInfoBeanの編集（ファイル処理状況の反映）　//処理の共通化[v2.1.11]
        editMailInfoBeanByFileStatus(receiveInfo, mailInfoBean, receiveInfo.isMailSanitizeFlg());

        // 添付ファイルをマルチパートで取得
        Multipart multiPart = this.getMultiPartFromFileList(receiveInfo);     //[248対応（簡易版）]

        // 添付ファイル無しemlファイルのフルパスを取得
        File eml = new File(CommonUtil.getFolderSend(receiveInfo.getSendInfo(), false, true),
                mailInfoBean.getSendInfoId() + ".eml");
        // emlファイルの存在有無で新旧処理を分岐
        if (eml.exists()) {
            // 添付ファイル無しemlファイルを指定してメール送信
            sendMail(this.connectMail(), mailInfoBean, eml.getPath(), multiPart);
        } else {
            // 旧方式のメール送信
            sendMail(this.connectMail(), mailInfoBean, multiPart);
        }

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }

    /**
     * 無害化済みメールを使わずメール送信する場合の基本的なメール情報を生成
     * @param receiveInfo
     * @return
     */
    private MailInfoBean getMailInfoBase(ReceiveInfo receiveInfo)
    {
        SendInfo sendInfo = receiveInfo.getSendInfo();
        MailInfoBean mailInfoBean = new MailInfoBean();
        mailInfoBean.setMessageId(sendInfo.getMessageId());
        mailInfoBean.setHeaderEnvelopeTo(receiveInfo.getReceiveMailAddress());
        mailInfoBean.setHeaderEnvelopeFrom(sendInfo.getSendMailAddress());
        mailInfoBean.setSendInfoId(sendInfo.getId());
        mailInfoBean.setHeaderFrom(sendInfo.getFromAddress());
        mailInfoBean.setHeaderTo(sendInfo.getToAddress());
        mailInfoBean.setHeaderCc(sendInfo.getCcAddress());
        mailInfoBean.setSentDate(sendInfo.getSendTime());                       // 元メールの送信時刻を設定
        mailInfoBean.setSubject(sendInfo.getSubject());
        mailInfoBean.setReceiveInfoId(receiveInfo.getId());
        mailInfoBean.setText(sendInfo.getContent());

        editMailHeaderFromReceiveInfo(mailInfoBean, receiveInfo);      // メール情報クラスのヘッダ情報調整

        return mailInfoBean;
    }

    //処理の共通化[v2.1.11]
    /**
     *
     * @param receiveInfo
     * @param mailInfoBean
     * @param mailSanitizeNgFlg
     * @return
     */
    private int editMailInfoBeanByFileStatus(ReceiveInfo receiveInfo,
        MailInfoBean mailInfoBean,
        boolean mailSanitizeNgFlg
            )
    {
        // 無害化されたファイル一覧の作成
        // [2017/02/23] パスワード解除・再付与関連ファイル一覧の作成
        List<String> deletedFileNameList = new ArrayList<>();
        List<String> sanitizedFileNameList = new ArrayList<>();
        List<String> erroredFileNameList = new ArrayList<>();
        List<String> blockedArchiveFileNameList = new ArrayList<>();    //一部のファイルがブロックされたアーカイブファイルリスト    [v2.2.3]
        List<String> decryptedFileNameList = new ArrayList<>();
        List<String> noGoodFileNameList = new ArrayList<>();
        List<String> zipCharsetUnconvertedFileNameList = new ArrayList<>();

        for (ReceiveFile receiveFile : receiveInfo.getReceiveFiles()) {
            if (receiveFile.isSanitizeFlg() && receiveFile.getFilePath().length() <= 0) {
                deletedFileNameList.add(receiveFile.getFileName());
            } else if (receiveFile.isSanitizeFlg()) {
                //アーカイブファイル内の一部ファイルがブロックされている場合
                if (receiveFile.getFileErrCode() == FileSanitizeResultKbn.ARCHIVECHILD_ERROR.value) {
                    blockedArchiveFileNameList.add(receiveFile.getFileName());
                }
                //正常に無害化完了
                else {
                    sanitizedFileNameList.add(receiveFile.getFileName());
                }
            } else {
                erroredFileNameList.add(receiveFile.getFileName());
            }

            // パスワード解除・再付与関連の判定
            DecryptFile decryptFileParent = null;                               // 親ファイル
            List<DecryptFile> decryptFiles = new ArrayList<>();                   // 対象の子ファイル
            for (DecryptFile decryptFile : receiveFile.getDecryptFiles()) {
                if (StringUtils.isEmpty(decryptFile.getParentId())) {           // 親ファイル
                    // 親ファイルはパスワード再付与判定せずに取得
                    decryptFileParent = decryptFile;
                } else // 子ファイル
                 if (decryptFile.isDecryptFlg()) {                           // パスワード解除済みのみ
                        decryptFiles.add(decryptFile);
                    }
            }
            if (decryptFileParent != null) {                                    // 親ファイル必須
                // 親ファイルのチェック
                if (decryptFileParent.isDecryptFlg()) {                         // パスワード解除済み
                    if (decryptFileParent.isEncryptFlg()) {                     // パスワード再付与
                        if (!decryptFileParent.isEncryptedFlg()) {              // パスワード再付与失敗
                            noGoodFileNameList.add(decryptFileParent.getFileName());
                        }
                    } else {
                        decryptedFileNameList.add(decryptFileParent.getFileName());
                    }
                }
                // 子ファイルのチェック
                String childFormat = itemHelper.findMailMessage("filelist_childname_format", Item.FUNC_COMMON).getValue();
                if (StringUtils.isEmpty(childFormat)) {
                    childFormat = "$cfname;($pfname;)";   // マスタ取得エラー時はこの設定とする
                }
                String fileName = childFormat.replace("$pfname;", decryptFileParent.getFileName());
                for (DecryptFile decryptFile : decryptFiles) {
                    if (decryptFile.isEncryptFlg()) {                           // パスワード再付与
                        if (!decryptFile.isEncryptedFlg()) {                    // パスワード再付与失敗
                            noGoodFileNameList.add(fileName.replace("$cfname;", decryptFile.getFileName()));
                        }
                    } else {
                        decryptedFileNameList.add(fileName.replace("$cfname;", decryptFile.getFileName()));
                    }
                }
            }

            // 文字コード変換確認（変換対象であり、変換されていないもの＝変換できなかったもの）
            if (receiveFile.isZipCharsetConvert() && !receiveFile.isZipCharsetConverted()) {
                zipCharsetUnconvertedFileNameList.add(receiveFile.getFileName());
            }
        }

        // 無害化お知らせヘッダーの出力（何れかのリストが有効なら出力）
        String sanitizeFiles = itemHelper.findMailMessage("sanitize_files", Item.FUNC_COMMON).getValue();
        // 各一覧の出力可否フラグ
        boolean deletedFlg = MailInfoBean.isDeletedFileNameList(sanitizeFiles, deletedFileNameList);
        boolean sanitizedFlg = MailInfoBean.isSanitizedFileNameList(sanitizeFiles, sanitizedFileNameList);
        boolean erroredFlg = MailInfoBean.isErroredFileNameList(sanitizeFiles, erroredFileNameList);
        boolean blockedArchiveFlg = MailInfoBean.isBlockedArchiveFileNameList(sanitizeFiles, blockedArchiveFileNameList);
        boolean decryptedFlg = MailInfoBean.isDecryptedFileNameList(sanitizeFiles, decryptedFileNameList);
        boolean noGoodFlg = MailInfoBean.isNoGoodFileNameList(sanitizeFiles, noGoodFileNameList);
        boolean zipCharsetUnconvertedFlg = MailInfoBean.isZipCharsetUnconvertedFileNameList(sanitizeFiles, zipCharsetUnconvertedFileNameList);

        if (deletedFlg || sanitizedFlg || erroredFlg || blockedArchiveFlg || decryptedFlg || noGoodFlg || zipCharsetUnconvertedFlg || mailSanitizeNgFlg) {
            String prefix = itemHelper.findMailMessage("filelist_prefix", Item.FUNC_COMMON).getValue();
            String indent = itemHelper.findMailMessage("filelist_indent", Item.FUNC_COMMON).getValue();
            String separator = itemHelper.findMailMessage("filelist_separator", Item.FUNC_COMMON).getValue();
            // 無害化お知らせをメール本文の先頭に出力
            mailInfoBean.setText(sanitizeFiles + mailInfoBean.getText());

            // メール本体無害化ＮＧメッセージ
            // ※MailSanitizedFlgの結果は関係なく、MailSanitizeの対象であればＮＧメッセージを出力する
//            if (receiveInfo.isMailSanitizeFlg()) {
            if(mailSanitizeNgFlg) {     //処理の共通化でisMailSanitizeFlgではなく引数で判定するよう修正[v2.1.11]
                String mailSanitizedMessage = itemHelper.findMailMessage("mail_sanitized_ng", Item.FUNC_COMMON).getValue();
                mailInfoBean.setToTextMailSanitizedMessage(mailSanitizedMessage);
            } else {
                // [2017/03/11] 「メールが無害化されました」メッセージを出さないように。
//                String mailSanitizedMessage = itemHelper.findMailMessage("mail_sanitized_ok", Item.FUNC_COMMON).getValue();
                mailInfoBean.setToTextMailSanitizedMessage("");
            }
            // 削除ファイルリストの出力
            if (deletedFlg) {
                String header = itemHelper.findMailMessage("filelist_header_deleted", Item.FUNC_COMMON).getValue();
                String footer = itemHelper.findMailMessage("filelist_footer_deleted", Item.FUNC_COMMON).getValue();
                mailInfoBean.setToTextDeletedFileNameList(deletedFileNameList, prefix, indent, separator, header, footer);
            } else {
                mailInfoBean.setToTextDeletedFileNameList(new ArrayList<>(), "", "", "", "", "");
            }
            // 無害化ファイルリストの出力
            if (sanitizedFlg) {
                String header = itemHelper.findMailMessage("filelist_header_sanitized", Item.FUNC_COMMON).getValue();
                String footer = itemHelper.findMailMessage("filelist_footer_sanitized", Item.FUNC_COMMON).getValue();
                mailInfoBean.setToTextSanitizedFileNameList(sanitizedFileNameList, prefix, indent, separator, header, footer);
            } else {
                mailInfoBean.setToTextSanitizedFileNameList(new ArrayList<>(), "", "", "", "", "");
            }
            // 無害化エラーファイルリストの出力
            if (erroredFlg) {
                String header = itemHelper.findMailMessage("filelist_header_errored", Item.FUNC_COMMON).getValue();
                String footer = itemHelper.findMailMessage("filelist_footer_errored", Item.FUNC_COMMON).getValue();
                mailInfoBean.setToTextErroredFileNameList(erroredFileNameList, prefix, indent, separator, header, footer);
            } else {
                mailInfoBean.setToTextErroredFileNameList(new ArrayList<>(), "", "", "", "", "");
            }
            // [v2.2.3]
            // 一部のファイルがブロックされたアーカイブファイルリストの出力
            if (blockedArchiveFlg) {
                String header = itemHelper.findMailMessage("filelist_header_zip_block_childfile", Item.FUNC_COMMON).getValue();
                String footer = itemHelper.findMailMessage("filelist_footer_zip_block_childfile", Item.FUNC_COMMON).getValue();
                mailInfoBean.setToTextBlockedArchiveFileNameList(blockedArchiveFileNameList, prefix, indent, separator, header, footer);
            } else {
                mailInfoBean.setToTextBlockedArchiveFileNameList(new ArrayList<>(), "", "", "", "", "");
            }
            // パスワード解除済みファイルリストの出力
            if (decryptedFlg) {
                String header = itemHelper.findMailMessage("filelist_header_decrypted", Item.FUNC_COMMON).getValue();
                String footer = itemHelper.findMailMessage("filelist_footer_decrypted", Item.FUNC_COMMON).getValue();
                mailInfoBean.setToTextDecryptedFileNameList(decryptedFileNameList, prefix, indent, separator, header, footer);
            } else {
                mailInfoBean.setToTextDecryptedFileNameList(new ArrayList<>(), "", "", "", "", "");
            }
            // パスワード再付与失敗ファイルリストの出力
            if (noGoodFlg) {
                String header = itemHelper.findMailMessage("filelist_header_encrypt_ng", Item.FUNC_COMMON).getValue();
                String footer = itemHelper.findMailMessage("filelist_footer_encrypt_ng", Item.FUNC_COMMON).getValue();
                mailInfoBean.setToTextNoGoodFileNameList(noGoodFileNameList, prefix, indent, separator, header, footer);
            } else {
                mailInfoBean.setToTextNoGoodFileNameList(new ArrayList<>(), "", "", "", "", "");
            }
            // zip内文字コード変換不可ファイルリストの出力
            if (zipCharsetUnconvertedFlg) {
                String header = itemHelper.findMailMessage("filelist_header_zip_charset_unconverted", Item.FUNC_COMMON).getValue();
                String footer = itemHelper.findMailMessage("filelist_footer_zip_charset_unconverted", Item.FUNC_COMMON).getValue();
                mailInfoBean.setToTextZipCharsetUnconvertedFileNameList(zipCharsetUnconvertedFileNameList, prefix, indent, separator, header, footer);
            } else {
                mailInfoBean.setToTextZipCharsetUnconvertedFileNameList(new ArrayList<>(), "", "", "", "", "");
            }
        }
        return 0;
    }


    /**
     * メール無害化のパスワード解除URL通知
     *
     * @param sendInfo 送信情報
     * @param addressTo 送信先アドレス
     */
    public void sendMailPasswordInput(SendInfo sendInfo, InternetAddress addressTo) {
        sendMailPasswordInput(sendInfo, addressTo, "password", "passwordUnlock", sendInfo.getId());
    }

    /**
     * ファイル交換のパスワード解除URL通知
     *
     * @param receiveInfo 受信情報
     * @param addressTo 送信先アドレス
     */
    public void sendMailPasswordInput(ReceiveInfo receiveInfo, InternetAddress addressTo) {
        SendInfo sendInfo = receiveInfo.getSendInfo();
        sendMailPasswordInput(sendInfo, addressTo, "sendTransferPassword", "sendTransferPasswordUnlock", receiveInfo.getId());
    }

    /**
     * パスワード解除ＵＲＬ通知
     *
     * @param sendInfo 送信情報
     * @param addressTo 送信先アドレス
     * @param funcId 機能ID
     * @param target ターゲット機能
     * @param infoId 識別ID
     */
    private void sendMailPasswordInput(SendInfo sendInfo, InternetAddress addressTo, String funcId, String target, String infoId) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        // メール情報クラスの生成
        MailInfoBean mailInfoBean = getBaseNorticeInfo(funcId);

//        mailInfoBean.setHeaderTo(sendInfo.getToAddress());
        mailInfoBean.setHeaderTo(addressTo.toString());     // ヘッダーToには宛先アドレスを設定
        mailInfoBean.setHeaderEnvelopeTo(addressTo.getAddress());

        this.editMailHeaderFromSendInfo(mailInfoBean, sendInfo, funcId);                                 // メール情報クラスのヘッダ情報調整

        // 生成したメール情報に文字挿入
        mailInfoBean.setToSubjectAddressFrom(getAddressShort(sendInfo.getSendMailAddress()));                           // [件名]送信元アドレス
        mailInfoBean.setToTextAddressFrom(getAddressLongUnicode(sendInfo.getSendMailAddress(), sendInfo.getSendUserName()));   // [本文]送信元アドレス（フル）
        mailInfoBean.setToTextExpirationTime(sendInfo.getExpirationTime());     // [本文]期限
        //ワンタイムユーザ情報の登録とメール情報へのセット（パスワード生成はしない）
        setMailInfoOnetimePassword(mailInfoBean, funcId, false, false, "", true,
                addressTo.getAddress(), target, infoId, sendInfo.getExpirationTime());
        mailInfoBean.setToTextSubject(sendInfo.getSubject());                   // [本文]送信メール件名
        mailInfoBean.setToTextSendText(sendInfo.getContent());                  // [本文]送信メール本文
        // メール送信
        mailInfoBean.setSentDate(new Date());                                   // 送信時刻にシステム時刻を設定
        sendMail(connectMail(), mailInfoBean, null);

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }

    // ダウンロードＵＲＬ・パスワード通知
    public void sendMailDownLoad(ReceiveInfo receiveInfo) {
        if(receiveInfo.getSendInfo() == null){
            sendMailDownLoad(receiveInfo, false);
            return;
        }
        sendMailDownLoad(receiveInfo, receiveInfo.getSendInfo().isLargeFlg());
    }

    // [248対応（簡易版）] メールサイズ超過を引数に追加
    /**
     * ダウンロードＵＲＬ・パスワード通知
     * @param receiveInfo
     * @param isLargeFlg
     */
    public void sendMailDownLoad(ReceiveInfo receiveInfo, boolean isLargeFlg) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        // メール送信以外かつ通知省略フラグが通知無(true)の場合、処理終了
        if (!receiveInfo.isAttachmentMailFlg() && receiveInfo.getSendInfo().isNoticeOmitFlg()) {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
            return;
        }

        // 送信先が庁内か庁外かを取得
        boolean isMyDomain = isMyDomain(receiveInfo.getReceiveMailAddress());
        // メールアドレスが庁外(LG)かどうかを判定
        boolean isLgDomain = isLgDomain(receiveInfo.getReceiveMailAddress());
        // 送信先が庁内もしくは庁外(LG)か、庁外かを取得
        boolean isLgwanAddress = (isMyDomain || isLgDomain);

        // [2016/12/29] funcIdの切り替え
        String funcId;
        if (isLargeFlg) {
            funcId = "largeMail";
            isLgwanAddress = true;      // [2017/02/14] 大容量の場合は必ずLGWAN側にも通知するため、庁内判定に強制設定する
        } else if (!isMyDomain) {
            funcId = isLgDomain ? "sendOuterLGWAN" : "sendOuter";
        } else {
            funcId = "sendInner";
        }

        // 送信情報の取得
        SendInfo sendInfo = receiveInfo.getSendInfo();

        // メール情報クラスの生成
        MailInfoBean mailInfoBean = getBaseNorticeInfo(funcId + "URL");
        // [2017/12/01]宛先メールアドレスには個別のアドレスのみ記載
        mailInfoBean.setHeaderTo(getAddressLongString(
                receiveInfo.getReceiveMailAddress(), receiveInfo.getReceiveUserName(),receiveInfo.getReceiveMailAddress()));
        mailInfoBean.setHeaderEnvelopeTo(receiveInfo.getReceiveMailAddress());

        this.editMailHeaderFromReceiveInfo(mailInfoBean, receiveInfo);                              // メール情報クラスのヘッダ情報調整
        mailInfoBean.setHeaderEnvelopeFrom(
                getAddressShort(mailInfoBean.getHeaderFrom(),mailInfoBean.getHeaderFrom()));

        // 生成したメール情報に文字挿入
        mailInfoBean.setToSubjectAddressFrom(getAddressShort(sendInfo.getSendMailAddress()));                           // [件名]送信元アドレス
        mailInfoBean.setToTextAddressFrom(getAddressLongUnicode(sendInfo.getSendMailAddress(),sendInfo.getSendUserName()));    // [本文]送信元アドレス（フル）
        mailInfoBean.setToTextExpirationTime(sendInfo.getExpirationTime());     // [本文]期限
        //ワンタイムパスワード、ユーザ情報の登録とメール情報へのセット
        setMailInfoOnetimePassword(mailInfoBean, funcId, true, sendInfo.isPassAuto(), sendInfo.getPassWord(), isLgwanAddress,
                receiveInfo.getReceiveMailAddress(), "receiveHistoryDetail", receiveInfo.getId(), sendInfo.getExpirationTime());
        mailInfoBean.setToTextSendText(sendInfo.getContent());                  // [本文]送信メール本文
        setMailInfoFileList(receiveInfo, mailInfoBean, funcId);                 // [本文]ファイル一覧

        // メールサーバ接続
        Session session = connectMail();

        // メール送信
        mailInfoBean.setSentDate(new Date());                                   // 送信時刻にシステム時刻を設定
        sendMail(session, mailInfoBean, null);

        // パスワード通知を送信
        if (sendInfo.isPassAuto() || sendInfo.isPassNotice()) {
            //[v2.1.11]共通化 ファイルリストは出力する
            sendMailPasswordNotice(session, sendInfo, receiveInfo, funcId, "Password",
                    mailInfoBean.getPasswordInt(), mailInfoBean.getPasswordLgw(), true);
        }

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }

    /**
     * パスワード通知メール送信
     * @param session
     * @param sendInfo
     * @param receiveInfo
     * @param funcId
     * @param noticeInfoId
     * @param passwordInt
     * @param passwordLgw
     * @param flgFileListOutput
     */
    public void sendMailPasswordNotice(Session session,
            SendInfo sendInfo, ReceiveInfo receiveInfo,
            String funcId, String noticeInfoId,
            String passwordInt, String passwordLgw, boolean flgFileListOutput)
    {
        try{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

            // パスワード通知を送信
            // メール情報クラスの生成
//            MailInfoBean mailInfoBean = getBaseNorticeInfo(funcId + "Password");
            MailInfoBean mailInfoBean = getBaseNorticeInfo(funcId + noticeInfoId);
            // [2017/12/01]宛先メールアドレスには個別のアドレスのみ記載
            mailInfoBean.setHeaderTo(getAddressLongString(
                    receiveInfo.getReceiveMailAddress(), receiveInfo.getReceiveUserName(),receiveInfo.getReceiveMailAddress()));
            mailInfoBean.setHeaderEnvelopeTo(receiveInfo.getReceiveMailAddress());

            this.editMailHeaderFromReceiveInfo(mailInfoBean, receiveInfo);                          // メール情報クラスのヘッダ情報調整
            mailInfoBean.setHeaderEnvelopeFrom(
                    getAddressShort(mailInfoBean.getHeaderFrom(),mailInfoBean.getHeaderFrom()));

            mailInfoBean.setToSubjectAddressFrom(getAddressShort(sendInfo.getSendMailAddress()));   // [件名]送信元アドレス
            if (!StringUtils.isBlank(passwordLgw)) {
                mailInfoBean.setToTextPasswordLgwan(passwordLgw); // [本文]パスワード（ＬＧＷＡＮ側）
            }
            mailInfoBean.setToTextPasswordInternet(passwordInt);  // [本文]パスワード（ＩＮＴＥＲＮＥＴ側）

            if(flgFileListOutput){
                setMailInfoFileList(receiveInfo, mailInfoBean, funcId);         // [本文]ファイル一覧
            }

            // メール送信
            mailInfoBean.setSentDate(new Date());                               // 送信時刻にシステム時刻を設定
            sendMail(session, mailInfoBean, null);
        }catch(Exception e)
        {
            LOG.error("#! sendMailPasswordNotice Error.  msg:"+e.getMessage());
            throw e;
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }

    /**
     * パスワード通知のメール情報にファイルリストをセットする
     * @param receiveInfo
     * @param mailInfoBean
     * @param funcId
     */
    public void setMailInfoFileList(ReceiveInfo receiveInfo, MailInfoBean mailInfoBean, String funcId)
    {
        // 受信ファイル一覧の取得
        List<String> fileNameList = new ArrayList<>();
        for (ReceiveFile receiveFile : receiveInfo.getReceiveFiles()) {
            fileNameList.add(receiveFile.getFileName());
        }
        if(fileNameList.isEmpty())
            return;
        String prefix = itemHelper.findMailMessage("filelist_prefix", funcId).getValue();
        String indent = itemHelper.findMailMessage("filelist_indent", funcId).getValue();
        String separator = itemHelper.findMailMessage("filelist_separator", funcId).getValue();
        mailInfoBean.setToTextFileNameList(fileNameList, prefix, indent, separator); // [本文]ファイル一覧
    }


    // ファイル送信依頼ＵＲＬ・パスワード通知
    public void sendMailRequest(SendRequestInfo sendRequestInfo) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        // 送信先が庁内もしくは庁外(LG)か、庁外かを取得
        boolean isLgwanAddress = isLgDomain(sendRequestInfo.getSendMailAddress());

        // [2017/06/13] funcIdの切り替え
        String funcid = isLgwanAddress ? "sendRequestLGWAN" : "sendRequest";

        // メール情報クラスの生成
        MailInfoBean mailInfoBean = getBaseNorticeInfo(funcid + "URL");
        String addressTo;
        try {
//            InternetAddress ia = new InternetAddress(sendRequestInfo.getSendMailAddress(), sendRequestInfo.getSendUserName());
            InternetAddress ia = new InternetAddress(sendRequestInfo.getSendMailAddress());
            addressTo = ia.toString();
        } catch (AddressException ex) {
            addressTo = "";
        }
        mailInfoBean.setHeaderTo(addressTo);
        mailInfoBean.setHeaderEnvelopeFrom(getAddressShort(mailInfoBean.getHeaderFrom()));
        mailInfoBean.setHeaderEnvelopeTo(sendRequestInfo.getSendMailAddress());

        // 依頼元の自治体名称の取得
        String sendName;
        try {
            sendName = itemHelper.find(Item.REGION_NAME, funcid).getValue();  // 機能ＩＤは送信依頼
        } catch (Exception e) {
            sendName = "";
        }

        // 依頼者アドレス情報の取得
        BasicUser basicUser = basicUserService.find(commonBean.getUserId());
        String addressFromL = getAddressLongUnicode(basicUser.getMailAddress(), basicUser.getName());

        // 生成したメール情報に文字挿入
        mailInfoBean.setToSubjectAddressFrom(sendName);                         // [件名]送信元名称
        mailInfoBean.setToTextExpirationTime(sendRequestInfo.getExpirationTime());  // [本文]期限
        // ワンタイムパスワード、ユーザ情報の登録とメール情報へのセット
        setMailInfoOnetimePassword(mailInfoBean, funcid, true,
                sendRequestInfo.isPassAuto(), sendRequestInfo.getPassWord(), isLgwanAddress,
                sendRequestInfo.getSendMailAddress(), "sendTransfer", sendRequestInfo.getId(), sendRequestInfo.getExpirationTime());
        mailInfoBean.setToTextSendText(sendRequestInfo.getContent());           // [本文]送信メール本文
        mailInfoBean.setToTextAddressFrom(addressFromL);                        // [本文]依頼者アドレス（フル）

        // メールサーバ接続
        Session session = connectMail();

        // メール送信
        mailInfoBean.setSentDate(new Date());                                   // 送信時刻にシステム時刻を設定
        sendMail(session, mailInfoBean, null);

        // パスワード通知を送信
        if (sendRequestInfo.isPassAuto() || sendRequestInfo.isPassNotice()) {
            String pswdLgw = mailInfoBean.getPasswordLgw();
            String pswdInt = mailInfoBean.getPasswordInt();

            // メール情報クラスの生成
            mailInfoBean = getBaseNorticeInfo(funcid + "Password");
            mailInfoBean.setHeaderTo(addressTo);
            mailInfoBean.setHeaderEnvelopeFrom(getAddressShort(mailInfoBean.getHeaderFrom()));
            mailInfoBean.setHeaderEnvelopeTo(sendRequestInfo.getSendMailAddress());
            mailInfoBean.setToSubjectAddressFrom(sendName);                     // [件名]送信元名称
            if (isLgwanAddress) {
                mailInfoBean.setToTextPasswordLgwan(pswdLgw);
            }                                                                   // [本文]ＵＲＬ（ＬＧＷＡＮ側）
            mailInfoBean.setToTextPasswordInternet(pswdInt);                     // [本文]パスワード（ＩＮＴＥＲＮＥＴ側）
            mailInfoBean.setToTextAddressFrom(addressFromL);                    // [本文]依頼者アドレス（フル）

            // メール送信
            mailInfoBean.setSentDate(new Date());                               // 送信時刻にシステム時刻を設定
            sendMail(session, mailInfoBean, null);
        }

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }

    
    //[V2.2.1]追加
    /**
     * サンドブラストへの送信
     * @param isMail            メール無害化かどうか（True:メール無害化 ／false：ファイル交換）
     * @param id                件名/本文に含めるID(UploadFileInfoのFileId）
     * @param fname             添付するファイルの添付用ファイル名
     * @param attachmentFile    添付するファイルの実体
     * @param flgCheckOnly      ふるまい検知のみかどうか（True：ふるまい検知のみ（静岡） / False：SandBlast結果ファイルをVotiro無害化に使用（京都）
     * @throws java.io.UnsupportedEncodingException 
     * @throws javax.mail.MessagingException 
     */
    public void sendMailSandBlast(boolean isMail, String id, String fname, File attachmentFile, boolean flgCheckOnly) 
            throws UnsupportedEncodingException, MessagingException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        try {
            // setting.properitesから各種設定値取得
            String subject = CommonUtil.getSetting("sandblastSubject");
            String text = CommonUtil.getSetting("sandblastText");        
            String headerFrom = CommonUtil.getSetting(isMail ? "sandblastFromMail" : "sandblastFrom");
            String headerTo = CommonUtil.getSetting(flgCheckOnly ? "sandblastToCheckOnly" : "sandblastTo");
            //ポート
//            String port = CommonUtil.getSetting( sandBlastOnlyFlg ? "smtpport_sb_out" : "smtpport_sb_out2");
            String port = CommonUtil.getSetting("smtpport_sb_out");

            LOG.debug("# sendMailSandBlast. [subject:{}, text:{}, headerFrom:{}, headerTo:{}, port:{}]",
                    subject, text, headerFrom, headerTo, port);
            //未設定チェック
            if(subject == null || text == null || headerFrom == null || headerTo == null || port == null){
                throw new RuntimeException("#! SandBlast送信用設定値が登録されていない項目があります。");
            }
            
            // メール情報クラスの生成
            MailInfoBean mailInfoBean = new MailInfoBean();
            mailInfoBean.setSubject(subject);   //件名
            mailInfoBean.setText(text);         //本文
            InternetAddress internetAddress = new InternetAddress(headerFrom);
            internetAddress.setPersonal(internetAddress.getPersonal());
            mailInfoBean.setHeaderFrom(internetAddress.toString());                 //From
            mailInfoBean.setHeaderTo(headerTo);                                     //To
            mailInfoBean.setHeaderEnvelopeFrom(internetAddress.getAddress());       //EnvelopeFrom
            mailInfoBean.setHeaderEnvelopeTo(headerTo);                            // EnvelopeTo(HeaderToと同じ)

            // 生成したメール情報に文字挿入
            mailInfoBean.setToSubjectId(id);                       // [件名]UploadFileInfoId
            mailInfoBean.setToTextId(id);                          // [本文]UploadFileInfoId
            mailInfoBean.setSentDate(new Date());                  // 送信時刻にシステム時刻を設定

            //セッション
            Session session = connectMail(port);

            // SandBlast宛メールはエンコード無しとしてメール送信処理を呼出す
            Multipart mmp = new MimeMultipart("mixed");
            BodyPart bodyPart = getBodyPartFromFile( fname, attachmentFile.getPath(), false, null);
            if (bodyPart == null) {
                //エラー
                throw new RuntimeException("#!SandBlast送信するMimeMessageへのファイル添付に失敗しました。[filePath:" + attachmentFile.getPath() + "]");
            }
            mmp.addBodyPart(bodyPart);
            sendMail(session, mailInfoBean, mmp, false);
        } catch (UnsupportedEncodingException | RuntimeException | MessagingException e) {
            //例外はそのままスローして、呼出側で対応する。
            throw e;            
        } finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }
    
    // [v2.2.1]単一ファイル毎に変更
    /**
     * サンドブラストへの送信
     * @param funcId
     * @param receiveInfo
     * @param sandBlastOnlyFlg 
     */
    /*
    public void sendMailSandBlast(String funcId, ReceiveInfo receiveInfo, boolean sandBlastOnlyFlg) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        // メール情報クラスの生成
        MailInfoBean mailInfoBean = getBaseNorticeInfo("toSandBlast");          // TODO: 機能名は検討中

        // [2017/05/15] メール無害化の場合、HeaderFromを書き換える
        if (receiveInfo.isAttachmentMailFlg()) {
            try {
                String headerFrom = itemHelper.findMailMessageDirect("nortice_headerFrom", "toSandBlastFromMail").getValue();
                InternetAddress internetAddress = new InternetAddress(headerFrom);
                internetAddress.setPersonal(internetAddress.getPersonal());
                mailInfoBean.setHeaderFrom(internetAddress.toString());
            } catch (Exception ex) {
                LOG.info("sendMailSandBlast item not found：nortice_headerFrom,toSandBlastFromMail");
            }
        }

        String addressTo;
        try {
            String[] domains = itemHelper.find(Item.REGION_DOMAIN, "toSandBlast").getValue().split(",");
            if (sandBlastOnlyFlg) {
                // ふるまい検知のみのドメイン指定
                addressTo = domains[0] + "@" + CommonUtil.getSetting("maildomain_sb_in2");
            } else {
                // ふるまい検知＋votiro無害化のドメイン指定
                addressTo = domains[0] + "@" + CommonUtil.getSetting("maildomain_sb_in");
            }
        } catch (Exception e) {
            addressTo = "";
            LOG.error("sendMailSandBlast：送信先設定エラー：receiveId=" + receiveInfo.getId());
        }
        mailInfoBean.setHeaderTo(addressTo);
        mailInfoBean.setHeaderEnvelopeFrom(getAddressShort(mailInfoBean.getHeaderFrom()));
        mailInfoBean.setHeaderEnvelopeTo(addressTo);                            // EnvelopeToはHeaderToと同じ

        // 生成したメール情報に文字挿入
        mailInfoBean.setToSubjectId(receiveInfo.getId());                       // [件名]ReceiveInfoId
        mailInfoBean.setToTextId(receiveInfo.getId());                          // [本文]ReceiveInfoId
        mailInfoBean.setSentDate(new Date());                                   // 送信時刻にシステム時刻を設定

        // 添付ファイルの一覧を作成する
//        List<String> fileList = new ArrayList<>();
        Map<String, String> fileMap = new HashMap<>();     //添付ファイル名のHashMap(Key=変換後ファイル名(IDファイル名）、Value=元ファイル名）

        // メール本体無害化の場合は添付排除メール情報を無害化対象として追加する
        String tempDir = new File(CommonUtil.getFolderTemp(), receiveInfo.getId()).getPath();   //保存先一時フォルダ
        if (receiveInfo.isMailSanitizeFlg()) {
            File mailFileFrom = new File(CommonUtil.getFolderSend(receiveInfo.getSendInfo(), false, true),
                    receiveInfo.getSendInfoId() + ".eml");  //元メールファイル
            File mailFileTo = new File(tempDir, receiveInfo.getId() + ".eml");      //メール本体ファイルをReceiveInfo.id + ".eml"とする

            // Sendフォルダの添付排除メール情報をTempフォルダに受信情報ＩＤにリネームして保存
            //テンポラリフォルダを念のためクリアする
            FileUtil.deleteFolder(tempDir, LOG);
            try {
                InputStream inputStream = new FileInputStream(mailFileFrom); //元メールファイル
                FileUtil.saveFile(inputStream, mailFileTo.getPath());

//                fileList.add(mailFpath);
                fileMap.put(mailFileTo.getName(), mailFileTo.getPath());  //メール本体は変換後ファイル名も元ファイル名と同じとする
            } catch (IOException ex) {
                LOG.error("メール本体無害化：eml添付失敗  [元:" + mailFileFrom.toString() + ", 先:" + mailFileTo.toString(), ex);
            }
        }
        //添付ファイル一覧生成
        switch (funcId) {
            case "passwordUnlock":
                for (ReceiveFile receiveFile : receiveInfo.getReceiveFiles()) {
                    // パスワード解除ファイル（親ファイルのみ対象）
                    for (DecryptFile _decryptFile : receiveFile.getDecryptFiles()) {
                        if (StringUtils.isEmpty(_decryptFile.getParentId())) {
                            if (_decryptFile.isTargetFlg()) {
                                // [2017/02/13] メール本体無害化の場合、メール無害化本体(receiveInfoId+".eml")と
                                // 同じ名前の添付ファイルは無害化処理できないため除外する
                                if (!(receiveInfo.isMailSanitizeFlg() && _decryptFile.getFileName().equalsIgnoreCase(receiveInfo.getId() + ".eml"))) {
//                                    fileList.add(_decryptFile.getFilePath());
                                    //ReceiveFile.ID + 拡張子をキーとする（添付ファイル名として使用）
                                    fileMap.put(_decryptFile.getReceiveFileId() + "." +  _decryptFile.getFileFormat() , _decryptFile.getFilePath());
                                }
                            }
                            break;
                        }
                    }
                }
                break;
            case "mailEntrance":
            case "sendTransfer":
            case "approveTransfer":
                List<ReceiveFile> rFileLst = receiveInfo.getReceiveFiles();

                for (SendFile sendFile : receiveInfo.getSendInfo().getSendFiles()) {
                    // [2017/02/13] メール本体無害化の場合、メール無害化本体(receiveInfoId+".eml")と
                    // 同じ名前の添付ファイルは無害化処理できないため除外する
                    if (!(receiveInfo.isMailSanitizeFlg() && sendFile.getFileName().equalsIgnoreCase(receiveInfo.getId() + ".eml"))) {
//                        fileList.add(sendFile.getFilePath());
                        //ReceiveIDを取得
                        String rcvId = "";
                        String fname = sendFile.getFileName();
                        for(ReceiveFile rcv : rFileLst){
                            if(rcv.getFileName().equals(fname)){
                                rcvId = rcv.getId();
                                break;
                            }
                        }
                        //ReceiveFile.ID + 拡張子をキーとする（添付ファイル名として使用）
                        if(!rcvId.isEmpty())
                            fileMap.put(rcvId + "." + sendFile.getFileFormat() , sendFile.getFilePath());
                    }
                }
                break;
            default:
                LOG.debug("無害化前ファイルのメール転送（送信ファイル）は実施しない...funcId=[" + funcId + "] ");
        }

        // 無害化前ファイルのメール転送（送信ファイル）
        Session session;
        if (sandBlastOnlyFlg) {
            session = connectMail(CommonUtil.getSetting("smtpport_sb_out2"));   // ふるまい検知のみのポート指定
        } else {
            session = connectMail(CommonUtil.getSetting("smtpport_sb_out"));    // ふるまい検知＋votiro無害化のポート指定
        }

        // [2017/08/23]SandBlast宛メールはエンコード無しとしてメール送信処理を呼出すように変更
        //sendMail(session, mailInfoBean, this.getMultiPartFromFileList(fileList));
        sendMail(session, mailInfoBean, this.getMultiPartFromFileList(fileMap, false, receiveInfo), false);     //[248対応（簡易版）]

        // メール本体無害化で作成したテンポラリフォルダを削除する
        if (receiveInfo.isMailSanitizeFlg()) {
            FileUtil.deleteFolder(tempDir, LOG);
        }

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }
    */
    
    /**
     * パスワード設定通知メール送信
     *
     * @param bean
     * @return
     */
    public int sendMailPasswdSet(ManageIdBean bean) {
        //[v2.1.11]共通化
        return sendMailPasswdSet(bean.getMailAddress(), bean.getName(), bean.getUserId());
    }

    /**
     * パスワード設定通知メール送信(ログイン画面より)
     *
     * @param user
     * @return
     */
    public int sendMailPasswdSet(BasicUser user) {
        //[v2.1.11]共通化
        return sendMailPasswdSet(user.getMailAddress(), user.getName(), user.getUserId());
    }

    //[v2.1.11]共通化
    /**
     * パスワード設定通知メール送信
     * @param mailAddress
     * @param name
     * @param userId
     * @return
     */
    public int sendMailPasswdSet(String mailAddress, String name, String userId) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        // 送信先が庁内か庁外かを取得
        boolean isMyDomain = isMyDomain(mailAddress);
        // メールアドレスが庁外(LG)かどうかを判定
        boolean isLgDomain = isLgDomain(mailAddress);
        // 送信先が庁内もしくは庁外(LG)か、庁外かを取得
        boolean isLgwanAddress = (isMyDomain || isLgDomain);

        // [2017/06/13] funcIdの切り替え
        String funcId;
        if (!isMyDomain) {
            funcId = isLgDomain ? "manageIdOuterLGWAN" : "manageIdOuter";
        } else {
            funcId = "manageIdInner";
        }

        //保存期間初期値
        Item item = itemHelper.find(Item.EXPIRATION_DEFAULT, funcId);           // 機能ＩＤはＩＤ管理
        Date expirationDefault = DateUtil.addDays(new Date(), item.getValue());

        // メール情報クラスの生成
        MailInfoBean mailInfoBean = getBaseNorticeInfo(funcId + "URL");
        String addressTo = getAddressLongString(mailAddress, name, "");     //送信先アドレス
        mailInfoBean.setHeaderTo(addressTo);
        mailInfoBean.setHeaderEnvelopeFrom(getAddressShort(mailInfoBean.getHeaderFrom()));
        mailInfoBean.setHeaderEnvelopeTo(mailAddress);

        // 生成したメール情報に文字挿入
        mailInfoBean.setToTextId(userId);                             // [本文]ユーザーID
        mailInfoBean.setToTextExpirationTime(expirationDefault);                // [本文]期限
        // ワンタイムパスワード、ユーザ情報の登録とメール情報へのセット
        setMailInfoOnetimePassword(mailInfoBean, funcId, true,
                true, "", isLgwanAddress,
                mailAddress, "userPasswordSet", userId, expirationDefault);

        if (isLgwanAddress) {
            mailInfoBean.setToTextLoginUrlLgwan(CommonUtil.createRootUrl(commonBean.getRegionId(), true));
        }                                                                       // [本文]ログインＵＲＬ（ＬＧＷＡＮ側）
        mailInfoBean.setToTextLoginUrlInternet(CommonUtil.createRootUrl(commonBean.getRegionId(), false));
        // [本文]ログインＵＲＬ（ＩＮＴＥＲＮＥＴ側）

        // メールサーバ接続
        Session session = connectMail();

        // メール送信
        mailInfoBean.setSentDate(new Date());                                   // 送信時刻にシステム時刻を設定
        sendMail(session, mailInfoBean, null);

        String pswdLgw = mailInfoBean.getPasswordLgw();
        String pswdInt = mailInfoBean.getPasswordInt();

        //-------------------------------------------------
        // パスワード通知を送信
        //-------------------------------------------------
        mailInfoBean = getBaseNorticeInfo(funcId + "Password");
        if (isLgwanAddress) {
            mailInfoBean.setToTextPasswordLgwan(pswdLgw);
        }
        // [本文]パスワード（ＬＧＷＡＮ側）
        mailInfoBean.setToTextPasswordInternet(pswdInt);      // [本文]パスワード（ＩＮＴＥＲＮＥＴ側）
        mailInfoBean.setHeaderTo(addressTo);
        mailInfoBean.setHeaderEnvelopeFrom(getAddressShort(mailInfoBean.getHeaderFrom()));
        mailInfoBean.setHeaderEnvelopeTo(mailAddress);

        // メール送信
        mailInfoBean.setSentDate(new Date());                                   // 送信時刻にシステム時刻を設定
        sendMail(session, mailInfoBean, null);

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));

        return 0;
    }

    // 承認依頼ＵＲＬ
    public void sendMailApprovalRequest(SendInfo sendInfo, ApproveInfo approveInfo, String mailAddressApprovals) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        //[v2.1.11] 承認依頼/却下URL送信　共通化
        sendMailApproval(
                "approvalRequest",
                sendInfo.getSendMailAddress(),
                sendInfo.getSendUserName(),
                mailAddressApprovals,
                approveInfo.getApproveMailAddress(),
                sendInfo.getApprovalsComment(),
                sendInfo.getExpirationTime()
                );

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }

    // 承認却下ＵＲＬ
    public void sendMailApprovalRejected(InternetAddress iaToAddress, ApproveInfo approveInfo, String mailAddressApprovals) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        //[v2.1.11] 承認依頼/却下URL送信　共通化
        sendMailApproval(
                "approvalRejected",
                approveInfo.getApproveMailAddress(),
                approveInfo.getApproveUserName(),
                mailAddressApprovals,
                iaToAddress.getAddress(),
                approveInfo.getApprovedComment(),
                null
                );

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }

    //[v2.1.11]共通化
    /**
     * 承認依頼・却下メール送信（共通）
     * @param funcId   機能ID
     * @param fromMailAddress   Fromメールアドレス
     * @param fromUserName      Fromユーザ名
     * @param toAddress         Toメールアドレス
     * @param enveloptToAddress EnvelopeToアドレス
     * @param contents          本文
     * @param expirationTime    期限
     */
    private void sendMailApproval(
            String funcId,
            String fromMailAddress,
            String fromUserName,
            String toAddress,
            String enveloptToAddress,
            String contents,
            Date expirationTime
            ) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        // メール情報クラスの生成
        MailInfoBean mailInfoBean = getBaseNorticeInfo(funcId);

        mailInfoBean.setHeaderTo(toAddress);
        mailInfoBean.setHeaderEnvelopeTo(enveloptToAddress);
        mailInfoBean.setHeaderEnvelopeFrom(getAddressShort(mailInfoBean.getHeaderFrom()));
        mailInfoBean.setToSubjectAddressFrom(getAddressShort(fromMailAddress));               // [件名]送信元アドレス
        mailInfoBean.setToTextAddressFrom(getAddressLongUnicode(fromMailAddress, fromUserName));     // [本文]送信元アドレス（フル）
        mailInfoBean.setToTextLoginUrlLgwan(
                CommonUtil.createRootUrl(commonBean.getRegionId(), true));      // [本文]ログインＵＲＬ（ＬＧＷＡＮ側）
        mailInfoBean.setToTextLoginUrlInternet(
                CommonUtil.createRootUrl(commonBean.getRegionId(), false));     // [本文]ログインＵＲＬ（ＩＮＴＥＲＮＥＴ側）
        if(expirationTime != null){
            mailInfoBean.setToTextExpirationTime(expirationTime);               // [本文]期限＝ファイル送信.有効期限
        }
        mailInfoBean.setToTextSendText(contents);                               // [本文]送信メール本文

        // メール送信
        mailInfoBean.setSentDate(new Date());                                   // 送信時刻にシステム時刻を設定
        sendMail(connectMail(), mailInfoBean, null);

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }

    //[v2.1.11]処理の共通化
    /**
     * メールアドレスを取得
     * @param mailAddress
     * @return
     */
    public static String getAddressShort(String mailAddress)
    {
        return getAddressShort(mailAddress, "");
    }

    //[v2.1.11]処理の共通化
    /**
     * メールアドレスを取得
     * @param mailAddress
     * @param defaultAddress   //メールアドレス変換出来なかった場合に返す値
     * @return
     */
    public static String getAddressShort(String mailAddress, String defaultAddress)
    {
        String addressS = defaultAddress;
        try {
            if(!StringUtils.isBlank(mailAddress)){
                InternetAddress iAddress = new InternetAddress(mailAddress);
                addressS = iAddress.getAddress();
            }
        } catch (Exception ex) {
            addressS = defaultAddress;
        }
        return addressS;
    }

    //[v2.1.11]処理の共通化
    /**
     * メールアドレス（日本語名称付）を取得
     * @param mailAddress
     * @param name
     * @return
     */
    public static String getAddressLongUnicode(String mailAddress, String name)
    {
        String addressL = "";
        try {
            InternetAddress iAddress = new InternetAddress(mailAddress, name);
            addressL = iAddress.toUnicodeString();
        } catch (Exception ex) {
        }
        return addressL;
    }

    //[v2.1.11]処理の共通化
    /**
     * メールアドレス（日本語変換前名称付）を取得
     * @param mailAddress
     * @param name
     * @param defaultSddress 変換失敗時に返す値
     * @return
     */
    public static String getAddressLongString(String mailAddress, String name, String defaultAddress)
    {
        String addressL = "";
        try {
            InternetAddress iAddress = new InternetAddress(mailAddress, name);
            addressL = iAddress.toString();
        } catch (Exception ex) {
            addressL = defaultAddress;
        }
        return addressL;
    }

    //[v2.1.11]処理の共通化
    /**
     * MimeMessageの生成
     * @param session
     * @param mailInfoBean
     * @return
     * @throws FileNotFoundException
     * @throws MessagingException
     */
    private FssMimeMessage getMimeMessageForSend(
            Session session, MailInfoBean mailInfoBean) throws FileNotFoundException, MessagingException
    {
        return getMimeMessageForSend(session, mailInfoBean, null);
    }

    //[v2.1.11]処理の共通化
    /**
     * MimeMessageの生成　（既存メールファイルから）
     * @param session
     * @param mailInfoBean
     * @param emlPath   メールファイル（nullの場合は新規MimeMessageの生成）
     * @return
     * @throws FileNotFoundException
     * @throws MessagingException
     */
    private FssMimeMessage getMimeMessageForSend(
            Session session, MailInfoBean mailInfoBean, String emlPath) throws FileNotFoundException, MessagingException
    {
        // EnvelopeFromを設定
        if (!StringUtils.isEmpty(mailInfoBean.getHeaderEnvelopeFrom())) {
            try {
                InternetAddress iaEnvelopeFrom = mailInfoBean.getAddressEnvelopeFrom()[0];
                session.getProperties().setProperty("mail.smtp.from", iaEnvelopeFrom.getAddress());
            } catch (AddressException ex) {
                LOG.error("EnvelopeFrom設定エラー:" + mailInfoBean.getHeaderEnvelopeFrom() + ", message:" + ex.getMessage());
                session.getProperties().setProperty("mail.smtp.from", mailInfoBean.getHeaderEnvelopeFrom());
            }
        }
        FssMimeMessage mimeMessage;
        if(StringUtils.isBlank(emlPath)){
            mimeMessage = new FssMimeMessage(session);
        }else{
            InputStream inputStream = new FileInputStream(emlPath);
            mimeMessage = new FssMimeMessage(session, inputStream);
        }
        mimeMessage.setOriginalMessageId(mailInfoBean.getMessageId());      // 元のMessageIdで上書き
        return mimeMessage;
    }

    //[v2.1.11]共通化
    /**
     * ワンタイムパスワード、ユーザ情報の登録とメール情報へのセット
     * @param mailInfo
     * @param funcId
     * @param flgMakePassword
     * @param isPassAuto
     * @param passwordDef
     * @param flgLgWan
     * @param mailAddress
     * @param target
     * @param infoId
     * @param expirationTime
     */
    private void setMailInfoOnetimePassword(MailInfoBean mailInfo, String funcId, boolean flgMakePassword, boolean isPassAuto, String passwordDef,  boolean flgLgWan,
            String mailAddress, String target, String infoId, Date expirationTime)
    {
        if(flgMakePassword){
            // ワンタイムパスワード生成
            Item item = itemHelper.find(Item.PASSWORD_CHAR_DEFAULT, funcId);       // 機能ＩＤはファイル送信
            int pwLength;
            try {
                pwLength = Integer.parseInt(item.getValue());
            } catch (Exception e) {
                pwLength = 8;
            }
            if (isPassAuto) {
                //パスワード自動生成フラグ=Trueの場合は、パスワードを生成する
                mailInfo.setPasswordLgw(flgLgWan ? RandomStringUtils.randomAlphanumeric(pwLength) : "");    // LGWAN側
                mailInfo.setPasswordInt(RandomStringUtils.randomAlphanumeric(pwLength));    // Int側
            }else{
                mailInfo.setPasswordLgw(flgLgWan ? passwordDef : "");
                mailInfo.setPasswordInt(passwordDef);
            }
        }else{
            mailInfo.setPasswordInt("");
            mailInfo.setPasswordLgw("");
        }

        // ワンタイムユーザ情報の登録
        OnceUser onceUserLgw = null;
        if (flgLgWan) {
            onceUserLgw = insertOnceUser(mailAddress, target, infoId, mailInfo.getPasswordLgw(), expirationTime);
        }
        OnceUser onceUserInt = insertOnceUser(mailAddress, target, infoId, mailInfo.getPasswordInt(), expirationTime);

        //mailInfoBeanのTextの所定位置を置換え
        if (onceUserLgw != null) {
            mailInfo.setToTextUrlLgwan(
                    CommonUtil.createOnetimeUrl(commonBean.getRegionId(), onceUserLgw.getOnetimeId(), true)
            );      // [本文]ＵＲＬ（ＬＧＷＡＮ側）
        }
        mailInfo.setToTextUrlInternet(
                CommonUtil.createOnetimeUrl(commonBean.getRegionId(), onceUserInt.getOnetimeId(), false)
        );        // [本文]ＵＲＬ（ＩＮＴＥＲＮＥＴ側）
    }

    /**
     * ヘッダーをチェックして変換が必要な場合は変換する（AppleMail対応）
     * マルチパートの階層は２階層まで
     * @param mime      MimeMessage
     * @return  変換有無
     * @throws MessagingException
     * @throws IOException
     */
    private boolean checkMailHeader(MimeMessage mime) throws MessagingException, IOException
    {
        return checkMailHeader(mime, 0, true);
    }

    /**
     * ヘッダーをチェックして変換が必要な場合は変換する（AppleMail対応）
     * Contents（本文、マルチパート）は対象外
     * @param mime      MimeMessage
     * @return  変換有無
     */
    private boolean checkMailHeaderWithoutContents(MimeMessage mime)
    {
        return checkMailHeader(mime, 0, false);
    }

    /**
     * ヘッダーをチェックして変換が必要な場合は変換する（AppleMail対応）
     * マルチパートの階層は２階層まで
     * @param part
     * @param level     マルチパート階層（0:メール本体、1～:マルチパート階層）
     * @param flgChkContents    本文、マルチパートも対象とするかどうか
     * @return  変換有無
     */
    private boolean checkMailHeader(Part part, int level, boolean flgChkContents)
    {
        //対象ヘッダ項目
        final List<String> HeaderListMain =
                Arrays.asList("From", "To", "Cc", "Bcc", "Subject", "X-Enverope-From", "X-Enverope-To");          //cp932変換対象メイン項目
        final List<String> HeaderListCharset = Arrays.asList("Content-Type");                        //cp932変換charsetパラメータ対象項目
        final List<String> HeaderListContent = Arrays.asList("Content-Type", "Content-Disposition");     //cp932,ファイル名"/"変換対象コンテンツ項目(マルチパート内にも含まれる）

        try{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

            //引数チェック
            if(part == null) return false;
            boolean bRet = false;   //変換有無

            //--------------------------------------------------------
            // RFC2047形式対応
            // ・CP932変換　（"=?cp932?" ⇒ "=?Windows-31J?" 変換）
            //--------------------------------------------------------
            List<String> hLst = new ArrayList<>();
            if(level == 0)
                hLst.addAll(HeaderListMain);    //メール本体（MimeMessage)の場合は対象項目としてヘッダメイン項目（From,To,Subject)を追加
            hLst.addAll(HeaderListContent);     //対象項目としてヘッダサブ項目（Content-Type,Content-Disposition)を追加
            if(checkMailHeaderRFC2047(part, hLst))
                bRet = true;

            //--------------------------------------------------------
            // ・charset CP932変換（"charset=cp932" ⇒ "charset=Windows-31J" 変換）
            //--------------------------------------------------------
            if(checkMailHeaderCharset(part, HeaderListCharset))
                bRet = true;

            //--------------------------------------------------------
            // RFC2231形式対応
            // ・CP932変換　（"*=cp932" ⇒ "*=Windows-31J" 変換）
            // ・ファイル名"/"変換　（"/" ⇒ "%2F" 変換）
            //--------------------------------------------------------
            if(checkMailHeaderRFC2231(part, HeaderListContent))
                bRet = true;

            //メール本文、マルチパートも対象とする場合
            if(flgChkContents){
                //マルチパートの再帰処理と全て添付ファイルパートだった場合のTextパートの追加
                //※マルチパート処理対象最大階層（MULTIPART_MAX_LEVEL）まで
                if (level < MULTIPART_MAX_LEVEL && part.getContentType().toLowerCase().contains("multipart")) {
                    MimeMultipart multiPartOriginal = (MimeMultipart) part.getContent();
                    boolean hasMainPart=false;  //メインコンテンツ（添付コンテンツ以外）有りフラグ
                    //マルチパートの全パートに対するヘッダチェック
                    for (int indexPart = 0; indexPart < multiPartOriginal.getCount(); indexPart++) {
                        BodyPart bpart = multiPartOriginal.getBodyPart(indexPart);
                        //ヘッダーチェックの再帰処理
                    if(checkMailHeader(bpart, level + 1, flgChkContents)){
                            bRet = true;    //変換有り
                        }
                        //添付ファイル以外かどうか
                        if ( !isAttachmentPart(bpart)){
                            //添付ファイルではない（分離されない）パートがある
                            hasMainPart = true;
                        }
                    }
                    //マルチパートに1つもテキストパートがなかった場合、text/plainのパートを追加する
                    //※後で添付ファイルの分離をした際にBodyPartが1つもないマルチパートとなり保存時にエラーとなるため
                    if(!hasMainPart){
                        BodyPart mbp = new MimeBodyPart();
                        mbp.setText("");    //test/plainパート
                        multiPartOriginal.addBodyPart(mbp, 0);
                        bRet = true;    //変換あり
                    }
                }
            }
            return bRet;
        }catch(Exception e){
            //この処理での例外発生は無視して続行する。
            LOG.warn("checkMailHeader Error!", e);
            return false;
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }

    /**
     * メールヘッダーチェック＆変換（RFC2047形式 CP932対応）
     * @param part
     * @param headerNameList
     * @return
     * @throws MessagingException
     */
    private boolean checkMailHeaderRFC2047(Part part, List<String> headerNameList) throws MessagingException
    {
        final String regex = "^=\\?CP932\\?[BQ]\\?.*\\?=.*";    //RFC2047形式 正規表現

        //--------------------------------------------------------
        // RFC2047形式対応
        // ・CP932変換　（"=?cp932?" ⇒ "=?Windows-31J?" 変換）
        //--------------------------------------------------------
        boolean bRet = false;
        for(String hName: headerNameList){
            String hValue = getMailHeader(part, hName);     //ヘッダー設定値を取得
            String hValueNew = hValue;
            int sPos = -1;
            boolean flg = false;
            //ヘッダー設定値から置換対象文字列「=?cp932?」を検索 （大文字/小文字無視で）し置換する
            while((sPos = hValueNew.toUpperCase().indexOf("=?CP932?", sPos+1)) >= 0)   //置換対象が複数の可能性があるので、繰り返し検索する。
            {
                //実際の値に（例えば件名）に"=?cp932?"の文字が含まれる場合（置換対象外）もあるので
                //検索対象文字以降の文字列がRFC2047に準拠しているかを判定
                //"=?{文字コード(CP932)}?{エンコード方式([B]or[Q]}?{エンコードデータ}?="
                String subStr = hValueNew.substring(sPos);
                Pattern p = Pattern.compile(regex, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE );
                if (p.matcher(subStr).find()){
                    hValueNew = hValueNew.substring(0, sPos) +
                            Pattern.compile("CP932",Pattern.CASE_INSENSITIVE).matcher(subStr).replaceFirst("Windows-31J");
                    flg = true; //変更あり
                }
            }
            if(flg){
                //ヘッダー書換え
                part.setHeader(hName, hValueNew);
                LOG.debug("#### checkMailHeader. replace '=?cp932?' to '=?Windows-31J?'. name:{}, value_old:{}, value_new:{}", hName, hValue, hValueNew);
                bRet = true;
            }
        }
        return bRet;
    }

    /**
     * メールヘッダーチェック＆変換（charset）
     * @param part
     * @param headerNameList
     * @return
     * @throws MessagingException
     */
    private boolean checkMailHeaderCharset(Part part, List<String> headerNameList) throws MessagingException
    {
        //--------------------------------------------------------
        // ・charset CP932変換（"charset=cp932" ⇒ "charset=Windows-31J" 変換）
        //--------------------------------------------------------
        //content-Typeのnemeパラメータのファイル名に対象文字列("charset=cp932"）が含まれている可能性があるので、以下のように判定する。
        //①ヘッダー設定値から"cp932"を大文字/小文字無視で検索。（大文字/小文字の違いと""に囲まれている可能性があるため）
        //nameパラメータのファイル名に"charset=cp932"が含まれる可能性を考慮し
        //②ヘッダー設定値を";"でパラメータ単位に分割（"に囲まれた";"は無視）し、パラメータ名"charset"の分割文字列中に"cp932"(大小文字無視）が含まれるか。
        //③パラメータ名"charset"の分割文字列内の"cp932"(大小文字無視）を"Windows-31J"に変換
        //④分割文字列を再結合して当該ヘッダにセットする。
        boolean bRet = false;
        for(String hName: headerNameList){
            String hValue = getMailHeader(part, hName);     //ヘッダー設定値を取得
            //ヘッダー設定値に"CP932"が含まれるか
            if(!hValue.toUpperCase().contains("CP932"))
                continue;
            //ヘッダー設定値をパラメータ毎に分割
            List<String> paramLst = splitHeaderParam(hValue);
            //"charset"パラメータを抽出し、"CP932"が含まれているか
            for(int pi =0; pi < paramLst.size(); pi++)
            {
                String param = paramLst.get(pi);
                if(isHeaderParam(param,"charset")){
                    //"charset"パラメータに"CP932"が含まれているか？
                    if(param.toUpperCase().contains("CP932")){
                        //"CP932"(大文字/小文字無視）を"Windows-31J"に変換
                        param = Pattern.compile("CP932",Pattern.CASE_INSENSITIVE).matcher(param).replaceFirst("Windows-31J");
                        paramLst.set(pi, param);
                        //分割文字列を再結合
                        String hValueNew = "";
                        for(String p:paramLst){
                            hValueNew += p;
                        }
                        //ヘッダー書換え
                        part.setHeader(hName, hValueNew);
                        LOG.debug("#### checkMailHeader. replace charset 'CP932' to 'Windows-31J'. name:{}, value_old:{}, value_new:{}", hName, hValue, hValueNew);
                        bRet = true;
                    }
                    break;
                }
            }
        }
        return bRet;
    }

    /**
     * メールヘッダーチェック＆変換（RFC2231形式対応）
     * @param part
     * @param headerNameList
     * @return
     * @throws MessagingException
     */
    private boolean checkMailHeaderRFC2231(Part part, List<String> headerNameList) throws MessagingException
    {
        //--------------------------------------------------------
        // RFC2231形式対応
        // ・CP932変換　（"*=cp932" ⇒ "*=Windows-31J" 変換）
        // ・ファイル名"/"変換　（"/" ⇒ "%2F" 変換）
        //--------------------------------------------------------
        //◆CP932変換
        //①ヘッダー設定値から置換対象文字列「*=CP932'」を検索 （大文字/小文字無視で）
        //  ※ファイル名には"*"が使用できないため、対象項目の実際の値に置換え対象文字列が含まれる可能性がないので、単純に検索、置換可能。
        //◆ファイル名"/"変換
        //①ヘッダー設定値から判定文字列「*=」を検索
        //②「*=」の位置から";"または文字列終端までの範囲にある置換対象文字列「/」を検索、置換　（複数の場合もあるので全て）
        //  ※単純に「/」を全て置換すると、対象パラメータ（filename,name）以外のパラメータに「/」が含まれている場合に誤って置換されるため検索範囲を限定する。
        boolean bRet = false;
        for(String hName: headerNameList){
            String hValue = getMailHeader(part, hName);     //ヘッダー設定値を取得
            //RFC2231形式でエンコードされているか"*="を検索する
            int sPos = hValue.indexOf("*=");
            if(sPos < 0)
                continue;
            //◆CP932変換
            //ヘッダー設定値の"*=CP932'"を"*=Windows-31J'"に置換
            if(hValue.toUpperCase().contains("*=CP932'")){
                //"*=CP932'"(大文字/小文字無視）を"*=Windows-31J'"に変換
                String hValueNew = Pattern.compile("\\*=CP932'",Pattern.CASE_INSENSITIVE).matcher(hValue).replaceAll("*=Windows-31J'");
                //ヘッダー書換え
                part.setHeader(hName, hValueNew);
                LOG.debug("#### checkMailHeader. replace '*=CP932' to '*=Windows-31J'. name:{}, value_old:{}, value_new:{}", hName, hValue, hValueNew);
                hValue = hValueNew;
                bRet = true;
            }

            //◆ファイル名"/"変換
            //「*=」の位置から";"または文字列終端までの範囲にある置換対象文字列「/」を検索、置換　（複数の場合もあるので全て）
            boolean flg = false;
            List<String> paramLst = splitHeaderParam(hValue);   //ヘッダー設定値をパラメータ毎に分割
            for(int pi =0; pi < paramLst.size(); pi++)
            {
                String param = paramLst.get(pi);
                if(param.contains("*=") && param.contains("/")){
                    // "/"を"%2F"に変換
                    param = param.replaceAll("/", "%2F");
                    paramLst.set(pi, param);
                    flg = true;
                }
            }
            if(flg){
                //分割文字列を再結合
                String hValueNew = "";
                for(String p:paramLst){
                    hValueNew += p;
                }
                //ヘッダー書換え
                part.setHeader(hName, hValueNew);
                bRet = true;
                LOG.debug("#### checkMailHeader. replace filename '/' to '%2f'. name:{}, value_old:{}, value_new:{}", hName, hValue,  hValueNew);
            }
        }
        return bRet;
    }

    /**
     * ヘッダーの内容をパラメータ毎（区切り文字";"）に分割
     * @param headerValue
     * @return
     */
    private static List<String> splitHeaderParam(String headerValue) {
        char c;
        StringBuilder s = new StringBuilder();
        List<String> paramLst = new ArrayList<>();
        boolean quoteFlg = false;
        for(int i=0; i < headerValue.length(); i++){
            c = headerValue.charAt(i);
            s.append(c);
            if (c == '"') {
                //ダブルコーテーション
                quoteFlg = !quoteFlg;
            }else if (!quoteFlg ){
                //ダブルコーテーション外
                if (c == ';'){
                    //区切り";"
                    paramLst.add(s.toString());
                    s.setLength(0);
                }
            }
        }
        if(s.length() > 0){
            paramLst.add(s.toString());
        }
        return paramLst;
    }

    /**
     * 指定したパラメータかどうかの判定
     */
    private static boolean isHeaderParam(String headerParam, String paramName)
    {
        //最初の"="の位置
        int index = headerParam.indexOf("=");
        if(index <= 0){
            return false;
        }
        //パラメータ名を取得
        String srcParamName = headerParam.substring(0, index).replaceAll("\n", "").trim();
        return srcParamName.equalsIgnoreCase(paramName);
    }

    /**
     * 添付ファイルパートかどうかの判定
     * @param part
     * @return
     */
    private static boolean isAttachmentPart(Part part) throws MessagingException
    {
        boolean bRet = false;
        String disposition = part.getDisposition();
        if(StringUtils.isEmpty(disposition))
            return bRet;
        if (Part.ATTACHMENT.equalsIgnoreCase(disposition)
                || (Part.INLINE.equalsIgnoreCase(disposition) && !StringUtils.isEmpty(part.getFileName()))) {
            //添付ファイルパート
            bRet = true;
        }
        //※添付ファイルだが”Content-Disposition: attachment;”が存在しない例があった。
        //そのケースにも対応するにはファイル名が取得できれば添付ファイルと判定するようにする対応が必要かも。

        return bRet;
    }

    public void SetLog(Logger log){
        this.LOG = log;
    }

    /**
     * MimeMessage取得
     * @param mailFile
     * @return
     * @throws FileNotFoundException
     * @throws IOException
     * @throws MessagingException 
     */
    public FssMimeMessage getMimeMessage(File mailFile) throws FileNotFoundException, IOException, MessagingException
    {
        try (InputStream inputStream = new FileInputStream(mailFile)) {
            Session session = Session.getInstance(new java.util.Properties());
            FssMimeMessage mimeMessage = new FssMimeMessage(session, inputStream);  // （注意）メールファイルのサイズが大きいとメモリ不足で異常終了(OutOfMemorryError)
            return mimeMessage;
        }
    }
    
    /**
     * SandBlastリターンメールから添付ファイルを分離保存する
     * （保存先指定の無い場合はチェックのみ）
     * @param mimeMessage   MimeMessage
     * @param fileName      ファイル名
     * @param saveDir       保存先フォルダ(保存せずチェックだけの場合(静岡系)はnullを指定）
     * @throws MessagingException
     * @throws IOException 
     * @throws FssException チェックNG（リトライ対象外）
     */
    public void saveAttachmentFileForSandBlast(
            FssMimeMessage mimeMessage,
            String fileName,
            String saveDir)
            throws MessagingException, IOException, FssException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "FileName=" + fileName, "saveDir=" + saveDir));        
        try {
            boolean result = false;
            //添付ファイルのチェック・保存
            Object objContent = mimeMessage.getContent();
            if (objContent instanceof Multipart) {
                MimeMultipart multiPart = (MimeMultipart) objContent;
                for (int indexPart = multiPart.getCount()-1; indexPart >= 0 ; indexPart--) {
                    BodyPart part = multiPart.getBodyPart(indexPart);
                    if (isAttachmentPart(part)) {
                        //マルチパートからファイル名取得
                        String partFileName = getDecodeFileName(part);
                        //ファイル名チェック
                        if(!fileName.equalsIgnoreCase(partFileName)){
                            //送ったはずのファイルと添付ファイル名が異なるのでエラーと判定する
                            throw new FssException("添付ファイルのファイル名が違います。(fileName:" + partFileName + ")");
                        }
                        //添付ファイルを保存する場合(保存先フォルダが指定されている場合）は指定のファイル名でファイル出力する
                        if(!StringUtils.isBlank(saveDir)){
                            Path dstPath = Paths.get(saveDir, partFileName);
                            FileUtil.saveFile(part.getInputStream(),dstPath.toString());
                        }
                        result = true;  //チェックOK
                        break;  //SandBlastリターンメールは添付ファイルは必ず１つのはずだから１つチェックしたらすぐ抜ける
                    }
                }                    
            }
            if(!result){
                //ここでチェックOKじゃなかったら添付ファイルがなかったと判定
                throw new FssException("添付ファイルがありません。");
            }
        }catch(IOException | MessagingException | FssException e){
            //例外をそのままスローする。
            //FssExceptionはチェックNG、それ以外は例外発生として呼出元で対応すること。
            throw e;
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }    
    
    /**
     * メールヘッダーチェック＆変換（222_AppleMail対応）テスト用ドライバ
     * @param mailPath  メールファイルフルパス
     * @param mailId    メールID（サブフォルダ名）
     * @param testDir   出力先フォルダ
     * @return 変換有無
     */
    public boolean testMailHeaderCheck(String mailPath, String mailId,  String testDir)
    {
        LOG = org.apache.logging.log4j.LogManager.getLogger("console");
        try (InputStream inputStream = new FileInputStream(mailPath)) {
            Session session = Session.getInstance(new java.util.Properties());
            FssMimeMessage mimeMessage = new FssMimeMessage(session, inputStream);

            //ヘッダーチェック＆変換
            boolean bRet = checkMailHeader(mimeMessage);
            
            String subject = mimeMessage.getSubject();
            String subjectOrg = mimeMessage.getSubjectOrg();
            System.out.println("SubjectNew:" + subject + "\nSubjectOld:" + subjectOrg);

            mimeMessage.setOriginalMessageId(mimeMessage.getMessageID());       // 元メールのMessageIDを退避
            MailInfoBean mailInfoBean = new MailInfoBean();
            mailInfoBean.setSendInfoId(mailId);                                 // 送信情報ＩＤを設定
            analyzeMimeMessage(mailInfoBean, mimeMessage);                      // メール情報の解析・取得
            mailInfoBean.setNoticeCode(checkMimeMessage(mimeMessage));          // メール対応チェック
            
            //返還後のメール（添付ファイル分離前）を保存（ファイル名 = 元メール+"_text.eml"）
            mimeMessage.saveChanges();
            try(OutputStream os1 = FileUtil.getFileOutputStream(mailPath +"_test.eml")){
                mimeMessage.writeTo(os1);
            }

            //分離保存
            savaAttachment(mimeMessage, mailId, testDir, mailInfoBean, FileUtil.MAX_FILENAME_LEN);

            return bRet;
        }catch(Exception e){
            LOG.error("!!TEST ERROR!!",e );
            return false;
        }
    }
}
