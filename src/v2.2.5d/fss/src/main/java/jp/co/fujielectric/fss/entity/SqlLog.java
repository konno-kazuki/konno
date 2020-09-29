/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.entity;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.SequenceGenerator;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * サンプルエンティティ
 */
@Entity // Entity(≒永続化クラス≒テーブル）であることを示すアノテーション
@Data   // lombokによる、データモデルであることを示すアノテーション
//  setter/getter、hashCode()、equals()、toString() をサポートする。
@SuppressWarnings("serial")
@NamedQueries({
    @NamedQuery(name = "SqlLog.findForOutput", query = "SELECT DISTINCT a FROM SqlLog a WHERE date_trunc('day', a.tStamp) between :dateFrom and :dateTo Order by a.tStamp DESC")
})
@AllArgsConstructor
@NoArgsConstructor
@XmlRootElement
public class SqlLog implements Serializable {

    // 主キーを表すアノテーション
    @Id
    @SequenceGenerator(name = "sqlLog_id_gen", sequenceName = "sqlLog_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "sqlLog_id_gen")
    private long id;

    @Column
    private String uId;

    @Column
    private String onceId;

    // カラムを表すアノテーション
    @Column
    private String clsName;

    @Column
    private String methodName;

    @Column
    private String status;

    @Column
    private String param;

    @Column
    private String lstSize;

    @Temporal(TemporalType.TIMESTAMP)
    private Date tStamp;
}
