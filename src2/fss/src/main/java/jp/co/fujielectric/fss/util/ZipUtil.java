package jp.co.fujielectric.fss.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.ZipOutputStream;
import net.lingala.zip4j.model.ZipModel;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.logic.ItemHelper;
import static jp.co.fujielectric.fss.util.FileUtil.ZIPSUFFIX;
import lombok.Data;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.util.InternalZipConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

/**
 * Zip関連ユーティリティ
 */
public class ZipUtil {
    //private static final String C_CHARSET_NAME = "csWindows31J";
    public static final String CHARSET_csWindows31J = "csWindows31J";

    /**
     * MAC-ZIPファイル判定用フォルダ名
     */
    private static final String MACZIP_FOLDERNAME = "__MACOSX";
    
    /**
     * ZIPファイル判定<br>
     * ※拡張子がzipファイルであること。<br>
     * net.lingala.zip4j.core.ZipFile.isValidZipFileでエラーとならないこと。
     *
     * @param filePath ファイルパス
     * @return Zipファイル判定結果[true:Zipファイル, false:その他]
     */
    public static boolean isZipFile(String filePath) {
        if(!FileUtil.getSuffix(filePath).equalsIgnoreCase(FileUtil.ZIPSUFFIX)) return false;
        try {
            File file = new File(filePath);
            if(!file.exists() || !file.isFile())
                return false;
            ZipFile zipFile = new ZipFile(file);
            return zipFile.isValidZipFile();
        } catch (ZipException ex) {
            return false;
        }        
    }
    
    /**
     * パスワードZIP判定
     *
     * @param filePath ファイルパス
     * @return パスワードZIP判定結果[true:パスワード有, false:パスワード無]
     */
    public static boolean isEncrypted(String filePath) {
        try {
            //読み込めないファイルの場合（ウィルス等）パスワードなしと判定する
            if(!Files.isReadable(Paths.get(filePath)))
                return false;               
            
            ZipFile zipFile = new ZipFile(filePath);
            if (!zipFile.isValidZipFile()) {
                return false;
            }
            return (zipFile.isEncrypted());
        } catch (ZipException ex) {
            return false;
        }
    }

    /**
     * 指定ファイルのZIPファイルを作成する。<br>
     * フォルダ配下全てのフォルダ対応
     *
     * @param targetFolder 対象フォルダ
     * @param outputPath 出力ZIPファイルパス
     * @throws net.lingala.zip4j.exception.ZipException
     */
    public static void createZip(String targetFolder, String outputPath) throws ZipException {
        File[] fileList = FileUtil.getFileFolder(new File(targetFolder).listFiles());
        createZip(targetFolder, fileList, outputPath);
    }

    /**
     * 指定ファイルのZIPファイルを作成する
     *
     * @param inputFolder 対象基準フォルダ
     * @param fileList 対象ファイルリスト
     * @param zipPath 出力ZIPファイル名
     * @throws net.lingala.zip4j.exception.ZipException
     */
    public static void createZip(String inputFolder, File[] fileList, String zipPath) throws ZipException {
        //zipファイルが既存の場合は削除する
        File exFile = new File(zipPath);
        if (exFile.exists()) {
            exFile.delete();
        }

        ZipFile zipFile = new ZipFile(zipPath);
        zipFile.setFileNameCharset(InternalZipConstants.CHARSET_UTF8);

        ZipParameters parameters = new ZipParameters();
        parameters.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
        parameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
        parameters.setDefaultFolderPath(inputFolder);

        for (File file : fileList) {
            zipFile.addFile(file, parameters);
        }
    }

    private static String getFileNameCharset(int fileNameCharsetType) {
        // 対応
        switch (fileNameCharsetType) {
        case 1:     // MS932(SJIS)
            return CHARSET_csWindows31J;
        case 2:     // UTF-8
            return InternalZipConstants.CHARSET_UTF8;
        default:
            return InternalZipConstants.CHARSET_UTF8;
        }
    }

    /**
     * 指定ファイルのZIPファイルを作成する。<br>
     * フォルダ配下全てのフォルダ対応
     *
     * @param targetFolder 対象フォルダ
     * @param password 圧縮パスワード
     * @throws net.lingala.zip4j.exception.ZipException
     * @throws java.io.IOException
     * @throws java.lang.CloneNotSupportedException
     */
    public static byte[] createZipInMemory(String targetFolder, String password)
            throws ZipException, IOException, CloneNotSupportedException {
        // デフォルトはUTF-8
        return createZipInMemory(targetFolder, password, InternalZipConstants.CHARSET_UTF8);
    }

    /**
     * 指定ファイルのZIPファイルを作成する。<br>
     * フォルダ配下全てのフォルダ対応
     *
     * @param targetFolder 対象フォルダ
     * @param password 圧縮パスワード
     * @param fileNameCharsetType ファイル名の文字コード種類
     * @throws net.lingala.zip4j.exception.ZipException
     * @throws java.io.IOException
     * @throws java.lang.CloneNotSupportedException
     */
    public static byte[] createZipInMemory(String targetFolder, String password, int fileNameCharsetType)
            throws ZipException, IOException, CloneNotSupportedException {
        return createZipInMemory(targetFolder, password, getFileNameCharset(fileNameCharsetType));
    }

    /**
     * 指定ファイルのZIPファイルを作成する。<br>
     * フォルダ配下全てのフォルダ対応
     *
     * @param targetFolder 対象フォルダ
     * @param password 圧縮パスワード
     * @param fileNameCharset ファイル名の文字コード
     * @throws net.lingala.zip4j.exception.ZipException
     * @throws java.io.IOException
     * @throws java.lang.CloneNotSupportedException
     */
    public static byte[] createZipInMemory(String targetFolder, String password, String fileNameCharset)
            throws ZipException, IOException, CloneNotSupportedException {
        ZipModel zipModel = new ZipModel();
        zipModel.setFileNameCharset(fileNameCharset);

        ZipParameters parameters = new ZipParameters();
        parameters.setDefaultFolderPath(targetFolder);

        // 圧縮率
        parameters.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
        parameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);

        // 暗号化
        if (password != null && password.trim().length() > 0) {
            parameters.setEncryptFiles(true);
            parameters.setEncryptionMethod(Zip4jConstants.ENC_METHOD_STANDARD); // 標準の暗号化
//            parameters.setAesKeyStrength(aesKeyStrength);
            parameters.setPassword(password.trim());                            // パスワード
        }

        try(ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();
            ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOut, zipModel);)
        {
            File[] fileList = FileUtil.getFileFolder(new File(targetFolder).listFiles());
            for (File file : fileList) {
                zipOutputStream.putNextEntry(file, parameters);
                try (InputStream inputStream = new FileInputStream(file)) {
                    byte[] readBuff = new byte[4096];
                    int readLen;
                    while ((readLen = inputStream.read(readBuff)) != -1) {
                        zipOutputStream.write(readBuff, 0, readLen);
                    }
                    zipOutputStream.closeEntry();
                }
            }
            zipOutputStream.finish();            
            return byteArrayOut.toByteArray();
        }
    }

    /**
     * 指定ファイルのZIPファイルを作成する
     *
     * @param fileList 対象ファイルリスト
     * @return 圧縮後のZipファイル（byte配列）
     * @throws net.lingala.zip4j.exception.ZipException
     * @throws java.io.IOException
     * @throws java.lang.CloneNotSupportedException
     */
    public static byte[] createZipInMemory(List<File> fileList)
            throws ZipException, IOException, CloneNotSupportedException {
        return ZipUtil.createZipInMemory(fileList, null);
    }

    /**
     * 指定ファイルのZIPファイルを作成する
     *
     * @param fileList 対象ファイルリスト
     * @param password 圧縮パスワード
     * @return 圧縮後のZipファイル（byte配列）
     * @throws net.lingala.zip4j.exception.ZipException
     * @throws java.io.IOException
     * @throws java.lang.CloneNotSupportedException
     */
    public static byte[] createZipInMemory(List<File> fileList, String password)
            throws ZipException, IOException, CloneNotSupportedException {
        return ZipUtil.createZipInMemory(fileList, null, null);
    }

    /**
     * 指定ファイルのZIPファイルを作成する
     *
     * @param fileList 対象ファイルリスト
     * @param password 圧縮パスワード
     * @param charset 文字コード
     * @return 圧縮後のZipファイル（byte配列）
     * @throws ZipException
     * @throws IOException
     * @throws CloneNotSupportedException
     */
    public static byte[] createZipInMemory(List<File> fileList, String password, String charset)
            throws ZipException, IOException, CloneNotSupportedException {
        if(StringUtils.isEmpty(charset)) {
            charset = InternalZipConstants.CHARSET_UTF8;
        }

        ZipModel zipModel = new ZipModel();
        zipModel.setFileNameCharset(charset);

        ZipParameters parameters = new ZipParameters();
//        parameters.setDefaultFolderPath(folderPath);

        // 圧縮率
        parameters.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
        parameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);

        // 暗号化
        if (password != null && password.trim().length() > 0) {
            parameters.setEncryptFiles(true);
            parameters.setEncryptionMethod(Zip4jConstants.ENC_METHOD_STANDARD); // 標準の暗号化
//            parameters.setAesKeyStrength(aesKeyStrength);
            parameters.setPassword(password.trim());                            // パスワード
        }

        try(ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();
            ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOut, zipModel);)
        {
            for (File file : fileList) {
                zipOutputStream.putNextEntry(file, parameters);
                try (InputStream inputStream = new FileInputStream(file)) {
                    byte[] readBuff = new byte[4096];
                    int readLen;
                    while ((readLen = inputStream.read(readBuff)) != -1) {
                        zipOutputStream.write(readBuff, 0, readLen);
                    }
                    zipOutputStream.closeEntry();
                }
            }
            zipOutputStream.finish();
            return byteArrayOut.toByteArray();            
        }
    }

    /**
     * ZIPファイルを展開する（全ファイル）
     *
     * @param inputZip ZIPファイル名
     * @param outputFolder 出力先フォルダ
     * @param password パスワード
     * @param charset 文字コード
     * @throws net.lingala.zip4j.exception.ZipException
     */
    public static void unzipAll(String inputZip, String outputFolder, String password, String charset) throws ZipException {
        ZipFile zipFile = new ZipFile(inputZip);
        zipFile.setFileNameCharset(charset); // ファイル名の文字コード(日本語)
        if (zipFile.isEncrypted() && !StringUtils.isEmpty(password)) {
            zipFile.setPassword(password);
        }
        zipFile.extractAll(outputFolder);
    }

    /**
     * ZIPファイルを展開する（全ファイル）
     *
     * @param inputZip ZIPファイル名
     * @param outputFolder 出力先フォルダ
     * @param password パスワード
     * @throws net.lingala.zip4j.exception.ZipException
     */
    public static void unzipAll(String inputZip, String outputFolder, String password) throws ZipException {
        unzipAll(inputZip, outputFolder, password, detectZipCharset(inputZip));
    }

    /**
     * 指定フォルダ内のZIPファイルを展開する。（全ファイル）<br>
     * 解凍できた場合、解凍ファイルの一覧を返す。
     *
     * @param targetPath
     * @return 解凍できたディレクトリ付ファイル名
     */
    public static List<String> unzipAll(String targetPath) {
        File dir = new File(targetPath);
        File[] files = dir.listFiles();
        List<String> ret = new ArrayList<>();
        for (File file : files) {
            if (!file.isDirectory() && FileUtil.getSuffix(file.getName()).equalsIgnoreCase(ZIPSUFFIX)) {   // 子階層は対象外のためフォルダは無視
                String inputZip = file.getPath();
                if (isZipFile(inputZip) && !isEncrypted(inputZip)) {
                    //ファイル名フォルダで解凍
                    try {
                        String charset = detectZipCharset(inputZip);
                        unzipAll(inputZip, Paths.get(file.getParentFile().getPath(), FileUtil.getFNameWithoutSuffix(file.getName())).toFile().getPath(), "", charset);
                        ret.add(file.getPath());
                    } catch (ZipException ex) {

                    }
                }
            }
        }
        return ret;
    }

    /**
     * ZIPファイルから指定拡張子のファイルを展開する
     *
     * @param zipFile
     * @param outputFolder 出力先フォルダ
     * @param password パスワード
     * @param suffixLst　拡張子リスト
     * @return 展開したファイル名リスト
     * @throws net.lingala.zip4j.exception.ZipException
     */
    public static List<String> unzipTargetFiles(ZipFile zipFile, String outputFolder, String password, List<String> suffixLst) throws ZipException {
        List<String> outputFileList = new ArrayList<>();

        if (zipFile.isEncrypted()) {
            //暗号化されている場合はパスワードを指定してパスワード解除
            zipFile.setPassword(password);
        }
        List<FileHeader> lst = zipFile.getFileHeaders();
        for (FileHeader header : lst) {
            String fname = header.getFileName();        //拡張子取得
            String suffix = FileUtil.getSuffix(fname).toLowerCase(); //拡張子を小文字に変換
            //指定拡張子リスト（小文字）に含まれるか
            if (suffixLst.contains(suffix)) {
                //展開
                zipFile.extractFile(header, outputFolder);      //指定ファイル展開
                outputFileList.add(fname);          //展開ファイル名リストに追加
            }
        }
        return outputFileList;
    }

    /**
     * ZIPファイルから指定拡張子のファイルを展開する
     *
     * @param inputZip ZIPファイル名
     * @param outputFolder 出力先フォルダ
     * @param password パスワード
     * @param suffixLst　拡張子リスト
     * @return 展開したファイル名リスト
     * @throws net.lingala.zip4j.exception.ZipException
     */
    public static List<String> unzipTargetFiles(String inputZip, String outputFolder, String password, List<String> suffixLst) throws ZipException {
        //拡張子リストを小文字に
        List<String> suffixLstL = suffixLst.stream().map(suffix -> suffix.toLowerCase()).collect(Collectors.toList());
        ZipFile zipFile = new ZipFile(inputZip);
        zipFile.setFileNameCharset(detectZipCharset(inputZip)); // ファイル名の文字コード(日本語)
        return unzipTargetFiles(zipFile, outputFolder, password, suffixLstL);
    }

    /**
     * ZIPファイルから指定ファイルを展開する
     *
     * @param zipFile ZIPファイル
     * @param outputFolder 出力先フォルダ
     * @param password パスワード
     * @param fileName　展開するファイル名
     * @throws net.lingala.zip4j.exception.ZipException
     */
    public static void unzipFile(ZipFile zipFile, String outputFolder, String password, String fileName) throws ZipException {
        if (zipFile.isEncrypted()) {
            //暗号化されている場合はパスワードを指定してパスワード解除
            zipFile.setPassword(password);
        }
        //展開
        zipFile.extractFile(fileName, outputFolder);      //指定ファイル展開
    }

    /**
     * ZIPファイルから指定ファイルを展開する
     *
     * @param inputZip ZIPファイル名
     * @param outputFolder 出力先フォルダ
     * @param password パスワード
     * @param fileName　展開するファイル名
     * @throws net.lingala.zip4j.exception.ZipException
     */
    public static void unzipFile(String inputZip, String outputFolder, String password, String fileName) throws ZipException {
        ZipFile zipFile = new ZipFile(inputZip);
        zipFile.setFileNameCharset(detectZipCharset(inputZip)); // ファイル名の文字コード(日本語)
        unzipFile(zipFile, outputFolder, password, fileName);
    }

    /**
     * ZIPファイルに含まれるファイルリストを返す
     *
     * @param zipFile ZIPファイル
     * @return ファイル名リスト
     * @throws net.lingala.zip4j.exception.ZipException
     */
    public static List<String> getFileNameList(ZipFile zipFile) throws ZipException {
        List<FileHeader> lst = zipFile.getFileHeaders();        
        List<String> fLst = lst.stream().map(h -> ((FileHeader) h).getFileName()).collect(Collectors.toList());
//TODO ファイル名超過対応
//        List<String> fLst = new ArrayList<>();
//        for(FileHeader h:lst){
//            if(!h.isDirectory()){
//                fLst.add(h.getFileName());
//            }
//        }        
        return fLst;
    }

    /**
     * ZIPファイルに含まれるファイルリストを返す
     *
     * @param inputZip ZIPファイル名
     * @return ファイル名リスト
     * @throws net.lingala.zip4j.exception.ZipException
     */
    public static List<String> getFileNameList(String inputZip) throws ZipException {
        ZipFile zipFile = new ZipFile(inputZip);
        zipFile.setFileNameCharset(detectZipCharset(inputZip)); // ファイル名の文字コード(日本語)
        return getFileNameList(zipFile);
    }

    /**
     * ZIP構成ファイル情報
     */
    @Data
    private static class ZipInfo {
        private String fname;
        private long fsize;
        private File file;
        private String outputFolder;
        private FileHeader fileHeader;
        private long fileId;

        public ZipInfo(FileHeader header) {
            this.fileHeader = header;
            this.fname = header.getFileName();
            this.fsize = header.getUncompressedSize();
        }

        /**
         * ZIP展開先フォルダの設定
         *
         * @param outputFolder ZIP展開先フォルダ
         */
        public void setOutputFolder(String outputFolder) {
            this.outputFolder = outputFolder;
            this.file = new File(Paths.get(outputFolder, this.fname).toFile().getPath());  //展開後ファイルパス
        }
    }

    /**
     * 文字コード判定(csWindows31J or UTF-8)
     * @param inputZip zipファイルパス
     * @return 文字コード
     * @throws ZipException
     */
    public static String detectZipCharset(String inputZip) throws ZipException {
        {
            // csWindows31J判定
            boolean errorFlg = false;
            ZipFile zipFile = new ZipFile(inputZip);
            zipFile.setFileNameCharset(CHARSET_csWindows31J);
            List<FileHeader> fileHeaders = zipFile.getFileHeaders();
            for(FileHeader fileHeader : fileHeaders) {
                String str = fileHeader.getFileName();
                if (str == null) {
                    break;
                }
                try {
                    // csWindows31J判定
                    byte[] byteString = str.getBytes(CHARSET_csWindows31J);
                    String tempString = new String(byteString, CHARSET_csWindows31J);
                    if (!str.equals(tempString)) {
                        errorFlg = true;
                        break;
                    }
                } catch (UnsupportedEncodingException e) {
                } catch (Exception e) {
                }
            }
            if (!errorFlg) {
                return CHARSET_csWindows31J;
            }
        }
        {
            // UTF-8判定
            boolean errorFlg = false;
            ZipFile zipFile = new ZipFile(inputZip);
            zipFile.setFileNameCharset(InternalZipConstants.CHARSET_UTF8);
            List<FileHeader> fileHeaders = zipFile.getFileHeaders();
            for(FileHeader fileHeader : fileHeaders) {
                String str = fileHeader.getFileName();
                if (str == null) {
                    break;
                }
                try {
                    // utf8判定
                    byte[] byteString = str.getBytes(InternalZipConstants.CHARSET_UTF8);
                    String tempString = new String(byteString, InternalZipConstants.CHARSET_UTF8);
                    if (!str.equals(tempString)) {
                        errorFlg = true;
                        break;
                    }
                } catch (UnsupportedEncodingException e) {
                } catch (Exception e) {
                }
            }
            if (!errorFlg) {
                return InternalZipConstants.CHARSET_UTF8;
            }
        }

        return InternalZipConstants.CHARSET_DEFAULT;
    }
    
    //【v2.1.13】
    /**
     * ZIP内ファイルに指定文字数を超えるファイルが含まれないかチェックする
     * @param inputZip ZIPファイル名
     * @param maxLen ファイル名文字数制限値
     * @param charset 文字コード (nullの場合は自動判定する）
     * @return 文字数オーバーファイルの有無（true:あり, false:なし）
     * @throws ZipException 
     */
    public static boolean checkHasFileNameLengthOver(String inputZip, int maxLen, String charset) throws ZipException{
        List<String> retLst = getLengthOverFileNameList(inputZip, maxLen, charset, true);
        //ファイル名文字数オーバーのファイルがひとつでもあったらtrueを返す
        return (retLst != null && retLst.size() > 0);
    }

    //【v2.1.13】
    /**
     * ZIP内ファイルの指定文字数を超えるファイルのファイル名リストを取得
     * @param inputZip ZIPファイル名
     * @param maxLen ファイル名文字数制限値
     * @param charset 文字コード (nullの場合は自動判定する）
     * @return 文字数オーバーファイルの有無（true:あり, false:なし）
     * @throws ZipException 
     */
    public static List<String> getLengthOverFileNameList(String inputZip, int maxLen, String charset) throws ZipException{
        return getLengthOverFileNameList(inputZip, maxLen, charset, false);
    }
    
    //【v2.1.13】
    /**
     * ZIP内ファイルの指定文字数を超えるファイルのファイル名リストを取得
     * @param inputZip ZIPファイル名
     * @param maxLen ファイル名文字数制限値
     * @param charset 文字コード (nullの場合は自動判定する）
     * @return 文字数オーバーファイルの有無（true:あり, false:なし）
     * @throws ZipException 
     */
    private static List<String> getLengthOverFileNameList(String inputZip, int maxLen, String charset, boolean flgCheckOnly) throws ZipException{
        List<String> retLst = new ArrayList<>();
        try {
            //文字コードが指定されていなければ自動判定
            if(StringUtils.isBlank(charset)){
                charset = detectZipCharset(inputZip);    //ファイル名の文字コード(日本語)判定
            }                        
            ZipFile zipFile = new ZipFile(inputZip);
            zipFile.setFileNameCharset(charset); // ファイル名の文字コード(日本語)

            List<FileHeader> lst = zipFile.getFileHeaders();
            if(lst == null)
                return retLst;
            for(FileHeader fh: lst){
                if(fh.isDirectory())
                    continue;   //ディレクトリは対象外
                //フォルダ部を除いたファイル名部のみを文字数チェック対象とする
                File infile = new File(fh.getFileName());
                if(infile.getName().length() > maxLen){
                    //文字数オーバー
                    retLst.add(infile.getPath());
                    if(flgCheckOnly){
                        //有無チェックのみの場合は一つ見つかった時点で抜ける
                        break;
                    }
                }
            }
//            //TODO :::UT:::Start v2.1.13_A004 例外発生
//            if(inputZip.contains("#UT#ZipLengthOverException#"))
//                throw new RuntimeException("#UT#ZIP内ファイル名文字数オーバーファイル取得で例外発生");    //例外発生
//            //TODO :::UT:::End

            return retLst;
        } catch (ZipException e) {
            //このメソッドでの例外発生はスローして呼出元で対応する
            throw e;
        }
    }    
    
    //【v2.1.13】
    /**
     * ZIPファイルを展開する（全ファイル、ファイル名文字数オーバーはリネーム）
     *
     * @param inputZip ZIPファイル名
     * @param outputFolder 出力先フォルダ
     * @param password パスワード
     * @param maxLen ファイル名文字数制限値
     * @param charset 文字コード (nullの場合は自動判定する）
     * @param log　ロガー
     * @return 文字数オーバーファイルのMap<リネーム後File,リネーム前File>
     * @throws net.lingala.zip4j.exception.ZipException
     */
    public static Map<File, File> unzipAllWithRenameByMaxLen(
            String inputZip, String outputFolder, String password, int maxLen, String charset, Logger log) throws ZipException {
        boolean isEncrypted = false;
        try{
            Map<File, File> resMap = new HashMap<>(); //戻り値用Map
            
            //文字コードが指定されていなければ自動判定
            if(StringUtils.isBlank(charset)){
                charset = detectZipCharset(inputZip);    //ファイル名の文字コード(日本語)判定
            }            
            ZipFile zipFile = new ZipFile(inputZip);
            zipFile.setFileNameCharset(charset); // ファイル名の文字コード(日本語)
            isEncrypted = zipFile.isEncrypted();    //パスワード付かどうか
            if ( isEncrypted && !StringUtils.isEmpty(password)) {
                //パスワード解除
                zipFile.setPassword(password);
            }
            //リネーム処理
            //ヘッダーのファイル名の文字数をチェックし、オーバ－している場合はリネームする
            List<FileHeader> lst = zipFile.getFileHeaders();
            if(lst == null)
                return resMap;
            for(FileHeader fh: lst){
                if(fh.isDirectory())
                    continue;   //ディレクトリは対象外
                //フォルダ部を除いたファイル名部のみを文字数チェック対象とする
                File inFile = new File(fh.getFileName());
                File outFile = inFile;
                String fnameOrg = inFile.getName();   //元ファイル名
                if(fnameOrg.length() > maxLen){
                    //文字数オーバー
                    //短縮ファイル名にセットしなおす。
                    //（70文字 + 連番４桁 + 拡張子）
                    //同名ファイル存在チェックし、既存であれば連番+1して存在しないファイル名を生成
                    //※連番9999までいっても既存であれば（あり得ないが）9999のファイル名で上書き
                    for(int count=1; count<10000; count++){
                        //短縮ファイル名取得
                        String fname = FileUtil.getFileNameWithMaxLen(fnameOrg, count, maxLen);
                        outFile = new File(inFile.getParentFile(), fname);  //リネーム後フルパス再構築
                        //同名ファイル存在チェックし
                        if(zipFile.getFileHeader(outFile.getPath()) == null){
                            //同名ファイル無し
                            break;
                        }
                        //同名ファイル有りなのでループ(連番インクリメント）
                    }
                    //zipファイルヘッダーにリネーム後ファイル名をセット
                    fh.setFileName(outFile.getPath());
                    resMap.put(outFile, inFile);    //戻り値用Mapに＜変更後File,変更前File＞を追加
                }
            }
            //zip展開
            zipFile.extractAll(outputFolder);
//            //TODO :::UT:::Start v2.1.13_A001 例外発生
//            if(inputZip.contains("#UT#unzipAllWithRenameByMaxLen Exception#"))
//                throw new ZipException("#UT# ZIP展開処理で例外");    //例外発生
//            //TODO :::UT:::End            
            return resMap;
        }catch(ZipException e){
            //このメソッドでの例外発生はスローして呼出元で対応する
            if(log!=null){
                if(!isEncrypted){
                    //パスワード無しなのに展開できなかった場合はログ出力
                    log.debug("#!Failed to unzipAllWithRenameByMaxLen.  inputZip:" + inputZip + ", msg:" + e.getMessage());
                }
            }
            throw e;            
        }
    } 

    //【v2.1.13】
    /**
     * ZIP内の文字数オーバーファイルのリネーム
     * (展開+リネーム+再圧縮でリネームを実現）
     *
     * @param inputZip ZIPファイル名
     * @param password パスワード
     * @param maxLen ファイル名文字数制限値
     * @param log　ロガー
     * @return リネームしたかどうか
     * @throws net.lingala.zip4j.exception.ZipException
     * @throws java.io.IOException
     * @throws java.lang.CloneNotSupportedException
     */
    public static Map<File,File> renameIncludeFilesByMaxlen(String inputZip, String password, int maxLen, Logger log)
            throws ZipException, IOException, CloneNotSupportedException {
        Map<File,File> resMap = null;
        File tmpFolder = null;
        try {
            if(!isZipFile(inputZip))
                return resMap;
            File zipF = new File(inputZip);
            String charset = detectZipCharset(inputZip);    //ファイル名の文字コード(日本語)判定
            //文字数オーバーファイル有無チェック
            if(!checkHasFileNameLengthOver(inputZip, maxLen, charset)){
                //文字数オーバーファイルがないのでなにもしない
                return resMap;
            }
            
            //展開先一時フォルダ
            //zipファイルがあるフォルダに"TMP"+連番4桁の一時フォルダを生成し展開先とする
            //同名ファイル、フォルダが存在しないかチェックし、存在すれば連番を変えて存在しないものを探す。
            File zipFolder = zipF.getParentFile();
            int seq = 1;
            do{
                tmpFolder = new File(zipFolder,  String.format("TMP%04d", seq));
            }while( ++seq <= 9999 && tmpFolder.exists());
            tmpFolder.mkdirs(); //展開先一時フォルダ生成
            
            //リネーム展開
            resMap = unzipAllWithRenameByMaxLen(inputZip, tmpFolder.getPath(), password, maxLen, charset, log);
            
            //再圧縮
            byte[] zipFileBytes = ZipUtil.createZipInMemory(tmpFolder.getPath(), password, charset);
            FileUtil.saveFile(new ByteArrayInputStream(zipFileBytes), inputZip);
            
            return resMap;            
        } catch (IOException | CloneNotSupportedException | ZipException e) {
            //このメソッドでの例外発生はスローして呼出元で対応する
            if(log!=null){
                log.error("#!Failed to rename includedFiles in zip by maxlen.  inputZip:" + inputZip + ", msg:" + e.getMessage());
            }
            throw e;            
        }finally{
            //展開先一時フォルダを削除する
            try {
                if(tmpFolder != null && tmpFolder.exists()){
                    FileUtil.deleteFolder(tmpFolder.getPath(), log);
                }
            } catch (Exception e) {
                //展開先一時フォルダの削除失敗は無視して継続する
            }
        }        
    } 

    
    /**
     * MAC-ZIPファイルの判定 [v2.2.5]
     * @param inputZip
     * @return 
     * @throws net.lingala.zip4j.exception.ZipException 
     */
    public static boolean isMacZipFile(String inputZip) throws ZipException
    {
        //ZIPファイルか判定
        if(!isZipFile(inputZip)){
            //ZIPファイルではないので対象外としてFalseを返す
            return false;
        }

        //ZipFile型に変換
        ZipFile zipFile = new ZipFile(inputZip);
        if(zipFile.isEncrypted()){
            //パスワード付きの場合は対象外としてFalseを返す
            return false;
        }
        //Zipヘッダーのファイル名リストからMAC-ZIPかどうかを判定する
        List<FileHeader> lst = zipFile.getFileHeaders();
        for(FileHeader fh: lst){
            if(fh != null && fh.getFileName() != null){
                //"__MACOSX"というフォルダ、または"__MACOSX/"から始まるファイル名が存在するかどうかで判定
                if((fh.isDirectory() && fh.getFileName().equals(MACZIP_FOLDERNAME))
                 || fh.getFileName().startsWith(MACZIP_FOLDERNAME + "/")){
                    //"__MACOSX"フォルダが含まれるのでMAC_ZIPと判定しTrueを返す
                    return true;
                }
            }
        }            
        return false;   //対象外
    }

    /**
     * ZIPファイルの展開＋再圧縮 [v2.2.5]
     * @param inputZip      対象となるZIPファイルのパス
     * @param outputZip     再圧縮先ZIPファイルのパス
     * @param extFolder     展開先フォルダ
     * @return 生成されたZIPファイルのパス
     * @throws IOException
     * @throws ZipException
     * @throws CloneNotSupportedException 
     */
    public static String reCreateZip(String inputZip, String outputZip, String extFolder) throws IOException, ZipException, CloneNotSupportedException{
        //--------------------------------
        // 文字コード変換展開 + 再圧縮
        //--------------------------------
        //展開先フォルダ（指定の一時フォルダに指定されたIDでサブフォルダを生成）
        try{
            //展開先フォルダを削除
            FileUtil.deleteFolder(extFolder);

            //文字コード判定
            String charset = detectZipCharset(inputZip);    //ファイル名の文字コード(日本語)判定

            //TempフォルダにZIPを展開
            ZipFile zipFile = new ZipFile(inputZip);
            zipFile.setFileNameCharset(charset);            //文字コードセット
            zipFile.extractAll(extFolder);

            //再圧縮
            byte[] zipMem = ZipUtil.createZipInMemory(extFolder, null);
            try(InputStream is = new ByteArrayInputStream(zipMem)){
                FileUtil.saveFile(is, outputZip);
            }
            return outputZip;
        }finally{
            //展開先フォルダを削除
            try {
                FileUtil.deleteFolder(extFolder);                
            } catch (Exception e) {}
        }
    }
    
    /**
     * 高圧縮ZIPファイル（ZipBomb）の判定 [v2.2.5d]
     * @param filePath              ZIPファイル名（フルパス）
     * @param zipFilesCountLimit    ZIPファイル内ファイル数上限
     * @param zipFilesSizeLimit     ZIPファイル内ファイルサイズ上限
     * @param log                   LOG
     * @return True:高圧縮ZIPファイル、False:それ以外
     */
    public static boolean isZipBomb(String filePath, int zipFilesCountLimit, int zipFilesSizeLimit, Logger log){
        try {
            //引数チェック
            if(zipFilesCountLimit < 0 || zipFilesSizeLimit < 0)
                return false;        
            //拡張子チェック
            if(!FileUtil.getSuffix(filePath).equalsIgnoreCase(FileUtil.ZIPSUFFIX)) 
                return false;
            //ファイル存在チェック
            File file = new File(filePath);
            if(!file.exists() || !file.isFile())
                return false;   //ファイルが存在しない

            //TODO テスト用（本番環境では無視される）
            if(VerifyUtil.chkUTKey(file.getName(), "#UT#", "ZIPBOMBTEST")){  //フィル名に#UT#ZIPBOMBTEST#が含まれている場合にZipBombと判定する
                if(log!=null)
                    log.warn("#! ZipBomb. #TEST#");
                return true;
            }
            
            //ZIPファイルかどうかの判定
            ZipFile zipFile = new ZipFile(file);
            if(!zipFile.isValidZipFile())
                return false;   //ZIPファイルではない

            //Zipヘッダーを取得
            @SuppressWarnings("unchecked")
            List<FileHeader> lst = zipFile.getFileHeaders();
            //ファイル数判定
            if(lst.size() > zipFilesCountLimit){
                //ファイル数上限値を超えるためZipBompと判定
                if(log!=null)
                    log.warn("#! ZipBomb. Files count over. (zipFile:{}, cout:{})", filePath, lst.size());
                return true;
            }
            //解凍後サイズ累計計算
            long sizeTotal=0;
            for(FileHeader fh: lst){
                sizeTotal += fh.getUncompressedSize();
            }
            if(sizeTotal > zipFilesSizeLimit * 1024 * 1024){
                //解凍後ファイルサイズ上限値を超えるためZipBompと判定
                if(log!=null)
                    log.warn("#! ZipBomb. Files size over. (zipFile:{}, size:{})", filePath, sizeTotal);
                return true;
            }
        } catch (Throwable ex) {
            if(log!=null)
                log.error("#! isZipBomb() Exception. (zipFile:{}, errMsg:{})", filePath, ex.toString());
            return false;
        }  
        return false;
    }

    /**
     * 高圧縮ZIPファイル（ZipBomb）の判定 [v2.2.5d]
     * @param filePath              ZIPファイル名（フルパス）
     * @param log                   LOG
     * @param itemHelper            ItemHelper (config設定値取得用）
     * @return True:高圧縮ZIPファイル、False:それ以外
     */
    public static boolean isZipBomb(String filePath, Logger log, ItemHelper itemHelper){
        try {
            //拡張子チェック （無駄にconfig値の取得等をしたくないので、拡張子チェックで".ZIP"以外は何もしない）
            if(!FileUtil.getSuffix(filePath).equalsIgnoreCase(FileUtil.ZIPSUFFIX)) 
                return false;
            //config設定値取得（ZIP内ファイルのファイル数上限値）
            int zipFilesCountLimit = itemHelper.findIntWithDefault(Item.ZIP_FILES_COUNT_LIMIT, Item.FUNC_COMMON, -1);
            //config設定値取得（ZIP内ファイル解凍後ファイルサイズ上限値）
            int zipFilesSizeLimit = itemHelper.findIntWithDefault(Item.ZIP_FILES_SIZE_LIMIT, Item.FUNC_COMMON, -1);
            //高圧縮ZIPファイル（ZipBomb）の判定
            return isZipBomb(filePath, zipFilesCountLimit, zipFilesSizeLimit, log);
        } catch (Throwable ex) {
            if(log!=null)
                log.error("#! isZipBomb Exception. (zipFile:{}, errMsg:{})", filePath, ex.toString());
            return false;
        }
    }

}
