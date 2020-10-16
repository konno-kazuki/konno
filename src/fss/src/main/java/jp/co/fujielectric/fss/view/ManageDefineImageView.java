package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
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
import jp.co.fujielectric.fss.entity.DefineImage;
import jp.co.fujielectric.fss.service.DefineImageService;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.context.RequestContext;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.UploadedFile;

/**
 * 画像設定ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class ManageDefineImageView extends ManageCommonView implements Serializable {

    @Getter
    @Setter
    private ManageDefineBean selectedRowDataTmp;
    @Getter
    @Setter
    private ManageDefineBean selectedRowData;
    @Getter
    @Setter
    private List<ManageDefineBean> manageDefineImageList;

    @Inject
    private DefineImageService defineImageService;

    @Getter
    @Setter
    protected UploadedFile upldFile;
    
    @Getter
    protected long maxFSize;                    //最大ファイルサイズ
    @Getter
    protected long maxlenImageId;               //画像ＩＤの文字数Max
    
    //コンストラクタ
    public ManageDefineImageView() {
        funcId = "manageDefineImage";
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

        //最大ファイルサイズ
        item = itemHelper.find(Item.FILE_SIZE_LIMIT, funcId);
        maxFSize = Long.parseLong(item.getValue());

        //画像IDの文字数Max
        item = itemHelper.find(Item.MAX_LEN_IMAGE_ID, funcId);
        maxlenImageId = Long.parseLong(item.getValue());
    }

    @Override
    protected void getItemList() {

        manageDefineImageList = new ArrayList<>();
        List<DefineImage> datas = defineImageService.findAll();
        for (DefineImage define : datas) {
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
            manageDefineImageList.add(mng);
        }
    }

    /**
     * ファイル選択
     *
     * @param event
     */
    public void handleFileUpload(FileUploadEvent event) {
//        UploadedFile upldFile = event.getFile();
        upldFile = event.getFile();

        //ファイル名
        String fname = event.getFile().getFileName();

        //ファイル名が文字化けするので変換する
        try {
            fname = new String(fname.getBytes("8859_1"), "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            java.util.logging.Logger.getLogger(SendCommonView.class.getName()).log(Level.SEVERE, null, ex);
        }
        LOG.debug("FileName:" + fname);

        //Base64エンコード
        byte[] encoded = Base64.getEncoder().encode(upldFile.getContents());
        selectedRowData.setItemValue("data:image/png;base64," + new String(encoded));

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
        
        if (manageDefineImageList != null) {
            for (ManageDefineBean bean : manageDefineImageList) {
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

        if (manageDefineImageList != null) {
            for (ManageDefineBean bean : manageDefineImageList) {
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
            DefineImage define = new DefineImage();
            define.setItemKey(selectedRowData.getItemKey());
            define.setItemValue(selectedRowData.getItemValue());
            defineImageService.create(define);

            // 選択クリア
            clearSelected();
            // 定義情報を取得(先にclearSelectedRowDataを行っているので、rowStyleはクリアされているはず)
            // (selectedRowDataをクリアした場合、リストに影響が出ないよう、定義情報取得を実施)
            getItemList();

            // 成功
            ret = 0;
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("画像設定追加失敗。", ex);
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
            DefineImage define = new DefineImage();
            define.setItemKey(selectedRowData.getItemKey());
            define.setItemValue(selectedRowData.getItemValue());
            defineImageService.edit(define);

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
            LOG.error("画像設定更新失敗。", ex);
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
            defineImageService.remove(selectedRowData.getItemKey());

            // 選択クリア
            clearSelected();
            // 定義情報を取得(先にclearSelectedRowDataを行っているので、rowStyleはクリアされているはず)
            // (selectedRowDataをクリアした場合、リストに影響が出ないよう、定義情報取得を実施)
            getItemList();

            // 成功
            ret = 0;
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("画像設定削除失敗。", ex);
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
        for (ManageDefineBean bean : manageDefineImageList) {
            if (bean.getItemKey().equals(key)) {
                return true;
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

        String frmName = "dispForm";

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

            if (_mode.equals(MODE_DELETE())) {
                return true;
            }

            //---------------------------
            //画像ＩＤ
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspDefineImageItemKey");
            componentId = frmName + ":" + "itemKeyInput";
            String itemKey = getTrimString(selectedRowData.getItemKey());
            selectedRowData.setItemKey(itemKey);
            if (StringUtils.isEmpty(itemKey)) {
                //画像ＩＤが未入力です。
                errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_REQUIRED, funcId, itemName);
            } else if (_mode.equals(MODE_ADD()) && isExistDuplicate(itemKey)) {
                //画像ＩＤが重複しています。
                errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_DUPLICATE, funcId, itemName);
            }
            if (!errMsg.isEmpty()) {
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                errComponentIdList.add(componentId);
                bret = false;
            }

            //---------------------------
            //画像ファイル
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspDefineImageItemValue"); ///"画像"
            componentId = frmName + ":" + "fileUploadArea";
            if (selectedRowData.getItemValue().isEmpty()) {
                //画像が未選択です。
                errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.SELECT_REQUIRED, funcId, itemName);
            }
            if (!errMsg.isEmpty()) {
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                errComponentIdList.add(componentId);
                bret = false;
                
                //ファイルエラーの画面（背景色）への反映はJavaScriptで行うので
                //ファイルエラーチェックの結果をCallbackParamで返す
                RequestContext reqContext = RequestContext.getCurrentInstance();
                reqContext.addCallbackParam("fileError", !bret);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("画像設定チェック失敗。", ex);
            return false;
        }

        return bret;
    }

}
