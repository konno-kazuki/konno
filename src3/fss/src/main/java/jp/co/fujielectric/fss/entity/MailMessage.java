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
 * メールメッセージマスタ
 */
@Entity
@Data
@SuppressWarnings("serial")
@NamedQueries({
    @NamedQuery(name = "MailMessage.findMessage", query = "SELECT a FROM MailMessage AS a WHERE a.funcId = :funcId AND a.itemKey = :itemKey")
})
@XmlRootElement
public class MailMessage implements Serializable {
    @Id
    private String id;

    @Column
    private String funcId;

    @Column
    private String itemKey;

    @Column(length = 1000)
    private String itemValue;
}
