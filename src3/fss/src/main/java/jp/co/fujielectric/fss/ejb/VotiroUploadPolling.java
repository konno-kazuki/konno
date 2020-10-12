package jp.co.fujielectric.fss.ejb;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.ejb.DependsOn;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Timeout;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.inject.Inject;
import jp.co.fujielectric.fss.data.CommonEnum;
import jp.co.fujielectric.fss.data.CommonEnum.StepKbn;
import jp.co.fujielectric.fss.entity.UploadFileInfo;
import jp.co.fujielectric.fss.exception.FssException;
import jp.co.fujielectric.fss.logic.UploadGroupInfoManager;
import jp.co.fujielectric.fss.logic.VotiroEntranceLogic;
import jp.co.fujielectric.fss.service.UploadFileInfoService;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;

/**
 * ポーリング用Ejb
 * メールエントランス
 */
@Singleton
@DependsOn({"SandBlastDownloadPolling"})  //DependsOnで先に生成されるべきejbクラスを指定し順に生成されるようにする。（そうしないとLOGが出力されない）
@Startup    //Startupを指定することで、サーバー起動時にインスタンスが生成され、それにより@PostConstructのメソッドが呼ばれる
@TransactionManagement(TransactionManagementType.BEAN)  //pollingのトランザクションタイムアウトによってServerログにエラーが出力されないように。
public class VotiroUploadPolling extends PollingBase{

    /**
     * スケジュール設定項目名（Setting.propertiesの項目名）
     */    
    private final String PROP_TIMER = "votiroUploadPollingTimer";

    /**
     * 機能の有効/無効設定項目名（Setting.propertiesの項目名）
     */    
    private final String PROP_ENABLE_FLG = "enable_polling";
    
    @Inject
    private VotiroEntranceLogic votiroLogic;

    @Inject
    UploadGroupInfoManager ugiManager;
    
    @Inject
    UploadFileInfoService uploadFileInfoService;
    
    public VotiroUploadPolling() {
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
            List<UploadFileInfo> ufList = uploadFileInfoService.findUploadFileInfoByStep(StepKbn.VotiroUploadWait, pollingOwner);
            if(ufList == null || ufList.isEmpty()){
                //処理対象なし
                return;
            }
            LOG.debug("##--VotiroUploadPolling timerProcess Start!  size:" + ufList.size());
            for(UploadFileInfo ufi: ufList){
                try {
                    //処理済みレコード内に同一ファイルのレコードがあるかチェックする
                    if(chkSameFileInList(ufi, ufList)){
                        //処理済みレコード内に同一ファイルのレコードがあるのでスキップする。
                        LOG.debug("  --- VotiroUploadPolling. Skip.（ループ内に処理済みの同一ファイルがあるため） [UploadFileInfoId:{}, ReceiveInfoId:{}, ReceiveFileId:{}, FileNameOrg:{}]", 
                            ufi.getFileId(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileNameOrg());
                        ufi.setSkipped(true);
                        continue;
                    }
                    
                    //TODO :::UT:::Start v2.2.1 スリープテスト
                    if(VerifyUtil.UT_MODE){
                        int utSleep = VerifyUtil.getUTArgValueInt(ufi.getFileNameOrg(), "#UT_VOTIROUPLOAD#", "S", 0);
                        if(utSleep > 0){
                            VerifyUtil.outputUtLog(LOG, "", false, "Sleep(%d)", utSleep);
                            Thread.sleep(utSleep*1000);
                        }
                    }
                    //TODO :::UT:::End v2.2.1 スリープテスト*/
                    //DBから最新情報を取り直す
                    ufi = uploadFileInfoService.findNew(ufi.getFileId());
                    if(ufi == null || ufi.getStep() != StepKbn.VotiroUploadWait.value){
                        //他の処理でステップが変更されていたらスキップする。
                        continue;
                    }                   

                    //同ファイル進行中チェック処理
                    if(ugiManager.chkSameFileProcessing(ufi)){
                        //同ファイルが進行中なので処理せず進行中ジョブの結果待ち。
                        LOG.debug("  -- VotiroUploadPolling. Skip.（進行中の同一ファイルがあるため） [UploadFileInfoId:{}, ReceiveInfoId:{}, ReceiveFileId:{}, FileNameOrg:{}]",
                            ufi.getFileId(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileNameOrg());
                        continue;
                    }
                    LOG.debug("  -- VotiroUploadPolling [UploadFileInfoId:{}, ReceiveInfoId:{}, ReceiveFileId:{}, FileNameOrg:{}]", 
                            ufi.getFileId(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileNameOrg());
                    
                    //Votiroアップロード処理
                    StepKbn newStep = votiroLogic.execVotiroUpload(ufi);
                    if(newStep.value != ufi.getStep()){
                        //ステップ更新
                        ugiManager.updateStep(ufi, newStep);
                    }
                } catch (FssException e) {
                    //リトライ対象エラー発生時（ログは出力済み）
                    try{
                        //リトライカウントアップ処理
                        if(ugiManager.retryCountUpFileInfo(ufi, e.getMessage(), false)){
                            //リトライカウントオーバーなのでステップをキャンセルに更新する
                            LOG.debug("#-VotiroUploadPolling リトライカウントオーバー (FileId:{}, Step:{}",
                                    ufi.getFileId(), ufi.getStep());

                            //[v2.2.3]
                            //Votiroへのファイルアップロード異常時のエラーを登録
                            ufi.setVotiroCompDate(DateUtil.getSysDateExcludeMillis());          //リトライカウントオーバ時刻で更新
                            ufi.setErrInfo(CommonEnum.ProcResultKbn.UPLOAD_ERROR.value);        //２：Votiroへのファイルアップロード時の異常
                            ufi.setErrDetails(Integer.toString(e.getCode()));                   //HTTPのレスポンスコード
                            ufi.setErrFile(CommonEnum.FileSanitizeResultKbn.FILE_ERROR.value);  //1：対象ファイルで異常検出

                            ufi = ugiManager.updateStep(ufi, StepKbn.Cancel);
                        }
                    }catch(Exception e2){
                        LOG.error("#! Votiroアップロードポーリング リトライカウントアップ処理中にエラーが発生しました。"
                                + " UploadFileInfoId:" + ufi.getFileId() + ", errMsg=" + e.getMessage(), e);                            
                    }
                } catch (Throwable e){
                    LOG.error("#! Votiroアップロードポーリング処理中にエラーが発生しました。"
                            + " UploadFileInfoId:" + ufi.getFileId() + ", errMsg=" + e.getMessage(), e);
                }
            }
            LOG.debug("##--VotiroUploadPolling timerProcess End!");            
        } catch (Throwable e) {
            LOG.error("#! Votiroアップロードポーリング処理中にエラーが発生しました。　errMsg=" + e.getMessage(), e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }        
    }
}
