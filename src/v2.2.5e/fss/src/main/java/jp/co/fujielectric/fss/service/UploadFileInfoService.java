package jp.co.fujielectric.fss.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.data.CommonEnum;
import jp.co.fujielectric.fss.data.CommonEnum.StepKbn;
import jp.co.fujielectric.fss.entity.UploadFileInfo;
import jp.co.fujielectric.fss.entity.UploadFileInfoComp;
import jp.co.fujielectric.fss.util.DateUtil;

/**
 * アップロードサービス
 */
//@AppTrace
//@SqlTrace
@ApplicationScoped
public class UploadFileInfoService {

//    @Inject
    @PersistenceContext(unitName = "fssdb")
    private EntityManager em;

    @Transactional
    public UploadFileInfo find(String id) {
        return em.find(UploadFileInfo.class, id);
    }    

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public UploadFileInfo findNew(String id) {
        return em.find(UploadFileInfo.class, id);
    }    
    
    /**
     * 行取得＆行ロック
     * @param id
     * @return 
     */
    @Transactional
    public UploadFileInfo findWithLock(String id) {
        return em.find(UploadFileInfo.class, id, LockModeType.PESSIMISTIC_WRITE);
    }    
    
    @Transactional
    public void create(UploadFileInfo entity) {
        entity.setUpdateDate(DateUtil.getSysDateExcludeMillis());   //更新日付
        entity.setInsertDate(entity.getUpdateDate());   //作成日付
        em.persist(entity);
    }

    @Transactional
    public UploadFileInfo edit(UploadFileInfo entity) {
        entity.setUpdateDate(DateUtil.getSysDateExcludeMillis());   //更新日付
        if(entity.getInsertDate() == null)
            entity.setInsertDate(entity.getUpdateDate());   //作成日付
        return em.merge(entity);
    }

    @Transactional
    public void flush() {
        em.flush();
    }
    
    @Transactional
    public void remove(UploadFileInfo entity) {
        if (entity != null) {
            em.remove(entity);
        }
    }

    @Transactional
    public void refresh(UploadFileInfo entity) {
        if (entity != null) {
            em.refresh(entity);
        }
    }
    
    @Transactional
    public void create(UploadFileInfoComp uploadStatus) {
        em.persist(uploadStatus);
    }

    @Transactional
    public UploadFileInfoComp edit(UploadFileInfoComp uploadStatus) {
        return em.merge(uploadStatus);
    }

    @Transactional
    public void remove(UploadFileInfoComp entity) {
        if (entity != null) {
            em.remove(entity);
        }
    }
    
    public List<UploadFileInfo> findUploadFileInfoAliveByGroup(String groupId) {
        return em.createNamedQuery("findFileInfoAliveByGroup", UploadFileInfo.class)
                .setParameter("id", groupId)
                .getResultList();
    }

    public List<UploadFileInfo> findUploadFileInfoByGroup(String groupId) {
        return em.createNamedQuery(UploadFileInfo.NAMED_QUEUE_FINDBY_GRPID, UploadFileInfo.class)
                .setParameter("id", groupId)
                .getResultList();
    }

    public List<UploadFileInfoComp> findUploadFileInfoCompByGroup(String groupId) {
        return em.createNamedQuery("findFileInfoCompByGroup", UploadFileInfoComp.class)
                .setParameter("id", groupId)
                .getResultList();
    }

    /**
     * 単一ステップに該当するレコードを検索する
     * @param step
     * @param owner
     * @return 
     */
    public List<UploadFileInfo> findUploadFileInfoByStep(StepKbn step, String owner) {
        return em.createNamedQuery( UploadFileInfo.NAMED_QUEUE_FINDBY_STEP, UploadFileInfo.class)
                .setParameter("step", step.value)
                .setParameter("owner", owner)
                .getResultList();
    }

    /**
     * 複数ステップに該当するレコードを検索する
     * @param stepList
     * @param owner
     * @return 
     */
    public List<UploadFileInfo> findUploadFileInfoBySteplist(List<StepKbn> stepList, String owner) {
        return em.createNamedQuery( UploadFileInfo.NAMED_QUEUE_FINDBY_STEP, UploadFileInfo.class)
                .setParameter("step", StepKbn.getValueLst(stepList))
                .setParameter("owner", owner)
                .getResultList();
    }    

    /**
     * 他ReceiveInfoに紐づく同一ファイルのレコードを検索する
     * @param sendFileId
     * @param decryptKbn
     * @param stepList
     * @param owner
     * @return 
     */
    public List<UploadFileInfo> findUploadFileInfoSameFile(
            String sendFileId, int decryptKbn, List<StepKbn> stepList, String owner ) {
                
        //パスワード解除区分＝5(パスワード付きファイル入りZIP　パスワード未解除あり）の場合は対象外とする
        if(decryptKbn == CommonEnum.DecryptKbn.ENCRYPTZIP.value){
            return new ArrayList<>();
        }
        
        if(stepList == null || stepList.isEmpty()){
            //ステップ指定なし
            return em.createNamedQuery( UploadFileInfo.NAMED_QUEUE_FIND_SAMEFILE_ALLSTEP, UploadFileInfo.class)
                    .setParameter("sendFileId", sendFileId)
                    .setParameter("decryptKbn", decryptKbn)
                    .setParameter("owner", owner)
                    .getResultList();            
        }else{
            //ステップ指定有り
            return em.createNamedQuery( UploadFileInfo.NAMED_QUEUE_FIND_SAMEFILE_INSTEP, UploadFileInfo.class)
                    .setParameter("sendFileId", sendFileId)
                    .setParameter("decryptKbn", decryptKbn)
                    .setParameter("stepList", StepKbn.getValueLst(stepList))
                    .setParameter("owner", owner)
                    .getResultList();                        
        }
    }

    /**
     * タイムアウトのレコードを検索する
     * @param step
     * @param owner
     * @param timeoutDate タイムアウト基準日時
     * @return 
     */
    public List<UploadFileInfo> findUploadFileInfoTimeOut(StepKbn step, String owner, Date timeoutDate) {
        return em.createNamedQuery( UploadFileInfo.NAMED_QUEUE_FINDBY_TIMEOUT, UploadFileInfo.class)
                .setParameter("step", step.value)
                .setParameter("owner", owner)
                .setParameter("timeoutDate", timeoutDate)
                .getResultList();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public UploadFileInfo editCommit(UploadFileInfo entity) {
        return edit(entity);
    }

    //[v2.2.3]
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<UploadFileInfo> editNew(List<UploadFileInfo> entityList) {
        List<UploadFileInfo> list = new ArrayList<>();
        for (UploadFileInfo entity : entityList) {
            list.add(edit(entity)) ;
        }
        return list;
    }
    
}
