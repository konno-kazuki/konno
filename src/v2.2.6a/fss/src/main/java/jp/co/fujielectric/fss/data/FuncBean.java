package jp.co.fujielectric.fss.data;

import jp.co.fujielectric.fss.entity.Func;
import lombok.Data;

/**
 * 機能情報クラス
 */
@Data
public class FuncBean {
    private String funcId;          ///機能ID
    private String funcName;        ///機能名
    private String funcParentId;    ///親ID
    private String target;          ///遷移先
    private boolean linkFlg;
    
    // コンストラクタ
    public FuncBean() {
        funcId = "";
        funcName = "";
        funcParentId = "";
        target = "";
        
        linkFlg = false;
    }
    
    // コンストラクタ
    public FuncBean(Func _func) {
        funcId = _func.getId();
        funcName = _func.getName();
        funcParentId = _func.getParentId();
        target = _func.getTarget();
        
        linkFlg = false;
    }
}
