package jp.co.fujielectric.fss.service;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.entity.UserTypePermission;

/**
 * ユーザ権限サービス
 */
@AppTrace
@SqlTrace
@ApplicationScoped
public class UserTypePermissionService {

    @Inject
    private EntityManager em;

    @Transactional
    public UserTypePermission find(Object id) {
        return em.find(UserTypePermission.class, id);
    }

    @Transactional
    public List<UserTypePermission> findAll() {
        javax.persistence.criteria.CriteriaQuery<UserTypePermission> cq = em.getCriteriaBuilder().createQuery(UserTypePermission.class);
        cq.select(cq.from(UserTypePermission.class));
        return em.createQuery(cq).getResultList();
    }

    @Transactional
    public UserTypePermission findByUnique(String link, String userTypeId, String sectionId) {
        TypedQuery<UserTypePermission> tq = em.createNamedQuery("UserTypePermission.findUnique", UserTypePermission.class);
        tq.setParameter("link", link);
        tq.setParameter("userTypeId", userTypeId);
        tq.setParameter("sectionId", sectionId);
        List<UserTypePermission> list = tq.getResultList();

        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }
}
