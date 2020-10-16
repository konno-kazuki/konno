package jp.co.fujielectric.fss.logic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import jp.co.fujielectric.fss.entity.MailQueue;
import jp.co.fujielectric.fss.entity.UploadGroupInfo;
import jp.co.fujielectric.fss.service.MailLostService.EnmMailLostFunction;
import jp.co.fujielectric.fss.servlet.MailLostServlet;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.JsonUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;

/**
 * Servletマネージャー
 */
@ApplicationScoped
public class ServletManager {

    @Inject
    private Logger LOG;

    private static final String SERVLET_MAILENTRANCE = "MailEntranceServlet";

    private static final String SERVLET_MAILERROR = "MailErrorServlet";

    private static final String SERVLET_MAILLOST = "MailLostServlet";
    
    /**
     * メール受信処理呼出
     * @param mq
     * @return  true:成功、false:解析エラーによりエラーメール送信済み
     * @throws IOException エラー発生（リトライまたはエラー処理が必要）
     */
    public boolean postEntrance(MailQueue mq) throws IOException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "mailId:" + mq.getId()));
        boolean result = false;

        List<NameValuePair> requestParams = new ArrayList<>();
        requestParams.add(new BasicNameValuePair("mailId", mq.getId()));
        requestParams.add(new BasicNameValuePair("mailDate", mq.getMailDate()));

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            String postUrl = CommonUtil.createLocalUrl(mq.getRegionId(), CommonUtil.isSectionLgwan(), false) + "mail/" + SERVLET_MAILENTRANCE;
            LOG.debug("---postEntrance (regionId:{}, postUrl:{}",mq.getRegionId(), postUrl);
            
            HttpPost httpPost = new HttpPost(postUrl);
            httpPost.setEntity(new UrlEncodedFormEntity(requestParams));
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                switch (response.getStatusLine().getStatusCode()) {
                    case HttpStatus.SC_OK:
                        // 成功
                        result = true;
                        //メール受信処理の成否をヘッダから取得する[v2.2.3]
                        { 
                            Header[] resHeader = response.getHeaders("Result");
                            if(resHeader != null && resHeader.length > 0){
                                String resValue = resHeader[0].getValue();
                                if(!"0".equals(resValue)){
                                    //0以外がセットされていたら解析エラーによりエラーメール送信済みとする
                                    result = false;
                                }
                                LOG.debug("#postEntrance HttpResponseHeader:[{}], result:{}", resValue, result);
                            }
                        }
                        LOG.trace("postEntrance HTTP通信[Success](" + mq.getId() + ")");
                        break;
                    default:
                        LOG.error("postEntrance HTTP通信[Failure](" + mq.getId() + ")" + response.toString());
                        throw new IOException("ServletManager.postEntrance Process Error");
                }
            }
        }
        requestParams.clear();
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "mailId:" + mq.getId()));
        return result;
    }

    public boolean postEntranceError(MailQueue mq) throws IOException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "mailId:" + mq.getId()));
        boolean result = false;

        List<NameValuePair> requestParams = new ArrayList<>();
        requestParams.add(new BasicNameValuePair("mailId", mq.getId()));
        requestParams.add(new BasicNameValuePair("mailDate", mq.getMailDate()));

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPost httpPost = new HttpPost(CommonUtil.createLocalUrl(mq.getRegionId(), CommonUtil.isSectionLgwan(), false) + "mail/" + SERVLET_MAILERROR);
            httpPost.setEntity(new UrlEncodedFormEntity(requestParams));
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                switch (response.getStatusLine().getStatusCode()) {
                    case HttpStatus.SC_OK:
                        // 成功
                        HttpEntity entity = response.getEntity();
                        String httpResult = EntityUtils.toString(entity, StandardCharsets.UTF_8);
                        LOG.debug(httpResult);
                        LOG.trace("postEntranceError HTTP通信[Success](" + mq.getId() + ")");
                        result = true;
                        break;
                    default:
                        throw new Exception("postEntranceError HTTP通信[Failure](response:" + response.toString() + ")");
                }
            }
        }catch(Exception ex){
            // MailLostを記録するためExceptionをスローしない。
            LOG.error("postEntranceError HTTP通信[Failure](" + mq.getId() + ")" + ex.getMessage(), ex);
        }
        requestParams.clear();
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "mailId:" + mq.getId()));
        return result;
    }

    /** [v2.2.1]削除
     * 
    public boolean postCheckStatus(UploadGroupInfo gi) throws IOException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "regionId:" + gi.getRegionId()));
        boolean result = false;

        List<NameValuePair> requestParams = new ArrayList<>();
        requestParams.add(new BasicNameValuePair("uploadGroupInfo", JsonUtil.fromObject(gi)));

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            String postUrl = CommonUtil.createLocalUrl(gi.getRegionId(), CommonUtil.isSectionLgwan()) + "mail/VotiroProcessServlet";
            LOG.debug("---postCheckStatus postUrl:{}",postUrl);
            HttpPost httpPost = new HttpPost(postUrl);
            httpPost.setEntity(new UrlEncodedFormEntity(requestParams));
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                switch (response.getStatusLine().getStatusCode()) {
                    case HttpStatus.SC_OK:
                        // 成功
                        HttpEntity entity = response.getEntity();
                        String httpResult = EntityUtils.toString(entity, StandardCharsets.UTF_8);
                        LOG.debug(httpResult);
                        LOG.trace("[Success] HTTP通信");
                        result = true;
                        break;
                    case HttpStatus.SC_NO_CONTENT:
                        LOG.trace("[Processing] HTTP通信");
                        break;
                    default:
                        LOG.trace("[Failure] HTTP通信");
                        throw new IOException("ServletManager.postCheckStatus Process Error");
                }
            }
        }
        requestParams.clear();

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "regionId:" + gi.getRegionId()));
        return result;
    }
    */

    public boolean postComplete(UploadGroupInfo gi) throws IOException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "regionId:" + gi.getRegionId()));
        boolean result = false;

        List<NameValuePair> requestParams = new ArrayList<>();
        requestParams.add(new BasicNameValuePair("uploadGroupInfo", JsonUtil.fromObject(gi)));

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPost httpPost = new HttpPost(CommonUtil.createLocalUrl(gi.getRegionId(), CommonUtil.isSectionLgwan()) + "mail/VotiroCompleteServlet");
            httpPost.setEntity(new UrlEncodedFormEntity(requestParams));
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                switch (response.getStatusLine().getStatusCode()) {
                    case HttpStatus.SC_OK:
                        // 成功
                        HttpEntity entity = response.getEntity();

                        String httpResult = EntityUtils.toString(entity, StandardCharsets.UTF_8);

                        LOG.debug(httpResult);
                        LOG.trace("[Success] HTTP通信");
                        result = true;
                        break;
                    default:
                        LOG.trace("[Failure] HTTP通信");
                        throw new IOException("ServletManager.postComplete Process Error");
                }
            }
        }
        requestParams.clear();

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "regionId:" + gi.getRegionId()));
        return result;
    }

    /**
     * メールロスト記録Servlet呼出し 
     * @param regionId
     * @param flgEncording
     * @param mailLostFunc
     * @param sendInfoId
     * @param recvInfoId
     * @param upldGrpInfoId
     * @return
     */
    public boolean postMailLost(String regionId, boolean flgEncording, EnmMailLostFunction mailLostFunc, String sendInfoId, String recvInfoId, String upldGrpInfoId) {
        boolean result = false;

        //メールロスト時のWarnログ出力
        switch (mailLostFunc) {
            case MailEntrance:
                LOG.warn("##### MailLost. (mailqueue,cancelflg=true,{})", sendInfoId);
                break;
            case VotiroEntrance:
                LOG.warn("##### MailLost. (mailqueue,cancelflg=true,{})", upldGrpInfoId);
                break;
            default:
                LOG.warn("##### MailLost. (uploadgroupinfo,cancelflg=true,{})", upldGrpInfoId);
                break;
        }
                        
        List<NameValuePair> requestParams = new ArrayList<>();
        requestParams.add(new BasicNameValuePair(MailLostServlet.PARAM_FUNC_KBN, mailLostFunc.name()));
        requestParams.add(new BasicNameValuePair(MailLostServlet.PARAM_SENDINFO_ID, sendInfoId));
        requestParams.add(new BasicNameValuePair(MailLostServlet.PARAM_RECVINFO_ID, recvInfoId));
        requestParams.add(new BasicNameValuePair(MailLostServlet.PARAM_UPLDGRPINFO_ID, upldGrpInfoId));

        String postUrl;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            postUrl = CommonUtil.createLocalUrl(regionId, CommonUtil.isSectionLgwan(), flgEncording) + "mail/" + SERVLET_MAILLOST;
            LOG.debug("---postMailLost postUrl:{}",postUrl);

            HttpPost httpPost = new HttpPost(postUrl);
            httpPost.setEntity(new UrlEncodedFormEntity(requestParams));
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int httpStatus = response.getStatusLine().getStatusCode();
                switch (httpStatus) {
                    case HttpStatus.SC_OK:
                        // 成功
                        LOG.debug("postMailLost HTTP通信[Success](regionId:{}, mailLostFunc:{}, sendInfoId:{}, recvInfoId:{}, upldGrpInfoId:{} "
                                ,regionId, mailLostFunc.name(), sendInfoId, recvInfoId, upldGrpInfoId);
                        result = true;
                        break;
                    default:
                        throw new Exception("postMailLost HTTP通信[Failure] ( posUrl:"+ postUrl 
                                + ", httpStatus:" + httpStatus
                                + ", regionId:" + regionId
                                + ", mailLostFunc:" + mailLostFunc.name()
                                + ", sendInfoId:" + sendInfoId
                                + ", recvInfoId:" + recvInfoId
                                + ", upldGrpInfoId:" + upldGrpInfoId
                                + ", respons:" + response.toString()
                                + ")");
                }
            }
        }catch(Exception ex){
            LOG.error("MailLostServletの呼出で失敗しました。", ex);
        }
        requestParams.clear();
        return result;
    }

}
