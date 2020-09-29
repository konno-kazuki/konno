package jp.co.fujielectric.fss.service;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.entity.Define;

/**
 * 定義サービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class DefineService {
    @Inject
    private EntityManager em;

    @Transactional
    public Define find(Object id) {
        return em.find(Define.class, id);
    }

    @Transactional
    public List<Define> findAll() {
//        javax.persistence.criteria.CriteriaQuery<Define> cq = em.getCriteriaBuilder().createQuery(Define.class);
//        cq.select(cq.from(Define.class));
//        return em.createQuery(cq).getResultList();

        javax.persistence.criteria.CriteriaBuilder cb = em.getCriteriaBuilder();
        javax.persistence.criteria.CriteriaQuery<Define> cq = em.getCriteriaBuilder().createQuery(Define.class);
        // cq から root の取得
        javax.persistence.criteria.Root<Define> root = cq.from(Define.class);
        // selectの作成
        cq.select(root);
        // orderの作成  （itemKeyでソート）
        cq.orderBy(cb.asc(root.get("itemKey")));
        return em.createQuery(cq).getResultList();

    }

    @Transactional
    public void create(Define entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(Define entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(String id) {
        Define entity = em.find(Define.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }
}
