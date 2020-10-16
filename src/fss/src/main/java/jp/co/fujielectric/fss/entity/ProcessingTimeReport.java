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
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Data;

/**
 * 処理時間記録
 *
 */
@Entity
@Data
@SuppressWarnings("serial")
@NamedQueries({
    @NamedQuery(name = ProcessingTimeReport.QUERY_FINDALL, query = "SELECT a FROM ProcessingTimeReport a Order by a.measureTime ASC"),
    @NamedQuery(name = ProcessingTimeReport.QUERY_FIND_BY_FROMTO, query = "SELECT a FROM ProcessingTimeReport a WHERE a.measureTime BETWEEN :fromDate AND :toDate Order by a.measureTime ASC")

})
@XmlRootElement
public class ProcessingTimeReport implements Serializable {

    /**
     * 全検索クエリ
     */
    public static final String QUERY_FINDALL = "ProcessingTimeReport.findAll";

    /**
     * 日付範囲指定検索
     */
    public static final String QUERY_FIND_BY_FROMTO = "ProcessingTimeReport.findByFromTo";

    /**
     * 計測日時
     */
    @Id
    @Temporal(TemporalType.TIMESTAMP)
    private Date measureTime;

    /**
     * 処理時間（分）
     */
    @Column(length = 10)
    private String processingTime;
}
