package jp.co.fujielectric.fss.entity;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ワンタイムユーザ情報
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table
@NamedQueries({
    @NamedQuery(name = "OnceUser.checkPW", query = "SELECT a FROM OnceUser a WHERE a.onetimeId = :onetimeId AND a.password = :password"),
    //指定したtargetで有効な（expirationTimeが未来の）一覧を取得
    @NamedQuery(name = "OnceUser.getEffectiveInfo", query = "SELECT a FROM OnceUser a WHERE a.target = :target AND a.expirationTime >= :expirationTime")
})
@XmlRootElement
public class OnceUser implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @NotNull
    @Column
    private String onetimeId;

    @Column
    private String password;

    @Column
    private String target;

    @Column
    private String mailId;

    @Column
    private String mailAddress;

    /**
     * 有効期限
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date expirationTime;
}
