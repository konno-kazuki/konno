package jp.co.fujielectric.fss.ejb;

import java.util.List;
import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Timeout;
import javax.ejb.Startup;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.inject.Inject;
import jp.co.fujielectric.fss.entity.MailQueue;
import jp.co.fujielectric.fss.logic.ServletManager;
import jp.co.fujielectric.fss.service.MailLostService;
import jp.co.fujielectric.fss.service.MailQueueService;
import jp.co.fujielectric.fss.util.VerifyUtil;

/**
 * ポーリング用Ejb
 * メールエントランス
 */
@Singleton
//@DependsOn({"xxxPolling"})  //DependsOnで先に生成されるべきejbクラスを指定し順に生成されるようにする。（そうしないとLOGが出力されない）
@Startup    //Startupを指定することで、サーバー起動時にインスタンスが生成され、それにより@PostConstructのメソッドが呼ばれる
@TransactionManagement(TransactionManagementType.BEAN)  //pollingのトランザクションタイムアウトによってServerログにエラーが出力されないように。
public class MailEntrancePolling extends PollingBase{

    /**
     * スケジュール設定項目名（Setting.propertiesの項目名）
     */    
    private final String PROP_TIMER = "mailEntrancePollingTimer";

    /**
     * 機能の有効/無効設定項目名（Setting.propertiesの項目名）
     */    
    private final String PROP_ENABLE_FLG = "enable_entrance";
    
    @Inject
    private ServletManager servletManager;

    @Inject
    private MailQueueService mailQueueService;
    
    public MailEntrancePolling() {
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
            //MailCaptureチケット読込み＆メールキュー出力処理
            readTicket(MailQueue.SERVLET_CODE_MAILENTRANCE);

            //メールキュー読込み＆Entrance処理呼出し処理
            execEntrance();
            
        } catch (Throwable e) {
            LOG.error("#! MailEntranceポーリング処理中にエラーが発生しました。　errMsg=" + e.getMessage(), e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }        
    }

    /**
     * メールキュー読込み＆Entrance処理呼出し処理
     *
     * @return
     */
    private void execEntrance() {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        try {
            //メールキュー情報取得 (対象："mailentrance"）
            List<MailQueue> mqLst = mailQueueService.findMailQueByServletCode(pollingOwner, MailQueue.SERVLET_CODE_MAILENTRANCE);
            if (mqLst.size() > 0) {
                LOG.debug("##--MailEntrancePolling timerProcess execEntrance Start!  size:" + mqLst.size());

                for (MailQueue mq : mqLst) {
                    LOG.debug("  -- MailEntrancePolling execEntrance [MailId:{}, ServletCode:{}]", mq.getId(), mq.getServletCode());
                    try {
                        //TODO :::UT:::Start v2.2.1 スリープテスト
                        if(VerifyUtil.UT_MODE){
                            //mailDateからUT文字列を判定
                            int utSleep = VerifyUtil.getUTArgValueInt(mq.getMailDate(), "#UT#", "MS", 0);
                            if(utSleep > 0){
                                VerifyUtil.outputUtLog(LOG, "", false, "Sleep(%d)", utSleep);
                                Thread.sleep(utSleep*1000);
                            }
                        }
                        //TODO :::UT:::End v2.2.1 スリープテスト
                        
                        boolean result = servletManager.postEntrance(mq);
                        // 処理完了
                        if (result) {
                            //成功
                            //メールキューを完了テーブル（MailQueueComp)に移動
                            mailQueueService.moveToComp(mq.getId(), false);
                        }else{
                            //メール解析エラーによりエラーメール送信済み（リトライ不要）
                            //メールキューを完了テーブル（MailQueueComp)に移動（キャンセルフラグ=true）
                            mailQueueService.moveToComp(mq.getId(), true);
                        }
                    } catch (Exception | OutOfMemoryError e) {
                        //処理NG
                        LOG.error(" -- execEntrance Error. (MailQueueId:"+mq.getId()+")", e);

                        //リトライカウントアップ
                        mailQueueService.retryCountUp(mq);
                    }
                    
                    //失敗＆キャンセルフラグ＝trueとなった場合の処理
                    if (mq.isCancelFlg()) {
                        try{                  
                            boolean result = servletManager.postEntranceError(mq);
                            if (result) {
                                // 処理完了扱い
                                //メールキューを完了テーブル（MailQueueComp)に移動
                                mailQueueService.moveToComp(mq.getId(), true);
                            }else{                            
                                //エラーメール送信に失敗した場合
                                //メールロスト履歴追加
                                servletManager.postMailLost(mq.getRegionId(), false, MailLostService.EnmMailLostFunction.MailEntrance, mq.getId(), "", "");
                            }
                        }catch(Throwable e){
                            LOG.error(" -- execEntrance Cancel Error! (MailQueueId:"+mq.getId()+")", e);
                        }
                    }
                }
                LOG.debug("##--MailEntrancePolling timerProcess execEntrance End!");
            }
        } catch (Exception ex) {
            LOG.error("#!MailEntrancePolling timerProcess execEntrance Read MailQueue or postEntrance Error! ", ex);
        }
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }
}
