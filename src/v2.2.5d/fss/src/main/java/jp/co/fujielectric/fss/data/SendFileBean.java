package jp.co.fujielectric.fss.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.enterprise.context.RequestScoped;
import javax.inject.Named;
import javax.mail.internet.InternetAddress;
import jp.co.fujielectric.fss.entity.SendInfo;
import lombok.Getter;
import lombok.Setter;

/**
 * 送信ファイル共通データクラス
 */
@Named
@RequestScoped
public class SendFileBean implements Serializable {
    @Getter @Setter private SendInfo sendInfo;
    @Getter @Setter private List<InternetAddress> mailToList;
    @Getter @Setter private List<FileInfoBean> fileInfoList;
    @Getter @Setter private String uuid;
    @Getter @Setter private boolean continueFlg;
    @Getter @Setter private String destination;
    @Getter @Setter private String currentFuncId;
    @Getter @Setter private List<InternetAddress> mailApproveList;

    public void clear() {
        sendInfo = null;
        mailToList = new ArrayList<>();
        fileInfoList = new ArrayList<>();
        uuid = "";
        continueFlg = false;
        destination = "";
        currentFuncId = "";
        mailApproveList = new ArrayList<>();
    }
}
