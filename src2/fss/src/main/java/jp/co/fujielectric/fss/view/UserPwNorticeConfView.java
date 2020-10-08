package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.util.List;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.logic.MailManager;
import jp.co.fujielectric.fss.data.ManageIdBean;
import jp.co.fujielectric.fss.logic.UserPwNorticeBean;
import lombok.Getter;
import lombok.Setter;

/**
 * パスワード設定通知確認ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class UserPwNorticeConfView extends CommonView implements Serializable {

    @Inject
    private MailManager mailManager;

    @Inject
    private UserPwNorticeBean userPwNorticeBean;

    @Getter
    @Setter
    private List<ManageIdBean> selectedItems;

//    @Getter
//    private String confirmMessage = "";

    @Getter
    private boolean isExecDone = false;    //処理済みかどうか

    private String inputUrl;   //入力画面URL
    private String listFilter;
    private int first;
    private int rows;
    private int currentPage;

    //コンストラクタ
    public UserPwNorticeConfView() {
        funcId = "userPwNorticeConf";
    }

    /**
     * 画面区分毎の初期化
     *
     */
    @Override
    protected void initFunc() {
        //URL
        inputUrl = "userPwNortice";

//        //確認メッセージ
//        //パスワード設定通知メールを選択したユーザー宛てに送付します。よろしいですか？
//        confirmMessage = itemHelper.findDispMessageStr(Item.ConfirmMsgItemKey.CONFIRM_NORTICE_PW_SEND, funcId);

        //前画面からの情報引き継ぎ
        //一覧
        selectedItems = userPwNorticeBean.getManageIdList();
        listFilter = userPwNorticeBean.getListFilter();
        first = userPwNorticeBean.getFirst();
        rows = userPwNorticeBean.getRows();
        currentPage = userPwNorticeBean.getCurrentPage();
    }

    /**
     * マスタ設定値からの変数初期化
     *
     */
    @Override
    protected void initItems() {
        //特になし
    }

    /**
     * 実行
     *
     * @return 実行後遷移URL
     */
    public String execAction() {
        LOG.debug("eventExec:");
        String url = "";

        //処理実行
        int ret;

        ///パスワード設定通知
        try {
            //メール送信
            for (ManageIdBean bean : selectedItems) {
                mailManager.sendMailPasswdSet(bean);
            }
            // 成功
            ret = 0;
        } catch (Exception ex) {
            ret = -1;
        }

        if (ret != 0) {
            ///処理に失敗した場合
            //メッセージ
            String errItem = "";
            FacesContext context = FacesContext.getCurrentInstance();
            String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.FAILED_NORTICE_PW_SEND, funcId);
            FacesMessage msg = new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg);
            context.addMessage(null, msg);
            //RequestContext.getCurrentInstance().addCallbackParam("msg", msg.getSummary() + msg.getDetail());
        } else {
            ///処理に成功した場合
            setBeanForUrlMove();
            url = inputUrl;
        }
        isExecDone = true;  //処理済み

        return url;
    }

    /**
     * 戻る
     *
     * @return 遷移先URL
     */
    public String prevAction() {
        setBeanForUrlMove();
        return inputUrl;
    }

    /**
     * 画面遷移時のBeanへのデータ受渡し
     *
     */
    protected void setBeanForUrlMove() {
        userPwNorticeBean.setManageIdList(selectedItems);
        userPwNorticeBean.setListFilter(listFilter);
        userPwNorticeBean.setFirst(first);
        userPwNorticeBean.setRows(rows);
        userPwNorticeBean.setCurrentPage(currentPage);
    }
}
