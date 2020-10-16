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
 * 機能マスタ
 * @author nakai
 */
@Entity
@Data
@SuppressWarnings("serial")
@NamedQueries({
    @NamedQuery(name = "UserTypePermission.findUnique", query = "SELECT utp FROM UserTypePermission AS utp WHERE utp.link = :link AND (utp.userTypeId = :userTypeId OR utp.userTypeId IS NULL) AND utp.sectionId = :sectionId"),
})
@XmlRootElement
public class UserTypePermission implements Serializable {
    @Id
    private String id;

    @Column
    private String link;

    @Column
    private String userTypeId;

    @Column
    private String sectionId;
}
