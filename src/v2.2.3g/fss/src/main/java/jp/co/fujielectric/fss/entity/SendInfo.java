package jp.co.fujielectric.fss.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import java.io.Serializable;
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
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.xml.bind.annotation.XmlRootElement;
import jp.co.fujielectric.fss.util.DateUtil;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 * 送信情報エンティティ<br>
 * 転送メールからパスワード解除へ送る案件、ファイル送信機能から登録された案件を格納する<br>
 * 一対多： 送信ファイル（添付ファイル）, 受信情報（宛先）<br>
 */
@Entity
@Data
@SuppressWarnings("serial")
@NamedQueries({
    @NamedQuery(name = "SendInfo.findForSendHistory", query = "SELECT DISTINCT a FROM SendInfo a LEFT JOIN FETCH a.sendFiles WHERE a.sendMailAddress = :sendMailAddress and a.historyDisp = true Order by a.sendTime DESC"),
    @NamedQuery(name = "SendInfo.findForSendHistoryOutput", query = "SELECT DISTINCT a FROM SendInfo a LEFT JOIN FETCH a.sendFiles WHERE coalesce(a.sendUserId,'') LIKE :sendUserId and date_trunc('day', a.sendTime) between :dateFrom and :dateTo and a.historyDisp = true Order by a.sendTime DESC"),
    @NamedQuery(name = "SendInfo.findForOriginalSearch", query = "SELECT DISTINCT a FROM SendInfo a LEFT JOIN FETCH a.sendFiles WHERE a.sendMailAddress = :sendMailAddress and a.attachmentMailFlg = true Order by a.sendTime DESC")
})
@XmlRootElement
public class SendInfo implements Serializable {

    public static final int ENVELOPE_FROM_FLAG = 0x0001;    // Envelope-From異常フラグ
    public static final int ENVELOPE_TO_FLAG = 0x0002;      // Envelope-To異常フラグ
    public static final int SENDTRANSFER_FLAG = 0x0004;     // 送信処理非同期処理エラーフラグ
    public static final int SENDTRANSFERAPPROVAL_FLAG = 0x0008;     // 送信処理非同期処理承認依頼エラーフラグ
    /**
     * ID
     */
    @Id
    private String id;

    /**
     * 送信者ID
     */
    @Column
    private String sendUserId;

    /**
     * 送信メールアドレス
     */
    @Column
    private String sendMailAddress;

    /**
     * 送信者名
     */
    @Column
    private String sendUserName;

    /**
     * 送信日時
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date sendTime;

    /**
     * 受信アドレス
     */
    @Column(columnDefinition = "TEXT")
    private String receiveAddresses = "";

    /**
     * 元送信アドレス
     */
    @Column(columnDefinition = "TEXT")
    private String originalSendAddress = "";

    /**
     * 元受信アドレス
     */
    @Column(columnDefinition = "TEXT")
    private String originalReceiveAddresses = "";

    //TODO: ヘッダー情報の保存については要検討
    //      通知メールについては宛先情報から個々に生成し、転送メールについては元のメール情報そのものから生成するなれば、情報として不要？
    /**
     * Message-Id:
     */
    @Column(columnDefinition = "TEXT")
    private String messageId = "";

    /**
     * 宛先アドレス（ヘッダー情報）
     */
    @Column(columnDefinition = "TEXT")
    private String toAddress = "";

    /**
     * CCアドレス（ヘッダー情報）
     */
    @Column(columnDefinition = "TEXT")
    private String ccAddress = "";

    /**
     * Fromアドレス（ヘッダー情報）
     */
    @Column(columnDefinition = "TEXT")
    private String fromAddress;

    /**
     * 件名
     */
    @Column(columnDefinition = "TEXT")
    private String subject;

    /**
     * 本文
     */
    @Column(columnDefinition = "TEXT")
    private String content = "";

    /**
     * 有効期限
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date expirationTime;

    /**
     * パス自動有無
     */
    @Column
    private boolean passAuto;

    /**
     * パス通知有無
     */
    @Column
    private boolean passNotice;

    /**
     * パスワード
     */
    @Column
    private String passWord;

    /**
     * 取り消しフラグ
     */
    @Column
    private boolean cancelFlg;

    /**
     * 履歴表示有無
     */
    @Column
    private boolean historyDisp;

    /**
     * 大容量フラグ
     */
    @Column
    private boolean largeFlg;

    /**
     * メール添付( or ダウンロード )
     */
    @Column
    private boolean attachmentMailFlg;

    /**
     * 承認待ちフラグ
     */
    @Column
    private boolean approvalFlg;

    /**
     * 承認全必要フラグ
     */
    @Column
    private boolean approvalsRequiredAllFlg;

    /**
     * 承認必要回数
     */
    @Column
    private int approvalsRequiredCount;

    /**
     * 承認済み回数
     */
    @Column
    private int approvalsDoneCount;

    /**
     * 承認文章
     */
    @Column(columnDefinition = "TEXT")
    private String approvalsComment;

    /**
     * 通知省略フラグ
     */
    @Column
    private boolean noticeOmitFlg;

    /**
     * サーバ設置場所 (ture: LGWAN側, false: Internet側)
     */
    @Column
    private boolean sectionLgwan;

    /**
     * ふるまい検知フラグ
     */
    @Column
    private boolean sendFileCheckFlg;

    /**
     * メール解析異常フラグ
     */
    @Column
    private String warningFlg = "0";

    //[v2.2.1]
    /**
     * 処理日付(YYYYMMDD)
     * 日付サブフォルダに使用
     */
    @Column(length = 16)
    private String procDate;
    
    //[v2.2.3]
    /**
     * エラー
     * ０：正常（エラー無し）
     * １：メール解析失敗による異常
     */
    @Column()
    private int errInfo;

    //[v2.2.1]
    /**
     * エラー詳細
     * １：「メール読込み時異常発生」
     * ２：「エンベローブFrom解析異常」
     * ３：「エンベローブTo解析異常」
     * ４：「日付ヘッダ解析異常」
     * ５：「件名ヘッダ解析異常」
     * ６：「メールヘッダ解析異常」
     * ７：「メール本文解析異常」
     * ８：「添付ファイル解析異常」
     */
    @Column
    private int errDetails;

    //[v2.2.3]
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
    
    /**
     * 送信ファイル（添付ファイル）
     */
    @OneToMany(mappedBy = "sendInfo", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    //@OneToMany(mappedBy = "sendInfo", cascade = CascadeType.ALL)
    @Setter(AccessLevel.NONE)
    @JsonManagedReference
    private List<SendFile> sendFiles = new ArrayList<>();
    
    /**
     * WarningFlgをintで取得
     * @return 
     */
    private int convWarningFlgInt()
    {
        try{
            return Integer.parseInt(warningFlg);
        }catch(NumberFormatException e){
            return 0;
        }
    }
    
    /**
     * WarningFlgのフラグ更新
     * @param flg 
     */
    public void updateWarningFlg(int flg){
        int iFlg = convWarningFlgInt();
        warningFlg = Integer.toString(iFlg | flg );
    }
    
    /**
     * WarningFlgに指定のフラグが立っているか判定
     * @param flg
     * @return 
     */
    public boolean hasWarningFlg(int flg){
        int iFlg = convWarningFlgInt();
        return ((iFlg & flg) == flg);
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
