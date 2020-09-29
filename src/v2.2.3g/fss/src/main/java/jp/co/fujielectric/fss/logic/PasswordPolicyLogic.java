package jp.co.fujielectric.fss.logic;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.data.Item;
import static jp.co.fujielectric.fss.data.Item.ErrMsgItemKey;
import jp.co.fujielectric.fss.data.ManagePasswordPolicy;
import jp.co.fujielectric.fss.entity.BasicUser;
import jp.co.fujielectric.fss.entity.Config;
import jp.co.fujielectric.fss.entity.UserPasswordHistory;
import jp.co.fujielectric.fss.service.BasicUserService;
import jp.co.fujielectric.fss.service.ConfigService;
import jp.co.fujielectric.fss.service.UserPasswordHistoryService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.IdUtil;
import org.apache.logging.log4j.Logger;

@RequestScoped
public class PasswordPolicyLogic {
    
    String funcId = "managePasswordPolicy";
    
    /**
     * パスワードリセット日時フォーマット
     */
    public static final String RESET_DATE_FORMAT = "yyyy/MM/dd hh:mm:ss";
    
    /**
     * パスワード失敗回数のリセット時間
     */
    protected static final int FAULTCNT_RESET_HOUR = 12;
    
    /**
     * 数値入力最大値
     */
    protected  final int maxNumVal = 999;
    
    @Inject
    private Logger LOG;
    
    @Inject
    private BasicUserService basicUserService;
    
    @Inject
    private UserPasswordHistoryService userPasswordHistoryService;
    
    @Inject
    protected ItemHelper itemHelper;
    
    @Inject
    private ConfigService configService;
    
    /**
     * パスワードポリシー更新
     * @param passwordPolicyList
     * @param pswdResetPolicy
     * @param policyNote 
     */
    @Transactional
    public void updatePasswordPolicy(
            List<ManagePasswordPolicy> passwordPolicyList, 
            ManagePasswordPolicy pswdResetPolicy,
            String policyNote){
        try {
            List<Config> confLst = new ArrayList(); //更新対象configアイテムリスト

            //「ログイン後、直ぐにパスワードを変更」項目の処理
            if(pswdResetPolicy.isChecked()){
                //BasicUserのリセットフラグ設定
                setPasswordResetAll();
                //configに現在日時をセット
                Config config = pswdResetPolicy.getValueConfig();
                Date dt = new Date();
                String strDt = new SimpleDateFormat(RESET_DATE_FORMAT).format(dt);
                config.setItemValue(strDt);
                confLst.add(config);    //更新対象configアイテムリストに追加
                //再表示用に画面表示される日付も更新する
                pswdResetPolicy.setUpadateDate(dt);
            }
            
            //設定変更されたアイテムを一括更新する（対象テーブル：config）
            for(ManagePasswordPolicy pp :passwordPolicyList){
                if(pp == pswdResetPolicy){
                    //「ログイン後、直ぐにパスワードを変更」は上で処理済み
                    continue;
                }
                //有効/無効
                if(pp.hasCheckChanged()){
                    Config itemConfig = pp.getItemConfig();
                    itemConfig.setItemValue(pp.isChecked() ? "true" : "false");
                    confLst.add(itemConfig);
                }
                //設定値
                //有効の場合のみ更新する
                if(pp.isChecked() && pp.hasValueChanged()){
                    Config valueConfig = pp.getValueConfig();
                    valueConfig.setItemValue(pp.getItemValue()); //入力値をconfigに反映
                    confLst.add(valueConfig);
                }
            }
            for(Config config: confLst){
                configService.edit(config);
            }
            
            //説明文更新
            setPasswordPolicyNote(policyNote);
        } catch (Exception ex) {
            throw new RuntimeException("パスワードポリシー更新失敗。", ex);
        }        
    }    
    
    /**
     * 全ユーザーパスワードリセット
     */
    @Transactional
    public void setPasswordResetAll(){
        LOG.debug("PasswordReset実行");

        //BasicUserから全データ取得
        List<BasicUser> userLst = basicUserService.findAll();

        //BasicUserの全ユーザのパスワードリセット処理をする
        for(BasicUser user:userLst){
            user.setPasswordResetFlg(true); //リセットフラグを立てる
            user.setPasswordFaultCount(0);  //パスワード失敗カウントのリセット
            user.setLoginLockFlg(false);    //ログインロックフラグのリセット
        }

        //BasicUserの更新
//        basicUserService.update(userLst);
        for(BasicUser entity: userLst){
            basicUserService.edit(entity);
        }
    }

    /**
     * ログインロックリセット処理
     * @param basicuser 
     * @return true:更新あり
     */
    @Transactional
    public boolean resetLoginLock(BasicUser basicuser)
    {
        boolean ret = false;
        
        //パスワード失敗日時がセットされていない場合は何もしない
        if(basicuser.getPasswordFaultDate() == null)
            return ret;
        
        if(basicuser.isLoginLockFlg()){
            //-----------------------------------------
            //ログインロックされている場合のロック解除処理
            //-----------------------------------------
            //、ログインロック解除時間をConfigから取得（ログインロック解除が有効の場合）
            int lockClearHour = getConfigNumValue(Item.PasswordPolicyItemKey.LOCKCLEAR, Item.PasswordPolicyItemKey.LOCKCLEAR_TIME);
            if(lockClearHour > 0){
                //パスワード失敗日時からログインロック解除時間経過しているか？
                if(isOverTime(basicuser.getPasswordFaultDate(), lockClearHour, Calendar.HOUR)){
                    //現在時刻がパスワード失敗日時+ログインロック解除時間 を超えている場合、ログインロックリセット処理をする
                    basicuser.setPasswordFaultCount(0);  //パスワード失敗カウントのリセット
                    basicuser.setLoginLockFlg(false);    //ログインロックフラグのリセット
                    ret = true; //DB更新要
                }                
            }
        }else{
            //-----------------------------------------
            //ログインロックされていない場合のパスワード失敗カウントのリセット処理
            //-----------------------------------------
            if(basicuser.getPasswordFaultCount() > 0){
                //パスワード失敗日時からパスワード失敗カウントリセット時間経過しているか？
                if(isOverTime(basicuser.getPasswordFaultDate(), FAULTCNT_RESET_HOUR, Calendar.HOUR)){
                    //現在時刻がパスワード失敗日時+パスワード失敗カウントリセット時間 を超えている場合、パスワード失敗カウントをリセットをする
                    basicuser.setPasswordFaultCount(0);  //パスワード失敗カウントのリセット
                    ret = true; //DB更新要
                }                                
            }
        }
        if(ret){
            basicUserService.edit(basicuser);   //DB更新
        }
        return ret;        
    }
   
    /**
     * パスワードミス時処理
     * @param basicuser
     * @return true:更新あり
     */
    @Transactional
    public boolean setPasswordMiss(BasicUser basicuser)
    {        
        //既にログインロックされている場合は何もしない
        if(basicuser.isLoginLockFlg())
            return false;
        
        //パスワード失敗日時に現在日時をセット
        basicuser.setPasswordFaultDate(new Date());
        //パスワード失敗カウントのインクリメント（+1）
        int faultCnt = basicuser.getPasswordFaultCount() + 1;
        basicuser.setPasswordFaultCount(faultCnt);
        
        //ログインロック試行回数をConfigから取得(ログインロックが有効の場合）
        int lockCountMax = getConfigNumValue(Item.PasswordPolicyItemKey.LOGINLOCK, Item.PasswordPolicyItemKey.LOCK_COUNT);        
        if(lockCountMax > 0){
            //パスワード失敗カウント＞ログインロック試行回数設定値となった場合ログインロックフラグを立てる
            if(faultCnt >= lockCountMax){
                basicuser.setLoginLockFlg(true);
            }
        }
        basicUserService.edit(basicuser);   //DB更新
        return true;
    }

    /**
     * パスワードリセットチェック処理
     * @param basicuser
     * @return リセット必要
     */
    public String chkPasswordResetAndGetMsg(BasicUser basicuser)
    {
        String warnMsg = "";

        //以下のパスワード再設定の要否チェック処理を行う		
	//・パスワードリセットフラグ = true	
	//・現在日時　> (パスワード設定日時 + パスワード有効期限設定値）	
        
        //パスワードリセットフラグの確認
        if(basicuser.isPasswordResetFlg()){
            //パスワードリセットフラグがたっている
            warnMsg = itemHelper.findDispMessageStr(ErrMsgItemKey.ERR_PW_RESET, funcId);
        }else if(basicuser.getPasswordSetDate() != null){
            //パスワード有効期限の確認
            
            //パスワード有効期限日数をConfigから取得（パスワード有効期限が有効の場合）
            int limitDay = getConfigNumValue(Item.PasswordPolicyItemKey.LIMIT_CHECK, Item.PasswordPolicyItemKey.LIMIT_DAY);
            if(limitDay > 0){
                //パスワード設定日時からパスワード有効期限日数経過しているか？
                if(isOverTime(basicuser.getPasswordSetDate(), limitDay, Calendar.DAY_OF_MONTH)){
                    //現在時刻がパスワード設定日時+有効期限日数 を超えている場合、リセット必要と判定
                    warnMsg = itemHelper.findDispMessageStr(ErrMsgItemKey.ERR_PW_LIMIT, funcId, limitDay);
                }                
            }
        }
        return warnMsg;
    }
    
    /**
     * パスワードリセットチェック処理
     * @param basicuser
     * @return リセット必要
     */
    public boolean chkPasswordReset(BasicUser basicuser)
    {
        return (!chkPasswordResetAndGetMsg(basicuser).isEmpty());
    }
    
    /**
     * パスワードチェック
     * @param userId
     * @param password
     * @return エラー：エラーメッセージ、　エラー無し：""
     */
    public String chkPasswordAndGetErrMsg(String userId, String password)
    {
        int numVal;
        
        //パスワード文字数Max
        int passwordCharMax = getConfigNumValue(Item.PASSWORD_CHAR_MAX);                
//トリミングは不要 (2018/10/10)
//        //調査対象パスワードをトリミング
//        String pswd = password.trim();
        String pswd = password;

        ///パスワードの半角英数字入力確認（既存処理をUserPasswordSetViewから移動）
        if (!CommonUtil.isValidPassword(pswd)) {
            LOG.trace("新パスワードで禁則文字を使用");

            ///errMsg = "パスワードには半角英数文字のみ入力してください。"
            return (itemHelper.findDispMessageStr(Item.ErrMsgItemKey.PASSWORD_INVALID, funcId));
        }
        
        //ID同一パスワード制限
        if(getConfigEnableValue(Item.PasswordPolicyItemKey.SAMEID_PW_CHECK)){
            if(pswd.equalsIgnoreCase(userId))
                // ユーザIDと同じパスワードは設定できません。
                return itemHelper.findDispMessageStr(ErrMsgItemKey.ERR_PW_SAMEID_PW, funcId);
        }
        
        // パスワード最小文字数チェック
        if((numVal = getConfigNumValue(Item.PasswordPolicyItemKey.MINLENGTH_CHECK, Item.PasswordPolicyItemKey.MINLENGTH)) <= 0){
            //パスワード最小文字数が設定されていなければ、configのパスワード最小文字を適用する
            numVal = getConfigNumValue(Item.PASSWORD_CHAR_MIN);
        }
        if(pswd.length() < numVal){
            // パスワードは%s文字以上必要です。
            return itemHelper.findDispMessageStr(ErrMsgItemKey.ERR_PW_MINLENGTH, funcId, numVal, passwordCharMax);
        }
        
//        //パスワードポリシーで文字数指定がなければconfigのパスワード最小文字数を適用してチェックする　（パスワードポリシー対応前のチェック）
//
//        //パスワード文字数Min
//        int passwordCharMin = getConfigNumValue(Item.PASSWORD_CHAR_MIN);
//        //パスワード文字数Max
//        int passwordCharMax = getConfigNumValue(Item.PASSWORD_CHAR_MAX);         
//        //パスワードの文字数確認
//        if (pswd.length() < passwordCharMin) {
//            //errMsg = "パスワードの文字数が短すぎます。[文字数：%s～%s]"
//            return itemHelper.findDispMessageStr(
//                Item.ErrMsgItemKey.PASSWORD_LENGTH_SHORT, funcId,passwordCharMin, passwordCharMax);
//        }            
        
        // パスワード英字最小文字数チェック
        if((numVal = getConfigNumValue(Item.PasswordPolicyItemKey.MINALP_CHECK, Item.PasswordPolicyItemKey.MINALP)) > 0){
            if(getValidCount(pswd, "a-zA-Z", false) < numVal){
                // パスワードには%s文字以上の英字を含める必要があります。
                return itemHelper.findDispMessageStr(ErrMsgItemKey.ERR_PW_MINALP, funcId, numVal, passwordCharMax);
            }
        }
        // パスワード数字最小文字数チェック
        if((numVal = getConfigNumValue(Item.PasswordPolicyItemKey.MINNUM_CHECK, Item.PasswordPolicyItemKey.MINNUM)) > 0){
            if(getValidCount(pswd, "0-9", false) < numVal){
                // パスワードには%s文字以上の数値を含める必要があります。
                return itemHelper.findDispMessageStr(ErrMsgItemKey.ERR_PW_MINNUM, funcId, numVal, passwordCharMax);
            }
        }
        // パスワード記号最小文字数チェック
        if((numVal = getConfigNumValue(Item.PasswordPolicyItemKey.MINMARK_CHECK, Item.PasswordPolicyItemKey.MINMARK)) > 0){
            if(getValidCount(pswd, "^0-9a-zA-Z", false) < numVal){
                // パスワードには%s文字以上の記号を含める必要があります。
                return itemHelper.findDispMessageStr(ErrMsgItemKey.ERR_PW_MINMARK, funcId, numVal, passwordCharMax);
            }
        }
        // パスワード同一文字制限チェック
        if((numVal = getConfigNumValue(Item.PasswordPolicyItemKey.MAXSAMECHAR_CHECK, Item.PasswordPolicyItemKey.MAXSAMECHAR)) > 0){
            if( chkIsOverCountSameChar(pswd, numVal)){
                // パスワードには%s文字以上の同一文字を使うことができません。
                return itemHelper.findDispMessageStr(ErrMsgItemKey.ERR_PW_MAXSAMECHAR, funcId, numVal, passwordCharMax);
            }
        }
        // パスワード除外文字チェック
        String ngStr = getConfigStrValue(Item.PasswordPolicyItemKey.EXCLUDECHAR_CHECK, Item.PasswordPolicyItemKey.EXCLUDECHAR);
        if(!"".equals(ngStr)){
            if( getValidCount(pswd, ngStr, true) > 0){
                // パスワードに使用できない文字が含まれています。
                return itemHelper.findDispMessageStr(ErrMsgItemKey.ERR_PW_EXCLUDECHAR, funcId, ngStr);
            }
        }
        // パスワード履歴チェック
        if(getConfigEnableValue(Item.PasswordPolicyItemKey.HISTORY_CHECK)){
            //入力されたパスワードを暗号化
            String pswdEnc = AuthLogic.getEncryptPassword(userId, pswd);
            //ユーザ情報取得
            BasicUser basicuser = basicUserService.find(userId);
            //履歴数
            numVal = getConfigNumValue(Item.PasswordPolicyItemKey.HISTORY, Item.PasswordPolicyItemKey.HISTORY_COUNT);
            
            //現在のパスワードとの比較
            if(pswdEnc.equals(basicuser.getPassword())){
                if(numVal <= 0)
                    numVal = 1;
                // 過去に同じパスワードが使用されています。
                return itemHelper.findDispMessageStr(ErrMsgItemKey.ERR_PW_HISTORY, funcId, numVal);                
            }
            //履歴数が設定されている場合、パスワード履歴から同一のものがないかチェックする
            if(numVal > 0){
                if(chkExsistsPasswordHistory(basicuser.getUserId(), pswdEnc, numVal)){
                    // 過去に同じパスワードが使用されています。
                    return itemHelper.findDispMessageStr(ErrMsgItemKey.ERR_PW_HISTORY, funcId, numVal);
                }
            }
        }
        
        return "";        
    }


    /**
     * 指定した文字列に指定文字（正規表現）が含まれる数を取得
     * @param targetValue   //調査対象文字列
     * @param regex         //カウント対象文字の正規表現（前後の[]は不要）
     * @param escapeFlg     //正規表現中の文字のエスケープ処理（対象が記号の場合に指定）
     * @return 
     */
    private static int getValidCount(String targetValue, String regex, boolean escapeFlg) {
        final String EscChar = "*+.?{}()[]^$-|\\";
        String ptnStr = "";
        //パターン生成
        if(escapeFlg){
            for(char c: regex.toCharArray()){
                if(EscChar.contains(String.valueOf(c))){
                    ptnStr += "\\";
                }
                ptnStr += c;
            }
        }else{
            ptnStr = regex;
        }
        
        Pattern pattern = Pattern.compile("[" + ptnStr + "]");
        Matcher matcher = pattern.matcher(targetValue);

        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * 同一文字カウントオーバーチェック
     * @param targetValue
     * @param maxCnt
     * @return 
     */
    private boolean chkIsOverCountSameChar(String targetValue, int maxCnt)
    {
        HashMap<String,Integer> map = new HashMap<>();
        //指定文字列を１文字ずつ同一文字数カウントする
        for(char c:targetValue.toCharArray()){
            String s = String.valueOf(c);
            if(map.containsKey(s)){
                if(map.get(s) >= maxCnt){
                    return true;       //MAXに達しているのでオーバーとする
                }
                map.replace(s, map.get(s) + 1); //該当文字数を+1する。
            }else{
                map.put(s, 1);
            }
        }
        return false;
    }
    
    
    /**
     * パスワード設定処理
     * @param basicuser
     * @param passwordEnc   パスワード（暗号化済み）
     */
    @Transactional
    public void setPassword(BasicUser basicuser, String passwordEnc)
    {
        //basicuserの更新　		
	//・パスワード失敗カウント、パスワードリセットフラグ、パスワード失敗日時のクリア
	//・パスワード設定日時に現在日時をセット
        basicuser.setPasswordFaultCount(0);
        basicuser.setPasswordResetFlg(false);
        basicuser.setPasswordFaultDate(null);
        basicuser.setLoginLockFlg(false);
//        basicuser.setPassword(password);
        basicuser.setPasswordSetDate(new Date());
        //userPaswordHistoryの更新			
	//パスワード履歴数設定値>0の場合、以下の処理を行う		
	//・userPasswordHistoryに追加（パスワードには新パスワードを指定）		
	//・当該ユーザIDのレコード数がパスワード履歴数設定値を超える分を古いものから削除
        addPasswordHistory(basicuser.getUserId(), passwordEnc);
    }
    
    /**
     * パスワード警告期限チェック＆メッセージ取得処理
     * @param basicuser
     * @return 警告期限切れ：警告メッセージ、　それ以外：""
     */
    public String chkPasswordWarnLimitAndGetMsg(BasicUser basicuser)
    {
        //パスワード有効期限の確認
        if(basicuser.getPasswordSetDate() != null){
            //パスワード警告期限日数をConfigから取得（パスワード期限が有効の場合）
            int limitDay = getConfigNumValue(Item.PasswordPolicyItemKey.WARNLIMIT_CHECK, Item.PasswordPolicyItemKey.WARNLIMIT_DAY);
            if(limitDay > 0){
                //パスワード設定日時からパスワード警告期限日数経過しているか？
                if(isOverTime(basicuser.getPasswordSetDate(), limitDay, Calendar.DAY_OF_MONTH)){
                    //現在時刻がパスワード設定日時+警告期限日数 を超えている場合、警告必要と判定
                    //「現在のパスワードを設定してから%s日以上経過しています。」
                    return itemHelper.findDispMessageStr(ErrMsgItemKey.ERR_PW_WARNLIMIT, funcId, limitDay);
                }                
            }
        }        
        return "";
    }
    
    /**
     * ログイン成功時処理
     * @param basicuser
     */
    @Transactional
    public void login(BasicUser basicuser)
    {
        LOG.debug("ログインOK　userId:{}, loginName:{}", basicuser.getUserId(), basicuser.getName());
        basicuser.setPasswordFaultCount(0);
        basicuser.setPasswordFaultDate(null);
        basicuser.setPasswordResetFlg(false);
        basicuser.setLastLoginDate(new Date());
        basicUserService.edit(basicuser);
    }
    
    /**
     * パスワード履歴追加
     * @param userId
     * @param passwordEnc   パスワード（暗号化済み）
     * @return 
     */
    @Transactional
    private boolean addPasswordHistory(String userId, String passwordEnc){
        //履歴数を取得（パスワード履歴保存が有効の場合）
        int historyMax = getConfigNumValue(Item.PasswordPolicyItemKey.HISTORY, Item.PasswordPolicyItemKey.HISTORY_COUNT);
        if(historyMax == 0){
            //履歴保存数が取得できないので履歴追加しない
            return false;
        }
        //履歴追加
        UserPasswordHistory rec = new UserPasswordHistory();
        rec.setId(IdUtil.createUUID());
        rec.setUserId(userId);
        rec.setPassword(passwordEnc);
        rec.setUpdateDate(new Date());
        userPasswordHistoryService.edit(rec);
        
        //履歴数設定値を超える履歴を削除（古いものから）
        //削除失敗は無視する（後の処理に悪影響はない）
        try{
            List<UserPasswordHistory> histLst = userPasswordHistoryService.findByUserId(userId);
            if(histLst.size() > historyMax){
                for(int i= historyMax; i < histLst.size(); i++){
                    userPasswordHistoryService.remove(histLst.get(i).getId());
                }
            }
        }catch(Exception ex){
            LOG.error("パスワード履歴の削除に失敗しました。　userId:" + userId, ex);
        }
        return true;
    }


    /**
     * パスワード履歴に指定パスワードと同じものが無いかチェックする
     * @param userId
     * @param passwordEnc    パスワード（暗号化済み）
     * @param historyMax
     * @return 
     */
    private boolean chkExsistsPasswordHistory(String userId, String passwordEnc, int historyMax)
    {
        boolean bRet = false;
        //パスワード履歴リストを取得
        List<UserPasswordHistory> histLst = userPasswordHistoryService.findByUserId(userId);
        for(int i=0; i<historyMax; i++){
            if(i >= histLst.size())
                break;
            if(passwordEnc.equals(histLst.get(i).getPassword())){
                //同じパスワード有り
                bRet = true;
                break;
            }
        }
        return bRet;
    }
    
    /**
     * パスワードポリシー情報リストを取得
     * @return 
     */
    public List<ManagePasswordPolicy> getPasswordPolicyItemList() {
        List<ManagePasswordPolicy> passwordPolicyList= new ArrayList<>();
        
        int itemNo = 0;

        //パスワードの文字数Max
        Item item = itemHelper.find(Item.PASSWORD_CHAR_MAX, Item.FUNC_COMMON);
        int maxLenPasword = Integer.parseInt(item.getValue());
        
        //ユーザID、パスワード同一不可
        passwordPolicyList.add(getItem(
                itemNo++,
                Item.PasswordPolicyItemKey.SAMEID_PW_CHECK,
                null,
                "dspPwPolicySameIdPw",
                null,
                "dspPwPolicySameIdPwNote",
                false, 0, 0));
        //パスワード有効期限
        passwordPolicyList.add(getItem(
                itemNo++,
                Item.PasswordPolicyItemKey.LIMIT_CHECK,
                Item.PasswordPolicyItemKey.LIMIT_DAY,
                "dspPwPolicyLimitDay",
                "dspPwPolicyUnitDay",
                "dspPwPolicyLimitDayNote",
                true, 1, maxNumVal));
        //パスワード期限警告
        passwordPolicyList.add(getItem(
                itemNo++,
                Item.PasswordPolicyItemKey.WARNLIMIT_CHECK,
                Item.PasswordPolicyItemKey.WARNLIMIT_DAY,
                "dspPwPolicyWarnLimitDay",
                "dspPwPolicyUnitDay",
                "dspPwPolicyWarnLimitDayNote",
                true, 1, maxNumVal));
        //パスワード最小文字数
        passwordPolicyList.add(getItem(
                itemNo++,
                Item.PasswordPolicyItemKey.MINLENGTH_CHECK,
                Item.PasswordPolicyItemKey.MINLENGTH,
                "dspPwPolicyMinLength",
                "dspPwPolicyUnitChar",
                "dspPwPolicyMinLengthNote",
                true, 1, maxLenPasword));
        //パスワード英字最小文字数
        passwordPolicyList.add(getItem(
                itemNo++,
                Item.PasswordPolicyItemKey.MINALP_CHECK,
                Item.PasswordPolicyItemKey.MINALP,
                "dspPwPolicyMinAlp",
                "dspPwPolicyUnitChar",
                "dspPwPolicyMinAlpNote",
                true, 1, maxLenPasword));
        //パスワード数字最小文字数
        passwordPolicyList.add(getItem(
                itemNo++,
                Item.PasswordPolicyItemKey.MINNUM_CHECK,
                Item.PasswordPolicyItemKey.MINNUM,
                "dspPwPolicyMinNum",
                "dspPwPolicyUnitChar",
                "dspPwPolicyMinNumNote",
                true, 1, maxLenPasword));
        //パスワード記号最小文字数
        passwordPolicyList.add(getItem(
                itemNo++,
                Item.PasswordPolicyItemKey.MINMARK_CHECK,
                Item.PasswordPolicyItemKey.MINMARK,
                "dspPwPolicyMinMark",
                "dspPwPolicyUnitChar",
                "dspPwPolicyMinMarkNote",
                true, 1, maxLenPasword));
        //パスワード同一文字制限
        passwordPolicyList.add(getItem(
                itemNo++,
                Item.PasswordPolicyItemKey.MAXSAMECHAR_CHECK,
                Item.PasswordPolicyItemKey.MAXSAMECHAR,
                "dspPwPolicyMaxSameChar",
                "dspPwPolicyUnitChar",
                "dspPwPolicyMaxSameCharNote",
                true, 1, maxLenPasword));
        //パスワード除外文字
        passwordPolicyList.add(getItem(
                itemNo++,
                Item.PasswordPolicyItemKey.EXCLUDECHAR_CHECK,
                Item.PasswordPolicyItemKey.EXCLUDECHAR,
                "dspPwPolicyExcludeChar",
                null,
                "dspPwPolicyExcludeCharNote",
                false, 0, 0));
        //パスワード履歴数
        passwordPolicyList.add(getItem(
                itemNo++,
                Item.PasswordPolicyItemKey.HISTORY,
                Item.PasswordPolicyItemKey.HISTORY_COUNT,
                "dspPwPolicyHistoryCount",
                "dspPwPolicyUnitCount",
                "dspPwPolicyHistoryCountNote",
                true, 1, maxNumVal));
        //パスワード履歴チェック
        passwordPolicyList.add(getItem(
                itemNo++,
                Item.PasswordPolicyItemKey.HISTORY_CHECK,
                null,
                "dspPwPolicyHistoryCheck",
                null,
                "dspPwPolicyHistoryCheckNote",
                false, 0, 0));
        //ログインロック試行回数
        passwordPolicyList.add(getItem(
                itemNo++,
                Item.PasswordPolicyItemKey.LOGINLOCK,
                Item.PasswordPolicyItemKey.LOCK_COUNT,
                "dspPwPolicyLockCount",
                "dspPwPolicyUnitCount",
                "dspPwPolicyLockCountNote",
                true, 1, maxNumVal));
        //ログインロック解除時間
        passwordPolicyList.add(getItem(
                itemNo++,
                Item.PasswordPolicyItemKey.LOCKCLEAR,
                Item.PasswordPolicyItemKey.LOCKCLEAR_TIME,
                "dspPwPolicyLockClearTime",
                "dspPwPolicyUnitHour",
                "dspPwPolicyLockClearTimeNote",
                true, 1, maxNumVal));
        
        //ログイン後、直ぐにパスワードを変更
        //この項目は他項目と異なり有効の場合にconfigに最終実施日を登録する項目。
        passwordPolicyList.add( getItem(
                itemNo++,
                null,
                Item.PasswordPolicyItemKey.RESETDATE,
                "dspPwPolicyReset",
                "dspPwPolicyProcessingDate",
                "dspPwPolicyResetNote",
                false, 0, 0));
        
        return passwordPolicyList;
    }

    /**
     * パスワードポリシーデータ 生成
     * @param itemNo    項目番号
     * @param checkItem 有効無効設定用configキー
     * @param valueItem 設定値用configキー
     * @param titleKey  項目名表示用dispMessageキー
     * @param unitKey   単位表示用dispMessageキー
     * @param commentKey    説明文表示用dispMessageキー
     * @param isNumeric 数値項目かどうか
     * @param minNumVal
     * @param maxNumVal
     * @return 
     */
    private ManagePasswordPolicy getItem(
            int itemNo,
            Item.PasswordPolicyItemKey checkItem,
            Item.PasswordPolicyItemKey valueItem,
            String titleKey, String unitKey, String commentKey,
            boolean isNumeric, int minNumVal, int maxNumVal) {

        //有効/無効のconfigを取得
        Config itemConfig;
        if(checkItem != null)
            itemConfig = configService.findByKey(checkItem.getItemKey(), Item.FUNC_COMMON);
        else{
            itemConfig = new Config();
            itemConfig.setItemKey("dmy");
            itemConfig.setItemValue("false");
        }
        
        //設定値のconfigを取得
        Config valueConfig = null;
        if(valueItem != null){
            valueConfig = configService.findByKey(valueItem.getItemKey(), Item.FUNC_COMMON);
        }
        //項目名称、単位、説明文をdispMessageから取得する
        String title = (titleKey != null ? itemHelper.findDispMessageStr(titleKey, funcId) : "");
        String unit = (unitKey != null ? itemHelper.findDispMessageStr(unitKey, funcId) : "");
        String comment = (commentKey != null ? itemHelper.findDispMessageStr(commentKey, funcId) : "");
        
        return new ManagePasswordPolicy(
                itemNo,
                itemConfig,
                valueConfig,
                title,
                unit,
                comment,                
                isNumeric,
                minNumVal,
                maxNumVal
        );
    }
        
    /**
     * パスワードポリシー情報リストから指定キーのデータを取得
     * @param passwordPolicyList
     * @param key
     * @return 
     */
    public ManagePasswordPolicy getPasswordPolicyItem(List<ManagePasswordPolicy> passwordPolicyList, Item.PasswordPolicyItemKey key){
        for(ManagePasswordPolicy pp :passwordPolicyList){
            if(pp.getItemKey().equals(key.getItemKey()) || pp.getValueKey().equals(key.getItemKey()))
                return pp;
        }
        return null;
    }    

    /**
     * 設定値がtrueかどうか
     * @param itemKey
     * @return 
     */
    public boolean getConfigEnableValue(Item.PasswordPolicyItemKey itemKey){
        String val = getConfigStrValue(itemKey);
        return (val.compareToIgnoreCase("true") == 0);
    }
    
    /**
     * 設定値を文字列で取得(無効の場合は空文字を返す）
     * @param boolKey   有効/無効キー
     * @param valueKey  設定値キー
     * @return 有効の場合は設定値（文字列）。無効の場合は""
     */
    public String getConfigStrValue(Item.PasswordPolicyItemKey boolKey, Item.PasswordPolicyItemKey valueKey){
        if(!getConfigEnableValue(boolKey))
            return "";
        return getConfigStrValue(valueKey);
    }

    /**
     * 設定値を文字列で取得
     * @param valueKey  設定値キー
     * @return 
     */
    public String getConfigStrValue(Item.PasswordPolicyItemKey valueKey){
        return getConfigStrValue(valueKey.getItem());
    }

    /**
     * 設定値を文字列で取得(config汎用）
     * @param valueItem  設定値configアイテム
     * @return 
     */
    public String getConfigStrValue(Item valueItem){
        Config config = configService.findByKey(valueItem.getKey(), Item.FUNC_COMMON);
        if(config == null)
            return "";
        String val = config.getItemValue();
        return (val == null ? "" : val );
    }
    
    /**
     * 設定値を数値型で取得(無効の場合は0を返す）
     * @param boolKey   有効/無効キー
     * @param valueKey  設定値キー
     * @return 有効の場合は設定値（数値）。無効の場合は""
     */
    public int getConfigNumValue(Item.PasswordPolicyItemKey boolKey, Item.PasswordPolicyItemKey valueKey){
        if(!getConfigEnableValue(boolKey))
            return 0;
        return getConfigNumValue(valueKey);
    }
    
    /**
     * 設定値を数値型で取得
     * @param valueKey  設定値キー
     * @return 
     */
    public int getConfigNumValue(Item.PasswordPolicyItemKey valueKey){
        return getConfigNumValue(valueKey.getItem());
    }
    
    /**
     * 設定値を数値型で取得(config汎用）
     * @param valueItem  設定値configアイテム
     * @return 
     */
    public int getConfigNumValue(Item valueItem){
        String val = getConfigStrValue(valueItem);
        int iVal = 0;
        try{
            iVal = Integer.parseInt(val);
        }catch(Exception e){}
        return iVal;
    }    
    
    /**
     * 現在日時が（指定日付＋追加時間）を超過しているかどうかの判定
     * @param srcDate   指定日付
     * @param addTime   追加時間   
     * @param calendarField 追加時間フィールド（時：Calendar.HOUR/ 日：Calendar.DAY_OF_MONTH)
     * @return True:超過している / False:超過していない
     */
    private boolean isOverTime(Date srcDate, int addTime, int calendarField)
    {
        if(srcDate == null)
            return false;
        Calendar cal = Calendar.getInstance();
        cal.setTime(srcDate);   //指定日時
        cal.add(calendarField, addTime);  //指定日時＋追加時間
        
        //現在時刻が(指定日時＋追加時間) を超えているかどうかを返す
        return Calendar.getInstance().after(cal);
    }
    
    /**
     * パスワードポリシー関連のエラーメッセージ取得
     * ※外部から取得必要なメッセージがあれば随時追加
     * @param errMsgKey
     * @param funcId    ファンクションID（共通の場合はnull,空文字を指定でも可）
     * @return 
     */
    public String getPasswordPolicyErrMsg(Item.ErrMsgItemKey errMsgKey, String funcId)
    {
        String errMsg = "";
        if(funcId == null || funcId.isEmpty())
            funcId = Item.FUNC_COMMON;
        switch(errMsgKey){
            case ERR_LOGIN_LOCK:    //ログインロックされている
            case ERR_PW_LOGINLOCK:  //ログインロックされた
                //ログインロック試行回数をConfigから取得(ログインロックが有効の場合）
                int lockCountMax = getConfigNumValue(Item.PasswordPolicyItemKey.LOGINLOCK, Item.PasswordPolicyItemKey.LOCK_COUNT);
                errMsg = itemHelper.findDispMessageStr(errMsgKey, funcId, lockCountMax);
                break;
            default:
                errMsg = itemHelper.findDispMessageStr(errMsgKey, funcId);
                break;
        }
        return errMsg;
    }
    
    /**
     * パスワードポリシー説明文取得
     * @return 
     */
    public String getPasswordPolicyNote()
    {
        //コンフィグからレコード取得
        Config config = configService.findByKey(
                Item.PasswordPolicyItemKey.POLICY_NOTE.getItemKey(),Item.FUNC_COMMON);
        if(config == null)
            return "";
        //設定値を返す
        return config.getItemValue();
    }
    
    /**
     * パスワードポリシー説明文設定
     * @param note パスワードポリシー説明文
     */
    @Transactional
    public void setPasswordPolicyNote(String note)
    {
        //コンフィグからレコード取得
        Config config = configService.findByKey(
                Item.PasswordPolicyItemKey.POLICY_NOTE.getItemKey(),Item.FUNC_COMMON);
        if(config == null)
            return;

        //設定値をセット
        config.setItemValue(note);
        //更新
        configService.edit(config);
    }
}
