package jp.co.fujielectric.fss.data;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * アイテムデータ
 */
@Data
@AllArgsConstructor
public class Item {

    /**
     * キー
     */
    private String key;
    /**
     * 値
     */
    private String value;

    public static final String DEFINE = "define";
    public static final String CONFIG = "config";
    public static final String MAIL_MESSAGE = "mailmassage";
    public static final String DISP_MESSAGE = "dispmassage";
    public static final String FUNC_COMMON = "0";
    public static final String FUNC_DELETEREASON = "delete_reason";

    public static final Item REGION_ID = new Item("RegionId", DEFINE);
    public static final Item REGION_NAME = new Item("RegionName", DEFINE);
    public static final Item REGION_DOMAIN = new Item("RegionDomain", DEFINE);

    public static final Item FILE_SIZE_LIMIT = new Item("FileSizeLimit", CONFIG);
    public static final Item EXPIRATION_MIN = new Item("expirationMin", CONFIG);
    public static final Item PASSWORD_CHAR_DEFAULT = new Item("passwordCharDefault", CONFIG);
    public static final Item PASSWORD_CHAR_MAX = new Item("passwordCharMax", CONFIG);
    public static final Item PASSWORD_CHAR_MIN = new Item("passwordCharMin", CONFIG);
    public static final Item HISTORY_ROWS_DEFAULT = new Item("historyRowsDefault", CONFIG);
    public static final Item HISTORY_ROWS_TEMPLATE = new Item("historyRowsTemplate", CONFIG);
    public static final Item FILES_SIZE_LIMIT = new Item("FilesSizeLimit", CONFIG);
    public static final Item FILES_COUNT_LIMIT = new Item("FilesCountLimit", CONFIG);
    public static final Item ADDRESSES_COUNT_LIMIT = new Item("addressesCountLimit", CONFIG);
    public static final Item ADDRESS_MAIL_CHAR_MAX = new Item("addressMailCharMax", CONFIG);
    public static final Item ADDRESS_NAME_CHAR_MAX = new Item("addressNameCharMax", CONFIG);
    public static final Item COMMENT_CHAR_MAX = new Item("commentCharMax", CONFIG);
    public static final Item EXPIRATION_DEFAULT = new Item("expirationDefault", CONFIG);
    public static final Item EXPIRATION_MAX = new Item("expirationMax", CONFIG);
    public static final Item SELECT_COUNT_LIMIT = new Item("selectCountLimit", CONFIG);
    public static final Item MAIL_SIZE_LIMIT = new Item("MailSizeLimit", CONFIG);
    public static final Item MAIL_SIZE_LIMIT_SANITIZED_INNER = new Item("MailSizeLimitSanitized", CONFIG);  //[248対応（簡易版）]
    public static final Item SEARCH_COUNT_LIMIT = new Item("searchCountLimit", CONFIG);
    public static final Item SEARCH_CHAR_MAX = new Item("searchCharMax", CONFIG);

    public static final Item MASTER_ROWS_DEFAULT = new Item("masterRowsDefault", CONFIG);
    public static final Item MASTER_ROWS_TEMPLATE = new Item("masterRowsTemplate", CONFIG);
    public static final Item FILE_EXCLUDE_SUFFIX_LIST = new Item("FileExcludeSuffixList", CONFIG);
    public static final Item FILE_INCLUDE_SUFFIX_LIST = new Item("FileIncludeSuffixList", CONFIG);

    public static final Item HELP_URL = new Item("helpUrl", CONFIG);
    public static final Item FAQ_URL = new Item("faqUrl", CONFIG);

    public static final Item MAX_LEN_USER_ID = new Item("maxlenUserId", CONFIG);
    public static final Item MAX_LEN_USER_NAME = new Item("maxlenUserName", CONFIG);
    public static final Item MAX_LEN_DEFINE_NAME = new Item("maxlenDefineName", CONFIG);
    public static final Item MAX_LEN_DEFINE_VALUE = new Item("maxlenDefineValue", CONFIG);
    public static final Item MAX_LEN_NORTICE_SUBJECT = new Item("maxlenNorticeSubject", CONFIG);
    public static final Item MAX_LEN_FUNC_ID = new Item("maxlenFuncId", CONFIG);
    public static final Item MAX_LEN_FUNC_KEY = new Item("maxlenFuncKey", CONFIG);
    public static final Item MAX_LEN_FUNC_VALUE = new Item("maxlenFuncValue", CONFIG);
    public static final Item MAX_LEN_IMAGE_ID = new Item("maxlenImageId", CONFIG);

    public static final Item NONE_SANITIZE_FLG = new Item("noneSanitizeFlg", CONFIG);
    public static final Item MAIL_SANITIZE_FLG = new Item("mailSanitizeFlg", CONFIG);
    public static final Item SENDIFLE_CHECK_FLG = new Item("sendFileCheckFlg", CONFIG);
    public static final Item PASSWORD_UNLOCK_FLG = new Item("passwordUnlockFlg", CONFIG);
    public static final Item PASSWORD_RE_ENCRYPT_FLG_INNER = new Item("passwordReEncryptFlg", CONFIG); //[248対応（簡易版）]

    public static final Item ENCRYPT_MODE_PDF = new Item("encryptModePDF", CONFIG);

    public static final Item BULK_INPUT_COUNT_LIMIT = new Item("bulkInputCountLimit", CONFIG);
    public static final Item INPUT_CHECK_REGEX = new Item("inputCheckRegex", CONFIG);
    public static final Item INPUT_CHECK_EXCLUDE = new Item("inputCheckExclude", CONFIG);
    public static final Item DEFAULT_DATE_START = new Item("defaultDateStart", CONFIG);
    public static final Item DEFAULT_DATE_END = new Item("defaultDateEnd", CONFIG);

    public static final Item INTERNAL_TRANSFER_FLG = new Item("internalTransferFlg", CONFIG);
    public static final Item EXTERNAL_TRANSFER_FLG = new Item("externalTransferFlg", CONFIG);

    public static final Item APPROVALS_FLG = new Item("approvalsFlg", CONFIG);
    public static final Item APPROVER_ADDRESSES_COUNT_LIMIT = new Item("approverAddressesCountLimit", CONFIG);
    public static final Item APPROVER_REQUIRED = new Item("approverRequired", CONFIG);

    public static final Item MAIL_CONVERT_FLG_INNER = new Item("mailConvertFlg", CONFIG); //[248対応（簡易版）]
    public static final Item MAIL_CONVERT_CHARSET_INNER = new Item("mailConvertCharset", CONFIG); //[248対応（簡易版）]
    public static final Item MAIL_CONVERT_ENCODING_INNER = new Item("mailConvertEncoding", CONFIG); //[248対応（簡易版）]

    public static final Item ZIP_CHARSET_CONVERT_INNER = new Item("zipCharsetConvert", CONFIG);  //[248対応（簡易版）]

    public static final Item ORIGINAL_HEADER_FROM_PASSWORD_UNLOCK = new Item("originalHeaderFromPasswordUnlock", CONFIG);

    public static final Item MONTHLY_REPORT_CHARSET = new Item("monthlyReportCharset", CONFIG);

    public static final Item LGWAN_DOMAIN = new Item("lgwanDomain", CONFIG);

    //SandBlast対応区分
    public static final Item SANDBLASTKBN_MAIL = new Item("sandBlastKbnMail", CONFIG);
    public static final Item SANDBLASTKBN_FILE = new Item("sandBlastKbnFile", CONFIG);

    //削除理由ファイル名[v2.2.3]
    public static final Item DELETEREASON_ATTACHMENTFILE = new Item("deleteReasonAttachedFileName", CONFIG);
    
    /**
     * パスワードポリシー用config情報enum
     */
    public enum PasswordPolicyItemKey {
        /**
         * ID同一パスワード制限
         */
        SAMEID_PW_CHECK("pwPolicySameIdPwCheck"),
        /**
         * パスワード有効期限チェック
         */
        LIMIT_CHECK("pwPolicyLimitCheck"),
        /**
         * パスワード有効期限日数
         */
        LIMIT_DAY("pwPolicyLimitDay"),
        /**
         * パスワード期限警告
         */
        WARNLIMIT_CHECK("pwPolicyWarnLimitCheck"),
        /**
         * パスワード期限警告日数
         */
        WARNLIMIT_DAY("pwPolicyWarnLimitDay"),
        /**
         * パスワード最小文字数チェック
         */
        MINLENGTH_CHECK("pwPolicyMinLengthCheck"),
        /**
         * パスワード最小文字数
         */
        MINLENGTH("pwPolicyMinLength"),
        /**
         * パスワード英字最小文字数チェック
         */
        MINALP_CHECK("pwPolicyMinAlpCheck"),
        /**
         * パスワード英字最小文字数
         */
        MINALP("pwPolicyMinAlp"),
        /**
         * パスワード数字最小文字数チェック
         */
        MINNUM_CHECK("pwPolicyMinNumCheck"),
        /**
         * パスワード数字最小文字数
         */
        MINNUM("pwPolicyMinNum"),
        /**
         * パスワード記号最小文字数チェック
         */
        MINMARK_CHECK("pwPolicyMinMarkCheck"),
        /**
         * パスワード記号最小文字数
         */
        MINMARK("pwPolicyMinMark"),
        /**
         * パスワード同一文字制限チェック
         */
        MAXSAMECHAR_CHECK("pwPolicyMaxSameCharCheck"),
        /**
         * パスワード同一文字制限
         */
        MAXSAMECHAR("pwPolicyMaxSameChar"),
        /**
         * パスワード除外文字チェック
         */
        EXCLUDECHAR_CHECK("pwPolicyExcludeCharCheck"),
        /**
         * パスワード除外文字
         */
        EXCLUDECHAR("pwPolicyExcludeChar"),
        /**
         * パスワード履歴記録
         */
        HISTORY("pwPolicyHistory"),
        /**
         * パスワード履歴数
         */
        HISTORY_COUNT("pwPolicyHistoryCount"),
        /**
         * パスワード履歴チェック
         */
        HISTORY_CHECK("pwPolicyHistoryCheck"),
        /**
         * ログインロック
         */
        LOGINLOCK("pwPolicyLoginLock"),
        /**
         * ログインロック試行回数
         */
        LOCK_COUNT("pwPolicyLockCount"),
        /**
         * ログインロック解除
         */
        LOCKCLEAR("pwPolicyLockClear"),
        /**
         * ログインロック解除時間
         */
        LOCKCLEAR_TIME("pwPolicyLockClearTime"),
        /**
         * パスワードリセット実施日時
         */
        RESETDATE("pwPolicyResetDate"),
        /**
         * パスワードポリシー説明文
         */
        POLICY_NOTE("pwPolicyNote");
        private final Item _item;

        private PasswordPolicyItemKey(final String itemKey) {
            this._item = new Item(itemKey, CONFIG);
        }

        public String getItemKey() {
            return this._item.key;
        }

        public Item getItem() {
            return this._item;
        }
    }

    /**
     * エラーメッセージ用ItemKey列挙体
     *
     */
    public enum ErrMsgItemKey {
        /**
         * 対象外ファイルのため送信不可
         */
        FILE_ENABLE_SEND_EXCLUDE("errFileSendExclude"),
        /**
         * パスワード付のため送信不可
         */
        FILE_ENABLE_SEND_PASSWORD("errFileSendPassword"),
        /**
         * メールアドレス未入力
         */
        MAIL_ADDRESS_REQUIRED("errMailAddressRequired"),
        /**
         * メールアドレスが不正
         */
        MAIL_ADDRESS_INVALID("errMailAddressInvalid"),
        /**
         * パスワード未入力
         */
        PASSWORD_REQUIRED("errPasswordRequired"),
        /**
         * パスワードの文字数が短すぎ。（※引数に最小値、最大値が必要） 例）パスワードの文字数が短すぎます。[%s～%s]
         */
        PASSWORD_LENGTH_SHORT("errPasswordLengthShort"),
        /**
         * パスワード不正（半角英数文字のみ）
         */
        PASSWORD_INVALID("errPasswordInvalid"),
        /**
         * 送信可能なファイルが未選択
         */
        FILES_REQUIRED("errFilesRequired"),
        /**
         * 送信可能なファイル数が超過　（※引数に最大数が必要） 例）送信可能なファイル数を超えています。（最大数：%s）
         */
        FILES_COUNT_OVER("errFilesCountOver"),
        /**
         * 総ファイルサイズオーバー
         */
        FILES_SIZE_OVER("errFilesSizeOver"),
        /**
         * 未入力エラー（汎用）「%sが未入力です。」
         */
        INPUT_REQUIRED("errInputRequired"),
        /**
         * 重複エラー（汎用）「%sが重複しています。」
         */
        INPUT_DUPLICATE("errInputDuplicate"),
        /**
         * 機能IDとキーの組合せが重複
         */
        CONFIG_DUPLICATE("errConfigDuplicate"),
        /**
         * 庁外向けファイル送信で庁内向けドメイン指定
         */
        MAIL_DOMAIN_SEND_OUTER("errMailDomainSendOuter"),
        /**
         * 未選択エラー（汎用）「%sが選択されていません。」
         */
        SELECT_REQUIRED("errSelectRequired"),
        //        /**
        //         * 開始日より過去の日付が入力されました。
        //         */
        //        DATE_REVERSE("errDateReverse"),
        /**
         * 出力データなし
         */
        OUTPUTDATA_NOT_EXIST("errOutputDataNotExist"),
        /**
         * CSVファイル作成失敗
         */
        CSV_FILE_CREATE_FAILED("errCsvFileCreateFailed"),
        /**
         * 過去の日付が入力されています。本日を含め未来の日付を入力して下さい。
         */
        DATE_REQUIRED_FUTURE_DATE("errDateRequiredFutureDate"),
        /**
         * 開始日・終了日の前後関係を確認して下さい。
         */
        DATE_FROM_TO_REVERSE("errDateFromToReverse"),
        /**
         * 終了日より未来の日付が入力されました。終了日を含め過去の日付を入力して下さい。
         */
        FROMDATE_INPUT_REQUIRED_PASTDATE("errFromDateInputRequiredPastDate"),
        /**
         * 開始日より過去の日付が入力されました。開始日を含め未来の日付を入力して下さい。
         */
        TODATE_INPUT_REQURED_FUTUREDATE("errToDateInputRequiredFutureDate"),
        /**
         * 終了日より未来の日付が選択されました。終了日を含め過去の日付を選択して下さい。
         */
        FROMDATE_SELECT_REQUIRED_PASTDATE("errFromDateSelectRequiredPastDate"),
        /**
         * 開始日より過去の日付が選択されました。開始日を含め未来の日付を選択して下さい。
         */
        TODATE_SELECT_REQURED_FUTUREDATE("errToDateSelectRequiredFutureDate"),
        /**
         * ユーザIDもしくはパスワードが誤っています。
         */
        LOGIN_ID_PSWD_WRONG("errLoginIdPswdWrong"),
        /**
         * ご利用出来ないユーザです。
         */
        LOGIN_EARLY("errLoginEarly"),
        /**
         * ご利用出来ないユーザです。
         */
        LOGIN_EXPIRED("errLoginExpired"),
        /**
         * 既にログインされています。
         */
        LOGIN_ALREADY("errLoginAlready"),
        /**
         * パスワードの設定に失敗しました。
         */
        LOGIN_PSWD_SET_FAILED("errLoginPswdSetFailed"),
        /**
         * パスワードの変更が行えませんでした。
         */
        LOGIN_PSWD_CHANGE_FAILED("errLoginPswdChangeFailed"),
        /**
         * パスワードが一致していません。
         */
        LOGIN_PSWD_INVALID("errLoginPswdInvalid"),
        /**
         * パスワードが誤っている。もしくは有効期限が切れています。
         */
        ONETIME_LOGIN_FAILED("errOnetimeLoginFailed"),
        /**
         * ダウンロードに失敗
         */
        DOWNLOAD_FILE_FAILED("errDownloadFileFailed"),
        /**
         * ダウンロード後の更新処理に失敗
         */
        DOWNLOAD_UPDATE_DB_FAILED("errDownloadUpdateDbFailed"),
        /**
         * アップロードに失敗
         */
        UPLOAD_FILE_FAILED("errUploadFileFailed"),
        /**
         * 追加処理に失敗
         */
        FAILED_ADD("errMessageAdd"),
        /**
         * 更新処理に失敗
         */
        FAILED_UPDATE("errMessageUpdate"),
        /**
         * 削除処理に失敗
         */
        FAILED_DELETE("errMessageDelete"),
        /**
         * パスワード設定通知送信に失敗
         */
        FAILED_NORTICE_PW_SEND("errMessageNorticePwSend"),
        /**
         * 取込処理中に不明なエラーが発生しました。%s
         */
        ERR_BULK_INPUT_UNKNOWN("errBulkInputUnknown"),
        /**
         * 取込処理中に不正なエラーが発生したため、登録処理をキャンセルしました。
         */
        ERR_BULK_INPUT_ALL_CANCEL("errBulkInputAllCancel"),
        /**
         * ＣＳＶの列数が不正です。
         */
        ERR_BULK_INPUT_COLUMN_CNT("errBulkInputColumnCnt"),
        /**
         * レコードフラグには、C(追加),U(更新),D(削除)の何れかを入力してください。
         */
        ERR_BULK_INPUT_RECORD_FLG("errBulkInputRecordFlg"),
        /**
         * %sの文字数が上限（%s）を超えています。
         */
        ERR_STR_LENGTH_OVER("errStrLengthOver"),
        /**
         * %sに登録できない文字%sが使われています。
         */
        ERR_STR_VALID("errStrValid"),
        /**
         * %sに登録できない値です。
         */
        ERR_VALUE_VALID("errValueValid"),
        /**
         * 既にユーザＩＤが登録されています。
         */
        ERR_INPUT_USERID_EXIST("errInputUserIdExist"),
        /**
         * 指定されたユーザIDが登録されていません。
         */
        ERR_INPUT_USERID_NOT_EXIST("errInputUserIdNotExist"),
        /**
         * 一括取込み可能な件数を超過しています。[最大%s件]
         */
        ERR_BULK_INPUT_COUNT_OVER("errBulkInputCountOver"),
        /**
         * 庁内で利用しているメールアドレスを指定してください。
         */
        ERR_MAIL_DOMAIN_APPROVAL("errMailDomainApproval"),
        /**
         * 想定外のエラー%s
         */
        UNKNOWN_DETAIL("errUnknownDetail"),
        /**
         * 想定外のエラー
         */
        UNKNOWN("errUnknown"),
        /**
         * （他処理にて）取消済みのため、%sできません。
         */
        ERR_EXCLUSION_SEND_CANCELED("errExclusionSendCanceled"),
        /**
         * （他処理にて）承認済みのため、%sできません。
         */
        ERR_EXCLUSION_APPROVED("errExclusionApproved"),
        /**
         * 他処理にてデータが更新された為、%sできません。
         */
        ERR_EXCLUSION("errExclusion"),
        /**
         * 外部へファイルを送信することはできません。庁内で利用しているメールアドレスを指定してください。 (外部へファイルを送信することはできません。庁内で利用しているメールアドレスを指定してください。)
         */
        ERR_EXTERNAL("errExternal"),
        /**
         * 庁内へファイルを送信することはできません。外部で利用しているメールアドレスを指定してください。 (庁内へ送信を依頼することはできません。外部で利用しているメールアドレスを指定してください。)
         */
        ERR_INTERNAL("errInternal"),
        /**
         * %s～%sの数値を入力してください。
         */
        INPUT_ERR_NUMBER("errInputNumber"),
        /**
         * 「パスワード最小文字数」には「パスワード英字最小文字数」＋「パスワード数字最小文字数」＋「パスワード記号最小文字数」以上の数値を入力してください。
         */
        INPUT_PSWD_LEN("errInputPswdLen"),
        /**
         * ユーザIDと同じパスワードは設定できません。
         */
        ERR_PW_SAMEID_PW("errPwPolicySameIdPw"),
        /**
         * パスワードの有効期限が切れています。新しいパスワードを設定してください。
         */
        ERR_PW_LIMIT("errPwPolicyLimitDay"),
        /**
         * 現在のパスワードを設定してから%s日以上経過しています。
         */
        ERR_PW_WARNLIMIT("errPwPolicyWarnLimitDay"),
        /**
         * パスワードは%s文字以上必要です。
         */
        ERR_PW_MINLENGTH("errPwPolicyMinLength"),
        /**
         * パスワードには%s文字以上の英字を含める必要があります。
         */
        ERR_PW_MINALP("errPwPolicyMinAlp"),
        /**
         * パスワードには%s文字以上の数値を含める必要があります。
         */
        ERR_PW_MINNUM("errPwPolicyMinNum"),
        /**
         * パスワードには%s文字以上の記号を含める必要があります。
         */
        ERR_PW_MINMARK("errPwPolicyMinMark"),
        /**
         * パスワードには%s文字以上の同一文字を使うことができません。
         */
        ERR_PW_MAXSAMECHAR("errPwPolicyMaxSameChar"),
        /**
         * パスワードに使用できない文字が含まれています。
         */
        ERR_PW_EXCLUDECHAR("errPwPolicyExcludeChar"),
        /**
         * 過去に同じパスワードが使用されています。
         */
        ERR_PW_HISTORY("errPwPolicyHistoryCheck"),
        /**
         * パスワード入力を%s回失敗したためロックされました。
         */
        ERR_PW_LOGINLOCK("errPwPolicyLockCount"),
        /**
         * パスワード入力の間違いが規定回数を超えたためロックされておりログインできません。
         */
        ERR_LOGIN_LOCK("errLoginLock"),
        /**
         * パスワードを変更する必要があります。新しいパスワードを設定してください。
         */
        ERR_PW_RESET("errPwPolicyReset"),
        /**
         * 送信者のメールアドレスは指定できません。
         */
        ERR_MAIL_ADDRESS_OWN("errMailAddressOwn"),
        /**
         * 府、省、庁とのファイル交換はできません。
         */
        ERR_REJECT_DOMAIN("errRejectDomain"),
        ;

        private final String _itemKey;

        private ErrMsgItemKey(final String itemKey) {
            this._itemKey = itemKey;
        }

        public String getString() {
            return this._itemKey;
        }
    }

    /**
     * インフォメーションメッセージ用ItemKey列挙体
     *
     */
    public enum InfMsgItemKey {
        /**
         * ワンタイムファイル送信完了後メッセージ （例：ファイルが送信されました。画面を閉じてください。\n再度、同じＵＲＬにログインした場合は、送信状況が確認出来る画面が表示されます。）
         */
        FILE_SEND_ONETIME_AFTER("infFileSendAfter"),
        /**
         * パスワードを設定しました。
         */
        LOGIN_PSWD_SET("infLoginPswdSet"),
        /**
         * パスワードを変更しました。
         */
        LOGIN_PSWD_CHANGED("infLoginPswdChanged"),
        /**
         * 追加しました。
         */
        SUCCESS_ADD("infMessageAdd"),
        /**
         * 更新しました。
         */
        SUCCESS_UPDATE("infMessageUpdate"),
        /**
         * 削除しました。
         */
        SUCCESS_DELETE("infMessageDelete"),
        /**
         * 一括登録処理が完了しました。エラーについては、エラー内容を確認してください。
         */
        INF_BULK_INPUT_COMLPETE("infBulkInputComplete"),;
        private final String _itemKey;

        private InfMsgItemKey(final String itemKey) {
            this._itemKey = itemKey;
        }

        public String getString() {
            return this._itemKey;
        }
    }

    /**
     * 確認メッセージ用ItemKey列挙体
     *
     */
    public enum ConfirmMsgItemKey {
        /**
         * 追加確認メッセージ
         */
        CONFIRM_ADD("cfmMessageAdd"),
        /**
         * 更新確認メッセージ
         */
        CONFIRM_UPDATE("cfmMessageUpdate"),
        /**
         * 削除確認メッセージ
         */
        CONFIRM_DELETE("cfmMessageDelete"),
        /**
         * パスワード設定通知メール送信確認メッセージ
         */
        CONFIRM_NORTICE_PW_SEND("cfmNorticePwSend"),
        /**
         * ユーザーパスワード設定確認メッセージ
         */
        CONFIRM_USER_PW_SET("cfmUserPwSet"),
        /**
         * 確認メッセージ（汎用）
         */
        CONFIRM("cfmMessage"),;

        private final String _itemKey;

        private ConfirmMsgItemKey(final String itemKey) {
            this._itemKey = itemKey;
        }

        public String getString() {
            return this._itemKey;
        }
    }

    /**
     * 警告メッセージ用ItemKey列挙体
     *
     */
    public enum WarningMsgItemKey {
        /**
         * 追加確認メッセージ
         */
        WARNING_ZIP_CHARSET_UNCONVERTED("wrnZipCharsetUnconverted"),;

        private final String _itemKey;

        private WarningMsgItemKey(final String itemKey) {
            this._itemKey = itemKey;
        }

        public String getString() {
            return this._itemKey;
        }
    }

    /**
     * 無害化処理異常検出メッセージ用ItemKey列挙体
     */
    public enum DeleteReasonItemKey {
        /**
         * （メール解析時の異常検出）
         * メール解析失敗による異常
         */
        MAIL_ANALYSIS_ERR("mail_analysis_err"),
        /**
         * （添付ファイル無害化処理の異常検出）
         * Votiroへのファイルアップロード時の異常
         */
        MAIL_UPLOAD_ERR("mail_upload_err"),
        /**
         * （添付ファイル無害化処理の異常検出）
         * Votiroでの無害化時の異常
         */
        MAIL_BLOCK("mail_block"),
        /**
         * （添付ファイル無害化処理の異常検出）
         * Votiroの監視、ファイルダウンロード時の異常
         */
        MAIL_DOWNLOAD_ERR("mail_download_err"),
        /**
         * （ファイル無害化処理の異常検出）
         * Votiroへのファイルアップロード時の異常
         */
        FILE_UPLOAD_ERR("file_upload_err"),
        /**
         * （ファイル無害化処理の異常検出）
         * Votiroでの無害化時の異常
         */
        FILE_BLOCK("file_block"),
        /**
         * （ファイル無害化処理の異常検出）
         * Votiroの監視、ファイルダウンロード時の異常
         */
        FILE_DOWNLOAD_ERR("file_download_err"),
        ;

        private final String _itemKey;

        private DeleteReasonItemKey(final String itemKey) {
            this._itemKey = itemKey;
        }

        /**
         * mailMessageのitemKeyを取得
         * @return itemKey
         */
        public String getItemKey() {
            return this._itemKey;
        }
        
        /**
         * mailMessageのfuncIdを取得
         * @return funcId
         */
        public String getFuncId() {
            return FUNC_DELETEREASON;
        }
    }

}
