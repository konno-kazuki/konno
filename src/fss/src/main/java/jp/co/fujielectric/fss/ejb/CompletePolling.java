package jp.co.fujielectric.fss.ejb;

import java.util.List;
import javax.annotation.PostConstruct;
import javax.ejb.DependsOn;
import javax.ejb.Singleton;
import javax.ejb.Timeout;
import javax.ejb.Startup;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.inject.Inject;
import jp.co.fujielectric.fss.entity.UploadGroupInfo;
import jp.co.fujielectric.fss.logic.ServletManager;
import jp.co.fujielectric.fss.logic.UploadGroupInfoManager;
import jp.co.fujielectric.fss.service.MailLostService;
import jp.co.fujielectric.fss.util.VerifyUtil;

/**
 * ポーリング用Ejb
 * メールエントランス
 */
@Singleton
@DependsOn({"VotiroDownloadPolling"})  //DependsOnで先に生成されるべきejbクラスを指定し順に生成されるようにする。（そうしないとLOGが出力されない）
@Startup    //Startupを指定することで、サーバー起動時にインスタンスが生成され、それにより@PostConstructのメソッドが呼ばれる
@TransactionManagement(TransactionManagementType.BEAN)  //pollingのトランザクションタイムアウトによってServerログにエラーが出力されないように。
public class CompletePolling extends PollingBase{

    /**
     * スケジュール設定項目名（Setting.propertiesの項目名）
     */    
    private final String PROP_TIMER = "completePollingTimer";

    /**
     * 機能の有効/無効設定項目名（Setting.propertiesの項目名）
     */    
    private final String PROP_ENABLE_FLG = "enable_polling";
    
    @Inject
    ServletManager servletManager;

    @Inject
    UploadGroupInfoManager ugiManager;
        
    public CompletePolling() {
    }

    /**
     * インスタンス生成時初期化処理
     */
    @PostConstruct
    @Override
    protected void postConstruct(){
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        //タイマの初期化
        super.initTimer(PROP_TIMER, PROP_ENABLE_FLG);
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }

    /**
     * タイマー処理
     */
    @Timeout
    @Override
    protected void polling(){
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        try {
            //処理対象UploadFileInfoを取得する
            List<UploadGroupInfo> ugiLst = ugiManager.findUploadGroupInfoSanitizedAll(pollingOwner);
            if(ugiLst == null || ugiLst.isEmpty())
                return;            
            LOG.debug("##--CompletePolling timerProcess Start!  size:" + ugiLst.size());
            for(UploadGroupInfo ugi: ugiLst){
                boolean isCountOver = false;
                //完了処理
                // グループ内全ファイルの無害化が終了
                try {
                    LOG.debug("  -- CompletePolling [UploadGroupInfoId:{}, ReceiveInfoId:{}, SendInfoId:{}]", 
                            ugi.getId(), ugi.getSubId(), ugi.getSendInfoId());
                    //TODO :::UT:::Start v2.2.1 スリープテスト
                    if(VerifyUtil.UT_MODE){
                        int utSleep = VerifyUtil.getUTArgValueInt(ugi.getProcDate(), "#UT#", "CS", 0);
                        if(utSleep > 0){
                            VerifyUtil.outputUtLog(LOG, "", false, "Sleep(%d)", utSleep);
                            Thread.sleep(utSleep*1000);
                        }
                    }
                    //TODO :::UT:::End v2.2.1 スリープテスト*/
                    if (servletManager.postComplete(ugi)) {
                        // グループ内全ファイルの後処理が終了
                        LOG.debug(" -- postComplete passing: " + ugi.getId());
                        // UploadGroupInfo、及びUploadFileInfoを履歴テーブルに移動
                        try {
                            ugiManager.moveUploadGroupInfoToComp(ugi.getId());
                            LOG.debug(" -- moveUploadGroupInfoToComp passing: " + ugi.getId());
                        } catch (Exception e) {
                            LOG.error("#!無害化完了処理 UploadGroupInfo/FileInfoのCompへの移動に失敗しました。  UploadGroupInfoId:{} Msg:{}", ugi.getId(),e.getMessage(), e);
                            try{
                                //UploadGroupInfo/FileInfo のCOMPへの移動が失敗した場合、キャンセルフラグをTrueにしてリトライされないようにする。
                                //完了処理（完了メール送信）は正常終了しているので、メールロスト処理には移行しないようにする。
                                ugiManager.updateCancelFlg(ugi.getId(), true);
                            }catch(Exception e2){
                                //キャンセルフラグのセットにも失敗した場合はどうしようもないのでエラーログ出力のみ。
                                //完了処理（完了メール送信）は正常終了しているので、メールロスト処理には移行しないようにする。
                                LOG.error("#!無害化完了処理 UploadGroupInfoのキャンセルフラグのセットに失敗しました。  UploadGroupInfoId:{} Msg:{}", ugi.getId(),e2.getMessage(), e2);
                            }                            
                        }
                    }  
                } catch (Exception e) {
                    LOG.error("#!無害化完了処理でエラーが発生しました。 uploadGroupInfoId:{}", ugi.getId() , e);
                    //UploadGroupInfoのリトライカウントアップ、キャンセル処理
                    try {
                        isCountOver = ugiManager.retryCountUpGroupInfo(ugi);
                    } catch (Exception e2) {
                        //リトライカウントアップエラー
                        LOG.error("#!無害化完了処理 UploadGroupInfoのリトライカウントアップに失敗しました。 --UploadGroupInfoId:{}", ugi.getId(), e2);
                    }
                }
                if(isCountOver){                    
                    //完了処理エラー　リトライオーバー時処理
                    try{
                        LOG.warn("##-- MailLost! UploadGroupInfoId:{}", ugi.getId());
                        //メールロスト履歴追加
                        servletManager.postMailLost(ugi.getRegionId(), true, 
                                MailLostService.EnmMailLostFunction.VotiroDownload, "", 
                                ugi.getMainId(), ugi.getId());
                    }catch(Exception ex){
                        LOG.error("PostMailLost Error", ex);
                    }                    
                }
            }
            LOG.debug("##--CompletePolling timerProcess End!");            
        } catch (Exception e) {
            LOG.error("#! 無害化完了処理ポーリング処理中にエラーが発生しました。　errMsg=" + e.getMessage(), e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }        
    } 
}
