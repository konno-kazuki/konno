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
 * メールロスト情報
 *
 */
@Entity
@Data
@SuppressWarnings("serial")
@NamedQueries({
    @NamedQuery(name = "MailLost.findAll", query = "SELECT a FROM MailLost a Order by a.updateDate DESC"),
    @NamedQuery(name = "MailLost.findAllForDisp", query = "SELECT a FROM MailLost a where a.sendInfoId <> '' Order by a.updateDate DESC")
})
@XmlRootElement
public class MailLost implements Serializable {
    
    /**
     * 全件取得用NamedQueue
     */
    public static final String NAMED_QUEUE_FIND_ALL = "MailLost.findAll";
    /**
     * 画面表示用一覧取得用NamedQueue
     * (SendInfoIdがセットされていない情報は対象外）
     */
    public static final String NAMED_QUEUE_FIND_FOR_DISP = "MailLost.findAllForDisp";
    
    /**
     * ID
     */
    @Id
    private String id;

    /**
     * 送信メールアドレス
     */
    @Column
    private String sendMailAddress;

    /**
     * 受信アドレス
     */
    @Column(columnDefinition = "TEXT")
    private String receiveAddresses = "";

    /**
     * 件名
     */
    @Column(columnDefinition = "TEXT")
    private String subject;
    
    /**
     * 送信日時
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date sendTime;

    /**
     * MailQueueID
     */
    @Column
    private String mailQueueId;
    
    /**
     * 送信情報ID（送信元）
     */
    @Column
    private String sendInfoId;

    /**
     * 受信情報ID
     */
    @Column
    private String receiveInfoId;
    
    /**
     * UploadGroupInfo ID
     */
    @Column
    private String uploadGroupInfoId;

    /**
     * メールロスト処理区分
     * MailEntrance　： メール受信処理～SandBlast送信処理で滞留
     * VotiroEntrance： SandBlastメール受信処理で滞留
     * VotiroUpload： Votiroアップロード処理で滞留
     * VotiroDownload： Votiroダウンロード～無害化完了処理で滞留
     */
    @Column
    private String functionKbn;

    /**
     * 更新日付
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateDate;
}


