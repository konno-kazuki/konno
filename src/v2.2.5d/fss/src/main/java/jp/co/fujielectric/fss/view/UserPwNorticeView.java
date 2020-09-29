package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.DataTableBean;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.data.ManageIdBean;
import jp.co.fujielectric.fss.entity.BasicUser;
import jp.co.fujielectric.fss.entity.OnceUser;
import jp.co.fujielectric.fss.entity.UserType;
import jp.co.fujielectric.fss.logic.UserPwNorticeBean;
import jp.co.fujielectric.fss.service.BasicUserService;
import jp.co.fujielectric.fss.service.OnceUserService;
import jp.co.fujielectric.fss.service.UserTypeService;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.context.RequestContext;

/**
 * パスワード設定通知入力ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class UserPwNorticeView extends CommonView implements Serializable {

    private static final String D_PasswordSetUrl = "userPasswordSet";

    @Inject
    private BasicUserService basicUserService;

    @Inject
    private UserTypeService userTypeService;

    @Inject
    protected UserPwNorticeBean userPwNorticeBean;

    @Inject
    private OnceUserService onceUserService;

    @Getter
    @Setter
    protected DataTableBean dataTable;

    @Getter
    @Setter
    private List<ManageIdBean> dispItems;

    @Getter
    @Setter
    private ManageIdBean selectedItem = new ManageIdBean();

    @Getter
    @Setter
    private List<ManageIdBean> filteredItems;

    @Getter
    @Setter
    private String listFilter;

    @Getter
    @Setter
    private List<UserType> userTypeList;
    @Setter
    private Map<String, UserType> maps_userType;

    @Getter
    protected int masterRowsDefault;     //一覧表示件数
    @Getter
    protected String masterRowsTemplete;     //一覧表示件数選択肢

    private List<ManageIdBean> manageIdList;

    protected String confUrl;   //確認画面URL

    private String passwordInfoOK;           //パスワード設定済み
    private String passwordInfoNone;         //パスワード未設定
    private String passwordInfoNonNortice;   //パスワード未通知

    //通知中のユーザ一覧生成
    private List<String> effectiveUserIdList;

    @Getter
    private final String PASSWORD_INFO_OK = "1";
    @Getter
    private final String PASSWORD_INFO_NONE = "2";
    @Getter
    private final String PASSWORD_INFO_NONNORTICE = "3";

    //コンストラクタ
    public UserPwNorticeView() {
        funcId = "userPwNortice";
    }

    /**
     * 画面区分毎の初期化
     *
     */
    @Override
    protected void initFunc() {
        //URL
        confUrl = "userPwNorticeConf";

        // 画面設定
        List<ManageIdBean> selectedItems = new ArrayList<>();
        dataTable = new DataTableBean();
        dataTable.initManageConfig();
        dataTable.setRowsPerPageTemplate(masterRowsTemplete);                   // 一覧表示件数選択肢
        if (!StringUtils.isBlank(userPwNorticeBean.getListFilter())) {
            selectedItems = userPwNorticeBean.getManageIdList();
            listFilter = userPwNorticeBean.getListFilter();
            dataTable.setFirst(userPwNorticeBean.getFirst());
            dataTable.setRows(userPwNorticeBean.getRows());                     // 一覧表示件数
            dataTable.setCurrentPage(userPwNorticeBean.getCurrentPage());       // 表示タブページ
        } else {
            listFilter = PASSWORD_INFO_NONNORTICE;
            dataTable.setFirst(0);
            dataTable.setRows(masterRowsDefault);                               //一覧表示件数
            dataTable.setCurrentPage(-1);                                       // 表示タブページ
        }

        // 情報を取得
        getItemList();

        // 前回チェック状態の復元
        for (ManageIdBean _selectedItem : selectedItems) {
            for (ManageIdBean _dispItem : dispItems) {
                if (_selectedItem.getUserId().equals(_dispItem.getUserId())) {
                    _dispItem.setChecked(true);
                    break;
                }
            }
        }
    }

    /**
     * マスタ設定値からの変数初期化
     *
     */
    @Override
    protected void initItems() {
        Item item;

        //一覧表示件数
        item = itemHelper.find(Item.MASTER_ROWS_DEFAULT, funcId);
        masterRowsDefault = Integer.parseInt(item.getValue());

        //一覧表示件数選択肢
        item = itemHelper.find(Item.MASTER_ROWS_TEMPLATE, funcId);
        masterRowsTemplete = item.getValue();

        //パスワード設定済み
        passwordInfoOK = getItemCaption("dspPasswordInfoOk");                   ///"○";
        //パスワード未設定
        passwordInfoNone = getItemCaption("dspPasswordInfoNone");               ///"未設定";
        //パスワード未設定（未通知/期限切れ）
        passwordInfoNonNortice = getItemCaption("dspPasswordInfoNoneNortice");  ///"未通知";
    }

    /**
     * 種別リストを作成
     */
    private void createUserTypeList() {

        maps_userType = new HashMap<>();
        userTypeList = new ArrayList<>();
        List<UserType> userTypes = userTypeService.findAll();
        if (userTypes != null) {
            for (UserType u : userTypes) {
                if (u.isDisp()) {
                    String _id = u.getId();
                    String _name = u.getName();
                    LOG.debug("getUserTypeList:id=" + _id + " name=" + _name);

                    //maps_userType追加
                    if (!maps_userType.containsKey(_id)) {
                        maps_userType.put(_id, u);
                    }
                    //userTypeList追加
                    userTypeList.add(u);
                }
            }
        }
    }

    /**
     * 種別名称を取得
     *
     * @param userType
     * @return
     */
    private UserType getUserTypeName(String userType) {
        UserType _match = null;
        if (maps_userType != null && !StringUtils.isEmpty(userType)) {
            _match = maps_userType.get(userType);
        }
        if (_match == null) {
            _match = new UserType();
        }
        return _match;
    }

    /**
     * 「ＩＤ管理」管理情報を取得
     */
    protected void getItemList() {

        // 種別リストを作成
        createUserTypeList();

        // ユーザリスト
        manageIdList = new ArrayList<>();
        List<BasicUser> datas = basicUserService.findAll();

        //通知済みで有効なワンタイム情報を取得する
        List<OnceUser> onceUserLst = onceUserService.getEffectiveInfo(D_PasswordSetUrl, sysDate);
        //通知中のメールアドレス一覧生成
        effectiveUserIdList = new ArrayList<>();
        for (OnceUser onceUser : onceUserLst) {
            effectiveUserIdList.add(onceUser.getMailId());
        }

        for (BasicUser user : datas) {
            ManageIdBean mng = new ManageIdBean();
            mng.setUserId(user.getUserId());
            mng.setName(user.getName());
            mng.setMailAddress(user.getMailAddress());
            mng.setPassword(user.getPassword());
            mng.setUserTypeClass(getUserTypeName(user.getUserType()));
            mng.setPasswordInfo(pwInfo(user));
            // 追加
            manageIdList.add(mng);
        }

        //表示用リスト生成
        onFilterChange();
    }

    public String pwInfo(BasicUser user) {
        String pw = user.getPassword();
        String userId = user.getUserId();

        if (StringUtils.isBlank(pw)) {
            //未設定

            //通知中か？
            if (effectiveUserIdList.contains(userId)) {
                //未設定（通知中）
                return passwordInfoNone;
            }
            //未設定（未通知・期限切れ）
            return passwordInfoNonNortice;
        }
        //設定済み
        return passwordInfoOK;
    }

    /**
     * 検索条件変更イベント
     */
    public void onFilterChange() {
        dispItems = new ArrayList<>();

        switch (listFilter) {
            case PASSWORD_INFO_NONE:
                //未設定
                for (ManageIdBean bean : manageIdList) {
                    //未設定（設定済み以外）を表示リストに追加
                    if (!bean.getPasswordInfo().equals(passwordInfoOK)) {
                        dispItems.add(bean);
                    }
                }
                break;
            case PASSWORD_INFO_NONNORTICE:
                //未設定（未通知/期限切れのみ）
                for (ManageIdBean bean : manageIdList) {
                    //未設定（設定済み以外）を表示リストに追加
                    if (bean.getPasswordInfo().equals(passwordInfoNonNortice)) {
                        dispItems.add(bean);
                    }
                }
                break;
            default:
                //すべて
                int index;
                dispItems = manageIdList;
                break;
        }
    }

    public boolean isSelectedItems() {
        for (ManageIdBean item : dispItems) {
            if (item.isChecked()) {
                return true;
            }
        }
        return false;
    }

    /**
     * ファイル選択
     */
    public void onRowSelect() {
        if (selectedItem.isChecked()) {
            selectedItem.setChecked(false);
        } else {
            // 選択件数チェック
            if (checkCount(1)) {
                selectedItem.setChecked(true);
            } else {
                //addCallbackParam
                RequestContext req_context = RequestContext.getCurrentInstance();
                req_context.addCallbackParam("isError", true);
            }
        }
    }

    /**
     * チェックボックス全選択
     */
    public void allCheck() {
        // ページ内の選択数取得
        long selectCount = 0;

        List<ManageIdBean> dispItems = this.dispItems;

        if(filteredItems != null) {
            dispItems = this.filteredItems;
        }

        // フィルター無し
        for (int index = dataTable.getFirst(); index < dataTable.getFirst() + dataTable.getRows(); index++) {
            if (dispItems.size() > index && dispItems.get(index).isChecked()) selectCount++;
        }

        // 選択件数チェック
        if (checkCount(dataTable.getRows() - selectCount)) {
            for (int index = dataTable.getFirst(); index < dataTable.getFirst() + dataTable.getRows(); index++) {
                if (dispItems.size() > index) dispItems.get(index).setChecked(true);
            }
        } else {
            //addCallbackParam
            RequestContext req_context = RequestContext.getCurrentInstance();
            req_context.addCallbackParam("isError", true);
        }
    }

    /**
     * 選択件数チェック、確認画面URL取得
     *
     * @return 遷移先URL
     */
    public String getActionConf() {
        //選択件数チェック
        boolean bret = checkCount();

        //addCallbackParam
        RequestContext req_context = RequestContext.getCurrentInstance();
        req_context.addCallbackParam("isError", !bret);
        if (!bret) {
            ///チェックエラーが見つかった場合nullを返す
            return null;
        }

        //画面遷移時のBeanへのデータ受渡し
        setBeanForUrlMove();

        //遷移先URLを返す
        return confUrl;
    }

    /**
     * 選択件数チェック
     *
     * @return チェック結果
     */
    private boolean checkCount() {
        return checkCount(0);
    }
    private boolean checkCount(long addCount) {
        long selectedCount = 0L;
        for (ManageIdBean item : dispItems) {
            if (item.isChecked()) selectedCount++;
        }

        long selectCountLimit = 0L;
        try {
            selectCountLimit = Long.parseLong(itemHelper.find(Item.SELECT_COUNT_LIMIT, funcId).getValue());
        } catch (Exception e) {
            selectCountLimit = 0L;
        }

        String errItem = "";
        String errMsg = "";
        if (selectCountLimit > 0 && selectCountLimit < selectedCount + addCount) {
            errMsg = itemHelper.findDispMessageStr("errSelectCountOver", funcId, selectCountLimit);
        }

        if (errMsg.length() > 0) {
            try {
                FacesContext context = FacesContext.getCurrentInstance();
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, errItem, errMsg));
                return false;
            } catch (Exception ex) {
                ex.printStackTrace();
                LOG.error("ユーザパスワード設定通知チェック失敗。", ex);
                return false;
            }
        }

        return true;
    }

    /**
     * 画面遷移時のBeanへのデータ受渡し
     */
    protected void setBeanForUrlMove() {
        List<ManageIdBean> selectedItems = new ArrayList<>();
        for (ManageIdBean item : dispItems) {
            if (item.isChecked()) selectedItems.add(item);
        }
        userPwNorticeBean.setManageIdList(selectedItems);
        userPwNorticeBean.setListFilter(listFilter);
        userPwNorticeBean.setFirst(dataTable.getFirst());
        userPwNorticeBean.setRows(dataTable.getRows());
        userPwNorticeBean.setCurrentPage(dataTable.getCurrentPage());
    }
}
