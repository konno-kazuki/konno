package jp.co.fujielectric.fss.service;

import java.util.Date;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.entity.MailQueue;
import jp.co.fujielectric.fss.entity.MailQueueComp;
import jp.co.fujielectric.fss.util.BeanUtils;
import jp.co.fujielectric.fss.util.CommonUtil;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * メールキューサービス
 */
//@AppTrace
//@SqlTrace
//@SyncDbAuto
@ApplicationScoped
public class MailQueueService {

    @PersistenceContext(unitName = "fssdb")
    private EntityManager em;

    @Transactional
    public void addMailQueue(String mailId, String owner, String regionId, String servletCode, String mailDate) {
        Date now = new Date();

        MailQueue entity = new MailQueue();
        entity.setId(mailId);
        entity.setOwner(owner);
        entity.setRegionId(regionId);
        entity.setServletCode(servletCode);
        entity.setRegistTime(now);
        entity.setUpdateTime(now);
        entity.setCancelFlg(false);
        entity.setRetryCount(0);
        entity.setMailDate(mailDate);   //[v2.2.1]

        em.persist(entity);
    }

    @Transactional
    public void create(MailQueue entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(MailQueue entity) {
        entity.setUpdateTime(new Date());
        em.merge(entity);
    }

    @Transactional
    public void remove(String id) {
        MailQueue entity = em.find(MailQueue.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    public MailQueue find(String id) {
        return em.find(MailQueue.class, id);
    }

    public List<MailQueue> findMailQueByOwner(String owner) {
        return em.createNamedQuery("MailQueue.findByOwner", MailQueue.class)
                .setParameter("owner", owner)
                .getResultList();
    }

    @Transactional
    public void retryCountUp(MailQueue entity) {
        int mailQueRetryMax = 0;
        //リトライマックス
        String strMailQueRetryMax = CommonUtil.getSetting("mailQueue_retry"); //フォルダ
        if (NumberUtils.isNumber(strMailQueRetryMax)) {
            mailQueRetryMax = Integer.parseInt(strMailQueRetryMax);
        }
        int retry = entity.getRetryCount();
        entity.setRetryCount(++retry);
        if (mailQueRetryMax >= 0 && retry > mailQueRetryMax) {
            //リトライ上限を超えるのでキャンセルフラグを立てる
            entity.setCancelFlg(true);
        }
        this.edit(entity);
    }

    /**
     * MailQueCompテーブルへ移す
     *
     * @param id
     * @param flgCancel キャンセルフラグ（キャンセルフラグをTrueとしてCOMPに移動させる場合にtrue）
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void moveToComp(String id, boolean flgCancel) {
        //MailQueueCompテーブルへコピー
        MailQueue src = find(id);
        if (src == null) {
            return;
        }
        MailQueueComp entity = new MailQueueComp();
        BeanUtils.beanToBean(entity, src);
//        entity.setId(src.getId());
//        entity.setOwner(src.getOwner());
//        entity.setRegionId(src.getRegionId());
//        entity.setServletCode(src.getServletCode());
//        entity.setRegistTime(src.getRegistTime());
//        entity.setUpdateTime(new Date());
//        entity.setCancelFlg(src.isCancelFlg());
//        entity.setRetryCount(src.getRetryCount());
//        entity.setMailDate(src.getMailDate());  //[v2.2.1]        
        if(flgCancel){
            //強制的にキャンセルとしてCOMPに移動する場合
            entity.setCancelFlg(flgCancel);
            entity.setUpdateTime(new Date());
        }        
        em.merge(entity); // Retry対応

        //MailQueueテーブルからの削除
        em.remove(src);
    }

    /**
     * サーブレットコードを指定した検索
     * @param owner
     * @param servletCode
     * @return 
     */
    public List<MailQueue> findMailQueByServletCode(String owner, String servletCode) {
        return em.createNamedQuery(MailQueue.NAMED_QUEUE_FIND_SERVLETCODE, MailQueue.class)
                .setParameter("owner", owner)
                .setParameter("servletCode", servletCode)
                .getResultList();
    }    
}
