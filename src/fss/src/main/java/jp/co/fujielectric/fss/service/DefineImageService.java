package jp.co.fujielectric.fss.service;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.entity.DefineImage;

/**
 * 定義サービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class DefineImageService {

    @Inject
    private EntityManager em;

    @Transactional
    public DefineImage find(Object id) {
        return em.find(DefineImage.class, id);
    }

    @Transactional
    public List<DefineImage> findAll() {
        javax.persistence.criteria.CriteriaQuery<DefineImage> cq = em.getCriteriaBuilder().createQuery(DefineImage.class);
        cq.select(cq.from(DefineImage.class));
        return em.createQuery(cq).getResultList();
    }

    @Transactional
    public void create(DefineImage entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(DefineImage entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(String id) {
        DefineImage entity = em.find(DefineImage.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }
}
