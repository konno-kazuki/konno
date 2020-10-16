package jp.co.fujielectric.fss.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.enterprise.context.RequestScoped;
import javax.inject.Named;
import javax.mail.internet.InternetAddress;
import jp.co.fujielectric.fss.entity.SendRequestInfo;
import lombok.Getter;
import lombok.Setter;

/**
 * 送信依頼共通データクラス
 */
@Named
@RequestScoped
public class SendRequestFileBean implements Serializable {

    @Getter
    @Setter
    private SendRequestInfo sendRequestInfo;
    @Getter
    @Setter
    private InternetAddress sendMailTo;
    @Getter
    @Setter
    private List<InternetAddress> receiveMailToList;
//    @Getter
//    @Setter
//    private List<SendRequestTo> sendRequestToList;
    @Getter
    @Setter
    private List<FileInfoBean> fileInfoList;
    @Getter
    @Setter
    private String uuid;
    @Getter
    @Setter
    private boolean continueFlg;

    public void clear() {
        sendRequestInfo = null;
        sendMailTo = null;
        receiveMailToList = new ArrayList<>();
//        sendRequestToList = new ArrayList<>();
        fileInfoList = new ArrayList<>();
        uuid = "";
        continueFlg = false;
    }
}
