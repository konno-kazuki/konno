package jp.co.fujielectric.fss.service;

import java.util.Date;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TemporalType;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.entity.Nortice;
import jp.co.fujielectric.fss.util.DateUtil;

/**
 * お知らせサービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class NorticeService {

    @Inject
    private EntityManager em;

    @Transactional
    public Nortice find(Object id) {
        return em.find(Nortice.class, id);
    }

    @Transactional
    public List<Nortice> findAll() {
        String queryName = "Nortice.findAll";
        TypedQuery<Nortice> query = em.createNamedQuery(queryName, Nortice.class);
        List<Nortice> norticeDatas = query.getResultList();

        return norticeDatas;
    }

    @Transactional
    public List<Nortice> findAllByToday() {
        Date _sysDate = DateUtil.getDateExcludeTime(DateUtil.getSysDate());

        // 掲載期間に当日が含まれる情報を取得する
        String queryName = "Nortice.findAllByToday";
        TypedQuery<Nortice> query = em.createNamedQuery(queryName, Nortice.class);
        query.setParameter("today", _sysDate, TemporalType.TIMESTAMP);
        List<Nortice> norticeDatas = query.getResultList();

        return norticeDatas;
    }

    @Transactional
    public void create(Nortice entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(Nortice entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(Long id) {
        Nortice entity = em.find(Nortice.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }
}
