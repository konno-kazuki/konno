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
import jp.co.fujielectric.fss.entity.DestAddressBook;

/**
 * アドレス帳サービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class DestAddressBookService {

    @Inject
    private EntityManager em;

    @Transactional
    public void create(DestAddressBook entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(DestAddressBook entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(String id) {
        DestAddressBook entity = em.find(DestAddressBook.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    /**
     * 「ユーザID」からアドレス帳情報を取得
     * 
     * @param uid ユーザID
     * 
     * @return アドレス帳情報
     */
    public List<DestAddressBook> findDestAddressBookByUid(String uid) {
        String queryName = "DestAddressBook.findByUid";
        Query query = em.createNamedQuery(queryName, DestAddressBook.class);
        query.setParameter("uid", uid);

        List<DestAddressBook> destAddressBookDatas = query.getResultList();

        return destAddressBookDatas;
    }
    
    /**
     * 「ユーザID」と「メールアドレス欄に入力された文字列」からアドレス帳情報を取得
     *
     * @param uid ユーザID
     * @param toAddress メールアドレス欄に入力された文字列
     *
     * @return アドレス帳情報
     */
    public List<DestAddressBook> findDestAddressBookByToAddressOnly(String uid, String toAddress) {
        String queryName = "DestAddressBook.findByToAddressOnly";
        Query query = em.createNamedQuery(queryName, DestAddressBook.class);
        query.setParameter("uid", uid);
        query.setParameter("toAddress", "%" + toAddress + "%");

        List<DestAddressBook> destAddressBookDatas = query.getResultList();

        return destAddressBookDatas;
    }
    
    /**
     * 「ユーザID」と「宛先メールアドレス」と「送信先名称欄に入力された文字列」からアドレス帳情報を取得
     * 
     * @param uid ユーザID
     * @param toAddress 宛先メールアドレス
     * @param personal 送信先名称欄に入力された文字列
     * 
     * @return アドレス帳情報 
     */
    public List<DestAddressBook> findDestAddressBookByPersonal(String uid, String toAddress, String personal) {
        String queryName = "DestAddressBook.findByPersonal";
        Query query = em.createNamedQuery(queryName, DestAddressBook.class);
        query.setParameter("uid", uid);
        query.setParameter("toAddress", toAddress);
        query.setParameter("personal", "%" + personal + "%");

        List<DestAddressBook> destAddressBookDatas = query.getResultList();

        return destAddressBookDatas;
    }
    
    /**
     * 「ユーザID」と「送信先名称欄に入力された文字列」からアドレス帳情報を取得
     *
     * @param uid ユーザID
     * @param personal 送信先名称欄に入力された文字列
     *
     * @return アドレス帳情報
     */
    public List<DestAddressBook> findDestAddressBookByPersonalOnly(String uid, String personal) {
        String queryName = "DestAddressBook.findByPersonalOnly";
        Query query = em.createNamedQuery(queryName, DestAddressBook.class);
        query.setParameter("uid", uid);
        query.setParameter("personal", "%" + personal + "%");

        List<DestAddressBook> destAddressBookDatas = query.getResultList();

        return destAddressBookDatas;
    }
    
    /**
     * 「ユーザID」と「宛先名称」と「メールアドレス欄に入力された文字列」からアドレス帳情報を取得
     *
     * @param uid ユーザID
     * @param personal 宛先名称
     * @param toAddress メールアドレス欄に入力された文字列
     *
     * @return アドレス帳情報
     */
    public List<DestAddressBook> findDestAddressBookByToAddress(String uid, String personal, String toAddress) {
        String queryName = "DestAddressBook.findByToAddress";
        Query query = em.createNamedQuery(queryName, DestAddressBook.class);
        query.setParameter("uid", uid);
        query.setParameter("personal", personal);
        query.setParameter("toAddress", "%" + toAddress + "%");

        List<DestAddressBook> destAddressBookDatas = query.getResultList();

        return destAddressBookDatas;
    }
}
