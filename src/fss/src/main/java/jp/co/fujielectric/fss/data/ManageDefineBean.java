package jp.co.fujielectric.fss.data;

import java.io.Serializable;
import javax.enterprise.context.RequestScoped;
import lombok.Data;

/**
 * 定義設定機能データクラス
 */
@Data
@RequestScoped
public class ManageDefineBean implements Serializable, Cloneable {
    /**
     * 定義名称
     */
    private String itemKey;
    /**
     * 定義値
     */
    private String itemValue;
    /**
     * rowStyle
     */
    private String rowStyle;
    /**
     * checked
     */
    private boolean checked;

    // コンストラクタ
    public ManageDefineBean() {
        this.itemKey = "";
        this.itemValue = "";
        this.rowStyle = "";
        checked = false;
    }

    @Override
    public ManageDefineBean clone() {
        ManageDefineBean b = null;
        try {
            b = (ManageDefineBean) super.clone();
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
