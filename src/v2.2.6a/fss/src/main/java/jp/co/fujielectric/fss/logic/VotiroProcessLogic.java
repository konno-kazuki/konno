package jp.co.fujielectric.fss.logic;

import java.io.File;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.data.VotStatus;
import jp.co.fujielectric.fss.entity.UploadFileInfo;
import jp.co.fujielectric.fss.entity.UploadGroupInfo;
import jp.co.fujielectric.fss.exception.FssException;
import jp.co.fujielectric.fss.service.UploadFileInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.FileUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

/**
 * Votiro状態確認、ダウンロードクラス
 */
@RequestScoped
public class VotiroProcessLogic {

    @Inject
    private Logger LOG;

    @Inject
    private UploadFileInfoService uploadFileInfoService;

    @Inject
    private UploadGroupInfoManager uploadGroupInfoManager;

    @Inject
    private VotiroManager votiroManager;

    /**
     * Votiroダウンロード
     * @param ufi
     * @return true:無害化済み(Done,Error,Blocked)　false:無害化中
     * @throws FssException エラー（要リトライ）
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean execVotiroDownload(UploadFileInfo ufi) throws FssException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        String proc = "VotiroGetStatus";
        boolean flgVotiroDownloaded = false;
        try {
            //UploadGroupInfo取得
            UploadGroupInfo ugi = uploadGroupInfoManager.findUploadGroupInfo(ufi.getId());
            if(ugi == null){
                //UploadGroupInfoが見つからない場合。（通常あり得ないが）
                throw new RuntimeException("UploadGroupInfoが見つかりません。 UploadFileInfoId:"+ufi.getFileId() + " UploadGroupInfoId:"+ufi.getId());
            }            
            
            //--------------------------------------
            //Votiroに対するステータス確認
            //--------------------------------------
            try {
                //TODO :::UT:::Start v2.2.1 Votiroリザルトテスト
                if(VerifyUtil.UT_MODE){
                    //元ファイル名にUT用文字列がありステータスコード指定がある場合Votiro処理結果偽装を行う
                    int utCode, utRetry;
                    if(!StringUtils.isEmpty(ufi.getFileNameOrg())){
                        utCode =VerifyUtil.getUTArgValueInt(ufi.getFileNameOrg(), "#UT_VOTIROSTATUS#", "CODE", 200); //ステータスコード
                        utRetry = VerifyUtil.getUTArgValueInt(ufi.getFileNameOrg(), "#UT_VOTIROSTATUS#", "R", 0);//対象リトライカウント
                    }else{
                        //メール本文はMailDate(ticketのメールフォルダ名）で偽装する
                        utCode =VerifyUtil.getUTArgValueInt(ugi.getProcDate(), "#VDL#", "C", 200); //ステータスコード
                        utRetry = VerifyUtil.getUTArgValueInt(ugi.getProcDate(), "#VDL#", "R", 0);//対象リトライカウント                        
                    }
                    if(utCode != 200 && ufi.getRetryCount() <= utRetry){
                        if(utCode == -1){ //例外
                            throw new java.net.NoRouteToHostException("UT_Votiroステータス確認例外テスト");
                        }else{          //200以外のStatusCode
                            //200以外はエラーとして検知済み例外スロー
                            throw new FssException( utCode);
                        }
                    }
                }
                //TODO :::UT:::End v2.2.1 Votiroリザルトテスト*/                
                
                VotStatus st = votiroManager.getStatus(ufi.getRequestId(), ufi.getVotiroIP());
                //ステータス確認成功
                LOG.debug("#VotiroStatus [UploadFileInfo.fileId:{}, requestId:{}, votiroIP:{}, status:{}]",
                        ufi.getFileId(), ufi.getRequestId(), ufi.getVotiroIP(), st.Status);
                if(st.Status.equals(VotStatus.EnmStatus.LimitExceeded.name())){
                    //ステータスが「LimitExceeded」の場合はエラー（リトライ対象）
                    //UploadFileInfoのStatusも更新しない。
                    LOG.warn("#!Votiroステータス確認 LimitExceeded. Status:{}  --ReceiveInfoId:{} --ReceiveFileId:{} --UploadFileInfoId:{} --FileNameOrg:{} --"
                            ,st.Status, ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileId(), ufi.getFileNameOrg());
                    throw new FssException( "VotiroGetStatus Error. LimitExceeded."); //呼出元でリトライ対応
                }else if (st.Status.equals(VotStatus.EnmStatus.Processing.name())
                 || st.Status.equals(VotStatus.EnmStatus.Queued.name())){
                    //ステータスが「Processing」「Queued」の場合は無害化未完了なので抜ける
                    //UploadFileInfoのStatusも更新しない。
                    return false;
                }else if (st.Status.equals(VotStatus.EnmStatus.Done.name())
                            || st.Status.equals(VotStatus.EnmStatus.Error.name())
                            || st.Status.equals(VotStatus.EnmStatus.Blocked.name())) {
                    // Done,Error,Blocked の場合、Votiro無害化処理完了と判断し、無害化フラグをonとする。
                    ufi.setStatus(st.Status);
                    ufi.setSanitizeFlg(true);
                }else{
                    //その他のステータスの場合
                    LOG.warn("#!Votiroステータス確認 Unknown Status:{}   --ReceiveInfoId:{} --ReceiveFileId:{} --UploadFileInfoId:{} --FileNameOrg:{} --"
                            ,st.Status, ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileId(), ufi.getFileNameOrg());
                    throw new FssException( "VotiroGetStatus Unknown Status;" + st.Status); //呼出元でリトライ対応
                }
                uploadFileInfoService.edit(ufi);
                uploadFileInfoService.flush();                
            } catch (FssException e) {
               //Votiroステータス確認結果異常(200以外のStatusCode、またはステータス異常）
                LOG.warn("#!Votiroステータス確認に失敗しました。（リトライあり） --StatusCode:{} --votiroIP:{} --ReceiveInfoId:{} --ReceiveFileId:{} --UploadFileInfoId:{} --FileNameOrg:{} --", 
                        e.getCode(), ufi.getVotiroIP(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileId(), ufi.getFileNameOrg());
                int statusCode = e.getCode();
                if(statusCode != 0 && statusCode != 200){
                    //ステータスコードのエラー
                    //検知済み例外をスローしてPollingでリトライ対応
                    throw new FssException(statusCode, "VotiroGetStatus Error. StatusCode:" + statusCode);
                }else{
                    //その他（ステータスエラ－）
                    throw e; //そのまま検知済み例外をスローしてPollingでリトライ対応
                }
            }
            if (ufi.getStatus().equals(VotStatus.EnmStatus.Done.name())){
                //[Done]の場合、無害化済みファイルをVotiroからダウンロードする
                //--------------------------------------
                //Votiroに対するダウンロード処理
                //--------------------------------------
                proc = "VotiroDownload";
                try {
                    //ファイルパス名を生成
                    String folder = CommonUtil.getFolderVotiro(ugi);    //Votiroフォルダ取得
                    File dldFile = new File(folder, ufi.getFileName());
                    
                    //TODO :::UT:::Start v2.2.1 Votiroダウンロードテスト
                    if(VerifyUtil.UT_MODE){
                        //元ファイル名にUT用文字列がありステータスコード指定がある場合Votiro処理結果偽装を行う
                        int utCode =VerifyUtil.getUTArgValueInt(ufi.getFileNameOrg(), "#UT_VOTIRODOWNLOAD#", "CODE", 200);//ステータスコード
                        int utRetry = VerifyUtil.getUTArgValueInt(ufi.getFileNameOrg(), "#UT_VOTIRODOWNLOAD#", "R", 0);//対象リトライカウント
                        if(utCode != 200 && ufi.getRetryCount() <= utRetry){
                            if(utCode == -1){ //例外
                                throw new java.net.NoRouteToHostException("UT_Votiroダウンロード例外テスト");
                            }else{          //200以外のStatusCode
                                //200以外はエラーとして検知済み例外スロー
                                throw new FssException( utCode); //例外スロー                    
                            }
                        }
                    }
                    //TODO :::UT:::End v2.2.1 Votiroダウンロードテスト*/                
                    
                    //Votiroダウンロード
                    votiroManager.getDownload(ufi.getRequestId(), dldFile.getPath(), ufi.getVotiroIP());
                    ufi.setVotiroFilePath(dldFile.getPath());
                    uploadFileInfoService.edit(ufi);
                    uploadFileInfoService.flush();                    
                } catch (FssException e) {
                    //Votiroステータス確認結果異常(200以外のステータス）
                     LOG.warn("#!Votiroダウンロードに失敗しました。（リトライあり） --StatusCode:{}  --votiroIP:{} --ReceiveInfoId:{} --ReceiveFileId:{} --UploadFileInfoId:{} --FileNameOrg:{} --", 
                             e.getCode(), ufi.getVotiroIP(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileId(), ufi.getFileNameOrg());
                    //200以外はエラーとして検知済み例外スローしてPollingでリトライ対応
                    throw new FssException(e.getCode(), "VotiroDownload Error. StatusCode:" + e.getCode()); //例外スロー
                }
            }
            flgVotiroDownloaded = true;

            //[v2.2.3]
            //下記の条件に当てはまる場合、Votiroの無害化レポートを取得する
            //条件１：VotiroステータスがError、又はBlocked
            //条件２：無害化が終了したファイルの拡張子がアーカイブ拡張子
            //※メール本体は対象外とする
            try {
                if (!StringUtils.isEmpty(ufi.getFileNameOrg()) &&                   //メールファイル本体は対象外
                    (ufi.getStatus().equals(VotStatus.EnmStatus.Error.name()) ||    //(1)無害化のステータスがErrorの場合
                    ufi.getStatus().equals(VotStatus.EnmStatus.Blocked.name()) ||   //(2)無害化のステータスがBlockedの場合
                    FileUtil.isArchiveFile(ufi.getFileName()))) {                   //(3)アーカイブファイルの場合

                    File rptFile = DeleteReasonFileLogic.getReportFile(ugi, ufi);    //レポートファイル
                    
                    //TODO :::UT:::Start v2.2.3 Votiro Reportファイルダウンロードテスト
                    if(VerifyUtil.UT_MODE){
                        //元ファイル名にUT用文字列がありステータスコード指定がある場合Votiro処理結果偽装を行う
                        int utCode =VerifyUtil.getUTArgValueInt(ufi.getFileNameOrg(), "#UT_VOTIROREPORT#", "CODE", 200);//ステータスコード
                        int utRetry = VerifyUtil.getUTArgValueInt(ufi.getFileNameOrg(), "#UT_VOTIROREPORT#", "R", 0);//対象リトライカウント
                        if(utCode != 200 && ufi.getRetryCount() <= utRetry){
                            if(utCode == -1){ //例外
                                throw new java.net.NoRouteToHostException("UT_Votiroレポートダウンロード例外テスト");
                            }else{          //200以外のStatusCode
                                //200以外はエラーとして検知済み例外スロー
                                throw new FssException( utCode); //例外スロー                    
                            }
                        }
                    }
                    //TODO :::UT:::End v2.2.3 Votiro Reportファイルダウンロードテスト*/       
                    
                    //無害化レポートを取得
                    votiroManager.getReport(ufi.getRequestId(), rptFile.getPath(), ufi.getVotiroIP());                    
                    ufi.setReportFilePath(rptFile.getPath());   //レポートファイルパス
                    uploadFileInfoService.edit(ufi);
                    uploadFileInfoService.flush();                                        
                }
            } catch (FssException e) {
                //Votiroステータス確認結果異常(200以外のステータス）
                 LOG.warn("#! 無害化レポートのダウンロードに失敗しました。（リトライあり） --StatusCode:{}  --votiroIP:{} --ReceiveInfoId:{} --ReceiveFileId:{} --UploadFileInfoId:{} --FileNameOrg:{} --",
                         e.getCode(), ufi.getVotiroIP(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileId(), ufi.getFileNameOrg());
                //200以外はエラーとして検知済み例外スローしてPollingでリトライ対応
                throw new FssException(e.getCode(), "VotiroReport Error. StatusCode:" + e.getCode()); //例外スロー
            }

            return true;    //Votiroでの無害化済み(Error,Blockedも含む）
        }catch (FssException e){    //検知済み例外の場合
            //そのまま検知済み例外FssExceptionをスローして呼出元でリトライ対応する
            //無害化済みファイルのダウンロードは成功していることが判別できるように、FlgをTrueとする。
            e.setFlg(flgVotiroDownloaded);
            throw e;
        } catch (Throwable e) {
            LOG.error("#! Votiroダウンロード({}) 例外発生。（リトライあり）errMsg:{}  --votiroIP:{} --ReceiveInfoId:{} --ReceiveFileId:{} --UploadFileInfoId:{} --FileNameOrg:{} --", 
                            proc,  e.toString(), ufi.getVotiroIP(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileId(), ufi.getFileNameOrg(), e);
            //例外が発生した場合、FssExceptionをスローして呼出元でリトライ対応する
            throw new FssException(-1, flgVotiroDownloaded, proc + " Error. Exception:" + e.getMessage(), e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }    
}
