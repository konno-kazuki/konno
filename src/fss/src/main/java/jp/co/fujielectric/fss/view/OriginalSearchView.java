package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.util.List;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.PersistenceException;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.DataTableBean;
import jp.co.fujielectric.fss.data.OriginalSearchBean;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.data.OriginalSearchForm;
import jp.co.fujielectric.fss.data.OriginalSearchResult;
import jp.co.fujielectric.fss.logic.OriginalSearchLogic;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.context.RequestContext;

/**
 * 原本検索ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class OriginalSearchView extends CommonView implements Serializable {
    // <<詳細画面とのデータ連携用>>
    @Inject private DataTableBean originalSearchDataTableBean;
    @Inject private OriginalSearchBean originalSearchConditionBean;

    @Getter @Setter private DataTableBean dataTableBean = null;                 // 検索結果リスト設定
    @Getter @Setter private OriginalSearchBean searchBean = null;               // 原本検索条件
    @Getter @Setter private List<OriginalSearchResult> searchResultList = null; // 検索結果リスト

    private int searchCountLimit;                                               // 検索条件：条件上限
    @Getter private int searchCharMax;                                          // 検索条件：入力文字上限

    @Inject
    private OriginalSearchLogic originalSearchLogic;

    // コンストラクタ
    public OriginalSearchView() {
        funcId = "originalSearch";
    }

    /**
     * 画面毎の初期化
     */
    @Override
    public void initFunc() {
        if (originalSearchConditionBean.isReady()) {                            // 原本検索画面⇔原本検索詳細画面遷移の場合
            // 詳細画面とのデータ連携用の引継ぎ
            dataTableBean = new DataTableBean();
            originalSearchDataTableBean.copyBean(dataTableBean);
            searchBean = new OriginalSearchBean();
            originalSearchConditionBean.copyBean(searchBean);

            // 原本検索一覧のロード
            searchResultList = originalSearchLogic.searchOriginal(searchBean.getFormList());
        } else if (searchBean == null) {                                        // ポータルからの初回遷移のみ
            // 初期設定
            dataTableBean = new DataTableBean();
            dataTableBean.initOriginalSearch();
            dataTableBean.setRows(Integer.parseInt(itemHelper.find(Item.HISTORY_ROWS_DEFAULT, funcId).getValue())); // 一覧表示件数
            dataTableBean.setRowsPerPageTemplate(itemHelper.find(Item.HISTORY_ROWS_TEMPLATE, funcId).getValue());   // 一覧表示件数選択肢

            searchBean = new OriginalSearchBean();
            this.initOriginalSearchBean(searchBean);
        }
        // 検索条件：条件上限の取得
        try {
            searchCountLimit = Integer.parseInt(itemHelper.find(Item.SEARCH_COUNT_LIMIT, funcId).getValue());
        } catch (Exception e) {
            searchCountLimit = 50;
        }
        // 検索条件：入力文字上限の取得
        try {
            searchCharMax = Integer.parseInt(itemHelper.find(Item.SEARCH_CHAR_MAX, funcId).getValue());
        } catch (Exception e) {
            searchCharMax = 255;
        }
    }

    /**
     * 種別ガイド
     *
     * @return 種別ガイド
     */
    public String getTypeGuide() {
        String typeGuide = "";
        String crlf = "";
        for (Item searchType : searchBean.getSearchTypeList()) {
            for (OriginalSearchForm originalSearchForm : searchBean.getFormList()) {
                if (searchType.getKey().equals(originalSearchForm.getColumn())) {
                    String helpMsg = searchBean.getHelpMessage(searchType.getKey());
                    if (!StringUtils.isEmpty(helpMsg)) {
                        typeGuide = typeGuide + crlf + "［" + searchType.getValue() + "］" + helpMsg;
                        crlf = "\n";
                    }
                    break;
                }
            }
        }
        return typeGuide;
    }

    /**
     * 一覧表示有無
     *
     * @return 一覧表示有無
     */
    public boolean isShowList() {
        return (searchResultList != null);
    }

    /**
     * 一覧件数
     *
     * @return 一覧件数
     */
    public int listCount() {
        if (searchResultList != null) {
            return searchResultList.size();
        } else {
            return 0;
        }
    }

    /**
     * 抽出条件の削除
     *
     * @param searchForm
     */
    public void deleteSearchFormAction(OriginalSearchForm searchForm) {
        searchBean.getFormList().remove(searchForm);
        // １件は必ず残す（削除⇒追加で値をクリア）
        if (searchBean.getFormList().isEmpty()) searchBean.getFormList().add(new OriginalSearchForm());
    }

    /**
     * 抽出条件の追加
     *
     * @param searchForm
     */
    public void addSearchFormAction(OriginalSearchForm searchForm) {
        // 検索条件条件の判定
        if (searchBean.getFormList().size() >= searchCountLimit) return;

        OriginalSearchForm form = new OriginalSearchForm();
        form.setOperate(OriginalSearchForm.SearchOperate.and.name());
        searchBean.getFormList().add(form);
    }

    /**
     * 原本検索イベント
     */
    public void eventSearchOriginal() {
        if (checkInput()) {                                                     // 入力チェック
            // 原本検索一覧のロード
            searchResultList = originalSearchLogic.searchOriginal(searchBean.getFormList());
        } else {
            // チェックエラーが見つかった場合
            RequestContext req_context = RequestContext.getCurrentInstance();
            req_context.addCallbackParam("isError", true);
        }
    }

    /**
     * 入力チェック
     *
     * @return  チェック結果
     */
    private boolean checkInput() {
        FacesContext context = FacesContext.getCurrentInstance();

        boolean errFlg = false;

        try {
            // エラーリストクリア
            errComponentIdList.clear();

            int count = 0;
            for (OriginalSearchForm originalSearchForm : searchBean.getFormList()) {
                //---------------------------
                // 演算子
                //---------------------------
                if (StringUtils.isEmpty(originalSearchForm.getOperate())) {
                    // １行目はチェック不要
                    if(count != 0) {
                        String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.SELECT_REQUIRED, funcId, getItemCaption("dspOperate"));
                        context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(getItemCaption("dspOperate") + "(" + count + ")"), errMsg));
                        errComponentIdList.add("dispForm:filterList:"+count+":selectOperate");
                        errFlg = true;
                    }
                }
                //---------------------------
                // 種別
                //---------------------------
                if (StringUtils.isEmpty(originalSearchForm.getColumn())) {
                    String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.SELECT_REQUIRED, funcId, getItemCaption("dspColumn"));
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(getItemCaption("dspColumn") + "(" + count + ")"), errMsg));
                    errComponentIdList.add("dispForm:filterList:"+count+":selectType");
                        errFlg = true;
                }
                //---------------------------
                // 検索文字列
                //---------------------------
                if (StringUtils.isEmpty(originalSearchForm.getWord())) {
                    if (StringUtils.isEmpty(originalSearchForm.getColumn())) {
                    }else switch(originalSearchForm.getSearchColumn()) {
                        case sender:
                        case receiver:
                        case time:
                        case subject:
                        case content:
                        case filename:
                            String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_REQUIRED, funcId, getItemCaption("dspWord"));
                            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(getItemCaption("dspWord") + "(" + count + ")"), errMsg));
                            errComponentIdList.add("dispForm:filterList:"+count+":inputWord");
                            errFlg = true;
                            break;
                        default:
                            break;
                    }
                }
                count++;
            }


            if(errFlg) {
                return false;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("検索チェック失敗。", ex);
            return false;
        }

        return true;
    }

    /**
     * rowSelectイベントからの選択情報
     *
     * @return 遷移先
     */
    public String eventRowSelect() {
        // 詳細画面とのデータ連携用の引継ぎ
        dataTableBean.copyBean(originalSearchDataTableBean);
        searchBean.copyBean(originalSearchConditionBean);

        // 詳細画面へ遷移
        return "originalSearchDetail";
    }

    /**
     * OriginalSearchBeanの初期値設定
     * @param searchBean
     */
    private void initOriginalSearchBean(OriginalSearchBean searchBean) {
        try {
            // プルダウンラベルの設定
            {
                searchBean.setLabel(OriginalSearchForm.SearchColumn.sender.name(), getItemCaption("dspColumnSender"));
                searchBean.setLabel(OriginalSearchForm.SearchColumn.receiver.name(), getItemCaption("dspColumnReceiver"));
                searchBean.setLabel(OriginalSearchForm.SearchColumn.time.name(), getItemCaption("dspColumnTime"));
                searchBean.setLabel(OriginalSearchForm.SearchColumn.subject.name(), getItemCaption("dspColumnSubject"));
                searchBean.setLabel(OriginalSearchForm.SearchColumn.content.name(), getItemCaption("dspColumnContent"));
                searchBean.setLabel(OriginalSearchForm.SearchColumn.filename.name(), getItemCaption("dspColumnFilename"));
                searchBean.setLabel(OriginalSearchForm.SearchColumn.error.name(), getItemCaption("dspColumnError"));
            }
            // プレースホルダーの設定
            {
                searchBean.setPlaceHolder(OriginalSearchForm.SearchColumn.sender.name(), getItemCaption("dspPlaceHolderSender"));
                searchBean.setPlaceHolder(OriginalSearchForm.SearchColumn.receiver.name(), getItemCaption("dspPlaceHolderReceiver"));
                searchBean.setPlaceHolder(OriginalSearchForm.SearchColumn.time.name(), getItemCaption("dspPlaceHolderTime"));
                searchBean.setPlaceHolder(OriginalSearchForm.SearchColumn.subject.name(), getItemCaption("dspPlaceHolderSubject"));
                searchBean.setPlaceHolder(OriginalSearchForm.SearchColumn.content.name(), getItemCaption("dspPlaceHolderContent"));
                searchBean.setPlaceHolder(OriginalSearchForm.SearchColumn.filename.name(), getItemCaption("dspPlaceHolderFilename"));
                searchBean.setPlaceHolder(OriginalSearchForm.SearchColumn.error.name(), getItemCaption("dspPlaceHolderError"));
            }
            // ガイドの設定
            {
                searchBean.setHelpMessage(OriginalSearchForm.SearchColumn.sender.name(), getItemCaption("dspHelpMessageSender"));
                searchBean.setHelpMessage(OriginalSearchForm.SearchColumn.receiver.name(), getItemCaption("dspHelpMessageReceiver"));
                searchBean.setHelpMessage(OriginalSearchForm.SearchColumn.time.name(), getItemCaption("dspHelpMessageTime"));
                searchBean.setHelpMessage(OriginalSearchForm.SearchColumn.subject.name(), getItemCaption("dspHelpMessageSubject"));
                searchBean.setHelpMessage(OriginalSearchForm.SearchColumn.content.name(), getItemCaption("dspHelpMessageContent"));
                searchBean.setHelpMessage(OriginalSearchForm.SearchColumn.filename.name(), getItemCaption("dspHelpMessageFilename"));
                searchBean.setHelpMessage(OriginalSearchForm.SearchColumn.error.name(), getItemCaption("dspHelpMessageError"));
            }
        } catch(PersistenceException e) {
            // データ取得失敗時は警告を発生し、そのまま実行
            LOG.warn("(initOriginalSearchBean)データ取得に失敗しました");
        }

        searchBean.initData();
    }
}
