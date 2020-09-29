/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 * 送信依頼情報エンティティ<br>
 * ファイル送信依頼（ワンタイムID発行）を行う際の情報を格納する<br>
 * 一対多：送信依頼受取先, 送信依頼添付ファイル<br>
 *
 * @author nakai
 */
@Entity
@Data
@SuppressWarnings("serial")
@XmlRootElement
public class SendRequestInfo implements Serializable {

    /**
     * ID
     */
    @Id
    private String id;

    /**
     * 送信者ID
     */
    @Column
    private String sendUserId;

    /**
     * 送信メールアドレス
     */
    @Column
    private String sendMailAddress;

    /**
     * 送信者名
     */
    @Column
    private String sendUserName;

    /**
     * 送信日時
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date sendTime;

    /**
     * 件名
     */
    @Column
    private String subject;

    /**
     * 本文
     */
    @Column(columnDefinition = "TEXT")
    private String content = "";

    /**
     * 有効期限
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date expirationTime;

    /**
     * パス自動有無
     */
    @Column
    private boolean passAuto;

    /**
     * パス通知有無
     */
    @Column
    private boolean passNotice;

    /**
     * パスワード
     */
    private String passWord;

    /**
     * 取り消しフラグ
     */
    private boolean cancelFlg;

    /**
     * 送信依頼宛先
     */
    @OneToMany(mappedBy = "sendRequestInfo", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @Setter(AccessLevel.NONE)
    @JsonManagedReference
    private List<SendRequestTo> sendRequestTos = new ArrayList<>();
}
