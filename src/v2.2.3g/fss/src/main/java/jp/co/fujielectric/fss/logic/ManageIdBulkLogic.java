package jp.co.fujielectric.fss.logic;

import com.orangesignal.csv.CsvConfig;
import com.orangesignal.csv.CsvReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.entity.BasicUser;
import jp.co.fujielectric.fss.entity.UserType;
import jp.co.fujielectric.fss.service.BasicUserService;
import jp.co.fujielectric.fss.service.UserTypeService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.ValidatorUtil;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

/**
 * 管理者メニュー：ユーザ一括登録機能用Logic
 */
@RequestScoped
public class ManageIdBulkLogic {
    private String funcId;
    private Map<Integer, UserType> utMap;
    private List<String> updDatas;                   //登録内容

    //レコードフラグ
    private int USER_FLG = 0;
    //ユーザID
    private int USER_ID = 1;
    //名前
    private int USER_NAME = 2;
    //メールアドレス
    private int USER_MAILADDRESS = 3;
    //並び順
    private int USER_ORDER = 4;
    //種別
    private int USER_TYPE = 5;
    //適用開始年
    private int USER_START_YEAR = 6;
    //適用開始月
    private int USER_START_MONTH = 7;
    //適用開始日
    private int USER_START_DAY = 8;
    //適用終了年
    private int USER_END_YEAR = 9;
    //適用終了月
    private int USER_END_MONTH = 10;
    //適用終了日
    private int USER_END_DAY = 11;

    //追加
    private final String RECORDFLG_CREATE = "C";
    //更新
    private final String RECORDFLG_UPDATE = "U";
    //削除
    private final String RECORDFLG_DELETE = "D";

    private long maxlenUserId;                    //管理ユーザIDの文字数Max
    private long addressMailCharMax;              //管理ユーザアドレスの文字数Max
    private long maxlenUserName;                  //管理ユーザ名称の文字数Max
    private String defaultDateStart;
    private String defaultDateEnd;
    private long bulkInputCountLimit;             //CSV一括取込み行Max(ヘッダ行+取込み行Max)

    @Getter
    private Map<Integer, String> CSV_MAP;
    @Getter
    @Setter
    private Map<Integer, List<String>> mapsErr;      //エラー内容

    @Getter
    @Setter
    private String summary;
    @Getter
    @Setter
    private String message;
    @Getter
    @Setter
    private int successCount;                       //(登録成功ユーザID)結果件数
    @Getter
    @Setter
    private int failureCount;                       //(登録失敗ユーザID)結果件数

    @Inject
    private AuthLogic authLogic;
    @Inject
    private MailManager mailManager;
    @Inject
    private Logger LOG;

    @Inject
    private ItemHelper itemHelper;

    @Inject
    private UserTypeService userTypeService;

    @Inject
    private BasicUserService basicUserService;

    // コンストラクタ
    public ManageIdBulkLogic() {
        funcId = "manageIdBulk";
        HashMap<Integer, String> map = new HashMap<>();
        map.put(USER_FLG, "レコードフラグ");
        map.put(USER_ID, "ユーザID");
        map.put(USER_NAME, "氏名");
        map.put(USER_MAILADDRESS, "メールアドレス");
        map.put(USER_ORDER, "並び順");
        map.put(USER_TYPE, "種別");
        map.put(USER_START_YEAR, "適用開始年");
        map.put(USER_START_MONTH, "適用開始月");
        map.put(USER_START_DAY, "適用開始日");
        map.put(USER_END_YEAR, "適用終了年");
        map.put(USER_END_MONTH, "適用終了月");
        map.put(USER_END_DAY, "適用終了日");
        CSV_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * マスタ設定値からの変数初期化
     */
    public void initItems() {
        Item item;

        //管理ユーザIDの文字数Max
        item = itemHelper.find(Item.MAX_LEN_USER_ID, funcId);
        maxlenUserId = Integer.parseInt(item.getValue());

        //管理ユーザアドレスの文字数Max
        item = itemHelper.find(Item.ADDRESS_MAIL_CHAR_MAX, funcId);
        addressMailCharMax = Integer.parseInt(item.getValue());

        //管理ユーザ名称の文字数Max
        item = itemHelper.find(Item.MAX_LEN_USER_NAME, funcId);
        maxlenUserName = Integer.parseInt(item.getValue());

        //CSV一括取込み行Max(ヘッダ行+取込み行Max)
        item = itemHelper.find(Item.BULK_INPUT_COUNT_LIMIT, funcId);
        bulkInputCountLimit = Integer.parseInt(item.getValue());

        //デフォルト日付
        defaultDateStart = getDefaultDate(Item.DEFAULT_DATE_START, "00:00:00");
        defaultDateEnd = getDefaultDate(Item.DEFAULT_DATE_END, "23:59:59");
        LOG.debug("defaultDateStart=" + defaultDateStart + " defaultDateEnd=" + defaultDateEnd);

        //管理ユーザ種別の判定マップ（ソート番号をキーとする）
        List<UserType> utList = userTypeService.findAllSort();
        utMap = new HashMap<>();
        for( UserType ut : utList ) {
            if (ut.isDisp()) {
                utMap.put(ut.getSort(), ut);
            }
        }
    }

    /**
     * デフォルト日付を取得
     *
     * @param keyItem
     * @param strTime
     *
     * @return
     */
    public String getDefaultDate(Item keyItem, String strTime) {
        try {
            String value = itemHelper.find(keyItem, funcId).getValue();
            if (!StringUtils.isEmpty(value)) return value + " " + strTime;
        } catch (Exception ex) {
        }
        return "";
    }

    /**
     * ＣＳＶ取込み
     * @param selectedFilePath
     * @return
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    @Transactional
    public boolean eventCsvInput(String selectedFilePath) throws FileNotFoundException, UnsupportedEncodingException, IOException {
        //一括登録失敗(dspBtnBulkInput=一括登録,dspFailure=失敗)
        summary = getItemCaption("dspBtnBulkInput") + getItemCaption("dspFailure");

        //チェック用
        Item _inputCheckRegex = itemHelper.find(Item.INPUT_CHECK_REGEX, funcId);
        String inputCheckRegex = _inputCheckRegex.getValue();
        Item _inputCheckExclude = itemHelper.find(Item.INPUT_CHECK_EXCLUDE, funcId);
        String inputCheckExclude = _inputCheckExclude.getValue();

        //-------------------------------
        // 前チェック（１）
        //-------------------------------
        if (StringUtils.isEmpty(selectedFilePath)) {
            ///%sが選択されていません。(dspUptakeFile=取込ファイル)
            message = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.SELECT_REQUIRED, funcId, getItemCaption("dspUptakeFile"));
            return false;
        }

        //-------------------------------
        // 前チェック（２）
        //-------------------------------
        TreeMap<String, BasicUser> map;     ///登録済みユーザIDリスト(キー:ユーザID、要素:ユーザ情報)
        //CSV形式に関する設定情報を管理
        CsvConfig csvConfig = new CsvConfig(',', '"', '"');                     //args[1]:区切り文字、args[2]:囲い文字、args[3]:エスケープ文字
//        csvConfig.setIgnoreEmptyLines(true);                                    //空行を無視する
        // ↑空行は無視されず以降のデータが読み込めなくなる不具合を提供元が発表しているため、現時点ではこの機能は使わずに空行判定を別途実施！
        boolean hasHeader = false;
        try {
            try (InputStream inputStream = new FileInputStream(selectedFilePath);
                 InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "SJIS");
                 CsvReader reader = new CsvReader(inputStreamReader, csvConfig);) {
                
                
                // 先頭１行の読み込み
                List<String> values = reader.readValues();
                // ヘッダチェック
                int index = 0;
                for (Map.Entry<Integer, String> h : CSV_MAP.entrySet()) {
                    if (!h.getValue().equals(values.get(index))) break;
                    index++;
                }   hasHeader = (CSV_MAP.size() == index);
                // 行数チェック
                // ※MAX件数を越えた時点で処理終了
                int csvCount = hasHeader ? 0 : 1;
                while ((values = reader.readValues()) != null) {
                    csvCount++;
                    if (csvCount > bulkInputCountLimit) break;
                }   if (csvCount > bulkInputCountLimit) {
                    ///一括取込み可能な件数を超過しています。[最大" + (maxRows) + "件]"
                    message = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_BULK_INPUT_COUNT_OVER, funcId, bulkInputCountLimit);
                    return false;
                }   //登録済みユーザIDリストを生成
                List<BasicUser> _userLst = basicUserService.findAll();
                map = new TreeMap<>();
                for (BasicUser basicUser : _userLst) {
                    //キー:ユーザID、要素:ユーザ情報 をハッシュテーブルに追加する
                    map.put(basicUser.getUserId(), basicUser);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();

            //想定外のエラーが発生しました。%s(%s=(前チェック時))
            message = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.UNKNOWN_DETAIL, funcId, "(前チェック時)");
            return false;
        }

        //-------------------------------
        // reader読込＆チェック
        //-------------------------------
        updDatas = new ArrayList<>();   ///成功
        mapsErr = new HashMap<>();

        int _lineNumber;
        boolean _errFlg;
        boolean bCUD;
        List<String> values;

        try(InputStream inputStream = new FileInputStream(selectedFilePath);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "SJIS");
            CsvReader reader = new CsvReader(inputStreamReader, csvConfig);){

            ///[reader行毎sta]------------------------------------------------------------------
            while ((values = reader.readValues()) != null) {
                _lineNumber = reader.getLineNumber();
                _errFlg = false;
                bCUD = false;

                try{

                    ///空白行の場合、何もしない
                    if (isEmptyValues(values)) {
                        continue;
                    }

                    String errMsg = "";
                    String recordFlg = "";
                    String userId = "";
                    String userTypeId = "";
                    Date startDate = null;
                    Date endDate = null;

                    ///---列数チェック
                    if (CSV_MAP.size() != values.size()) {
                        //列数チェック（CSVの列数が不正です）
                        _errFlg = true;
                        errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_BULK_INPUT_COLUMN_CNT, funcId);
                    }

                    ///---ヘッダの場合、次の行へ
                    if (hasHeader && _lineNumber == 1) {
                        continue;
                    }

                    ///---レコードフラグチェック(CUDの何れかで無ければエラー)
                    if (!_errFlg) {
                        recordFlg = values.get(USER_FLG);
                        if (recordFlg.equals(RECORDFLG_CREATE) || recordFlg.equals(RECORDFLG_UPDATE) || recordFlg.equals(RECORDFLG_DELETE)) {
                        } else {
                            _errFlg = true;
                            ///レコードフラグには、"C"(追加),"U"(更新),"D"(削除)の何れかを入力してください
                            errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_BULK_INPUT_RECORD_FLG, funcId);
                        }
                    }

                    //******************************************
                    String _value;
                    String _koumoku;

                    if (!_errFlg) {
                        ///----------------------
                        ///---ユーザID
                        ///----------------------
                        _value = values.get(USER_ID);
                        _koumoku = CSV_MAP.get(USER_ID);

                        if (StringUtils.isEmpty(_value)) {
                            // 必須チェック(xxxが登録されていません。)
                            _errFlg = true;
                            errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_REQUIRED, funcId, _koumoku);
                        } else if (!CommonUtil.isCharMax(_value, maxlenUserId)) {
                            // 文字数Maxチェック(%sの文字数が上限（%s）を超えています。)
                            _errFlg = true;
                            errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_STR_LENGTH_OVER, funcId, _koumoku, maxlenUserId);
                        } else if (!ValidatorUtil.isValidRegex(_value, inputCheckRegex, inputCheckExclude)) {
                            // 不正文字チェック(%sに登録できない文字%sが使われています。)
                            _errFlg = true;
                            errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_STR_VALID, funcId, _koumoku);
                        }
                        if (!_errFlg) { userId = _value; }

                        ///----------------------
                        ///---名前
                        ///----------------------
                        _value = values.get(USER_NAME);
                        _koumoku = CSV_MAP.get(USER_NAME);

                        if(StringUtils.isEmpty(_value)) {
                            // 必須チェック(xxxが登録されていません。)
                            _errFlg = true;
                            errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_REQUIRED, funcId, _koumoku);
                        } else if (!CommonUtil.isCharMax(_value, maxlenUserName)) {
                            // 文字数Maxチェック(%sの文字数が上限（%s）を超えています。)
                            _errFlg = true;
                            errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_STR_LENGTH_OVER, funcId, _koumoku, maxlenUserName);
                        } else if (!ValidatorUtil.isValidJisX2080(_value)) {
                            // 不正文字チェック(%sに登録できない文字%sが使われています。)
                            _errFlg = true;
                            errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_STR_VALID, funcId, _koumoku);
                        }

                        ///----------------------
                        ///---メールアドレス
                        ///----------------------
                        _value = values.get(USER_MAILADDRESS);
                        _koumoku = CSV_MAP.get(USER_MAILADDRESS);
                        boolean _errFlgMailAddress = true;

                        if(StringUtils.isEmpty(_value)) {
                            // 必須チェック(xxxが登録されていません。)
                            _errFlg = true;
                            errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_REQUIRED, funcId, _koumoku);
                        } else if (!CommonUtil.isCharMax(_value, addressMailCharMax)) {
                            // 文字数Maxチェック(%sの文字数が上限（%s）を超えています。)
                            _errFlg = true;
                            errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_STR_LENGTH_OVER, funcId, _koumoku, addressMailCharMax);
                        } else if (!CommonUtil.isValidEmail(_value)) {
                            // 不正文字チェック(%sに登録できない文字%sが使われています。)
                            _errFlg = true;
                            errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_STR_VALID, funcId, _koumoku);
                        } else {
                            // メールアドレス正常
                            _errFlgMailAddress = false;
                        }

                        ///----------------------
                        ///---並び順
                        ///----------------------
                        //TODO 並び順の改修時まで非対応

                        ///----------------------
                        ///---種別
                        ///----------------------
                        _value = values.get(USER_TYPE);
                        _koumoku = CSV_MAP.get(USER_TYPE);
                        boolean _errFlgUserTypeId = true;

                        if(StringUtils.isEmpty(_value)) {
                            // 必須チェック(xxxが登録されていません。)
                            _errFlg = true;
                            errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.INPUT_REQUIRED, funcId, _koumoku);
                        } else {
                            // 種別には、userType.sort値が設定されているので注意
                            //（ソート値で出力するのは一時対応...TODO）
                            java.util.Set<java.util.Map.Entry<Integer, UserType>> stKey = utMap.entrySet();
                            java.util.Iterator<java.util.Map.Entry<Integer, UserType>> ite = stKey.iterator();
                            while (ite.hasNext()) {
                                java.util.Map.Entry<Integer, UserType>entry = ite.next();
                                Integer _key = entry.getKey();
                                UserType _u = entry.getValue();
                                if (_value.equals(String.valueOf(_key))) {
                                    userTypeId = _u.getId();   ///userType.id
                                    break;
                                }
                            }
                            if (userTypeId.equals("")) {
                                ///%sに登録できない値です。
                                _errFlg = true;
                                errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_VALUE_VALID, funcId, _koumoku);
                            }else{
                                // 種別正常
                                _errFlgUserTypeId = false;
                            }
                        }

                        ///----------------------
                        ///---メールアドレスと種別の関連チェック
                        ///----------------------
                        // メールアドレスと種別が正常の場合
                        if(!_errFlgMailAddress && !_errFlgUserTypeId){
                            //庁内/庁外判定
                            boolean isSendInner = mailManager.isMyDomain(values.get(USER_MAILADDRESS));
                            // 選択されている種別の内部ユーザフラグを取得(true:内部ユーザ、false:外部ユーザ)
                            boolean internalflg = authLogic.isUserTypeInternalflg(userTypeId);
                            // 種別が内部ユーザかつドメインが庁外の場合
                            if (internalflg && !isSendInner) {
                                // 庁内のユーザ種別に対して外部のメールアドレスは利用できません。庁内のメールアドレスを指定してください。
                                _errFlg = true;
                                errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_EXTERNAL, funcId);

                            // 種別が外部ユーザかつドメインが庁内の場合
                            } else if (!internalflg && isSendInner) {
                                // 外部のユーザ種別に対して庁内のメールアドレスは利用できません。外部のメールアドレスを指定してください。
                                _errFlg = true;
                                errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_INTERNAL, funcId);
                            }
                        }

                        ///----------------------
                        ///---適用開始年月日
                        ///----------------------
                        _koumoku = CSV_MAP.get(USER_START_YEAR) + "," + CSV_MAP.get(USER_START_MONTH) + "," + CSV_MAP.get(USER_START_DAY);

                        // 適用開始年月日+00:00:00
                        if (StringUtils.isEmpty(values.get(USER_START_YEAR)) &&
                                StringUtils.isEmpty(values.get(USER_START_MONTH)) &&
                                StringUtils.isEmpty(values.get(USER_START_DAY))) {
                            ///空欄なら、デフォルト開始日(ex.2016/01/01 00:00:00)
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                            startDate = sdf.parse(defaultDateStart.replace("/", "").replace("-", ""));    ///sdf.parse("20160101 00:00:00");
                        } else {
                            try{
                                int year = Integer.parseInt(values.get(USER_START_YEAR));
                                int month = Integer.parseInt(values.get(USER_START_MONTH));
                                int day = Integer.parseInt(values.get(USER_START_DAY));

                                ///日付チェック(%sに登録できない文字%sが使われています。)
                                if (!checkDate(year, month, day)) {
                                    _errFlg = true;
                                    errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_STR_VALID, funcId, _koumoku);
                                }
                                else {
                                    startDate = DateUtil.getDateExcludeTime(DateUtil.getDate(year, month, day));
                                }

                            }catch(ArrayIndexOutOfBoundsException | NumberFormatException e) {
                                // 日付変換エラー(%sに登録できない文字%sが使われています。)
                                _errFlg = true;
                                errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_STR_VALID, funcId, _koumoku);
                            }
                        }

                        ///----------------------
                        ///---適用終了年月日
                        ///----------------------
                        _koumoku = CSV_MAP.get(USER_END_YEAR) + "," + CSV_MAP.get(USER_END_MONTH) + "," + CSV_MAP.get(USER_END_DAY);

                        // 適用終了年月日+23:59:59
                        if (StringUtils.isEmpty(values.get(USER_END_YEAR)) &&
                                StringUtils.isEmpty(values.get(USER_END_MONTH)) &&
                                StringUtils.isEmpty(values.get(USER_END_DAY))) {
                            ///空欄なら、デフォルト終了日(ex.9999/12/31 23:59:59)
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                            endDate = sdf.parse(defaultDateEnd.replace("/", "").replace("-", ""));    ///sdf.parse("99991231 23:59:59");
                        } else {
                            try{
                                int year = Integer.parseInt(values.get(USER_END_YEAR));
                                int month = Integer.parseInt(values.get(USER_END_MONTH));
                                int day = Integer.parseInt(values.get(USER_END_DAY));

                                ///日付チェック(%sに登録できない文字%sが使われています。)
                                if (!checkDate(year, month, day)) {
                                    _errFlg = true;
                                    errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_STR_VALID, funcId, _koumoku);
                                }
                                else {
                                    endDate = DateUtil.getDateExcludeMillisExpirationTime(DateUtil.getDate(year, month, day));
                                }

                            }catch(ArrayIndexOutOfBoundsException | NumberFormatException e) {
                                // 日付変換エラー(%sに登録できない文字%sが使われています。)
                                _errFlg = true;
                                errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_STR_VALID, funcId, _koumoku);
                            }
                        }
                    }

                    //レコードフラグ-ユーザＩＤ:関連チェック
                    if (!StringUtils.isEmpty(userId)) {
                        _koumoku = CSV_MAP.get(USER_ID);
                        boolean _exist = map.containsKey(userId);
                        if (recordFlg.equals(RECORDFLG_CREATE) && _exist) {
                            ///重複エラー(既にユーザＩＤが登録されています。)
                            _errFlg = true;
                            errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_INPUT_USERID_EXIST, funcId, _koumoku);

                        } else if ((recordFlg.equals(RECORDFLG_UPDATE) || recordFlg.equals(RECORDFLG_DELETE)) && !_exist) {
                            ///存在エラー(指定されたユーザIDが登録されていません。)
                            _errFlg = true;
                            errMsg += itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_INPUT_USERID_NOT_EXIST, funcId, _koumoku);
                        }
                    }

                    //******************************************

                    // エラーチェック
                    if (_errFlg) {
                        mapsErr = addMaps(mapsErr, values, errMsg, _lineNumber);
                    }
                    else {
                        // 正常動作
                        switch(recordFlg) {
                            case RECORDFLG_CREATE:
                                //追加
                                BasicUser createUser = new BasicUser();
                                createUser.setUserId(values.get(USER_ID));
                                createUser.setPassword("");
                                createUser.setMailAddress(values.get(USER_MAILADDRESS));
                                createUser.setName(values.get(USER_NAME));
                                createUser.setUserType(userTypeId);
                                createUser.setStartTime(startDate);
                                createUser.setEndTime(endDate);
    //                            basicUserService.create(createUser);
                                basicUserService.edit(createUser); // データ差異があった際に上書きするために、editを使用
                                bCUD = true;

                                //map更新（追加分）
                                map.put(userId, createUser);
                                break;

                            case RECORDFLG_UPDATE:
                                //更新(ユーザID、パスワードは変更不可のためセットしない)
                                BasicUser editUser = map.get(userId);
                                editUser.setName(values.get(USER_NAME));
                                editUser.setMailAddress(values.get(USER_MAILADDRESS));
                                //TODO 並び順
                                editUser.setUserType(userTypeId);
                                editUser.setStartTime(startDate);
                                editUser.setEndTime(endDate);
                                basicUserService.edit(editUser);
                                bCUD = true;

                                //map更新（更新分）
                                map.put(userId, editUser);
                                break;

                            case RECORDFLG_DELETE:
                                //削除
                                basicUserService.remove(userId);
                                bCUD = true;

                                //map更新（削除分）
                                map.remove(userId);
                                break;

                            default:
                                break;
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();

                    _errFlg = true;

                    // 不明エラー発生時、問題箇所をエラーデータに記載
                    ///取込処理中に不明なエラーが発生しました。%s
                    String errMsg = itemHelper.findDispMessageStr(Item.ErrMsgItemKey.ERR_BULK_INPUT_UNKNOWN, funcId,"");
                    mapsErr = addMaps(mapsErr, values, errMsg, _lineNumber);

                    // 対象IDをログに出力。無理なら諦める
                    try {
                        LOG.warn("ManageIdBulkInput:" + values.get(USER_ID) + " is Error");
                    }catch(Exception e2) {}
                }

                finally {
                    //if (!_errFlg && (!hasHeader || _lineNumber > 1)) {
                    if (!_errFlg && bCUD) {
                        updDatas.add(values.toString());
                    }
                }
            }
            ///[reader行毎end]------------------------------------------------------------------
        }
        //-------------------------------
        // 後処理
        //-------------------------------
        successCount = updDatas.size();
        failureCount = mapsErr.size();
        LOG.trace("---successCount=" + successCount);
        LOG.trace("---failureCount" + failureCount);

        //完了メッセージ
        ///一括登録処理が完了しました。エラーについては、エラー内容紹介を確認してください。
        summary = getItemCaption("dspBtnBulkInput") + getItemCaption("dspSuccess");
        message = itemHelper.findDispMessageStr(Item.InfMsgItemKey.INF_BULK_INPUT_COMLPETE, funcId);
        return true;
    }

    /**
     * 画面の各コントロールのキャプション取得
     *
     * @param key
     * @return
     */
    private String getItemCaption(String key) {
        return itemHelper.findDispMessageStr(key, funcId);
    }

    /**
     * mapに情報追加（キー：登録順、値＝内容リスト＋エラー内容）
     *
     * @param maps
     * @param datas
     * @param msg
     * @param lineNumber
     *
     * @return maps
     */
    private Map<Integer, List<String>> addMaps(
        Map<Integer, List<String>> maps, List<String>values, String msg, int lineNumber) {

        //キー=登録順
        int key = maps.size();
        //値=内容リスト＋エラー内容（エラー行番号）
        String _add = msg;
        if (lineNumber>0) {
            _add = _add + itemHelper.findDispMessageStr("dspLineNumber", funcId, lineNumber);
        }
        values.add(_add);
        //追加
        maps.put(key, values);
        //return
        return maps;
    }

    /**
     * valuesが全て空白かどうか
     */
    private boolean isEmptyValues(List<String>values) {
        if (values==null) { return true; }
        for (String s : values) {
            if (!StringUtils.isEmpty(s)) { return false; }
        }
        return true;
    }

    /**
     * 日付チェック
     *
     * @param _year
     * @param _month
     * @param _day
     *
     * @return 結果(true=問題なし、false=不正)
     */
    private boolean checkDate(int _year, int _month, int _day) {
        boolean bFailure = false;

        //年
        if (String.valueOf(_year).trim().length() != 4) { return bFailure; }
        //月
        if (_month<1 || _month>12) { return bFailure; }
        //日
        if (_day<1 || _day>31) { return bFailure; }

        //YYYY/MM/DD
        String strDate = String.valueOf(_year).trim() + "/" +
            StringUtils.leftPad(String.valueOf(_month), 2, "0") + "/" +
            StringUtils.leftPad(String.valueOf(_day), 2, "0");

		//日付・時刻解析を厳密に行うかどうかを設定
        DateFormat dateFormat = DateFormat.getDateInstance();
		dateFormat.setLenient(false);

        //チェック
        try {
            dateFormat.parse(strDate);
		} catch (Exception e) {
			return bFailure;
		}

        //結果
        return true;
    }
}
