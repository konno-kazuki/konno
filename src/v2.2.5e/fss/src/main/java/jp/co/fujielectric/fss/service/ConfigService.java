package jp.co.fujielectric.fss.service;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.entity.Config;
import org.apache.logging.log4j.Logger;

/**
 * 機能設定サービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class ConfigService {
    @Inject
    private EntityManager em;

    @Inject
    private Logger LOG;
    
    
    @Transactional
    public Config find(Object id) {
        return em.find(Config.class, id);
    }

    @Transactional
    public List<Config> findAll() {
//        //全件取得
//        javax.persistence.criteria.CriteriaQuery<Config> cq = em.getCriteriaBuilder().createQuery(Config.class);
//        cq.select(cq.from(Config.class));
//        return em.createQuery(cq).getResultList();

        // NamedQueryを利用して名称からエンティティを取得（全件をソートして取得）
        String queryName = "Config.findAllConfig";
        Query query = em.createNamedQuery(queryName, Config.class);
        List<Config> configLst = query.getResultList();
        return configLst;
    }

    /**
     * キーとFuncIDを指定して検索
     * @param key
     * @param funcId
     * @return 
     */
    public Config findByKey(String key, String funcId) {
        TypedQuery<Config> tq = em.createNamedQuery("Config.findConfig", Config.class);
        try {
            tq.setParameter("itemKey", key);
            tq.setParameter("funcId", funcId);
            return tq.getSingleResult();
        } catch (Exception e) {
            LOG.error("findByKey Error. Key:" + key + ", funcId=" + funcId);
            throw e;
        }
    }
    
    @Transactional
    public void create(Config entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(Config entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(String id) {
        Config entity = em.find(Config.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }
}
