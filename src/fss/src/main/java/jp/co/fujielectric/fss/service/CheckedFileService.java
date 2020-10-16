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
import jp.co.fujielectric.fss.entity.CheckedFile;

/**
 * ふるまい検知ファイルサービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class CheckedFileService {
    @Inject
    private EntityManager em;

    @Transactional
    public void create(CheckedFile entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(CheckedFile entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(String id) {
        CheckedFile entity = em.find(CheckedFile.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    public CheckedFile find(String id) {
        return em.find(CheckedFile.class, id);
    }

    public List<CheckedFile> findReceiveInfoId(String receiveInfoId) {
        // NamedQueryを利用して名称からエンティティを取得
        Query query = em.createNamedQuery("CheckedFile.findReceiveInfoId", CheckedFile.class);
        query.setParameter("receiveInfoId", receiveInfoId);
        return query.getResultList();
    }
}
