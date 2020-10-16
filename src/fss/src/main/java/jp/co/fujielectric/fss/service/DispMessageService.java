package jp.co.fujielectric.fss.service;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.entity.DispMessage;

/**
 * 画面メッセージサービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class DispMessageService {
    @Inject
    private EntityManager em;

    public DispMessage find(Object id) {
        return em.find(DispMessage.class, id);
    }

    public List<DispMessage> findAll() {
        javax.persistence.criteria.CriteriaQuery<DispMessage> cq = em.getCriteriaBuilder().createQuery(DispMessage.class);
        cq.select(cq.from(DispMessage.class));
        return em.createQuery(cq).getResultList();
    }

    @Transactional
    public void create(DispMessage entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(DispMessage entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(String id) {
        DispMessage entity = em.find(DispMessage.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }
}
