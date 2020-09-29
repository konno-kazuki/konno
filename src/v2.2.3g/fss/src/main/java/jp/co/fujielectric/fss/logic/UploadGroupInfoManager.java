package jp.co.fujielectric.fss.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.data.CommonEnum.DecryptKbn;
import jp.co.fujielectric.fss.data.CommonEnum.StepKbn;
import jp.co.fujielectric.fss.entity.ReceiveFile;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.entity.UploadFileInfo;
import jp.co.fujielectric.fss.entity.UploadFileInfoComp;
import jp.co.fujielectric.fss.entity.UploadGroupInfo;
import jp.co.fujielectric.fss.entity.UploadGroupInfoComp;
import jp.co.fujielectric.fss.exception.FssException;
import jp.co.fujielectric.fss.service.UploadFileInfoService;
import jp.co.fujielectric.fss.util.BeanUtils;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.IdUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.Logger;

/**
 * アップロードグループ情報マネージャ
 */
@ApplicationScoped
//@SqlTrace
//@Transactional(Transactional.TxType.REQUIRES_NEW)
@Transactional
public class UploadGroupInfoManager {

    @PersistenceContext(unitName = "fssdb")
    private EntityManager rootEm;

    @Inject
    private UploadFileInfoService uploadFileInfoService;

    @Inject
    private Logger LOG;

    /**
     * リトライMAX(UploadFileInfo)
     */
    private int retryMax = 0;

    /**
     * リトライMAX(UploadGroupInfo)
     */
    private int retryMaxGrp = 0;

    /**
     * リトライMAX(SandBlast用)
     */
    private int retryMaxSB = 0;
    
    public UploadGroupInfoManager(){
        //リトライマックス値をsetting.propertiesから取得する
        String pollingRetryMax = CommonUtil.getSetting("polling_retry");
        if (NumberUtils.isNumber(pollingRetryMax)) {
            retryMax = Integer.parseInt(pollingRetryMax);
        }        
        String completeRetryMax = CommonUtil.getSetting("completePolling_retry");
        if (NumberUtils.isNumber(completeRetryMax)) {
            retryMaxGrp = Integer.parseInt(completeRetryMax);
        }        
        String sandBlastRetryMax = CommonUtil.getSetting("sandBlastPolling_retry");
        if (NumberUtils.isNumber(sandBlastRetryMax)) {
            retryMaxSB = Integer.parseInt(sandBlastRetryMax);
        }        
    }
    
    //[v2.2.1]
    /**
     * UploadGroupInfo の生成
     * @param gid
     * @param regionId
     * @param owner
     * @param fileCnt
     * @param recvInfo
     * @return 
     */
    @Transactional
    public UploadGroupInfo createUploadGroupInfo(
            String gid, 
            String regionId,
            String owner, 
            int fileCnt,
            ReceiveInfo recvInfo) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "ReceiveInfoId:" + recvInfo.getId()));
        
        SendInfo sendInfo = recvInfo.getSendInfo();
        
        UploadGroupInfo ugi = new UploadGroupInfo();
        ugi.setId(gid);
        ugi.setMainId(recvInfo.getId());
        ugi.setSendInfoId(sendInfo.getId());
        ugi.setRegionId(regionId);
        ugi.setFinCount(0);
        ugi.setFileCount(fileCnt);
        ugi.setOwner(owner);
        ugi.setRetryCount(0);
        ugi.setProcDate(sendInfo.getProcDate());
        ugi.setCancelFlg(false);
        ugi.setMailFlg(sendInfo.isAttachmentMailFlg());
        
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "ReceiveInfoId:" + recvInfo.getId()));
        return ugi;
    }

    
    //[v2.2.1]
    /**
     * UploadfileInfo の生成
     * @param uploadGroupInfoId
     * @param rcvInfo
     * @param rcvFile
     * @param owner
     * @param step
     * @param decryptKbn
     * @return 
     */
    @Transactional
    public UploadFileInfo createUploadFileInfo(
            String uploadGroupInfoId,
            ReceiveInfo rcvInfo,
            ReceiveFile rcvFile,
            String owner,
            StepKbn step,
            DecryptKbn decryptKbn
            ){
        UploadFileInfo ufi = new UploadFileInfo();
        ufi.setFileId(IdUtil.createUUID());
        ufi.setOwner(owner);
        ufi.setSandBlastKbn(rcvInfo.getSandBlastKbn());
        ufi.setRequestId("");
        ufi.setStep(step.value);
        ufi.setRetryCount(0);
        ufi.setReceiveInfoId(rcvInfo.getId());
//        ufi.setUploadFilePath("");  //TODO
        ufi.setVotiroFilePath("");
        ufi.setErrorInfo("");
        ufi.setId(uploadGroupInfoId);
        ufi.setSanitizeFlg(false);
        ufi.setStatus("");
        ufi.setDecryptKbn(decryptKbn.value);
        if(rcvFile != null){
            //メール本体ファイル以外
            ufi.setSendFileId(rcvFile.getSendFileId());
            ufi.setFileNameOrg(rcvFile.getFileName());
            ufi.setReceiveFileId(rcvFile.getId());
            ufi.setFileName(rcvFile.getId() + "." + rcvFile.getFileFormat());    //ファイル名はReceiveFileID+FileType
        }else{
            //メール本体ファイル
            ufi.setSendFileId(rcvInfo.getSendInfoId());     //メール本体の場合はSendFileidにSendInfoIdをセットする
            ufi.setFileNameOrg("");
            ufi.setReceiveFileId("");
            ufi.setFileName(rcvInfo.getId() + ".eml");    //ファイル名はReceiveInfoID+".eml"
        }
        return ufi;
    }
    
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void moveUploadGroupInfoToComp(String id) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "UploadGroupInfoId:" + id));
//        UploadGroupInfo uploadGroupInfo = rootEm.find(UploadGroupInfo.class,id);
        UploadGroupInfo uploadGroupInfo = findUploadGroupInfo(id);
        if(uploadGroupInfo == null){
            //UploadGroupInfoが見つからない場合。（通常あり得ないが）
            throw new RuntimeException("moveUploadGroupInfoToComp Error. UploadGroupInfoが見つかりません。 UploadGroupInfoId:"+id);
        }                    
        UploadGroupInfoComp uploadGroupInfoComp = new UploadGroupInfoComp();
        BeanUtils.beanToBean(uploadGroupInfoComp, uploadGroupInfo);
        rootEm.merge(uploadGroupInfoComp);
        rootEm.remove(uploadGroupInfo);

        // [2017/07/04]処理速度改善のため、UploadFileInfoCompの削除処理を除去
        List<UploadFileInfo> uploadFileInfoList = uploadFileInfoService.findUploadFileInfoByGroup(id);
        for (UploadFileInfo uploadFileInfo : uploadFileInfoList) {
            UploadFileInfoComp uploadFileInfoComp = new UploadFileInfoComp();
            BeanUtils.beanToBean(uploadFileInfoComp, uploadFileInfo);
            // 今回のUploadFileInfoCompの登録
            // ※[2017/07/04]Compデータの削除処理をなくしたため、万が一競合しても問題ないようにマージ処理に変更
            uploadFileInfoService.edit(uploadFileInfoComp);
            uploadFileInfoService.remove(uploadFileInfo);
        }

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }

    /**
     * キャンセルフラグの更新
     * @param id
     * @param cancelFlg
     * @return UploadGroupInfo (指定IDのEntityがない場合はnull）
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public UploadGroupInfo updateCancelFlg(String id, boolean cancelFlg) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));

        //最新のUploadGroupInfoを取得
        UploadGroupInfo entity = rootEm.find(UploadGroupInfo.class, id);
        if(entity == null)
            return null;
        entity.setCancelFlg(cancelFlg);

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        return entity;
    }
    
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public UploadGroupInfo findUploadGroupInfo(String id) {
//        return rootEm.createNamedQuery("findGroup", UploadGroupInfo.class)
//                .setParameter("id", id)
//                .getSingleResult();
        return rootEm.find(UploadGroupInfo.class, id);        
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<UploadGroupInfo> findUploadGroupInfoByOnwer(String owner) {
        return rootEm.createNamedQuery("findGroupByOwner", UploadGroupInfo.class)
                .setParameter("owner", owner)
                .getResultList();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<UploadGroupInfo> findUploadGroupInfoAliveByOnwer(String owner) {
        return rootEm.createNamedQuery("findAliveGroups", UploadGroupInfo.class)
                .setParameter("owner", owner)
                .getResultList();
    }

    @Transactional
    public UploadGroupInfo edit(UploadGroupInfo entity) {
        entity.setUpdateDate(DateUtil.getSysDateExcludeMillis());   //更新日付 [v2.2.1]
        if(entity.getInsertDate() == null)
            entity.setInsertDate(entity.getUpdateDate());   //作成日付 [v2.2.1]
        return rootEm.merge(entity);
    }
    
    //[v2.2.1]
    /**
     * UploadGroupInfoのマージ＆即時コミット
     * @param uploadGroupInfo 
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void editCommit(UploadGroupInfo uploadGroupInfo){
        edit(uploadGroupInfo);
    }
    
    //[v2.2.1]
    /**
     * 複数UploadGroupInfo/FileInfoのマージ＆即時コミット
     * @param uploadGroupInfos 
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void editCommit(List<UploadGroupInfo> uploadGroupInfos){
        for(UploadGroupInfo ugi: uploadGroupInfos){
            for(UploadFileInfo ufi: ugi.getUploadFileInfos()){
                uploadFileInfoService.edit(ufi);
            }
            this.edit(ugi);
        }
    }
        
    //[v2.2.1]
    /**
     * UploadFileInfoのリトライカウントアップ
     * @param ufi 
     * @param errMsg
     * @param flgExCheck
     * @return  true:リトライカウントオーバー　false:未リトライカウントオーバー
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean retryCountUpFileInfo(UploadFileInfo ufi, String errMsg, boolean flgExCheck) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "uploadFileInfoId:" + ufi.getFileId(), "errMsg:" + errMsg));
        
        boolean isCountOver = false;
        try {
            //引数で渡されたUploadFileInfoを使ってDB更新、最新情報取得は排他チェックの目的で使用する。[v2.2.3a]
            
            //最新のUploadFileInfoを取得（排他）
            UploadFileInfo ufiTmp = uploadFileInfoService.findWithLock(ufi.getFileId());            
            //[排他]念の為、別のトランザクションでステップが更新されていないか調べ、更新されていれば処理を続行しない
            if(ufiTmp == null || ufiTmp.getStep() != ufi.getStep()){
                LOG.warn("#retryCountUpFileInfo. 該当のUploadFileInfoが見つからない、またはステップが既に更新されているため、リトライカウントアップ処理をスキップします。[fileId:{}]", ufi.getFileId());
                return false;
            }            
            //排他チェック（同一レコードに対して同時アクセスの可能性がある場合にチェックする）
            //※仕様によりこの処理は不要となったが、念の為残しておく
            if(flgExCheck){
                //（例：SandBlastダウンロードタイムアウトチェック処理）
                if(ufiTmp.getStep() != ufi.getStep() || !(ufiTmp.getUpdateDate().equals(ufi.getUpdateDate()))){
                    //ステップまたは更新日時が違うので、他処理で更新済みと判断し何もせず抜ける（排他対応）
                    LOG.debug("# リトライカウントアップ　排他によるスキップ.  [src.Step:{}, now.Step:{}, src.UpdateDate:{}, now.UpdateDate:{}]", 
                            ufiTmp.getStep(), ufi.getStep(), ufiTmp.getUpdateDate(), ufi.getUpdateDate());
                    return false;
                }
            }
            
            //リトライカウントのインクリメント
            int retryCount = ufi.getRetryCount();
            ufi.setRetryCount(++retryCount);
            
            int rMax;               //リトライMax値
            StepKbn retryStep;      //リトライ時のステップ
            if(ufi.getStep() == StepKbn.SandBlastUploading.value || ufi.getStep() == StepKbn.StartWait.value ){
                //SandBlast処理でのエラーの場合
                rMax = retryMaxSB;  //SandBlast送信中のリトライMax値
                ufi.setSandBlastResult(errMsg);   //エラー情報
                retryStep = StepKbn.StartWait;      //リトライ時のステップは無害化開始待ち
            }else{
                rMax = retryMax;            //リトライMax値
                ufi.setErrorInfo(errMsg);   //エラー情報
                retryStep = StepKbn.VotiroUploadWait;   //リトライ時のステップはVotiroアップロード待ち
            }
            if(rMax >= 0 && retryCount > rMax){
                //リトライカウントオーバー
                LOG.debug("# リトライカウントオーバー. UploadFileInfoId:{}, UploadGroupInfoId:{}, ReceiveInfoId:{}, ReceiveFileId:{}, Step:{}, RetryCount:{}"
                        ,ufi.getFileId(), ufi.getId(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getStep(), ufi.getRetryCount());
                //キャンセル処理は別のトランザクションでするため呼出元で呼び出す。
                isCountOver = true;
            }else{
                //リトライカウントオーバーじゃない場合は、ステップを戻してリトライする
                LOG.debug("# リトライ.  --uploadFileInfoId:{}, nextStep:{}, fromStep:{}", ufi.getFileId(), retryStep, ufi.getStep());
                ufi.setStep(retryStep.value);
            }
            uploadFileInfoService.edit(ufi);    //更新（更新日付セット）
            rootEm.flush();     //DB反映（コミットではない）
            return isCountOver;
        } catch (Exception e) {
            //リトライカウントアップ（エラー処理）は例外をスローしない
            LOG.error("#! UploadFileInfo RetryCountUp エラー.", e);
            return false;
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }
    
    //[v2.2.1]
    /**
     * UploadGroupInfoのリトライカウントアップ
     * @param ugi 
     * @return  true:リトライカウントオーバー　false:未リトライカウントオーバー
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean retryCountUpGroupInfo(UploadGroupInfo ugi) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "uploadGroupInfoId:" + ugi.getId()));
        boolean isCountOver = false;
        try {
            //最新のUploadGroupInfoを取得
            ugi = findUploadGroupInfo(ugi.getId());
            //リトライカウントのインクリメント
            int retryCount = ugi.getRetryCount();
            ugi.setRetryCount(++retryCount);
            LOG.debug("# UploadGroupInfo Retry Count Up.  UploadGroupInfoId:{}, RetryCount:{}", ugi.getId(), ugi.getRetryCount());
            if(retryMaxGrp >= 0 && retryCount > retryMaxGrp){
                //リトライカウントオーバー
                isCountOver = true;
                LOG.debug("# UploadGroupInfo Retry Count Over. and Set CancelFlg.  UploadGroupInfoId:{}", ugi.getId());
                ugi.setCancelFlg(true); //キャンセルフラグをセット
            }
            this.edit(ugi);    //更新（更新日付セット）
            rootEm.flush();     //DB反映（コミットではない）
            return isCountOver;
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }    

    //[v2.2.1]
    /**
     * UploadFileInfoの処理ステップの更新
     * @param ufi
     * @param stepKbn
     * @return 
     * @throws FssException 
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public UploadFileInfo updateStep(UploadFileInfo ufi, StepKbn stepKbn) throws FssException
    {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "uploadFileInfoId:" + ufi.getFileId(), "Step:" + stepKbn.name()));
        try {           
            //最新のUploadFileInfoを取得
            UploadFileInfo ufiTmp = uploadFileInfoService.find(ufi.getFileId());
            //念の為、別のトランザクションでステップが更新されていないか調べ、更新されていれば処理を続行しない
            if(ufiTmp == null || ufiTmp.getStep() != ufi.getStep()){
                LOG.warn("#updateStep. 該当のUploadFileInfoが見つからない、またはステップが既に更新されているため、ステップ更新処理をスキップします。[fileId:{}]", ufi.getFileId());
                return ufi;
            }
            //SandBlast処理からVotiroダウンロード待ちに更新する場合、リトライカウントをリセットする
            if((ufi.getStep() == StepKbn.SandBlastUploading.value || ufi.getStep() == StepKbn.StartWait.value)
                    && stepKbn == StepKbn.VotiroUploadWait){
                ufi.setRetryCount(0);
            }            
            //ステップ更新
            ufi = updateStepSub(ufi, stepKbn.value);

            //[キャンセル」[ダウンロード済み]に変更する場合、同一ファイルへの反映処理をする。（同一トランザクション内で）        
            if(stepKbn == StepKbn.Cancel || stepKbn == StepKbn.VotiroDownloaded){
                updateSameFileProcessing(ufi);
            }
            return ufi;            
        } catch (Exception e) {
            LOG.error("#! UpdateStep Error!  errMsg:" + e.getMessage(), e);
            //コミットして戻りたいのでRuntimeException以外の例外をスローする
            throw new FssException("UpdateStep Error!", e);
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));            
        }
    }
    
    //[v2.2.1]
    /**
     * UploadFileInfoの処理ステップの更新
     * @param ufi
     * @param step
     * @return 
     */
    @Transactional
    private UploadFileInfo updateStepSub(UploadFileInfo ufi, int step) throws FssException
    {
        try {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "uploadFileInfoId:" + ufi.getFileId(), "Step:" + step));
            //UploadFileInfoのステップを変更
            ufi.setStep(step);
            ufi = uploadFileInfoService.edit(ufi);    //更新（更新日付セット）
            ufi = rootEm.merge(ufi);

            //[キャンセル」[ダウンロード済み]に変更する場合、UploadGroupInfoの処理済み件数をインクリメントする
            if(step == StepKbn.Cancel.value || step == StepKbn.VotiroDownloaded.value){
                //UploadGroupInfoの処理済み件数を更新（ロックをかけて同時実行に対応する）
                UploadGroupInfo ugi = rootEm.find(UploadGroupInfo.class, ufi.getId(), LockModeType.PESSIMISTIC_WRITE);
                if(ugi != null){
                    ugi.setFinCount(ugi.getFinCount() + 1);
                    edit(ugi);                    
                }else{
                    LOG.warn("#! ステップ更新.　UploadGroupInfoが見つかりませんでした。 UploadFileInfoId:{}, UploadGroupInfoId:{}, Step:{}"
                            , ufi.getFileId(), ufi.getId(), step);
                }
            }
            rootEm.flush();
            return ufi;            
        } catch (Exception e) {
            //例外発生した場合
            rootEm.clear(); //この処理でのDB更新を破棄する
            throw new FssException("ステップ更新に失敗しました。 UploadFileInfoId:" + ufi.getFileId(), e);  //ロールバックさせないようRuntimeException以外をスローする
        } finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));            
        }
    }    
    
    
    //[v2.2.1]    
    /**
     * 全ファイル無害化済みUploadGroupInfo取得
     * @param owner
     * @return 
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<UploadGroupInfo> findUploadGroupInfoSanitizedAll(String owner) {
        return rootEm.createNamedQuery(UploadGroupInfo.NAMED_QUEUE_FIND_SANITIZEDALL, UploadGroupInfo.class)
                .setParameter("owner", owner)
                .getResultList();
    }

    //[v2.2.1]
    /**
     * 同ファイル進行中チェック
     * ※このメソッドでエラー発生しても呼出元には影響しないようにする必要がある。
     * @param ufi
     * @return 
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean chkSameFileProcessing(UploadFileInfo ufi){
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        try {
            //同じSendFileに指定(複数）のステップに紐づくUploadFileInfoを取得する
            //＜検索対象ステップ＞
            //SandBlastアップロード時：「SandBlastアップロード中」or「Votiroアップロード待ち」or「ダウンロード待ち」
            //Votiroアップロード時：　「ダウンロード待ち」
            List<StepKbn> stepLst;
            if(ufi.getStep() == StepKbn.StartWait.value){
                stepLst = Arrays.asList(StepKbn.SandBlastUploading, StepKbn.VotiroUploadWait, StepKbn.VotiroDownloadWait);
            }else{
                stepLst = Arrays.asList(StepKbn.VotiroDownloadWait);
            }
            List<UploadFileInfo> ufiList = getSameUploadFileInfosByStepLst(ufi, stepLst);
            
            if(ufiList != null && !ufiList.isEmpty()){
                //一つでも指定ステップの同一ファイルがあれば進行中有りとする
                LOG.debug("#同ファイル反映# 同ファイル進行中チェック. 進行中同ファイルが存在するため処理をスキップします。"
                        + "(Step:{}, UploadFileInfoId:{}, UploadGroupInfoId:{}, FileName:{}, ReceiveInfoId:{}, ReceiveFileId:{}, SendFileId:{},"
                        + " ＜進行中＞  ReceiveInfoId:{}, UploadFileInfoId:{}, Step:{})",
                        StepKbn.getStepKbn(ufi.getStep()), ufi.getFileId(), ufi.getId(), ufi.getFileNameOrg(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getSendFileId(),
                        ufiList.get(0).getReceiveInfoId(), ufiList.get(0).getFileId(), StepKbn.getStepKbn(ufiList.get(0).getStep()));
                return true;
            }
            return false;
        } catch (Exception e) {
            LOG.error("#! 同ファイル進行中チェックエラー.　errMsg:" + e.getMessage(), e);
            return false;   //例外をスローしない
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));            
        }
    }    
   
    //[v2.2.1]
    /**
     * 同ファイルへの反映処理
     * トランザクション：引継ぐ
     * ※呼出元が失敗した場合には、このメソッドでの更新（同ファイルの更新）もロールバックされる必要がある。
     * ※このメソッドでの更新（同ファイルの更新）が失敗した場合でも、呼出元には影響しないようにする必要がある。
     * @param ufi
     * @return 
     */
    @Transactional
    private boolean updateSameFileProcessing(UploadFileInfo ufi){
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        boolean chkFlg = false;
        try {
            //同じSendFileに指定(複数）のステップに紐づくUploadFileInfoを取得する
            //検索対象ステップ :「無害化待ち」or「Votiroアップロード待ち」
            List<StepKbn> stepLst = Arrays.asList(StepKbn.StartWait, StepKbn.VotiroUploadWait);
            List<UploadFileInfo> ufiList = getSameUploadFileInfosByStepLst(ufi, stepLst);
            //同ファイルに対して結果、ステップを反映する
            for(UploadFileInfo eUfi: ufiList){
                LOG.debug("#同ファイル反映# 同ファイルへの反映処理. 処理待ちの同ファイルに処理結果を反映します。"
                        + "(Step:{}, UploadFileInfoId:{}, UploadGroupInfoId:{}, FileName:{}, ReceiveInfoId:{}, ReceiveFileId:{}, SendFileId:{},"
                        + " ＜反映対象＞ ReceiveInfoId:{}, UploadFileInfoId:{} Step:{})",
                        StepKbn.getStepKbn(ufi.getStep()), ufi.getFileId(), ufi.getId(), ufi.getFileNameOrg(), ufi.getReceiveInfoId(), ufi.getReceiveFileId(), ufi.getSendFileId(), 
                        eUfi.getReceiveInfoId(), eUfi.getFileId(), StepKbn.getStepKbn(eUfi.getStep()));
                copyInfoBySameFile(eUfi, ufi);
                updateStepSub(eUfi, ufi.getStep());     //ステップ更新
            }
            rootEm.flush(); //DB更新する（コミット時にエラー発生する前に、ここで検知するため）
            return chkFlg;            
        } catch (Exception e) {
            LOG.error("#! 同ファイルへの反映処理エラー.　errMsg:" + e.getMessage(), e);
            rootEm.clear();
            return false;   //例外をスローしない（RuntimeExceptionをスローするとトランザクションがロールバックオンリーになってしまう）
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));            
        }
    }    
   
    //[v2.2.1]
    /**
     * 同じSendFileに紐付く指定(複数）ステップのUploadFileInfoを取得する
     * トランザクション：新規
     * ※SanitizeHelperから個別DBに対するトランザクション内から呼出されるため
     * @param ufi
     * @param stepKbnLst
     * @return 
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<UploadFileInfo> getSameUploadFileInfosByStepLst(UploadFileInfo ufi, List<StepKbn> stepKbnLst){
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", 
                "sendFIleId:" +ufi.getSendFileId(), "decryptKbn:"+ufi.getDecryptKbn(),
                "owner:"+ufi.getOwner(), "stepKbnLst:"+stepKbnLst ));        
        List<UploadFileInfo> ufiList = new ArrayList<>();
        try {
            //SendFileIDが同値
            //AND　Step in (指定のステップリスト）
            //AND　パスワード解除区分が同値
            ufiList = uploadFileInfoService.findUploadFileInfoSameFile(
                    ufi.getSendFileId(), ufi.getDecryptKbn(), stepKbnLst, ufi.getOwner());
            if(ufiList.size() > 0 && !StringUtils.isEmpty(ufi.getFileId())){
                for(UploadFileInfo chkUfi: ufiList){
                    if(chkUfi.getFileId().equals(ufi.getFileId())){
                        //自分自身は対象外
                        ufiList.remove(chkUfi);
                        break;
                    }
                }            
            }            
            return ufiList;
        } catch (Exception e) {
            LOG.error("#! getSameUploadFiles. Exception!  errMsg:" + e.getMessage(), e);            
            return ufiList;
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));            
        }
    }
    
    /**
     * 同ファイルのUploadFileInfoの内容を反映する（ステップ以外）
     * @param toUfi     コピー先UploadFileInfo
     * @param fromUfi   コピー元UploadFileInfo
     */
    public void copyInfoBySameFile(UploadFileInfo toUfi, UploadFileInfo fromUfi){
        toUfi.setVotiroFilePath(fromUfi.getVotiroFilePath());
        toUfi.setStatus(fromUfi.getStatus());
        toUfi.setSanitizeFlg(fromUfi.isSanitizeFlg());
        toUfi.setSandBlastResult(fromUfi.getSandBlastResult());    //SandBlast無害化結果（ふるまい検知の更新に使用するため必要）
        toUfi.setErrorInfo(fromUfi.getErrorInfo());
        toUfi.setReportFilePath(fromUfi.getReportFilePath());
        toUfi.setErrInfo(fromUfi.getErrInfo());
        toUfi.setErrFile(fromUfi.getErrFile());
        toUfi.setErrDetails(fromUfi.getErrDetails());
        //SandBlastStartDate,CompDate（実績）は流用の場合には値のコピーをしない。
//        toUfi.setSandblastStartDate(fromUfi.getSandblastStartDate());
//        toUfi.setSandblastCompDate(fromUfi.getSandblastCompDate());
    }

}
