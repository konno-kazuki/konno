package jp.co.fujielectric.fss.data;

import java.io.Serializable;
import javax.enterprise.context.SessionScoped;
import javax.inject.Named;
import lombok.Getter;
import lombok.Setter;

/**
 * 共通クラス
 */
@Named
@SessionScoped
@SuppressWarnings("serial")
public class CommonBean implements Serializable{
    @Getter @Setter
    private String regionId;

    @Getter @Setter
    private String userId;

    @Getter @Setter
    private String onetimeId;

    @Getter @Setter
    private String userType = "";

    @Getter @Setter
    private boolean userTypeInternalFlg = false;

    @Getter @Setter
    private boolean loginFlg = false;

    @Getter @Setter
    private String loginName;

    @Getter @Setter
    private String mailAddress;
}
