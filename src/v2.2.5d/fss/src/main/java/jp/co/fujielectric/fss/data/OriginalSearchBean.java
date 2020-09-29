package jp.co.fujielectric.fss.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.RequestScoped;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * 原本検索条件クラス
 */
@Data
@RequestScoped
@SuppressWarnings("serial")
public class OriginalSearchBean implements Serializable {
    private List<Item> searchTypeList;                                          // 検索種別リスト
    private List<OriginalSearchForm> formList;                                  // 検索条件リスト
    private OriginalSearchResult selectedRowData;                               // 選択データ

    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private Map<String, String> labelMap = new HashMap<>();
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private Map<String, String> placeholderMap = new HashMap<>();
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private Map<String, String> helpmessageMap = new HashMap<>();

    public OriginalSearchBean() {
        labelMap.put(OriginalSearchForm.SearchColumn.sender.name(), "送信者");
        labelMap.put(OriginalSearchForm.SearchColumn.receiver.name(), "宛先");
        labelMap.put(OriginalSearchForm.SearchColumn.time.name(), "日時");
        labelMap.put(OriginalSearchForm.SearchColumn.subject.name(), "件名");
        labelMap.put(OriginalSearchForm.SearchColumn.content.name(), "本文");
        labelMap.put(OriginalSearchForm.SearchColumn.filename.name(), "添付ファイル名");
        labelMap.put(OriginalSearchForm.SearchColumn.error.name(), "処理不能メール");
    }

    public boolean isReady() {
        // 検索種別が取得できていれば準備ＯＫとする
        if(searchTypeList == null) { return false; }
        return (searchTypeList.size() > 0);
    }

    public void initData() {
        formList = new ArrayList<>();
        formList.add(new OriginalSearchForm());

        // 検索種別リストの初期化
        // （変数設定されている文字列にて生成。文字列を変更するには事前に変更すること）
        searchTypeList = new ArrayList<>();
        searchTypeList.add(new Item("",""));
        searchTypeList.add(new Item(OriginalSearchForm.SearchColumn.sender.name(), labelMap.get(OriginalSearchForm.SearchColumn.sender.name())));
        searchTypeList.add(new Item(OriginalSearchForm.SearchColumn.receiver.name(), labelMap.get(OriginalSearchForm.SearchColumn.receiver.name())));
        searchTypeList.add(new Item(OriginalSearchForm.SearchColumn.time.name(), labelMap.get(OriginalSearchForm.SearchColumn.time.name())));
        searchTypeList.add(new Item(OriginalSearchForm.SearchColumn.subject.name(), labelMap.get(OriginalSearchForm.SearchColumn.subject.name())));
        searchTypeList.add(new Item(OriginalSearchForm.SearchColumn.content.name(), labelMap.get(OriginalSearchForm.SearchColumn.content.name())));
        searchTypeList.add(new Item(OriginalSearchForm.SearchColumn.filename.name(), labelMap.get(OriginalSearchForm.SearchColumn.filename.name())));
//        searchTypeList.add(new Item(OriginalSearchForm.SearchColumn.error.name(), labelMap.get(OriginalSearchForm.SearchColumn.error.name())));
    }

    /**
     * 検索種別の最大文字列数を取得
     * @return 文字数
     */
    public int countSerachTypeLength() {
        int length = 0;
        for(Item searchType : searchTypeList) {
            if(length < searchType.getValue().length()) {
                length = searchType.getValue().length();
            }
        }
        return length;
    }

    /**
     * コピー
     *
     * @param originalSearchBean
     */
    public void copyBean(OriginalSearchBean originalSearchBean) {
        originalSearchBean.setSearchTypeList(searchTypeList);
        originalSearchBean.setFormList(formList);
        originalSearchBean.setSelectedRowData(selectedRowData);
    }

    /**
     * 検索文字列不要判定
     * @param key
     * @return 検索文字の要／不要
     */
    public boolean isWordOmit(String key) {
        try {
            if(OriginalSearchForm.SearchColumn.valueOf(key) == null) {
            } else switch(OriginalSearchForm.SearchColumn.valueOf(key)) {
                case error:
                    return true;
                default:
            }
            return false;
        }catch(Exception e) {
            return false;
        }
    }

    /**
     * 検索種別ラベルの設定
     * @param key
     * @param value
     */
    public void setLabel(String key, String value) {
        labelMap.put(key, value);
    }

    /**
     * 検索種別プレースホルダーの設定
     * @param key
     * @param value
     */
    public void setPlaceHolder(String key, String value) {
        placeholderMap.put(key, value);
    }

    /**
     * 検索種別プレースホルダーの取得
     * @param key
     * @return プレースホルダー文言
     */
    public String getPlaceHolder(String key) {
        String value = placeholderMap.get(key);
        if(value == null) { return ""; }
        return value;
    }

    /**
     * 検索種別ヘルプメッセージの設定
     * @param key
     * @param value
     */
    public void setHelpMessage(String key, String value) {
        helpmessageMap.put(key, value);
    }

    /**
     * 検索種別ヘルプメッセージの取得
     * @param key
     * @return ヘルプメッセージ文言
     */
    public String getHelpMessage(String key) {
        String value = helpmessageMap.get(key);
        if(value == null) { return ""; }
        return value;
    }
}
