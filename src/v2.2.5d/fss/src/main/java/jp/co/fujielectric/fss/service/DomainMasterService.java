package jp.co.fujielectric.fss.service;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.entity.DomainMaster;

/**
 * ドメインマスターサービス
 */
@AppTrace
@SqlTrace
@ApplicationScoped
public class DomainMasterService {
    @PersistenceContext(unitName = "fssdb")
    private EntityManager em;

    public DomainMaster find(String domain) {
        return em.find(DomainMaster.class, domain);
    }
}
