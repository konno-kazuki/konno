package jp.co.fujielectric.fss.service;

import java.util.Date;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TemporalType;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.entity.ProcessingTimeReport;
import jp.co.fujielectric.fss.util.DateUtil;

/**
 * 処理時間記録サービス
 */
@AppTrace
@SqlTrace
@ApplicationScoped
public class ProcessingTimeReportService {

    @PersistenceContext(unitName = "fssdb")
    private EntityManager em;

    /**
     * 計測日時指定検索
     * @param measureTime
     * @return
     */
    @Transactional
    public ProcessingTimeReport find(Object measureTime) {
        return em.find(ProcessingTimeReport.class, measureTime);
    }

    /**
     * 全件検索
     * @return
     */
    public List<ProcessingTimeReport> findAll() {
        Query query = em.createNamedQuery(ProcessingTimeReport.QUERY_FINDALL, ProcessingTimeReport.class);
        return query.getResultList();
    }

    /**
     * 当日検索
     * @return
     */
    public List<ProcessingTimeReport> findAllByToday() {
        //当日の0:00
        Date fromDate = DateUtil.getDateExcludeTime(DateUtil.getSysDate());
        //当日の23:59
        Date toDate = DateUtil.getDateExcludeMillisExpirationTime(fromDate);

        // 当日分の情報を取得する
        return findAllByStartEnd(fromDate, toDate);
    }

    /**
     * 計測日時範囲指定検索
     * @param fromDate 計測日時(from)
     * @param toDate   計測日時(to)
     * @return
     */
    public List<ProcessingTimeReport> findAllByStartEnd(Date fromDate, Date toDate) {

        //　計測日時が指定日時範囲の情報を取得する
        Query query = em.createNamedQuery(ProcessingTimeReport.QUERY_FIND_BY_FROMTO, ProcessingTimeReport.class);
        query.setParameter("fromDate", fromDate, TemporalType.TIMESTAMP);
        query.setParameter("toDate", toDate, TemporalType.TIMESTAMP);
        return query.getResultList();
    }


    @Transactional
    public void create(ProcessingTimeReport entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(ProcessingTimeReport entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(Long id) {
        ProcessingTimeReport entity = em.find(ProcessingTimeReport.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }
}
