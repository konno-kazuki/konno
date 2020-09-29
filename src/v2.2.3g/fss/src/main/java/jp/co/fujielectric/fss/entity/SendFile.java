package jp.co.fujielectric.fss.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.xml.bind.annotation.XmlRootElement;
import jp.co.fujielectric.fss.util.DateUtil;
import lombok.Data;

/**
 * 送信ファイルエンティティ<br>
 * 送信情報エンティティに紐付く送信ファイル、添付ファイルの情報を格納する<br>
 * 多対一： 送信情報<br>
 */
@Entity
@Data
@SuppressWarnings("serial")
@XmlRootElement
public class SendFile implements Serializable {

    /**
     * ID
     */
    @Id
    private String id;

    /**
     * 送信情報
     */
    @ManyToOne
    @JoinColumn(name = "sendinfo_id")
    @JsonBackReference
    private SendInfo sendInfo;

    /**
     * ファイル名
     */
    @Column
    private String fileName;

    /**
     * ファイルパス（実体名）
     */
    @Column(columnDefinition = "TEXT")
    private String filePath = "";

    /**
     * ファイル形式
     */
    @Column
    private String fileFormat;

    /**
     * ファイルサイズ
     */
    @Column
    private long fileSize;

    /**
     * ファイルパスワード
     */
    @Column
    private String filePass;

    //[v2.2.3]
    /**
     * 登録日時
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date insertDate;

    //[v2.2.3]
    /**
     * 更新日時
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateDate;

    // ファイルパスを返す
    @Override
    public String toString() {
        return filePath;
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
