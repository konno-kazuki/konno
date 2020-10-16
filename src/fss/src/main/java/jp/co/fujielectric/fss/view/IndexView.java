/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.view;

import javax.inject.Named;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.RoutingBean;
import jp.co.fujielectric.fss.common.ViewTrace;

/**
 * 索引ビュー
 *
 * @author nakai
 */
@AppTrace
@ViewTrace
@Named
@RequestScoped
public class IndexView {

    @Inject
    RoutingBean routingBean;

    /**
     * ターゲットアクション<br>
     * RoutingBeanに設定されたtargetへ自動遷移する
     *
     * @return
     */
    public String targetAction() {
        return routingBean.getTarget();
    }
}
