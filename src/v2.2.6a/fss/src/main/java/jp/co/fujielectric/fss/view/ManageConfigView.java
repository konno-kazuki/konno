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
import jp.co.fujielectric.fss.data.ManageConfigBean;
import jp.co.fujielectric.fss.entity.Config;
import jp.co.fujielectric.fss.service.ConfigService;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.jpamodelgen.util.StringUtil;

/**
 * 機能設定ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class ManageConfigView extends ManageCommonView implements Serializable {
    /**
     * 手動追加IDの頭文字
     */
    static final String C_ADDID_PRE = "a";
    
    @Getter
    @Setter
    private ManageConfigBean selectedRowDataTmp;
    @Getter
    @Setter
    private ManageConfigBean selectedRowData;
    @Getter
    @Setter
    private List<ManageConfigBean> manageConfigList;

    @Getter
    protected long maxlenFuncId;                        //機能IDの文字数Max
    @Getter
    protected long maxlenFuncKey;                       //機能キーの文字数Max
    @Getter
    protected long maxlenFuncValue;                     //機能値の文字数Max

    @Inject
    private ConfigService configService;

    //コンストラクタ
    public ManageConfigView() {
        funcId = "manageConfig";
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

        //機能IDの文字数Max
        item = itemHelper.find(Item.MAX_LEN_FUNC_ID, funcId);
        maxlenFuncId = Integer.parseInt(item.getValue());

        //機能キーの文字数Max
        item = itemHelper.find(Item.MAX_LEN_FUNC_KEY, funcId);
        maxlenFuncKey = Integer.parseInt(item.getValue());

        //機能値の文字数Max
        item = itemHelper.find(Item.MAX_LEN_FUNC_VALUE, funcId);
        maxlenFuncValue = Integer.parseInt(item.getValue());
    }

    /**
     * 機能設定情報を取得
     */
    @Override
    protected void getItemList() {

        manageConfigList = new ArrayList<>();
        List<Config> datas = configService.findAll();
        for (Config item : datas) {
            ManageConfigBean mng = new ManageConfigBean(item);      //[248対応（簡易版）]

            // 選択
            if (selectedRowData != null && isSelected
                    && selectedRowData.getId().equals(item.getId())) {
                mng.setRowStyleSelect();
                mng.setChecked();
            }

            // 追加
            manageConfigList.add(mng);
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
        LOG.debug("selectedRowData: " + selectedRowData.getId());
        isSelected = true;
        if (manageConfigList != null) {
            for (ManageConfigBean bean : manageConfigList) {
                bean.setRowStyle("");
                bean.setCheckedOff();

                if (selectedRowData != null && selectedRowData.getId().equals(bean.getId())) {
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
            selectedRowData = new ManageConfigBean();
        }
    }

    /**
     * 選択クリア
     */
    @Override
    protected void clearSelected() {

        isSelected = false;
        if (selectedRowData == null) {
            selectedRowData = new ManageConfigBean();
        }

        if (!errComponentIdList.isEmpty()) {
            errComponentIdList.clear();
        }

        selectedRowData.setId("");
        selectedRowData.setFuncId("");
        selectedRowData.setItemKey("");
        selectedRowData.setItemValue("");

        if (manageConfigList != null) {
            for (ManageConfigBean bean : manageConfigList) {
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
            int id = 0;

            //IDを新規採番(MAX+1)
            if (manageConfigList != null) {
                //[248対応（簡易版)]IDに数値以外もセットできるように修正
                id = manageConfigList.stream().map(bean -> getIdNumber(bean.getId(),C_ADDID_PRE, 0))
                        .max((a, b) -> a.compareTo(b)).get();
            }
            //追加IDは'a000N'とする　[248対応（簡易版)]
            String strId = String.format("%s%04d", C_ADDID_PRE, ++id);    //手動追加用ID生成（'a'+'000N')
                    
            // insert
            Config config = new Config();
            config.setId(strId);
            config.setFuncId(selectedRowData.getFuncId());
            config.setItemKey(selectedRowData.getItemKey());
            config.setItemValue(selectedRowData.getItemValue());
            configService.create(config);

            // 選択クリア
            clearSelected();
            // ユーザ情報を取得(先にclearSelectedRowDataを行っているので、rowStyleはクリアされているはず)
            // (selectedRowDataをクリアした場合、リストに影響が出ないよう、ユーザ情報取得を実施)
            getItemList();

            // 成功
            ret = 0;
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("機能設定追加失敗。", ex);
        }

        //return
        return ret;
    }

    //[248対応（簡易版)]IDに数値以外もセットできるように
    /**
     * 指定されたID文字列が指定頭文字の場合にそれ以降の数値部を数値として返す
     * @param strId     //対象ID文字列
     * @param strPre    //頭文字（手動追加IDの場合は'a'）
     * @param defaultNum    //対象外文字の場合の数値
     * @return 
     */
    public static int getIdNumber(String strId, String strPre, int defaultNum)
    {
        try{
            //頭文字が指定頭文字かどうか
            if(!strId.startsWith(strPre)){
                //指定された頭文字じゃなければデフォルト値を返す
                return defaultNum;
            }
            //頭文字を削除
            String tmpStr = strId.substring(strPre.length());
            if(StringUtils.isNumeric(tmpStr)){
                //数値文字列の場合、数値型にして返す
                return Integer.parseInt(tmpStr);
            }
            return defaultNum;
        }catch(Exception e){
            return defaultNum;
        }
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
            Config config = new Config();
            config.setId(selectedRowData.getId());
            config.setFuncId(selectedRowData.getFuncId());
            config.setItemKey(selectedRowData.getItemKey());
            config.setItemValue(selectedRowData.getItemValue());
            configService.edit(config);

            // ユーザ情報を取得
            getItemList();
            // 更新した状態に戻るよう、選択クリアは行わない...値確認
            LOG.debug("eventUpdate");
            LOG.debug("---isSelected=" + isSelected);
            LOG.debug("---selectedRowData=" + selectedRowData.getId() + " " + selectedRowData.getRowStyle());
            LOG.debug("---");

            // 成功
            ret = 0;
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("機能設定更新失敗。", ex);
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
            configService.remove(selectedRowData.getId());

            // 選択クリア
            clearSelected();
            // ユーザ情報を取得(先にclearSelectedRowDataを行っているので、rowStyleはクリアされているはず)
            // (selectedRowDataをクリアした場合、リストに影響が出ないよう、ユーザ情報取得を実施)
            getItemList();

            // 成功
            ret = 0;
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("機能設定削除失敗。", ex);
        }

        //return
        return ret;
    }

    /**
     * 機能ID、キー重複チェック
     *
     * @param _funcId
     * @param _itemKey
     * @return true(同じIDが存在)、false(同じIDが存在しない)
     */
    protected boolean isExistDuplicate(String _funcId, String _itemKey) {
        if (manageConfigList != null) {
            for (ManageConfigBean bean : manageConfigList) {
                if (bean.getFuncId().compareToIgnoreCase(_funcId) == 0
                        && bean.getItemKey().compareToIgnoreCase(_itemKey) == 0) {
                    return true;
                }
            }
        }
        return false;
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
                errComponentIdList.add(frmName + ":idInput");

                ///以降のチェックは必要なし
                return false;
            }

            //---------------------------
            //機能ID
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspConfigFuncId"); ///"機能ID"
            componentId = frmName + ":" + "funcIdInput";
            String chk_funcId = "";
            if (!_mode.equals(MODE_DELETE())) {
                chk_funcId = getTrimString(selectedRowData.getFuncId());
                selectedRowData.setFuncId(chk_funcId);
                if (chk_funcId.isEmpty()) {
                    //"機能IDが未入力です。"
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_REQUIRED, funcId, itemName);
                }
                if (!errMsg.isEmpty()) {
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                    errComponentIdList.add(componentId);
                    bret = false;
                }
            }

            //---------------------------
            //キー
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspConfigItemKey"); ///"キー"
            String itemKey;
            componentId = frmName + ":" + "itemKeyInput";
            if (!_mode.equals(MODE_DELETE())) {
                itemKey = getTrimString(selectedRowData.getItemKey());
                selectedRowData.setItemKey(itemKey);
                if (itemKey.isEmpty()) {
                    //"キーが未入力です。"
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_REQUIRED, funcId, itemName);
                } else if (_mode.equals(MODE_ADD()) && isExistDuplicate(chk_funcId, itemKey)) {
                    //errMsg = "機能IDとキーの組合せが重複しています。";
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.CONFIG_DUPLICATE, funcId);
                }
                if (!errMsg.isEmpty()) {
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                    errComponentIdList.add(componentId);
                    bret = false;
                }
            }

            //---------------------------
            //設定値
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspConfigItemValue"); ///"設定値"
            componentId = frmName + ":" + "itemValueInput";
            if (!_mode.equals(MODE_DELETE())) {
                if (selectedRowData.getItemValue().isEmpty()) {
                    //errMsg = "設定値が未入力です。";
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
            LOG.error("機能設定チェック失敗。", ex);
            return false;
        }

        return bret;
    }

}
