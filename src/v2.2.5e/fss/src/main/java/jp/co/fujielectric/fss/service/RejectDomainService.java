package jp.co.fujielectric.fss.service;

import java.util.List;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.entity.RejectDomain;
import org.apache.commons.lang3.StringUtils;

/**
 * 送信不可ドメイン管理サービス
 */
//@AppTrace
//@SqlTrace
@ApplicationScoped
public class RejectDomainService {

    @Inject
    private EntityManager em;
    
    @Transactional
    public RejectDomain find(Object id) {
        return em.find(RejectDomain.class, id);
    }
    
    @Transactional
    public List<RejectDomain> findAll() {
        javax.persistence.criteria.CriteriaQuery<RejectDomain> cq = em.getCriteriaBuilder().createQuery(RejectDomain.class);
        cq.select(cq.from(RejectDomain.class));
        return em.createQuery(cq).getResultList();
    }
    
    /**
     * 送信不可ドメインのリストを取得
     * @return 
     */
    @Transactional
    public List<String> getDomainListAll() {
        //送信不可ドメイン管理テーブルから全レコード取得
        List<RejectDomain> lst = findAll();
        //送信不可ドメイン列を文字列のリスト型で取得（前後ブランクの削除、全文字の小文字化）
        return lst.stream()
                .filter(rec-> !StringUtils.isBlank(rec.getDomain()))
                .map(x -> x.getDomain().trim().toLowerCase())
                .collect(Collectors.toList());
    }
    
}
