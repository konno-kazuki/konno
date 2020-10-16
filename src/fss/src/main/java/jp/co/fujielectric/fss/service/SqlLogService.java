package jp.co.fujielectric.fss.service;

import java.util.Date;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.entity.SqlLog;

/**
 * SQLトレースサービス
 */
@ApplicationScoped
public class SqlLogService {

    @Inject
    private EntityManager em;

    @Transactional
    public void create(SqlLog entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(SqlLog entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(String id) {
        SqlLog entity = em.find(SqlLog.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    public SqlLog find(String id) {
        return em.find(SqlLog.class, id);
    }
    
    /**
     * SQLトレース出力情報取得
     *
     * @param dateFrom (検索)期間-開始
     * @param dateTo (検索)期間-終了
     *
     * @return SQLトレース出力情報
     */
    public List<SqlLog>findForOutput(Date dateFrom, Date dateTo) {
        
        String queryName = "SqlLog.findForOutput";
        Query query = em.createNamedQuery(queryName, SqlLog.class);
        query.setParameter("dateFrom", dateFrom);
        query.setParameter("dateTo", dateTo);

        List<SqlLog> logDatas = query.getResultList();
        return logDatas;
    }
}
