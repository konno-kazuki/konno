/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.servlet;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.apache.commons.codec.binary.Base64;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.fujielectric.fss.common.AppTrace;

import org.apache.logging.log4j.Logger;

/**
 *
 */
@Named
@RequestScoped
@WebServlet("/sampleServlet")
public class SampleServlet extends HttpServlet{
    @Inject
    private Logger LOG;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        System.out.println("sampleServlet.doGet()");
        LOG.info("sampleServlet.doGet()");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        System.out.println("sampleServlet.doPost()");
        LOG.info("sampleServlet.doPost()");

        // こんな感じでメール情報が取れると思う。。。
        String mailInfo = req.getParameter("mailInfo");
        byte[] decoded = Base64.decodeBase64(mailInfo);//デコード処理
        String meilInfoDC = new String(decoded);//デコード結果のbyte[]を文字列にする

        System.out.println(meilInfoDC);

        try {
            InputStream in = new ByteArrayInputStream(meilInfoDC.getBytes("utf-8"));;
            Session session = Session.getDefaultInstance(new java.util.Properties(), null);
            MimeMessage message = new MimeMessage(session, in);
            // 送信元
            System.out.println(message.getFrom()[0]);
            // 宛先
            System.out.println(message.getRecipients(Message.RecipientType.TO)[0]);
            // 件名
            System.out.println(message.getSubject());
            // 本文
            System.out.println(message.getContent().toString());

//            // 添付ファイルを実体化
//            // （注意）postfixがマルチパートの[Content-Description: Undelivered Message]にメール情報を閉じ込めてしまう
//            //        調査中のため、一時的に３つ目のメール情報を抜き出して処理を継続していることに注意！
//            final Object objContent = message.getContent();
//            if (objContent instanceof Multipart) {
//                final Multipart multiPart = (Multipart) objContent;
//                if (multiPart.getCount() == 3) {
//                    final Part part = multiPart.getBodyPart(2);
//                    InputStream is = part.getInputStream();
//                    message = new MimeMessage(session, is);
//                }
//            }
            // ↑上記はメールエラーによる転送メールで、本来は発生しないケースだった。
            createMailFiles(message);
        } catch (Exception ex) {
            LOG.error(ex.getMessage());
        }
    }

    /**
     * 添付ファイルを出力する.
     */
    @AppTrace()
    private void createMailFiles(Message message) throws Exception {
        final Object objContent = message.getContent();
        if (objContent instanceof Multipart) {
            final Multipart multiPart = (Multipart) objContent;
            for (int indexPart = 0; indexPart < multiPart.getCount(); indexPart++) {
                final Part part = multiPart.getBodyPart(indexPart);
                final String disposition = part.getDisposition();
                if (Part.ATTACHMENT.equals(disposition) || Part.INLINE.equals(disposition)) {
                    String fileName = part.getFileName();
                    if (fileName != null) {
                        fileName = MimeUtility.decodeText(fileName);
                        
                        LOG.debug(fileName);
                        
                        InputStream is = part.getInputStream();
                        // ファイル名が重複しても出力名がかぶらないようにUUIDを使用
                        String outputDir = "/tmp/mailFiles/";
//                        String outputDir = "C:\\Temp\\";
                        String uuid = UUID.randomUUID().toString();

                        LOG.debug(outputDir + fileName + "_" + uuid);
                        
                        File file = new File(outputDir + fileName + "_" + uuid);

                        createFileWithInputStream(is, file);
                        is.close();
                    }
                }
            }
        }
    }

    /**
     * 添付ファイルを出力する.
     */
    @AppTrace(loggingLevel = AppTrace.LoggingLevel.DEBUG)
    void createFileWithInputStream(InputStream inputStream, File destFile) throws IOException {
        byte[] buffer = new byte[1024];
        int length = 0;
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(destFile);
            while ((length = inputStream.read(buffer)) >= 0) {
                fos.write(buffer, 0, length);
            }

            fos.close();
            fos = null;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
