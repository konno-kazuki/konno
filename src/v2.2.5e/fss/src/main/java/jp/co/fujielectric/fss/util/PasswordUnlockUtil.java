package jp.co.fujielectric.fss.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.PDEncryption;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
//import org.apache.poi.ss.usermodel.Workbook;
//import org.apache.poi.xslf.usermodel.XMLSlideShow;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;
//import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * パスワード解除ユーティリティ
 */
public class PasswordUnlockUtil {

    static Logger LOG = LogManager.getLogger();

    /**
     * パスワード付ファイルを精査
     *
     * @param filePath ファイル名（フルパス）
     * @return 結果(true:PW付、false:PW無)
     */
    public static boolean isPwdProtected(String filePath) {
        if (StringUtils.isEmpty(filePath)) {
            return false;
        }

        //読み込めないファイルの場合（ウィルス等）パスワードなしと判定する
        if(!Files.isReadable(Paths.get(filePath)))
            return false;        
        
        // 拡張子の対象チェック
        String suffix = FileUtil.getSuffix(filePath).toLowerCase();
        if (!FileUtil.SUFFIXLST.contains(suffix)) {
            return false;                 // 対象外
        }
        //パスワード有無のチェック
        if (suffix.equalsIgnoreCase(FileUtil.PDFSUFFIX)) {
            //PDFファイルパスワード設定(Off)
            return isPwdProtectedPDF(filePath);
        } else {
            ///パスワード付きOfficeファイルの読み込み＆書き込み。
            ///Office2003より新しい形式専用の処理。
            return isPwdProtectedOOX(filePath);
        }
    }

    /**
     * パスワード付ファイルの精査(PDF)
     *
     * @param filePath ファイル名（フルパス）
     * @return 結果(true:PW付、false:PW無)
     */
    public static boolean isPwdProtectedPDF(String filePath) {
        try {
            //パスワード指定なしで読込んでみる。
            try (PDDocument document = PDDocument.load(new File(filePath))) {
                //パスワード指定なしでloadできればパスワード付ではないと判断できる
                //boolean isEncrypted = document.isEncrypted();
            }
            return false;
        } catch (InvalidPasswordException ex){
            //パスワード付の場合はパスワード指定していないのでInvalidPasswordExceptionが発生
            return true;
        } catch (IOException ex) {
            //ファイルがPDFファイルとして読み込めない場合（拡張子偽装等）はIOExceptionが発生
            //パスワード付PDFではないと判定する
            return false;
        } catch (Exception ex) {
            //それ以外の例外の場合、パスワード付PDFではないと判定する
            return false;
        }        
    }

    /**
     * パスワード付きOfficeファイルの読み込み。<br>
     * Office2003より新しい形式専用の処理。<br>
     * 参考：https://poi.apache.org/encryption.html
     *
     * @param filePath ファイル名（フルパス）
     * @return 結果(true:PW付、false:PW無)
     */
    public static boolean isPwdProtectedOOX(String filePath) {
        //パスワード付きOfficeファイルの読み込み
        try(InputStream inputStream = new FileInputStream(filePath);
            POIFSFileSystem fileSystem = new POIFSFileSystem(inputStream);) 
        {
            return true;
        } catch (Exception e) {
            // 「例外発生=パスワード無」と判定する。
            // ※パスワードがない場合に、形式エラーでExceptionとなる
            return false;
        }
    }

    //【v2.1.13】
    /**
     * パスワード付ファイルを精査（InputStream対応）
     * @param filePath ファイル名（フルパス）
     * @param is    InputStream
     * @return 結果(true:PW付、false:PW無)
     */
    public static boolean isPwdProtectedInMem(String filePath, InputStream is) {
        if (StringUtils.isBlank(filePath)) {
            return false;
        }        
        // 拡張子の対象チェック
        String suffix = FileUtil.getSuffix(filePath).toLowerCase();
        if (!FileUtil.SUFFIXLST.contains(suffix)) {
            return false;                 // 対象外
        }
        //パスワード有無のチェック
        if (suffix.equalsIgnoreCase(FileUtil.PDFSUFFIX)) {
            //PDFファイルパスワード設定(Off)
            return isPwdProtectedPDF(is);
        } else {
            ///パスワード付きOfficeファイルの読み込み＆書き込み。
            ///Office2003より新しい形式専用の処理。
            return isPwdProtectedOOX(is);
        }
    }
    
    //【v2.1.13】
    /**
     * パスワード付ファイルの精査(PDF) InputStrema対応
     *
     * @param is    InputStream
     * @return 結果(true:PW付、false:PW無)
     */
    public static boolean isPwdProtectedPDF(InputStream is) {
        try {
            //パスワード指定なしで読込んでみる。
            try (PDDocument document = PDDocument.load(is)) {
                //パスワード指定なしでloadできればパスワード付ではないと判断できる
                //boolean isEncrypted = document.isEncrypted();
            }
            return false;
        } catch (InvalidPasswordException ex){
            //パスワード付の場合はパスワード指定していないのでInvalidPasswordExceptionが発生
            return true;
        } catch (IOException ex) {
            //ファイルがPDFファイルとして読み込めない場合（拡張子偽装等）はIOExceptionが発生
            //パスワード付PDFではないと判定する
            return false;
        } catch (Exception ex) {
            //それ以外の例外の場合、パスワード付PDFではないと判定する
            return false;
        }        
    }
    
    //【v2.1.13】
    /**
     * パスワード付きOfficeファイルの読み込み。<br>
     *
     * @param is    InputStream
     * @return 結果(true:PW付、false:PW無)
     */
    public static boolean isPwdProtectedOOX(InputStream is) {
        //パスワード付きOfficeファイルの読み込み
        try(POIFSFileSystem fileSystem = new POIFSFileSystem(is);){
            return true;
        } catch (Exception e) {
            // 「例外発生=パスワード無」と判定する。
            // ※パスワードがない場合に、形式エラーでExceptionとなる
            return false;
        }
    }
    
     /**
     * パスワード解除
     *
     * @param filePath ファイル名（フルパス）
     * @param password パスワード
     * @param outputPath 出力先ファイル名（フルパス）
     * @return 結果(true:成功、false:失敗)
     */
    public static boolean unlockPassword(String filePath, String password, String outputPath) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "BEGIN", 
                "filePath:" + filePath, "password:" + password, "outputPath:" + outputPath));
        if (StringUtils.isEmpty(filePath)) {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "END", "ret:false", "filePath:" + filePath));
            return false;
        }

        // 拡張子の対象チェック
        String suffix = FileUtil.getSuffix(filePath).toLowerCase();
        if (!FileUtil.SUFFIXLST.contains(suffix)) {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "END", "ret:false", "filePath:" + filePath));
            return false;                 // 対象外
        }
        
        //出力先フォルダが存在しない場合は作成する
        File outputDir = new File(outputPath).getParentFile();
        if(!outputDir.exists()){
            outputDir.mkdirs();
        }
        
        //パスワード解除
        boolean isUnlockPassword;
        if (suffix.equalsIgnoreCase(FileUtil.PDFSUFFIX)) {
            //PDFファイルパスワード設定(Off)
            isUnlockPassword = unlockPasswordPdf(filePath, password, outputPath);
        } else {
            ///パスワード付きOfficeファイルの読み込み＆書き込み。
            ///Office2003より新しい形式専用の処理。
            isUnlockPassword = unlockPasswordOfficeX(filePath, password, outputPath);
        }

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "END", "ret:" + isUnlockPassword, "filePath:" + filePath));
        return isUnlockPassword;
    }

    /**
     * PDFファイルのパスワード解除
     *
     * @param filePath ファイル名（フルパス）
     * @param password パスワード
     * @param outputPath 出力先ファイル名（フルパス）
     * @return 結果(ture:解除、false:失敗)
     */
    public static boolean unlockPasswordPdf(String filePath, String password, String outputPath) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "BEGIN", "filePath:" + filePath, "password:" + password));
        if (StringUtils.isEmpty(filePath)) {
            return false;
        }

        // pdfboxでパスワード付きPDFを開いて保存
        // ※最新のセキュリティバージョンに対応
        try (PDDocument document = PDDocument.load(new File(filePath), password)) {
            document.setAllSecurityToBeRemoved(true);
            document.save(outputPath);
            return true;
        } catch (Exception ex) {
            LOG.debug("#!Can not unlock Password.(Pdf) [{}]", ex.toString());
            return false;
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "END", "filePath:" + filePath));
        }
    }

    /**
     * PDFファイルのパスワード解除<br>
     * Office2003より新しい形式専用の処理。<br>
     * 参考：https://poi.apache.org/encryption.html
     *
     * @param filePath ファイル名（フルパス）
     * @param password パスワード
     * @param outputPath 出力先ファイル名（フルパス）
     * @return 結果(ture:解除、false:失敗)
     */
    public static boolean unlockPasswordOfficeX(String filePath, String password, String outputPath) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "BEGIN", "filePath:" + filePath, "password:" + password));
        try (FileInputStream fileInputStream = new FileInputStream(filePath);
            POIFSFileSystem fileSystem = new POIFSFileSystem(fileInputStream);)
        {
            EncryptionInfo info = new EncryptionInfo(fileSystem);
            Decryptor decryptor = Decryptor.getInstance(info);

            if (decryptor.verifyPassword(password) == false) {
                return false;
            }
            
            try (InputStream inputStream = decryptor.getDataStream(fileSystem)) {
                switch (FileUtil.getSuffix(filePath).substring(0, 2).toLowerCase()) {
                    case "xl":      // Excel
//                        Workbook workbook = new XSSFWorkbook(inputStream);
//                        workbook.write(new FileOutputStream(filePath));
//                        workbook.close();                       
                        break;
                    case "do":      // Word
//                        XWPFDocument doc = new XWPFDocument(inputStream);
//                        doc.write(new FileOutputStream(filePath));
                        break;
                    case "pp":      // PowerPoint
                    case "po":
//                        XMLSlideShow slideShow = new XMLSlideShow(inputStream);
//                        slideShow.write(new FileOutputStream(filePath));
                        break;
                    default:
                        // ファイルを開く前に拡張子判断するのがよいが保留。
                        return false;
                }
                //[Ver2.1.10]InputStreamから直接ファイルに変換するように修正
                Files.copy(inputStream, new File(outputPath).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception ex) {
            LOG.debug("#!Can not unlock Password.(OfficeX) [{}]", ex.toString());
            return false;
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "END", "filePath:" + filePath));
        }
    }

    /**
     * PDFファイルの暗号化情報
     *
     * @param filePath ファイル名（フルパス）
     * @param password パスワード
     * @return 暗号化情報
     */
    public static String getEncryptInfoPdf(String filePath, String password) {
        if (StringUtils.isEmpty(filePath)) {
            return "";
        }

        // pdfboxでパスワード付きPDFを開いて保存
        try (PDDocument document = PDDocument.load(new File(filePath), password)) {
            PDEncryption encryption = document.getEncryption();
//            System.out.println("length=" + encryption.getLength());
            String encryptInfo = String.valueOf(encryption.getLength());        // 暗号化キーサイズ： 40, 128 or 256
            // TODO: RC4 or AES を取得したい
            return encryptInfo;
        } catch (Exception ex) {
            ex.printStackTrace();
            return "";
        }
    }

    /**
     * パスワード付与
     *
     * @param filePath ファイル名（フルパス）
     * @param password パスワード
     * @param encryptModePDF [PDF]暗号化モード（1:40Bit RC4, 2:128Bit RC4, 3:128Bit AES, 4:256Bit AES）
     * @return 結果(true:成功、false:失敗)
     */
    public static boolean lockPassword(String filePath, String password, int encryptModePDF) {
        if (StringUtils.isEmpty(filePath)) {
            return false;
        }

        // 拡張子の対象チェック
        String suffix = FileUtil.getSuffix(filePath).toLowerCase();
        if (!FileUtil.SUFFIXLST.contains(suffix)) {
            return false;                 // 対象外
        }
        // パスワード付与
        if (suffix.equalsIgnoreCase(FileUtil.PDFSUFFIX)) {
            // PDF
            return lockPasswordPdf(filePath, password, encryptModePDF);
        } else {
            // Officeファイル
            return lockPasswordOfficeX(filePath, password);
        }
    }

    /**
     * パスワード付与（ＰＤＦ）
     *
     * @param filePath ファイル名（フルパス）
     * @param password パスワード
     * @param encryptModePDF [PDF]暗号化モード（1:40Bit RC4, 2:128Bit RC4, 3:128Bit AES, 4:256Bit AES）
     * @return 結果(ture:成功、false:失敗)
     */
    private static boolean lockPasswordPdf(String filePath, String password, int encryptModePDF) {
        int keyLength;
        boolean preferAES;
        switch (encryptModePDF) {
            case 1:     // 40Bit RC4
                keyLength = 40;
                preferAES = false;
                break;
            case 2:     // 128Bit RC4
                keyLength = 128;
                preferAES = false;
                break;
            case 3:     // 128Bit AES
                keyLength = 128;
                preferAES = true;
                break;
            case 4:     // 256Bit AES
                keyLength = 256;
                preferAES = true;
                break;
            default:    // システム規定値は「128Bit AES（PDF1.6以降）」
                keyLength = 128;
                preferAES = true;
                break;
        }

        // pdfboxでPDFを開いてパスワード付与して保存
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            document.setAllSecurityToBeRemoved(true);
            AccessPermission ap = new AccessPermission();
            ap.setCanPrint(false);
            String ownerPassword = password;
            String userPassword = password;
            StandardProtectionPolicy spp = new StandardProtectionPolicy(ownerPassword, userPassword, ap);
            spp.setEncryptionKeyLength(keyLength);                              // 暗号化キーサイズ： 40, 128 or 256
            spp.setPreferAES(preferAES);                                        // 暗号化方式： true:AES, false:RC4
            spp.setPermissions(ap);
            document.protect(spp);

            document.save(filePath);
            document.close();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * パスワード付与（Ｏｆｆｉｃｅファイル）
     *
     * @param filePath ファイル名（フルパス）
     * @param password パスワード
     * @return 結果(ture:成功、false:失敗)
     */
    private static boolean lockPasswordOfficeX(String filePath, String password) {
        // Officeファイルを開いてパスワード付与して保存
        try {
            try (POIFSFileSystem fileSystem = new POIFSFileSystem()) {
                EncryptionInfo encryptionInfo = new EncryptionInfo(EncryptionMode.agile);
                Encryptor encryptor = encryptionInfo.getEncryptor();
                encryptor.confirmPassword(password);
                try (OPCPackage opcPackage = OPCPackage.open(filePath, PackageAccess.READ_WRITE);
                    OutputStream outputStream = encryptor.getDataStream(fileSystem);) {
                    opcPackage.save(outputStream);
                }
                try (FileOutputStream fileOutputStream = new FileOutputStream(filePath)) {
                    fileSystem.writeFilesystem(fileOutputStream);               // パスワード付きで上書き保存
                }
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
