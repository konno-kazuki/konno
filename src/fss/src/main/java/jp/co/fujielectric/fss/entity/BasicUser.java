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
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Data;

/**
 * ユーザ情報
 *
 * @author nakai
 */
@Entity
@Data
@SuppressWarnings("serial")
@XmlRootElement
public class BasicUser implements Serializable {
    /** ID */
    @Id
    private String userId;

    /** ユーザ名 */
    @Column
    private String name;

    /** パスワード */
    @Column
    private String password;

    /** メールアドレス */
    @Column
    private String mailAddress;

    /** 種別 */
    @Column
    private String userType;

    /** 適用開始日 */
    @Temporal(TemporalType.TIMESTAMP)
    private Date startTime;

    /** 適用終了日 */
    @Temporal(TemporalType.TIMESTAMP)
    private Date endTime;

    /*パスワード失敗カウント*/
    @Column
    private int passwordFaultCount;
    
    /*ログインロックフラグ*/
    @Column
    private boolean loginLockFlg;
    
    /*パスワードリセットフラグ*/
    @Column
    private boolean passwordResetFlg;
    
    /*パスワード設定日時*/
    @Temporal(TemporalType.TIMESTAMP)
    private Date passwordSetDate;
    
    /*パスワード失敗日時*/
    @Temporal(TemporalType.TIMESTAMP)
    private Date passwordFaultDate;

    /*最終ログイン日時*/
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastLoginDate;
    
}
