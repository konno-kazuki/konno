/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 * サンプル一覧エンティティ（一対多）
 */
@Entity
@Data
@SuppressWarnings("serial")
@XmlRootElement
public class SampleSubject implements Serializable {
    @Id
    private String id;

    @Column
    private String subject;

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "sampleSubject", cascade = CascadeType.ALL)
    private final List<SampleContent> SampleContents = new ArrayList<>();
}
