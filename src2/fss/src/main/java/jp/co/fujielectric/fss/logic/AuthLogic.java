package jp.co.fujielectric.fss.logic;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import jp.co.fujielectric.fss.data.CommonBean;
import java.util.Date;
import java.util.List;
import javax.enterprise.context.RequestScoped;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpSession;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.EntityManagerProducer;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.entity.BasicUser;
import jp.co.fujielectric.fss.entity.OnceUser;
import jp.co.fujielectric.fss.entity.UserType;
import jp.co.fujielectric.fss.entity.UserTypePermission;
import jp.co.fujielectric.fss.exception.FssLoginException;
import jp.co.fujielectric.fss.service.BasicUserService;
import jp.co.fujielectric.fss.service.OnceUserService;
import jp.co.fujielectric.fss.service.UserTypePermissionService;
import jp.co.fujielectric.fss.service.UserTypeService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.FileUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

/**
 * 認証ロジック ログイン、ログアウトを管理するロジック commonBeanに対する操作を行う
 *
 * @author nakai
 */
@Named
@RequestScoped
@SuppressWarnings("serial")
public class AuthLogic {

    @Inject
    Logger LOG;

    @Inject
    private CommonBean commonBean;

    @Inject
    private BasicUserService basicUserService;

    @Inject
    private OnceUserService onceUserService;

    @Inject
    private UserTypePermissionService userTypePermissionService;

    @Inject
    protected ItemHelper itemHelper;

    @Inject
    private UserTypeService userTypeService;

    @Inject
    private PasswordPolicyLogic passwordPolicyLogic;

    /**
     * ログインチェック
     *
     * @param userId
     * @param password
     * @return ログイン結果
     * @throws jp.co.fujielectric.fss.exception.FssLoginException
     */
    @Transactional
    public boolean loginCheck(String userId, String password) throws FssLoginException {
        LOG.trace("login");

        // ログイン状態確認
        if (checkBasicLogin(userId)) {
            LOG.trace("ログインエラー：ログイン済");
            throw new FssLoginException(FssLoginException.Code.ALREADY_LOGGED_IN);
        }

        // ユーザ情報の取得
        BasicUser user = basicUserService.find(userId);
        if (user == null) {
            LOG.trace("ログインエラー：ユーザ未登録");
            throw new FssLoginException(FssLoginException.Code.NOTHING_USER);
        }

        if (user.getStartTime() == null || DateUtil.getSysDate().compareTo(user.getStartTime()) < 0) {
            LOG.trace("ログインエラー：有効期限以前");
            throw new FssLoginException(FssLoginException.Code.EARLY_ACCESS);
        }

        if (user.getEndTime() == null || DateUtil.getSysDate().compareTo(user.getEndTime()) > 0) {
            LOG.trace("ログインエラー：有効期限切れ");
            throw new FssLoginException(FssLoginException.Code.EXPIRED_ACCESS);
        }

        if (user.getPassword() == null || user.getPassword().equals("")) {
            LOG.trace("ログインエラー：パスワード未登録");
            throw new FssLoginException(FssLoginException.Code.NOTHING_PASSWORD);
        }

        //ログインロックリセット処理（パスワードポリシー対応）
        passwordPolicyLogic.resetLoginLock(user);
        
        //ログインロックされている場合（パスワードポリシー対応）
        if(user.isLoginLockFlg()){
            LOG.debug("ログインロックのためログイン不可 userId:{}", userId);
            String errMsg = passwordPolicyLogic.getPasswordPolicyErrMsg(Item.ErrMsgItemKey.ERR_LOGIN_LOCK, "");
            throw new FssLoginException(FssLoginException.Code.LOGIN_LOCK, errMsg);
        }
        
        //パスワードチェック
        if (!checkBasicUserPassword(userId, password)) {
            //---------------------------    
            //パスワード不一致の場合の処理
            //---------------------------    
            LOG.trace("ログインエラー：パスワード不一致 userId:{}", userId);
            
            //パスワードミス処理（パスワードポリシー対応）
            passwordPolicyLogic.setPasswordMiss(user);
            
            //ログインロックされた場合
            if(user.isLoginLockFlg()){
                //ログインロックをスロー
                LOG.debug("ログインロックされました。 userId:{}", userId);
                String errMsg = passwordPolicyLogic.getPasswordPolicyErrMsg(Item.ErrMsgItemKey.ERR_PW_LOGINLOCK, "");
                throw new FssLoginException(FssLoginException.Code.PW_LOGIN_LOCKED, errMsg);
            }
            //パスワード不一致をスロー
            throw new FssLoginException(FssLoginException.Code.UNMATCH_PASWORD);
        }
        
        return true;
    }
    
    /**
     * ログイン
     *
     * @param user
     * @param loginFlg
     */
    public void login(BasicUser user, boolean loginFlg){
        // 情報の格納
        commonBean.setLoginFlg(loginFlg);
        commonBean.setUserId(user.getUserId());
        commonBean.setUserType(user.getUserType());
        commonBean.setLoginName(user.getName());
        commonBean.setMailAddress(user.getMailAddress());
        commonBean.setUserTypeInternalFlg(isUserTypeInternalflg(user.getUserType()));
        if(loginFlg){
            //ログイン時処理（パスワードポリシー対応）
            passwordPolicyLogic.login(user);
        }
    }

    /**
     * ログイン
     * @param userId 
     */
    public void login(String userId){
        login(basicUserService.find(userId),true);
    }
    
    /**
     * 内部ユーザ判定フラグ
     *
     * @param userType
     * @return true(内部ユーザ)、false(外部ユーザ)
     */
    public boolean isUserTypeInternalflg(String userType) {
        boolean internalflg = false;
        
        List<UserType> userTypes = userTypeService.findAll();
        if (userTypes != null) {
            for (UserType u : userTypes) {
                if (u.getId().equals(userType)) {
                    internalflg = u.isInternalflg();
                }
            }
        }
        return internalflg;
    }

    /**
     * ログアウト
     *
     * @return ログアウト結果
     */
    public boolean logout() {
//        LOG.trace("ログアウト：ログイン状態確認");
//        if( !commonBean.isLoginFlg() ) {
//            LOG.trace("ログアウトエラー：未ログイン");
//            return false;
//        }

        LOG.trace("ログアウト：情報クリア");
        HttpSession session = (HttpSession) FacesContext.getCurrentInstance().getExternalContext().getSession(false);
        if (session != null) {
            session.invalidate();
        }

        return true;
    }

    /**
     * ログインチェック（ID不問）
     *
     * @return true/false
     */
    public boolean checkBasicLogin() {
        // ログイン中であり、ユーザーIDが空白でないこと
        return commonBean.isLoginFlg() && commonBean.getUserId() != null && !commonBean.getUserId().equals("");
    }

    /**
     * ログインチェック（ID合致）
     *
     * @param userId
     * @return true/false
     */
    public boolean checkBasicLogin(String userId) {
        // ログイン中であり、ユーザーIDが空白でなく、一致していること
        return commonBean.isLoginFlg() && commonBean.getUserId() != null && !commonBean.getUserId().equals("") && commonBean.getUserId().equals(userId);
    }

    /**
     * ユーザーパスワードチェック
     *
     * @param userId
     * @param password
     * @return チェック結果
     */
    public boolean checkBasicUserPassword(String userId, String password) {
        BasicUser user = basicUserService.find(userId);
        if (user == null || user.getPassword() == null) {
            return false;
        }

        //入力されたパスワードを暗号化
        String _password = getEncryptPassword(userId, password);

        return user.getPassword().equals(_password);
    }

    /**
     * ユーザメールアドレスチェック
     *
     * @param userId
     * @param mailAddress
     * @return チェック結果
     */
    public boolean checkBasicUserMailAddress(String userId, String mailAddress) {
        BasicUser user = basicUserService.find(userId);
        if (user == null || user.getMailAddress() == null) {
            return false;
        }
        return user.getMailAddress().equals(mailAddress);
    }
    
    /**
     * ユーザーパスワード変更
     *
     * @param userId
     * @param passwordNew
     * @param onetimeId
     * @return 変更結果
     */
    @Transactional
    public boolean modifyBasicUserPassword(String userId, String passwordNew, String onetimeId) {
        //BasicUser取得
        BasicUser user = basicUserService.find(userId);
        if (user == null || passwordNew == null) {
            return false;
        }

        //パスワードは暗号化して登録
        String _passwordNew = getEncryptPassword(userId, passwordNew);

        // パスワードの変更
        user.setPassword(_passwordNew);
        //パスワードポリシー関連更新
        passwordPolicyLogic.setPassword(user, _passwordNew);
        //更新
        basicUserService.edit(user);

        List<OnceUser> OnceUsers = onceUserService.getEffectiveInfo("userPasswordSet", new Date());
        for(OnceUser onceUser : OnceUsers) {
            if(onceUser.getMailId().equals(userId)) {
                onceUser.setExpirationTime(new Date());
                onceUserService.edit(onceUser);
            }
        }

        return true;
    }

    /**
     * ワンタイムログイン
     *
     * @param userId
     * @return ログイン結果
     */
    public boolean loginOnetime(String userId) throws FssLoginException {
        return loginOnetime(userId, "");
    }

    /**
     * ワンタイムログイン
     *
     * @param onetimeId
     * @param password
     * @return ログイン結果
     */
    public boolean loginOnetime(String onetimeId, String password) throws FssLoginException {
        LOG.trace("loginOnetime");

        // ユーザ情報の取得
        OnceUser user = onceUserService.find(onetimeId);
        if (user == null) {
            LOG.trace("ワンタイムログインエラー：ユーザ未登録");
            throw new FssLoginException(FssLoginException.Code.NOTHING_USER);
        }

        if (user.getExpirationTime() == null || DateUtil.getSysDate().compareTo(user.getExpirationTime()) > 0) {
            LOG.trace("ワンタイムログインエラー：有効期限切れ");
            throw new FssLoginException(FssLoginException.Code.EXPIRED_ACCESS);
        }

        if ((user.getPassword() != null && !user.getPassword().equals("")) && !user.getPassword().equals(password)) {
            LOG.trace("ワンタイムログインエラー：パスワード不一致");
            throw new FssLoginException(FssLoginException.Code.UNMATCH_PASWORD);
        }

        // 情報の格納
        commonBean.setOnetimeId(onetimeId);
        if (!checkBasicLogin()) {
            // 通常ログインしてない場合のみ
            //  ログインユーザ名をメールアドレスで設定
            commonBean.setLoginName(user.getMailAddress());
            //  ユーザタイプをワンタイム用に設定
            commonBean.setUserType("onetime");
        }
        return true;
    }

    /**
     * ワンタイムログインチェック（ID不問）
     *
     * @return true/false
     */
    public boolean checkOnetimeLogin() {
        // ワンタイムIDが空白でないこと
        return commonBean.getOnetimeId() != null && !commonBean.getOnetimeId().equals("");
    }

    /**
     * ワンタイムログインチェック（ID合致）
     *
     * @param onetimeId
     * @return true/false
     */
    public boolean checkOnetimeLogin(String onetimeId) {
        // ワンタイムIDが空白でなく、一致していること
        return commonBean.getOnetimeId() != null && !commonBean.getOnetimeId().equals("") && commonBean.getOnetimeId().equals(onetimeId);
    }

    /**
     * ワンタイムユーザー取得
     *
     * @param onetimeId
     * @return OnceUser
     */
    public OnceUser findOnetimeUser(String onetimeId) {
        LOG.trace("onetimeID = " + onetimeId);
        return onceUserService.find(onetimeId);
    }

    /**
     * ワンタイムIDデコード
     *
     * @param onetime
     * @return onetimeId
     */
    public String decodeOnetime(String onetime) {
        return new String(Base64.decodeBase64(onetime));
    }

    /**
     * リージョンチェック
     * @return 正常判定
     */
    public boolean checkRegion() {
        // リージョンが正しく設定されているかを確認
        // EntityManagerが取得できなければエラー
        return EntityManagerProducer.getEntityManager(commonBean.getRegionId()) != null;
    }

    /**
     * リージョン横断チェック
     *
     * @param region リージョン情報
     * @return 正常判定
     */
    public boolean checkSameRegion(String region) {
        LOG.trace("checkSameRegion");

        // デコード
        String regionId = CommonUtil.decodeBase64(region);

        LOG.trace(regionId + ":" + commonBean.getRegionId());

        if (commonBean.getRegionId() == null) {
            // 未設定であれば許可
            return true;
        }
        // リージョンが同一であるかどうか
        return commonBean.getRegionId().equals(regionId);
    }

    /**
     * リージョンセッター
     *
     * @param region リージョン情報
     */
    public boolean setRegion(String region) {
        LOG.trace("setRegionId:" + region);
        // デコード
        String regionId = CommonUtil.decodeBase64(region);
        // EntityManagerが取得できなければエラー
        if(EntityManagerProducer.getEntityManager(regionId) == null) {
            return false;
        }
        commonBean.setRegionId(regionId);
        return true;
    }

    /**
     * リージョンゲッター
     *
     * @return regionId リージョン情報
     */
    public String getRegion() {
        LOG.trace("getRegionId");
        // エンコード
        return CommonUtil.encodeBase64(commonBean.getRegionId());
    }

    /**
     * 権限チェック
     *
     * @param path アクセスパス
     * @return 操作権限
     */
    public boolean checkPermission(String path) {
        // 対象パスから拡張子を除く
        String link = FileUtil.getFNameWithoutSuffix(path);

        LOG.trace(link + "," + commonBean.getUserType() + "," + CommonUtil.getSetting("section"));

        // 対象リンクに対して権限があるかを確認
        UserTypePermission utp = userTypePermissionService.findByUnique(link, commonBean.getUserType(), CommonUtil.getSetting("section"));
        return utp != null;
    }

    private static final int PSWD_STRETCH_COUNT = 1000;

    /**
     * salt + ストレッチングしたパスワードを取得
     *
     * @param userId
     * @param password
     * @return
     */
    public static String getEncryptPassword(String userId, String password) {
        String salt = getSha256(userId);
        String hash = "";

        for (int i = 0; i < PSWD_STRETCH_COUNT; i++) {
            hash = getSha256(hash + salt + password);
        }

        return hash;
    }

    /**
     * 文字列から SHA256 のハッシュ値を取得
     * @param target
     * @return 
     */
    public static String getSha256(String target) {
        String hashedStr = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(target.getBytes());
            BigInteger bi = new BigInteger(1, hash);
            hashedStr = String.format("%0" + (hash.length << 1) + "x", bi);

        } catch (NoSuchAlgorithmException e) {
            //
        }
        return hashedStr;
    }
}
