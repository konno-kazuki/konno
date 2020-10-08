package jp.co.fujielectric.fss.entity;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Data;

/**
 * ドメインマスター
 */
@XmlRootElement
@Entity
@Data
public class DomainMaster  implements Serializable  {

    /**
     * ドメイン名
     */
    @Id
    private String domain;
    
    /**
     * データベース名
     */
    @Column(length = 128)
    private String database;    
}
