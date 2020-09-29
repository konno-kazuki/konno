package jp.co.fujielectric.fss.logic;

import java.io.File;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.data.CommonEnum.SandBlastKbn;
import jp.co.fujielectric.fss.data.CommonEnum.StepKbn;
import jp.co.fujielectric.fss.data.FssMimeMessage;
import jp.co.fujielectric.fss.entity.UploadFileInfo;
import jp.co.fujielectric.fss.entity.UploadGroupInfo;
import jp.co.fujielectric.fss.exception.FssException;
import jp.co.fujielectric.fss.service.UploadFileInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

//[v2.2.1]追加
/**
 * SandBlastアップロード処理用Logic
 */
@RequestScoped
public class SandblastLogic {

    @Inject
    private Logger LOG;

    @Inject
    UploadFileInfoService uploadFileInfoService;
    
    @Inject
    private UploadGroupInfoManager ugiManager;

    @Inject
    private MailManager mailManager;

    @Inject
    private SanitizeHelper sanitizeHelper;
    
    /**
     * Sandblastアップロード
     * @param ufi
     * @return 次のステップ
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public StepKbn execSandblastUpload(UploadFileInfo ufi) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "uploadFileInfoId=" + ufi.getFileId()));

        try {
            //--------------------------
            //対象送信ファイル取得
            //--------------------------
            UploadGroupInfo ugi = ugiManager.findUploadGroupInfo(ufi.getId());  //UploadGroupInfo取得            
            File uploadFilePath = sanitizeHelper.getUploadTargetFile(ugi, ufi);
            boolean flgSandblastOnly = (ufi.getSandBlastKbn() == SandBlastKbn.CHECK_ONLY.value);    //ふるまい検知のみかどうか
            String fname = ufi.getFileName();  //無害化処理用のファイル名（ID+拡張子）
            LOG.debug("#SandBlastUpload. [UploadFilePath:{}, flgSandBlastOnly:{}, FileName:{}]", uploadFilePath, flgSandblastOnly, fname);
            if(!uploadFilePath.exists()){
                //送信対象ファイルが存在しない場合
                throw new Exception("SandBlast送信対象ファイルが存在しません。(File:" + uploadFilePath + ")");
            }
            
            //TODO :::UT:::Start v2.2.1 SandBlast送信時エラーテスト
            if(VerifyUtil.UT_MODE){
                //元ファイル名にUT用文字列でリトライ回数指定がある場合、そのリトライ回数まで例外発生偽装する
                int utRetry = VerifyUtil.getUTArgValueInt(ufi.getFileNameOrg(), "#UT_SBUPLOAD#", "R", -1);//対象リトライカウント
                if(ufi.getRetryCount() >=0 && ufi.getRetryCount() <= utRetry){
                    throw new FssException("#UT#SandBlastUpload Exception.");
                }
            }
            //TODO :::UT:::End v2.2.1 SandBlast送信時エラーテスト*/
            
            //--------------------------
            //SandBlastアップロード実行
            //-------------------------- 
            mailManager.sendMailSandBlast(ugi.isMailFlg(), ufi.getFileId(), fname, uploadFilePath, flgSandblastOnly);

            //sandblastStartDateをセットする
            ufi.setSandblastStartDate(DateUtil.getSysDateExcludeMillis());
            uploadFileInfoService.edit(ufi);    
            uploadFileInfoService.flush();            
            
            //アップロード成功の場合、ステップをSandBlastアップロード中にする
            return StepKbn.SandBlastUploading;
        }catch (Throwable e){
            LOG.error("#! SandBlastアップロード　例外発生。 （リトライあり）errMsg:{}  --ReceiveInfoId:{} --ReceiveFileId:{} --UploadFileInfoId:{} --FileNameOrg:{} --", 
                            e.getMessage(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileId(), ufi.getFileNameOrg(), e);
            //RuntimeExceptionをスローして呼出元で例外処理をする
            throw new RuntimeException("Exception:" + e.getMessage(), e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }

    /**
     * SandBlastダウンロード処理結果通知用クラス
     */
    @Data    
    public class SandBlastDownloadResult
    {
        /**
         * UploadFileInfo
         */
        UploadFileInfo uploadFileInfo = null;
        
        /**
         * ふるまい検知フラグ
         */
        boolean isCheckOK = false;
        
        /**
         * エラーフラグ
         */
        boolean isError = false;

        /**
         * エラーメッセージ
         */
        String errMessage = null;
    }
    
    /**
     * Sandblastダウンロード
     * @param mailId mailQueueId
     * @param mailDate mailQueueのMailDate
     * @return 対象となるUploadFileInfoが特定できた場合はSandBlastDownloadResultで結果を返す
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public SandBlastDownloadResult execSandblastDownload(String mailId, String mailDate)
    {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "mailId=" + mailId, "mailDate=" + mailDate));
        UploadFileInfo ufi = null;
        SandBlastDownloadResult result = new SandBlastDownloadResult();
        try {
            //SandBlastリターンメール
            File mailFile = new File(CommonUtil.getFolderMailSandBlast(mailDate), mailId + ".eml");            
            //SandBlastリターンメールからMimeMessage取得
            FssMimeMessage mimeMessage = mailManager.getMimeMessage(mailFile);
            //件名からUploadFileInfoのIDを取得
            String subject = null;
            String fileId = null;
            try {
                subject = mimeMessage.getSubject();
                if(!StringUtils.isEmpty(subject))
                    fileId = subject.substring(subject.indexOf("[") + 1, subject.indexOf("]"));                
            } catch (Exception e) {}
            if (StringUtils.isBlank(fileId)) {
                //UploadFileInfoのIDを取得できなかった場合、IDもわからない、リトライも意味がないので対象がいとして戻る
                LOG.warn("#! サンドブラストリターンメールからUploadFileinfoIdが取得できませんでした。(mailId={}, subject={}, mailFile={}, )",
                        mailId, subject, mailFile.getPath());
                return result;
            }            
            //対象となるUploadFileInfo,UploadGroupInfoを取得（排他ロック）
            //（同一レコードに対してタイムアウト処理で同時アクセスされている可能性があるため排他処理とする）
            //※SandBlastリターンメールが、送信元のWeb/APサーバに戻る仕様となるため、
            //タイムアウト処理との同時アクセスは発生しないため排他の必要性は無くなったが、念の為排他でのfindのままとしておく
            ufi = uploadFileInfoService.findWithLock(fileId);
            if(ufi==null){
                //UploadFileInfoが取得できなかった場合
                //タイムアウト後で既にCOMPへ移動済み等が考えられるので、処理をスキップする。
                LOG.warn("#! execSandblastDownload. Canceled. UploadFileInfoが取得できませんでした。 (mailId={}, fileId={}, mailFile={})", mailId, fileId, mailFile.getPath());
                return result;
            }
            if(ufi.getStep() != StepKbn.SandBlastUploading.value){
                //他のステップに更新されている場合、タイムアウト処理で既に別ステップに移行済み等が考えられるので、処理をスキップする。
                //SandBlastメール送信からステップのSandBlastUploadingへの変更が反映されるまでのわずかなタイミングでリターンメールを受信する可能性がゼロではなく
                //その場合は必要なメールキューを破棄してしまうことになるが、タイムアウト処理によりリトライされるので、そのための特別な処置はしない。
                LOG.warn("#! execSandblastDownload. Canceled. 他のステップに移行済みのため処理をスキップします。(現在のステップ:{}, mailId:{}, fileId:{})"
                        , StepKbn.getStepKbn(ufi.getStep()), mailId, fileId);
                return result;
            }
            //リターンメールのチェック（ふるまい検知チェック）/保存 
            String sandBlastResult = UploadFileInfo.SANDBLAST_RESULT_NG;    //ふるまい検知NG
            try {
                String fileName = ufi.getFileName();    //添付フィル名

                LOG.debug("#SandblastDownload. [MailId={}, FileId={}, FileName={}, ReceiveInfoId:{}",
                    mailId, fileId, fileName, ufi.getReceiveInfoId());
                
                //TODO :::UT:::Start v2.2.1 SandBlastふるまい検知チェックテスト
                if(VerifyUtil.UT_MODE){
                    //元ファイル名にUT用文字列の指定がある場合にUT用偽装を行う
                    //ファイル名偽装（ふるまい検知NG偽装）
                    if(VerifyUtil.chkUTKey(ufi.getFileNameOrg(), "#UT_FURUMAI#", "NG")){
                        fileName += "UT";   //ファイル名偽装
                    }
                    //例外発生偽装（ふるまい検知例外偽装）
                    int utRetry = VerifyUtil.getUTArgValueInt(ufi.getFileNameOrg(), "#UT_FURUMAI#", "EX", -1); //対象リトライカウント
                    boolean utNoId = VerifyUtil.chkUTKey(ufi.getFileNameOrg(), "#UT_FURUMAI#", "NOID"); //ID不明とする
                    if(utRetry >= 0 && ufi.getRetryCount() <= utRetry){
                        //指定したリトライ回数以下の場合に例外を発生させる。
                        if(utNoId){
                            //ID不明偽装の場合（該当UploadFile不明としてリトライさせない）
                            ufi = null;
                        }
                        throw new RuntimeException("#UT_Exception# ふるまい検知例外偽装."
                                + " (fileId=" + fileId + "retryCount=" + ufi.getRetryCount() + ", noId=" + utNoId + ", FileNameOrg=" + ufi.getFileNameOrg() + ")");
                    }
                }
                //TODO :::UT:::End v2.2.1 SandBlastふるまい検知チェックテスト*/                
                
                String saveDir = null;
                if(ufi.getSandBlastKbn() == SandBlastKbn.USE_VOTIRO.value){
                    //----------------------------------
                    //SandBlast対応区分=2の場合（SandBlast使用。VotiroアップロードにSandBlast無害化ファイルを使用する。【京都】）
                    //添付ファイルを保存するので保存先を取得する
                    //----------------------------------
                    UploadGroupInfo ugi = ugiManager.findUploadGroupInfo(ufi.getId());  //UploadGroupInfoを取得
                    saveDir = CommonUtil.getFolderSandBlast(ugi);                       //添付ファイルの保存先を取得
                }
                //添付ファイルのチェックと保存。　（保存は京都系のみ）
                mailManager.saveAttachmentFileForSandBlast(mimeMessage, fileName, saveDir);

                result.setCheckOK(true);   //OK
                sandBlastResult = UploadFileInfo.SANDBLAST_RESULT_OK;   //ふるまい検知OK
            } catch (FssException e) {
                //検査済み例外発生時、リターンメールから対象UploadFileInfoの添付ファイルが元ファイルと異なるためSandBlastでの検知NGとみなす  
                LOG.warn("## ふるまい検知NG. (mailId:{}, fileId:{}, Message:{})", mailId, fileId, e.getMessage());
                //例外はスローしない。 
            }
            //UploadFileInfoのSandBlast無害化結果にふるまい検知結果(OK/NG)をセットしてDB更新
            ufi.setSandBlastResult(sandBlastResult);    //ふるまい検知結果（OK/NG）
            ufi.setSandblastCompDate(DateUtil.getSysDateExcludeMillis());  //sandblastの完了日時
            ufi.setSandblastMailId(mailId); //メールID
            uploadFileInfoService.edit(ufi);    
            uploadFileInfoService.flush();
            
            result.setUploadFileInfo(ufi);
            return result;
        }catch (Throwable e){
            if(ufi != null){
                //UploadFileInfoが特定できていれば、リトライ可としてResultで返す
                LOG.error("#! SandBlastダウンロード　例外発生。 （リトライあり）errMsg:{}  --MailId:{} --ReceiveInfoId:{} --ReceiveFileId:{} --UploadFileInfoId:{} --FileNameOrg:{} --", 
                            e.getMessage(), mailId, ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getFileId(), ufi.getFileNameOrg(), e);                
                result.setUploadFileInfo(ufi);
                result.setError(true);
                result.setErrMessage("Exception:" + e.getMessage());
                return result;
            }else{
                //UploadFileInfoが特定できない場合、SandBlast送信からのリトライは不可能として
                //RuntimeExceptionをスローして呼出元で例外処理をする
                LOG.error("#! SandBlastダウンロード　例外発生。 （UploadFileInfo未特定）errMsg:{}  --MailId:{} --", 
                            e.getMessage(), mailId, e);                
                throw new RuntimeException("Exception:" + e.getMessage(), e);            
            }
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }    
}
