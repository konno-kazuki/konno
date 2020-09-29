package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.data.Item.ErrMsgItemKey;
import jp.co.fujielectric.fss.data.Item.PasswordPolicyItemKey;
import jp.co.fujielectric.fss.data.ManagePasswordPolicy;
import jp.co.fujielectric.fss.logic.PasswordPolicyLogic;
import jp.co.fujielectric.fss.service.ConfigService;
import lombok.Getter;
import lombok.Setter;

/**
 * パスワードポリシービュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class ManagePasswordPolicyView extends ManageCommonView implements Serializable {
//    /**
//     * パスワードリセット日時フォーマット
//     */
//    private static final String RESET_DATE_FORMAT = "yyyy/MM/dd hh:mm:ss";
    
    @Inject
    private ConfigService configService;

    @Inject
    private PasswordPolicyLogic passwordPolicyLogic;
    
    @Getter
    @Setter
    private List<ManagePasswordPolicy> passwordPolicyList;

    @Getter
    @Setter
    private String passwordPolicyNote;
    
    @Getter
    private long maxlenValue;                     //機能値の文字数Max

    @Getter
    private int maxLenPasword;                   //パスワードの文字数Max
    

    private ManagePasswordPolicy pswdResetPolicy;           //パスワードリセット設定行用アイテム 
    
    //コンストラクタ
    public ManagePasswordPolicyView() {
        funcId = "managePasswordPolicy";
    }

    @PostConstruct
    @Override
    public void init() {
        super.init();
    }

    /**
     * マスタ設定値からの変数初期化
     *
     */
    @Override
    protected void initItems() {
        super.initItems();
       
         //configの設定値の文字数Max
         Item item = itemHelper.find(Item.MAX_LEN_FUNC_VALUE, funcId);
         maxlenValue = Integer.parseInt(item.getValue());
         item = itemHelper.find(Item.PASSWORD_CHAR_MAX, funcId);
         maxLenPasword = Integer.parseInt(item.getValue());
    }

    /**
     * 表示リストを取得
     */
    @Override
    protected void getItemList() {
        
        //パスワードポリシー情報リストを取得
        passwordPolicyList = passwordPolicyLogic.getPasswordPolicyItemList();
        
        //「ログイン後、直ぐにパスワードを変更」（パスワードリセット）は他項目とは異なる扱いとする。
        //この項目は他項目と異なりconfigには最終実施日を登録する。
        //有効/無効の初期表示はfalse。
        pswdResetPolicy = getPasswordPolicyItem(PasswordPolicyItemKey.RESETDATE);
        try{
            //設定値（日付文字列）を日付型に変換
            SimpleDateFormat sdFormat = new SimpleDateFormat(PasswordPolicyLogic.RESET_DATE_FORMAT);
            Date date = sdFormat.parse(pswdResetPolicy.getItemValue());
            pswdResetPolicy.setUpadateDate(date);  //最終実施日として表示
        }catch(Exception e){}
        pswdResetPolicy.setHasValue(false);    //設定値表示なし
        pswdResetPolicy.setShowDate(true);     //日付表示
        
        //パスワードポリシー説明文の取得
        passwordPolicyNote = passwordPolicyLogic.getPasswordPolicyNote();
    }
    
    /**
     * 更新
     *
     * @return 結果(0=成功、0以外＝失敗)
     */
    @Override
    protected int exeUpdate() {

        int ret = -1;

        try {
            passwordPolicyLogic.updatePasswordPolicy(passwordPolicyList, pswdResetPolicy, passwordPolicyNote);
            // 成功
            ret = 0;
        } catch (Exception ex) {
            LOG.error("パスワードポリシー更新失敗。", ex);
        }

        return ret;
    }    
    
    /**
     * 入力チェック
     *
     * @param _mode 処理モード(ManageConfigBean.MODE_ADD,MODE_UPDATE,MODE_DELETE)
     *
     * @return チェック結果
     */
    @Override
    protected boolean checkInput(String _mode) {
        boolean bret = true;
        String errMsg;
        String componentId;
        String itemName;

        String frmName = "inputForm:passwordPolicyTable";

        try {
            FacesContext context = FacesContext.getCurrentInstance();

            //エラーリストクリア
            errComponentIdList.clear();
            
            for(ManagePasswordPolicy pp :passwordPolicyList){
                //無効または設定値の無い項目はチェック対象外
                if(!pp.isChecked() || !pp.isHasValue())
                    continue;
                
                if(pp.isNumeric()){
                    //------------------------------
                    //数値項目チェック
                    //------------------------------
                    itemName = pp.getItemTitle();   //"項目名"
                    componentId = frmName + ":" + pp.getItemNo() + ":itemNumValue"; //項目ID
                    errMsg = "";
                    try {
                        //数値判定
                        int inputNum = pp.getNumValue();
                        pp.setItemValue(Integer.toString(inputNum));
                        //最小、最大判定
                        if(inputNum < pp.getMinNumValue() || pp.getMaxNumValue() < inputNum ){
                            errMsg = itemHelper.findDispMessageStr(ErrMsgItemKey.INPUT_ERR_NUMBER, funcId, pp.getMinNumValue(), pp.getMaxNumValue());
                        }
                    } catch (NumberFormatException e) {
                        errMsg = itemHelper.findDispMessageStr(ErrMsgItemKey.INPUT_ERR_NUMBER, funcId, pp.getMinNumValue(), pp.getMaxNumValue());
                    }
                    pp.setError(!errMsg.equals(""));
                    if(pp.isError()){
                        context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                        errComponentIdList.add(componentId);
                        bret = false;                        
                    }                    
                }else{
                    //------------------------------
                    //文字列項目チェック
                    //------------------------------
                    // 特になし
                }
            }
            //----------------------
            //組合わせチェック
            //----------------------
            //④パスワード最小文字数　>　⑤パスワード英字最小文字数＋⑥パスワード数字最小文字数＋⑦パスワード記号最小文字数　であること
            ManagePasswordPolicy ppMinLen = getPasswordPolicyItem(PasswordPolicyItemKey.MINLENGTH_CHECK);
            if(ppMinLen.isChecked() && !ppMinLen.isError() ){
                ManagePasswordPolicy ppMinAlpLen = getPasswordPolicyItem(PasswordPolicyItemKey.MINALP_CHECK);
                ManagePasswordPolicy ppMinNumLen = getPasswordPolicyItem(PasswordPolicyItemKey.MINNUM_CHECK);
                ManagePasswordPolicy ppMinMarkLen = getPasswordPolicyItem(PasswordPolicyItemKey.MINMARK_CHECK);
                if( !ppMinAlpLen.isError() && !ppMinNumLen.isError() && !ppMinMarkLen.isError()){
                    try{
                        if(ppMinLen.getNumValue() < ppMinAlpLen.getNumValue() + ppMinNumLen.getNumValue() + ppMinMarkLen.getNumValue()){
                            //「パスワード最小文字数」には「パスワード英字最小文字数」＋「パスワード数字最小文字数」＋「パスワード記号最小文字数」以上の数値を入力してください。
                            ppMinLen.setError(true);
                            errMsg = itemHelper.findDispMessageStr(ErrMsgItemKey.INPUT_PSWD_LEN, funcId);
                            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(ppMinLen.getItemTitle()), errMsg));
                            errComponentIdList.add(frmName + ":" + ppMinLen.getItemNo() + ":itemNumValue");
                            bret = false;                        
                        }
                    }catch(Exception e){}
                }                
            }
        } catch (Exception ex) {
            LOG.error("パスワードポリシー　入力チェック失敗。", ex);
            return false;
        }

        return bret;
    }

    /**
     * キーから項目データを取得
     */
    private ManagePasswordPolicy getPasswordPolicyItem(PasswordPolicyItemKey key){
        return passwordPolicyLogic.getPasswordPolicyItem(passwordPolicyList, key);
    }
}
