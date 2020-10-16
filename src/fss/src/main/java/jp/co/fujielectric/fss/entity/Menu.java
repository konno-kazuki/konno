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
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Data;

/**
 * メニュー管理エンティティ<br>
 * ポータルに表示されるメニューを管理する<br>
 * @author nakai
 */
@Entity
@Data
@SuppressWarnings("serial")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Menu.findTabNo", query = "SELECT m FROM Menu m WHERE m.tabNo = :tabNo ORDER BY m.position")
})
public class Menu implements Serializable {
    @Id
    private int id;

    @Column
    private String name;

    @Column
    private String link;

    @Column
    private String icon;

    @Column
    private String comment;

    @Column
    private String button;

    @Column
    private String style;

    @Column
    private int tabNo;

    @Column
    private int position;

    @Transient
    private String target;
}
