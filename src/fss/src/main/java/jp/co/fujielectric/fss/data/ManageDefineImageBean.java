package jp.co.fujielectric.fss.data;

import java.io.Serializable;
import javax.enterprise.context.RequestScoped;
import lombok.Data;

/**
 * イメージ定義設定機能データクラス
 */
@Data
@RequestScoped
public class ManageDefineImageBean implements Serializable, Cloneable {

    /**
     * 定義名称
     */
    private String itemKey;
    /**
     * 画像情報（Base64文字列）
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
    public ManageDefineImageBean() {
        this.itemKey = "";
        this.itemValue = "";
        this.rowStyle = "";
        checked = false;
    }

    @Override
    public ManageDefineImageBean clone() {
        ManageDefineImageBean b = null;
        try {
            b = (ManageDefineImageBean) super.clone();
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
