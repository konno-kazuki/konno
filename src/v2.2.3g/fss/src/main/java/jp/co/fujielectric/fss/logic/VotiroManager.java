package jp.co.fujielectric.fss.logic;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import jp.co.fujielectric.fss.data.Tuple;
import jp.co.fujielectric.fss.data.VotStatus;
import jp.co.fujielectric.fss.data.VotUploadResult;
import jp.co.fujielectric.fss.exception.FssException;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.FileUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;

/**
 * Votiroマネージャー
 */
@ApplicationScoped
public class VotiroManager {

    private static final String COOKIE_NAME_LOADBALANCE = "sdsip";

    @Inject
    private Logger LOG;

    //[UT用]
    public void setLogger(Logger log)
    {
        LOG = log;
    }
    
    /**
     * Votiroへのアップロード処理
     * @param filename （アップロードファイル名）
     * @param is （アップロードファイル実態）
     * @param length （アップロードファイルサイズ）
     * @return Tuple(Votiroから返ったRequestID, VotiroSDS宛先IPアドレス)
     * @throws IOException
     * @throws FssException HttpStatus.SC_OK以外のステータス受信時
     */
    public Tuple<String, String> postUploadFile(String filename, InputStream is, long length) throws IOException, FssException {
        try{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "filename:" + filename));

            Tuple<String, String> tuple = new Tuple<>(null, null);
            String postUrl = "";

            // リクエストから1分経過で強制エラー
            RequestConfig requestConfig = RequestConfig.custom()
                    .setSocketTimeout(1 * 60 * 1000)
                    .setConnectTimeout(1 * 60 * 1000)
                    .setConnectionRequestTimeout(1 * 60 * 1000)
                    .build();

            // Cookie格納用の定義
            CookieStore cookieStore = new BasicCookieStore();

            // Httpクライアントの作成
            // ※処理終了後に自動close
            try(CloseableHttpClient httpClient = HttpClientBuilder.create()
                    .setDefaultRequestConfig(requestConfig)
                    .setDefaultCookieStore(cookieStore)
                    .build()){

                // requestの作成
                postUrl = CommonUtil.createVotiroUrl() + "upload/file" + "?fileName=" + URLEncoder.encode(filename, "UTF-8")
                        + CommonUtil.getSetting("votiroParameter");
                HttpPost httpPost = new HttpPost(postUrl);
                LOG.debug("#Votiro Upload postUrl:{}", postUrl);

                ContentType contentType = ContentType.DEFAULT_BINARY;
                // (シングルパート バイナリエンティティ)
                InputStreamEntity requestEntity = new InputStreamEntity(is, length, contentType);
                httpPost.setEntity(requestEntity);

                // HttpResponseの取得
                // ※処理終了後に自動close
                try(CloseableHttpResponse response = httpClient.execute(httpPost)){
                    int statusCode = response.getStatusLine().getStatusCode();
                    LOG.debug("#Votiro Upload. StatusCode:{} FileName:{}", statusCode, filename);
                    if (statusCode == HttpStatus.SC_OK) {
                        // 成功 statusCode==200
                        HttpEntity entity = response.getEntity();

                        // リターン値の解析
                        ObjectMapper mapper = new ObjectMapper();
                        VotUploadResult vur = mapper.readValue(
                                EntityUtils.toString(entity, StandardCharsets.UTF_8),
                                VotUploadResult.class);
                        LOG.trace("#Votiro Upload Return AllValues. :"
                                + "{}", ReflectionToStringBuilder.toString(vur, ToStringStyle.MULTI_LINE_STYLE));

                        tuple.setValue1(vur.RequestID);

                        // クッキー値の取得
                        List<Cookie> cookies = cookieStore.getCookies();
                        if(cookies != null) {
                            for(Cookie cookie : cookies) {
                                if(     cookie != null
                                     && cookie.getName() != null
                                     && cookie.getName().equals(COOKIE_NAME_LOADBALANCE)) {
                                    tuple.setValue2(cookie.getValue());
                                }
                            }
                        }
                    } else {
                        //ステータスコード=200以外はエラーとして検知済み例外スローでステータスコードを知らせる
                        //ステータスコード毎の処理分岐は呼出し側で行う。
                        throw new FssException(statusCode);
                    }
                }
            }

            LOG.debug("#Votiro Upload Complete. RequestID:{}, Cookie(sdsip):{}", tuple.getValue1(), tuple.getValue2());
            return tuple;
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));            
        }
    }

    /**
     * Votiroステータス確認
     * @param requestId リクエストID
     * @param sdsip （VotiroSDS宛先IPアドレス）
     * @return Votiroステータス
     * @throws FssException HttpStatus.SC_OK以外のステータス受信時
     * @throws IOException
     */
    public VotStatus getStatus(String requestId, String sdsip) throws FssException, IOException {
        try{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "requestId:" + requestId, "sdsip:" + sdsip));

            VotStatus vs = null;

                // リクエストから1分経過で強制エラー
            RequestConfig requestConfig = RequestConfig.custom()
                    .setSocketTimeout(1 * 60 * 1000)
                    .setConnectTimeout(1 * 60 * 1000)
                    .setConnectionRequestTimeout(1 * 60 * 1000)
                    .build();

            // Cookie格納用の定義
            CookieStore cookieStore = new BasicCookieStore();

            // requestの作成
            try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                    .setDefaultRequestConfig(requestConfig)
                    .setDefaultCookieStore(cookieStore)
                    .build()) {
                HttpGet httpGet = new HttpGet(CommonUtil.createVotiroUrl() + "file/" + requestId + "/status");

                // Cookie情報(sdsip)のセット
                BasicClientCookie cookie = new BasicClientCookie(COOKIE_NAME_LOADBALANCE, sdsip);
                cookie.setDomain(httpGet.getURI().getHost());
                cookie.setPath("/");
                cookieStore.addCookie(cookie);

                // responseの取得
                try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    LOG.debug("#Votiro getStatus. StatusCode:{} RequestId:{} sdsip:{}", statusCode, requestId, sdsip);                
                    if (statusCode == HttpStatus.SC_OK) {
                        // 成功
                        HttpEntity entity = response.getEntity();

                        ObjectMapper mapper = new ObjectMapper();

                        vs = mapper.readValue(
                                EntityUtils.toString(entity, StandardCharsets.UTF_8),
                                VotStatus.class);
                        LOG.debug(ReflectionToStringBuilder.toString(vs, ToStringStyle.MULTI_LINE_STYLE));
                    } else {
                        //ステータスコード=200以外はエラーとして検知済み例外スローでステータスコードを知らせる
                        throw new FssException(statusCode);
                    }
                }
            }

            return vs;
        }finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }

    /**
     * 
     * Votiroダウンロード
     * @param requestId リクエストID
     * @param filePath
     * @param sdsip （VotiroSDS宛先IPアドレス）
     * @throws FssException HttpStatus.SC_OK以外のステータス受信時
     * @throws java.io.IOException 
     */
    public void getDownload(String requestId, String filePath, String sdsip) throws FssException, IOException {
        try{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "requestId:" + requestId, "sdsip:" + sdsip));

            RequestConfig requestConfig = RequestConfig.custom()
                    .setSocketTimeout(1 * 60 * 1000)
                    .setConnectTimeout(1 * 60 * 1000)
                    .setConnectionRequestTimeout(1 * 60 * 1000)
                    .build();

            // Cookie格納用の定義
            CookieStore cookieStore = new BasicCookieStore();

            // requestの作成
            try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                    .setDefaultRequestConfig(requestConfig)
                    .setDefaultCookieStore(cookieStore)
                    .build()) {
                HttpGet httpGet = new HttpGet(CommonUtil.createVotiroUrl() + "file/" + requestId);

                // Cookie情報(sdsip)のセット
                BasicClientCookie cookie = new BasicClientCookie(COOKIE_NAME_LOADBALANCE, sdsip);
                cookie.setDomain(httpGet.getURI().getHost());
                cookie.setPath("/");
                cookieStore.addCookie(cookie);

                // responseの取得
                try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                    int statusCode = response.getStatusLine().getStatusCode();             
                    if (statusCode == HttpStatus.SC_OK) {
                        // 成功
                        HttpEntity entity = response.getEntity();
                        InputStream is = entity.getContent();
                        FileUtil.saveFile(is, filePath);
                    } else {
                        //ステータスコード=200以外はエラーとして検知済み例外スローでステータスコードを知らせる
                        throw new FssException(statusCode); //例外スロー                    
                    }
                }
            }
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }

    /**
     * Votiro無害化時のレポート取得
     * @param requestId リクエストID
     * @param filePath
     * @param sdsip （VotiroSDS宛先IPアドレス）
     * @throws IOException
     * @throws FssException HttpStatus.SC_OK以外のステータス受信時
     */
    public void getReport(String requestId, String filePath, String sdsip) throws IOException, FssException {
        //レポートファイルのダウンロードは、requestIdに"/report"を付加するだけでOK
        getDownload(requestId + "/report", filePath, sdsip);
    }
}
