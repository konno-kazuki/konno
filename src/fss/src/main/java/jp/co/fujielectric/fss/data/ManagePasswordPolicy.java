package jp.co.fujielectric.fss.data;

import java.io.Serializable;
import java.util.Date;
import javax.enterprise.context.RequestScoped;
import jp.co.fujielectric.fss.entity.Config;
import lombok.Data;

/**
 * パスワードポリシーデータクラス
 */
@Data
@RequestScoped
public class ManagePasswordPolicy implements Serializable, Cloneable {
    
    /**
     * 項目番号
     */
    private int itemNo;

    /**
     * 有効無効設定用Config
     */
    private Config itemConfig;
    
    /**
     * 設定値用Config
     */
    private Config valueConfig;
    
    /**
     * 項目タイトル
     */
    private String itemTitle;

    /**
     * 単位
     */
    private String itemUnit;
    
    /**
     * 説明文
     */
    private String itemComment;
    
    /**
     * 有効/無効
     */
    private boolean checked;
    
    /**
     * 定義値
     */
    private String itemValue;
    
    /**
     * 設定値が数値かどうか
     */
    private boolean isNumeric;

    /**
     * 設定値あり項目か
     */
    private boolean hasValue;
    
    /**
     * 更新日時
     */
    private Date upadateDate;

    /**
     * 日付表示有無
     */
    private boolean isShowDate;

    /**
     * 最小値
     */
    private int minNumValue;

    /**
     * 最大値
     */
    private int maxNumValue;

    /**
     * エラーかどうか
     */
    private boolean isError;

     /**
     * コンストラクタ
     * @param itemNo
     * @param itemConfig
     * @param valueConfig
     * @param title
     * @param unit
     * @param comment
     * @param isNumeric 
     * @param minNumVal 
     * @param maxNumVal 
     */
    public ManagePasswordPolicy(
            int itemNo,
            Config itemConfig, Config valueConfig, String title, String unit, String comment,
            boolean isNumeric, int minNumVal, int maxNumVal) {
        
        if(itemConfig == null)
            return;

        if(itemConfig.getItemValue() == null)
            itemConfig.setItemValue("");
        
        if(valueConfig != null && valueConfig.getItemValue() == null)
            valueConfig.setItemValue("");
        
        this.itemNo = itemNo;
        this.itemConfig = itemConfig;
        this.valueConfig = valueConfig;
        this.itemTitle = title;
        this.itemUnit = unit;
        this.itemComment = comment;        
        this.checked = (itemConfig.getItemValue().compareToIgnoreCase("true") == 0);
        this.itemValue = (valueConfig != null ? valueConfig.getItemValue() : "");
        this.hasValue = (valueConfig != null);
        this.isNumeric = isNumeric;
        
        this.minNumValue = minNumVal;
        this.maxNumValue = maxNumVal;
    }
    
    /**
     * 有効無効変更有無
     * @return 
     */
    public boolean hasCheckChanged(){
        String newVal = (checked ? "true" : "false");
        return (itemConfig.getItemValue().compareToIgnoreCase(newVal) != 0);
    }

    /**
     * 設定値変更有無
     * @return 
     */
    public boolean hasValueChanged(){
        if(valueConfig == null)
            return false;
        return (valueConfig.getItemValue().compareTo(itemValue) != 0);
    }
    
    /**
     * 設定値をint型で取得
     * @return
     * @throws NumberFormatException 
     */
    public int getNumValue() throws NumberFormatException{
        if(isChecked())
            return Integer.parseInt(getItemValue());
        //無効の場合は0を返す
        return 0;
    }
    
    public String getItemKey()
    {
        return (itemConfig == null ? "" : itemConfig.getItemKey());
    }

    public String getValueKey()
    {
        return (valueConfig == null ? "" : valueConfig.getItemKey());
    }
    
    @Override
    public ManagePasswordPolicy clone() {
        ManagePasswordPolicy b = null;
        try {
            b = (ManagePasswordPolicy) super.clone();
        } catch (CloneNotSupportedException e) {
        }
        return b;
    }
}
