package jp.co.fujielectric.fss.data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import javax.enterprise.context.RequestScoped;
import jp.co.fujielectric.fss.entity.ApproveInfo;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendInfo;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * 履歴一覧表示データクラス
 * （送信履歴、受信履歴、承認履歴など）
 */
@Data
@RequestScoped
public class HistoryBean implements Serializable {

    /**
     * 送信メールのユニークID
     */
    private String uid;

    /**
     * 件名
     */
    private String subject;

    /**
     * 本文
     */
    private String content;

    /**
     * 送信元
     */
    private String sender;

    /**
     * 添付ファイル数
     */
    private int attachNum;

    /**
     * 添付ファイルの合計サイズ
     */
    private long fileSize;

    /**
     * 添付ファイルの合計サイズ（表示用）
     */
    private String fileSizeText;

    /**
     * 登録日
     */
    private Date registDate;
    private String registDateShort;
    private String registDateLong;
    private String registDateLongAddTime;

    /**
     * 送信日
     */
    private Date sendDate;
    private String sendDateShort;
    private String sendDateLong;
    private String sendDateLongAddTime;
    
    /**
     * 保存期限
     */
    private Date expirationDate;
    private String expirationDateShort;
    private String expirationDateLong;
    private String expirationDateLongAddTime;
    
    /**
     * 状況(未受領、受領、無害化処理中、取り消し、有効期限切れ、承認待ち)
     */
    private String status;

    /**
     * 状況（受領人数）
     */
    private String receiveCntText;

    /**
     * 状況（承認人数）
     */
    private String approveCntText;
    
    /**
     * 承認情報
     */
    private List<ApproveInfo> approveInfos;

    /**
     * 受信情報
     */
    private List<ReceiveInfo> receiveInfos;

    /**
     * 送信情報
     */
    private SendInfo sendInfo;

    /**
     * 承認情報ID
     */
    private String approveId;
    
    /**
     * rowStyle
     */
    private String rowStyle;

    /**
     * 選択可否
     */
    private boolean isSelected;
    /**
     * 送信取消
     */
    private boolean isSendCanceld;
    /**
     * ダウンロード
     */
    private boolean isDownload;
    /**
     * PW解除
     */
    private boolean isPwDecrypt;
    /**
     * 承認可否
     */
    private boolean isApproved;
    /**
     * 却下可否
     */
    private boolean isRejected;
    /**
     * 本文ライン表示
     */
    private boolean isContentLineDisp;
    /**
     * 承認者-本文ライン表示
     */
    private boolean isApproveContentLineDisp;
    
    /**
     * 非同期処理中フラグ
     * このフラグがtrueでReceiveInfoが存在しない場合は開始処理（非同期処理）が完了していないと判断する
     */
    private boolean asyncFlg = false;
    
    // コンストラクタ
    public HistoryBean() {
        this.isSelected = false;
        this.isSendCanceld = false;
        this.isDownload = false;
        this.isPwDecrypt = false;
        this.isContentLineDisp = true;
        this.isApproveContentLineDisp = true;
        this.isApproved = false;
        this.isRejected = false;
    }

    /**
     * 表示用-送信先
     * （受信情報.受信者名,受信メールアドレスから表示用-送信先を作成）
     *
     * @return 表示用-送信先
     */
    public String getReceiveAddressText() {
        String receiveAddressText;
        if (receiveInfos != null && !receiveInfos.isEmpty()) {
            receiveAddressText = receiveInfos.get(0).getReceiveUserName();
            if (StringUtils.isEmpty(receiveAddressText)) {  // 名称が取得できない場合、メールアドレスを設定
                receiveAddressText = receiveInfos.get(0).getReceiveMailAddress();
            }
        } else {
            if(asyncFlg){
                //非同期処理中（開始処理中）は送信先はまだ未登録なので何も表示しない
                receiveAddressText = "";
            }else{
                receiveAddressText = "（送信先なし）";
            }
        }
        return receiveAddressText;
    }

    /**
     * 表示用-送信先（残人数）
     * （受信情報数から、表示用-送信先に表示できない残人数を求め、表示用-送信先（残人数）を作成）
     *
     * @return 表示用-送信先（残人数）
     */
    public String getReceiveRemainText() {
        String receiveRemainText = "";
        if (receiveInfos != null && !receiveInfos.isEmpty()) {
            if (receiveInfos.size() > 1) {      // ２名以上の残人数を設定
                receiveRemainText = " (他" + String.valueOf(receiveInfos.size() - 1) + "名)";
            }
        }
        return receiveRemainText;
    }

    /**
     * 表示用-送信先（残人数幅）
     * （受信情報数から求めた、表示用-送信先（残人数）が空白か否かで、表示幅を設定）
     *
     * @return 表示用-送信先（残人数幅）
     */
    public String getReceiveRemainWidth() {
        if (StringUtils.isEmpty(getReceiveRemainText())) {
            return "0px";
        } else {
            return "65px";
        }
    }

    /**
     * 表示用-状況
     *
     * @return 表示用-状況
     */
    public String getStatusText() {
        String statusText = status;
        
        //承認待ちフラグ＝true の場合
        if (sendInfo!=null && sendInfo.isApprovalFlg()) {
            if (!StringUtils.isEmpty(approveCntText)) {
                statusText = statusText + approveCntText;
            }
        }
        //上記以外の場合
        else {
            if (!StringUtils.isEmpty(receiveCntText)) {
                statusText = statusText + receiveCntText;
            }
        }
        
        return statusText;
    }

    /**
     * 状況＝取り消し (送信履歴：送信側で取り消しを行った状態)
     *
     * @param _status
     */
    public void statusCancelSend(String _status) {
        this.status = _status;                              ///StatusItem.STATUS_CANCEL_SEND.getString();    ///"取り消し";
        this.isSelected = true;                             ///選択：可
        this.isSendCanceld = false;                         ///送信取消：不可
        this.rowStyle = DataTableBean.ROWSTYLE_DISABLED;    ///disabledRow
        this.isApproved = false;                            ///承認：不可
        this.isRejected = false;                            ///却下：不可
        
        this.receiveCntText = "";
        this.approveCntText = "";
    }

    /**
     * 状況＝取り消し (受信履歴：送信側で取り消しをされた状態)
     *
     * @param _status
     */
    public void statusCancelReceive(String _status) {
        this.status = _status;                              ///StatusItem.STATUS_CANCEL_RECEIVE.getString(); ///"取消";
        this.rowStyle = DataTableBean.ROWSTYLE_DISABLED;    ///disabledRow
    }

    /**
     * 状況＝有効期限切れ (送信・受信履歴：ダウンロードを行える有効期限が切れた状態)
     *
     * @param _status
     * @param isApproved    承認済か（承認済の場合、true）
     */
    public void statusExpiration(String _status, boolean isApproved) {
        this.status = _status;                              ///StatusItem.STATUS_EXPIRATION.getString();     ///"有効期限切れ";
        this.isSelected = true;                             ///選択：可
        this.isSendCanceld = true;                          ///送信取消：可
        this.rowStyle = DataTableBean.ROWSTYLE_DISABLED;    ///disabledRow
        this.isApproved = false;                            ///承認：不可
        this.isRejected = false;                            ///却下：不可
        
        this.receiveCntText = "";
        this.approveCntText = "";
    }

    /**
     * 状況＝無害化処理中 (送信・受信履歴：無害化が完了していない状態)
     *
     * @param _status
     */
    public void statusSanitize(String _status) {
        this.status = _status;                              ///StatusItem.STATUS_SANITIZE.getString();       ///"無害化処理中";
        this.isSelected = true;                             ///選択：可
        this.isSendCanceld = true;                          ///送信取消：可
        this.rowStyle = DataTableBean.ROWSTYLE_NONE;        ///スタイル無し
        this.isApproved = false;                            ///承認：不可
        this.isRejected = false;                            ///却下：不可（承認済なので、却下不可）
    }

    /**
     * 状況＝未受領 (送信履歴：無害化が完了しているが、まだダウンロードされていない状態)
     *
     * @param _status
     */
    public void statusReceiveNone(String _status) {
        this.status = _status;                              ///StatusItem.STATUS_RECEIVE_NONE.getString();   ///"未受領";
        this.isSelected = true;                             ///選択：可
        this.isSendCanceld = true;                          ///送信取消：可
        this.rowStyle = DataTableBean.ROWSTYLE_NONE;        ///スタイル無し
        this.isApproved = false;                            ///承認：不可
        this.isRejected = false;                            ///却下：不可（承認済なので、却下不可）
    }

    /**
     * 状況＝受領 (送信履歴：無害化が完了し、受領者１名以上がダウンロードを行った状態（内訳を括弧書きで補足する）)
     *
     * @param _status
     * @param cntReceived
     */
    public void statusReceive(String _status, int cntReceived) {
        this.status = _status;                              ///StatusItem.STATUS_RECEIVE.getString();        ///"受領";
        this.receiveCntText = " ( " + String.valueOf(cntReceived) + " / " + String.valueOf(receiveInfos.size()) + " 人)";
        this.isSelected = true;                             ///選択：可
        this.isSendCanceld = true;                          ///送信取消：可
        this.rowStyle = DataTableBean.ROWSTYLE_NONE;        ///スタイル無し
        this.isApproved = false;                            ///承認：不可
        this.isRejected = false;                            ///却下：不可（承認済なので、却下不可）
    }

    /**
     * 状況＝添付ファイル無 (受信履歴：ダウンロード可能なファイルが存在しない状態)
     *
     * @param _status
     */
    public void statusFileEmpty(String _status) {
        this.status = _status;                              ///StatusItem.STATUS_FILE_EMPTY.getString();     ///"添付ファイル無";
        this.isSelected = true;                             ///選択：可
        this.rowStyle = DataTableBean.ROWSTYLE_NONE;        ///スタイル無し
    }

    /**
     * 状況＝パスワード解除待ち (受信履歴：メールとして受信したが、パスワードがかかっていて無害化できない状態)
     *
     * @param _status
     */
    public void statusDecrypting(String _status) {
        this.status = _status;                              ///StatusItem.STATUS_DECRYPTING.getString();     ///"パスワード解除待ち";
        this.isSelected = true;                             ///選択：可
        this.isPwDecrypt = true;                            ///PW解除：可
        this.rowStyle = DataTableBean.ROWSTYLE_NONE;        ///スタイル無し
    }

    /**
     * 状況＝ダウンロード可能 (受信履歴：無害化が完了し、ダウンロードが可能な状態。ファイルはまだダウンロードしていない)
     *
     * @param _status
     */
    public void statusDownloadAble(String _status) {
        this.status = _status;                              ///StatusItem.STATUS_DOWNLOAD_ABLE.getString();  ///"ダウンロード可能";
        this.isSelected = true;                             ///選択：可
        this.isDownload = true;                             ///ダウンロード：可
        this.rowStyle = DataTableBean.ROWSTYLE_NONE;        ///スタイル無し
    }

    /**
     * 状況＝ダウンロード完了 (受信履歴：無害化が完了し、ダウンロードが可能な状態。ファイルはダウンロード済)
     *
     * @param _status
     */
    public void statusDownloadComplete(String _status) {
        this.status = _status;                              ///StatusItem.STATUS_DOWNLOAD_COMPLETE.getString();  ///"ダウンロード完了";
        this.isSelected = true;                             ///選択：可
        this.isDownload = true;                             ///ダウンロード：可
        this.rowStyle = DataTableBean.ROWSTYLE_NONE;        ///スタイル無し
    }

    /**
     * 状況＝承認待ち
     *
     * @param _status
     * @param cntApproved
     */
    public void statusApprove(String _status, int cntApproved) {
        int cntApprovedAll = 0;
        if (approveInfos!=null) { cntApprovedAll = approveInfos.size(); }
        
        this.status = _status;
        this.approveCntText = " ( " + String.valueOf(cntApproved) + " / " + String.valueOf(cntApprovedAll) + " 人)";
        this.isSelected = true;                             ///選択：可
        this.isSendCanceld = true;                          ///送信取消：可
        this.rowStyle = DataTableBean.ROWSTYLE_NONE;        ///スタイル無し
        this.isApproved = true;                             ///承認：可
        this.isRejected = true;                             ///却下：可
        
        //TODO
        //this.isDownload = true;                           ///ダウンロード：可
    }

    /**
     * 状況＝却下済み (承認履歴：承認者が承認却下を行った状態)
     *
     * @param _status
     * @param cntRejected
     */
    public void statusRejected(String _status, int cntRejected) {
        this.status = _status;
        this.isSelected = true;                             ///選択：可
        this.isSendCanceld = false;                         ///送信取消：不可
        this.rowStyle = DataTableBean.ROWSTYLE_DISABLED;    ///disabledRow
        this.isApproved = false;                            ///承認：不可
        this.isRejected = false;                            ///却下：不可
        
        this.receiveCntText = "";
        this.approveCntText = "";
    }

    /**
     * 本文ライン表示=ON
     */
    public void contentLineDispOn() {
        this.isContentLineDisp = true;
    }

    /**
     * 本文ライン表示=OFF（全文表示）
     */
    public void contentLineDispOff() {
        this.isContentLineDisp = false;
    }

    /**
     * 承認者-本文ライン表示=ON
     */
    public void approveContentLineDispOn() {
        this.isApproveContentLineDisp = true;
    }

    /**
     * 承認者-本文ライン表示=OFF（全文表示）
     */
    public void approveContentLineDispOff() {
        this.isApproveContentLineDisp = false;
    }

    /**
     * 本文セッター（空欄時スペース格納※JSエラー対処）
     * @param content
     */
    public void setContent(String content) {
        if(StringUtils.isEmpty(content)) {content = "　";}
        this.content = content;
    }
    
    /**
     * 承認履歴表示用rowKeyとなるユニークID
     * (承認履歴一覧の場合、同じuid(送信情報.ID)で複数の承認者が存在するので、ユニークとなるrowKeyをセット)
     * @return
     */
    public String getApproveUuid() {
        String approveUuid = uid + "_" + approveId;
        return approveUuid;
    }
    
    /**
     * 承認履歴表示用-送信元（送信情報.送信メールアドレス）
     * 
     * @return
     */
    public String getApproveFromAddressText() {
        String approveFromAddressText = "";
        if (sendInfo!=null) {
            if (!StringUtils.isEmpty(sendInfo.getSendUserName())) {
                approveFromAddressText = sendInfo.getSendUserName();
            } else {
                approveFromAddressText = sendInfo.getSendMailAddress();
            }
        }
        return approveFromAddressText;
    }
    
//    /**
//     * 承認履歴表示用-送信先
//     * （承認情報.承認者名,承認メールアドレスから承認履歴表示用-送信先を作成）
//     * 
//     * @return 承認履歴表示用-送信先
//     */
//    public String getApproveAddressText() {
//        String approveAddressText = "";
//        if (approveInfos != null && !approveInfos.isEmpty()) {
//            approveAddressText = approveInfos.get(0).getApproveUserName();
//            if (StringUtils.isEmpty(approveAddressText)) {  // 名称が取得できない場合、メールアドレスを設定
//                approveAddressText = approveInfos.get(0).getApproveMailAddress();
//            }
//        } else {
//            approveAddressText = "（承認先なし）";
//        }
//        return approveAddressText;
//    }
//
//    /**
//     * 承認履歴表示用-送信先（残人数）
//     * （承認情報数から、承認履歴表示用-送信先に表示できない残人数を求め、承認履歴表示用-送信先（残人数）を作成）
//     *
//     * @return 承認履歴表示用-送信先（残人数）
//     */
//    public String getApproveRemainText() {
//        String approveRemainText = "";
//        if (approveInfos != null && !approveInfos.isEmpty()) {
//            if (approveInfos.size() > 1) {      // ２名以上の残人数を設定
//                approveRemainText = " (他" + String.valueOf(approveInfos.size() - 1) + "名)";
//            }
//        }
//        return approveRemainText;
//    }
//
//    /**
//     * 承認履歴表示用-送信先（残人数幅）
//     * （承認情報数から求めた、承認履歴表示用-送信先（残人数）が空白か否かで、表示幅を設定）
//     *
//     * @return 承認履歴表示用-送信先（残人数幅）
//     */
//    public String getApproveRemainWidth() {
//        if (StringUtils.isEmpty(getApproveRemainText())) {
//            return "0px";
//        } else {
//            return "65px";
//        }
//    }
}
