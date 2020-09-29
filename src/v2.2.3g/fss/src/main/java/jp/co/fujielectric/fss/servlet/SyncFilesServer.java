package jp.co.fujielectric.fss.servlet;

import java.io.File;
import java.io.FileOutputStream;
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

//@ServerEndpoint(
//        value = "/{region_id}/ws/syncdb",
//        encoders = { WebSocketEncoder.class },
//        decoders = { WebSocketDecoder.class })
@ServerEndpoint("/{region_id}/ws/syncfile")
public class SyncFilesServer {
    //ファイル受信完了時のメッセージ
    public static final String SESSION_MESSAGE_COMPLETE = "complete";

    private String filePath = "";
    private FileOutputStream fileOutputStream;

    @Inject
    private Logger LOG;

    @OnMessage
    public void OnMessage(Session session, String path, @PathParam("region_id") String regionId) {
        LOG.debug("SyncFilesServer:OnMessage[path] (session.id:{}, regionId:{}, filePath:{})"
                , session.getId(), regionId, path);
        filePath = path;
        try {
            File file = new File(filePath);
            if (file.exists()) file.delete();                                   // 前回の失敗ファイルがある場合は削除
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();  // フォルダがない場合は作成
            fileOutputStream = new FileOutputStream(filePath, true);
        } catch (Throwable ex) {
            try { 
                //受信側のエラーでSessionがCloseされたことがわかるようにCloseReasonnを指定してCloseする。
                session.close( new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "OnMessage Path.(" + ex.toString() + ")")); 
            } catch (Exception e) { }
            try { fileOutputStream.close(); } catch (Exception e) { }
            fileOutputStream = null;
            LOG.error("#! SyncFileServer#OnMessage[path] Error. (session.id:{}, regionId:{}, filePath:{}) ErrMsg:{}",
                    session.getId(), regionId, path, ex.toString(), ex);
            System.err.println("SyncFiles error:" + ex.getMessage());
            throw new RuntimeException("SyncFiles error:" + ex.getMessage());
        }
    }

    @OnMessage
    public void OnMessage(Session session, byte[] bytes, boolean isLast, @PathParam("region_id") String regionId) {
        if (fileOutputStream == null) return;
        try {
            //TODO :::UT:::Start v2.2.3 SyncFile
            if(VerifyUtil.UT_MODE){
                //ログ出力が負荷とならないようUTモードの場合のみログを出力する
                VerifyUtil.outputUtLog(LOG, "", false, 
                        "SyncFilesServer:OnMessage[bytes] (session.id:%s, regionId:%s, isLast:%b, bytes.length:%d, isOpen:%b, filePath:%s)",
                        session.getId(), regionId, isLast, (bytes == null ? 0 : bytes.length), session.isOpen(), filePath);
                //Sleepテスト（ファイル同期受信側で処理遅延したケースをシミュレート）
                int utSleep = VerifyUtil.getUTArgValueInt(filePath, "#UT#", "SS", 0);   //Sleep取得
                if(utSleep > 0){
                    VerifyUtil.outputUtLog(LOG, "", false, "Sleep(%d)", utSleep);
                    Thread.sleep(utSleep*1000);
                }
                //例外発生テスト（ファイル同期受信側で例外発生したケースをシミュレート）
                if(VerifyUtil.chkUTKey(filePath, "#UT#", "EX")){
                    throw new FssException("#UT#模擬例外発生!");    //例外発生
                }
            }
            //TODO :::UT:::End v2.2.3 SyncFile

            //タイムアウトで既にSessionがクローズされている場合は、エラーなので処理を無駄に継続しない
            if(!session.isOpen()){
                throw new FssException("タイムアウト等によりセッションが閉じられたためファイル同期処理を中止します。");
            }
            
            fileOutputStream.write(bytes);
            if (isLast) {
                LOG.debug("SyncFiles OnMessage:" + regionId + ", filePath=" + filePath + ", isLast!!");
                fileOutputStream.flush();                                       // 強制的にバッファを書き込み
                fileOutputStream.close();
                fileOutputStream = null;
                session.getBasicRemote().sendText(SESSION_MESSAGE_COMPLETE);                  // clientへ完了通知
                session.close();
            }
        } catch (Throwable ex) {
            try {
                //受信側のエラーでSessionがCloseされたことがわかるようにCloseReasonnを指定してCloseする。
                session.close( new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "OnMessage Bytes.(" + ex.toString() + ")")); 
            } catch (Exception e) { }
            try { fileOutputStream.close(); } catch (Exception e) { }
            fileOutputStream = null;
            LOG.error("#! SyncFileServer#OnMessage[bytes] Error.  (session.id:{}, regionId:{}, isLast:{}, bytes.length:{}, filePath:{}) ErrMsg:{}",
                session.getId(), regionId, isLast, (bytes == null ? 0 : bytes.length), filePath, ex.toString(), ex);
            System.err.println("SyncFiles error:" + ex.getMessage());
            throw new RuntimeException("SyncFiles error:" + ex.getMessage());
        }
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("region_id") String regionId) {
        LOG.debug("SyncFilesServer:Open (session.id:{}, regionId:{})", session.getId(), regionId);
    }

    @OnError
    public void onError(Session session, Throwable throwable, @PathParam("region_id") String regionId) throws Exception {
        LOG.warn("SyncFilesServer:Error (session.id:{}, regionId:{}, throwable={})"
                , session.getId(), regionId, throwable.toString());
    }

    @OnClose
    public void onClose(Session session, @PathParam("region_id") String regionId) {
        LOG.debug("SyncFilesServer:Close (session.id:{}, regionId:{})", session.getId(), regionId);
        try {
            //念の為Streamをcloseする
            if(fileOutputStream != null)
                fileOutputStream.close();
        } catch (Exception e) {}
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
}
