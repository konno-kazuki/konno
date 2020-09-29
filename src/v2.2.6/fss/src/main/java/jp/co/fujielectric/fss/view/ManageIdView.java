package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.data.ManageIdBean;
import jp.co.fujielectric.fss.entity.BasicUser;
import jp.co.fujielectric.fss.entity.UserType;
import jp.co.fujielectric.fss.logic.AuthLogic;
import jp.co.fujielectric.fss.logic.FileDownload;
import jp.co.fujielectric.fss.logic.MailManager;
import jp.co.fujielectric.fss.service.BasicUserService;
import jp.co.fujielectric.fss.service.UserTypeService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.FileUtil;
import jp.co.fujielectric.fss.util.ValidatorUtil;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.context.RequestContext;
import org.primefaces.event.SelectEvent;
import org.primefaces.model.UploadedFile;

/**
 * ＩＤ管理ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class ManageIdView extends ManageCommonView implements Serializable {

    //カレンダーのプレースホルダ値
    private final String DSP_PLACEHOLDER_DATE = "dspPlaceholderDate";

    @Getter
    @Setter
    private ManageIdBean selectedRowDataTmp;
    @Getter
    @Setter
    private ManageIdBean selectedRowData;
    @Getter
    @Setter
    private List<ManageIdBean> manageIdList;

    @Getter
    @Setter
    private List<UserType> userTypeList;
    @Setter
    private Map<String, UserType> maps_userType;

    @Getter
    protected long maxlenUserId;                    //管理ユーザIDの文字数Max
    @Getter
    protected long addressMailCharMax;              //管理ユーザアドレスの文字数Max
    @Getter
    protected long maxlenUserName;                  //管理ユーザ名称の文字数Max
    @Getter
    protected String defaultDateStart;
    @Getter
    protected String defaultDateEnd;

    @Getter
    @Setter
    protected UploadedFile upldFile;

    @Inject
    private BasicUserService basicUserService;
    @Inject
    private UserTypeService userTypeService;
    @Inject
    private MailManager mailManager; 
    @Inject
    private AuthLogic authLogic;
    
    @Getter
    private String placeholderVal;
    @Getter
    protected boolean externalTransferFlg = false;     //セキュリティ便フラグ

    //コンストラクタ
    public ManageIdView() {
        funcId = "manageId";
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
        Item item;

        super.initItems();

        //管理ユーザIDの文字数Max
        item = itemHelper.find(Item.MAX_LEN_USER_ID, funcId);
        maxlenUserId = Integer.parseInt(item.getValue());

        //管理ユーザアドレスの文字数Max
        item = itemHelper.find(Item.ADDRESS_MAIL_CHAR_MAX, funcId);
        addressMailCharMax = Integer.parseInt(item.getValue());

        //管理ユーザ名称の文字数Max
        item = itemHelper.find(Item.MAX_LEN_USER_NAME, funcId);
        maxlenUserName = Integer.parseInt(item.getValue());

        //セキュリティ便フラグ
        item = itemHelper.find(Item.EXTERNAL_TRANSFER_FLG, funcId);
        externalTransferFlg = "true".equals(item.getValue().toLowerCase());

        //デフォルト日付
        defaultDateStart = getDefaultDate(Item.DEFAULT_DATE_START, "00:00:00");
        defaultDateEnd = getDefaultDate(Item.DEFAULT_DATE_END, "23:59:59");
        LOG.debug("defaultDateStart=" + defaultDateStart + " defaultDateEnd=" + defaultDateEnd);

        // カレンダーのプレースホルダ値（"例）" + _strDate）
        Date _sysDate = DateUtil.getSysDate();
        DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.JAPAN);
        String _strDate = df.format(_sysDate);
        placeholderVal = itemHelper.findDispMessageStr(DSP_PLACEHOLDER_DATE, funcId, _strDate);
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
                    //セキュリティ便フラグ（ON)または、セキュリティ便フラグ(OFF)かつ種別が内部ユーザの場合
                    if (externalTransferFlg || (!externalTransferFlg && u.isInternalflg())) {
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
    @Override
    protected void getItemList() {

        // 種別リストを作成
        createUserTypeList();

        // ユーザリスト
        manageIdList = new ArrayList<>();
        List<BasicUser> datas = basicUserService.findAll();
        for (BasicUser user : datas) {
            ManageIdBean mng = new ManageIdBean();
            mng.setUserId(user.getUserId());
            mng.setName(user.getName());
            mng.setMailAddress(user.getMailAddress());
            mng.setPassword(user.getPassword());
            mng.setUserTypeClass(getUserTypeName(user.getUserType()));
            mng.setStartTime(user.getStartTime());
            mng.setEndTime(user.getEndTime());

            // 選択
            if (selectedRowData != null && isSelected
                    && selectedRowData.getUserId().equals(user.getUserId())) {
                mng.setRowStyleSelect();
                mng.setChecked();
            }

            // 追加
            manageIdList.add(mng);
        }
    }

    /**
     * rowSelectイベントからの選択情報
     *
     * @return
     */
    @Override
    public String eventRowSelect() {

        LOG.debug("eventRowSelect start");

        // エラーありのコンポーネントIDのリストをクリア
        if (errComponentIdList != null) {
            errComponentIdList.clear();
        }

        // selectedRowDataが指定されていない場合
        if (selectedRowData == null) {
            ///選択クリア（本来、ここに来ることはない。万が一を考慮）
            clearSelected();
            return "";
        }

        // 選択行の情報を複写
        selectedRowData = selectedRowDataTmp.clone();

        // 選択処理
        LOG.debug("selectedRowData: " + selectedRowData.getUserId());
        isSelected = true;
        if (manageIdList != null) {
            for (ManageIdBean bean : manageIdList) {
                bean.setRowStyle("");
                bean.setCheckedOff();

                if (selectedRowData != null && selectedRowData.getUserId().equals(bean.getUserId())) {
                    bean.setRowStyleSelect();
                    bean.setChecked();
                }
            }
        }

        // 終了
        LOG.debug("eventRowSelect end");
        return "";
    }

    /**
     * pageイベントからの選択情報
     */
    @Override
    public void eventPage() {

        // エラーありのコンポーネントIDのリストをクリア
        if (errComponentIdList != null) {
            errComponentIdList.clear();
        }

        if (isSelected) {
            //TODO ページ変更時、選択情報は解除する？
        }
        if (selectedRowData == null) {
            selectedRowData = new ManageIdBean();
        }
    }

    /**
     * 選択クリア
     */
    @Override
    protected void clearSelected() {

        isSelected = false;
        if (selectedRowData == null) {
            selectedRowData = new ManageIdBean();
        }

        if (!errComponentIdList.isEmpty()) {
            errComponentIdList.clear();
        }

        selectedRowData.setUserId("");
        selectedRowData.setMailAddress("");
        selectedRowData.setName("");
        selectedRowData.setStartTime(null);
        selectedRowData.setEndTime(null);
        selectedRowData.setPassword("");

        UserType u = new UserType();
        selectedRowData.setUserTypeClass(u);

        if (manageIdList != null) {
            for (ManageIdBean bean : manageIdList) {
                bean.setRowStyle("");
                bean.setCheckedOff();
            }
        }
    }

    /**
     * 選択された種別
     *
     * @return 結果(0=成功、0以外＝失敗)
     */
    private String getUserTypeFromSelected() {
        if (selectedRowData.getUserType() != null) {
            if (!StringUtils.isEmpty(selectedRowData.getUserTypeClass().getId())) {
                return selectedRowData.getUserTypeClass().getId();
            }
        }
        return "";
    }

    /**
     * 追加
     *
     * @return 結果(0=成功、0以外＝失敗)
     */
    @Override
    protected int exeAdd() {

        int ret = -1;

        try {
            // 開始日未指定の場合は、デフォルト開始日(ex.2016/01/01 00:00:00)
            if (selectedRowData.getStartTime() == null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                Date _staTime = sdf.parse(defaultDateStart.replace("/", "").replace("-", ""));    ///sdf.parse("20160101 00:00:00");
                selectedRowData.setStartTime(_staTime);
            }

            // 終了日未指定の場合は、デフォルト終了日(ex.9999/12/31 23:59:59)
            if (selectedRowData.getEndTime() == null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                Date _endTime = sdf.parse(defaultDateEnd.replace("/", "").replace("-", ""));      ///sdf.parse("99991231 23:59:59");
                selectedRowData.setEndTime(_endTime);
            } else {
                ///指定有りの場合は指定日 23:59:59
                selectedRowData.setEndTime(DateUtil.getDateExcludeMillisExpirationTime(selectedRowData.getEndTime()));
            }

            // insert（パスワードは空白）
            BasicUser basicUser = new BasicUser();
            basicUser.setUserId(selectedRowData.getUserId());
            basicUser.setName(selectedRowData.getName());
            basicUser.setMailAddress(selectedRowData.getMailAddress());
            basicUser.setStartTime(selectedRowData.getStartTime());
            basicUser.setEndTime(selectedRowData.getEndTime());

            basicUser.setUserType(getUserTypeFromSelected());

            basicUser.setPassword("");
            basicUserService.create(basicUser);

            // 選択クリア
            clearSelected();
            // ユーザ情報を取得(先にclearSelectedRowDataを行っているので、rowStyleはクリアされているはず)
            // (selectedRowDataをクリアした場合、リストに影響が出ないよう、ユーザ情報取得を実施)
            getItemList();

            // 成功
            ret = 0;
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("ＩＤ管理追加失敗。", ex);
        }

        //return
        return ret;
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
            // 開始日未指定の場合は、デフォルト開始日(ex.2016/01/01 00:00:00)
            if (selectedRowData.getStartTime() == null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                Date _staTime = sdf.parse(defaultDateStart.replace("/", "").replace("-", ""));    ///sdf.parse("20160101 00:00:00");
                selectedRowData.setStartTime(_staTime);
            }

            // 終了日未指定の場合は、デフォルト終了日(ex.9999/12/31 23:59:59)
            if (selectedRowData.getEndTime() == null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                Date _endTime = sdf.parse(defaultDateEnd.replace("/", "").replace("-", ""));      ///sdf.parse("99991231 23:59:59");
                selectedRowData.setEndTime(_endTime);
            } else {
                ///指定有りの場合は指定日 23:59:59
                selectedRowData.setEndTime(DateUtil.getDateExcludeMillisExpirationTime(selectedRowData.getEndTime()));
            }

            // update
            BasicUser basicUser = new BasicUser();
            basicUser.setUserId(selectedRowData.getUserId());
            basicUser.setName(selectedRowData.getName());
            basicUser.setMailAddress(selectedRowData.getMailAddress());
            basicUser.setStartTime(selectedRowData.getStartTime());
            basicUser.setEndTime(selectedRowData.getEndTime());

            basicUser.setUserType(getUserTypeFromSelected());

            basicUser.setPassword(selectedRowData.getPassword());
            basicUserService.edit(basicUser);

            // ユーザ情報を取得
            getItemList();
            // 更新した状態に戻るよう、選択クリアは行わない...値確認
            LOG.debug("exeUpdate");
            LOG.debug("---isSelected=" + isSelected);
            LOG.debug("---selectedRowData=" + selectedRowData.getUserId() + " " + selectedRowData.getRowStyle());
            LOG.debug("---");

            // 成功
            ret = 0;
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("ＩＤ管理更新失敗。", ex);
        }

        //return
        return ret;
    }

    /**
     * 削除
     *
     * @return 結果(0=成功、0以外＝失敗)
     */
    @Override
    protected int exeDelete() {
        int ret = -1;
        try {
            // delete
            basicUserService.remove(selectedRowData.getUserId());

            // 選択クリア
            clearSelected();
            // ユーザ情報を取得(先にclearSelectedRowDataを行っているので、rowStyleはクリアされているはず)
            // (selectedRowDataをクリアした場合、リストに影響が出ないよう、ユーザ情報取得を実施)
            getItemList();

            // 成功
            ret = 0;
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("ＩＤ管理削除失敗。", ex);
        }

        //return
        return ret;
    }

    /**
     * ID重複チェック
     *
     * @param id
     * @return true(同じIDが存在)、false(同じIDが存在しない)
     */
    protected boolean isExistDuplicate(String id) {
        if (manageIdList != null) {
            for (ManageIdBean bean : manageIdList) {
                if (bean.getUserId().equals(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 入力チェック
     *
     * @param _mode 処理モード(ManageIdBean.MODE_ADD,MODE_UPDATE,MODE_DELETE)
     *
     * @return チェック結果
     */
    @Override
    protected boolean checkInput(String _mode) {
        boolean bret = true;
        String errMsg;
        String componentId;
        String itemName;

        String frmName = "inputForm";

        try {
            FacesContext context = FacesContext.getCurrentInstance();

            Item _inputCheckRegex = itemHelper.find(Item.INPUT_CHECK_REGEX, funcId);
            String inputCheckRegex = _inputCheckRegex.getValue();
            Item _inputCheckExclude = itemHelper.find(Item.INPUT_CHECK_EXCLUDE, funcId);
            String inputCheckExclude = _inputCheckExclude.getValue();

            //エラーリストクリア
            errComponentIdList.clear();

            //selectedRowData
            if (selectedRowData == null) {

                //errMsg = "想定しないエラーが発生しました。";
                errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.UNKNOWN, funcId);
                context.addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(""), errMsg));
                errComponentIdList.add(frmName + ":userIdInput");

                ///以降のチェックは必要なし
                return false;
            }

            //---------------------------
            //ユーザID
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspUserId"); ///"ユーザＩＤ"
            componentId = frmName + ":" + "userIdInput";
            {
                String userId = getTrimString(selectedRowData.getUserId());
                selectedRowData.setUserId(userId);

                if (userId.isEmpty()) {
                    // ユーザＩＤが未入力です。
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_REQUIRED, funcId, itemName);
                } else if (_mode.equals(MODE_ADD()) && isExistDuplicate(userId)) {
                    // ユーザＩＤが重複しています。
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_DUPLICATE, funcId, itemName);
                } else if (!CommonUtil.isCharMax(userId, maxlenUserId)) {
                    // 文字数Maxチェック(%sの文字数が上限（%s）を超えています。)
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_STR_LENGTH_OVER, funcId, itemName, maxlenUserId);
                } else if (!ValidatorUtil.isValidRegex(userId, inputCheckRegex, inputCheckExclude)) {
                    // 不正文字チェック(%sに登録できない文字%sが使われています。)
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_STR_VALID, funcId, itemName);
                }
                if (!errMsg.isEmpty()) {
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                    errComponentIdList.add(componentId);
                    bret = false;
                }
            }

            //---------------------------
            //名前
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspName");   ///"名前"
            componentId = frmName + ":" + "nameInput";
            if (!_mode.equals(MODE_DELETE())) {
                String userName = getTrimString(selectedRowData.getName());
                selectedRowData.setName(userName);

                if (userName.isEmpty()) {
                    // 名前が未入力です。
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_REQUIRED, funcId, itemName);
                } else if (!CommonUtil.isCharMax(userName, maxlenUserName)) {
                    // 文字数Maxチェック(%sの文字数が上限（%s）を超えています。)
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_STR_LENGTH_OVER, funcId, itemName, maxlenUserName);
                } else if (!ValidatorUtil.isValidJisX2080(userName)) {
                    // 不正文字チェック(%sに登録できない文字%sが使われています。)
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_STR_VALID, funcId, itemName);
                }
                if (!errMsg.isEmpty()) {
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                    errComponentIdList.add(componentId);
                    bret = false;
                }
            }

            //---------------------------
            //メールアドレス
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspMailAddress");    ///"メールアドレス"
            componentId = frmName + ":" + "mailAddressInput";
            if (!_mode.equals(MODE_DELETE())) {
                String mailAddress = getTrimString(selectedRowData.getMailAddress());
                selectedRowData.setMailAddress(mailAddress);
                boolean isSendInner = mailManager.isMyDomain(mailAddress);      //庁内/庁外判定
                if (mailAddress.isEmpty()) {
                    // メールアドレスが未入力です。
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_REQUIRED, funcId, itemName);
                } else if (!CommonUtil.isCharMax(mailAddress, addressMailCharMax)) {
                    // 文字数Maxチェック(%sの文字数が上限（%s）を超えています。)
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_STR_LENGTH_OVER, funcId, itemName, addressMailCharMax);
                } else if (!CommonUtil.isValidEmail(mailAddress)) {
                    // 正しいメールアドレスではありません。
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.MAIL_ADDRESS_INVALID, funcId);
                } else if (selectedRowData.getUserType() != null && StringUtils.isNotBlank(selectedRowData.getUserTypeClass().getId())){
                    // 選択されている種別の内部ユーザフラグを取得(true:内部ユーザ、false:外部ユーザ)
                    boolean internalflg = authLogic.isUserTypeInternalflg(selectedRowData.getUserTypeClass().getId());
                    // 種別が内部ユーザかつドメインが庁外の場合
                    if (internalflg && !isSendInner) {          
                        // 庁内のユーザ種別に対して外部のメールアドレスは利用できません。庁内のメールアドレスを指定してください。
                        errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_EXTERNAL, funcId);

                    // 種別が外部ユーザかつドメインが庁内の場合    
                    } else if (!internalflg && isSendInner) {    
                        // 外部のユーザ種別に対して庁内のメールアドレスは利用できません。外部のメールアドレスを指定してください。
                        errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_INTERNAL, funcId);
                    }
                }
                if (!errMsg.isEmpty()) {
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                    errComponentIdList.add(componentId);
                    bret = false;
                }
            }

            //---------------------------
            //種別
            //---------------------------
            itemName = getItemCaption("dspUserType");   ///"種別"
            errMsg = "";
            componentId = frmName + ":" + "selectUserType";
            if (!_mode.equals(MODE_DELETE())) {
                if (selectedRowData.getUserType() == null || StringUtils.isBlank(selectedRowData.getUserTypeClass().getId())) {
                    // 種別が未選択です。
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.SELECT_REQUIRED, funcId, itemName);
                }
                if (!errMsg.isEmpty()) {
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                    errComponentIdList.add(componentId);
                    bret = false;
                }
            }

            //---------------------------
            //開始日・終了日
            //---------------------------
            errMsg = "";
            //itemName = getItemCaption("dspStartDate") + "・" + getItemCaption("dspEndDate");    ///"開始日・終了日"
            itemName = getItemCaption("dspPeriod");    ///"期間"
            componentId = frmName + ":" + "endTimeInput";
            // 前後関係のチェック
            if (selectedRowData.getStartTime() != null && selectedRowData.getEndTime() != null) {
                if (!checkFromTo()) {
                    // 開始日・終了日の前後関係を確認して下さい。
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.DATE_FROM_TO_REVERSE, funcId);
                }
            }
            if (!errMsg.isEmpty()) {
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                errComponentIdList.add(componentId);
                bret = false;
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("ＩＤ管理チェック失敗。", ex);
            return false;
        }

        return bret;
    }

    /**
     * 開始日の入力チェック
     *
     * @param event イベント情報
     */
    public void fromDateSelect(SelectEvent event) {
        FacesContext context = FacesContext.getCurrentInstance();

        if (!checkFromTo()) {
            String errItem = getItemCaption("dspStartDate");    ///"開始日：　
            // 終了日より未来の日付が選択されました。終了日を含め過去の日付を選択して下さい。
            String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.FROMDATE_SELECT_REQUIRED_PASTDATE, funcId);
            context.addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(errItem), errMsg));
        }
    }

    /**
     * 終了日の入力チェック
     *
     * @param event イベント情報
     */
    public void toDateSelect(SelectEvent event) {
        FacesContext context = FacesContext.getCurrentInstance();

        if (!checkFromTo()) {
            String errItem = getItemCaption("dspEndDate");  ///"終了日：　
            // 開始日より過去の日付が選択されました。開始日を含め未来の日付を選択して下さい。
            String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.TODATE_SELECT_REQURED_FUTUREDATE, funcId);
            context.addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(errItem), errMsg));
        }
    }

    /**
     * 期間の前後チェック
     */
    private boolean checkFromTo() {
        if (selectedRowData.getStartTime() != null && selectedRowData.getEndTime() != null) {
            // 当日チェック
            DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.JAPAN);
            String _strStartTime = df.format(selectedRowData.getStartTime());
            String _strEndTime = df.format(selectedRowData.getEndTime());
            if (!_strStartTime.equals(_strEndTime)) {
                // 開始日が終了日よりも未来の場合
                if (selectedRowData.getStartTime().after(selectedRowData.getEndTime())) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 開始日の入力チェック（手入力用）
     *
     */
    public void fromDateInput() {
        FacesContext context = FacesContext.getCurrentInstance();

        // 入力チェック
        Date _inputDate = selectedRowData.getStartTime();
        if (_inputDate == null) {
            return;
        }
        // 開始日・終了日の前後関係チェック
        if (!checkFromTo()) {
            String errItem = getItemCaption("dspStartDate");    ///"開始日：　";
            //String errMsg = "開始日が終了日よりも未来が入力されました。終了日を含め過去を入力して下さい。";
            String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.FROMDATE_INPUT_REQUIRED_PASTDATE, funcId);
            context.addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(errItem), errMsg));
        }
    }

    /**
     * 終了日の入力チェック（手入力用）
     *
     */
    public void toDateInput() {
        FacesContext context = FacesContext.getCurrentInstance();

        // 入力チェック
        Date _inputDate = selectedRowData.getEndTime();
        if (_inputDate == null) {
            return;
        }

        // 開始日・終了日の前後関係チェック
        if (!checkFromTo()) {
            String errItem = getItemCaption("dspEndDate");  ///"終了日：　";
            //String errMsg = "終了日が開始日よりも過去が入力されました。開始日を含め未来を入力して下さい。";
            String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.TODATE_INPUT_REQURED_FUTUREDATE, funcId);
            context.addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(errItem), errMsg));
        }
    }

    /**
     * ＣＳＶ出力
     */
    @Override
    public void eventCsvOutput() {
        LOG.debug("eventCsvOutput start");

        String errMsg;
        FacesContext context = FacesContext.getCurrentInstance();
        RequestContext req_context = RequestContext.getCurrentInstance();

        String _itemName = getItemCaption("dspBtnBulkOutput");  // "一括出力"
        String _summary = getFacesMessageSummary(_itemName);

        //ファイル名
        Date _date = new Date();
        String _fileName = "_" + new SimpleDateFormat("yyyyMMdd").format(_date) + ".csv";
        String fileName = getItemCaption("nameBulkFile") + _fileName;
        if (fileName.isEmpty()) {
            fileName = _fileName;
        }

        //ダウンロード可否
        boolean _isDownload = false;
        this.isDownLoad = _isDownload;

        //--------------------------------
        //ダウンロード
        //--------------------------------
        FileDownload fdData = null;
        fdData = findAllBasicUser();

        //出力件数
        int outCnt = (fdData == null) ? -1 : fdData.getListCnt();
        if (outCnt == 0) {
            ///出力データ無し
            //"出力データが見つかりません。"
            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.OUTPUTDATA_NOT_EXIST, funcId);
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, _summary, errMsg));
        } else if (outCnt < 0) {
            ///例外発生
            //"ＣＳＶファイル作成に失敗しました。"
            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.CSV_FILE_CREATE_FAILED, funcId);
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, _summary, errMsg));
        }

        //ダウンロード準備
        if (fdData != null && outCnt > 0) {
            try {
                this.downloadFile = fdData.getDownloadFile(fileName);

                ///ダウンロード可能
                _isDownload = true;

            } catch (Exception ex) {
                ex.printStackTrace();
                LOG.error("ＩＤ管理ダウンロード準備失敗。", ex);
            }
        }

        //設定
        this.isDownLoad = _isDownload;  ///ダウンロード可否
        req_context.addCallbackParam(callbackParam_Download, this.isDownLoad);
        LOG.debug("eventCsvOutput end");
    }

    /**
     * (userType)ID値から、(userType)ソート値を取得
     * @param userType
     * @return
     */
    private String getUserTypeSort(String userType) {

        String _sort = "";
        UserType _ut = maps_userType.get(userType);
        if (_ut!=null && _ut.getId()!=null) {
            _sort = String.valueOf(_ut.getSort());
        }

        //return
        return _sort;
    }

    /**
     * 管理ID情報を検索
     *
     * @return 管理ID情報を返す
     */
    protected FileDownload findAllBasicUser() {
        FileDownload fd = new FileDownload();

        //ユーザ情報取得
        List<BasicUser> userDatas = basicUserService.findAll();
        if (userDatas != null && userDatas.size() > 0) {

            //ヘッダ
            fd.addOneData("レコードフラグ");
            fd.addOneData("ユーザID");
            fd.addOneData("氏名");
            fd.addOneData("メールアドレス");
            fd.addOneData("並び順");
            fd.addOneData("種別");
            fd.addOneData("適用開始年");
            fd.addOneData("適用開始月");
            fd.addOneData("適用開始日");
            fd.addOneData("適用終了年");
            fd.addOneData("適用終了月");
            fd.addOneData("適用終了日");

            fd.addNewLine();

            //データ
            for (BasicUser user : userDatas) {
                //レコードフラグ（＝更新）
                fd.addOneData(RECORDFLG_UPDATE);
                //ユーザID
                fd.addOneData(FileUtil.getCsvString(user.getUserId()));
                //名前
                fd.addOneData(FileUtil.getCsvString(user.getName()));
                //メールアドレス
                fd.addOneData(FileUtil.getCsvString(user.getMailAddress()));
                //並び順
                fd.addOneData("");
                //種別（ソート値で出力するのは一時対応...TODO）
                fd.addOneData(FileUtil.getCsvString(getUserTypeSort(user.getUserType())));
                //適用開始
                if(user.getStartTime() != null) {
                    //適用開始(年)
                    fd.addOneData(FileUtil.getCsvString(new SimpleDateFormat("yyyy").format(user.getStartTime())));
                    //適用開始(月)
                    fd.addOneData(FileUtil.getCsvString(new SimpleDateFormat("MM").format(user.getStartTime())));
                    //適用開始(日)
                    fd.addOneData(FileUtil.getCsvString(new SimpleDateFormat("dd").format(user.getStartTime())));
                } else {
                    fd.addOneData("");
                    fd.addOneData("");
                    fd.addOneData("");
                }
                //適用終了
                if(user.getEndTime() != null) {
                //適用終了(年)
                    fd.addOneData(FileUtil.getCsvString(new SimpleDateFormat("yyyy").format(user.getEndTime())));
                    //適用終了(月)
                    fd.addOneData(FileUtil.getCsvString(new SimpleDateFormat("MM").format(user.getEndTime())));
                    //適用終了(日)
                    fd.addOneData(FileUtil.getCsvString(new SimpleDateFormat("dd").format(user.getEndTime())));
                } else {
                    fd.addOneData("");
                    fd.addOneData("");
                    fd.addOneData("");
                }

                ///改行
                fd.addNewLine();
            }
        }

        return fd;
    }
}
