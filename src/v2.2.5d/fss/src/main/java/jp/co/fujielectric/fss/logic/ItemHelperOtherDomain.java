package jp.co.fujielectric.fss.logic;

import java.util.Arrays;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.EntityManagerProducer;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.entity.DomainMaster;
import jp.co.fujielectric.fss.exception.FssException;
import jp.co.fujielectric.fss.service.DomainMasterService;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

/**
 * ItemHelperOtherDomain Logicクラス
 */
@AppTrace
@ApplicationScoped
public class ItemHelperOtherDomain {
    @Inject
    private Logger LOG;

    @Inject
    private DomainMasterService domainMasterService;

    /**
     * 対象ドメインのDBから設定値を取得するConfig項目の対象リスト
     */
    private static final List<String> configKeyListWithDomain = Arrays.asList(
            "mailConvertFlg",
            "mailConvertCharset",
            "mailConvertEncoding",
            "MailSizeLimitSanitized",
            "zipCharsetConvert"
            );
    
    /**
     * DB名（persistance.xmlに登録した名前）を指定してConfigテーブルの登録データを取得
     * @param key Configテーブルのkey
     * @param funcId ConfigテーブルのFuncID
     * @param dbName DB名（persistance.xmlのname）
     * @return Configテーブルから取得した情報。取得できなかった場合は例外発生(NoResultException)。
     */
    @Transactional(Transactional.TxType.NOT_SUPPORTED)  //別団体用のDBにアクセスするため、トランザクションと継承しない
    public Item findConfigFromOhterDB(String key, String funcId, String dbName) {
        EntityManager em = EntityManagerProducer.getEntityManager(dbName);
        if(em == null){
            throw new RuntimeException("他ドメイン用DB名からEntityManagerが取得できませんでした。"
                    + "persistence.xmlに登録されていない可能性があります。(dbName:" + dbName + ")");
        }
        return ItemHelper.findConfig(em, key, funcId);
        //例外処理は呼出し側で実装
    }
    
    /**
     * メールアドレスのドメインからデータベース名を取得する
     * @param mailAddress メールアドレス
     * @return DB名。取得できない場合はnullを返す。
     */
    @Transactional(Transactional.TxType.NOT_SUPPORTED)  //共通DBにアクセスするため、トランザクションを継承しない
    public String GetDatabaseName(String mailAddress)
    {
        try {
            if(StringUtils.isBlank(mailAddress))
                return null;
            String domain = mailAddress.substring(mailAddress.indexOf("@") + 1).toLowerCase();
            //DomainMasterからドメイン名を指定してレコード取得
            DomainMaster rec = domainMasterService.find(domain);
            if(rec == null){
                //指定したドメイン名が登録されていない場合nullを返す
                return null;
            }
            //DB名を返す
            return rec.getDatabase();
        } catch (Exception e) {
            LOG.error("#! GetDatabaseName Error!  mailAddress:{}", mailAddress, e);
            //例外発生で取得できなかった場合、未取得としてnullを返し続行する。
            return null;
        }
    }

    /**
     * 対象ドメインのDBから設定値を取得するConfig項目かどうか
     * @param key
     * @return 
     */
    public static boolean isConfigItemWithDomain(String key){
        return configKeyListWithDomain.stream().anyMatch((_item) -> (key.compareToIgnoreCase(_item) == 0));
    }
}
