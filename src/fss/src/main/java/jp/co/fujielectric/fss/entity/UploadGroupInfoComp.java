package jp.co.fujielectric.fss.entity;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import lombok.Data;

@Entity
@Data
//@Table(name="uploadgroupinfocomp")
//public class UploadGroupInfoComp extends UploadGroupInfo implements Serializable {
public class UploadGroupInfoComp implements Serializable {

    private static final long serialVersionUID = 1L;

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
    private boolean mailFlg;
}
