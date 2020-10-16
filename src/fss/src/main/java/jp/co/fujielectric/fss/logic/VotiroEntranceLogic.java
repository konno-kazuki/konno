package jp.co.fujielectric.fss.logic;

import java.io.File;
import java.io.FileInputStream;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.data.CommonEnum;
import jp.co.fujielectric.fss.data.CommonEnum.StepKbn;
import jp.co.fujielectric.fss.data.Tuple;
import jp.co.fujielectric.fss.entity.UploadFileInfo;
import jp.co.fujielectric.fss.exception.FssException;
import jp.co.fujielectric.fss.service.UploadFileInfoService;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.Logger;

/**
 *
 */
@RequestScoped
public class VotiroEntranceLogic {

    @Inject
    private Logger LOG;

    @Inject
    private VotiroManager votiroManager;

    @Inject
    private UploadFileInfoService uploadFileInfoService;

    @Inject
    private SanitizeHelper sanitizeHelper;
    
    /**
     * Votiroアップロード
     * @param ufi
     * @return 次のステップ
     * @throws FssException エラー（要リトライ）
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public StepKbn execVotiroUpload(UploadFileInfo ufi) throws FssException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        StepKbn step;
        try {
            //--------------------------
            //対象送信ファイル取得
            //--------------------------
            File uploadFilePath = sanitizeHelper.getUploadTargetFile(null, ufi);            
            String fname = ufi.getFileName();  //無害化処理用のファイル名（ID+拡張子）
            LOG.debug("#VotiroUpload. [UploadFilePath:{},  FileName:{}", uploadFilePath, fname);
            
            ufi.setUploadFilePath(uploadFilePath.getPath());                //Votiroアップロード対象ファイル
            ufi.setVotiroStartDate(DateUtil.getSysDateExcludeMillis());     //votiroへの要求日時　(v2.2.3)
            uploadFileInfoService.edit(ufi);    //更新日付をセット
            uploadFileInfoService.flush();      //アップロード処理でのエラー発生も考慮して一旦DB更新（≠コミット）
            
            //--------------------------
            //Votiroアップロード実行
            //-------------------------- 
            try(FileInputStream is = new FileInputStream(uploadFilePath)) { 
                //TODO :::UT:::Start v2.2.1 Votiroリザルトテスト
                if(VerifyUtil.UT_MODE){
                    //元ファイル名にUT用文字列がありステータスコード指定がある場合Votiro処理結果偽装を行う
                    int utCode =VerifyUtil.getUTArgValueInt(ufi.getFileNameOrg(), "#UT_VOTIROUPLOAD#", "CODE", 200);//ステータスコード
                    int utRetry = VerifyUtil.getUTArgValueInt(ufi.getFileNameOrg(), "#UT_VOTIROUPLOAD#", "R", 0);//対象リトライカウント
                    if(utCode != 200 && ufi.getRetryCount() <= utRetry){
                        if(utCode == -1){ //例外
                            throw new java.net.NoRouteToHostException("UT_Votiroアップロード例外テスト");
                        }else{          //200以外のStatusCode
                            throw new FssException(utCode);
                        }
                    }
                }
                //TODO :::UT:::End v2.2.1 Votiroリザルトテスト*/
                
                Tuple<String, String> t = votiroManager.postUploadFile(
                    fname, is, uploadFilePath.length());
                //アップロード成功時
                ufi.setRequestId(t.getValue1());    //TupleのValue1にはRequestIdがセットされている。
                ufi.setVotiroIP(t.getValue2());     //VotiroIPアドレス（TupleのValue2はCookie値(sdsip=VotiroのIP)がセットされている。）
                //UploadFileInfoのStepをダウンロード待ちにする
                //※uploadFileInfo.stepの変更はここではせず、呼出し側ポーリングで実施。（ステップ更新は他のレコードへの影響もあり、別トランザクションにて一括して処理するため）
                step = StepKbn.VotiroDownloadWait;    //uploadFileInfo.stepの変更はここではしない。（ステップ更新は他のレコードへの影響もあり、別トランザクションにて一括して処理するため）
            } catch (FssException e) {
                //Votiroアップロード結果異常(StatusCodeが200以外）の場合、StatusCodeによってリトライ有無を切替える
                int statusCode = e.getCode();
                if (statusCode == HttpStatus.SC_BAD_REQUEST || statusCode == HttpStatus.SC_CONFLICT) {
                    // VOTIRO処理にて StatusCode=400（Bad Request）、409（Conflict）の場合、無害化失敗とする（リトライなし）                    
                    LOG.warn("#!Votiroアップロードに失敗しました。（リトライなし） --StatusCode:{} --ReceiveInfoId:{} --ReceiveFileId:{} --UploadFileInfoId:{} --FileNameOrg:{} --", 
                            statusCode, ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileId(), ufi.getFileNameOrg());
                    //Complete処理でエラーとなるようにする
                    ufi.setStatus("");
                    ufi.setSanitizeFlg(true);
                    ufi.setErrorInfo("VotiroUpload Error. StatusCode:" + statusCode);   //エラー情報をセットする
                    //Votiro完了情報セット（v2.2.3)
                    ufi.setVotiroCompDate(DateUtil.getSysDateExcludeMillis());          //エラー時刻で更新
                    ufi.setErrInfo(CommonEnum.ProcResultKbn.UPLOAD_ERROR.value);        //２：Votiroへのファイルアップロード時の異常
                    ufi.setErrDetails(Integer.toString(statusCode));                    //HTTPのレスポンスコード
                    ufi.setErrFile(CommonEnum.FileSanitizeResultKbn.FILE_ERROR.value);  //1：対象ファイルで異常検出
                    
                    //UploadFileInfoのStepをダウンロード済みにしてComplete処理に進むようにする
                    //※uploadFileInfo.stepの変更はここではせず、呼出し側ポーリングで実施。（ステップ更新は他のレコードへの影響もあり、別トランザクションにて一括して処理するため）
                    step = StepKbn.VotiroDownloaded;    
                } else {
                    //それ以外（StatusCode=429（Too many requests）、404（Votiro停止中など））の場合、リトライ対象（カウントアップあり）としてExceptonをスローする
                    LOG.warn("#!Votiroアップロードに失敗しました。（リトライあり） --StatusCode:{} --ReceiveInfoId:{} --ReceiveFileId:{} --UploadFileInfoId:{} --FileNameOrg:{} --", 
                            statusCode, ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileId(), ufi.getFileNameOrg());                    
                    //検知済み例外をスローしてPollingでリトライ対応
                    throw new FssException(statusCode, "VotiroUpload Error. StatusCode:" + statusCode);
                }
            }
            uploadFileInfoService.edit(ufi);    //更新日付をセット
            uploadFileInfoService.flush();
            return step;            
        }catch (FssException e){    //検知済み例外の場合
            //そのまま検知済み例外をスローして呼出元でリトライ対応する
            throw e;
        }catch (Throwable e){
            LOG.error("#! Votiroアップロード　例外発生。 （リトライあり）errMsg:{}  --ReceiveInfoId:{} --ReceiveFileId:{} --UploadFileInfoId:{} --FileNameOrg:{} --", 
                            e.toString(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileId(), ufi.getFileNameOrg(), e);
            //例外が発生した場合、検知済み例外(FssException)をスローして呼出元でリトライ対応する
            throw new FssException(-1, "VotiroUpload Error. Exception:" + e.getMessage(), e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }
}
