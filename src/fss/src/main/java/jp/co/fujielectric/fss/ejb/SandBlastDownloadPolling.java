package jp.co.fujielectric.fss.ejb;

import java.util.Date;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.ejb.DependsOn;
import javax.ejb.Singleton;
import javax.ejb.Timeout;
import javax.ejb.Startup;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.inject.Inject;
import jp.co.fujielectric.fss.data.CommonEnum;
import jp.co.fujielectric.fss.data.CommonEnum.StepKbn;
import jp.co.fujielectric.fss.entity.MailQueue;
import jp.co.fujielectric.fss.entity.UploadFileInfo;
import jp.co.fujielectric.fss.exception.FssException;
import jp.co.fujielectric.fss.logic.SandblastLogic;
import jp.co.fujielectric.fss.logic.SandblastLogic.SandBlastDownloadResult;
import jp.co.fujielectric.fss.logic.ServletManager;
import jp.co.fujielectric.fss.logic.UploadGroupInfoManager;
import jp.co.fujielectric.fss.service.MailQueueService;
import jp.co.fujielectric.fss.service.UploadFileInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;

/**
 * ポーリング用Ejb
 * SandBlastダウンロード
 */
@Singleton
@DependsOn({"SandBlastUploadPolling"})  //DependsOnで先に生成されるべきejbクラスを指定し順に生成されるようにする。（そうしないとLOGが出力されない）
@Startup    //Startupを指定することで、サーバー起動時にインスタンスが生成され、それにより@PostConstructのメソッドが呼ばれる
@TransactionManagement(TransactionManagementType.BEAN)  //pollingのトランザクションタイムアウトによってServerログにエラーが出力されないように。
public class SandBlastDownloadPolling extends PollingBase{

    /**
     * スケジュール設定項目名（Setting.propertiesの項目名）
     */    
    private final String PROP_TIMER = "sandBlastDownloadPollingTimer";

    /**
     * 機能の有効/無効設定項目名（Setting.propertiesの項目名）
     */    
    private final String PROP_ENABLE_FLG = "enable_entrance";
    
    @Inject
    ServletManager servletManager;

    @Inject
    UploadGroupInfoManager ugiManager;
    
    @Inject
    UploadFileInfoService uploadFileInfoService;    
    
    @Inject
    private MailQueueService mailQueueService;

    @Inject
    private SandblastLogic sandblastLogic;
    
    public SandBlastDownloadPolling() {
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
            readTicket(MailQueue.SERVLET_CODE_VOTIROENTRANCE);

            //メールキュー読込み＆SandBlastDownload処理呼出し処理
            execSandBlastDownload();
            
            //SandBlastタイムアウトチェック処理
            checkSandBlastTimeout();
            
        } catch (Throwable e) {
            LOG.error("#! SandBlastダウンロードポーリング処理中にエラーが発生しました。　errMsg=" + e.getMessage(), e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }        
    }
    
    /**
     * メールキュー読込み＆SandBlastDownload処理呼出し処理
     *
     */
    private void execSandBlastDownload() {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        try {
            //メールキュー情報取得 (対象："votiroentrance"）
            List<MailQueue> mqLst = mailQueueService.findMailQueByServletCode(pollingOwner, MailQueue.SERVLET_CODE_VOTIROENTRANCE);
            if (mqLst.size() > 0) {
                LOG.debug("##--SandBlastDownloadPolling timerProcess execEntrance Start!  size:" + mqLst.size());

                //メールキュー分のループ処理
                for (MailQueue mq : mqLst) {
                    LOG.debug("  -- SandBlastDownloadPolling execEntrance [MailId:{}]", mq.getId());
                    boolean flgCancel = false;
                    SandBlastDownloadResult result;
                    UploadFileInfo ufi = null;
                    try {
                        //TODO :::UT:::Start v2.2.1 スリープテスト
                        if(VerifyUtil.UT_MODE){
                            //mailDateからUT文字列を判定
                            int utSleep = VerifyUtil.getUTArgValueInt(mq.getMailDate(), "#UT#", "SS", 0);
                            if(utSleep > 0){
                                VerifyUtil.outputUtLog(LOG, "", false, "Sleep(%d)", utSleep);
                                Thread.sleep(utSleep*1000);
                            }
                        }
                        //TODO :::UT:::End v2.2.1 スリープテスト
                        
                        //SandBlastダウンロード処理実行（SandBlastリターンメール解析）
                        result = sandblastLogic.execSandblastDownload(mq.getId(), mq.getMailDate());
                        ufi = result.getUploadFileInfo();
                        if(ufi == null){
                            //処理対象となるUploadFileInfoが見つからないケースなので当該MailQueueをキャンセル
                            flgCancel = true;
                        }else if(result.isError()){
                            //エラー（UploadFileInfoが特定できた場合）
                            //ふるまい検知チェックでの例外発生としてリトライ対象とする
                            throw new FssException(result.getErrMessage());
                        }else{
                            //ふるまい検知でOK/NG判定成功した場合
                            //成功時、ステップ更新して次の処理（Votiroアップロード）のポーリング対象となるようにする。
                            //※ふるまい検知でNGになった場合もVoriroアップロードに進むが、
                            //  その場合、京都（SandBlast結果をVotiroアップロードに使用）でも、Votiroアップロードに元ファイルを使用することとする
                            ugiManager.updateStep(ufi, StepKbn.VotiroUploadWait);
                        }
                    } catch (Throwable e) {
                        flgCancel = true;   //エラー時は当該MailQueueをキャンセルとしてCOMPに移動
                        
                        String uploadFileInfoId = (ufi != null ? ufi.getFileId() : null);
                        LOG.error("#! SandBlastダウンロードポーリング処理中にエラーが発生しました。"
                                + " (MailId:{}, UploadFileInfoId:{}, errMsg:{})",
                                mq.getId(), uploadFileInfoId, e.getMessage(), e);                                                                            
                        try {
                            if(ufi != null) {
                                //UploadFileInfo特定済みの場合のエラー発生時
                                //UploadFileInfoのリトライカウントアップ+リトライ（SandBlastアップロード）
                                String errMsg = String.format("Receive Error. (%s)", e.getMessage());
                                if(ugiManager.retryCountUpFileInfo(ufi, errMsg, false)){
                                    //リトライカウントオーバーなのでステップをVotiroアップロード待ちにしてVotiroアップロードに進む。
                                    LOG.warn("#-SandBlastDownloadPolling リトライカウントオーバー (MailId:{}, FileId:{})",
                                            mq.getId(), uploadFileInfoId);
                                    ugiManager.updateStep(ufi, StepKbn.VotiroUploadWait);
                                }
// UploadFileInfoが特定できない場合でもMailQueueに対するリトライをしない
// 理由：MailQueueリトライで成功するような例外発生状況がほぼ考えられない。タイムアウト監視があるので、永遠に処理待ちになることがない。
// ※もしMailQueueのリトライが必要であれば、以下コメントを外すこと。
//                            }else{    
//                                //UploadFileInfo未特定みの場合のエラー発生時
//                                //MailQueueのリトライカウントアップ+リトライ（SandBlastダウンロード）
//                                mailQueueService.retryCountUp(mq);
//                                LOG.warn("#-SandBlastDownloadPolling MailQueue リトライカウントアップ (MailId:{}, FileId:{}, RetryCountOver:{})",
//                                            mq.getId(), uploadFileInfoId, mq.isCancelFlg());
//                                if(!mq.isCancelFlg()){
//                                    //リトライオーバーじゃなければCOMPへの移動をせず、再度同MailQueueのチェックをする
//                                    continue;
//                                }                                
                            }                            
                        } catch (Exception e2) {
                            LOG.error("#! SandBlastダウンロードポーリング リトライカウントアップ処理中にエラーが発生しました。"
                                + "(MailId:{}, UploadFileInfoId:{}, errMsg:{})", mq.getId(), uploadFileInfoId, e.getMessage(), e);                            
                        }
                    }
                    
                    try{
                        //成否に関係なくメールキューを完了テーブル（MailQueueComp)に移動。
                        //(エラー時もSandBlast送信からリトライするので、同じMailQueueに対するリトライはしないのでCompに移動する）
                        //UplodFileInfoが特定され処理が（ふるまい検知結果に関係無く）成功した場合のみキャンセルフラグ=falseとする
                        mailQueueService.moveToComp(mq.getId(), flgCancel);
                    }catch(Exception e){
                        //メールキュー移動失敗時はエラーログ出力してそのまま続行する。
                        LOG.error("#! SandBlastDownloadPolling MailQueue MoveToComp Error. (MailId:{})", mq.getId(), e);                        
                    }
                }
                LOG.debug("##--SandBlastDownloadPolling timerProcess execEntrance End!");
            }
        } catch (Throwable ex) {
            LOG.error("#! SandBlastダウンロードポーリング処理中にエラーが発生しました。　errMsg:{}", ex.getMessage(), ex);
        }
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }
    
    /**
     * SandBlastアップロード中レコードのタイムアウトチェック
     */
    private void checkSandBlastTimeout(){
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        try {
            //タイムアウト設定値を取得する
            long tout = CommonUtil.getSettingInt("sandblast_timeout");
            Date timeoutDate = new Date(new Date().getTime() - tout*1000);  //タイムアウト基準日時
            
            //処理対象UploadFileInfoを取得する
            List<UploadFileInfo> ufList = uploadFileInfoService.findUploadFileInfoTimeOut(CommonEnum.StepKbn.SandBlastUploading, pollingOwner, timeoutDate);
            if(ufList == null || ufList.isEmpty()){
                //処理対象なし
                return;
            }
            LOG.debug("##--SandBlastDownloadPolling checkSandBlastTimeout Start!  size:" + ufList.size());
            for(UploadFileInfo ufi: ufList){
                try {                    
                    //TODO :::UT:::Start v2.2.1 スリープテスト
                    if(VerifyUtil.UT_MODE){
                        int utSleep = VerifyUtil.getUTArgValueInt(ufi.getFileNameOrg(), "#UT_SBTIMEOUT#", "S", 0);
                        if(utSleep > 0){
                            VerifyUtil.outputUtLog(LOG, "", false, "Sleep(%d)", utSleep);
                            Thread.sleep(utSleep*1000);
                        }
                    }
                    //TODO :::UT:::End v2.2.1 スリープテスト*/
                    LOG.warn("#! --SandBlastDownloadPolling checkSandBlastTimeout [UploadFileInfoId:{}, ReceiveInfoId:{}, ReceiveFileId:{}, FileNameOrg:{}]", 
                            ufi.getFileId(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileNameOrg());
                    
                    //リトライカウントアップ処理
                    if(ugiManager.retryCountUpFileInfo(ufi, "Receive Timeout.", true)){
                        //リトライカウントオーバーなのでステップをVotiroアップロード待ちにしてVotiroアップロードに進む。
                        LOG.warn("#! ---SandBlastDownloadPolling checkSandBlastTimeout　リトライカウントオーバー (FileId:{}, Step:{}",
                                ufi.getFileId(), ufi.getStep());
                        ugiManager.updateStep(ufi, CommonEnum.StepKbn.VotiroUploadWait);
                    }
                } catch (Throwable e){
                    LOG.error("#! SandBlastダウンロードポーリング　タイムアウト処理中にエラーが発生しました。"
                            + " UploadFileInfoId:" + ufi.getFileId() + ", errMsg=" + e.getMessage(), e);
                }
            }
            LOG.debug("##--SandBlastDownloadPolling checkSandBlastTimeout End!");            
        } catch (Throwable e) {
            LOG.error("#! SandBlastタイムアウトチェック処理中にエラーが発生しました。　errMsg=" + e.getMessage(), e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }          
    }
    
}
