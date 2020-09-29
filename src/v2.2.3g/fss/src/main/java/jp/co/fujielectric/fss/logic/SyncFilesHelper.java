package jp.co.fujielectric.fss.logic;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.data.CommonBean;
import jp.co.fujielectric.fss.servlet.SyncFilesClient;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.FileUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.logging.log4j.Logger;
import jp.co.fujielectric.fss.exception.FssException;

/**
 * ファイル同期サービス
 */
@AppTrace
@SqlTrace
@ApplicationScoped
public class SyncFilesHelper {
    private final int BUF_SIZE = 4 * 1024;
    private final int TIMEOUT_DEFAULT = 30 * 60;    //タイムアウトデフォルト値（30分）

    @Inject
    private Logger LOG;

    @Inject
    private CommonBean commonBean;

    public void syncFiles(String targetFolder) {
        if (!Boolean.valueOf(CommonUtil.getSetting("sync_files"))) return;
        File folder = new File(targetFolder);
        if(folder != null && folder.isDirectory()){
            File[] fileList = FileUtil.getFileFolder(folder);
            if(fileList != null){
                for (File file : fileList) {
                    syncFile(file);
                }
            }
        }
    }

    public void syncFile(String filePath) {
        if (!Boolean.valueOf(CommonUtil.getSetting("sync_files"))) return;
        syncFile(new File(filePath));
    }

    private void syncFile(File file) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "filePath:" + file.getPath()));
        try {
            //タイムアウト値取得[v2.2.3]
            long timeout = CommonUtil.getSettingInt("syncFile_timeout", TIMEOUT_DEFAULT);           
            //TODO :::UT:::Start v2.2.3 SyncFile テスト用処理（テストモード時のみ実行）
            if(VerifyUtil.UT_MODE){
                //テスト用タイムアウト時間設定
                int utTimeout = VerifyUtil.getUTArgValueInt(file.getPath(), "#UT#", "TIMEOUT", -1);
                if(utTimeout != -1){
                    timeout = utTimeout;
                }
                VerifyUtil.outputUtLog(LOG, "", false, "Timeout(%d)", timeout);
            }
            //TODO :::UT:::End v2.2.3 SyncFile      

            Session session;
            try (InputStream inputStream = new FileInputStream(file)) {
                byte[] readBuff = new byte[BUF_SIZE];
                int readLen;
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                session = container.connectToServer(SyncFilesClient.class, URI.create(CommonUtil.createWebSocketUrl(commonBean.getRegionId(), !CommonUtil.isSectionLgwan())));     
                if(timeout > 0){
                    //タイムアウトが設定されている場合（0より大きい場合）、Sessionにタイムアウトを設定して停止を防ぐ[v2.2.3]
                    session.setMaxIdleTimeout(timeout * 1000);
                }
                session.getBasicRemote().sendText(file.getCanonicalPath());     // ファイルパスの送信
                while ((readLen = inputStream.read(readBuff)) != -1) {          // ファイルデータの送信
                    ByteBuffer byteBuffer = ByteBuffer.wrap(Arrays.copyOfRange(readBuff, 0, readLen));
                    session.getBasicRemote().sendBinary(byteBuffer, false);
                } 
                ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[0]);       // NOTE: 終端通知のため０バイト送信
                session.getBasicRemote().sendBinary(byteBuffer, true);
            }
            VerifyUtil.outputUtLog(LOG, "", false, "syncFile. 全データ送信完了.");
            
            // ファイル転送完了まで待つ
            // TODO: 本来はpolling処理か
            while (session.isOpen()) {
                Thread.sleep(100);  // 100ms間隔で確認
            }
            //onClose処理でCloseReasonがUserPropertiesにセットされるのを待つ
            //※永久ループを防ぐため最長で10秒(100mmSec x 100回)とする。
            //・通常、即時onCloseの結果が反映されるため問題なし。　
            //・もし10秒で反映されず次に進んでもタイムアウトかどうかの判断ができないだけなので実害なし
            int closeReasonCode = -1;   //close受信前はonOpenにて初期値として-1がセットされている
            for(int i=0; i<100; i++){
                synchronized (SyncFilesClient.getLock()) {
                    closeReasonCode = (int)session.getUserProperties().get(SyncFilesClient.SESSION_PROP_CLOSEREASON);
                }                
                if(closeReasonCode != -1)
                    break;  //-1以外がセットされていれば、onCloseでCloseReasonが取得された後と判定する
                Thread.sleep(100);  // 100ms間隔で確認
            }
            //受信先からの完了メッセージ受信結果を確認する
            boolean isSessionComplete = false;
            synchronized (SyncFilesClient.getLock()) {                          // 排他制御
                isSessionComplete = SyncFilesClient.getSessions().get(session);
                SyncFilesClient.getSessions().remove(session);
            }
            if (!isSessionComplete) {
                //タイムアウトかどうかの判定
                if(closeReasonCode == CloseReason.CloseCodes.CLOSED_ABNORMALLY.getCode()){
                    //タイムアウト (タイムアウトの場合にCloseReasonにCLOSED_ABNORMALLYがセットされる）
                    //【対象】
                    //・Session.setMaxIdleTimeoutでセットしたタイムアウトを超えて同期先サーバからのレスポンス（完了メッセージ）が返ってこない場合。
                    //・同期先サーバ側での処理時間がhttp-listenerのread-timeoutの設定値（同期先サーバでの設定）を超えて、同期先でタイムアウトが発生した場合。
                    throw new FssException("ファイル同期がタイムアウトにより失敗しました。(file:" + file.getPath() + ")");  //検査済み例外
                }
                //それ以外（受信側でエラー発生の場合はcloseReasonCodeに[CloseReason.CloseCodes.CANNOT_ACCEPT]がセットされる）
                throw new FssException("syncFile reject. closeReason:" + closeReasonCode + ", file:" + file.getPath());  //検査済み例外
            }
        }catch(FssException ex){
            //検査済み例外
            LOG.error(ex.getMessage());
            throw new RuntimeException(ex.getMessage(), ex);
        } catch (Exception ex) {
            //検査済み例外以外の例外
            String errMsg = String.format("syncFile error. file:%s, error:%s", file.getPath(), ex.toString());
            LOG.error(errMsg);
            throw new RuntimeException(errMsg, ex);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "filePath:" + file.getPath()));
        }
    }
}
