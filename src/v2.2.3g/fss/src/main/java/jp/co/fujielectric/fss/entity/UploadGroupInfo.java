package jp.co.fujielectric.fss.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Data;

@XmlRootElement
@Entity
@Data
@NamedQueries({
    @NamedQuery(name = "findGroup", query = "from UploadGroupInfo a where a.id = :id"),
    @NamedQuery(name = "findGroupByOwner", query = "from UploadGroupInfo a where a.owner = :owner and a.cancelFlg = false"),
    @NamedQuery(name = "findAliveGroups", query = "from UploadGroupInfo a where (a.fileCount - a.finCount) > 0 and a.owner = :owner and a.cancelFlg = false"),
    @NamedQuery(name = UploadGroupInfo.NAMED_QUEUE_FIND_SANITIZEDALL, 
            query = "from UploadGroupInfo a where a.finCount >= a.fileCount and a.owner = :owner and a.cancelFlg = false"),
})
public class UploadGroupInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 全ファイル無害化済み検索用NamedQueue
     */
    public static final String NAMED_QUEUE_FIND_SANITIZEDALL = "findAllSanitizedGroups";
    
    /**
     * アップロード処理単位の固有ID
     */
    @Id
    private String id;

    /**
     * 主ID(receiveInfo.id)
     */
    @Column
    private String mainId;

    /**
     * 補助ID(sendinfo.id)
     */
    @Column
    private String subId;

    /**
     * 総ファイル数
     */
    @Column
    private int fileCount;

    /**
     * 処理済みファイル数
     */
    @Column
    private int finCount;

    /**
     * 処理担当ノード
     */
    @Column
    private String owner;

    /**
     * 問合せ先DBを特定するリージョンID
     */
    @Column
    private String regionId;

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
     * 送信情報ID
     * Votiro負荷軽減で同一SendInfoからのファイルかどうかの判定に利用
     */
    @Column
    private String sendInfoId;

    //[v2.2.1]
    /**
     * 処理日付(YYYYMMDD)
     * 日付サブフォルダに使用
     */
    @Column(length = 16)
    private String procDate;
    
    //[v2.2.1]
    /**
     * 登録日時
     */    
    @Temporal(TemporalType.TIMESTAMP)
    private Date insertDate;

    //[v2.2.1]
    /**
     * 更新日時
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateDate;

    //[v2.2.1]
    /**
     * メール無害化フラグ
     */
    @Column
    private boolean mailFlg = false;
    
    @Transient
    List<UploadFileInfo> uploadFileInfos = new ArrayList<>();
}
