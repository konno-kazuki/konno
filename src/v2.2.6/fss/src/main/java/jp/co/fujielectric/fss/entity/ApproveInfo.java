package jp.co.fujielectric.fss.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.text.SimpleDateFormat;
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

/**
 * 承認情報エンティティ<br>
 * ファイルの送信時などに承認者が設定された場合の、承認用情報を格納する。<br>
 * 本情報は承認対象となる情報のIDに紐付く。<br>
 * 多対一：承認対象情報<br>
 */
@Entity
@Data
@XmlRootElement
@SuppressWarnings("serial")
@NamedQueries({
    @NamedQuery(name = "ApproveInfo.findForApproveHistory", query = "SELECT a FROM ApproveInfo a WHERE a.approveMailAddress = :approveMailAddress Order by a.requestTime DESC"),
    @NamedQuery(name = "ApproveInfo.findForSendInfoId", query = "SELECT DISTINCT a FROM ApproveInfo a WHERE a.approveId = :approveId Order by a.id ASC"),
    @NamedQuery(name = "ApproveInfo.findForGrowl", query = "SELECT DISTINCT a FROM ApproveInfo a WHERE a.approveId = :approveId AND a.approvedComment IS NOT NULL AND a.approvedComment <> '' Order by a.approvedTime ASC")
})
public class ApproveInfo implements Serializable {
    /**
     * ID
     */
    @Id
    private String id;

    /**
     * 承認対象ID（送信情報IDや受信情報ID）
     */
    @Column
    private String approveId;

    /**
     * 承認メールアドレス
     */
    @Column(columnDefinition = "TEXT")
    private String approveMailAddress = "";

    /**
     * 承認者名
     */
    @Column
    private String approveUserName;

    /**
     * 承認文章
     */
    @Column(columnDefinition = "TEXT")
    private String approvedComment;

    /**
     * 承認フラグ<br>
     * 正数：承認<br>
     * 負数：却下<br>
     */
    @Column
    private int approvedFlg;

    /**
     * 承認日時
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date approvedTime;

    /**
     * 依頼日時
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date requestTime;
    
    /**
     * 承認日時テキスト
     *
     * @return 承認日時テキスト
     */
    @JsonIgnore
    public String getApprovedTimeText() {
        if (approvedTime == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy/MM/dd[HH:mm:ss]").format(approvedTime);
    }
    
    /**
     * 承認フラグテキスト
     *
     * @return 承認フラグテキスト
     */
    @JsonIgnore
    public String getApprovedFlgText() {
        if (approvedFlg > 0) {
            return "○";
        } else if (approvedFlg < 0) {
            return "×";
        }
        return "";
    }
}
