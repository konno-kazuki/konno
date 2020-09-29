package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.entity.MailLost;
import jp.co.fujielectric.fss.service.MailLostService;
import lombok.Getter;
import lombok.Setter;

/**
 * メールロスト履歴ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class MailLostHistoryView extends ManageCommonView implements Serializable {

    @Getter
    @Setter
    private List<MailLost> mailLostHistoryList;


    @Inject
    private MailLostService mailLostService;

    //コンストラクタ
    public MailLostHistoryView() {
        funcId = "mailLostHistory";
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
        super.initItems();
    }

    /**
     * お知らせ情報を取得
     */
    @Override
    protected void getItemList() {
        mailLostHistoryList = mailLostService.findAllForDisp();
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
    }
    
    /**
     * rowSelectイベントからの選択情報
     *
     * @return
     */
    @Override
    public String eventRowSelect() {
        return "";
    }


}
