package jp.co.fujielectric.fss.logic;

//import com.ocpsoft.pretty.faces.util.StringUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.data.CommonBean;
import jp.co.fujielectric.fss.data.CommonEnum.DecryptKbn;
import jp.co.fujielectric.fss.data.CommonEnum.ResultKbn;
import jp.co.fujielectric.fss.data.CommonEnum.SandBlastKbn;
import jp.co.fujielectric.fss.data.CommonEnum.StepKbn;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.data.VotStatus;
import jp.co.fujielectric.fss.entity.CheckedFile;
import jp.co.fujielectric.fss.entity.ReceiveFile;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendFile;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.entity.UploadFileInfo;
import jp.co.fujielectric.fss.entity.UploadGroupInfo;
import jp.co.fujielectric.fss.service.CheckedFileService;
import jp.co.fujielectric.fss.service.ReceiveFileService;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.IdUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.logging.log4j.Logger;
import org.apache.commons.lang3.StringUtils;

/**
 * 無害化処理へのファイル送信を行う
 */
@Named
@RequestScoped
public class SanitizeHelper {

    @Inject
    private Logger LOG;

    @Inject
    private ItemHelper itemHelper;

    @Inject
    private MailManager mailManager;

    @Inject
    private SyncFilesHelper syncFilesHelper;

    @Inject
    private CheckedFileService checkedFileService;

    @Inject
    protected ReceiveInfoService receiveInfoService;
    
    @Inject
    private ReceiveFileService receiveFileService;
    
    @Inject
    private UploadGroupInfoManager ugiManager;
    
    @Inject
    protected CommonBean commonBean;

    /**
     * 無害化開始時処理（パスワード解除用）
     *
     * @param receiveInfo RecieveInfo
     */
    @Transactional
    public void startForPassword(ReceiveInfo receiveInfo) {
        start("passwordUnlock", Arrays.asList(receiveInfo));
    }
    
    /**
     * 無害化開始時処理
     *
     * @param funcId 呼び出し元の機能ＩＤ
     * @param receiveInfoLst RecieveInfo
     */
    @Transactional
    public void start(String funcId, List<ReceiveInfo> receiveInfoLst) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "funcId=" + funcId));
                
        //パスワード処理有無
        boolean flgPassword = "passwordUnlock".equalsIgnoreCase(funcId);
        
        // ReceiveInfoの更新、ふるまい検知 
        for(ReceiveInfo receiveInfo: receiveInfoLst){
            //sanitizeFlgをfalseに戻す【v2.1.13 A002】
            List<String> rfileIdLst = new ArrayList<>();
            for(ReceiveFile rfile:receiveInfo.getReceiveFiles()){
                if(rfile.isSanitizeFlg()){
                    rfileIdLst.add(rfile.getId());
                }
            }
            if(!rfileIdLst.isEmpty()){
                resetSanitizeFlg(rfileIdLst);
            }
            
            //パスワード解除の場合 （Viewから移設）[v2.2.3a] 
            if(flgPassword){
                receiveInfo.setPasswordUnlockWaitFlg(false);
                receiveInfo.resetDate();    //更新日付セット
                receiveInfoService.edit(receiveInfo);
            }
            
            //ふるまい検知情報生成
            _createCheckedFile(receiveInfo);
        }
    
        //------------------------------------
        //UploadGroupInfo/FileInfoの生成 [v2.2.1]
        // ※1. 全ReceiveInfoに紐付くレコードをまとめてコミットする必要あり。（生成タイミングがずれて他処理からの検索に影響がでないように）
        // ※2. 共通DBに対する処理となるため、検索・更新は別トランザクションとする。
        //------------------------------------
        List<UploadGroupInfo> ugiLst = new ArrayList<>();
        String owner = CommonUtil.getSetting("polling_owner");  //ポーリングオーナー取得
        for(ReceiveInfo receiveInfo: receiveInfoLst){
            //処理ステップ
            StepKbn step = StepKbn.VotiroUploadWait;  //Votiroアップロード待ち
            if(receiveInfo.getSandBlastKbn() == SandBlastKbn.USE_VOTIRO.value
              || (receiveInfo.getSandBlastKbn() == SandBlastKbn.CHECK_ONLY.value && receiveInfo.getSendInfo().isSendFileCheckFlg())){
                //SandBlastを利用する場合は、ステップを「無害化開始待ち」にする
                //※CheckOnlyの場合はふるまい検知フラグがtrueの場合のみ
                step = StepKbn.StartWait;    //無害化待ち（SandBlastアップロード待ち）
            }
            LOG.debug("#UT#v2.2.1# Step:{} ReceiveInfo.SandBlastKbn:{}", step, receiveInfo.getSandBlastKbn());
            
            //UploadGroupInfoのIDを採番
            String gid = IdUtil.createUUID();
            
            //UploadFileInfoの生成
            List<UploadFileInfo> ufiLst = new ArrayList<>();
            if(receiveInfo.isMailSanitizeFlg()){
                //メール本体
                ufiLst.add( ugiManager.createUploadFileInfo(gid, receiveInfo, null, owner, step, DecryptKbn.NONE));
            }
            for(ReceiveFile rf: receiveInfo.getReceiveFiles()){
                //添付ファイル・送信ファイル                
                DecryptKbn decryptKbn = DecryptFileLogic.getDecryptKbn(rf); //パスワード解除区分を取得
                ufiLst.add( ugiManager.createUploadFileInfo(gid, receiveInfo, rf, owner, step, decryptKbn));
            }
            
            //UploadGroupInfoの生成
            UploadGroupInfo ugi = ugiManager.createUploadGroupInfo(
                    gid,
                    commonBean.getRegionId(),
                    owner,
                    ufiLst.size(),
                    receiveInfo);
            ugi.setUploadFileInfos(ufiLst);
            ugiLst.add(ugi);
            
            if(flgPassword){
                //パスワード解除の場合、同ファイルからの反映処理
                updateBySameFileForPasswordUnlock(ugi);
            }            
        }
        
        //まとめて即時コミットする
        ugiManager.editCommit(ugiLst);  
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }
    
    private void resetSanitizeFlg(List<String> idLst)
    {
        for(String id: idLst){
            //最新情報取得
            ReceiveFile rFile = receiveFileService.find(id);
            rFile.setSanitizeFlg(false);
            rFile.resetDate();
            receiveFileService.edit(rFile);
        }        
    }
      
    /**
     * ふるまい検知情報生成
     *
     * @param receiveInfo RecieveInfo
     */
    @Transactional
    private void _createCheckedFile(ReceiveInfo receiveInfo) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "receiveInfoId:" + receiveInfo.getId()));
        
        //------------------------------------
        //振る舞い検知情報生成
        //------------------------------------
        SendInfo sendInfo = receiveInfo.getSendInfo();
        if (sendInfo.isSendFileCheckFlg()) {
            // ふるまい検知ファイル情報を作成
            for (SendFile sendFile : sendInfo.getSendFiles()) {
                CheckedFile checkedFile = new CheckedFile();
                checkedFile.setId(IdUtil.createUUID());
                checkedFile.setReceiveInfoId(receiveInfo.getId());
                checkedFile.setFileName(sendFile.getFileName());
                checkedFile.setFilePath(sendFile.getFilePath());
                checkedFile.setFileFormat(sendFile.getFileFormat());
                checkedFile.setFileSize(sendFile.getFileSize());
                checkedFile.setCheckedFlg(false);
                checkedFile.setFileMessage("");
                checkedFileService.create(checkedFile);
                receiveInfo.getCheckedFiles().add(checkedFile);
            }
        }

    //TODO SandBlastへのメール送信処理をコメント化 [v2.2.1]
//        // SandBlast＆votiro無害化を実行するメール送信を呼び出す
//        mailManager.sendMailSandBlast(funcId, receiveInfo, false);
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "receiveInfoId:" + receiveInfo.getId()));
    }
    
    /**
     * パスワード解除の場合の別タイミングで生成済みの同ファイルの情報(UploadFileInfo/ReceiveFile)からの反映処理
     * @param ugi 
     */
    private void updateBySameFileForPasswordUnlock(UploadGroupInfo ugi)
    {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "uploadGroupInfo.Id:" + ugi.getId()));
        try {
            //----------------------------------
            //同ファイルからの反映処理
            //----------------------------------
            for(UploadFileInfo ufi: ugi.getUploadFileInfos()){
                //①同ファイルの既存UploadFileInfoを検索（全ステップ）
                //※共通DBからの検索のため、別トランザクションでの検索とする必要がある。
                List<UploadFileInfo> ufiLst = ugiManager.getSameUploadFileInfosByStepLst(ufi, null);
                LOG.debug("#UT#v2.2.1#getSameUploadFileInfosByStepLst UploadFileInfo count:{}", ufiLst.size());
                //②UploadFileInfoに該当レコードがあった場合                      
                //    ②-1. Stepが「キャンセル」または「ダウンロード済み」があった場合
                //        該当UploadFileInfoから状態を反映する。⇒無害化完了ポーリングの処理対象となる。
                //    ②-２. Stepが「キャンセル」または「ダウンロード済み」が無かった場合
                //        反映処理をしない。⇒Votiroアップロードポーリング/SandBlastアップロードポーリングの処理対象となる。
                if(!ufiLst.isEmpty()){
                    for(UploadFileInfo chkUfi: ufiLst){
                        LOG.debug("#UT#v2.2.1#getSameUploadFileInfosByStepLst chkUploadFileInfo.Id:{}, .StepKbn:{}", chkUfi.getId(), chkUfi.getStep());
                        if(chkUfi.getStep() == StepKbn.Cancel.value || chkUfi.getStep() == StepKbn.VotiroDownloaded.value){
                            LOG.debug("#同ファイル反映#  パスワード解除無害化開始_同ファイル反映処理. 進行中の同ファイルが存在するため既存UploadFileInfoの値を反映します。"
                                    + "(UploadFileInfoId:{}, UploadGroupInfoId:{}, FileName:{}, ReceiveInfoId:{}, ReceiveFileId:{}, SendFileId:{},"
                                    + " ＜進行中UploadFileInfo＞ ReceiveInfoId:{}, Id:{}, Step:{})",
                                    ufi.getFileId(), ufi.getId(), ufi.getFileNameOrg(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getSendFileId(),
                                    chkUfi.getReceiveInfoId(), chkUfi.getFileId(), StepKbn.getStepKbn(chkUfi.getStep()));

                            ugiManager.copyInfoBySameFile(ufi, chkUfi); //データ反映（Step以外）
                            ufi.setStep(chkUfi.getStep());              //Step反映
                            ugi.setFinCount(ugi.getFinCount()+1);       //UploadGroupInfoのFinConuntもインクリメントする
                            break;
                        }
                    }
                    continue;
                }

                //③UploadFileInfoに該当レコードがなかった場合、同一ファイルで無害化処理済みのReceiveFileを検索
                //    検索キー：                  
                //          SendFileIDが同値            
                //          AND　パスワード解除区分が同値
                //          AND　sanitizeFlg（無害化済みフラグ）=true
                //          AND　result（無害化結果）in（1,2,4,5） ←6(Cancel）、7(無害化なし）は対象外とする。
                //    ※検索対象ReceiveFileに、パスワード解除処理前の自ReceiveFileレコードも含む。
                //    既に無害化処理をした後に再度パスワード解除から無害化実行した場合
                //    現状では、既に無害化済みのファイル、パスワード解除状態に変化の無い（パスワード未解除）のファイルも再度Votiroにアップロードされ、同じ結果となる。
                //    このようなファイルも既存結果を流用することができる。
                //    ※receiveFile.votiroFilePath（追加項目）のファイルが存在しない場合は対象外とする。
                ReceiveFile srcRcvFile = null;
                //ReceiveFileを検索
                List<ReceiveFile> rcvFileLst = receiveFileService.findReceiveFileSameFile(
                        ufi.getReceiveInfoId(), ufi.getSendFileId(), ufi.getDecryptKbn());
                LOG.debug("#UT#v2.2.1#findReceiveFileSameFile sendFileId:{}, decryptKbn:{}, ReceiveFile count:{}", ufi.getSendFileId(), ufi.getDecryptKbn(), rcvFileLst.size());
                for(ReceiveFile rf: rcvFileLst){
                    LOG.debug("#UT#v2.2.1#findReceiveFileSameFile chkReceiveFile.Id:{}, .Result:{}", rf.getId(), rf.getResult());
                    if(rf.getResult() == ResultKbn.SANITIZED.value){
                        //無害化済みの場合、VotiroFilePathのファイルが存在するかチェックする
                        if(StringUtils.isBlank(rf.getVotiroFilePath()))
                            continue;
                        if(!Files.exists(Paths.get(rf.getVotiroFilePath())))
                            continue;                    
                    }
                    //該当ReceiveFileが見つかった場合
                    srcRcvFile = rf;
                    break;
                }
                //④UploadGroupInfo/FileInfoを生成する
                //    ④-1. ③で該当レコードがあった場合                  
                //        該当ReceiveFileから状態を反映する。
                //          Step = 「Votiroダウンロード済み」
                //          ⇒無害化完了ポーリングの処理対象となる。
                //    ④-２. ③で該当レコードが無かった場合
                //        反映処理をしない。⇒Votiroアップロードポーリングの処理対象となる。            
                if(srcRcvFile != null){
                    ResultKbn resKbn = ResultKbn.getResultKbn(srcRcvFile.getResult());
                    String status = "";                             //ステータス
                    switch(resKbn){
                        case SANITIZED:
                            //無害化完了
                            status = VotStatus.EnmStatus.Done.name();
                            break;
                        case BLOCKED:
                            //ブロック
                            status = VotStatus.EnmStatus.Blocked.name();
                            break;
                        case ERROR:
                            //エラー
                            break;
                        case REJECTED:
                            //無害化非対応エラー
                            status = VotStatus.EnmStatus.Error.name();
                            break;
                        case NONE:
                            //無害化なし
                            break;
                        default:
                            continue;
                    }
                    LOG.debug("#同ファイル反映# パスワード解除無害化開始_同ファイル反映処理. 無害化完了した同ファイルが存在するため既存ReceiveFileの値を反映します。"
                            + "(UploadFileInfoId:{}, UploadGroupInfoId:{}, FileName:{}, ReceiveInfoId:{}, ReceiveFileId:{}, SendFileId:{},"
                            + " ＜完了ReceiveFile＞ ReceiveInfoId:{}, ReceiveFileId:{}, status:{}, Result:{})",
                            ufi.getFileId(), ufi.getId(), ufi.getFileNameOrg(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getSendFileId(),
                            srcRcvFile.getReceiveInfo().getId(), srcRcvFile.getId(), status, resKbn);

                    ufi.setVotiroFilePath(srcRcvFile.getVotiroFilePath());
                    ufi.setStatus(status);
                    ufi.setSanitizeFlg(true);
                    ufi.setSandBlastResult(srcRcvFile.getSandBlastResult());    //SandBlast無害化結果（ふるまい検知の更新に使用するため必要）
                    //エラー情報[v2.2.3]
                    ufi.setReportFilePath(srcRcvFile.getReportFilePath());
                    ufi.setErrDetails(srcRcvFile.getErrDetails());
                    ufi.setErrInfo(srcRcvFile.getErrInfo());
                    ufi.setErrFile(srcRcvFile.getFileErrCode());
                    
                    ufi.setStep(StepKbn.VotiroDownloaded.value);     //ステップ
                    ugi.setFinCount(ugi.getFinCount()+1);       //UploadGroupInfoのFinConuntもインクリメントする                
                }
            }            
        } catch (Exception e) {
            //同ファイルからの反映処理は失敗してもそのまま（反映せず）続行する。
            LOG.warn("#! updateBySameFileForPasswordUnlock Exception! ", e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }
    
    /**
     * SandBlast対応区分を取得
     * @param sendInfo
     * @return 
     * configに未定義の場合は例外(runtimeException）発生するが、あえてcatchせずエラー発生させる。
     */
    public SandBlastKbn getSandBlastKbn(SendInfo sendInfo){
        boolean isMail = sendInfo.isAttachmentMailFlg();
        Item sandBlastKbnItem;
        if(isMail){
            //メール無害化用
            sandBlastKbnItem = itemHelper.find(Item.SANDBLASTKBN_MAIL, Item.FUNC_COMMON);            
        }else{
            //ファイル交換用
            sandBlastKbnItem = itemHelper.find(Item.SANDBLASTKBN_FILE, Item.FUNC_COMMON);            
        }
        SandBlastKbn sandBlastKbn = SandBlastKbn.NONE;
        if(sandBlastKbnItem != null){
            sandBlastKbn = SandBlastKbn.getSandBlastKbn(sandBlastKbnItem.getValue());
        }
        if(sandBlastKbn == SandBlastKbn.CHECK_ONLY 
           && !sendInfo.isSendFileCheckFlg()){
            //SandBlast使用区分がCheckOnlyの場合、ふるまい検知フラグがfalseの場合はSandBlast送信する意味がないのでSandBlast不使用とする
            //(※基本的にはそのような設定をすることは無いはず）
            sandBlastKbn = SandBlastKbn.NONE;
        }        
        return sandBlastKbn;
    }
    
    /**
     * 無害化完了時の処理
     *
     * @param receiveInfo RecieveInfo
     */
    @Transactional
    public void onComplete(ReceiveInfo receiveInfo) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "receiveInfoId:" + receiveInfo.getId()));
        boolean isSanitizedLargeMail = false;
        boolean isLarge = receiveInfo.getSendInfo().isLargeFlg();   //[248対応（簡易版）]largeFlgバグ対応

        // 無害化前に大容量と判定されたメールに対しては、無害化後のメールサイズチェックは行わない
        if (!isLarge && receiveInfo.isAttachmentMailFlg()) {
            try {
                // 無害化後のメールサイズ
//                String emlPath = CommonUtil.getSetting("receivedir") + receiveInfo.getId() + ".eml";
                File emlFile = new File(CommonUtil.getFolderReceive(receiveInfo, false, true),
                    receiveInfo.getId() + ".eml");
                long emlFileSize = emlFile.length();

                // 無害化後のファイルサイズN件の合計サイズ
                long receiveFileSize = 0;
                for (ReceiveFile file : receiveInfo.getReceiveFiles()) {
                    receiveFileSize += file.getFileSize();
                }
                //[v2.2.3]
                //削除理由ファイルのファイルサイズを加算
                File deleteReasonFile = DeleteReasonFileLogic.getDeleteReasonFileRecv(receiveInfo);
                if (deleteReasonFile.exists()) {
                    receiveFileSize += deleteReasonFile.length();
                }

                // メールサイズ計算
                // -------------------------------------
                // 計算式 : 無害化後のメールサイズ + (無害化後のファイルN件の合計サイズ * 1.5)
                //                   （base64による増加33％＋変動要素及びマージンを考慮し＋17％）を計算
                // -------------------------------------
                double receiveMailSize = emlFileSize + (receiveFileSize * 1.5);

                // 無害化後の大容量ファイル判定（機能ＩＤは共通）
                Item item = itemHelper.find(Item.MAIL_SIZE_LIMIT_SANITIZED_INNER, Item.FUNC_COMMON, receiveInfo); //[248対応（簡易版）]
                long mailSizeLimitSanitized = Long.parseLong(item.getValue());
                if (receiveMailSize > (double) (mailSizeLimitSanitized)) {          // emlファイルサイズが上限を超えている場合
                    isSanitizedLargeMail = true;
                    //receiveInfo.getSendInfo().setLargeFlg(isSanitizedLargeMail);  //[248対応（簡易版）]largeFlgバグ対応。sendInfoのLargeFlgには反映しない
                    isLarge = isSanitizedLargeMail;                     //[248対応（簡易版）]largeFlgバグ対応
                }
            } catch (Exception e) {
                LOG.error(e.toString() + "msg:" + e.getMessage());
                throw e;
            }
        }

        if (receiveInfo.isAttachmentMailFlg() && !isLarge) {
            // -------------------------------------
            // メール送信（受信ファイル）
            //  ①メールで送信されている
            //  ②大容量メールではない
            // -------------------------------------
            // [2017/02/10] メール本体無害化済み判定
            if (receiveInfo.isMailSanitizedFlg()) {
                mailManager.sendMailReceiveFilesForMailSanitized(receiveInfo);
            } else {
                mailManager.sendMailReceiveFiles(receiveInfo);
            }
        } else if (receiveInfo.isAttachmentMailFlg() && receiveInfo.isMailSanitizeFlg() && receiveInfo.getSendInfo().getSendFiles().isEmpty() && isLarge) {
            // -------------------------------------
            // 添付ファイルなし大容量メールの無害化対応
            //  ①メールで送信されている
            //  ②メール本文無害化の対象である
            //  ③添付ファイルがない
            //  ④大容量メールである
            // -------------------------------------
            mailManager.sendMailTextOnlyForMailSanitized(receiveInfo);          // 無害化済みメール本文の送信（添付ファイル無し）
        } else {
            // [2017/01/04] 大容量ファイルはダウンロード通知へ
            // ファイル同期処理の呼び出し
//            syncFilesHelper.syncFiles(CommonUtil.getSetting("receivedir") + receiveInfo.getId());
            syncFilesHelper.syncFiles(CommonUtil.getFolderReceive(receiveInfo, false, false));

            if (isSanitizedLargeMail) {
                // 無害化後のメールサイズ超過
                // ※無害化後のメール本文(+ダウンロードURL付加)
                mailManager.sendMailTextWithURL(receiveInfo);   //[ver.2.1.11]バグ対応 mailSanitizeFlg=Falseにも対応
            } else {
                // [2017/02/06] 大容量ファイル＆メール本体無害化の場合、本文のみメールを送信
                // ※メール本体無害化がＮＧ(isMailSanitizedFlg=false)の場合も本文のみメールは送信する
                if (isLarge && receiveInfo.isMailSanitizeFlg()) {
                    mailManager.sendMailTextOnlyForMailSanitized(receiveInfo);      // 無害化済みメール本文の送信（添付ファイル無し）
                }
                // ダウンロード通知メール送信（受信ファイル）
                mailManager.sendMailDownLoad(receiveInfo, isLarge);
            }
        }
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "receiveInfoId:" + receiveInfo.getId()));
    }

    //[v2.2.1]追加
    /**
     * アップロード対象ファイルを取得する
     * @param ugi
     * @param ufi
     * @return 
     */
    public File getUploadTargetFile(UploadGroupInfo ugi, UploadFileInfo ufi)
    {
        //--------------------------
        //対象送信ファイル取得
        //--------------------------
        File uploadFilePath;
        if(ugi == null){
            ugi = ugiManager.findUploadGroupInfo(ufi.getId());
            if(ugi == null){
                //UploadGroupInfoが見つからない場合。（通常あり得ないが）
                throw new RuntimeException("UploadGroupInfoが見つかりません。 UploadFileInfoId:"+ufi.getFileId() + " UploadGroupInfoId:"+ufi.getId());
            }
        }
        if( ufi.getSandBlastKbn() == SandBlastKbn.USE_VOTIRO.value
            && ufi.isCheckedFileFlg()){
            //----------------------------
            // VotiroアップロードでSandBlastの結果ファイルを使用する場合
            // 【条件】 
            //    かつSandBlast結果ファイルをVotiroアップロードに使用する場合（京都）
            //    かつふるまい検知でOKの場合
            //----------------------------
            //フォルダ：{sandblastフォルダ}/ReceiveInfoID/
            String uploadFolder =  CommonUtil.getFolderSandBlast(ugi);
            //送信ファイルの実ファイルパス（SandBlast送信時に既にID+拡張子にリネーム済み）
            uploadFilePath = new File(uploadFolder, ufi.getFileName());  
        }else{
            //----------------------------
            //SandBlastアップロード時、またはVotiroアップロードでSandBlast結果ファイルをVotiroアップロードに使用しない場合
            //----------------------------

            //Sendフォルダから送信ファイルを取得
            //メール本体（ReceiveFileIdがセットされてない）か添付・送信ファイルの分岐
            if(StringUtils.isEmpty(ufi.getReceiveFileId())){
                //----------------------------
                //メール本体
                //----------------------------
                //メール本体ファイルはsendフォルダ直下のsendInfoId+".eml"
                String uploadFolder =  CommonUtil.getFolderSend(ugi, true);
                String emlname = ugi.getSendInfoId() + ".eml";
                uploadFilePath = new File(uploadFolder, emlname);    //送信ファイルの実ファイルパス
            }else{
                //----------------------------
                //添付・送信ファイル
                //----------------------------
                //パスワード有無による分岐
                if(ufi.getDecryptKbn() == DecryptKbn.NONE.value){
                    //----------------------------
                    //パスワード無しファイル
                    //----------------------------
                    //フォルダ：{sendフォルダ}/SendInfoID/
                    String uploadFolder =  CommonUtil.getFolderSend(ugi, false);
                    //送信ファイルの実ファイルパス                        
                    uploadFilePath = new File(uploadFolder, ufi.getFileNameOrg());  
                }else{
                    //----------------------------
                    //パスワード付きファイル
                    //----------------------------
                    //フォルダ：{dectyptフォルダ}/ReceiveInfoID/
                    String uploadFolder =  CommonUtil.getFolderDecrypt(ugi);
                    //送信ファイルの実ファイルパス                 
                    uploadFilePath = new File(uploadFolder, ufi.getFileNameOrg());  
                }
            }
        }        
        return uploadFilePath;
    }
}
