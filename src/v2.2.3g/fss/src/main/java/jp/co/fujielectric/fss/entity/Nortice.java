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
 * お知らせ情報
 *
 * @author nakai
 */
@Entity
@Data
@SuppressWarnings("serial")
@NamedQueries({
    @NamedQuery(name = "Nortice.findAll", query = "SELECT a FROM Nortice a Order by a.id ASC"),
    @NamedQuery(name = "Nortice.findAllByToday", query = "SELECT a FROM Nortice a WHERE :today BETWEEN a.startTime AND a.endTime Order by a.endTime ASC, a.startTime ASC, a.id ASC")

})
@XmlRootElement
public class Nortice implements Serializable {

    /**
     * ID
     */
    @Id
    private long id;

    /**
     * 件名
     */
    @Column
    private String subject;

    /**
     * 本文
     */
    @Column(columnDefinition = "TEXT")
    private String content = "";

    /**
     * 開始日
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date startTime;

    /**
     * 終了日
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date endTime;
}
