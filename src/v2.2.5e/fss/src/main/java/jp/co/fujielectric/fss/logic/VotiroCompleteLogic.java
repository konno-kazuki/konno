package jp.co.fujielectric.fss.logic;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.data.CommonEnum.FileSanitizeResultKbn;
import jp.co.fujielectric.fss.data.CommonEnum.ProcResultKbn;
import jp.co.fujielectric.fss.data.CommonEnum.ResultKbn;
import jp.co.fujielectric.fss.data.CommonEnum.StepKbn;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.data.VotReportInfo;
import jp.co.fujielectric.fss.data.VotStatus;
import jp.co.fujielectric.fss.entity.DecryptFile;
import jp.co.fujielectric.fss.entity.ReceiveFile;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendFile;
import jp.co.fujielectric.fss.entity.UploadFileInfo;
import jp.co.fujielectric.fss.entity.UploadGroupInfo;
import jp.co.fujielectric.fss.exception.FssException;
import jp.co.fujielectric.fss.service.DecryptFileService;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.service.UploadFileInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.FileUtil;
import jp.co.fujielectric.fss.util.PasswordUnlockUtil;
import jp.co.fujielectric.fss.util.TextUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import jp.co.fujielectric.fss.util.ZipUtil;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.util.InternalZipConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

/**
 * Votiro完了処理クラス
 */
@RequestScoped
public class VotiroCompleteLogic {

    @Inject
    private Logger LOG;

    @Inject
    protected ItemHelper itemHelper;

    @Inject
    private ReceiveInfoService receiveInfoService;

    @Inject
    private CheckedFileLogic checkedFileLogic;

    @Inject
    private DecryptFileService decryptFileService;

    @Inject
    private SanitizeHelper sanitizeHelper;

    @Inject
    private UploadFileInfoService uploadFileInfoService;

    @Inject 
    private DeleteReasonFileLogic deleteReasonFileLogic;
    
    /**
     * 無害化完了処理
     * @param uploadGroupInfo
     * @param fileInfoList
     * @return 
     **/ 
    @Transactional
    public int execVotiroComplete(UploadGroupInfo uploadGroupInfo, List<UploadFileInfo> fileInfoList) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "UploadGroupInfo.ID:" + uploadGroupInfo.getId()));
        
        String receiveId = uploadGroupInfo.getMainId(); //ReceiveInfoのID
        
        //UploadGroupInfoのMainId(ReceiveFileId）からReceiveInfoを取得
        ReceiveInfo receiveInfo = receiveInfoService.findWithRelationTables(receiveId);
        if(receiveInfo == null){
            throw new RuntimeException("指定したIdのReceiveInfoが見つかりません。[id:" + receiveId + "]");
        }
        LOG.debug("#execVotiroComplete Start (uploadGroupInfo.id:" + uploadGroupInfo.getId() + ", fileInfoList.size:" + fileInfoList.size() + ") "
                + "[ReceiveInfo .Id:" + receiveInfo.getId() + ", MailSanitaizeFlg:" + receiveInfo.isMailSanitizeFlg() + "]");

        try {
            List<VotReportInfo> vriLstAll = new ArrayList<>();  //レポート情報リスト（トータル）
            List<UploadFileInfo> updatedFileInfoList = new ArrayList<>();   //更新対象のUploadFileInfoのリスト
            // UploadFileInfoから該当するReceiveInfo,ReceiveFileへの結果登録           
            for (UploadFileInfo uploadFileInfo : fileInfoList) {
                LOG.debug("-execVotiroComplete()  [UploadFileInfo .Id:{}, .FileId:{}, .FileName:{}, .Status:{}, .SanitizeFlg:{}, .ReceiveFileId:{}, .FileNameOrg:{}]"
                        ,uploadFileInfo.getId(), uploadFileInfo.getFileId(), uploadFileInfo.getFileName()
                        ,uploadFileInfo.getStatus(), uploadFileInfo.isSanitizeFlg()
                        ,uploadFileInfo.getReceiveFileId(), uploadFileInfo.getFileNameOrg());

                //TODO :::UT:::Start v2.2.1 Complete スリープテスト（トランザクションタイムアウトのテスト）
                if(VerifyUtil.UT_MODE){
                    int utSleep = VerifyUtil.getUTArgValueInt(uploadFileInfo.getFileNameOrg(), "#UT_COMPLETE#", "S", 0);
                    int utRetry = VerifyUtil.getUTArgValueInt(uploadFileInfo.getFileNameOrg(), "#UT_COMPLETE#", "R", 0);
                    if(utSleep > 0 && uploadGroupInfo.getRetryCount() <= utRetry){    //RetryCountが"R="で指定した回数以下の場合だけスリープする
                        VerifyUtil.outputUtLog(LOG, "", false, "Sleep(%d)", utSleep);
                        Thread.sleep(utSleep*1000);
                    }                    
                }
                //TODO :::UT:::End v2.2.1 Complete スリープテスト*/             
                
                List<VotReportInfo> vriLst = new ArrayList<>();
                boolean isMailBody = false;         //メール本体かどうか
                ReceiveFile receiveFile = null;     //該当ReceiveFile（添付ファイルの場合)
                String dspMessage = "";             //無害化結果メッセージ
                String dspMessageKey = "";          //無害化結果メッセージ取得用キー
                ResultKbn result;                   //無害化結果区分
                boolean isCancel = false;           //キャンセルフラグ
                //Votiroステータス
                String votiroStatus = (uploadFileInfo.getStatus() == null ? "" : uploadFileInfo.getStatus());
                
                //メール本体かどうかの判定、および該当ReceiveFileの取得
                if (receiveInfo.isMailSanitizeFlg() 
                        && FileUtil.getFNameWithoutSuffix(uploadFileInfo.getFileName()).equals(receiveInfo.getId())) { 
                    //メール本体
                    isMailBody = true;
                    
                    if(receiveInfo.isMailSanitizedFlg()){
                        //処理済みの場合はスキップ（リトライ対応）
                        continue;
                    }
                }else{
                    //添付ファイルの場合
                    //UploadFileInfo.receiveFileId から該当ReceiveFileを検索
                    for (ReceiveFile rFile : receiveInfo.getReceiveFiles()) {
                        if(rFile.getId().equals(uploadFileInfo.getReceiveFileId())){
                            receiveFile = rFile;
                            break;
                        }
                    }
                    if(receiveFile == null){
                        //該当ReceiveFileが見つからない場合（有り得ない）
                        LOG.warn("execVotiroComplete - uploadFileInfoに該当するReceiveFileが見つかりません。 [UploadFileInfo .Id:{}, .FileId={}, .FileNameOrg:{}]",
                                uploadFileInfo.getId(), uploadFileInfo.getFileId(), uploadFileInfo.getFileNameOrg());
                        continue;
                    }
                    if(receiveFile.isSanitizeFlg()){
                        //処理済みの場合はスキップ（リトライ対応）
                        continue;
                    }
                    
                    //無害化完了時のパスワード解除区分をReceiveFileにセットする
                    receiveFile.setDecryptKbn(uploadFileInfo.getDecryptKbn());
                    //SandBlast結果をReceiveFileにセットする
                    receiveFile.setSandBlastResult(uploadFileInfo.getSandBlastResult());
                    //TODO その他UploadFileInfoの内容をReceiveInfoに反映する処理はここに実装する
                }
                
                if(uploadFileInfo.getStep() == StepKbn.Cancel.value || !uploadFileInfo.isSanitizeFlg()){
                    //キャンセル（または無害化未完了）
                    isCancel = true;
//                    continue;
                }

                int fileErrorInfo = ProcResultKbn.SUCCESS.value;
                int fileErrorCode = FileSanitizeResultKbn.SUCCESS.value;
                if( isCancel ){
                    LOG.debug("#無害化キャンセル" + "ReceiveInfoID=" + uploadGroupInfo.getMainId() + ", FileId=" + uploadFileInfo.getId());
                    result = ResultKbn.CANCEL;
                    dspMessageKey = "errVotiroUpIncomplete";
                    dspMessage = "無害化処理が完了しませんでした";
                }
                else if (votiroStatus.equals(VotStatus.EnmStatus.Done.name())) {
                    LOG.debug("#無害化完了" + "ReceiveInfoID=" + uploadGroupInfo.getMainId() + ", FileId=" + uploadFileInfo.getFileId());
                    //後処理（ファイルコピー、ZIP文字コード変換、パスワード再付与）
                    try {
                        doneAfter(receiveInfo, receiveFile, uploadFileInfo);                        
                        LOG.debug("・・・無害化完了[ReceiveFile .Id:{}, DownloadedFilePath:{}, FileName:{}]",
                                uploadFileInfo.getReceiveFileId(), uploadFileInfo.getVotiroFilePath(), uploadFileInfo.getFileNameOrg());                        

                        //[v2.2.3]
                        //正常終了、かつアーカイブファイルの場合（1階層目のファイルにブロックされたファイルがないか調べる）
                        if (FileUtil.isArchiveFile(uploadFileInfo.getFileNameOrg())) {
                            try {
                                //レポートファイル解析（アーカイブファイル用）
                                vriLst = DeleteReasonFileLogic.getArchiveFileReportInfo(uploadFileInfo.getReportFilePath(), uploadFileInfo.getFileNameOrg());                                
                                if (!vriLst.isEmpty()) {
                                    fileErrorInfo = ProcResultKbn.SANITIZED_ERROR.value;
                                    fileErrorCode = FileSanitizeResultKbn.ARCHIVECHILD_ERROR.value;
                                    dspMessage = "一部のファイルが無害化処理により削除されました";
                                    dspMessageKey = "errVotiroUpArchiveChild";
                                }
                            } catch (Exception e) {
                                //レポートファイル解析に失敗した場合、無害化が正常に完了した状態の扱いとする
                                LOG.warn("無害化レポートの解析に失敗しました（アーカイブファイル）。[UploadFileInfoId:{}, ReceiveFileId:{}, ReportFilePath:{}, Exception:{}]",
                                        uploadFileInfo.getFileId(), uploadFileInfo.getReceiveFileId(), uploadFileInfo.getReportFilePath(), e.toString(), e);
                            }
                        }
                        result = ResultKbn.SANITIZED;
                    } catch (FssException e){
                        //無害化完了として継続できるエラー発生（ZIP文字コード変換、パスワード再付与）
                        LOG.warn("無害化後処理で問題が発生しました。(成功として継続）"
                                + "[UploadFileInfo .Id:" + uploadFileInfo.getId() + ", .FileId:" + uploadFileInfo.getFileId() 
                                + ", VotiroFilePath:"+ uploadFileInfo.getVotiroFilePath()
                                + ", ReceiveFile .Id:" + uploadFileInfo.getReceiveFileId()+ ", .FileName:" + uploadFileInfo.getFileNameOrg() + "]", e);                        
                        result = ResultKbn.SANITIZED;
                    } catch (Exception e) {
                        //想定外のエラーが発生した場合
                        LOG.warn("無害化後処理で問題が発生しました。（無害化失敗として継続）"
                                + "[UploadFileInfo .Id:" + uploadFileInfo.getId() + ", .FileId:" + uploadFileInfo.getFileId() 
                                + ", VotiroFilePath:"+ uploadFileInfo.getVotiroFilePath()
                                + ", ReceiveFile .Id:" + uploadFileInfo.getReceiveFileId()+ ", .FileName:" + uploadFileInfo.getFileNameOrg() + "]", e);
                        dspMessage = "無害化処理が失敗しました";
                        dspMessageKey = "errVotiroUpException"; //TODO エラーメッセージを追加する必要があるかも
                        result = ResultKbn.ERROR;
                    }
                }else{
                    //UploadFileInfoのステータスに応じた処理分岐
                    if (votiroStatus.equals(VotStatus.EnmStatus.Blocked.name())) {
                        LOG.debug("無害化ブロック" + "ReceiveInfoID=" + uploadGroupInfo.getMainId() + ", FileId=" + uploadFileInfo.getId());
                        // [2016/12/26] "ファイルがブロックされました"
                        dspMessage = "ファイルがブロックされました";
                        dspMessageKey = "errVotiroUpBlocked";
                        result = ResultKbn.BLOCKED;
                    } else if (votiroStatus.equals(VotStatus.EnmStatus.Error.name())) {
                        LOG.debug("無害化エラー" + "ReceiveInfoID=" + uploadGroupInfo.getMainId() + ", FileId=" + uploadFileInfo.getId());
                        // [2016/12/26] "対応していないファイルです"
                        dspMessage = "対応していないファイルです";
                        dspMessageKey = "errVotiroUpError";
                        result = ResultKbn.REJECTED;
                    } else {
                        //キャンセルじゃないのにVotiroステータスがセットされていない場合はエラー発生　（VotiroアップロードでStatusCode=400,409の場合）
                        LOG.debug("votiro登録エラー" + "ReceiveInfoID=" + uploadGroupInfo.getMainId() + ", FileId=" + uploadFileInfo.getId());
                        // [2016/12/26] "無害化処理が失敗しました"
                        dspMessage = "無害化処理が失敗しました";
                        dspMessageKey = "errVotiroUpException";
                        result = ResultKbn.ERROR;
                    }

                    //[v2.2.3]
                    if (result != ResultKbn.ERROR && !isMailBody) {
                        try {
                            //レポートファイル解析
                            VotReportInfo vri = DeleteReasonFileLogic.getFileReportInfo(uploadFileInfo.getReportFilePath(), uploadFileInfo.getFileNameOrg());
                            if(vri == null){
                                throw new FssException("対象となる情報が見つかりませんでした。");                                
                            }
                            vriLst.add(vri);
                        } catch (Exception e) {
                            //レポートファイル解析に失敗した場合、「無害化エンジンでのエラー」扱いとする
                            LOG.warn("無害化レポートの解析に失敗しました（ファイル本体）。[UploadFileInfoId:{}, ReceiveFileId:{}, ReportFilePath:{}, Exception:{}]",
                                    uploadFileInfo.getFileId(), uploadFileInfo.getReceiveFileId(), uploadFileInfo.getReportFilePath(), e.toString(), e);
                            VotReportInfo vri = new VotReportInfo();
                            vri.setFileName(uploadFileInfo.getFileNameOrg());     //無害化ファイル名
                            vri.setId(10050500);                            //無害化エンジンでのエラーID
                            vri.setDetails(String.valueOf(vri.getId()));                     //エラー詳細
                            vriLst.add(vri);
                        }
                        fileErrorInfo = ProcResultKbn.SANITIZED_ERROR.value;
                        fileErrorCode = FileSanitizeResultKbn.FILE_ERROR.value;
                    }
                }
                //無害化結果メッセージ取得
                if(!StringUtils.isEmpty(dspMessageKey)){
                    try {
                        dspMessage = itemHelper.findDispMessageStr(dspMessageKey, Item.FUNC_COMMON);
                    } catch (Exception e) { 
                        LOG.warn("メッセージ取得に失敗しました。[key:" + dspMessageKey + ", funcId:" + Item.FUNC_COMMON + " ] ex.msg:" + e.getMessage());
                        //取得に失敗した場合は、あらかじめセットしておいたメッセージを使用する 
                    }
                }

                //UploadFileInfを更新[v2.2.3]
                if(!vriLst.isEmpty()){
                    updatedFileInfoList.add(uploadFileInfo);    //更新対象としてリストに追加
                    //VotReportInfoの値をUploadFileInfoに反映
                    uploadFileInfo.setErrInfo(fileErrorInfo);                   //エラー(添付ファイル／ファイルの異常検出時のエラーを格納)
                    uploadFileInfo.setErrFile(fileErrorCode);                   //エラーファイル(添付ファイル／ファイルの異常検出対象ファイル)
                    //エラー詳細
                    if(vriLst.get(0).getId() == 10050100){
                        //ポリシーによるブロックの場合は "10050100" + Detailsの[]内の文字列
                        uploadFileInfo.setErrDetails(String.valueOf(vriLst.get(0).getId()) + vriLst.get(0).getDetails());   //エラー詳細(添付ファイル／ファイルの異常検出時のエラー詳細を格納)                        
                    }else{
                        uploadFileInfo.setErrDetails(vriLst.get(0).getDetails());   //エラー詳細(添付ファイル／ファイルの異常検出時のエラー詳細を格納)
                    }
                }
                
                if (isMailBody) {  // メール本体
                    // ReceiveInfoを更新
                    receiveInfo.setMailSanitizedFlg(uploadFileInfo.isSanitizeFlg());
                    receiveInfo.setMailSanitizeMessage(dspMessage);
//                    receiveInfoService.edit(receiveInfo);
                    LOG.debug("・・・無害化（メール本体）ReceiveInfo更新 [ReceiveInfo .Id" + receiveInfo.getId() 
                            + ", .MailSanitizeMessage:" + receiveInfo.getMailSanitizeMessage() + ", .SanitizeFlg:" + receiveInfo.isMailSanitizedFlg() + "]"); 
                } else if(receiveFile != null) {
                    // ReceiveFileを更新
                    receiveFile.setFileMessage(dspMessage);
                    receiveFile.setSanitizeFlg(true);
                    receiveFile.setResult(result.value);    //無害化結果
                    receiveFile.resetDate();                //更新日付の更新
                    
                    receiveFile.setFileErrCode(uploadFileInfo.getErrFile());    //ファイルエラーコード  [v2.2.3]
                    receiveFile.setReportFilePath(uploadFileInfo.getReportFilePath());  //レポートファイルパス  [v2.2.3]
                    receiveFile.setErrInfo(uploadFileInfo.getErrInfo());        //エラー(添付ファイル／ファイルの異常検出時のエラーを格納)  [v2.2.3]
                    receiveFile.setErrDetails(uploadFileInfo.getErrDetails());  //エラー詳細(添付ファイル／ファイルの異常検出時のエラー詳細を格納)  [v2.2.3]
                    LOG.debug("・・・無害化（添付ファイル）ReceiveFile更新 [ReceiviFile .Id:" + receiveFile.getId() 
                            + ", .FileMessage:" + receiveFile.getFileMessage() + ", .SanitizeFlg:" + receiveFile.isSanitizeFlg()
                            + ", .FilePath:" + receiveFile.getFilePath() + ", .FileSize:" + receiveFile.getFileSize() + "]");
                }
                vriLstAll.addAll(vriLst);
            }

            // UploadFileInfoのリストに紐付くレコードがなかったReceiveFileに対する処理
            // 未完了のエラーとしてフラグは無害化済とする。
            // ※対象となるレコードは存在しないはずだが一応残しておく
            for (ReceiveFile receiveFile : receiveInfo.getReceiveFiles()) {
                if (receiveFile.isSanitizeFlg() == false) {
                    // [2017/03/23] "無害化処理が完了しませんでした"
                    String dspMessageKey = "errVotiroUpIncomplete";
                    String dspMessage = "無害化処理が完了しませんでした";
                    try {
                        dspMessage = itemHelper.findDispMessageStr(dspMessageKey, Item.FUNC_COMMON);
                    } catch (Exception e) {
                        LOG.warn("メッセージ取得に失敗しました。[key:" + dspMessageKey + ", funcId:" + Item.FUNC_COMMON + " ] ex.msg:" + e.getMessage());
                    }
                    receiveFile.setFileMessage(dspMessage);
                    receiveFile.setSanitizeFlg(true);
                    receiveFile.setResult(ResultKbn.CANCEL.value);   //無害化結果=キャンセル
                    receiveFile.resetDate();     //更新日付の更新
                    LOG.debug("・・・無害化未完了 ReceiveFile更新 [ReceiviFile .Id:" + receiveFile.getId() 
                            + ", .FileMessage:" + receiveFile.getFileMessage() + ", .SanitizeFlg:" + receiveFile.isSanitizeFlg() + "]");
                }
            }

            //削除理由ファイルを作成する
            try {
                deleteReasonFileLogic.createDeleteReasonFileRecv(receiveInfo, vriLstAll, fileInfoList);
            } catch (Exception e) {
                //削除理由ファイルの生成の失敗が原因で無害化完了しないことがないよう
                //例外発生時にも処理を続行する
                LOG.error("#! 削除理由ファイルの生成に失敗しました。[ReceiveInfoId:{}, UploadGroupInfoId:{}, Exception:{}]",
                        receiveInfo.getId(), uploadGroupInfo.getId(), e.toString(), e);
            }

            //更新対象のUploadFileInfoをDB更新する [v2.2.3]
            if(!updatedFileInfoList.isEmpty()){
                uploadFileInfoService.editNew(updatedFileInfoList);
            }

            //ReceiveInfo,ReceiveFileのDB更新
            receiveInfo.setCompDate(DateUtil.getSysDateExcludeMillis());    //全ファイルの処理完了日付
            receiveInfo.resetDate();    //更新日付セット
            receiveInfoService.edit(receiveInfo);
            receiveInfoService.flush(); //DB更新(コミットではない）

            //ふるまい検知結果反映
            checkedFileLogic.execUpdateCheckedFile(receiveInfo, fileInfoList);
            
            // 無害化完了時の処理の呼び出し
            sanitizeHelper.onComplete(receiveInfo);

            LOG.debug("VotiroCompleteServlet Success(ReceiveInfo.ID=[" + receiveInfo.getId() + "])");
            return HttpServletResponse.SC_OK;
        }catch(Throwable e){
            LOG.error("ExecVotiroComplete Error. (ReceiveInfo.ID=[" + receiveId+"])", e);
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }
    
    /**
     * 無害化後処理
     *
     * @param receiveFile ReceiveFile
     */
    private void doneAfter(ReceiveInfo receiveInfo, ReceiveFile receiveFile, UploadFileInfo uploadFileInfo)
            throws ZipException, IOException, CloneNotSupportedException, FssException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        
        if(receiveFile != null){
            //ダウンロードファイルファイルパスをReceiveFileInfoに反映
            receiveFile.setVotiroFilePath(uploadFileInfo.getVotiroFilePath());
        }
        if(StringUtils.isBlank(uploadFileInfo.getVotiroFilePath())){
            //Votiroダウンロードファイルパスがセットされていない            
            throw new FileNotFoundException("votiroFilePathがセットされていません。");
        }        
        //ダウンロード済みファイルパス
        Path dldFile = Paths.get(uploadFileInfo.getVotiroFilePath());
        if(!Files.exists(dldFile)){
            //ダウンロードファイルが存在しない
            //（通常は有り得ないが何らかの原因でファイルが削除されている場合などに対応）
            throw new FileNotFoundException("VotiroFilePathのファイルが存在しません。(" + uploadFileInfo.getVotiroFilePath() + ")");
        }        
        
        //VotiroダウンロードファイルをRecvフォルダにコピーする
        Path destPath = null;
        try {
            if(receiveFile == null){
                //メール本体の場合、Recvフォルダ直下にReceiveInfoId+".eml"にリネームしてコピー
                destPath = Paths.get( CommonUtil.getFolderReceive(receiveInfo, false, true), receiveInfo.getId() + ".eml");
            }else{
                //添付・送信ファイルの場合、receiveInfoIDサブフォルダに元ファイル名にリネームしてコピー
                destPath = Paths.get(CommonUtil.getFolderReceive(receiveInfo, false, false), uploadFileInfo.getFileNameOrg());
            }
            LOG.debug("#Receiveフォルダ:{}",  destPath.getParent());
            Files.createDirectories(destPath.getParent());  //コピー前にフォルダを生成しておく
            Files.copy(dldFile, destPath, StandardCopyOption.REPLACE_EXISTING);           
            LOG.debug("#VotiroフォルダからReceiveフォルダにファイルをコピーしました。[コピー元：{}, コピー先:{}]{},{}"
                ,dldFile, destPath, Files.isDirectory(dldFile), Files.isDirectory(destPath)); 
        } catch (IOException e) {
            //ファイルコピー失敗
            String errMsg = String.format("#!Votiroからダウンロードしたファイルの受信フォルダへのコピーに失敗しました。[コピー元：%s, コピー先:%s]"
                ,dldFile, destPath);
            LOG.warn(errMsg, e);
            throw new IOException(errMsg);
        }
        if(receiveFile == null){
            //メール本体の場合
            //それ以外の後処理はないので抜ける
            return;
        }else{
            //添付ファイル・送信ファイルの場合
            //ReceiveFileの更新
            receiveFile.setFilePath(destPath.toString());
            receiveFile.setFileSize(Files.size(destPath));
        }

        //TODO :::UT:::Start v2.2.1 Complete処理例外テスト
        if(VerifyUtil.UT_MODE){
            if(VerifyUtil.chkUTKey(uploadFileInfo.getFileNameOrg(), "#UT_DONEAFTER#", "EX1")){
                throw new RuntimeException("UT_Complete.doneAfter 不明例外テスト");
            }
        }
        //TODO :::UT:::End v2.2.1 Complete処理例外テスト*/          
        
        //ZIP文字コード変換、パスワード再付与
        //※この処理での例外はFssExceptionで戻して、呼出側は処理を続行する。
        try {
            //高圧縮ZIPファイル（ZipBomb）は対象外とする（Votiroがブロックしない可能性を考慮） [v.2.2.5d]
            SendFile sendFile = receiveFile.getReceiveInfo().getSendInfo().getSendFiles().stream()
                    .filter(s -> s.getId().equals(receiveFile.getSendFileId())).findFirst().orElse(new SendFile());
            VerifyUtil.outputUtLog(LOG, "#v2.2.5d#ZipBomb#", false, "IsZipBomb:%b, File:%s", sendFile.isZipBomb(), sendFile.getFileName());
            if(sendFile.isZipBomb())
                return;
            
            boolean zipCharsetConvert = false;
            boolean encryptedFlg;
            try {
                if(receiveFile.getFileFormat().equalsIgnoreCase(FileUtil.ZIPSUFFIX)){
                    Item item = itemHelper.find(Item.ZIP_CHARSET_CONVERT_INNER, Item.FUNC_COMMON, receiveInfo);    //[248対応（簡易版）]
                    zipCharsetConvert = item.getValue().equalsIgnoreCase("true");
                    VerifyUtil.outputUtLog(LOG, "#UT_v2.2.4#", false, "zipCharsetConvert:%b, mailAddress:%s", zipCharsetConvert, receiveInfo.getReceiveMailAddress());
                }
            } catch (Exception e) {
                LOG.warn("(doneAfter)configパラメータの取得に失敗したためZIP内文字コード変換をスキップします。（Key:{}, receiveInfoId:{}, Exception:{}）",
                        Item.ZIP_CHARSET_CONVERT_INNER, receiveInfo.getId(), e.toString());
            }

            // エンコード変換が有効＆zip判定
            if (zipCharsetConvert) {
                // エンコード変換が必要なzip
                receiveFile.setZipCharsetConvert(true);

                // 変換出来ない文字が含まれていないか確認
                boolean convertibleWin31J = true;
                List<String> fileNameList = ZipUtil.getFileNameList(receiveFile.getFilePath());
                for (String fileName : fileNameList) {
                    if (!TextUtil.isValidWin31J(fileName)) {
                        convertibleWin31J = false;
                    }
                }

                // 変換可否
                if (convertibleWin31J) {
                    // Win31J変換可
                    // 指定文字コードでパスワード再付与(csWindows31J指定)
                    encryptedFlg = reEncryptPassword(receiveFile, ZipUtil.CHARSET_csWindows31J);
                    // パスワード再付与を行っていないファイルか
                    if (!encryptedFlg) {
                        // エンコード変換
                        zipCharsetConvert(receiveFile, ZipUtil.CHARSET_csWindows31J);
                    }
                    // 変換完了
                    receiveFile.setZipCharsetConverted(true);
                } else {
                    // Win31J変換不可
                    // 指定文字コードでパスワード再付与(UTF-8指定)
                    encryptedFlg = reEncryptPassword(receiveFile, InternalZipConstants.CHARSET_UTF8);
                }
            } else {
                // エンコード判定不要 or zip以外
                // 指定文字コードでパスワード再付与(UTF-8指定)
                encryptedFlg = reEncryptPassword(receiveFile, InternalZipConstants.CHARSET_UTF8);
            }
            //TODO :::UT:::Start v2.2.1 Complete処理例外テスト
            if(VerifyUtil.UT_MODE){
                if(VerifyUtil.chkUTKey(uploadFileInfo.getFileNameOrg(), "#UT_DONEAFTER#", "EX2")){
                    throw new RuntimeException("UT_Complete.doneAfter 変換例外テスト");
                }
            }
            //TODO :::UT:::End v2.2.1 Complete処理例外テスト*/                      
        } catch (Exception e) {
            throw new FssException("無害化後処理に失敗しました。", e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));          
        }

    }

    /**
     * パスワード再付与処理
     *
     * @param receiveFile ReceiveFile
     */
    @Transactional
    private boolean reEncryptPassword(ReceiveFile receiveFile, String charset) throws ZipException, IOException, CloneNotSupportedException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        boolean encryptedFlg = false;

        DecryptFile decryptFileParent = null;                                   // 親ファイル
        List<DecryptFile> decryptFiles = new ArrayList<>();                     // 対象の子ファイル
        for (DecryptFile decryptFile : receiveFile.getDecryptFiles()) {
            if (StringUtils.isEmpty(decryptFile.getParentId())) {               // 親ファイル
                // 親ファイルはパスワード再付与判定せずに取得
                decryptFileParent = decryptFile;
            } else // 子ファイル
             if (decryptFile.isEncryptFlg()) {
                    decryptFiles.add(decryptFile);  // パスワード再付与の対象判定（パスワードなしはEncryptFlg=falseとなる）
                }
        }

        // 再付与処理は親ファイル必須
        if (decryptFileParent != null) {
            if (decryptFileParent.isEncryptFlg() || !decryptFiles.isEmpty()) {  // 親子のいずれかにパスワード再付与対象がある場合のみ
                Item item = itemHelper.find(Item.ENCRYPT_MODE_PDF, Item.FUNC_COMMON);   // 機能ＩＤは共通
                int encryptModePDF;
                try {
                    encryptModePDF = Integer.parseInt(item.getValue());
                } catch (Exception e) {
                    encryptModePDF = 3;     // システム規定値は「3:128Bit AES（PDF1.6以降）」
                }

                if (decryptFileParent.getFileFormat().equalsIgnoreCase(FileUtil.ZIPSUFFIX)) {   // Zipファイル判定
//                    String tempDir = CommonUtil.getSetting("tempdir") + receiveFile.getId() + File.separatorChar;
                    File tempFolder = new File(CommonUtil.getFolderTemp(), receiveFile.getId());
                    File zipFolder = new File(tempFolder,  FileUtil.getFNameWithoutSuffix(receiveFile.getFileName()));
                    // 親ファイルZipをTempフォルダで解凍
                    ZipUtil.unzipAll(receiveFile.getFilePath(), zipFolder.getPath(), null, InternalZipConstants.CHARSET_UTF8);

                    // 子ファイルのパスワード再付与
                    for (DecryptFile decryptFile : decryptFiles) {
                        // tempdirにある解凍済みファイルのパスを求める
                        String relativeFilePath = decryptFile.getFilePath();
                        relativeFilePath = relativeFilePath.substring(relativeFilePath.indexOf(decryptFile.getReceiveInfoId()) + decryptFile.getReceiveInfoId().length() + 1);
                        if (PasswordUnlockUtil.lockPassword( new File(tempFolder, relativeFilePath).getPath(), decryptFile.getFilePassword(), encryptModePDF)) {
                            decryptFile.setEncryptedFlg(true);
                            decryptFile.resetDate();  //更新日付の更新
                            decryptFileService.edit(decryptFile);
                        }
                    }

                    // フォルダをZipファイルに圧縮
                    // ※元のZipファイルは削除・上書きされる
                    byte[] zipFile = ZipUtil.createZipInMemory(zipFolder.getPath(), (decryptFileParent.isEncryptFlg() ? decryptFileParent.getFilePassword() : null), charset);
                    try(InputStream is = new ByteArrayInputStream(zipFile)){
                        FileUtil.saveFile(is, receiveFile.getFilePath());                       
                    }
                    if (decryptFileParent.isEncryptFlg()) {
                        decryptFileParent.setEncryptedFlg(true);
                        decryptFileParent.resetDate();  //更新日付の更新
                        decryptFileService.edit(decryptFileParent);
                    }
                    encryptedFlg = true;
                    // メール本体無害化で作成したテンポラリフォルダを削除する
                    FileUtil.deleteFolder(tempFolder.getPath());
                } else // 親Zip以外
                 if (decryptFiles.isEmpty()) {
//                        String tempDir = CommonUtil.getSetting("tempdir") + receiveFile.getId() + File.separatorChar;
                        File tempFolder = new File(CommonUtil.getFolderTemp(), receiveFile.getId());
                        if (decryptFileParent.isEncryptFlg()) {
                            String fname = new File(tempFolder, receiveFile.getFileName()).getPath();
                            try (InputStream inputStream = new FileInputStream(receiveFile.getFilePath())) {
                                FileUtil.saveFile(inputStream, fname);
                            }
                            if (PasswordUnlockUtil.lockPassword(fname, decryptFileParent.getFilePassword(), encryptModePDF)) {
                                decryptFileParent.setEncryptedFlg(true);
                                decryptFileParent.resetDate();  //更新日付の更新
                                decryptFileService.edit(decryptFileParent);
                            }
                            try (InputStream inputStream = new FileInputStream(fname)) {
                                FileUtil.saveFile(inputStream, receiveFile.getFilePath());
                            }
                            encryptedFlg = true;
                            FileUtil.deleteFolder(tempFolder.getPath());
                        }
                    } else {
                        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
                        // 親Zip以外＋子ファイル再付与ありの場合はエラーとする
                        throw new RuntimeException("VotiroCompleteLogic::reEncryptPassword ParentFile NotFound!");
                    }
            }
        } else // 親ファイル無し＋子ファイル再付与ありの場合はエラーとする
         if (decryptFiles.size() > 0) {
                LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
                throw new RuntimeException("VotiroCompleteLogic::reEncryptPassword ParentFile NotFound!");
            }

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        return encryptedFlg;
    }

    /**
     * ZIPフィル文字コード変換
     * @param receiveFile
     * @param charset
     * @throws ZipException
     * @throws IOException
     * @throws CloneNotSupportedException 
     */
    private void zipCharsetConvert(ReceiveFile receiveFile, String charset) throws ZipException, IOException, CloneNotSupportedException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

//        String tempDir = CommonUtil.getSetting("tempdir") + receiveFile.getId() + File.separatorChar;
//        String zipDir = FileUtil.getFNameWithoutSuffix(tempDir + receiveFile.getFileName()) + File.separatorChar;
        File tempFolder = new File(CommonUtil.getFolderTemp(), receiveFile.getId());
        File zipFolder = new File(tempFolder,  FileUtil.getFNameWithoutSuffix(receiveFile.getFileName()));
        
        // 親ファイルZipをTempフォルダで解凍
        ZipUtil.unzipAll(receiveFile.getFilePath(), zipFolder.getPath(), null, InternalZipConstants.CHARSET_UTF8);            

        // フォルダをZipファイルに圧縮
        // ※元のZipファイルは削除・上書きされる
        byte[] zipFile = ZipUtil.createZipInMemory(zipFolder.getPath(), null, charset);
        try(InputStream is = new ByteArrayInputStream(zipFile)){
            FileUtil.saveFile(is, receiveFile.getFilePath());            
        }

        // メール本体無害化で作成したテンポラリフォルダを削除する
        FileUtil.deleteFolder(tempFolder.getPath());

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }
}
