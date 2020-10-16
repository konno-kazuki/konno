package jp.co.fujielectric.fss.data;

import java.io.Serializable;
import javax.enterprise.context.RequestScoped;
import jp.co.fujielectric.fss.entity.BasicUser;
import jp.co.fujielectric.fss.entity.UserType;
import lombok.Data;

/**
 * ＩＤ管理機能データクラス
 */
@Data
@RequestScoped
public class ManageIdBean extends BasicUser implements Serializable, Cloneable {
    /**
     * 種別
     */
    private UserType userTypeClass;
    /**
     * rowStyle
     */
    private String rowStyle;
    /**
     * checked
     */
    private boolean checked;
    /**
     * パスワード設定状況
     */
    private String passwordInfo;

    // コンストラクタ
    public ManageIdBean() {
        this.setUserId("");
        this.setName("");
        this.setMailAddress("");
        this.setPassword("");
        this.setUserType("");
        this.setStartTime(null);
        this.setEndTime(null);

        this.userTypeClass = null;
        this.rowStyle = "";
        checked = false;
        this.passwordInfo = "";
    }

    @Override
    public ManageIdBean clone() {
        ManageIdBean b = null;
        try {
            b = (ManageIdBean) super.clone();
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
