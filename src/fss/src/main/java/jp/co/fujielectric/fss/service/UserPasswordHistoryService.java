package jp.co.fujielectric.fss.service;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.entity.UserPasswordHistory;

/**
 * ユーザーパスワード履歴サービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class UserPasswordHistoryService {

    @Inject
    private EntityManager em;

    public UserPasswordHistory find(String id) {
        return em.find(UserPasswordHistory.class, id);
    }

    /**
     * 指定したユーザIDの全レコード一覧取得
     * @param userId
     * @return 
     */
    public List<UserPasswordHistory> findByUserId(String userId) {
        return em.createNamedQuery(UserPasswordHistory.NAMEDQUERY_FIND_BY_USERID, UserPasswordHistory.class)
                .setParameter("userId", userId)
                .getResultList();
    }
   
    @Transactional
    public void create(UserPasswordHistory entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(UserPasswordHistory entity) {
        em.merge(entity);
    }
    
    @Transactional
    public void remove(String id) {
        UserPasswordHistory entity = em.find(UserPasswordHistory.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }
}
