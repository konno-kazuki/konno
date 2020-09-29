/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Named;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.entity.Nortice;
import jp.co.fujielectric.fss.service.NorticeService;
import lombok.Getter;
import lombok.Setter;

/**
 * お知らせビュークラス (BackingBean)
 *
 */
@AppTrace
@ViewTrace
@Named
@RequestScoped
public class NorticeView implements Serializable {

    @Inject
    private NorticeService norticeService;

    @Getter
    private List<Nortice> norticeList;

    @Getter
    @Setter
    private Nortice selectedNortice = null;

    // コンストラクタ
    @PostConstruct
    public void init() {
        // お知らせ情報
        norticeList = norticeService.findAllByToday();
    }

    /**
     * お知らせ情報の有無を返す
     *
     * @return true:お知らせ無し / false:お知らせ有り
     */
    public boolean isNorticeEmpty() {
        return norticeList.isEmpty();
    }

}
