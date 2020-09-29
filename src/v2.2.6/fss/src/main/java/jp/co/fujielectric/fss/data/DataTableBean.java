package jp.co.fujielectric.fss.data;

import java.io.Serializable;
import java.util.Date;
import javax.enterprise.context.RequestScoped;
import lombok.Data;

/**
 * dataTable用データクラス
 */
@Data
@RequestScoped
public class DataTableBean implements Serializable {

    private static final String SORTORDER_ASC = "ascending";
    private static final String SORTORDER_DSC = "descending";
    private static final String SELECTIONMODE_SINGLE = "single";
    private static final String SELECTIONMODE_MULT = "multiple";

    public static final String ROWSTYLE_NONE = "";
    public static final String ROWSTYLE_DISABLED = "disabledRow";
    public static final String ROWSTYLE_DISABLED_RED = "disabledRowRed";
    public static final String ROWSTYLE_SELECTED = "selectedRow";

    /**
     * リクエスト("","save"など)
     */
    private String req;
    /**
     * (コンボボックスで選択できる)1ページ当たりの行数
     */
    private String rowsPerPageTemplate;
    /**
     * 一覧表示件数(コンボボックスで選択された数値）
     */
    private int rows;
    /**
     * ページナビゲーター
     */
    private boolean paginator;
    /**
     * ページング表示内容
     */
    private String paginatorTemplate;
    /**
     * 選択モード
     */
    private String selectionMode;
    /**
     * 最初のデータを示す属性(0～)
     */
    private int first;
    /**
     * 現在ページ
     */
    private int currentPage;

    /* 日付検索条件 [v2.2.4] */   
    /**
     * 検索条件 日付From
     */
    private Date searchTimeFrom = new Date();
    /**
     * 検索条件 日付To
     */
    private Date searchTimeTo = new Date();    
    
    /**
     * カレントページ
     */
    //String sortBy;                      /** ソート項目 */
    //String sortOrder;                   /** (sortByに対する)ソート */
    // コンストラクタ
    public DataTableBean() {
        this.req = "";                              //リクエスト
        this.rowsPerPageTemplate = "";              //(コンボボックスで選択できる)1ページ当たりの行数
        this.rows = -1;                             //一覧表示件数(コンボボックスで選択された数値）
        this.currentPage = -1;                      //カレントページ
        this.first = -1;                            //最初のデータを示す属性(1頁目)
        this.paginator = false;                     //ページナビゲーター
        this.paginatorTemplate = "";                //ページング表示内容
        this.selectionMode = SELECTIONMODE_SINGLE;  //選択モード...１選択
        //this.sortBy = "";                         //ソート項目
        //this.sortOrder = SORTORDER_ASC;           //(sortByに対する)ソート
    }

    /**
     * 初期表示[送信履歴/受信履歴]設定
     */
    public void initHistory() {
        setByHistory("");
    }

    /**
     * 初期表示[原本検索]設定
     */
    public void initOriginalSearch() {
        setByHistory("");
    }

    /**
     * 初期表示[ＩＤ管理]設定
     */
    public void initManageId() {
        setByManage("");
    }

    /**
     * 初期表示[機能設定]設定
     */
    public void initManageConfig() {
        setByManage("");
    }

    /**
     * 初期表示[共有ファイルフォルダ]設定
     */
    public void initCommonDirectory() {
        setByCommonDir("");
    }

    /**
     * 送信・受信履歴用設定
     *
     * @param req 保管するリクエスト
     */
    private void setByHistory(String req) {

        this.req = req;

        // 一覧表示件数は呼出し側(View)でセットする
//        //(コンボボックスで選択できる)1ページ当たりの行数
//        this.rowsPerPageTemplate = "5,10,15";
//        //一覧表示件数(コンボボックスで選択された数値）
//        this.rows = 10;
        //カレントページ
        this.currentPage = 1;
        //最初のデータを示す属性(1頁目=0、2頁目=init_rows、3頁目=init_rows*2)
//        this.first = (currentPage - 1) * this.rows;
        this.first = 0;
        //ページナビゲーター
        this.paginator = true;
        //ページング表示内容
        //(ex:[(1 of 3)][|<][<<][ページボタン][>>][>|][ページあたりの行数選択コンボボックス]
        this.paginatorTemplate = "{CurrentPageReport} {FirstPageLink} {PreviousPageLink} {PageLinks} {NextPageLink} {LastPageLink} {RowsPerPageDropdown}";
        //選択モード...１選択
        this.selectionMode = SELECTIONMODE_SINGLE;
    }

    /**
     * 管理機能用設定
     *
     * @param req 保管するリクエスト
     */
    private void setByManage(String req) {

        this.req = req;

        // 一覧表示件数は呼出し側(View)でセットする
//        //(コンボボックスで選択できる)1ページ当たりの行数
//        this.rowsPerPageTemplate = "5,10,15";
//        //一覧表示件数(コンボボックスで選択された数値）
//        this.rows = 5;
        //カレントページ
        this.currentPage = 1;
        //最初のデータを示す属性(1頁目=0、2頁目=init_rows、3頁目=init_rows*2)
        this.first = 0;
        //ページナビゲーター
        this.paginator = true;
        //ページング表示内容
        //(ex:[(1 of 3)][|<][<<][ページボタン][>>][>|][ページあたりの行数選択コンボボックス]
        this.paginatorTemplate = "{CurrentPageReport} {FirstPageLink} {PreviousPageLink} {PageLinks} {NextPageLink} {LastPageLink} {RowsPerPageDropdown}";
        //選択モード...１選択
        this.selectionMode = SELECTIONMODE_SINGLE;
    }

    /**
     * 共有ファイルフォルダ機能用設定
     *
     * @param req 保管するリクエスト
     */
    private void setByCommonDir(String req) {

        this.req = req;

        // 一覧表示件数は呼出し側(View)でセットする
//        //(コンボボックスで選択できる)1ページ当たりの行数
//        this.rowsPerPageTemplate = "5,10,15";
//        //一覧表示件数(コンボボックスで選択された数値）
//        this.rows = 5;
        //カレントページ
        this.currentPage = 1;
        //最初のデータを示す属性(1頁目=0、2頁目=init_rows、3頁目=init_rows*2)
        this.first = 0;
        //ページナビゲーター
        this.paginator = true;
        //ページング表示内容
        //(ex:[(1 of 3)][|<][<<][ページボタン][>>][>|][ページあたりの行数選択コンボボックス]
        this.paginatorTemplate = "{CurrentPageReport} {FirstPageLink} {PreviousPageLink} {PageLinks} {NextPageLink} {LastPageLink} {RowsPerPageDropdown}";
        //選択モード...複数選択
        this.selectionMode = SELECTIONMODE_SINGLE;
    }

    /**
     * 選択モード＝１選択
     */
    public void selectionModeSingle() {
        this.selectionMode = SELECTIONMODE_SINGLE;
    }

    /**
     * 選択モード＝複数選択
     */
    public void selectionModeMultiple() {
        this.selectionMode = SELECTIONMODE_MULT;
    }

    /**
     * dataTable用sortOrder昇順
     *
     * @return 昇順
     */
    public String sortOrderAsc() {
        return SORTORDER_ASC;
    }

    /**
     * dataTable用sortOrder降順
     *
     * @return 降順
     */
    public String sortOrderDesc() {
        return SORTORDER_DSC;
    }

    /**
     * コピー
     * 
     * @param dataTableBean
     */
    public void copyBean(DataTableBean dataTableBean) {
        dataTableBean.setReq(req);
        dataTableBean.setRowsPerPageTemplate(rowsPerPageTemplate);
        dataTableBean.setRows(rows);
        dataTableBean.setCurrentPage(currentPage);
        dataTableBean.setFirst(first);
        dataTableBean.setPaginator(paginator);
        dataTableBean.setPaginatorTemplate(paginatorTemplate);
        dataTableBean.setSelectionMode(selectionMode);
        
        /* 日付検索条件 [v2.2.4] */
        dataTableBean.setSearchTimeFrom(getSearchTimeFrom());
        dataTableBean.setSearchTimeTo(getSearchTimeTo());
    }
}
