package jp.co.fujielectric.fss.servlet;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Timer;
import java.util.TimerTask;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.logging.log4j.Logger;
import jp.co.fujielectric.fss.exception.FssException;
import jp.co.fujielectric.fss.util.CommonUtil;

//@ServerEndpoint(
//        value = "/{region_id}/ws/syncdb",
//        encoders = { WebSocketEncoder.class },
//        decoders = { WebSocketDecoder.class })
@ServerEndpoint("/{region_id}/ws/syncfile")
public class SyncFilesServer {
    /**
     * タイマースケジュール時間
     */
    private final int TIMEOUT_DEFAULT = 30 * 60;    //タイムアウトデフォルト値（30分）
    /**
     * 強制クローズのタイムアウトからの遅延時間 [v2.2.5a]
     */
    private final long TIMEOUT_DELAY_DEFAULT = 70;   //タイムアウト後強制クローズ遅延時間 デフォルト値（70秒）
    /**
     * 強制クローズ用タイマー [v2.2.5a]
     */
    private Timer timer = null;
    /**
     * 排他ロック処理用オブジェクト
     */
    private final Object lock = new Object();    
    /**
     * 受信サイズ累計
     * ファイル名、またはデータを最初に受信した時点で0をセットする。(データを先に受信するのは異常ケース）
     */
    private long fileSize = -1;
    /**
     * セッションID
     */
    private String sessionId = "";
    
    //ファイル受信完了時のメッセージ
    public static final String SESSION_MESSAGE_COMPLETE = "complete";

    private String filePath = "";
    volatile private FileOutputStream fileOutputStream;

    
    
    @Inject
    private Logger LOG;

    /**
     * ファイル名通知受信
     */
    @OnMessage
    public void OnMessage(Session session, String path, @PathParam("region_id") String regionId) {
        LOG.debug("SyncFilesServer:OnMessage[path] (session.id:{}, regionId:{}, File:{})"
                , session.getId(), regionId, path);
        try {
            //TODO :::UT:::Start v2.2.5a SyncFile
            if(VerifyUtil.UT_MODE){
                //Sleepテスト（ファイル同期受信側で処理遅延したケースをシミュレート）
                int utSleep = VerifyUtil.getUTArgValueInt(path, "#UT225#", "PSLP", 0);   //Sleep取得
                if(utSleep > 0){
                    VerifyUtil.outputUtLog(LOG, "", false, "#SyncFilesServer: OnMessage[path] Sleep(%d)", utSleep);
                    for(int i=0; i<utSleep; i++)
                        Thread.sleep(1000);
                }
                //例外発生テスト（ファイル同期受信側で例外発生したケースをシミュレート）
                if(VerifyUtil.chkUTKey(path, "#UT225#", "PEX")){
                    throw new RuntimeException("#UT#OnMessage[path] 模擬例外発生!");    //例外発生
                }
                //異常状態発生テスト（ファイル同期受信側でファイル名を受信しない状況をシミュレート）
                if(VerifyUtil.chkUTKey(path, "#UT225#", "NORECVP")){
                    VerifyUtil.outputUtLog(LOG, "", false, "SyncFileServer: OnMessage[path] ファイル名通知を無視");
                    return;
                }                
            }
            //TODO :::UT:::End v2.2.5a SyncFile            
            
            filePath = path;
            File file = new File(filePath);
            if (file.exists()) file.delete();                                   // 前回の失敗ファイルがある場合は削除
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();  // フォルダがない場合は作成
            fileOutputStream = new FileOutputStream(filePath, true);
            fileSize = 0;
        } catch (Throwable ex) {
            //クローズ等の後始末
            //受信側のエラーでSessionがCloseされたことがわかるようにCloseReasonnを指定してCloseする。
            clean(session, "OnMessage Path.(" + ex.toString() + ")");
            
            LOG.error("#! SyncFileServer#OnMessage[path] Error. (session.id:{}, regionId:{}, File:{}) ErrMsg:{}",
                    session.getId(), regionId, path, ex.toString(), ex);
//            System.err.println("SyncFiles error:" + ex.getMessage());
            throw new RuntimeException("SyncFiles error:" + ex.getMessage());
        }
    }

    /**
     * ファイルデータ受信
     * @param session
     * @param bytes
     * @param isLast
     * @param regionId 
     */
    @OnMessage
    public void OnMessage(Session session, byte[] bytes, boolean isLast, @PathParam("region_id") String regionId) {
        if (fileOutputStream == null){
            if(fileSize < 0){
                //このケースはあり得ないのでWarnログを出力する.（初回だけ） [v2.2.5a]
                LOG.warn("SyncFileServer:OnMessage Warn. ファイル名未受信(session.id:{}, bytes:{}, isLast:{}, regionId:{})",
                    session.getId(), (bytes == null ? 0 : bytes.length), isLast, regionId);
                fileSize = 0;
            }
            return;
        }
        try {
            //タイムアウトで既にSessionがクローズされている場合は、エラーなので処理を無駄に継続しない
            if(!session.isOpen()){
                throw new FssException("タイムアウト等によりセッションが閉じられたためファイル同期処理を中止します。");
            }

            //TODO :::UT:::Start v2.2.5a SyncFile
            if(VerifyUtil.UT_MODE){
                if(isLast){
                    //Sleepテスト（ファイル同期受信側で処理遅延したケースをシミュレート）
                    int utSleep = VerifyUtil.getUTArgValueInt(filePath, "#UT225#", "BSLP", 0);   //Sleep取得
                    if(utSleep > 0){
                        VerifyUtil.outputUtLog(LOG, "", false, "#SyncFilesServer: OnMessage[data] Sleep[last](%d) Size:(%d) ", utSleep, fileSize);
                        for(int i=0; i<utSleep; i++)
                            Thread.sleep(1000);
                    }
                    //例外発生テスト（ファイル同期受信側で例外発生したケースをシミュレート）
                    if(VerifyUtil.chkUTKey(filePath, "#UT225#", "BEX")){
                        throw new RuntimeException("#UT#模擬例外発生!");    //例外発生
                    }
                    //異常状態発生テスト（ファイル同期受信側でデータ受信しない状況をシミュレート）
                    if(VerifyUtil.chkUTKey(filePath, "#UT225#", "NORECVB")){
                        if(isLast)
                            VerifyUtil.outputUtLog(LOG, "", false, "SyncFilesServer:OnMessage: データ受信を無視");
                        return;
                    }
                }else if((fileSize + bytes.length) / (1024 * 1024) > fileSize / (1024 * 1024)){
                    //1MByte毎のSleepテスト（ファイル同期受信側で処理遅延したケースをシミュレート）
                    int utSleep = VerifyUtil.getUTArgValueInt(filePath, "#UT225#", "BSLP1", 0);   //Sleep取得
                    if(utSleep > 0){
                        VerifyUtil.outputUtLog(LOG, "", false, "#SyncFilesServer: OnMessage[data] Sleep[/MB](%d) Size:(%d) ", utSleep, fileSize);
                        for(int i=0; i<utSleep; i++)
                            Thread.sleep(1000);
                    }
                }
            }
            //TODO :::UT:::End v2.2.5a SyncFile

            //ログ出力（先頭、1MByte毎にログ出力）  [v2.2.5a]
            if(fileSize == 0){
                LOG.debug("SyncFilesServer:OnMessage: Start(session.id:{}, File:{}, Size:{})", 
                        session.getId(), filePath, bytes.length);    //最初のデータ受信時のログ出力 [v2.2.5a]
            }else if((fileSize + bytes.length) / (1024 * 1024) > fileSize / (1024 * 1024)){
                LOG.debug("SyncFilesServer:OnMessage: Receive(session.id:{}, File:{}, Size:{})", 
                        session.getId(), filePath, fileSize + bytes.length);    //1MBtye単位のログ出力  [v2.2.5a]
            }
            fileSize += bytes.length;   //ファイルサイズ累計
            synchronized (lock) {   //fileOutputSreamへのアクセスがTimerスレッドと競合しないように排他制御する
                if(fileOutputStream != null){
                    fileOutputStream.write(bytes);
                    if (isLast) {
                        LOG.debug("SyncFilesServer:OnMessage: End(session.id:{}, File:{}, Size:{})", 
                                session.getId(), filePath, fileSize);
                        fileOutputStream.flush();                                       // 強制的にバッファを書き込み
                        fileOutputStream.close();
                        fileOutputStream = null;
                        session.getBasicRemote().sendText(SESSION_MESSAGE_COMPLETE);                  // clientへ完了通知
                        LOG.debug("SyncFilesServer:OnMessage: SendCompleteMessage(session.id:{}, File:{})", 
                                session.getId(), filePath); //完了通知ログ　[v2.2.5a]
                        session.close();
                        LOG.debug("SyncFilesServer:OnMessage: CloseSession(session.id:{}, File:{})", 
                                session.getId(), filePath); //セッションクローズログ　[v2.2.5a]                    
                        timer.cancel();
                        timer = null;
                    }
                }
            }
        } catch (Throwable ex) {
            //クローズ等の後始末
            //受信側のエラーでSessionがCloseされたことがわかるようにCloseReasonnを指定してCloseする。
            clean(session, "OnMessage Bytes.(" + ex.toString() + ")");
            
            LOG.error("#! SyncFileServer#OnMessage[bytes] Error.  (session.id:{}, regionId:{}, isLast:{}, bytes.length:{}, filePath:{}) ErrMsg:{}",
                session.getId(), regionId, isLast, (bytes == null ? 0 : bytes.length), filePath, ex.toString(), ex);
//            System.err.println("SyncFilesServer error:" + ex.getMessage());
            throw new RuntimeException("SyncFilesServer error:" + ex.getMessage());
        }
    }
    
    @OnOpen
    public void onOpen(Session session, @PathParam("region_id") String regionId) {
        LOG.debug("SyncFilesServer:onOpen (session.id:{}, regionId:{})", session.getId(), regionId);
        sessionId = session.getId();
        
        //タイマーのセット
        setTimer(session, regionId);
    }

   
    
    @OnError
    public void onError(Session session, Throwable throwable, @PathParam("region_id") String regionId) throws Exception {
        LOG.warn("SyncFilesServer:onError (session.id:{}, regionId:{}, throwable={})"
                , session.getId(), regionId, throwable.toString());
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason, @PathParam("region_id") String regionId) {
        try {
            if(closeReason.getCloseCode() != CloseReason.CloseCodes.NORMAL_CLOSURE){
                //CloseReasonが正常クローズ以外の場合、WARNログ出力する
                LOG.debug("SyncFilesServer:onClose (session.id:{}, regionId:{}, closeReason:{}, closeReason.Code:{})",
                    session.getId(), regionId, closeReason.toString(), closeReason.getCloseCode().toString());
            }else{
                LOG.debug("SyncFilesServer:onClose (session.id:{}, regionId:{}, closeReason:{}, closeReason.Code:{})",
                    session.getId(), regionId, closeReason.toString(), closeReason.getCloseCode().toString());
            }
            
            //後始末
            clean(session, "");
            
        } catch (Exception e) {
            LOG.error("#! SyncFilesServer:onClose Error. ({})",e.toString(), e);
        }
    }
        
//    // encoders, decodersを使ったテストソース
//    private ByteArrayOutputStream byteArrayOut = null;
//    @OnMessage
//    public WebSocketData OnMessage(Session session, WebSocketData webSocketData, boolean isLast, @PathParam("region_id") String regionId) {
//        System.out.println("SyncFiles OnMessage:" + regionId + ", " + webSocketData.getPath() + "(index:" + String.valueOf(webSocketData.getIndex()) + ")");
//
//        if (byteArrayOut == null) {
//            byteArrayOut = new ByteArrayOutputStream();
//        }
//        try {
//            byteArrayOut.write(webSocketData.getDataPart());
//        } catch (IOException ex) {
//            webSocketData.setResult("1");
//            webSocketData.setResultMessage("SyncFiles error:" + ex.getMessage());
//            return webSocketData;
//        }
//        if (isLast) {
//            try {
//                FileOutputStream fileOutputStream = new FileOutputStream(webSocketData.getPath() + "\\bbb.zip");
//                fileOutputStream.write(webSocketData.getDataPart());
//                fileOutputStream.close();
//            } catch (IOException ex) {
//            }
//
//            try {
//                byteArrayOut.close();
//            } catch (IOException ex) {
//                webSocketData.setResult("9");
//                webSocketData.setResultMessage("SyncFiles error:" + ex.getMessage());
//                return webSocketData;
//            }
//            byteArrayOut = null;
//
//            webSocketData.setResult("0");
//            webSocketData.setResultMessage("ファイル同期完了");
//        } else {
//            webSocketData.setResult("0");
//            webSocketData.setResultMessage("通信完了（継続）");
//        }
//
//        return webSocketData;
//    }
    
    /**
     * タイマーのセット[v2.2.5a]
     * @param session 
     */
    private void setTimer(Session session, String regionId){
        try {
            //タイムアウト値取得 （単位：秒）
            long timeoutDelay = CommonUtil.getSettingInt("syncFile_timeout", TIMEOUT_DEFAULT);
            timeoutDelay += TIMEOUT_DELAY_DEFAULT;  //タイムアウト値+遅延値（60秒)
            
            //TODO :::UT:::Start v2.2.5a SyncFile
            if(VerifyUtil.UT_MODE){
                //テスト用タイムアウト時間設定
                regionId=CommonUtil.decodeBase64(regionId);
                int utTimeout = VerifyUtil.getUTArgValueInt(regionId, "#UT225#", "TIMER", -1);
                if(utTimeout != -1){
                    timeoutDelay = utTimeout;
                }
            }
            //TODO :::UT:::End v2.2.5a SyncFile

            LOG.debug("SyncFilesServer:setTimer (session.id:{}, regionId:{}, timerDelay:{})", session.getId(), regionId, timeoutDelay);
            if(timeoutDelay <= 0){
                //タイムアウトに0以下がセットされている場合はTimer設定はしない
                return;
            }
            //クローズされない状況を回避するため、Timerでクローズ処理をスケジュールする。　[v2.2.5a]
            TimerTask task = new TimerTask() {
                //タイマー発動時の処理
                @Override
                public void run() {
                    LOG.warn("#! SyncFileServer:OnTimer.");
                    try {
                        //クローズ処理
                        clean(session, "SyncFileServer:Timer timeout!");
                    } catch (Exception e) {
                        LOG.error("#! SyncFileServer:OnTimer Error. msg:{}", e.toString(), e);
                    }
                }
            };
            //タイマーのスケジュール
            timer = new Timer();
            timer.schedule(task, timeoutDelay*1000); //スケジュール（単位:ミリ秒）            
        } catch (Exception e) {
            //Timer処理での例外で処理がストップしないよう、処理を継続する
            LOG.error("#! SyncFilesServer:setTimer Error!. (session.id:{}, msg:{})", session.getId(), e.toString(), e);
        }

    }

    /**
     * 後始末
     * @param session 
     */
    private void clean(Session session, String closeReasonMsg)
    {
        //後始末
        //FileOutputStreamのクローズ、Sessionのクローズ、タイマーのキャンセルをする
        try {
            synchronized (lock) {
                if(fileOutputStream != null){
                    fileOutputStream.close();
                    fileOutputStream = null;
                    LOG.debug("SyncFilesServer:clean. fileOutputStream=null");
                }
                if(session != null && session.isOpen()){
                    LOG.debug("SyncFilesServer:clean. session.CloseReason:{}", closeReasonMsg);
                    session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, closeReasonMsg));
                }
                if(timer != null){
                    timer.cancel();
                    timer = null;
                }
            }
        } catch (Exception e) {
            LOG.error("#! SyncFileServer. Clean Error. msg:{}", e.toString(), e);
        }        
    }

    
    boolean isDestroyd = false;
    /**
     * インスタンス破棄時の処理
     */
    @PreDestroy
    public void onPreDestroy(){
        try {
            if(!isDestroyd){
                LOG.debug("SyncFilesServer:onPreDestroy. (session.id:{}, File:{})", sessionId, filePath);
                //念の為、後始末する
                clean(null,"");
            }
            isDestroyd=true;
        } catch (Exception e) {
            //ここでの例外は無視してインスタンス破棄を継続
        }
    }    

}
