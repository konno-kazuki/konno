package jp.co.fujielectric.fss.common;

import java.util.List;
import java.io.Serializable;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.entity.OnceUser;
import jp.co.fujielectric.fss.logic.AuthLogic;
import jp.co.fujielectric.fss.data.OnetimeBean;
import jp.co.fujielectric.fss.entity.Menu;
import jp.co.fujielectric.fss.exception.FssLoginException;
import jp.co.fujielectric.fss.service.MenuService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.DateUtil;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;

/**
 * 遷移先情報管理Bean
 * pretty-config.xmlでマッチしたURLをここで解析し、該当ページに飛ばす
 * @author nakai
 */
@Named
@RequestScoped
@SuppressWarnings("serial")
public class RoutingBean implements Serializable {
    @Inject
    Logger LOG;

    @Inject
    private AuthLogic authLogic;

    @Inject
    private OnetimeBean onetimeBean;

    @Inject
    MenuService menuService;
    
    @Getter @Setter
    private String path;

    @Getter @Setter
    private String id;

    @Getter @Setter
    private String regionParam;

    @Getter @Setter
    private String onetimeParam;

    @Getter @Setter
    private String target;

    public String root() {
        LOG.trace("root");

        if(!authLogic.checkRegion()) {
            return "/notfound.xhtml";
        }
        if(!authLogic.checkPermission(path)) {
            LOG.warn("権限チェック：エラー");
            return "/conflict.xhtml";
        }

        return "/" + path;
    }

    /**
     * メールからの転送
     * @return path
     */
    public String mail() {
        LOG.trace("mail() start:" + path);

        if(!authLogic.setRegion(regionParam)) {
            return "/notfound.xhtml";
        }

        return "/" + path;
    }

    /**
     * resourceへの通信（データ同期）
     * @return path
     */
    public String resource() {
        LOG.trace("resource() start:" + path);

        if(!authLogic.setRegion(regionParam)) {
            return "/notfound.xhtml";
        }

        return "/webresources/" + path;
    }

    public String routeAction() {
        LOG.trace("routeAction");

        // リージョンチェック
        if( !authLogic.checkSameRegion(regionParam) ) {
            LOG.trace("リージョンエラー");
            return "/conflict.xhtml";
        }

        if(!authLogic.setRegion(regionParam)) {
            return "/notfound.xhtml";
        }

        // ログインチェック
        if( !authLogic.checkBasicLogin() ) {
            // 未ログイン
            target = "login";
            return null;
        }

        if( path == null ) {
            target = "portal";
            return null;
        }

        return null;
    }

    public String onetimeRouteAction() {
        LOG.trace("onetimeRouteAction");

        // リージョンチェック
        if( !authLogic.checkSameRegion(regionParam) ) {
            LOG.trace("リージョンエラー");
            return "/conflict.xhtml";
        }

        if(!authLogic.setRegion(regionParam)) {
            return "/notfound.xhtml";
        }

        // ワンタイムユーザの取得
        String onetimeId = authLogic.decodeOnetime(onetimeParam);
        OnceUser onceUser = authLogic.findOnetimeUser(onetimeId);
        if(onceUser == null) {
            LOG.trace("ワンタイムユーザ不明");
            String oldLink = getOldLinkURL();
            //該当するワンタイムユーザが見つからない場合
            //旧システムへのリンクが登録されていれば旧システムへのリンクとする[v2.2.3]
            if(!Strings.isBlank(oldLink)){
                onetimeBean.setOnetimeId(onetimeParam);
                onetimeBean.setTarget(oldLink + onetimeParam + "/");
                LOG.debug("#Onetime OldLink. OnetimeId:{}, OldLink:{}, URL:{}",onetimeBean.getOnetimeId(), oldLink, onetimeBean.getTarget());
                return "loginOnetimeOldLink";                
            }
            return "/notfound.xhtml";
        }
        if( onceUser.getExpirationTime() == null || DateUtil.getSysDate().compareTo(onceUser.getExpirationTime()) > 0 ) {
            LOG.trace("ワンタイムユーザ有効期限切れ");
            return "/notfound.xhtml";
        }

        // パスワード有無 or ログイン済チェック
        if( (onceUser.getPassword() != null && !onceUser.getPassword().equals("")) ) {
            LOG.trace("パスワード有り");
            if( !authLogic.checkOnetimeLogin(onetimeId) ) {
                LOG.trace("未ログイン => loginOnetime");
                target = "loginOnetime";
            } else {
                LOG.trace("ログイン済 => " + onceUser.getTarget());
                target = onceUser.getTarget();
            }
        } else {
            LOG.trace("パスワード無し => " + onceUser.getTarget());
            try{
                if (authLogic.loginOnetime(onetimeId) ) {
                    target = onceUser.getTarget();
                }
            } catch(FssLoginException e) {}
        }

        // 通常ログイン時、ワンタイムIDによる異なるIDのパスワード変更は許可しない
        if( onceUser.getTarget() != null && onceUser.getTarget().equals("userPasswordSet") ) {
            // 通常ログインされていて、ワンタイムIDが指定するIDではないか　確認
            if(authLogic.checkBasicLogin() && !authLogic.checkBasicLogin(onceUser.getMailId())) {
                return "/conflict.xhtml";
            }
        }

        // targetに応じた事前処理
        if( target != null && target.equals("loginOnetime") ) {
            // ワンタイムログイン画面
            onetimeBean.setOnetimeId(onceUser.getOnetimeId());
            onetimeBean.setTarget(onceUser.getTarget());
        }

        return null;
    }

    /**
     * 旧システムリンクの取得
     */
    public String getOldLinkURL()
    {
        List<Menu> _portal = menuService.findByPermission(10, "unknown", CommonUtil.getSetting("section"));
        for (Menu var : _portal) {
            if(var.getPosition() == 9){
                return var.getLink();
            }
        }
        return null;
    }
}
