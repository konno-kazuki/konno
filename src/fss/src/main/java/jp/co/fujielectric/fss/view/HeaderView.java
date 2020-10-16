/*
 *
 */
package jp.co.fujielectric.fss.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.entity.Func;
import jp.co.fujielectric.fss.entity.Menu;
import jp.co.fujielectric.fss.data.FuncBean;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.service.FuncService;
import jp.co.fujielectric.fss.service.MenuService;
import jp.co.fujielectric.fss.util.CommonUtil;
import static jp.co.fujielectric.fss.util.CommonUtil.getSetting;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * ヘッダビュー
 */
@Named
@RequestScoped
public class HeaderView extends CommonView {

    @Inject
    private FuncService funcService;

    @Inject
    private MenuService menuService;

    @Getter
    @Setter
    String target;
    @Setter
    List<FuncBean> funcList;
    @Getter
    Menu header;

    @Getter
    @Setter
    private boolean isHeaderEmpty = false;    //ヘッダー情報存在有無
    
//    @Getter
    private String helpURL;

//    @Getter
    private String faqURL;

    /**
     * サービス名称
     */
    @Getter
    private String headerTitle = "";
    
    @PostConstruct
    @Override
    public void init() {
        LOG.debug("HeaderView.init");

        String section = CommonUtil.getSetting("section");

        List<Menu> _headerLst = menuService.findByPermission(0, commonBean.getUserType(), section);
        if (!_headerLst.isEmpty()) {
            header = _headerLst.get(0);
            isHeaderEmpty = false;
        } else {
            header = new Menu();
            isHeaderEmpty = true;
        }
        
        //サービス名称
        //インターネット環境とLGWAN環境で表示を切替える（defineから取得）
        if(CommonUtil.isSectionLgwan())
        {
            //LGWAN環境 (define項目：「RegionTitleLGWAN」)
            headerTitle = itemHelper.findDefineStr("RegionTitleLGWAN");
        }else{
            //インターネット環境 (define項目：「RegionTitleInternet」)
            headerTitle = itemHelper.findDefineStr("RegionTitleInternet");            
        }
        if(headerTitle.isEmpty()){
            //defineにLGWAN用、インターネット用の項目が設定されていなかった場合
            // (define項目：「RegionTitle」)
            headerTitle = itemHelper.findDefineStr("RegionTitle");
        }
    }

    /**
     * target用機能リスト構築
     *
     * @param _target
     * @return 機能リスト
     */
    public List<FuncBean> getFuncList(String _target) {
        LOG.trace("getFuncList _target=" + _target);

        //複数回呼び出されることも考慮。必要時のみ、機能リスト構築を実施
        if (target != null && target.equals(_target) && funcList != null && !funcList.isEmpty()) {
            return funcList;
        }

        // 機能リスト構築
        createFuncList(_target);

        // HELP、FAQのURLを生成
        setLinkURL();

        return funcList;
    }

    /**
     * 操作方法（Help)のURL
     *
     * @param _target
     * @return
     */
    public String getHelpURL(String _target) {
        getFuncList(_target);
        return helpURL;
    }

    /**
     * よくある質問（FAQ)のURL
     *
     * @param _target
     * @return
     */
    public String getFaqURL(String _target) {
        getFuncList(_target);
        return faqURL;
    }

    /**
     * 機能リスト構築
     *
     * @param _target
     */
    private void createFuncList(String _target) {
        LOG.trace("loadFuncList start:" + _target);

        target = null;
        funcList = new ArrayList<>();
        Map<String, Func> maps = new HashMap<>();

        if (!StringUtils.isEmpty(_target)) {
            //機能マスタ情報を取得し、mapsにセット
            List<Func> funcDatas = funcService.findAll();
            if (funcDatas != null) {
                for (Func func : funcDatas) {
                    String _id = func.getId();
                    if (!maps.containsKey(_id)) {
                        maps.put(_id, func);
                    }
                }
            }

            //targetからparentidがnullになるまで遡る
            String sch_id = _target;
            int cnt = 0;
            if (maps.size() > 0) {
                while (!StringUtils.isEmpty(sch_id)) {
                    if (maps.containsKey(sch_id)) {
                        Func _func = maps.get(sch_id);

                        ///FuncBean作成
                        FuncBean add = new FuncBean(_func);

                        if (funcList.isEmpty()) {
                            funcId = add.getFuncId();       //対象ページのFuncID
                        } else {
                            add.setLinkFlg(true);           ///リンクフラグON
                        }
                        funcList.add(0, add);       ///最初に追加していく

                        ///次検索準備
                        sch_id = _func.getParentId();

                    } else {
                        sch_id = null;
                    }

                    ///カウンター（無限ループを防ぐ）
                    cnt++;
                    if (cnt > maps.size()) {
                        break;
                    }
                }
            }
        }

        //return
        target = _target;
        LOG.trace("loadFuncList end:" + _target + " 件数=" + funcList.size());
    }

    /**
     * 画面遷移
     *
     * @param link_target
     * @return 遷移先
     */
    public String forward(String link_target) {
        LOG.debug("HeaderView.link:" + link_target);
        return link_target;
    }

    /**
     * 操作方法(Help)、よくある質問（FAQ)のリンク先を生成
     */
    private void setLinkURL() {
        Item item;

        //コンテキスト外のコンテンツを参照することを考慮し、コンテキストパスも明示的に指定が必要とする
//        String contextPath = "/" + getSetting("contextpath");
        String contextPath = "";

        //HELP URL
        item = itemHelper.find(Item.HELP_URL, funcId);
        helpURL = item.getValue();
        if (helpURL.startsWith("/")) {
            helpURL = contextPath + helpURL;
        }

        //FAQ URL
        item = itemHelper.find(Item.FAQ_URL, funcId);
        faqURL = item.getValue();
        if (faqURL.startsWith("/")) {
            faqURL = contextPath + faqURL;
        }
    }
}
