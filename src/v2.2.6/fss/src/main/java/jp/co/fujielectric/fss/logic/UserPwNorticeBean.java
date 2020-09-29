package jp.co.fujielectric.fss.logic;

import jp.co.fujielectric.fss.data.ManageIdBean;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.enterprise.context.RequestScoped;
import javax.inject.Named;
import lombok.Data;

/**
 * ユーザーパスワード設定通知　データクラス
 */
@Named
@Data
@RequestScoped
public class UserPwNorticeBean implements Serializable {
    private List<ManageIdBean> manageIdList = new ArrayList<>();
    private String listFilter;
    private int first;
    private int rows;
    private int currentPage;

    public void clear() {
        manageIdList.clear();
        listFilter = "0";
        first = 0;
        rows = 10;
        currentPage = -1;
    }
}
