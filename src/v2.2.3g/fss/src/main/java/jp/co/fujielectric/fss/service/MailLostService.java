package jp.co.fujielectric.fss.service;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.entity.MailLost;

/**
 * メールロストサービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class MailLostService {

    /**
     * メールロスト発生処理区分
     */
    public enum EnmMailLostFunction{
        /**
         * メール受信処理～SandBlast送信処理で滞留
         */
        MailEntrance,
        /**
         * SandBlastメール受信処理で滞留
         */
        VotiroEntrance,
        /**
         * Votiroアップロード処理で滞留
         */
        VotiroUpload,
        /**
         * Votiroダウンロード～無害化完了処理で滞留
         */
        VotiroDownload
    }

    @Inject
    private EntityManager em;

    @Transactional
    public MailLost find(Object id) {
        return em.find(MailLost.class, id);
    }

    @Transactional
    public List<MailLost> findAll() {
        TypedQuery<MailLost> query = em.createNamedQuery(MailLost.NAMED_QUEUE_FIND_ALL, MailLost.class);
        return query.getResultList();
    }

    /**
     * 表示対象一覧取得
     * @return
     */
    @Transactional
    public List<MailLost> findAllForDisp() {
        TypedQuery<MailLost> query = em.createNamedQuery(MailLost.NAMED_QUEUE_FIND_FOR_DISP, MailLost.class);
        return query.getResultList();
    }

    @Transactional
    public void create(MailLost entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(MailLost entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(Long id) {
        MailLost entity = em.find(MailLost.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }
}
