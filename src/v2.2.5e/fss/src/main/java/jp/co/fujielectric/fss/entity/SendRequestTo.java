/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Data;

/**
 * 送信依頼宛先エンティティ<br>
 * 送信依頼情報に紐付く宛先の情報を格納する<br>
 * 多対一： 送信依頼情報<br>
 *
 * @author nakai
 */
@Entity
@Data
@SuppressWarnings("serial")
@XmlRootElement
public class SendRequestTo implements Serializable {

    /**
     * ID
     */
    @Id
    private String id;

    /**
     * 送信依頼情報
     */
    @ManyToOne
    @JoinColumn(name = "sendrequestinfo_id")
    @JsonBackReference
    private SendRequestInfo sendRequestInfo;

    /**
     * 受信メールアドレス
     */
    @Column
    private String receiveMailAddress;

    /**
     * 受信者名
     */
    @Column
    private String receiveUserName;

    @Override
    public String toString() {
        return "id=" + (id == null ? "" : id) + "_"
                + "receiveMailAddress=" + (receiveMailAddress == null ? "" : receiveMailAddress) + "_"
                + "receiveUserName=" + (receiveUserName == null ? "" : receiveUserName);
    }

}
