package jp.co.fujielectric.fss.ejb;

import java.util.ArrayList;
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
import jp.co.fujielectric.fss.entity.UploadFileInfo;
import jp.co.fujielectric.fss.exception.FssException;
import jp.co.fujielectric.fss.logic.SandblastLogic;
import jp.co.fujielectric.fss.logic.UploadGroupInfoManager;
import jp.co.fujielectric.fss.service.UploadFileInfoService;
import jp.co.fujielectric.fss.util.VerifyUtil;

/**
 * ポーリング用Ejb
 * メールエントランス
 */
@Singleton
@DependsOn({"MailEntrancePolling"})  //DependsOnで先に生成されるべきejbクラスを指定し順に生成されるようにする。（そうしないとLOGが出力されない）
@Startup    //Startupを指定することで、サーバー起動時にインスタンスが生成され、それにより@PostConstructのメソッドが呼ばれる
@TransactionManagement(TransactionManagementType.BEAN)  //pollingのトランザクションタイムアウトによってServerログにエラーが出力されないように。
public class SandBlastUploadPolling extends PollingBase{

    /**
     * スケジュール設定項目名（Setting.propertiesの項目名）
     */    
    private final String PROP_TIMER = "sandBlastUploadPollingTimer";

    /**
     * 機能の有効/無効設定項目名（Setting.propertiesの項目名）
     */    
    private final String PROP_ENABLE_FLG = "enable_polling";

    @Inject
    UploadGroupInfoManager ugiManager;

    @Inject
    UploadFileInfoService uploadFileInfoService;
    
    @Inject
    private SandblastLogic sandblastLogic;
    
    public SandBlastUploadPolling() {
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
            List<UploadFileInfo> ufList = uploadFileInfoService.findUploadFileInfoByStep(CommonEnum.StepKbn.StartWait, pollingOwner);
            if(ufList == null || ufList.isEmpty()){
                //処理対象なし
                return;
            }
            LOG.debug("##--SandBlastUploadPolling timerProcess Start!  size:" + ufList.size());
            for(UploadFileInfo ufi: ufList){
                try {
                    //処理済みレコード内に同一ファイルのレコードがあるかチェックする
                    if(chkSameFileInList(ufi, ufList)){
                        //処理済みレコード内に同一ファイルのレコードがあるのでスキップする。
                        LOG.debug("  --- SandBlastUploadPolling. Skip.（ループ内に処理済みの同一ファイルがあるため） [UploadFileInfoId:{}, ReceiveInfoId:{}, ReceiveFileId:{}, FileNameOrg:{}]", 
                            ufi.getFileId(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileNameOrg());
                        ufi.setSkipped(true);
                        continue;
                    }
                    
                    //TODO :::UT:::Start v2.2.1 スリープテスト
                    if(VerifyUtil.UT_MODE){
                        int utSleep = VerifyUtil.getUTArgValueInt(ufi.getFileNameOrg(), "#UT_SANDBLASTUPLOAD#", "S", 0);
                        if(utSleep > 0){
                            VerifyUtil.outputUtLog(LOG, "", false, "Sleep(%d)", utSleep);
                            Thread.sleep(utSleep*1000);
                        }
                    }
                    //TODO :::UT:::End v2.2.1 スリープテスト*/
                    //DBから最新情報を取り直す
                    ufi = uploadFileInfoService.findNew(ufi.getFileId());
                    if(ufi == null || ufi.getStep() != CommonEnum.StepKbn.StartWait.value){
                        //他の処理でステップが変更されていたらスキップする。
                        continue;
                    }
                    //同ファイル進行中チェック処理
                    if(ugiManager.chkSameFileProcessing(ufi)){
                        //同ファイルが進行中なので処理せず進行中ジョブの結果待ち。
                        LOG.debug("  --- SandBlastUploadPolling. Skip.（進行中の同一ファイルがあるため） [UploadFileInfoId:{}, ReceiveInfoId:{}, ReceiveFileId:{}, FileNameOrg:{}]", 
                            ufi.getFileId(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileNameOrg());
                        continue;
                    }
                    LOG.debug("  -- SandBlastUploadPolling [UploadFileInfoId:{}, ReceiveInfoId:{}, ReceiveFileId:{}, FileNameOrg:{}]", 
                            ufi.getFileId(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileNameOrg());
                    
                    //SandBlastアップロード処理
                    CommonEnum.StepKbn newStep = sandblastLogic.execSandblastUpload(ufi);
                    if(newStep.value != ufi.getStep()){
                        //ステップ更新
                        ugiManager.updateStep(ufi, newStep);
                    }
                } catch (Throwable e) {
                    LOG.error("#! SandBlastアップロードポーリング処理中にエラーが発生しました。"
                        + " UploadFileInfoId:" + ufi.getFileId() + ", errMsg=" + e.getMessage(), e);                       
                    try{
                        //リトライカウントアップ処理
                        String errMsg = String.format("Send Error. (%s)", e.getMessage());
                        if(ugiManager.retryCountUpFileInfo(ufi, errMsg, false)){
                            //リトライカウントオーバーなのでステップをVotiroアップロード待ちにしてVotiroアップロードに進む。
                            LOG.warn("#-SandBlastUploadPolling リトライカウントオーバー (FileId:{}, Step:{})",
                                    ufi, ufi.getStep());
                            ugiManager.updateStep(ufi, CommonEnum.StepKbn.VotiroUploadWait);
                        }
                    }catch(Exception e2){
                        LOG.error("#! SandBlastアップロードポーリング リトライカウントアップ処理中にエラーが発生しました。"
                                + " UploadFileInfoId:" + ufi.getFileId() + ", errMsg=" + e.getMessage(), e);                            
                    }
                }
            }
            LOG.debug("##--SandBlastUploadPolling timerProcess End!");            
        } catch (Throwable e) {
            LOG.error("#! SandBlastアップロードポーリング処理中にエラーが発生しました。　errMsg=" + e.getMessage(), e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }        
    } 
}
