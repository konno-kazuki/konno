package jp.co.fujielectric.fss.service;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.entity.BasicUser;

/**
 * ユーザサービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class BasicUserService {

    @Inject
    private EntityManager em;

    @Transactional
    public BasicUser find(String id) {
        return em.find(BasicUser.class, id);
    }

    @Transactional
    public List<BasicUser> findAll() {
        // CriteriaBuilder 及び CriteriaQuery の作成
        javax.persistence.criteria.CriteriaBuilder cb = em.getCriteriaBuilder();
        javax.persistence.criteria.CriteriaQuery<BasicUser> cq = cb.createQuery(BasicUser.class);
        // cq から root の取得
        javax.persistence.criteria.Root<BasicUser> root = cq.from(BasicUser.class);
        // selectの作成
        cq.select(root);
        // orderの作成  （userIdでソート）
        cq.orderBy(cb.asc(root.get("userId")));
        return em.createQuery(cq).getResultList();
    }

    @Transactional
    public void create(BasicUser entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(BasicUser entity) {
        em.merge(entity);
    }
    
    @Transactional
    public void remove(String id) {
        BasicUser entity = em.find(BasicUser.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }
}
