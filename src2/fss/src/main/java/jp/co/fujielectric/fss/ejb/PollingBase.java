package jp.co.fujielectric.fss.ejb;

import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import javax.annotation.Resource;
import javax.ejb.EJBException;
import javax.ejb.ScheduleExpression;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.transaction.UserTransaction;
import jp.co.fujielectric.fss.data.CommonEnum.DecryptKbn;
import jp.co.fujielectric.fss.entity.UploadFileInfo;
import jp.co.fujielectric.fss.service.MailQueueService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

/**
 * ポーリング用 基底クラス
 */
@TransactionManagement(TransactionManagementType.BEAN)  //pollingのトランザクションタイムアウトによってServerログにエラーが出力されないように。
public abstract class PollingBase {

    @Inject
    Logger LOG;
    
    @Inject
    private MailQueueService mailQueueService;
    
    @Resource
    TimerService timerService;

    @Resource
    private UserTransaction tx;    
    
    /**
     * ポーリングオーナー
     */
    protected String pollingOwner;
    
    /**
     * インスタンス生成時初期化処理
     * ※継承クラスでの実装には「@PostConstruct」アノテーションを宣言すること
     */
    protected abstract void postConstruct();

    /**
     * タイマー処理
     * ※継承クラスでの実装には「@TimeOut」アノテーションを宣言すること
     */
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    protected abstract void polling();
    
    /**
     * タイマー初期化処理
     * @param timerName タイマー設定項目名(setting.propertiesの項目名)
     * @param enableFlgName 機能の有効/無効設定項目名(setting.propertiesの項目名)
     */
    protected void initTimer(String timerName, String enableFlgName){
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        try {
            // 機能の有効/無効切替え判定
            String enableFlg = CommonUtil.getSetting(enableFlgName);
            LOG.debug("## EJB 機能有効フラグ値：{}={}", enableFlgName, enableFlg);            
            if (enableFlg != null && enableFlg.equalsIgnoreCase("false")) {
                //機能が有効ではない。
                LOG.debug("## EJB 機能有効フラグ値({})がfalseに設定されているためタイマ処理は実行されません。", enableFlgName);
                return;
            }  

            //タイマ値（分ごとの秒設定）を取得
            String propSec = CommonUtil.getSetting(timerName);
            LOG.debug("## EJBタイマー値：{}={}", timerName, propSec);
            if(StringUtils.isBlank(propSec)){
                //setting.propertiesにタイマの設定がなければタイマー設定せずに抜ける
                LOG.debug("## EJB タイマー値({})が設定されていないためタイマ処理は実行されません。", timerName);
                return;
            }

            //ポーリングオーナー取得
            pollingOwner = CommonUtil.getSetting("polling_owner");
            LOG.debug("## EJB ポーリングオーナー：{}", pollingOwner);
            
            //タイマ起動
            TimerConfig timerConfig = new TimerConfig(timerName, false);    //persistent=falseとして永続化しないようにする。
            ScheduleExpression schedule = new ScheduleExpression();
            schedule.hour("*");
            schedule.minute("*");
            schedule.second(propSec);
            timerService.createCalendarTimer(schedule, timerConfig);
        } catch (IllegalArgumentException | IllegalStateException | EJBException e) {
            LOG.error("#! EJB初期化処理エラー。 timer:" + timerName + " msg:" + e.getMessage(), e);
        } finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));            
        }
    }

    /**
     * MailCaptureチケット読込み＆メールキュー出力処理
     * @param ticketServletCode 対象サーブレットコード（原本メール：mailentrance, SandBlastリターンメール:votiroentrance)
     * @return
     */
    protected boolean readTicket(String ticketServletCode) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        try {
            String mailId;
            String regionId;
            String servletCode;
            String mailDate;
            
            //チケットフォルダ
            String ticketFolder;
            ticketFolder = CommonUtil.getFolderTicket(ticketServletCode);
            //チケットフォルダ内のチケットファイル数毎のループ
            File folder = new File(ticketFolder);
            File[] files = folder.listFiles();

            if (files != null) {
                for (File f : files) {
                    //ファイル１行読込み
                    try (BufferedReader br = Files.newBufferedReader(f.toPath())) {
                        //１行目だけ読む
                        String line = br.readLine();
                        if (line == null) {
                            LOG.error("#! {} timerProcess readTicket. _ Ticket Read Error! Can't read line.  File:{}", this.getClass().getSimpleName(), f.getPath());
                            continue;
                        }
                        //CSVからセパレートする
                        String[] infos = line.split(",");
                        if (infos.length < 3) {
                            LOG.error("#! {} timerProcess readTicket. _ Ticket Read Error! File Format Error.(column count)  File:{}", this.getClass().getSimpleName(), f.getPath());
                            continue;
                        }
                        regionId = infos[0];
                        servletCode = infos[1];
                        mailId = infos[2];
                        mailDate = "";
                        //CSVの４つめをメール日付として取得[v2.2.1]
                        if(infos.length >3){
                            if(infos[3].length() > 16){
                                //16桁を超える場合は異常データ.　
                                //それ以外のチェック(YYYYMMDDかどうか等）はチェックしない。(YYYYMMDDでなくても処理上の問題はない）
                                //実際のサブフォルダと異なる文字列だった場合は、メール変換処理でエラー処理されてMailLostとなる。
                                LOG.error("#! {} timerProcess readTicket. _ Ticket Read Error! File Format Error.(mailDate length)  File:{}", this.getClass().getSimpleName(), f.getPath());
                                continue;                                
                            }
                            mailDate = infos[3];
                        }                        
                    } catch (Exception e) {
                        LOG.error("#! {} timerProcess readTicket. _ Ticket Read Error!  File:{}", this.getClass().getSimpleName(), f.getPath(), e);
                        continue;
                    }

                    try {
                        this.tx.begin();    //トランザクションスタート

                        //念のためメールキューに追加済みかチェックし、あれば削除する
                        if (mailQueueService.find(mailId) != null) {
                            mailQueueService.remove(mailId);
                        }

                        //メールキューに情報追加
                        mailQueueService.addMailQueue(
                                mailId,
                                pollingOwner,
                                regionId,
                                servletCode,
                                mailDate);

                        this.tx.commit();   //コミットする
                    } catch (Exception e) {
                        LOG.error("#! {} timerProcess readTicket. _ Can't add MailQueue! mailId:{} File:{}", 
                                this.getClass().getSimpleName(), mailId, f.getPath(), e);
                        continue;
                    }

                    try {
                        //チケットファイル削除
                        f.delete();
                    } catch (Exception e) {
                        LOG.error("#! {} timerProcess readTicket. _ Can't delete ticket file!  File:{}", this.getClass().getSimpleName(), f.getPath(), e);
                    }
                }
            }
            return true;
        } catch (Exception ex) {
            LOG.error("#! {} timerProcess readTicket. Ticket Read Error! ", this.getClass().getSimpleName(), ex);
            return false;
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }    

    /**
     * 同一ファイルレコードがリスト内の処理済みレコードの中に含まれるかチェックする
     * （リストの内、指定したレコードまでの処理済みのレコードがチェック対象）
     * @param ufi   調査するUploadFileInfo
     * @param ufiLst    対象となるリスト
     * @return True:同一ファイルあり
     */
    protected boolean chkSameFileInList(UploadFileInfo ufi, List<UploadFileInfo> ufiLst){
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        if(ufi == null || ufiLst == null)
            return false;
        int decryptKbn = ufi.getDecryptKbn();   //パスワード解除区分
        String fileId = ufi.getSendFileId();    //送信ファイルID
        //パスワード解除区分＝5(パスワード付きファイル入りZIP　パスワード未解除あり）の場合は対象外とする
        if(decryptKbn == DecryptKbn.ENCRYPTZIP.value){
            return false;
        }
        //リスト内の処理済みレコードの中に、同一ファイルかつパスワード解除区分が同じレコードないかチェックする
        for(UploadFileInfo lstUfi: ufiLst){
            if(ufi == lstUfi){
                //調査対象レコード自体の位置まで調査したら終わり。（リストの内、指定したレコードまでの処理済みのレコードがチェック対象）
                break;
            }
            if(lstUfi.isSkipped()){
                //スキップ済みのレコードは対象外
                continue;
            }                
            if(fileId.equals(lstUfi.getSendFileId())
            && decryptKbn == lstUfi.getDecryptKbn()){
                //同一ファイルがリスト内にある
                return true;
            }
        }
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));            
        //同一ファイルがリスト内に無い
        return false;
    }      
}
