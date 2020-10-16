package jp.co.fujielectric.fss.entity;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Data;

/**
 * 設定マスタ
 */
@Entity
@Data
@SuppressWarnings("serial")
@NamedQueries({
    @NamedQuery(name = "Config.findConfig", query = "SELECT c FROM Config AS c WHERE c.funcId = :funcId AND c.itemKey = :itemKey"),
    @NamedQuery(name = "Config.findAllConfig", query = "SELECT c FROM Config AS c Order by c.funcId, lower(c.itemKey)")
})
@XmlRootElement
public class Config implements Serializable {
    
    @Id
    private String id;

    @Column
    private String funcId;

    @Column
    private String itemKey;

    @Column(columnDefinition = "TEXT")
    private String itemValue;

    //[248対応（簡易版）]
    /**
     * 編集不可フラグ(機能設定画面）
     * trueの場合、機能設定画面での編集を不可とする
     */
    @Column
    private boolean uneditableFlg;
    
}
