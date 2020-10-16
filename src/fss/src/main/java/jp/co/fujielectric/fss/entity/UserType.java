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
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Data;

/**
 * 機能マスタ
 * @author nakai
 */
@Entity
@Data
@SuppressWarnings("serial")
@XmlRootElement
public class UserType implements Serializable {
    @Id
    private String id;

    @Column
    private String name;

    @Column
    private boolean disp;

    @Column
    private int sort;

    @Column
    private boolean internalflg;
}
