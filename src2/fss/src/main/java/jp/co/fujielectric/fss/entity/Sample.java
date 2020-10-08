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
import javax.persistence.NamedQuery;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * サンプルエンティティ
 */
@Entity // Entity(≒永続化クラス≒テーブル）であることを示すアノテーション
@Data   // lombokによる、データモデルであることを示すアノテーション
        //  setter/getter、hashCode()、equals()、toString() をサポートする。
@AllArgsConstructor
@NoArgsConstructor

@NamedQuery(name = "Sample.findName", query = "select s from Sample s where s.name = :name")

@XmlRootElement
public class Sample implements Serializable{
    // 主キーを表すアノテーション
    @Id
    private int id;

    // カラムを表すアノテーション
    @Column
    private String name;

    // カラムを表すアノテーション（必須＆長さを制限）
    @Column(nullable = false , length = 8)
    private String type;

    // 非DB要素を表すアノテーション（コードに対する名前情報など、DBに格納しなくてよい値）
    @Transient
    private String type_name;
}
