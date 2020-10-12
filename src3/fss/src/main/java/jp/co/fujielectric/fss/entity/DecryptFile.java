package jp.co.fujielectric.fss.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import jp.co.fujielectric.fss.util.DateUtil;
import lombok.Data;

/**
 * パスワード解除ファイルエンティティ<br>
 * 受信情報エンティティに紐付くパスワード解除ファイルの情報を格納する<br>
 * 多対一： 受信情報
 */
@Entity
@Data
@SuppressWarnings("serial")
@NamedQueries({
    @NamedQuery(name = "DecryptFile.findReceiveInfoId", query = "SELECT a FROM DecryptFile a WHERE a.receiveInfoId = :receiveInfoId")
})
@XmlRootElement
public class DecryptFile implements Serializable {
    /** ID */
    @Id
    private String id;

    /** 親ID */
    @Column
    private String parentId;

    /** 受信情報ID */
    @Column
    private String receiveInfoId;

    /** 受信ファイルID */
    @Column
    private String receiveFileId;

    /** ファイル名 */
    @Column
    private String fileName;

    /** ファイルパス（実体名） */
    @Column
    private String filePath;

    /** ファイル形式 */
    @Column
    private String fileFormat;

    /** ファイルサイズ */
    @Column
    private long fileSize;

    /** 無害化対象フラグ */
    @Column
    private boolean targetFlg;

    /** パスワード有無フラグ */
    @Column
    private boolean passwordFlg;

    /** ファイルパスワード */
    @Column
    private String filePassword;

    /** パスワード解除フラグ */
    @Column
    private boolean decryptFlg;

    /** パスワード再付与フラグ */
    @Column
    private boolean encryptFlg;

    /** パスワード再付与済みフラグ */
    @Column
    private boolean encryptedFlg;

    //[v2.2.3]
    /** 登録日時 */
    @Temporal(TemporalType.TIMESTAMP)
    private Date insertDate;

    //[v2.2.3]
    /** 更新日時 */
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
