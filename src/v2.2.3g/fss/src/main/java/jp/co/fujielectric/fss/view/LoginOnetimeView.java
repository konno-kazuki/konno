package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.data.OnetimeBean;
import jp.co.fujielectric.fss.exception.FssLoginException;
import jp.co.fujielectric.fss.logic.AuthLogic;
import jp.co.fujielectric.fss.logic.ItemHelper;
import jp.co.fujielectric.fss.util.CommonUtil;
import lombok.Getter;
import lombok.Setter;

/**
 * LoginOnetimeView (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@RequestScoped
@SuppressWarnings("serial")
public class LoginOnetimeView extends CommonView implements Serializable {

    @Inject
    private OnetimeBean onetimeBean;

    @Inject
    private AuthLogic authLogic;

    @Inject
    protected ItemHelper itemHelper;

    @Getter
    @Setter
    private String onetimeId;

    @Getter
    @Setter
    private String password;

    @Getter
    @Setter
    private String target;

    @Getter
    protected long passwordCharMax;         //パスワード文字数Max

    public LoginOnetimeView() {
        funcId = "loginOnetime";
    }

    /**
     * マスタ設定値からの変数初期化
     *
     */
    @Override
    protected void initItems() {
        //パスワード文字数Max
        passwordCharMax = CommonUtil.getPasswordCharMax(itemHelper, funcId);
    }

    @PostConstruct
    public void initParam() {
        if (onetimeBean != null) {
            if (onetimeBean.getOnetimeId() != null) {
                onetimeId = onetimeBean.getOnetimeId();
            }
            if (onetimeBean.getTarget() != null) {
                target = onetimeBean.getTarget();
            }
        }
    }

    public String login() {
        String ret = null;

        LOG.debug(onetimeId + ":" + password + "=>" + target);
        try {
            if (authLogic.loginOnetime(onetimeId, password)) {
                LOG.trace("ワンタイムログイン成功");

                ret = target;
            }
        } catch(FssLoginException e) {
            LOG.trace("ワンタイムログイン失敗");
            String errMsg = "";

            // エラーの出力
            if(e.getCode() == null) {
                e.printStackTrace();
            }else switch(e.getCode()) {
                case ALREADY_LOGGED_IN:
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.LOGIN_ALREADY, funcId);
                    break;
                case NOTHING_PASSWORD:
                case EARLY_ACCESS:
                case EXPIRED_ACCESS:
                case UNMATCH_PASWORD:
                case NOTHING_USER:
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ONETIME_LOGIN_FAILED, funcId);
                    break;
                default:
                    LOG.warn("Unexpected route case");
                    e.printStackTrace();
                    break;
            }
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    errMsg,
                    errMsg));
            FacesContext.getCurrentInstance().renderResponse();
        }

        return ret;
    }
}
