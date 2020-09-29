package jp.co.fujielectric.fss.service;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.entity.OriginalFileLog;

/**
 * 原本ファイルログサービス
 */
@ApplicationScoped
public class OriginalFileLogService {

    @Inject
    private EntityManager em;

    @Transactional
    public void create(OriginalFileLog entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(OriginalFileLog entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(String id) {
        OriginalFileLog entity = em.find(OriginalFileLog.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }
}
