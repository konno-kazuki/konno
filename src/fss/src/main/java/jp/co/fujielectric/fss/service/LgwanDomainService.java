package jp.co.fujielectric.fss.service;

import java.util.List;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.entity.LgwanDomain;
import org.apache.commons.lang3.StringUtils;

/**
 * LGWANドメイン管理サービス
 */
//@AppTrace
//@SqlTrace
//@SyncDbAuto
@ApplicationScoped
public class LgwanDomainService {

//    @Inject
    @PersistenceContext(unitName = "fssdb")
    private EntityManager em;

    @Transactional
    public LgwanDomain find(Object id) {
        return em.find(LgwanDomain.class, id);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)    
    public List<LgwanDomain> findAll() {
        javax.persistence.criteria.CriteriaQuery<LgwanDomain> cq = em.getCriteriaBuilder().createQuery(LgwanDomain.class);
        cq.select(cq.from(LgwanDomain.class));
        return em.createQuery(cq).getResultList();
    }
    
    /**
     * LGWANドメインのリストを取得
     * @return 
     */
    public List<String> getDomainListAll() {
        //LGWANドメイン管理テーブルから全レコード取得
        List<LgwanDomain> lst = findAll();
        //LGWANドメイン列を文字列のリスト型で取得（前後ブランクの削除、全文字の小文字化）
        return lst.stream()
                .filter(rec-> !StringUtils.isBlank(rec.getDomain()))
                .map(x -> x.getDomain().trim().toLowerCase())
                .collect(Collectors.toList());
    }
}
