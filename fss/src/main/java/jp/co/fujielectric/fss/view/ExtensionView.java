/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.inject.Named;
import javax.enterprise.context.RequestScoped;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.service.FuncService;
import jp.co.fujielectric.fss.logic.ItemHelper;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.Logger;

/**
 * その他画面ダイアログ制御用ビュークラス
 *
 * @author nakai
 */
@AppTrace
@ViewTrace
@Named
@RequestScoped
public class ExtensionView {

    @Inject
    Logger LOG;

    @Inject
    FuncService funcService;

    @Inject
    ItemHelper itemHelper;

    @Getter
    @Setter
    private String header;

    @Getter
    @Setter
    private List<Item> items;

    @PostConstruct
    public void init() {

        Map<String, String> parameter = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
        String funcid = parameter.get("funcid");

        header = funcService.find(funcid).getName();

        // funcidに応じてDBから取得
        items = new ArrayList<>();
        try {
            {
                Item item = new Item("title", itemHelper.findDispMessage("title", funcid).getValue());
                items.add(item);
            }
            {
                Item item = new Item("caption", itemHelper.findDispMessage("caption", funcid).getValue());
                items.add(item);
            }
            {
                Item item = new Item("article", itemHelper.findDispMessage("article", funcid).getValue());
                items.add(item);
            }
        } catch (Exception e) {
            LOG.warn("item not found:" + e.getMessage());
            items = new ArrayList<>();
            items.add((new Item("", "未設定")));
        }
    }
}
