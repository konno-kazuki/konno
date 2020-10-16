package jp.co.fujielectric.fss.service;

import java.util.Date;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.Query;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.entity.OnceUser;

/**
 * ワンタイムユーザ情報サービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class OnceUserService {

    @Inject
    private EntityManager em;

    @Transactional
    public void create(OnceUser entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(OnceUser entity) {
        em.merge(entity);
    }
   
    @Transactional
    public void remove(String id) {
        OnceUser entity = em.find(OnceUser.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    public OnceUser find(String id) {
        return em.find(OnceUser.class, id);
    }

    /**
     * 行取得＆行ロック
     * @param id
     * @return 
     */
    public OnceUser findWithLock(String id) {
        return em.find(OnceUser.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    /**
     * 指定したtargetで有効な（expirationTimeが未来の）一覧を取得
     *
     * @param target
     * @param expirationTime
     * @return
     */
    public List<OnceUser> getEffectiveInfo(String target, Date expirationTime) {
        Query query = em.createNamedQuery("OnceUser.getEffectiveInfo", OnceUser.class);
        query.setParameter("target", target);
        query.setParameter("expirationTime", expirationTime);
        List<OnceUser> users = query.getResultList();
        return users;
    }
}
