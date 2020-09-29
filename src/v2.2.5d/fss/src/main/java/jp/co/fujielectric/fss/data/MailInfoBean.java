package jp.co.fujielectric.fss.data;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * メール情報クラス
 */
@Data
public class MailInfoBean {

    private String messageId;
    private String sendInfoId;
    private String receiveInfoId;
    private String subject;
    private String headerFrom;
    private String headerTo;
    private String headerCc;
    private String headerBcc;
    private String headerEnvelopeFrom;
    private String headerEnvelopeTo;
    private String headerEnvelopeFromOrg;
    private String headerEnvelopeToOrg;
    private String text;
    private Date sentDate;

    private long emlFileSize;
    private String noticeCode;

    private String passwordLgw="";  //パスワード（LGWan用）
    private String passwordInt="";  //パスワード（Internet用）

    private String mailDate;    //[v2.2.1]
    
    public InternetAddress[] getAddressFrom() throws AddressException {
        return InternetAddress.parse(Optional.ofNullable(headerFrom).orElse(""));
    }

    public InternetAddress[] getAddressTo() throws AddressException {
        return InternetAddress.parse(Optional.ofNullable(headerTo).orElse(""));
    }

    public InternetAddress[] getAddressCc() throws AddressException {
        return InternetAddress.parse(Optional.ofNullable(headerCc).orElse(""));
    }

    public InternetAddress[] getAddressBcc() throws AddressException {
        return InternetAddress.parse(Optional.ofNullable(headerBcc).orElse(""));
    }

    public InternetAddress[] getAddressEnvelopeFrom() throws AddressException {
        return InternetAddress.parse(Optional.ofNullable(headerEnvelopeFrom).orElse(""));
    }

    public InternetAddress[] getAddressEnvelopeTo() throws AddressException {
        return InternetAddress.parse(Optional.ofNullable(headerEnvelopeTo).orElse(""));
    }

    public InternetAddress[] getAddressEnvelopeFromOrg() throws AddressException {
        return InternetAddress.parse(Optional.ofNullable(headerEnvelopeFromOrg).orElse(""));
    }

    public InternetAddress[] getAddressEnvelopeToOrg() throws AddressException {
        return InternetAddress.parse(Optional.ofNullable(headerEnvelopeToOrg).orElse(""));
    }

    // 以降、文字挿入用関数
    public void setToSubjectAddressFrom(String addressFrom) {
        subject = subject.replace("$af;", addressFrom);
    }

    public void setToSubjectId(String id) {
        subject = subject.replace("$id;", id);
    }

    public void setToSubjectSubject(String subject) {
        this.subject = this.subject.replace("$s;", subject);
    }

    public void setToTextId(String id) {
        text = text.replace("$id;", id);
    }

    public void setToTextExpirationTime(Date expirationTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日（E）");
        text = text.replace("$d;", sdf.format(expirationTime));
    }

    public void setToTextUrlLgwan(String url) {
        text = text.replace("$ul;", url);
    }

    public void setToTextUrlInternet(String url) {
        text = text.replace("$ui;", url);
    }

    public void setToTextLoginUrlLgwan(String url) {
        text = text.replace("$lul;", url);
    }

    public void setToTextLoginUrlInternet(String url) {
        text = text.replace("$lui;", url);
    }

    public void setToTextPasswordLgwan(String password) {
        text = text.replace("$pl;", password);
    }

    public void setToTextPasswordInternet(String password) {
        text = text.replace("$pi;", password);
    }

    public void setToTextAddressFrom(String addressFrom) {
        text = text.replace("$af;", addressFrom);
    }

    public void setToTextSubject(String subject) {
        text = text.replace("$s;", subject);
    }

    public void setToTextSendText(String sendText) {
        text = text.replace("$t;", sendText);
    }

    public void setToTextMailSanitizedMessage(String mailSanitizedMessage) {
        text = text.replace("$smsg;", mailSanitizedMessage);
    }

    public void setToTextFileNameList(List<String> fileNameList, String prefix, String indent, String separator) {
        String fileNames = StringUtils.join(fileNameList, separator + indent);
        text = text.replace("$flst;", prefix + fileNames);
    }

    public void setToTextDeletedFileNameList(List<String> fileNameList, String prefix, String indent, String separator, String header, String footer) {
        String fileNames = StringUtils.join(fileNameList, separator + indent);
        text = text.replace("$dflst;", header + prefix + fileNames + footer);
    }

    public void setToTextSanitizedFileNameList(List<String> fileNameList, String prefix, String indent, String separator, String header, String footer) {
        String fileNames = StringUtils.join(fileNameList, separator + indent);
        text = text.replace("$sflst;", header + prefix + fileNames + footer);
    }

    public void setToTextErroredFileNameList(List<String> fileNameList, String prefix, String indent, String separator, String header, String footer) {
        String fileNames = StringUtils.join(fileNameList, separator + indent);
        text = text.replace("$eflst;", header + prefix + fileNames + footer);
    }

    //[v2.2.3]
    public void setToTextBlockedArchiveFileNameList(List<String> fileNameList, String prefix, String indent, String separator, String header, String footer) {
        String fileNames = StringUtils.join(fileNameList, separator + indent);
        text = text.replace("$zbcflst;", header + prefix + fileNames + footer);
    }

    public void setToTextDecryptedFileNameList(List<String> fileNameList, String prefix, String indent, String separator, String header, String footer) {
        String fileNames = StringUtils.join(fileNameList, separator + indent);
        text = text.replace("$pflst;", header + prefix + fileNames + footer);
    }

    public void setToTextNoGoodFileNameList(List<String> fileNameList, String prefix, String indent, String separator, String header, String footer) {
        String fileNames = StringUtils.join(fileNameList, separator + indent);
        text = text.replace("$nglst;", header + prefix + fileNames + footer);
    }

    public void setToTextZipCharsetUnconvertedFileNameList(List<String> fileNameList, String prefix, String indent, String separator, String header, String footer) {
        String fileNames = StringUtils.join(fileNameList, separator + indent);
        text = text.replace("$zculst;", header + prefix + fileNames + footer);
    }

    public static boolean isDeletedFileNameList(String content, List<String> fileNameList) {
        return content.indexOf("$dflst;") > 0 && fileNameList.size() > 0;
    }

    public static boolean isSanitizedFileNameList(String content, List<String> fileNameList) {
        return content.indexOf("$sflst;") > 0 && fileNameList.size() > 0;
    }

    public static boolean isErroredFileNameList(String content, List<String> fileNameList) {
        return content.indexOf("$eflst;") > 0 && fileNameList.size() > 0;
    }

    //[v2.2.3]
    public static boolean isBlockedArchiveFileNameList(String content, List<String> fileNameList) {
        return content.indexOf("$zbcflst;") > 0 && fileNameList.size() > 0;
    }

    public static boolean isDecryptedFileNameList(String content, List<String> fileNameList) {
        return content.indexOf("$pflst;") > 0 && fileNameList.size() > 0;
    }

    public static boolean isNoGoodFileNameList(String content, List<String> fileNameList) {
        return content.indexOf("$nglst;") > 0 && fileNameList.size() > 0;
    }

    public static boolean isZipCharsetUnconvertedFileNameList(String content, List<String> fileNameList) {
        return content.indexOf("$zculst;") > 0 && fileNameList.size() > 0;
    }
}
