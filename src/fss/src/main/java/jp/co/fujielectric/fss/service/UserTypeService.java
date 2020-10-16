package jp.co.fujielectric.fss.service;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.entity.UserType;

/**
 * ユーザタイプサービス
 */
@AppTrace
@SqlTrace
@ApplicationScoped
public class UserTypeService {

    @Inject
    private EntityManager em;

    @Transactional
    public UserType find(Object id) {
        return em.find(UserType.class, id);
    }

    @Transactional
    public List<UserType> findAll() {
        javax.persistence.criteria.CriteriaQuery<UserType> cq = em.getCriteriaBuilder().createQuery(UserType.class);
        cq.select(cq.from(UserType.class));
        return em.createQuery(cq).getResultList();
    }

    @Transactional
    public List<UserType> findAllSort() {
        // CriteriaBuilder 及び CriteriaQuery の作成
        javax.persistence.criteria.CriteriaBuilder cb = em.getCriteriaBuilder();
        javax.persistence.criteria.CriteriaQuery<UserType> cq = cb.createQuery(UserType.class);
        // cq から root の取得
        javax.persistence.criteria.Root<UserType> root = cq.from(UserType.class);
        // selectの作成
        cq.select(root);
        // orderの作成
        cq.orderBy(cb.asc(root.get("sort")));
        return em.createQuery(cq).getResultList();
    }
}
