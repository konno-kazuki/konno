package jp.co.fujielectric.fss.data;

import java.io.File;
import java.util.List;
import jp.co.fujielectric.fss.data.CommonEnum.FileSanitizeResultKbn;
import jp.co.fujielectric.fss.entity.CheckedFile;
import jp.co.fujielectric.fss.entity.DecryptFile;
import jp.co.fujielectric.fss.entity.ReceiveFile;
import jp.co.fujielectric.fss.entity.SendFile;
import jp.co.fujielectric.fss.util.FileUtil;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * ファイル情報クラス
 */
@Data
public class FileInfoBean {

    private String fileId;
    private String fileParentId = "";
    private String fileName;
    private String filePath;
    private boolean targetFlg;
    private boolean passwordFlg;
    private String password;
    private boolean decryptFlg;
    private boolean excludeFlg;
    private boolean sanitizeFlg;
    private String fileMessage = "";
    private String fileDecryptMessage;
    private long size;
    private long downloadCount;
    private boolean receiveFlg;
    private String errMsg;
    private File file;
    private int fileErrCode;

    private String originalInfoId = "";
    private String originalFileId = "";
    private boolean originalFileFlg = false;

    private boolean cancelFlg;      ///キャンセルフラグ
    private boolean expirationFlg;  ///有効期限切れフラグ

    private boolean zipCharsetUnconvertedFlg = false;

    private boolean checked;
    
    private ReceiveFile receiveFile;

    //【v2.1.13】
    /**
     * エラー理由（送信実行時用）
     */
    public enum FileErrorReason {
        /**
         * OK
         */
        OK,
        /**
         * ファイル名文字数オーバー
         */
        FileNameLengthOver,
        /**
         * ZIP内ファイル名文字数オーバー
         */
        FileNameLengthOverInZip,
        /**
         * 高圧縮ZIPファイル（ZipBomb）
         */
        ZipBomb,
        /**
         * その他
         */
        Othre
    };    
    private FileErrorReason fileErrorReason = FileErrorReason.OK; //ファイルエラー理由【v2.1.13】

    // コンストラクタ
    public FileInfoBean() {
    }

    // コンストラクタ（パスワード解除ファイル用）
    public FileInfoBean(DecryptFile decryptFile) {
        fileId = decryptFile.getId();
        fileParentId = decryptFile.getParentId();
        fileName = decryptFile.getFileName();
        filePath = decryptFile.getFilePath();
        targetFlg = decryptFile.isTargetFlg();
        passwordFlg = decryptFile.isPasswordFlg();
        password = decryptFile.getFilePassword();
        decryptFlg = decryptFile.isDecryptFlg();
        excludeFlg = false;
        sanitizeFlg = false;
        fileMessage = "";
        size = decryptFile.getFileSize();
        downloadCount = 0;
        receiveFlg = false;

        cancelFlg = false;
        expirationFlg = false;

        checked = false;
    }

    // コンストラクタ（受信ファイル用）
    public FileInfoBean(ReceiveFile receiveFile) {
        this.receiveFile = receiveFile;
        fileId = receiveFile.getId();
        fileName = receiveFile.getFileName();
        filePath = receiveFile.getFilePath();
        targetFlg = receiveFile.isTargetFlg();
        excludeFlg = receiveFile.isExcludeFlg();
        sanitizeFlg = receiveFile.isSanitizeFlg();
        fileMessage = receiveFile.getFileMessage();
        size = receiveFile.getFileSize();
        downloadCount = receiveFile.getDownloadCount();
        receiveFlg = receiveFile.isReceiveFlg();

        cancelFlg = false;
        expirationFlg = false;

        checked = false;
        fileErrCode = receiveFile.getFileErrCode();

        // パスワード解除ファイルの状態を反映
        passwordFlg = false;
        password = "";
        decryptFlg = false;
        List<DecryptFile> decryptFiles = receiveFile.getDecryptFiles();
        if (decryptFiles != null) {
            for (DecryptFile decryptFile : decryptFiles) {
                if (decryptFile.getParentId().length() == 0) {  // 親ファイル
                    passwordFlg = decryptFile.isPasswordFlg();
                    password = decryptFile.getFilePassword();
                    decryptFlg = decryptFile.isDecryptFlg();
                    break;
                }
            }
        }

        // zip内文字コード変換失敗状態
        if (receiveFile.isZipCharsetConvert() && !receiveFile.isZipCharsetConverted()) {
            zipCharsetUnconvertedFlg = true;
        }
    }

    /**
     * コンストラクタ（原本ファイル用）
     *
     * @param receiveFile 受信ファイル
     * @param sendFile 送信ファイル
     */
    public FileInfoBean(ReceiveFile receiveFile, SendFile sendFile) {
        this.receiveFile = receiveFile;
        fileId = receiveFile.getId();
        fileName = sendFile.getFileName();
        filePath = sendFile.getFilePath();
        size = sendFile.getFileSize();
        downloadCount = receiveFile.getDownloadCount();
        receiveFlg = receiveFile.isReceiveFlg();

        // 通常の受信可能状態としてデータを整理
        targetFlg = true;
        excludeFlg = false;
        sanitizeFlg = true;
        fileMessage = "";
        cancelFlg = false;
        expirationFlg = false;

        checked = false;

        passwordFlg = false;
        password = "";
        decryptFlg = false;

        // 原本情報として送信情報、ファイルのIDを格納
        originalInfoId = sendFile.getSendInfo().getId();
        originalFileId = sendFile.getId();
        // 原本ファイルフラグをtrueへ
        originalFileFlg = true;
    }

    /**
     * コンストラクタ（原本ファイル用）
     *
     * @param sendFile 送信ファイル
     */
    public FileInfoBean(SendFile sendFile) {
        fileId = sendFile.getId();
        fileName = sendFile.getFileName();
        filePath = sendFile.getFilePath();
        size = sendFile.getFileSize();

        // 通常の受信可能状態としてデータを整理
        targetFlg = true;
        excludeFlg = false;
        sanitizeFlg = true;
        fileMessage = "";
        cancelFlg = false;
        expirationFlg = false;

        checked = false;

        passwordFlg = false;
        password = "";
        decryptFlg = false;

        // 原本情報として送信情報、ファイルのIDを格納
        originalInfoId = sendFile.getSendInfo().getId();
        originalFileId = sendFile.getId();
        // 原本ファイルフラグをtrueへ
        originalFileFlg = true;
    }

    /**
     * コンストラクタ（ふるまい検知ファイル用）
     *
     * @param receiveFile 受信ファイル
     * @param checkedFile ふるまい検知ファイル
     */
    public FileInfoBean(ReceiveFile receiveFile, CheckedFile checkedFile) {
        fileId = receiveFile.getId();
        fileName = checkedFile.getFileName();
        filePath = checkedFile.getFilePath();
        size = checkedFile.getFileSize();
        downloadCount = receiveFile.getDownloadCount();
        receiveFlg = receiveFile.isReceiveFlg();

        // 通常の受信可能状態としてデータを整理
        targetFlg = true;
        excludeFlg = false;
        sanitizeFlg = checkedFile.isCheckedFlg();
        fileMessage = checkedFile.getFileMessage();
        cancelFlg = false;
        expirationFlg = false;

        checked = false;

        passwordFlg = false;
        password = "";
        decryptFlg = false;

        // 原本情報として送信情報、ファイルのIDを格納
        originalInfoId = receiveFile.getReceiveInfo().getSendInfoId();
        originalFileId = checkedFile.getId();
        // 原本ファイルフラグをtrueへ
        originalFileFlg = true;
    }

    // コンストラクタ（ファイル用）
    public FileInfoBean(File file) {
        this.file = file;
        this.fileId = "0";
        this.fileName = file.getName();
        this.filePath = file.getPath();
        this.targetFlg = false;
        this.passwordFlg = false;
        this.password = "";
        this.checked = false;
        this.size = file.length();
        this.downloadCount = 0;
        this.receiveFlg = false;
        this.errMsg = "";
        this.fileErrCode = 0;

        this.cancelFlg = false;
        this.expirationFlg = false;

    }

    public String getSizeText() {
        if (fileId.equals("")) {
            return "";
        }
        String sizeText;
        //sizeText = String.format("%1$,3d KByte", (long) Math.ceil(size / 1024.0));
        sizeText = FileUtil.getSizeText(size);
        return sizeText;
    }

    public String getFileIcon() {
        return FileUtil.getFileIconKind(fileName);
    }

    public boolean isFileNormal() {
        if (fileErrCode == FileSanitizeResultKbn.ARCHIVECHILD_ERROR.value) {
            //アーカイブファイル内の一部ファイルで異常検出
            return (targetFlg && sanitizeFlg && !cancelFlg && !expirationFlg);
        }

        return (targetFlg && sanitizeFlg && fileMessage.length() == 0 && !cancelFlg && !expirationFlg);
    }

    public boolean isDecrypting() {
        return (targetFlg && !excludeFlg && passwordFlg && !decryptFlg);
    }

    public boolean isUnlocked() {
        return (targetFlg && !decryptFlg && passwordFlg && password.length() > 0);
    }


    public boolean isFileArchiveChildErr() {
        return fileErrCode == FileSanitizeResultKbn.ARCHIVECHILD_ERROR.value;
    }

    //
    // 以下、ダウンロード画面専用
    //
    public String getDownloadStatusText() {
        String downloadStatusText;
        downloadStatusText = downloadCount > 0 ? String.valueOf(downloadCount) : "";
        return downloadStatusText;
    }

    public String getStatusIcon() {
        String statusIcon;
        if (cancelFlg) {                                                        // キャンセル
            statusIcon = "Trash.png";
        } else if (excludeFlg) {                                                // 破棄
            statusIcon = "Trash.png";
        } else if (expirationFlg) {                                             // 有効期限切れ
            statusIcon = "Trash.png";
        } else if (isUnlocked()) {                                              // パスワード未解除
            statusIcon = "Unlock.png";
        } else if (sanitizeFlg && fileMessage.length() > 0 
                && fileErrCode != FileSanitizeResultKbn.ARCHIVECHILD_ERROR.value) { // 無害化された
            statusIcon = "Cancel.png";
        } else {
            statusIcon = "None.png";                                            // (透明)
        }
        return statusIcon;
    }

    //
    // 以下、アップロード画面専用
    //
    public boolean isError() {
        return (!StringUtils.isEmpty(errMsg));
    }
}
