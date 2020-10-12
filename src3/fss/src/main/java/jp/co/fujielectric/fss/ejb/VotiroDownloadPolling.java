package jp.co.fujielectric.fss.ejb;

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
import jp.co.fujielectric.fss.logic.ServletManager;
import jp.co.fujielectric.fss.logic.UploadGroupInfoManager;
import jp.co.fujielectric.fss.logic.VotiroProcessLogic;
import jp.co.fujielectric.fss.service.UploadFileInfoService;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;

/**
 * ポーリング用Ejb
 * メールエントランス
 */
@Singleton
@DependsOn({"VotiroUploadPolling"})  //DependsOnで先に生成されるべきejbクラスを指定し順に生成されるようにする。（そうしないとLOGが出力されない）
@Startup    //Startupを指定することで、サーバー起動時にインスタンスが生成され、それにより@PostConstructのメソッドが呼ばれる
@TransactionManagement(TransactionManagementType.BEAN)  //pollingのトランザクションタイムアウトによってServerログにエラーが出力されないように。
public class VotiroDownloadPolling extends PollingBase{

    /**
     * スケジュール設定項目名（Setting.propertiesの項目名）
     */    
    private final String PROP_TIMER = "votiroDownloadPollingTimer";

    /**
     * 機能の有効/無効設定項目名（Setting.propertiesの項目名）
     */    
    private final String PROP_ENABLE_FLG = "enable_polling";
    
    @Inject
    ServletManager servletManager;

    @Inject
    private VotiroProcessLogic votiroLogic;

    @Inject
    UploadGroupInfoManager ugiManager;
    
    @Inject
    UploadFileInfoService uploadFileInfoService;
    
    public VotiroDownloadPolling() {
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
            List<UploadFileInfo> ufList = uploadFileInfoService.findUploadFileInfoByStep(StepKbn.VotiroDownloadWait, pollingOwner);
            if(ufList == null || ufList.isEmpty()){
                //処理対象なし
                return;
            }
            LOG.debug("##--VotiroDownloadPolling timerProcess Start!  size:" + ufList.size());
            for(UploadFileInfo ufi: ufList){
                try {
                    //TODO :::UT:::Start v2.2.1 スリープテスト
                    if(VerifyUtil.UT_MODE){
                        int utSleep = VerifyUtil.getUTArgValueInt(ufi.getFileNameOrg(), "#UT_VOTIRODOWNLOAD#", "S", 0);
                        if(utSleep > 0){
                            VerifyUtil.outputUtLog(LOG, "", false, "Sleep(%d)", utSleep);
                            Thread.sleep(utSleep*1000);
                        }
                    }
                    //TODO :::UT:::End v2.2.1 スリープテスト*/
                    
                    //DBから最新情報を取り直す
                    ufi = uploadFileInfoService.findNew(ufi.getFileId());
                    if(ufi == null || ufi.getStep() != StepKbn.VotiroDownloadWait.value){
                        //他の処理でステップが変更されていたらスキップする。
                        continue;
                    }
                    LOG.debug("  -- VotiroDownloadPolling [UploadFileInfoId:{}, ReceiveInfoId:{}, ReceiveFileId:{}, FileNameOrg:{}]", 
                            ufi.getFileId(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileNameOrg());
                    
                    //Votiroダウンロード処理
                    if( votiroLogic.execVotiroDownload(ufi) ){

                        //[v2.2.3]
                        //Votiroの無害化が正常終了したときの情報を登録
                        ufi.setVotiroCompDate(DateUtil.getSysDateExcludeMillis());      //votiroの無害化が終了（ステータスが完了、エラー、ブロック）した時刻
                        
                        //ステップ更新（ダウンロード済み）
                        ugiManager.updateStep(ufi, StepKbn.VotiroDownloaded);
                    }
                } catch (FssException e) {
                    //リトライ対象エラー発生時（ログは出力済み）
                    try{
                        //リトライカウントアップ処理
                        if(ugiManager.retryCountUpFileInfo(ufi, e.getMessage(), false)){
                            //リトライカウントオーバーなのでステップをキャンセルに更新する
                            LOG.debug("#-VotiroDownloadPolling リトライカウントオーバー (FileId:{}, Step:{}",
                                    ufi.getFileId(), ufi.getStep());

                            //無害化レポート取得失敗エラーの場合
                            if (e.isFlg()) {
                                //無害化ファイルダウンロード自体は成功して無害化レポートファイルのダウンロードに失敗した場合は
                                //成功として処理を継続する。
                                ufi = ugiManager.updateStep(ufi, StepKbn.VotiroDownloaded);
                            }
                            //無害化エンジンエラーの場合
                            else {
                                //無害化ファイルダウンロードが失敗した場合はキャンセルとする

                                //[v2.2.3]
                                //Votiroの監視、ファイルダウンロード時の異常エラーを登録
                                ufi.setVotiroCompDate(DateUtil.getSysDateExcludeMillis());          //リトライカウントオーバした時刻
                                ufi.setErrInfo(CommonEnum.ProcResultKbn.DOWNLOAD_ERROR.value);      //４：Votiroの監視、ファイルダウンロード時の異常
                                ufi.setErrDetails(Integer.toString(e.getCode()));                   //HTTPのレスポンスコード
                                ufi.setErrFile(CommonEnum.FileSanitizeResultKbn.FILE_ERROR.value);  //1：対象ファイルで異常検出
                                
                                ufi = ugiManager.updateStep(ufi, StepKbn.Cancel);
                            }
                        }                            
                    }catch(Exception e2){
                        LOG.error("#! Votiroダウンロードポーリング　リトライカウントアップ処理中にエラーが発生しました。"
                                + " UploadFileInfoId:" + ufi.getFileId() + ", errMsg=" + e.getMessage(), e);                            
                    }
                } catch (Throwable e){
                    LOG.error("#! Votiroダウンロードポーリング処理中にエラーが発生しました。"
                            + " UploadFileInfoId:" + ufi.getFileId() + ", errMsg=" + e.getMessage(), e);
                }
            }    
            LOG.debug("##--VotiroDownloadPolling timerProcess End!");            
        } catch (Throwable e) {
            LOG.error("#! Votiroダウンロードポーリング処理中にエラーが発生しました。　errMsg=" + e.getMessage(), e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }        
    } 
}
