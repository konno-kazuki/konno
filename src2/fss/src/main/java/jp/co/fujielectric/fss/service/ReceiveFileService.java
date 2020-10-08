package jp.co.fujielectric.fss.service;

import java.util.ArrayList;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.data.CommonEnum;
import jp.co.fujielectric.fss.data.CommonEnum.DecryptKbn;
import jp.co.fujielectric.fss.entity.ReceiveFile;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendFile;
import jp.co.fujielectric.fss.util.IdUtil;

/**
 * 受信ファイルサービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class ReceiveFileService {

    @Inject
    private EntityManager em;

    @Transactional
    public void create(ReceiveFile entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(ReceiveFile entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(String id) {
        ReceiveFile entity = em.find(ReceiveFile.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    public ReceiveFile find(String id) {
        return em.find(ReceiveFile.class, id);
    }
    
    /**
     * ReceiveFile（複数） の生成
     * @param receiveInfo
     * @param sendFileLst
     * @param flgReject     //対象外かどうか
     * @return 
     */
    @Transactional
    public List<ReceiveFile> createReceiveFiles(ReceiveInfo receiveInfo, List<SendFile> sendFileLst, boolean flgReject){
        List<ReceiveFile> receiveFileLst = new ArrayList<>();
        for(SendFile sendFile: sendFileLst){
            receiveFileLst.add(createReceiveFile(receiveInfo, sendFile, flgReject));
        }
        return receiveFileLst;
    }
    
    /**
     * ReceiveFile の生成
     * @param receiveInfo
     * @param sendFile
     * @param flgReject     //対象外かどうか
     * @return 
     */
    @Transactional
    public ReceiveFile createReceiveFile(ReceiveInfo receiveInfo, SendFile sendFile, boolean flgReject)
    {
        ReceiveFile receiveFile = new ReceiveFile();
        receiveFile.setId(IdUtil.createUUID());
        receiveFile.setFileName(sendFile.getFileName());
        receiveFile.setFileFormat(sendFile.getFileFormat());
        receiveFile.setTargetFlg(true);
        receiveFile.setSendFileId(sendFile.getId());
        receiveFile.setDecryptKbn(0);   //パスワード無し
        receiveFile.setResult(0);       //処理中
        receiveFile.setVotiroFilePath("");
        receiveFile.setExcludeFlg(false);
        receiveFile.setFileMessage("");
        receiveFile.setReceiveFlg(false);       // 未受信
        receiveFile.setReceiveTime(null);       // 〃
        receiveFile.resetDate();   //更新日付,作成日付
        if(flgReject){
            //無害化対象外
            receiveFile.setFilePath(sendFile.getFilePath());
            receiveFile.setFileSize(sendFile.getFileSize());
            receiveFile.setSanitizeFlg(true);
            receiveFile.setResult(CommonEnum.ResultKbn.NONE.value); //無害化なし
        }else{
            // 無害化対象
            receiveFile.setFilePath("");
            receiveFile.setFileSize(0);
            receiveFile.setSanitizeFlg(false);            
        }
        
        receiveFile.setSendFile(sendFile);
        receiveFile.setReceiveInfo(receiveInfo);
        receiveInfo.getReceiveFiles().add(receiveFile);
        return receiveFile;
    }
    
    //[v2.2.1]
    /**
     * 処理済みの同一ファイルレコードを検索する
     * @param receiveInfoId
     * @param sendFileId
     * @param decryptKbn
     * @return 
     */
    public List<ReceiveFile> findReceiveFileSameFile(
            String receiveInfoId, String sendFileId, int decryptKbn) {
        
        //パスワード解除区分＝5(パスワード付きファイル入りZIP　パスワード未解除あり）の場合は対象外とする
        if(decryptKbn == DecryptKbn.ENCRYPTZIP.value){
            return new ArrayList<>();
        }
        
        //【検索条件】
        // SendFileIDが同値
        // AND パスワード解除区分が同値
        // AND (sanitizeFlg（無害化済みフラグ）=true または receiveInfoIdが同値）※自ReceiveInfoはsanitizeFlgは無視する。
        // AND result in (1,2,4,5）
        return em.createNamedQuery( ReceiveFile.NAMED_QUEUE_FIND_SAMEFILE, ReceiveFile.class)
                .setParameter("receiveInfoId", receiveInfoId)
                .setParameter("sendFileId", sendFileId)
                .setParameter("decryptKbn", decryptKbn)
                .getResultList();            
    }
}
