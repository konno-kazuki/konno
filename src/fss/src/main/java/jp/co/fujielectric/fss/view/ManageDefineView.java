package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.util.ArrayList;
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
import jp.co.fujielectric.fss.data.ManageDefineBean;
import jp.co.fujielectric.fss.entity.Define;
import jp.co.fujielectric.fss.service.DefineService;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * 定義設定ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class ManageDefineView extends ManageCommonView implements Serializable {

    @Getter
    @Setter
    private ManageDefineBean selectedRowDataTmp;
    @Getter
    @Setter
    private ManageDefineBean selectedRowData;
    @Getter
    @Setter
    private List<ManageDefineBean> manageDefineList;

    @Getter
    protected long maxlenDefineName;                    //固有設定名称の文字数Max
    @Getter
    protected long maxlenDefineValue;                   //固有設定値の文字数Max

    @Inject
    private DefineService defineService;

    //コンストラクタ
    public ManageDefineView() {
        funcId = "manageDefine";
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

        //固有設定名称の文字数Max
        item = itemHelper.find(Item.MAX_LEN_DEFINE_NAME, funcId);
        maxlenDefineName = Integer.parseInt(item.getValue());

        //固有設定値の文字数Max
        item = itemHelper.find(Item.MAX_LEN_DEFINE_VALUE, funcId);
        maxlenDefineValue = Integer.parseInt(item.getValue());
    }

    @Override
    protected void getItemList() {

        manageDefineList = new ArrayList<>();
        List<Define> datas = defineService.findAll();
        for (Define define : datas) {
            ManageDefineBean mng = new ManageDefineBean();
            mng.setItemKey(define.getItemKey());
            mng.setItemValue(define.getItemValue());

            // 選択
            if (selectedRowData != null && isSelected
                    && selectedRowData.getItemKey().equals(define.getItemKey())) {
                mng.setRowStyleSelect();
                mng.setChecked();
            }

            // 追加
            manageDefineList.add(mng);
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
        LOG.debug("selectedRowData: " + selectedRowData.getItemKey());
        isSelected = true;
        if (manageDefineList != null) {
            for (ManageDefineBean bean : manageDefineList) {
                bean.setRowStyle("");
                bean.setCheckedOff();

                if (selectedRowData != null && selectedRowData.getItemKey().equals(bean.getItemKey())) {
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
            selectedRowData = new ManageDefineBean();
        }
    }

    /**
     * 選択クリア
     */
    @Override
    protected void clearSelected() {

        isSelected = false;
        if (selectedRowData == null) {
            selectedRowData = new ManageDefineBean();
        }

        if (!errComponentIdList.isEmpty()) {
            errComponentIdList.clear();
        }

        selectedRowData.setItemKey("");
        selectedRowData.setItemValue("");

        if (manageDefineList != null) {
            for (ManageDefineBean bean : manageDefineList) {
                bean.setRowStyle("");
                bean.setCheckedOff();
            }
        }
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
            // insert（パスワードは空白）
            Define define = new Define();
            define.setItemKey(selectedRowData.getItemKey());
            define.setItemValue(selectedRowData.getItemValue());
            defineService.create(define);

            // 選択クリア
            clearSelected();
            // 定義情報を取得(先にclearSelectedRowDataを行っているので、rowStyleはクリアされているはず)
            // (selectedRowDataをクリアした場合、リストに影響が出ないよう、定義情報取得を実施)
            getItemList();

            // 成功
            ret = 0;
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("定義設定追加失敗。", ex);
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
            // update
            Define define = new Define();
            define.setItemKey(selectedRowData.getItemKey());
            define.setItemValue(selectedRowData.getItemValue());
            defineService.edit(define);

            // 定義情報を取得
            getItemList();
            // 更新した状態に戻るよう、選択クリアは行わない...値確認
            LOG.debug("eventUpdate");
            LOG.debug("---isSelected=" + isSelected);
            LOG.debug("---selectedRowData=" + selectedRowData.getItemKey() + " " + selectedRowData.getRowStyle());
            LOG.debug("---");

            // 成功
            ret = 0;
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("定義設定更新失敗。", ex);
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
            defineService.remove(selectedRowData.getItemKey());

            // 選択クリア
            clearSelected();
            // 定義情報を取得(先にclearSelectedRowDataを行っているので、rowStyleはクリアされているはず)
            // (selectedRowDataをクリアした場合、リストに影響が出ないよう、定義情報取得を実施)
            getItemList();

            // 成功
            ret = 0;
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("定義設定削除失敗。", ex);
        }

        //return
        return ret;
    }

    /**
     * キー重複チェック
     *
     * @param key
     * @return true(同じキーが存在)、false(同じキーが存在しない)
     */
    private boolean isExistDuplicate(String key) {
        if (manageDefineList != null) {
            for (ManageDefineBean bean : manageDefineList) {
                if (bean.getItemKey().equals(key)) {
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

            //エラーリストクリア
            errComponentIdList.clear();

            //selectedRowData
            if (selectedRowData == null) {

                //errMsg = "想定しないエラーが発生しました。";
                errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.UNKNOWN, funcId);
                context.addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(""), errMsg));
                errComponentIdList.add(frmName + ":itemKeyInput");

                ///以降のチェックは必要なし
                return false;
            }

            //---------------------------
            //定義名称
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspDefineItemKey"); ///"定義名称"
            componentId = frmName + ":" + "itemKeyInput";
            String itemKey = getTrimString(selectedRowData.getItemKey());
            selectedRowData.setItemKey(itemKey);
            if (StringUtils.isEmpty(itemKey)) {
                //errMsg = "定義名称が未入力です。";
                errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_REQUIRED, funcId, itemName);
            } else if (_mode.equals(MODE_ADD()) && isExistDuplicate(itemKey)) {
                //errMsg = "定義名称が重複しています。";
                errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_DUPLICATE, funcId, itemName);
            }
            if (!errMsg.isEmpty()) {
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                errComponentIdList.add(componentId);
                bret = false;
            }

            //---------------------------
            //定義値
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspDefineItemValue"); ///"定義値"
            componentId = frmName + ":" + "itemValueInput";
            if (!_mode.equals(MODE_DELETE())) {
                if (selectedRowData.getItemValue().isEmpty()) {
                    //errMsg = "定義値が未入力です。";
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_REQUIRED, funcId, itemName);
                }
                if (!errMsg.isEmpty()) {
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                    errComponentIdList.add(componentId);
                    bret = false;
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("定義設定チェック失敗。", ex);
            return false;
        }

        return bret;
    }

}
