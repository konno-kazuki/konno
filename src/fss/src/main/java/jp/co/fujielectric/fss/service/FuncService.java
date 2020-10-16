package jp.co.fujielectric.fss.service;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.entity.Func;

/**
 * 機能マスタサービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class FuncService {
    @Inject
    private EntityManager em;

    @Transactional
    public Func find(Object id) {
        return em.find(Func.class, id);
    }

    @Transactional
    public List<Func> findAll() {
        javax.persistence.criteria.CriteriaQuery<Func> cq = em.getCriteriaBuilder().createQuery(Func.class);
        cq.select(cq.from(Func.class));
        return em.createQuery(cq).getResultList();
    }

    @Transactional
    public void create(Func entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(Func entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(String id) {
        Func entity = em.find(Func.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }
}
