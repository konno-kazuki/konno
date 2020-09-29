package jp.co.fujielectric.fss.service;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.entity.DecryptFile;
import jp.co.fujielectric.fss.entity.ReceiveFile;
import jp.co.fujielectric.fss.util.DateUtil;

/**
 * パスワード解除ファイルサービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class DecryptFileService {

    @Inject
    private EntityManager em;

    /**
     * レコード追加（永続化）
     * ※対象Entityに対し、事前にResetDate()で作成日付・更新日付をセットすることを忘れずに
     * @param entity 
     */
    @Transactional
    public void create(DecryptFile entity) {
        em.persist(entity);
    }

    /**
     * レコード更新（永続化）
     * ※対象Entityに対し、事前にResetDate()で更新日付をセットすることを忘れずに
     * @param entity 
     */
    @Transactional
    public void edit(DecryptFile entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(String id) {
        DecryptFile entity = em.find(DecryptFile.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    public DecryptFile find(String id) {
        return em.find(DecryptFile.class, id);
    }

    public List<DecryptFile> findReceiveInfoId(String receiveInfoId) {
        // NamedQueryを利用して名称からエンティティを取得
        Query query = em.createNamedQuery("DecryptFile.findReceiveInfoId", DecryptFile.class);
        query.setParameter("receiveInfoId", receiveInfoId);
        return query.getResultList();
    }

    @Transactional
    public void deleteDecryptFiles(ReceiveFile receiveFile) {
        for (DecryptFile decryptFile : receiveFile.getDecryptFiles()) {
            remove(decryptFile.getId());
        }
    }
}
