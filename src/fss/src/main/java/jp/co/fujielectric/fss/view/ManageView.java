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
import jp.co.fujielectric.fss.util.CommonUtil;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理者メニュービュークラス (BackingBean)
 * 
 * @author nakai
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
@SuppressWarnings("serial")
public class ManageView extends CommonView implements Serializable {

    @Getter
    @Setter
    List<Menu> manageLst;

    @Inject
    MenuService menuService;

    //コンストラクタ
    public ManageView() {
        funcId = "manage";
    }

    @PostConstruct
    @Override
    public void init() {
        LOG.debug("ManageView.init");

        String section = CommonUtil.getSetting("section");

        manageLst = menuService.findByPermission(3, commonBean.getUserType(), section);
    }

    public String forward(String link) {
        LOG.debug("ManageView.link:" + link);
        return link;
    }
}
