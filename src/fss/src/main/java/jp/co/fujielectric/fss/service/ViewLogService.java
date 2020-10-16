package jp.co.fujielectric.fss.service;

import java.util.Date;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.entity.ViewLog;

/**
 * Viewトレースサービス
 */
@ApplicationScoped
public class ViewLogService {

    @Inject
    private EntityManager em;

    @Transactional
    public void create(ViewLog entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(ViewLog entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(String id) {
        ViewLog entity = em.find(ViewLog.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    public ViewLog find(String id) {
        return em.find(ViewLog.class, id);
    }
    
    /**
     * VIEWトレース出力情報取得
     *
     * @param dateFrom (検索)期間-開始
     * @param dateTo (検索)期間-終了
     *
     * @return VIEWトレース出力情報
     */
    public List<ViewLog>findForOutput(Date dateFrom, Date dateTo) {
        
        String queryName = "ViewLog.findForOutput";
        Query query = em.createNamedQuery(queryName, ViewLog.class);
        query.setParameter("dateFrom", dateFrom);
        query.setParameter("dateTo", dateTo);

        List<ViewLog> logDatas = query.getResultList();
        return logDatas;
    }
}
