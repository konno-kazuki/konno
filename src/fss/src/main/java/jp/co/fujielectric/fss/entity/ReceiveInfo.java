package jp.co.fujielectric.fss.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlRootElement;
import jp.co.fujielectric.fss.util.DateUtil;
import lombok.Data;

/**
 * 受信情報エンティティ<br>
 * 転送メールからパスワード解除を行った案件、ファイル送信機能から受信した案件を格納する<br>
 * 本テーブル自体はファイル送信時に自動的に作成される<br>
 * 一対多： 受信ファイル（添付ファイル）<br>
 * 多対一： 送信情報（送信元）<br>
 *
 * @author nakai
 */
@Entity
@Data
@SuppressWarnings("serial")
@NamedQueries({
    @NamedQuery(name = "ReceiveInfo.findForSendInfoId", query = "SELECT DISTINCT a FROM ReceiveInfo a LEFT JOIN FETCH a.receiveFiles WHERE a.sendInfoId = :sendInfoId order by a.id ASC"),
    @NamedQuery(name = ReceiveInfo.NAMED_QUEUE_FIND_RECEIVEHISTORY, 
            query = "SELECT DISTINCT a FROM ReceiveInfo a LEFT JOIN FETCH a.receiveFiles WHERE a.receiveMailAddress = :receiveMailAddress AND a.historyDisp = true AND a.insertDate BETWEEN :dateFrom AND :dateTo ORDER BY a.insertDate DESC"),
    @NamedQuery(name = "ReceiveInfo.findForReceiveHistoryOutput", query = "SELECT DISTINCT a FROM ReceiveInfo a LEFT JOIN FETCH a.receiveFiles WHERE coalesce(a.receiveUserId,'') LIKE :receiveUserId and date_trunc('day', a.sendTime) between :dateFrom and :dateTo and a.historyDisp = true Order by a.sendTime DESC"),
//    @NamedQuery(name = "ReceiveInfo.findForOriginalHistory", query = "SELECT DISTINCT a FROM ReceiveInfo a LEFT JOIN FETCH a.receiveFiles WHERE a.receiveMailAddress = :receiveMailAddress and a.attachmentMailFlg = true Order by a.sendTime DESC"),
    @NamedQuery(name = ReceiveInfo.NAMED_QUEUE_FIND_ORIGINALHISTORY,
            query = "SELECT DISTINCT a FROM ReceiveInfo a LEFT JOIN FETCH a.receiveFiles WHERE a.receiveMailAddress = :receiveMailAddress AND a.attachmentMailFlg = true AND a.insertDate BETWEEN :dateFrom AND :dateTo ORDER BY a.insertDate DESC"),
    @NamedQuery(name = "ReceiveInfo.findForOriginalSearch", query = "SELECT DISTINCT a FROM ReceiveInfo a LEFT JOIN FETCH a.receiveFiles WHERE a.receiveMailAddress = :receiveMailAddress and a.attachmentMailFlg = true Order by a.sendTime DESC")
})
@XmlRootElement
public class ReceiveInfo implements Serializable {

    public static final String NAMED_QUEUE_FIND_RECEIVEHISTORY = "ReceiveInfo.findForReceiveHistory";
    public static final String NAMED_QUEUE_FIND_ORIGINALHISTORY = "ReceiveInfo.findForOriginalHistory";
    
    /**
     * ID
     */
    @Id
    private String id;

    /**
     * 送信情報ID（送信元）
     */
    @Column
    private String sendInfoId;

    /**
     * 受信者ID
     */
    @Column
    private String receiveUserId;

    /**
     * 受信メールアドレス
     */
    @Column(columnDefinition = "TEXT")
    private String receiveMailAddress = "";

    /**
     * 受信者名
     */
    @Column
    private String receiveUserName;

    /**
     * 送信日時
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date sendTime;

    /**
     * 元受信アドレス
     */
    @Column(columnDefinition = "TEXT")
    private String originalReceiveAddress = "";

    /**
     * 履歴表示有無
     */
    @Column
    private boolean historyDisp;

    /**
     * メール添付( or ダウンロード )
     */
    @Column
    private boolean attachmentMailFlg;

    /**
     * メール本体無害化フラグ
     */
    @Column
    private boolean mailSanitizeFlg;

    /**
     * メール本体無害化済みフラグ
     */
    @Column
    private boolean mailSanitizedFlg;

    /**
     * メール本体無害化メッセージ
     */
    @Column
    private String mailSanitizeMessage;

    /**
     * パスワード解除待ちフラグ
     */
    @Column
    private boolean passwordUnlockWaitFlg;

    //[v2.2.1]
    /**
     * SandBlast対応区分
     * 0：SandBlast 不使用。　（例：その他団体）	
     * 1：SandBlast 使用。　VotiroアップロードにSandBlast無害化ファイルを使用しない。　（例：静岡）	
     * 2：SandBlast 使用。　VotiroアップロードにSandBlast無害化ファイルを使用する。　　（例：京都）	
     */
    @Column
    private int sandBlastKbn;

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

    //[v2.2.3]
    /**
     * 完了日時
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date compDate;

    /**
     * 受信ファイル
     */
    @OneToMany(mappedBy = "receiveInfo", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderBy("fileName DESC")
    @JsonManagedReference
    private List<ReceiveFile> receiveFiles = new ArrayList<>();

    /**
     * 送信情報
     */
    @Transient    
    private SendInfo sendInfo = null;

    /**
     * ふるまい検知ファイル
     */
    @Transient
    private List<CheckedFile> checkedFiles = new ArrayList<>();

    /**
     * 無害化フラグ
     *
     * @return 無害化フラグを返す
     */
    @JsonIgnore
    public boolean getSanitizeFlg() {
        if (receiveFiles == null) {
            return false;
        }
        for (ReceiveFile receiveFile : receiveFiles) {
            if (!receiveFile.isSanitizeFlg()) {
                return false;
            }
        }
        return true;
    }

    /**
     * メールOpen日時
     *
     * @return 最新の受信日時を返す
     */
    @JsonIgnore
    public Date getMailOpenTime() {
        Date mailOpenTime = null;
        if (receiveFiles == null) {
            return null;
        }
        for (ReceiveFile receiveFile : receiveFiles) {
            Date dt = receiveFile.getReceiveTime();
            if (dt == null) {
                continue;
            }
            if (mailOpenTime == null) {
                mailOpenTime = dt;
            }
            if (dt.after(mailOpenTime)) {
                mailOpenTime = dt;
            }
        }
        return mailOpenTime;
    }

    /**
     * メールOpen日時
     *
     * @return 最新の受信日時を返す
     */
    @JsonIgnore
    public String getMailOpenTimeText() {
        Date dt = getMailOpenTime();
        if (dt == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy/MM/dd[HH:mm:ss]").format(dt);
    }
    
    /**
     * 作成日付、更新日付に現在日時をセットする
     */
    @JsonIgnore
    public void resetDate(){
        //更新日付に現在日時をセットする
        updateDate = DateUtil.getSysDateExcludeMillis();   //更新日付
        if(insertDate == null)
            insertDate = updateDate;   //作成日付
    }
 }
