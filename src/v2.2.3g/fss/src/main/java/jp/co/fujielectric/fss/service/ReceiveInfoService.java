package jp.co.fujielectric.fss.service;

import java.util.Date;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.mail.internet.InternetAddress;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.data.CommonEnum.SandBlastKbn;
import jp.co.fujielectric.fss.entity.DecryptFile;
import jp.co.fujielectric.fss.entity.ReceiveFile;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.IdUtil;

/**
 * 受信情報サービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class ReceiveInfoService {

    @Inject
    private EntityManager em;

    @Inject
    private SendInfoService sendInfoService;

    @Inject
    private DecryptFileService decryptFileService;

    /**
     * DB更新
     */
    @Transactional
    public void flush(){
        em.flush();
    }

    /**
     * DB更新対象解除(Detach)
     */
    @Transactional
    public void clear(){
        em.clear();
    }

    /**
     * レコード追加（永続化）
     * ※対象Entityおよび追加対象のReceiveFileに対し、事前にResetDate()で作成日付・更新日付をセットすることを忘れずに
     * @param entity 
     */
    @Transactional
    public void create(ReceiveInfo entity) {
        em.persist(entity);
    }

    /**
     * レコード更新（永続化）
     * ※対象Entityおよび更新対象のReceiveFileに対し、事前にResetDate()で更新日付をセットすることを忘れずに
     * @param entity 
     */
    @Transactional
    public void edit(ReceiveInfo entity) {
        em.merge(entity);
    }
    
    @Transactional
    public void remove(String id) {
        ReceiveInfo entity = em.find(ReceiveInfo.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    public ReceiveInfo find(String id) {
        return em.find(ReceiveInfo.class, id);
    }

    /**
     * 関連情報を含む受信情報を取得
     *
     * @param id 受信情報ID
     *
     * @return 受信情報
     */
    public ReceiveInfo findWithRelationTables(String id) {
        ReceiveInfo receiveInfo = find(id);

        // 受信情報の関連情報を取得
        setRelationTables(receiveInfo);

        return receiveInfo;
    }

    /**
     * 受信情報の関連情報を取得
     *
     * @param receiveInfo 受信情報
     */
    public void setRelationTables(ReceiveInfo receiveInfo) {
        // 送信情報の取得
        receiveInfo.setSendInfo(sendInfoService.find(receiveInfo.getSendInfoId()));

        // パスワード解除ファイル情報の取得
        List<DecryptFile> decryptFiles = decryptFileService.findReceiveInfoId(receiveInfo.getId());

        // 受信ファイルごとにパスワード解除ファイル情報を関連付け
        if (receiveInfo.getReceiveFiles() != null && decryptFiles != null
                && !receiveInfo.getReceiveFiles().isEmpty() && !decryptFiles.isEmpty()) {
            for (ReceiveFile receiveFile : receiveInfo.getReceiveFiles()) {
                for (DecryptFile decryptFile : decryptFiles) {
                    if (receiveFile.getId().equals(decryptFile.getReceiveFileId())) {
                        receiveFile.getDecryptFiles().add(decryptFile);
                    }
                }
            }
        }
    }

    /**
     * 送信情報IDから受信情報リストを取得
     *
     * @param sendInfoId 送信情報ID
     *
     * @return 受信情報リスト
     */
    public List<ReceiveInfo> findForSendInfoId(String sendInfoId) {

        String queryName = "ReceiveInfo.findForSendInfoId";
        TypedQuery<ReceiveInfo> query = em.createNamedQuery(queryName, ReceiveInfo.class);
        query.setParameter("sendInfoId", sendInfoId);

        List<ReceiveInfo> receiveInfoDatas = query.getResultList();

        return receiveInfoDatas;
    }

    /**
     * 受信履歴一覧取得
     *
     * @param receiveUserId 受信者ID
     * @param receiveMailAddress メールアドレス
     *
     * @return 受信履歴一覧
     */
    public List<ReceiveInfo> findForReceiveHistory(String receiveUserId, String receiveMailAddress) {

        String queryName = "ReceiveInfo.findForReceiveHistory";
        TypedQuery<ReceiveInfo> query = em.createNamedQuery(queryName, ReceiveInfo.class);
        query.setParameter("receiveMailAddress", receiveMailAddress);

        List<ReceiveInfo> receiveInfoDatas = query.getResultList();

        return receiveInfoDatas;
    }

    /**
     * 受信履歴出力情報取得
     *
     * @param receiveUserId (検索)受信者ID
     * @param dateFrom (検索)期間-開始
     * @param dateTo (検索)期間-終了
     *
     * @return 受信履歴一覧
     */
    public List<ReceiveInfo> findForReceiveHistoryOutput(String receiveUserId, Date dateFrom, Date dateTo) {

        String queryName = "ReceiveInfo.findForReceiveHistoryOutput";
        TypedQuery<ReceiveInfo> query = em.createNamedQuery(queryName, ReceiveInfo.class);
        query.setParameter("receiveUserId", "%" + receiveUserId + "%" ); ///ユーザＩＤは部分一致検索
        query.setParameter("dateFrom", dateFrom);
        query.setParameter("dateTo", dateTo);

        List<ReceiveInfo> receiveInfoDatas = query.getResultList();

        // 送信情報の取得
        for (ReceiveInfo receiveInfo : receiveInfoDatas) {
            receiveInfo.setSendInfo(sendInfoService.find(receiveInfo.getSendInfoId()));
        }

        return receiveInfoDatas;
    }

    /**
     * 原本履歴一覧取得
     *
     * @param receiveUserId 受信者ID
     * @param receiveMailAddress メールアドレス
     *
     * @return 原本履歴一覧
     */
    public List<ReceiveInfo> findForOriginalHistory(String receiveUserId, String receiveMailAddress) {

        String queryName = "ReceiveInfo.findForOriginalHistory";
        TypedQuery<ReceiveInfo> query = em.createNamedQuery(queryName, ReceiveInfo.class);
        query.setParameter("receiveMailAddress", receiveMailAddress);
        query.setParameter("sysdate", new Date());  //SendInfoのExpirationTimeが現在日付以降のものだけ取得[v2.2.3b]

        List<ReceiveInfo> receiveInfoDatas = query.getResultList();

        return receiveInfoDatas;
    }

    /**
     * 原本検索一覧取得
     *
     * @param receiveMailAddress 検索メールアドレス
     *
     * @return 原本検索一覧
     */
    public List<ReceiveInfo> findForOriginalSearch(String receiveMailAddress) {

        String queryName = "ReceiveInfo.findForOriginalSearch";
        TypedQuery<ReceiveInfo> query = em.createNamedQuery(queryName, ReceiveInfo.class);
        query.setParameter("receiveMailAddress", receiveMailAddress);

        List<ReceiveInfo> receiveInfoDatas = query.getResultList();

        return receiveInfoDatas;
    }
    
    /**
     * ReceiveInfoの生成
     * @param sendInfo
     * @param receiveAddress
     * @param mailSanitizeFlg
     * @param passwordFlg   パスワード解除対象か
     * @param sandBlstKbn   SandBlast対応区分
     * @return 
     */
    public ReceiveInfo createReceiveInfo(SendInfo sendInfo, 
            InternetAddress receiveAddress, 
            boolean mailSanitizeFlg,
            boolean passwordFlg,
            SandBlastKbn sandBlstKbn){
        ReceiveInfo receiveInfo = new ReceiveInfo();                            // 受信情報の作成
        receiveInfo.setId(IdUtil.createUUID());
        receiveInfo.setSendInfo(sendInfo);
        receiveInfo.setSendInfoId(sendInfo.getId());
        receiveInfo.setReceiveUserId("");                                       // 受信者IDは空白
        receiveInfo.setReceiveMailAddress(receiveAddress.getAddress());
        receiveInfo.setReceiveUserName(receiveAddress.getPersonal());
        receiveInfo.setSendTime(sendInfo.getSendTime());
        receiveInfo.setAttachmentMailFlg(sendInfo.isAttachmentMailFlg());
        receiveInfo.setPasswordUnlockWaitFlg(passwordFlg);
        receiveInfo.resetDate();   //更新日付,作成日付
                
        if(sendInfo.isAttachmentMailFlg()){
            //メール無害化
            receiveInfo.setOriginalReceiveAddress(sendInfo.getOriginalReceiveAddresses());  // [2017/06/21] 元受信アドレスに複数宛先が入るケースはない            
            receiveInfo.setHistoryDisp(sendInfo.isHistoryDisp());
            receiveInfo.setMailSanitizeFlg(mailSanitizeFlg);        // メール本体無害化フラグ設定
            receiveInfo.setMailSanitizedFlg(false);
        }else{
            //ファイル交換
            receiveInfo.setHistoryDisp(true);
        }
        receiveInfo.setSandBlastKbn(sandBlstKbn.value);     //SandBlast対応区分
        
        return receiveInfo;
    }    
}
