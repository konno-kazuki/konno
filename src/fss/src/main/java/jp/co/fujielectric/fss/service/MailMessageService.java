package jp.co.fujielectric.fss.service;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.entity.MailMessage;

/**
 * メールメッセージサービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class MailMessageService {

    @Inject
    private EntityManager em;

    public MailMessage find(Object id) {
        return em.find(MailMessage.class, id);
    }

    public List<MailMessage> findAll() {
        javax.persistence.criteria.CriteriaQuery<MailMessage> cq = em.getCriteriaBuilder().createQuery(MailMessage.class);
        cq.select(cq.from(MailMessage.class));
        return em.createQuery(cq).getResultList();
    }

    @Transactional
    public void create(MailMessage entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(MailMessage entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(String id) {
        MailMessage entity = em.find(MailMessage.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }
}
