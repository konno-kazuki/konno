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
 * ユーザーパスワード履歴
 *
 */
@Entity
@Data
@SuppressWarnings("serial")
@NamedQueries({
    @NamedQuery(name = UserPasswordHistory.NAMEDQUERY_FIND_BY_USERID, 
            query = "SELECT c FROM UserPasswordHistory AS c WHERE c.userId = :userId ORDER BY c.updateDate DESC"),
})
@XmlRootElement
public class UserPasswordHistory implements Serializable {
    public static final String NAMEDQUERY_FIND_BY_USERID = "findByUserID";
    
    /**
     * 主キー
     */
    @Id
    private String id;

    /** ユーザID */
    @Column
    private String userId;

    /** パスワード */
    @Column
    private String password;

    /** 更新日時 */
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateDate;
}
