/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 共通Enum定義用クラス
 */
public class CommonEnum {
    //[v2.2.1]
    /**
     * 処理ステップ区分
     * 1：無害化開始待ち
     * 2：SandBlastアップロード中
     * 3：アップロード待ち
     * 4：アップロード中
     * 5：ダウンロード待ち
     * 6：ダウンロード済み
     * 7：無害化完了
     * 8：キャンセル	
     */
    public static enum StepKbn {
        /**
         * 未定義
         */
        Unknown(0),
        /**
         * 無害化開始待ち
         */
        StartWait(1),
        /**
         * SandBlastアップロード中
         */
        SandBlastUploading(2),
        /**
         * Votiroアップロード待ち
         */
        VotiroUploadWait(3),
//        /**
//         * Votiroアップロード中
//         */
//        VotiroUploading(4),
        /**
         * Votiroダウンロード待ち
         */
        VotiroDownloadWait(5),
        /**
         * Votiroダウンロード済み
         */
        VotiroDownloaded(6),
        /**
         * 無害化済み
         */
        Completed(7),
        /**
         * キャンセル
         */
        Cancel(8);
    
        // フィールドの定義
        public final int value;

        // コンストラクタの定義
        private StepKbn(int kbn) {
            this.value = kbn;
        }
        
        /**
         * StepKbnのリストをIntegerのリストに変換
         * @param stepList
         * @return 
         */
        public static List<Integer> getValueLst(List<StepKbn>  stepList){
            List<Integer> stepListI = new ArrayList<>();
            stepList.forEach((kbn) -> {
                stepListI.add(kbn.value);
            });
            return stepListI;
        }
        
        /**
         * 設定値（数値）からStepKbnを返す
         * @param step
         * @return 
         */
        public static StepKbn getStepKbn(int step){
            for(StepKbn stepKbn: StepKbn.values()){
                if(stepKbn.value == step)
                    return stepKbn;
            }
            return StepKbn.Unknown; //対象外の場合はUnknownを返す
        }        
    }
    
    //[v2.2.1]
    /**
     * SandBlast対応区分
     * 0：不使用
     * 1：使用。Votiroアップロードには使用しない。（例：静岡）
     * 2：使用。Votiroアップロードに使用。（例：京都）
     */
    public static enum SandBlastKbn {
        /**
         * 不使用
         */
        NONE(0),
        /**
         * 使用。Votiroアップロードには使用しない。（例：静岡）
         */
        CHECK_ONLY(1),
        /**
         * 使用。Votiroアップロードに使用。（例：京都）
         */
        USE_VOTIRO(2);
    
        // フィールドの定義
        public final int value;

        // コンストラクタの定義
        private SandBlastKbn(int kbn) {
            this.value = kbn;
        }
        
        /**
         * SandBlastKbnのリストをIntegerのリストに変換
         * @param kbnList
         * @return 
         */
        public static List<Integer> getValueLst(List<SandBlastKbn>  kbnList){
            List<Integer> valueListI = new ArrayList<>();
            kbnList.forEach((kbn) -> {
                valueListI.add(kbn.value);
            });
            return valueListI;
        }
        
        /**
         * 数字文字列をSandBlastKbnに変換
         * @param strKbn
         * @return 
         */
        public static SandBlastKbn getSandBlastKbn(String strKbn){
            for(SandBlastKbn kbn: values()){
                if(String.valueOf(kbn.value).equals(strKbn))
                    return kbn;
            }
            return NONE;
        }
    }        

    //[v2.2.1]
    /**
     * パスワード解除状態	区分	
     * 0:パスワード無しファイル
     * 2:パスワード付きファイル　パスワード解除済み
     * 3:パスワード付きファイル　パスワード未解除	
     * 4:パスワード付きファイル入りZIPファイル　全ファイル解除済み
     * 5:パスワード付きファイル入りZIPファイル　パスワード未解除あり
     * Votiro負荷軽減で同一ファイル判定に利用		
     */
    public static enum DecryptKbn {
        /**
         * パスワード無しファイル
         */
        NONE(0),
        /**
         * パスワード付きファイル　パスワード解除済み
         */
        DECRYPTED(2),
        /**
         * パスワード付きファイル　パスワード未解除
         */
        ENCTYPT(3),
        /**
         * パスワード付きファイル入りZIPファイル　全ファイル解除済み
         */
        DECRYPTEDZIP(4),
        /**
         * パスワード付きファイル入りZIPファイル　パスワード未解除あり
         */
        ENCRYPTZIP(5);
    
        // フィールドの定義
        public final int value;

        // コンストラクタの定義
        private DecryptKbn(int kbn) {
            this.value = kbn;
        }
    }        
    
    //[v2.2.1]
    /**
     * 無害化結果区分
     * 0:処理中
     * 1:無害化済み
     * 2:ブロック
     * 4:無害化エラー
     * 5:無害化対象外
     * 6:キャンセル
     */
    public static enum ResultKbn {
        /**
         * 処理中
         */
        PROCESSING(0),
        /**
         * 無害化済み
         */
        SANITIZED(1),
        /**
         * ブロック
         */
        BLOCKED(2),
        /**
         * 無害化エラー
         */
        ERROR(4),
        /**
         * 無害化非対応エラー
         */
        REJECTED(5),
        /**
         * キャンセル
         */
        CANCEL(6),
        /**
         * 無害化なし
         */
        NONE(7),
        ;
    
        // フィールドの定義
        public final int value;

        // コンストラクタの定義
        private ResultKbn(int value) {
            this.value = value;
        }
        
        /**
         * 設定値（数値）からResultKbnを返す
         * @param value
         * @return 
         */
        public static ResultKbn getResultKbn(int value){
            for(ResultKbn kbn: ResultKbn.values()){
                if(kbn.value == value)
                    return kbn;
            }
            return ResultKbn.PROCESSING; //対象外の場合はPROCESSINGを返す
        }
    }

    //[v2.2.3]
    /**
     * 無害化処理結果区分
     * ０：正常（エラー無し）
     * １：メール解析失敗による異常
     * ２：Votiroへのファイルアップロード時の異常
     * ３：Votiroでの無害化時の異常
     * ４：Votiroの監視、ファイルダウンロード時の異常
     */
    public static enum ProcResultKbn {
        /**
         * 正常（エラー無し）
         */
        SUCCESS(0),
        /**
         * メール解析失敗による異常
         */
        MAILANALYSIS_ERROR(1),
        /**
         * Votiroへのファイルアップロード時の異常
         */
        UPLOAD_ERROR(2),
        /**
         * Votiroでの無害化時の異常
         */
        SANITIZED_ERROR(3),
        /**
         * Votiroの監視、ファイルダウンロード時の異常
         */
        DOWNLOAD_ERROR(4),
        ;

        // フィールドの定義
        public final int value;

        // コンストラクタの定義
        private ProcResultKbn(int kbn) {
            this.value = kbn;
        }
    }

    //[v2.2.3]
    /**
     * メール解析失敗区分
     * 1：メール読込み時異常発生
     * 2：メールヘッダ解析異常
     * 3：メール本文解析異常
     * 4：添付ファイル解析異常
     */
    public static enum MailAnalyzeResultKbn {
        /**
         * 正常（エラー無し）
         */
        SUCCESS(0),
        /**
         * メール読込み時異常発生
         */
        ORGMAIL_READ_ERROR(1),
        /**
         * メールヘッダ解析異常
         */
        HEADER_OTHER_ERROR(2),
        /**
         * メール本文解析異常
         */
        BODY_ERROR(3),
        /**
         * 添付ファイル解析異常
         */
        ATTACHMENT_ERROR(4),
        ;

        // フィールドの定義
        public final int value;

        // コンストラクタの定義
        private MailAnalyzeResultKbn(int kbn) {
            this.value = kbn;
        }

        /**
         * 設定値（数値）からMailAnalyzeResultKbnを返す
         * @param value
         * @return
         */
        public static MailAnalyzeResultKbn getMailAnalyzeResultKbn(int value){
            for(MailAnalyzeResultKbn kbn: MailAnalyzeResultKbn.values()){
                if(kbn.value == value)
                    return kbn;
            }
            return MailAnalyzeResultKbn.SUCCESS; //対象外の場合はSUCCESSを返す
        }
    }

    //[v2.2.3]
    /**
     * ファイル無害化結果区分
     * 0：正常（エラーなし）
     * 1：対象ファイルで異常検出
     * 2：アーカイブファイル内のファイルで異常検出
     */
    public static enum FileSanitizeResultKbn {
        /**
         * 正常（エラー無し）
         */
        SUCCESS(0),
        /**
         * 対象ファイルで異常検出
         */
        FILE_ERROR(1),
        /**
         * アーカイブファイル内のファイルで異常検出
         */
        ARCHIVECHILD_ERROR(2),
        ;

        // フィールドの定義
        public final int value;

        // コンストラクタの定義
        private FileSanitizeResultKbn(int kbn) {
            this.value = kbn;
        }
    }
    
}
