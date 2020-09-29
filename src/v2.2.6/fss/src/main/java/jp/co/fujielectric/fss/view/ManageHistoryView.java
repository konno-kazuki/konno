package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.entity.Config;
import jp.co.fujielectric.fss.entity.ReceiveFile;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendFile;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.logic.FileDownload;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.service.SendInfoService;
import jp.co.fujielectric.fss.util.DateUtil;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.context.RequestContext;
import org.primefaces.model.StreamedContent;

/**
 * 履歴出力ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class ManageHistoryView extends CommonView implements Serializable {

    @Getter
    @Setter
    private List<Config> historyTypeList = new ArrayList<>();     //出力履歴リスト

    @Getter
    @Setter
    private Date dateFrom;              //(検索)期間（From）
    @Getter
    @Setter
    private Date dateTo;                //(検索)期間 (To)
    @Getter
    @Setter
    private String schUserId;           //(検索)ユーザID
    @Getter
    @Setter
    private String selectHistory;       //(検索)出力履歴
    @Getter
    @Setter
    private boolean isDownLoad = false; //ダウンロード可否
    @Getter
    @Setter
    StreamedContent downloadFile;       //ダウンロードファイル
    @Getter

    private String placeholderVal;

    @Inject
    private SendInfoService sendInfoService;

    @Inject
    private ReceiveInfoService receiveInfoService;

    //種別名
    protected String nameHistoryTypeSend;
    protected String nameHistoryTypeReceive;

    //callbackParam
    private final String callbackParam_Error = "isError";
    private final String callbackParam_Download = "isDownLoad";

    //履歴種別
    private final String KEY_HISTORY_TYPE_SEND = "1";
    private final String KEY_HISTORY_TYPE_RECEIVE = "2";
    private final String DSP_HISTORY_TYPE_SEND = "dspHistoryTypeSend";
    private final String DSP_HISTORY_TYPE_RECEIVE = "dspHistoryTypeReceive";

    //カレンダーのプレースホルダ値
    private final String DSP_PLACEHOLDER_DATE = "dspPlaceholderDate";

    //コンストラクタ
    public ManageHistoryView() {
        funcId = "manageHistory";
    }

    /**
     * 画面毎の初期化
     *
     */
    @Override
    public void initFunc() {

        LOG.debug("initFunc start");
        LOG.debug("onetimeId = " + commonBean.getOnetimeId());
        LOG.debug("userId = " + commonBean.getUserId());

        // 種別名
        nameHistoryTypeSend = getItemCaption(DSP_HISTORY_TYPE_SEND);
        nameHistoryTypeReceive = getItemCaption(DSP_HISTORY_TYPE_RECEIVE);

        // 出力履歴リスト
        historyTypeList.add(createSelectItem(KEY_HISTORY_TYPE_SEND, nameHistoryTypeSend));
        historyTypeList.add(createSelectItem(KEY_HISTORY_TYPE_RECEIVE, nameHistoryTypeReceive));

        // カレンダーのプレースホルダ値（"例）" + _strDate）
        Date _sysDate = DateUtil.getSysDate();
        DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.JAPAN);
        String _strDate = df.format(_sysDate);
        placeholderVal = itemHelper.findDispMessageStr(DSP_PLACEHOLDER_DATE, funcId, _strDate);

        // 終了
        LOG.debug("initFunc end");
    }

    /**
     * 出力帳票変更イベント
     */
    public void eventChangeOutType() {
        LOG.debug("eventChangeOutType:出力履歴＝" + selectHistory);

        FacesContext context = FacesContext.getCurrentInstance();
        RequestContext req_context = RequestContext.getCurrentInstance();

        // 入力チェック(bret:true=OK,false=NG)
        boolean bDefault = false;   ///未選択時の初期値をセットしない
        boolean bret = checkInput(context, getSchDateFrom(bDefault), getSchDateTo(bDefault));
        if (!bret) {
            ///チェックエラーが見つかった場合
            req_context.addCallbackParam(callbackParam_Error, !bret);
        }
    }

    /**
     * CSV出力用文字列を取得
     *
     * @param str
     *
     * @return CSV出力用文字列
     */
    private String getCsvString(String str) {

        // CSV出力用文字列
        String _outputStr = "";
        if (!StringUtils.isEmpty(str)) {
            _outputStr = str;

            ///ダブルコーテーション１つに対し、ダブルコーテーションが２つになるよう変換
            {
                _outputStr = _outputStr.replaceAll("\"", "\"\"");
            }
        }

        //カンマを考慮し、ダブルコーテーションで囲む
        _outputStr = "\"" + _outputStr + "\"";

        //return
        return _outputStr;
    }

    /**
     * 日付入力パターン
     *
     * @return 日付入力パターン
     */
    public String getDateInputPattern() {
        return "yyyy/MM/dd";
    }

    /**
     * 日付出力パターン
     *
     * @return 日付出力パターン
     */
    private String getDateOutPattern() {
        return "yyyy/MM/dd HH:mm:ss";
//        return "yyyy年MM月dd日（EEE）";
    }

    /**
     * 検索用-対象期間(開始日)を取得
     *
     * @param bDefault 未選択時の初期値をセットするか(true=未選択時、2000年1月1日)
     * @return 検索用-対象期間(開始日)
     */
    private Date getSchDateFrom(boolean bDefault) {
        if (!bDefault && dateFrom == null) {
            return null;
        }

        Date _dateFrom = dateFrom;
        if (_dateFrom == null) {
            Calendar cal = Calendar.getInstance();
            cal.set(2000, 0, 1);    //2000年1月1日
            _dateFrom = cal.getTime();
        }
        return _dateFrom;
    }

    /**
     * 検索用-対象期間(終了日)を取得
     *
     * @param bDefault 未選択時の初期値をセットするか(true=未選択時、9999年12月31日)
     * @return 検索用-対象期間(終了日)
     */
    private Date getSchDateTo(boolean bDefault) {
        if (!bDefault && dateTo == null) {
            return null;
        }

        Date _dateTo = dateTo;
        if (_dateTo == null) {
            Calendar cal = Calendar.getInstance();
            cal.set(9999, 11, 31);    //9999年12月31日
            _dateTo = cal.getTime();
        }
        return _dateTo;
    }

    private String getUnicodeAddress(String address) {
        String unicodeAddress = "";
        String separator = "";
        InternetAddress[] iaList;
        try {
            iaList = InternetAddress.parse(address);
            for (InternetAddress ia : iaList) {
                if (StringUtils.isEmpty(ia.getPersonal())) {
                    unicodeAddress += separator + ia.getAddress();
                } else {
                    unicodeAddress += separator + ia.toUnicodeString();
                }
                separator = ", ";
            }
        } catch (AddressException ex) {
            unicodeAddress = address;
        }
        return unicodeAddress;
    }

    /**
     * 送信履歴情報取得
     *
     * @param sendUserId (検索)ユーザID
     * @param dateFrom (検索)期間-開始
     * @param dateTo (検索)期間-終了
     *
     * @return
     */
    private FileDownload findSendHistory(String sendUserId, Date dateFrom, Date dateTo) {

        FileDownload fd = new FileDownload();

        //送信履歴情報取得
        List<SendInfo> sendInfoDatas = sendInfoService.findForSendHistoryOutput(sendUserId, dateFrom, dateTo);
        if (sendInfoDatas != null && sendInfoDatas.size() > 0) {

            //ヘッダ
            fd.addOneData("id");    ///"ID"という文言でCSV出力した場合、[破損警告]が出るので使用しないこと
            fd.addOneData("送信者ID");
            fd.addOneData("送信メールアドレス");
            fd.addOneData("送信者名");
            fd.addOneData("送信日時");

            fd.addOneData("宛先アドレス");
            fd.addOneData("CCアドレス");
            fd.addOneData("Fromアドレス");

            fd.addOneData("件名");
            fd.addOneData("本文");
            fd.addOneData("保存期限");

            fd.addOneData("送信ファイル数");
            fd.addOneData("サイズ（B）");

            fd.addNewLine();

            //日付出力パターン
            SimpleDateFormat sdfLong = new SimpleDateFormat(getDateOutPattern());

            //データ
            for (SendInfo sendInfo : sendInfoDatas) {

                ///ID
                fd.addOneData(getCsvString(sendInfo.getId()));
                ///送信者ID
                fd.addOneData(getCsvString(sendInfo.getSendUserId()));
                ///送信メールアドレス
                fd.addOneData(getCsvString(sendInfo.getSendMailAddress()));
                ///送信者名
                fd.addOneData(getCsvString(sendInfo.getSendUserName()));
                ///送信日時
                String _sendTime = "";
                if (sendInfo.getSendTime() != null) {
                    _sendTime = sdfLong.format(sendInfo.getSendTime());
                }
                fd.addOneData(getCsvString(_sendTime));

                ///宛先アドレス
                fd.addOneData(getCsvString(getUnicodeAddress(sendInfo.getToAddress())));
                ///CCアドレス
                fd.addOneData(getCsvString(getUnicodeAddress(sendInfo.getCcAddress())));
                ///Fromアドレス
                fd.addOneData(getCsvString(getUnicodeAddress(sendInfo.getFromAddress())));

                ///件名
                fd.addOneData(getCsvString(sendInfo.getSubject()));
                ///本文
                fd.addOneData(getCsvString(sendInfo.getContent()));
                ///保存期限
                String _expirationTime = "";
                if (sendInfo.getExpirationTime() != null) {
                    _expirationTime = sdfLong.format(sendInfo.getExpirationTime());
                }
                fd.addOneData(getCsvString(_expirationTime));

                ///送信ファイル
                int _attach_num = 0;                            ///添付ファイル数
                long _fileSizeB = 0;                            ///添付ファイルの合計サイズ(B)
                List<SendFile> sendFiles = sendInfo.getSendFiles();
                for (SendFile sendFile : sendFiles) {
                    _fileSizeB = _fileSizeB + sendFile.getFileSize();
                    _attach_num++;
                }
                fd.addOneData(getCsvString(String.valueOf(_attach_num)));
                fd.addOneData(getCsvString(String.valueOf(_fileSizeB)));

                ///改行
                fd.addNewLine();
            }
        }

        //return
        return fd;
    }

    /**
     * 受信履歴情報取得
     *
     * @param schUserId (検索)ユーザID
     * @param dateFrom (検索)期間-開始
     * @param dateTo (検索)期間-終了
     *
     * @return
     */
    private FileDownload findReceiveHistory(String schUserId, Date dateFrom, Date dateTo) {

        FileDownload fd = new FileDownload();

        //受信履歴情報取得
        List<ReceiveInfo> receiveInfoDatas = receiveInfoService.findForReceiveHistoryOutput(schUserId, dateFrom, dateTo);
        if (receiveInfoDatas != null && receiveInfoDatas.size() > 0) {

            //ヘッダ
            fd.addOneData("id");        ///"ID"という文言でCSV出力した場合、[破損警告]が出るので使用しないこと
            fd.addOneData("受信者ID");
            fd.addOneData("受信メールアドレス");
            fd.addOneData("受信者名");
            fd.addOneData("登録日時");

            fd.addOneData("件名");
            fd.addOneData("本文");
            fd.addOneData("送信元");
            fd.addOneData("保存期限");

            fd.addOneData("受信ファイル数");
            fd.addOneData("サイズ（B）");
            fd.addOneData("ダウンロード数");

            fd.addNewLine();

            //日付出力パターン
            SimpleDateFormat sdfLong = new SimpleDateFormat(getDateOutPattern());

            //データ
            for (ReceiveInfo receiveInfo : receiveInfoDatas) {
                SendInfo sendInfo = receiveInfo.getSendInfo();

                ///ID
                fd.addOneData(getCsvString(receiveInfo.getId()));
                ///受信者ID
                fd.addOneData(getCsvString(receiveInfo.getReceiveUserId()));
                ///受信メールアドレス
                fd.addOneData(getCsvString(receiveInfo.getReceiveMailAddress()));
                ///受信者名
                fd.addOneData(getCsvString(receiveInfo.getReceiveUserName()));

                ///送信日時
                String _sendTime = "";
                if (receiveInfo.getSendTime() != null) {
                    _sendTime = sdfLong.format(receiveInfo.getSendTime());
                }
                fd.addOneData(getCsvString(_sendTime));

                ///件名、本文、送信元、保存期限
                String _subject = "";
                String _content = "";
                String _sendUserName = "";
                String _expirationTime = "";
                if (sendInfo != null) {
                    _subject = sendInfo.getSubject();
                    _content = sendInfo.getContent();
                    _sendUserName = sendInfo.getSendUserName();
                    if (sendInfo.getExpirationTime() != null) {
                        _expirationTime = sdfLong.format(sendInfo.getExpirationTime());
                    }
                }
                fd.addOneData(getCsvString(_subject));
                fd.addOneData(getCsvString(_content));
                fd.addOneData(getCsvString(_sendUserName));
                fd.addOneData(getCsvString(_expirationTime));

                ///受信ファイル
                int _attach_num = 0;                            ///添付ファイル数
                long _fileSizeB = 0;                            ///添付ファイルの合計サイズ(B)
                long _downloadCount = 0;                        ///ダウンロード数
                List<ReceiveFile> receiveFiles = receiveInfo.getReceiveFiles();
                for (ReceiveFile receiveFile : receiveFiles) {
                    _fileSizeB = _fileSizeB + receiveFile.getFileSize();
                    _attach_num++;
                    _downloadCount = _downloadCount + receiveFile.getDownloadCount();
                }
                fd.addOneData(getCsvString(String.valueOf(_attach_num)));
                fd.addOneData(getCsvString(String.valueOf(_fileSizeB)));
                fd.addOneData(getCsvString(String.valueOf(_downloadCount)));

                ///改行
                fd.addNewLine();
            }
        }

        //return
        return fd;
    }

    /**
     * 入力チェック
     *
     * @param context
     * @param _dateFrom
     * @param _dateTo
     *
     * @return チェック結果(true=OK,false=NG)
     */
    protected boolean checkInput(FacesContext context, Date _dateFrom, Date _dateTo) {
        boolean bret = true;
        String errMsg;
        String componentId;
        String itemName;

        String frmName = "inputForm";

        try {

            //エラーリストクリア
            errComponentIdList.clear();

            //---------------------------
            //出力履歴
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspHistoryType"); ///"出力履歴"
            componentId = frmName + ":" + "selectHistory";
            if (StringUtils.isEmpty(selectHistory)) {
                //errMsg = "出力履歴が選択されていません。";
                errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.SELECT_REQUIRED, funcId, itemName);
            }
            if (!errMsg.isEmpty()) {
                context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                errComponentIdList.add(componentId);
                bret = false;
            }

            //---------------------------
            //ユーザＩＤ
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspUserId");
            componentId = frmName + ":" + "schUserId";
            {
                ///チェック不要
            }
            
            //---------------------------
            //日付
            //---------------------------
            errMsg = "";
            itemName = getItemCaption("dspTargetPeriod"); ///"対象期間"
            componentId = frmName + ":" + "calTo";
            if (_dateFrom != null && _dateTo != null) {
                if (_dateFrom.getTime() > _dateTo.getTime()) {
                    //errMsg = "開始日・終了日の前後関係を確認して下さい。";
                    errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.DATE_FROM_TO_REVERSE, funcId);

                    ///チェックエラー
                    context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, getFacesMessageSummary(itemName), errMsg));
                    errComponentIdList.add(componentId);
                    bret = false;
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("履歴出力チェック失敗。", ex);
            return false;
        }

        return bret;
    }

    /**
     * ＣＳＶ出力
     */
    public void eventCsvOutput() {
        String errMsg;
        LOG.debug("eventCsvOutput start");

        String _itemName = getItemCaption("dspBtnCsvOutput");  ///"ＣＳＶ出力：　";
        String _summary = getFacesMessageSummary(_itemName);

        //ファイル名
        Date _date = new Date();
        String _fileName = "_" + new SimpleDateFormat("yyyyMMdd").format(_date) + ".csv";

        //ダウンロード可否
        boolean _isDownload = false;
        this.isDownLoad = _isDownload;

        //検索条件
        LOG.debug("出力履歴＝" + selectHistory);
        boolean bDefault = true;    ///未選択時の初期値をセットする
        Date _dateFrom = getSchDateFrom(bDefault);
        Date _dateTo = getSchDateTo(bDefault);
        LOG.debug("検索期間＝" + dateFrom + "～" + dateTo + "(" + _dateFrom + "～" + _dateTo + ") ユーザID=" + schUserId);

        FacesContext context = FacesContext.getCurrentInstance();
        RequestContext req_context = RequestContext.getCurrentInstance();

        // 入力チェック(bret:true=OK,false=NG)
        boolean bret = checkInput(context, _dateFrom, _dateTo);
        if (!bret) {
            ///チェックエラーが見つかった場合
            req_context.addCallbackParam(callbackParam_Error, !bret);
            return;
        }

        //--------------------------------
        //ダウンロード
        //--------------------------------
        FileDownload fdData = null;
        String fileName = "";
        if (selectHistory.equals(KEY_HISTORY_TYPE_SEND)) {
            ///送信履歴
            fdData = findSendHistory(schUserId, _dateFrom, _dateTo);
            fileName = nameHistoryTypeSend + _fileName;
        } else if (selectHistory.equals(KEY_HISTORY_TYPE_RECEIVE)) {
            ///受信履歴
            fdData = findReceiveHistory(schUserId, _dateFrom, _dateTo);
            fileName = nameHistoryTypeReceive + _fileName;
        }
        if (fileName.isEmpty()){ fileName = _fileName; }

        //出力件数
        int outCnt = (fdData == null) ? -1 : fdData.getListCnt();
        if (outCnt == 0) {
            ///出力データ無し
            //"出力データが見つかりません。"
            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.OUTPUTDATA_NOT_EXIST, funcId);
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, _summary, errMsg));
        } else if (outCnt < 0) {
            ///例外発生
            //"ＣＳＶファイル作成に失敗しました。"
            errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.CSV_FILE_CREATE_FAILED, funcId);
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, _summary, errMsg));
        }

        //ダウンロード準備
        if (fdData != null && outCnt > 0) {
            try {
                this.downloadFile = fdData.getDownloadFile(fileName);

                ///ダウンロード可能
                _isDownload = true;
//                context.addMessage(null,
//                    new FacesMessage(FacesMessage.SEVERITY_INFO, _summary, "ダウンロードの準備が整いました。"));

            } catch (Exception ex) {
                ex.printStackTrace();
                LOG.error("履歴出力ダウンロード準備失敗。", ex);
            }
        }

        //設定
        this.isDownLoad = _isDownload;  ///ダウンロード可否
        req_context.addCallbackParam(callbackParam_Download, this.isDownLoad);
    }
}
