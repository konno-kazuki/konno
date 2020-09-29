package jp.co.fujielectric.fss.entity;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import lombok.Data;

/**
 * 原本ファイルログエンティティ<br>
 * 送信ファイルエンティティにダウンロード履歴情報を格納する<br>
 * 一対多： 送信ファイル<br>
 *
 * @author nakai
 */
@Entity
@Data
@SuppressWarnings("serial")
public class OriginalFileLog implements Serializable {

    /** ID */
    @Id
    @SequenceGenerator(name = "originalfilelog_id_gen", sequenceName = "originalfilelog_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "originalfilelog_id_gen")
    private Long id;

    /** 送信情報ID */
    @Column
    private String sendInfoId;

    /** 送信ファイルID */
    @Column
    private String sendFileId;

    /** ファイル名 */
    @Column
    private String fileName;

    /** ユーザID */
    @Column
    private String uId;

    /** ワンタイムID */
    @Column
    private String onceId;

    /** タイムスタンプ */
    @Temporal(TemporalType.TIMESTAMP)
    private Date tStamp;
}
