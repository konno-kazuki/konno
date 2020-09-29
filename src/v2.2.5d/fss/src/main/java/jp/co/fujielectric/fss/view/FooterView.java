/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Named;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import org.apache.logging.log4j.Logger;
import org.primefaces.context.RequestContext;

/**
 * フッター制御用ビュークラス
 * @author nakai
 */
@Named
@RequestScoped
public class FooterView {

    @Inject
    Logger LOG;

    /**
     * その他画面ダイアログを表示
     * @param target
     */
    public void showExtension(String target) {
        // Dialogのoption設定
        Map<String,Object> options = new HashMap<>();
        options.put("modal", true);
        options.put("headerElement", "extensionHeader");

        // Dialogのparameter設定
        Map<String, List<String>> params = new HashMap<>();
        List<String> values = new ArrayList<>();

        values.add(target);
        params.put("funcid", values);

        RequestContext.getCurrentInstance().openDialog("extension", options, params);
    }

}
