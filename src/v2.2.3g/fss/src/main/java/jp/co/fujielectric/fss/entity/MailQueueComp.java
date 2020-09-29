package jp.co.fujielectric.fss.entity;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Data;

@XmlRootElement
@Entity
@Data
@NamedQueries({
    //指定オーナーのみ取得(cancelFlg=trueも含む）
    @NamedQuery(name = "MailQueueComp.findAllByOwner", query = "from MailQueueComp where owner = :owner order by registTime"),})
public class MailQueueComp implements Serializable {

    /**
     * メール処理単位の固有ID（メールID）
     */
    @Id
    private String id;

    /**
     * 登録日時
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date registTime;

    /**
     * 処理担当ノード
     */
    @Column
    private String owner;

    /**
     * リージョンID
     */
    @Column
    private String regionId;

    /**
     * サーブレットコード
     */
    @Column
    private String servletCode;

    /**
     * リトライカウント
     */
    @Column
    private int retryCount;

    /**
     * キャンセルフラグ（リトライオーバー等で使用）
     */
    @Column
    private boolean cancelFlg;

    //[v2.2.1]
    /**
     * メール処理日付(YYYYMMDD)
     * 日付サブフォルダに使用
     */
    @Column(length = 16)
    private String mailDate;
    
    /**
     * 更新日時
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;

}
