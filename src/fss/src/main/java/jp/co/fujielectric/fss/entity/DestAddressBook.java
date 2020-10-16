package jp.co.fujielectric.fss.entity;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.SequenceGenerator;
import lombok.Data;

/**
 * アドレス帳エンティティ<br>
 * アドレス帳エンティティに過去にファイル送信したメールアドレス・宛先情報を格納する<br>
 *
 * @author nakai
 */
@Entity
@Data
@SuppressWarnings("serial")
@NamedQueries({
    @NamedQuery(name = "DestAddressBook.findByToAddressOnly", query = "SELECT DISTINCT a FROM DestAddressBook a WHERE a.uid = :uid and a.toAddress LIKE :toAddress order by a.id ASC"),
    @NamedQuery(name = "DestAddressBook.findByPersonal", query = "SELECT DISTINCT a FROM DestAddressBook a WHERE a.uid = :uid and a.toAddress = :toAddress and a.personalName LIKE :personal order by a.id ASC"),
    @NamedQuery(name = "DestAddressBook.findByUid", query = "SELECT DISTINCT a FROM DestAddressBook a WHERE a.uid = :uid order by a.id ASC"),
    @NamedQuery(name = "DestAddressBook.findByPersonalOnly", query = "SELECT DISTINCT a FROM DestAddressBook a WHERE a.uid = :uid and a.personalName LIKE :personal order by a.id ASC"),
    @NamedQuery(name = "DestAddressBook.findByToAddress", query = "SELECT DISTINCT a FROM DestAddressBook a WHERE a.uid = :uid and a.personalName = :personal and a.toAddress LIKE :toAddress order by a.id ASC")
})
public class DestAddressBook implements Serializable {

    /**
     * ID
     */
    @Id
    private String id;

    /**
     * ユーザID
     */
    @Column
    private String uid;

    /**
     * 宛先メールアドレス
     */
    @Column
    private String toAddress;

    /**
     * 宛先名称
     */
    @Column
    private String personalName;

    /**
     * 使用回数
     */
    @Column
    private long useCount;
}
