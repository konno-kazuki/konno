package jp.co.fujielectric.fss.service;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.entity.MonthlyReport;

/**
 * 月報サービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class MonthlyReportService {

    @Inject
    private EntityManager em;

    @Transactional
    public MonthlyReport find(Object id) {
        return em.find(MonthlyReport.class, id);
    }

    @Transactional
    public List<MonthlyReport> findAll() {
        String queryName = "MonthlyReport.findAll";
        Query query = em.createNamedQuery(queryName, MonthlyReport.class);
        List<MonthlyReport> monthlyReportDatas = query.getResultList();

        return monthlyReportDatas;
    }

    @Transactional
    public void create(MonthlyReport entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(MonthlyReport entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(Long id) {
        MonthlyReport entity = em.find(MonthlyReport.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }
    
    /**
     * 月報表示一覧情報取得
     *
     * @return 月報表示一覧情報
     */
    public List<MonthlyReport>findForMonthlyReportList() {
        String queryName = "MonthlyReport.findForMonthlyReportList";
        Query query = em.createNamedQuery(queryName, MonthlyReport.class);

        List<MonthlyReport> monthlyReportDatas = query.getResultList();

        return monthlyReportDatas;
    }
}
