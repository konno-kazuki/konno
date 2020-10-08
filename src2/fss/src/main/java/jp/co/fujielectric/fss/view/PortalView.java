/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.entity.Menu;
import jp.co.fujielectric.fss.service.MenuService;
import jp.co.fujielectric.fss.service.UserTypePermissionService;
import jp.co.fujielectric.fss.util.CommonUtil;
import lombok.Getter;
import lombok.Setter;

/**
 * 無害化サービスポータルビュークラス (BackingBean)
 *
 * @author nakai
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
@SuppressWarnings("serial")
public class PortalView extends CommonView implements Serializable {

    @Getter
    @Setter
    Menu menu1;
    @Getter
    @Setter
    Menu menu2;
    @Getter
    @Setter
    Menu menu3;
    @Getter
    @Setter
    Menu menu4;
    @Getter
    @Setter
    Menu menu5;
    @Getter
    @Setter
    Menu menu6;
    @Getter
    @Setter
    Menu menu7;
    @Getter
    @Setter
    Menu menu8;
    @Getter
    @Setter
    Menu menu9;

    @Inject
    MenuService menuService;

    @Inject
    UserTypePermissionService userTypePermissionService;

    //コンストラクタ
    public PortalView() {
        funcId = "portal";
    }

    @PostConstruct
    @Override
    public void init() {
        LOG.debug("PortalView.init");

        String section = CommonUtil.getSetting("section");

        List<Menu> _portal = menuService.findByPermission(10, commonBean.getUserType(), section);
        for (Menu var : _portal) {
            switch (var.getPosition()) {
                case 1:
                    menu1 = var;
                    break;
                case 2:
                    menu2 = var;
                    break;
                case 3:
                    menu3 = var;
                    break;
                case 4:
                    menu4 = var;
                    break;
                case 5:
                    menu5 = var;
                    break;
                case 6:
                    menu6 = var;
                    break;
                case 7:
                    menu7 = var;
                    break;
                case 8:
                    menu8 = var;
                    break;
                case 9:
                    menu9 = var;
                    break;
                default:
                    break;
            }
        }
    }

    public String forward(String link) {
        LOG.debug("PortalView.link:" + link);
        return link;
    }
}
