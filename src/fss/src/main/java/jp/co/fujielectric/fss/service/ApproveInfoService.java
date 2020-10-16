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
import jp.co.fujielectric.fss.entity.ApproveInfo;

/**
 * 承認情報サービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class ApproveInfoService {

    @Inject
    private EntityManager em;

    @Transactional
    public ApproveInfo find(String id) {
        return em.find(ApproveInfo.class, id);
    }

    @Transactional
    public List<ApproveInfo> findAll() {
        // CriteriaBuilder 及び CriteriaQuery の作成
        javax.persistence.criteria.CriteriaBuilder cb = em.getCriteriaBuilder();
        javax.persistence.criteria.CriteriaQuery<ApproveInfo> cq = cb.createQuery(ApproveInfo.class);
        // cq から root の取得
        javax.persistence.criteria.Root<ApproveInfo> root = cq.from(ApproveInfo.class);
        // selectの作成
        cq.select(root);
        // orderの作成  （userIdでソート）
        cq.orderBy(cb.asc(root.get("userId")));
        return em.createQuery(cq).getResultList();
    }

    @Transactional
    public void create(ApproveInfo entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(ApproveInfo entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(String id) {
        ApproveInfo entity = em.find(ApproveInfo.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    /**
     * 承認履歴一覧取得
     *
     * @param approveMailAddress メールアドレス
     *
     * @return 承認履歴一覧
     */
    public List<ApproveInfo>findForApproveHistory(String approveMailAddress) {

        String queryName = "ApproveInfo.findForApproveHistory";
        Query query = em.createNamedQuery(queryName, ApproveInfo.class);
        query.setParameter("approveMailAddress", approveMailAddress);

        List<ApproveInfo> approveInfoDatas = query.getResultList();

        return approveInfoDatas;
        
    }

    /**
     * 送信情報IDから承認情報リストを取得
     *
     * @param sendInfoId 送信情報ID
     *
     * @return 承認情報リスト
     */
    public List<ApproveInfo>findForSendInfoId(String sendInfoId) {

        String queryName = "ApproveInfo.findForSendInfoId";
        Query query = em.createNamedQuery(queryName, ApproveInfo.class);
        query.setParameter("approveId", sendInfoId);

        List<ApproveInfo> approveInfoDatas = query.getResultList();

        return approveInfoDatas;
        
    }

    /**
     * 送信情報IDからGrowl用承認情報リストを取得
     *
     * @param sendInfoId 送信情報ID
     *
     * @return 承認情報リスト
     */
    public List<ApproveInfo>findForGrowl(String sendInfoId) {

        String queryName = "ApproveInfo.findForGrowl";
        Query query = em.createNamedQuery(queryName, ApproveInfo.class);
        query.setParameter("approveId", sendInfoId);

        List<ApproveInfo> approveInfoDatas = query.getResultList();

        return approveInfoDatas;
        
    }
}
