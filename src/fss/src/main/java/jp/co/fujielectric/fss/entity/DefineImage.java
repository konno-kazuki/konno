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
 *
 * @author nakai
 */
@Entity
@Data
@SuppressWarnings("serial")
@XmlRootElement
public class DefineImage implements Serializable {

    @Id
    private String itemKey;

    @Column(length = 10485760)
    private String itemValue;

}
