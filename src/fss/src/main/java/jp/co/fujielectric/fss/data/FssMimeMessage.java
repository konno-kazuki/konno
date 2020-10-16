/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.data;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * 無害化サービス用MimeMessage
 */
public class FssMimeMessage extends MimeMessage{
    @Setter
    protected String originalMessageId;

    public FssMimeMessage(Session session) {
        super(session);
    }

    public FssMimeMessage(Session session, InputStream is) throws MessagingException {
        super(session, is);
    }

    public FssMimeMessage(MimeMessage source) throws MessagingException {
        super(source);
    }

    @Override
    protected void updateMessageID() throws MessagingException {
        if(StringUtils.isEmpty(originalMessageId)) {
            super.updateMessageID();
        } else {
            setHeader("Message-ID", originalMessageId);
        }
    }

    /**
     * Subjectのエンコード文字の直前に文字があった場合にデコードされない症状の対応
     * @return Subjec
     */
    @Override
    public String getSubject() {
        try{
            String subject;
            String[] hValues = getHeader("Subject");    //ヘッダー設定値（エンコード文字列）を取得
            if(hValues != null && hValues.length > 0){
                //デコード文字列取得（MimeUtilityで正常にデコードされないので対応）
                subject = decodeMimeString(hValues[0]);                
            }else{
                //getHeaderでSubjectが取得できない場合、通常のgetSubjectで件名取得（通常はありえない）
                subject = super.getSubject();
            }
            if(subject == null){
                //念のため、nullだった場合は""とする。
                subject = "";
            }
            return subject;
        }catch(Exception e){
            //件名の取得に失敗した場合は""を返す
            return "";  
        }
    }
    
    public String getSubjectOrg() throws MessagingException {
        return super.getSubject();
    }    

    /**
     * MimeUtilityで対応できないデコードの調整
     * @param mimeString
     * @return 
     */
    public static String decodeMimeString(String mimeString) {
        if(mimeString == null)
            return null;
        //---------
        // ※RFC2047形式のエンコードで以下の問題があるが、この処理で改善される
        // ①エンコード開始文字の直前に文字があるとデコードされない。
        // ②エンコード開始文字の直前にスペースを入れるとデコードされる。
        //   ただし
        //   ・１つめのエンコードブロックは、直前のスペースはデコード後もスペースのまま。
        //   ・２つめ以降はデコードすると直前のスペースが削除される。
        //---------

        mimeString = mimeString.replace("\r", "");                          // 改行コード除去
        mimeString = mimeString.replace("\n", "");                          // 改行コード除去
        mimeString = mimeString.replace("?= =?", "?==?");                   // エンコード分割の空白除去

        // [2017/05/11] MimeUtilityで変換できないascii＋RFC2231の対応を含み関数化
        Pattern pattern = Pattern.compile("=\\?([^?]+)\\?([^?]+)\\?([^?]+)\\?=");
        Matcher matcher = pattern.matcher(mimeString);
        ArrayList<String> encodedParts = new ArrayList<>();
        while (matcher.find()) {
            encodedParts.add(matcher.group(0));
        }
        try {
            for (String encoded : encodedParts) {
                mimeString = mimeString.replace(encoded, MimeUtility.decodeText(encoded));
            }
        } catch (Exception ex) {
        }
        //0x00が含まれている場合にエラー原因となるため0x00を除去
        if(mimeString != null && mimeString.contains("\0")){
            mimeString = mimeString.replaceAll("\0", "");
        }
        return mimeString;
    }    
}
