package jp.co.fujielectric.fss.entity;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Data;

/**
 * ふるまい検知ファイルエンティティ<br>
 * 受信情報エンティティに紐付く添付ファイルの情報を格納する<br>
 * 多対一： 受信情報<br>
 */
@Entity
@Data
@SuppressWarnings("serial")
@NamedQueries({
    @NamedQuery(name = "CheckedFile.findReceiveInfoId", query = "SELECT a FROM CheckedFile a WHERE a.receiveInfoId = :receiveInfoId")
})
@XmlRootElement
public class CheckedFile implements Serializable {
    /**
     * ID
     */
    @Id
    private String id;

    /** 受信情報ID */
    @Column
    private String receiveInfoId;

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
     * ふるまい検知済フラグ
     */
    @Column
    private boolean checkedFlg;

    /**
     * ファイルメッセージ
     */
    @Column
    private String fileMessage;
}
