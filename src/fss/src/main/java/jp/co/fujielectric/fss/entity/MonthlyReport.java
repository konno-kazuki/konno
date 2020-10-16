/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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
 * 月報
 * @author nakai
 */
@Entity
@Data
@SuppressWarnings("serial")
@NamedQueries({
    @NamedQuery(name = "MonthlyReport.findForMonthlyReportList", query = "SELECT DISTINCT a FROM MonthlyReport a Order by a.yearmonth DESC")
})
@XmlRootElement
public class MonthlyReport implements Serializable {
    @Id
    private String yearmonth;

    @Column
    private String sla_vpn_vpn;

    @Column
    private String sla_vpn_lg;

    @Column
    private String sla_lg_vpn;

    @Column
    private String sla_internet_f;

    @Column
    private String sla_lgwan_f;

    @Column
    private String delaycount;

    @Column
    private long sendcount_m;

    @Column
    private long recvcount_m;

    @Column
    private long sanitizedfilecount_m;

    @Column
    private long deletedfilecount_m;

    @Column
    private long sendcount_f;

    @Column
    private long recvcount_f;

    @Column
    private long sanitizedfilecount_f;

    @Column
    private long deletedfilecount_f;
    
    @Column
    private String comfirmdate;
}
