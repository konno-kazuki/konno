package jp.co.fujielectric.fss.service;

import java.util.Date;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendInfo;
import org.apache.logging.log4j.Logger;

/**
 * 送信情報サービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class SendInfoService {

    @Inject
    protected Logger LOG;
    
    @Inject
    private EntityManager em;

    @Inject
    private ReceiveInfoService receiveInfoService;
    
    /**
     * レコード追加（永続化）
     * ※対象Entityおよび追加対象のSendFileに対し、事前にResetDate()で作成日付・更新日付をセットすることを忘れずに
     * @param entity 
     */
    @Transactional
    public void create(SendInfo entity) {
        //既存レコードを取得
        SendInfo entityOld = find(entity.getId());
        if(entityOld != null){
            //既存レコードがある場合・・・
            LOG.debug("# DB create SendInfo. remove existing records. SendInfoID:{}", entity.getId());            
            //関連テーブル(ReceiveInfo,ReceiveFile)のレコードを削除する
            try {
                //関連テーブル(ReceiveInfo,ReceiveFile)のレコードも含めて削除する
                List<ReceiveInfo> rcvInfoLst = receiveInfoService.findForSendInfoId(entity.getId());
                for(ReceiveInfo ri : rcvInfoLst){
                    em.remove(ri);
                }                
            } catch (Exception e) {
                //関連レコードの削除で例外発生した場合は無視する。
                LOG.warn("#! DB SendInfo remove existing receiveInfo record error.", e);
            }            
            //既存レコード(SendInfo,SendFile)を削除する。
            try {
                em.remove(entityOld);
            } catch (Exception e) {
                //既存レコードの削除で例外発生した場合は無視する。
                LOG.warn("#! DB SendInfo remove existing record error.", e);
            }
        }
        //新規レコード追加
        em.persist(entity);
    }

    /**
     * レコード更新（永続化）
     * ※対象Entityおよび更新対象のSendFileに対し、事前にResetDate()で更新日付をセットすることを忘れずに
     * @param entity 
     */
    @Transactional
    public void edit(SendInfo entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(String id) {
        SendInfo entity = em.find(SendInfo.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    public SendInfo find(String id) {
        return em.find(SendInfo.class, id);
    }
    
    public SendInfo findLocking(String id) {
        return em.find(SendInfo.class, id, LockModeType.PESSIMISTIC_READ);
    }

    /**
     * 送信履歴一覧取得
     *
     * @param sendUserId 送信者ID
     * @param sendMailAddress メールアドレス
     * @param dateFrom 検索日付条件From
     * @param dateTo 検索日付条件To
     * @param limit 検索最大件数
     *
     * @return 送信履歴一覧
     */
    public List<SendInfo> findForSendHistory(
            String sendUserId, 
            String sendMailAddress,
            Date dateFrom, 
            Date dateTo,
            int limit) 
    {
        String queryName = SendInfo.NAMEDQUEUE_FIND_SENDHISTORY;
        TypedQuery<SendInfo> query = em.createNamedQuery(queryName, SendInfo.class);
        query.setParameter("sendMailAddress", sendMailAddress);
        query.setParameter("dateFrom", dateFrom);
        query.setParameter("dateTo", dateTo);
        query.setFirstResult(0);
        query.setMaxResults(limit);

        return query.getResultList();
    }    
    
    /**
     * 原本検索一覧取得
     *
     * @param sendMailAddress メールアドレス
     *
     * @return 原本検索一覧
     */
    public List<SendInfo> findForOriginalSearch(String sendMailAddress) {

        String queryName = "SendInfo.findForOriginalSearch";
        Query query = em.createNamedQuery(queryName, SendInfo.class);
        query.setParameter("sendMailAddress", sendMailAddress);

        List<SendInfo> sendInfoDatas = query.getResultList();

        return sendInfoDatas;
    }

    /**
     * 送信履歴出力情報取得
     *
     * @param sendUserId (検索)送信者ID
     * @param dateFrom (検索)期間-開始
     * @param dateTo (検索)期間-終了
     *
     * @return 送信履歴出力情報
     */
    public List<SendInfo> findForSendHistoryOutput(String sendUserId, Date dateFrom, Date dateTo) {

        String queryName = "SendInfo.findForSendHistoryOutput";
        Query query = em.createNamedQuery(queryName, SendInfo.class);
        query.setParameter("sendUserId", "%" + sendUserId + "%" ); ///ユーザＩＤは部分一致検索
        query.setParameter("dateFrom", dateFrom);
        query.setParameter("dateTo", dateTo);

        List<SendInfo> sendInfoDatas = query.getResultList();

        return sendInfoDatas;
    }
    
    /**
     * 無害化処理中ファイル送信 一覧取得 （連続ファイル送信制限用）
     * 指定したメールアドレスの指定時間内に送信登録された無害化中のファイル送信を検索
     * @param sendMailAddress メールアドレス
     * @param dateFrom 検索日付条件From
     * @param dateTo 検索日付条件To
     *
     * @return 送信履歴一覧
     */
    public List<SendInfo> findSendFileProcessing(
            String sendMailAddress,
            Date dateFrom, 
            Date dateTo) 
    {
        return em.createNamedQuery(SendInfo.NAMEDQUEUE_FIND_SENDFILE_PROCESSING, SendInfo.class)
                .setParameter("sendMailAddress", sendMailAddress)
                .setParameter("dateFrom", dateFrom)
                .setParameter("dateTo", dateTo)
                .getResultList();
    }      
}
