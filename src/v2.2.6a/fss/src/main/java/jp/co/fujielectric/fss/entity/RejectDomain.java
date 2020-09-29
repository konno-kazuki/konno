package jp.co.fujielectric.fss.entity;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Data;

/**
 * 送信不可ドメインマスタ
 */
@Entity
@Data
@SuppressWarnings("serial")
@XmlRootElement
public class RejectDomain implements Serializable {
    
    /**
     * ID
     */
    @Id
    private String id;

    /**
     * 送信不可ドメイン
     */
    @Column(length = 4096)
    private String domain;
}
