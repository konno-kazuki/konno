package jp.co.fujielectric.fss.data;

import java.io.Serializable;
import java.util.Date;
import javax.enterprise.context.RequestScoped;
import lombok.Data;

/**
 * ＩＤ管理機能データクラス
 */
@Data
@RequestScoped
public class ManageNorticeBean implements Serializable, Cloneable {

    /* ID */
    long id;
    /* 件名 */
    private String subject;
    /* 本文 */
    private String content;
    /* 開始日 */
    private Date startTime;
    /* 終了日 */
    private Date endTime;
    /* rowStyle */
    String rowStyle;
    /* checked */
    private boolean checked;

    // コンストラクタ
    public ManageNorticeBean() {
        this.subject = "";
        this.content = "";
        this.rowStyle = "";
        this.startTime = null;
        this.endTime = null;
        checked = false;
    }

    @Override
    public ManageNorticeBean clone() {
        ManageNorticeBean b = null;
        try {
            b = (ManageNorticeBean) super.clone();
        } catch (CloneNotSupportedException e) {
        }
        return b;
    }

    /**
     * rowStyle＝行選択
     */
    public void setRowStyleSelect() {
        this.rowStyle = DataTableBean.ROWSTYLE_SELECTED;
    }

    /**
     * チェックＯＮ
     */
    public void setChecked() {
        this.checked = true;
    }

    /**
     * チェッククリア
     */
    public void setCheckedOff() {
        this.checked = false;
    }
}
