package jp.co.fujielectric.fss.service;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.entity.SendRequestInfo;

/**
 * 送信依頼サービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class SendRequestInfoService {

    @Inject
    private EntityManager em;

    @Transactional
    public void create(SendRequestInfo entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(SendRequestInfo entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(String id) {
        SendRequestInfo entity = em.find(SendRequestInfo.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    public SendRequestInfo find(String id) {
        return em.find(SendRequestInfo.class, id);
    }
}
