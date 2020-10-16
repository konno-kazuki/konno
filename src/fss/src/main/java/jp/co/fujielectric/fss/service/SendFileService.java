package jp.co.fujielectric.fss.service;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.entity.SendFile;

/**
 * 送信ファイルサービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class SendFileService {

    @Inject
    private EntityManager em;

    @Transactional
    public void create(SendFile entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(SendFile entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(String id) {
        SendFile entity = em.find(SendFile.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    public SendFile find(String id) {
        return em.find(SendFile.class, id);
    }
}
