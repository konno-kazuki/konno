/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.entity;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Data;
import lombok.ToString;

/**
 * サンプル詳細エンティティ（多対一）
 */
@Entity
@Data
@ToString(exclude = "sampleSubject")
@SuppressWarnings("serial")
@XmlRootElement
public class SampleContent implements Serializable {
    @Id
    private String id;

    @ManyToOne
    @JoinColumn(name = "subject_id")
    private SampleSubject sampleSubject;

    @Column
    private int contentNo;

    @Column
    private String content;
}
