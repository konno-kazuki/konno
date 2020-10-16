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

    private final double TIMEOUT_ADJUST_RATE_DEF = 1.0; //タイムアウト データ送信完了前調整倍率デフォルト値 [v2.2.5a]
    /**
     * 強制クローズのタイムアウトからの遅延時間 [v2.2.5a]
     */
    private final long TIMEOUT_DELAY_DEFAULT = 60;   //タイムアウト後強制クローズ遅延時間 デフォルト値（1分）
    
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
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "File:" + file.getPath() + ", Size:" + file.length()));
        Session session = null;
        String sessionId = "";
        
        int dataSendCount = 0;
        try {
            //RegionID
            String regionId = commonBean.getRegionId();
            //タイムアウト値取得 （単位：秒）[v2.2.3]
            long timeout = CommonUtil.getSettingInt("syncFile_timeout", TIMEOUT_DEFAULT);
            //強制タイムアウト時間（単位：秒） [v2.2.5a]
            long timeoutDelay = timeout + TIMEOUT_DELAY_DEFAULT;    //強制タイムアウト = セッションタイムアウト + 60秒
            if(timeout <= 0)
                timeoutDelay = 0;   //タイムアウトに0以下が設定されている場合は強制クローズもしない
            //タイムアウト データ送信完了前調整倍率 [v2.2.5a]
            double timeoutAdjustRate = CommonUtil.getSettingDouble("syncFile_adjustRate", TIMEOUT_ADJUST_RATE_DEF);
            
            // [v2.2.5b]
            //ファイル同期クライアント用タイムアウト値取得。
            long cliTimeout = CommonUtil.getSettingInt("syncFile_cli_timeout", -1);
            if(cliTimeout >= 0){
                timeoutDelay = cliTimeout;  //取得したプロパティ値を強制タイムアウト値に設定
                //※未設定の場合、強制タイムアウト=セッションタイムアウト+60秒　のまま
                //※プロパティに0がセットされている場合は、強制タイムアウトをしない。
            }
            
            //ファイル同期開始時に、現在時刻を取得　（日付ではなくlong値）【v2.2.5a】
            long startTime = System.currentTimeMillis();
            
            //TODO :::UT:::Start v2.2.5 SyncFile テスト用処理（テストモード時のみ実行）
            if(VerifyUtil.UT_MODE){
                //テスト用タイムアウト時間設定
                int utTimeout = VerifyUtil.getUTArgValueInt(file.getPath(), "#UT225#", "SYNCTIMEOUT", -1);
                if(utTimeout != -1){
                    timeout = utTimeout;
                }
                //テスト用タイムアウト後強制クローズ遅延時間設定
                int utTimeoutDelay = VerifyUtil.getUTArgValueInt(file.getPath(), "#UT225#", "SYNCDELAY", -1);
                if(utTimeoutDelay != -1){
                    timeoutDelay = utTimeoutDelay;
                }
                //テスト用のregionIdにサーバー側TimerDelay値を含める（Server側Timerのテスト用）
                long timerDelay = timeout + timeoutDelay;
                int utTimerDelay = VerifyUtil.getUTArgValueInt(file.getPath(), "#UT225#", "TIMER", -1);
                if(utTimerDelay != -1){
                    timerDelay = utTimerDelay;
                }
                regionId = String.format("#UT225#TIMER=%d#", timerDelay);
                VerifyUtil.outputUtLog(LOG, "", false, "#UT#SyncFile: timeout:%d, timeoutDelay:%d, timerDelay:%d",
                        timeout,timeoutDelay,timerDelay);
            }
            //TODO :::UT:::End v2.2.5 SyncFile      

            FILESEND:try (InputStream inputStream = new FileInputStream(file)) {
                byte[] readBuff = new byte[BUF_SIZE];
                int readLen;
                long sendSizeTotal = 0;   //送信サイズ合計
                
                //接続(Open)
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                session = container.connectToServer(SyncFilesClient.class, URI.create(CommonUtil.createWebSocketUrl(regionId, !CommonUtil.isSectionLgwan())));     
                sessionId = session.getId();
                if(timeout > 0){
                    //タイムアウトが設定されている場合（0より大きい場合）、Sessionにタイムアウトを設定して停止を防ぐ[v2.2.3]
                    //[v2.2.5a]
                    //データ送信完了までに通信異常となった場合に、データ送信処理sendBinaryで内部リトライにより指定タイムアウトを超える待ち時間が発生するため
                    //この時点ではタイムアウト値に指定の調整倍率（デフォルト：1.0）をかけた値をタイムアウト値として指定する
                    //調整が必要な場合に適宜setting.propertiesの「syncFile_adjustRate」に調整倍率を設定することとする。
                    session.setMaxIdleTimeout((long)((double)(timeout * 1000) * timeoutAdjustRate));  //単位:ミリ秒
                }
                LOG.debug("SyncFile: connect(session.id:{}, Setting.timeout:{}, Setting.adjustRate:{}, Setting.cliTimeout:{}, SessionTimeout:{})",
                        sessionId, timeout, timeoutAdjustRate, cliTimeout, session.getMaxIdleTimeout()/1000);    //[v2.2.5a]
            
                //TODO :::UT:::Start v2.2.5a SyncFile
                if(VerifyUtil.UT_MODE){
                    //ファイル名送信以降がServerに通知されない状況のテスト
                    if(VerifyUtil.chkUTKey(file.getName(), "#UT225#", "NOPATH")){
                        VerifyUtil.outputUtLog(LOG, "", false, "#FileSync: ファイル名送信スキップ");
                        break FILESEND; //FILESENDスコープから抜ける
                    }
                    //Sleepテスト（ファイル送信完了前にネットワーク異常を起こすシミュレート用。※この間に手動でネットワーク異常を起こすこと）
                    int utSleep = VerifyUtil.getUTArgValueInt(file.getName(), "#UT225#", "CSLP1", 0);   //Sleep取得
                    if(utSleep > 0){
                        VerifyUtil.outputUtLog(LOG, "", false, "#FileSync: ファイル名送信前スリープ (%d)", utSleep);
                        for(int i=0; i<utSleep; i++)
                            Thread.sleep(1000);
                    }                    
                }
                //TODO :::UT:::End v2.2.5a SyncFile                

                // ファイルパスの送信
                session.getBasicRemote().sendText(file.getCanonicalPath());
                LOG.debug("SyncFile: sendFileName(session.id:{}, FileName:{})", session.getId(), file.getCanonicalPath());    //[v2.2.5a]            
                
                // ファイルデータ送信
                while ((readLen = inputStream.read(readBuff)) != -1) {          // ファイルデータの送信
                    ByteBuffer byteBuffer = ByteBuffer.wrap(Arrays.copyOfRange(readBuff, 0, readLen));
                    session.getBasicRemote().sendBinary(byteBuffer, false);
                    //ログ出力（先頭、1MByte毎にログ出力） [v2.2.5a]
                    if(sendSizeTotal == 0){
                        LOG.debug("SyncFile: sendBinary.Start(session.id:{}, File:{}, Size:{})",
                                session.getId(), file.getCanonicalPath(), readLen);   //最初のデータ送信時のログ出力 [v2.2.5a]
                    }else if((sendSizeTotal + readLen) / (1024 * 1024) > (sendSizeTotal / (1024 * 1024))){
                        LOG.debug("SyncFile: sendBinary.Send(session.id:{}, File:{}, Size:{})", 
                                session.getId(), file.getCanonicalPath(), sendSizeTotal+readLen);   //1MBtye単位のログ出力  [v2.2.5a]
                    }
                    sendSizeTotal += readLen;   //サイズ累計
                    dataSendCount++;    //データ送信回数カウントアップ
                } 
                //TODO :::UT:::Start v2.2.5a SyncFile
                if(VerifyUtil.UT_MODE){
                    //ファイル送信完了がServerに通知されない状況のテスト
                    if(VerifyUtil.chkUTKey(file.getName(), "#UT225#", "NOEND")){
                        VerifyUtil.outputUtLog(LOG, "", false, "#FileSync: 送信完了通知送信スキップ");
                        break FILESEND; //FILESENDスコープから抜ける
                    }
                    //Sleepテスト（ファイル送信完了前にネットワーク異常を起こすシミュレート用。※この間に手動でネットワーク異常を起こすこと）
                    int utSleep = VerifyUtil.getUTArgValueInt(file.getName(), "#UT225#", "CSLP2", 0);   //Sleep取得
                    if(utSleep > 0){
                        VerifyUtil.outputUtLog(LOG, "", false, "#FileSync: データ送信完了前スリープ (%d)", utSleep);
                        for(int i=0; i<utSleep; i++)
                            Thread.sleep(1000);
                    }
                }
                //TODO :::UT:::End v2.2.5a SyncFile
                
                // ファイルデータ送信完了通知（NOTE: 終端通知のため０バイト送信）
                ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[0]);
                session.getBasicRemote().sendBinary(byteBuffer, true);
                LOG.debug("SyncFile: sendBinary.End(session.id:{}, File:{}, Size:{})", 
                                session.getId(), file.getCanonicalPath(), sendSizeTotal);    //[v2.2.5a]
            }
            
            if(timeout > 0){
                //タイムアウトの調整倍率が設定されている場合を考慮し、ここで指定のタイムアウト値に再設定する。
                //※この時点ですでに経過した時間を差し引いた時間をタイムアウト時間とする。
                long tmpTimeout = timeout * 1000 - (System.currentTimeMillis() - startTime);
                if(tmpTimeout > 0){
                    session.setMaxIdleTimeout(tmpTimeout);  //単位:ミリ秒
                    VerifyUtil.outputUtLog(LOG, "", false, "#FileSync: データ送信完了後タイムアウト再設定 (Timeout[mmSec]:%d)", tmpTimeout);
                }
            }
            
            // ファイル転送完了まで待つ
            while (session.isOpen()) {
                Thread.sleep(100);  // 100ms間隔で確認
                if(timeoutDelay > 0){
                    //経過時間を計測し、タイムアウト時間(+1分）を超える場合の処理を実装 [v2.2.5a]
                    long tmTimer = System.currentTimeMillis() - startTime;   //経過時間 = 現在時刻 - 開始時刻
                    if( tmTimer > timeoutDelay*1000) {      //強制タイムアウト値を超過したか判定。
                        //---------------------------------------------------------
                        //タイムアウトを超過してもクローズしない異常発生時の処理
                        //---------------------------------------------------------
                        LOG.debug("SyncFile: タイムアウト発生. (session.id:{}, File:{})", session.getId(), file.getPath());    //[v2.2.5a]
                        //セッションをクローズする。
                        session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, "Timeout Error!."));
                        session = null;
                        //例外を発生させ、リトライ対象とする		
                        throw new FssException("SyncFile: ファイル同期強制終了。 (File:" + file.getPath() + ")");
                    }					
                }
            }
            //onClose処理でCloseReasonがUserPropertiesにセットされるのを待つ
            //※永久ループを防ぐため最長で10秒(100mmSec x 100回)とする。
            //・通常、即時onCloseの結果が反映されるため問題なし。　
            //・もし10秒で反映されず次に進んでもタイムアウトかどうかの判断ができないだけなので実害なし
            int closeReasonCode = -1;   //close受信前はonOpenにて初期値として-1がセットされている
            for(int i=0; i<100; i++){
                closeReasonCode = (int)session.getUserProperties().get(SyncFilesClient.SESSION_PROP_CLOSEREASON);
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
                    throw new FssException("SyncFile: ファイル同期がタイムアウトにより失敗しました。(File:" + file.getPath() + ")");  //検査済み例外
                }
                //それ以外（受信側でエラー発生の場合はcloseReasonCodeに[CloseReason.CloseCodes.CANNOT_ACCEPT]がセットされる）
                throw new FssException("SyncFile: reject. closeReason:" + closeReasonCode + ", File:" + file.getPath());  //検査済み例外
            }
        }catch(FssException ex){
            //検査済み例外
            LOG.error(ex.getMessage());
            throw new RuntimeException(ex.getMessage(), ex);
        } catch (Throwable ex) {
            //検査済み例外以外の例外
            String errMsg = String.format("SyncFile: error. (session.id:%s, File:%s, dataSendCount:%d, Error:%s)",sessionId, file.getPath(), dataSendCount, ex.toString());
            LOG.error(errMsg);
            throw new RuntimeException(errMsg, ex);
        }finally{
            try {
                //もしsessionが残っていたらcloseする。　例外発生しても処理が継続する。
                if(session != null && session.isOpen()){
                    session.close();
                }
                //SyncFilesの静的なMap変数からsessionを除去する。
                synchronized (SyncFilesClient.getLock()) {                          // 排他制御
                    SyncFilesClient.getSessions().remove(session);
                }                
            } catch (Exception e) {}
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "File:" + file.getPath()));
        }
    }
}
