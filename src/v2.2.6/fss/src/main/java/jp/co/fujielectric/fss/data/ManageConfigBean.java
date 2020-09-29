package jp.co.fujielectric.fss.data;

import java.io.Serializable;
import javax.enterprise.context.RequestScoped;
import jp.co.fujielectric.fss.entity.Config;
import lombok.Data;

/**
 * 機能設定データクラス
 */
@Data
@RequestScoped
public class ManageConfigBean implements Serializable, Cloneable {
    /**
     * ＩＤ
     */
    private String id;
    /**
     * 機能ID
     */
    private String funcId;
    /**
     * キー
     */
    private String itemKey;
    /**
     * 設定値
     */
    private String itemValue;       
    
    //[248対応（簡易版）]
    /**
     * 編集不可フラグ(機能設定画面）
     * trueの場合、機能設定画面での編集を不可とする
     */
    private boolean uneditableFlg = false;
    
    /**
     * rowStyle
     */
    private String rowStyle;
    /**
     * checked
     */
    private boolean checked;

    // コンストラクタ
    public ManageConfigBean() {
        this.id = "";
        this.funcId = "";
        this.itemKey = "";
        this.itemValue = "";
        this.uneditableFlg = false;     //[248対応（簡易版）]
        this.rowStyle = "";
        checked = false;
    }

    //[248対応（簡易版）]
    /**
     * コンストラクタ
     * @param config 値コピー元config
     */
    public ManageConfigBean(Config config) {
        this.id = config.getId();
        this.funcId = config.getFuncId();
        this.itemKey = config.getItemKey();
        this.itemValue = config.getItemValue();
        this.uneditableFlg = config.isUneditableFlg();
        this.rowStyle = "";
        checked = false;
    }
    
    @Override
    public ManageConfigBean clone() {
        ManageConfigBean b = null;
        try {
            b = (ManageConfigBean) super.clone();
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
     
    //[248対応（簡易版）]
    /**
     * プロパティをコピーしたConfigインスタンスを取得
     * @return 
     */
    public Config getConfig(){
        Config config = new Config();
        config.setId(this.id);
        config.setFuncId(this.funcId);
        config.setItemKey(this.itemKey);
        config.setItemValue(this.itemValue);
        config.setUneditableFlg(this.uneditableFlg);
        return config;
    }
}
