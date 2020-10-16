package jp.co.fujielectric.fss.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import static java.lang.Math.pow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.logic.ItemHelper;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.ZipInputStream;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

/**
 * ファイル関連ユーティリティ
 */
public class FileUtil {
    /**
     * ファイル名文字列数制限値
     */
    public static final int MAX_FILENAME_LEN = 79;
    /**
     * ファイル名文字数オーバー時、固定ファイル名文字数
     */
    public static final int FIX_FILENAME_LEN = 70;
    /**
     * 拡張子最大文字数
     */
    public static final int MAX_SUFFIX_LEN = 40;
    
    //対象ファイルの拡張子リスト
    public static final List<String> SUFFIXLST = Arrays.asList("docx", "docm", "dotx", "dotm", "xlsx", "xlsm", "xltx", "xltm", "pptx", "pptm", "potx", "potm", "ppsx", "ppsm", "pdf");

    //zipの拡張子リスト
    public static final String ZIPSUFFIX = "zip";

    //pdfの拡張子リスト
    public static final String PDFSUFFIX = "pdf";

    /**
     * office系アプリ
     */
    private final static String OFFICEKIND[][] = {
        // Word
        {"Word", "docx"}, {"Word", "docm"}, {"Word", "dotx"}, {"Word", "dotm"}, {"Word", "doc"},
        // Excel
        {"Excel", "xlsx"}, {"Excel", "xlsm"}, {"Excel", "xltx"}, {"Excel", "xltm"}, {"Excel", "xlsb"}, {"Excel", "xlam"}, {"Excel", "xls"},
        // PowerPoint
        {"PowerPoint", "pptx"}, {"PowerPoint", "pptm"}, {"PowerPoint", "potx"}, {"PowerPoint", "potm"}, {"PowerPoint", "ppam"}, {"PowerPoint", "ppsx"}, {"PowerPoint", "ppsm"}, {"PowerPoint", "sldx"}, {"PowerPoint", "sldm"}, {"PowerPoint", "thmx"}, {"PowerPoint", "ppt"},
        // Access
        {"Access", "mdb"}, {"Access", "accdb"},
        // PDF
        {"Pdf", "pdf"},
        // ZIP、その他アーカイブ
        {"Zip", "zip",}, {"Zip", "cab"}, {"Zip", "tar"}, {"Zip", "rar"}, {"Zip", "7z"}, {"Zip", "gz"}
    };

    /**
     * アーカイブファイルの拡張子リスト
     */
    private static final List<String> ARCHIVE_SUFFIXLST = Arrays.asList("zip","cab","tar","rar","7z","gz");
    
    
    /**
     * オフィス系ファイルのアイコン取得<br>
     * 参考@ ICONの取得元"https://www.iconfinder.com/iconsets/FileTypesIcons"
     *
     * @param fileName ファイル名
     * @return アイコン名
     */
    public static String getFileIconKind(String fileName) {
        String suffix = FileUtil.getSuffix(fileName);
        for (String[] val : OFFICEKIND) {
            if (val[1].equalsIgnoreCase(suffix)) {
                return val[0] + ".png";
            }
        }
        return "None.png";      // (透明)
    }

    /**
     * InputStreamから実ファイルを作成します。（上書き）
     *
     * @param is InputStream
     * @param path 作成するパス
     * @throws IOException 作成できない場合のエラー
     */
    public static void saveFile(InputStream is, String path) throws IOException {
        File file = new File(path);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * InputStreamから実ファイルを作成。同名ファイルがある場合にカウンタを付加した名称で別名保存
     *
     * @param is InputStream
     * @param path 作成するパス
     * @param maxLen    ファイル名MAX文字数[v2.1.13 ADD]
     * @return 保存先ファイル
     * @throws IOException 作成できない場合のエラー
     */
    public static Path saveFileWithRename(InputStream is, String path, int maxLen) throws IOException {
        if(maxLen <= 0){
            //maxLenに0以下が指定された場合、Integer.MAX_VALUEとして結果的に文字数制限はチェックしないこととする。
            maxLen = Integer.MAX_VALUE;
        }
        File file = new File(path);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        //ファイル名文字数チェック（v2.1.13）
        if(file.getName().length() > maxLen){
            //文字数制限を超えるので保存不可
            return null;
        }
        
         // 拡張子の手前にカウントを挿入する
        int pos = file.getPath().lastIndexOf(".");
        String filePath1;
        String filePath2;
        if (pos != -1) {
            filePath1 = file.getPath().substring(0, pos);
            filePath2 = file.getPath().substring(pos);
        } else {
            filePath1 = file.getPath();
            filePath2 = "";
        }
        Path filePath = file.toPath();
        int count = 0;
        while (Files.exists(filePath)) {
            count++;
            filePath = Paths.get(filePath1 + String.format(" (%d)", count) + filePath2);
 
            //リネーム後のファイル名でファイル名文字数チェック（v2.1.13）
            if(filePath.getFileName().toString().length() > maxLen){
                //文字数制限を超えるので保存不可
                return null;
            }
        }
        Files.copy(is, filePath);
        return filePath;
    }

    // 【v2.1.13 A004 ファイル名文字数オーバー対応】    
    /**
     * InputStreamから実ファイルを作成。同名ファイルがある場合にカウンタを付加した名称で別名保存
     *
     * @param is InputStream
     * @param path 作成するパス
     * @param maxLen    ファイル名MAX文字数
     * @return 保存先ファイル
     * @throws IOException 作成できない場合のエラー
     */
    public static Path saveFileWithRenameMaxLen(InputStream is, String path, int maxLen) throws IOException {
        try{
//            //TODO :::UT:::Start v2.1.13_A004 例外発生
//            if(path.contains("#UT#saveFileWithRenameMaxLen Exception#"))
//                throw new RuntimeException("#UT# リネーム処理で例外");    //例外発生
//            //TODO :::UT:::End
            
            File file = new File(path);
            File folder = file.getParentFile();
            if (!folder.exists()) {
                //保存先フォルダがなければ作成する。
                file.getParentFile().mkdirs();
            }
            String fnameOrg = file.getName();   //元ファイル名
            String fname = fnameOrg;            //リネーム後ファイル名
            
            //ファイル名チェック＆短縮(80文字以上のファイル名を短縮）
            int count = 0;
            while ((fname.length() > maxLen || file.exists()) && count<9999) {
                //ファイル名がmaxLenを超えているか同名ファイルが存在した場合、連番をインクリメントしてリネーム
                count++;
                fname = getFileNameWithMaxLen(fnameOrg, count, maxLen);
                file = new File(folder, fname); //フルパス再構築
            }
            //ファイル保存
            Files.copy(is, file.toPath());
            return file.toPath();            
        }catch(IOException e){
            //このメソッドでの例外発生はスローして呼出元で対応する
            throw e;
        }
    }    
   
    // 【v2.1.13 A004 ファイル名文字数オーバー対応】
    /**
     * 文字数制限内に短縮した連番付きファイル名を生成する
     *
     * @param fname ファイル名
     * @param seqNo 連番
     * @param maxLen Max文字数 （基本的には79とする）
     */
    public static String getFileNameWithMaxLen(String fname, int seqNo, int maxLen) {
        //ファイル名固定部文字数
        int baseLen = FIX_FILENAME_LEN;
        if(maxLen <= 0){
            //maxLenに0以下が指定された場合は、仕様通り79とする
            maxLen = MAX_FILENAME_LEN;      //定数で設定してあるMax文字数(79)を使用
        }else if(maxLen != MAX_FILENAME_LEN){
            //maxLenに仕様の79文字以外が指定された場合は
            //ファイル名固定部文字数を算出する
            baseLen = maxLen - (MAX_FILENAME_LEN - FIX_FILENAME_LEN);
            if(baseLen < 0){
                throw new IllegalArgumentException("maxLenの指定値が小さすぎます。");
            }
        }        
        if(StringUtils.isBlank(fname)){
            fname ="";
        }
        fname = fname.trim();
        
        // ファイル名と拡張子を分離
        int pos = fname.lastIndexOf(".");
        String name;    //ファイル名(拡張子除く）
        String sufix;   //拡張子
        if (pos != -1) {
            //拡張子がある場合
            name = fname.substring(0, pos);
            sufix = fname.substring(pos);
        } else {
            //拡張子がない場合
            name = fname;
            sufix = "";
        }
        //-------------------------------
        //70文字 + 連番４桁 + 拡張子 に短縮
        //ただし、拡張子の文字数が大きい場合も考慮する
        //・70文字+連番4桁+拡張子 > 79文字の場合			
        //   (79-4-拡張子文字数)文字 + 連番４桁 + 拡張子	
        //・連番4桁+拡張子 > 79文字の場合 （通常ありえないが）			
        //   連番４桁 + 拡張子75桁
        //-------------------------------
        if(4+sufix.length() > maxLen){
            //連番4桁+拡張子 > 79文字の場合
            //連番４桁 + 拡張子75桁
            fname = String.format("%04d%s", seqNo, getSubString(sufix, baseLen+5));
        }else if(baseLen + 4 + sufix.length() > maxLen){
            //70文字+連番4桁+拡張子 > 79文字の場合
            //(79-4-拡張子文字数)文字 + 連番４桁 + 拡張子	
            fname = String.format("%s%04d%s", getSubString(name, maxLen - 4 - sufix.length()), seqNo, sufix);
        }else if(name.length() < baseLen){
            //ファイル名(拡張子除く)<70文字の場合
            //ファイル名(拡張子除く) + 連番４桁 + 拡張子
            fname = String.format("%s%04d%s", name, seqNo, sufix);            
        }else{
            //70文字 + 連番４桁 + 拡張子 に短縮
            fname = String.format("%s%04d%s", getSubString(name, baseLen), seqNo, sufix);            
        }
        return fname;
    }
    
    /**
     * サロゲート文字を跨がないようにSubstringをする
     * ※サロゲート文字は2文字とカウント
     * ※サロゲート文字を跨ぐ場合はlen-1文字の文字列を返す
     * @param srcText   対象文字列
     * @param len       文字数
     * @return 指定文字数に切り詰めた文字列
     */
    public static String getSubString(String srcText, int len)
    {
        if(srcText == null || srcText.length() <= len || len <= 0){
            //引数から文字列短縮できない判断される場合は対象文字列をそのまま返す
            return srcText;
        }
        char[] charArray = srcText.toCharArray();
        //指定Length-1のコードポイントを取得
        int codePoint = Character.codePointAt(charArray, len - 1);
        //コードポイントを表すのに必要な char 値の数を判定（サロゲートペアの場合は2、そうでない場合は1）
        int charCount = Character.charCount(codePoint);
        if(charCount != 1){
            //サロゲートペアを跨ぐので切り詰める文字数を指定文字数-1とする
            len -= 1;
        }
        return srcText.substring(0, len);
    }
    
    /**
     * ファイル名からFileOutputStreamを作成（上書き用）
     * @param path
     * @return
     * @throws FileNotFoundException 
     */
    public static FileOutputStream getFileOutputStream(String path) throws FileNotFoundException
    {
        File file = new File(path);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }        
        return new FileOutputStream(file);
    }
    
//    /**
//     * パスワード有無を判定します。<br>
//     * zip内ファイルのパスワードZIPファイルは、パスワード有無の判定をしません。<br>
//     * isRestorationがtrueの場合、作成されたフォルダを削除します。
//     *
//     * @see FileUtil#checkPw(java.io.File, boolean, int)
//     * @param file
//     * @param isRestoration zipファイルの中身を精査する
//     * @return true:パスワードあり、false:パスワードなし
//     */
//    public static boolean checkPw(File file, boolean isRestoration) {
//        boolean isPw = checkPw(file, isRestoration, 1);
//        if (isRestoration) {
//            //ファイル名から拡張子を除去したフォルダがあれば削除する。
//            File rm = new File(file.getParent(), FileUtil.getFNameWithoutSuffix(file.getName()));
//            if (rm.exists() && rm.isDirectory()) {
//                deleteFiles(rm, true);
//            }
//        }
//        return isPw;
//    }
//
//    /**
//     * パスワード有無を判定します。<br>
//     * 2階層目以降（zip内ファイル）のパスワードなしZIPファイルは、パスワード有無の判定を行いません。
//     *
//     * @param file
//     * @param isRestoration zipファイルの中身を精査する
//     * @param level 階層
//     * @return true:パスワードあり、false:パスワードなし
//     */
//    private static boolean checkPw(File file, boolean isRestoration, int level) {
//        boolean rc = false;
//        
//        //読み込めないファイルの場合（ウィルス等）パスワードなしと判定する
//        if(!file.exists() || !Files.isReadable(file.toPath()))
//            return false;        
//        
//        String suffix = getSuffix(file.getName()).toLowerCase();    //拡張子
//        
//        if (isRestoration && file.isDirectory()) {
//            // フォルダの場合、配下のフォルダもしくは、ファイルに対して確認する
//            for (File f : file.listFiles()) {
//                if (checkPw(f, isRestoration, level)) {
//                    rc = true;
//                }
//            }
//        }else if (suffix.equalsIgnoreCase(ZIPSUFFIX)) {
//            try {
//                //------------------------------------
//                //ZIPファイルの処理
//                //------------------------------------
//                if (ZipUtil.isZipFile(file.getPath())) {
//                    if (ZipUtil.isEncrypted(file.getPath())) {                  //PW付ZIP
//                        rc = true;
//                    } else if (isRestoration) {
//                        if (level >= 2) {
//                            return false;
//                        }
//                        if (hasTargetFileInZip(file.getPath())) {           //対象ファイルを含むか？(SUFFIXLST)
//                            String folder = Paths.get(file.getParent(), FileUtil.getFNameWithoutSuffix(file.getName())).toString();
//                            ZipUtil.unzipAll(file.getPath(), folder, "");
//                            File folderFile = new File(folder);
//                            for (File f : folderFile.listFiles()) {
//                                if (!f.isDirectory()) {
//                                    if (PasswordUnlockUtil.isPwdProtected(f.getPath())) {  //PW付ファイル
//                                        rc = true;
//                                    }
//                                } else if (checkPw(f, true, level++)) {
//                                    rc = true;
//                                }
//                            }
//
//                        }
//                    }
//                }
//            } catch (ZipException ex) {
//                //TODO ファイル名超過対応
//                //throw new RuntimeException("CheckPassword in ZipFile Error. msg:" + ex.getMessage(), ex);
//            }
//        } else if (SUFFIXLST.contains(suffix)) {
//            //------------------------------------
//            //対象拡張子の場合の処理
//            //------------------------------------
//            if (PasswordUnlockUtil.isPwdProtected(file.getPath())) {  //PW付ファイル
//                rc = true;
//            }
//        }
//        return rc;
//    }

    
    //【v2.1.13】
    /**
     * パスワード有無チェック (zipファイルのファイル展開無し）
     * @param file
     * @param isRestoration zipファイルの中身を精査する
     * @param log ロガー
     * @param itemHelper            ItemHelper (config設定値取得用）
     * @return 
     */
    public static boolean checkPw(File file, boolean isRestoration, Logger log, ItemHelper itemHelper) {
        boolean rc = false;
        try {
            //読み込めないファイルの場合（ウィルス等）パスワードなしと判定する
            if(file == null || !file.exists() || !Files.isReadable(file.toPath()))
                return false;        

            String suffix = getSuffix(file.getName()).toLowerCase();    //拡張子

            if (suffix.equalsIgnoreCase(ZIPSUFFIX)) {
                //------------------------------------
                //ZIPファイルの処理
                //------------------------------------
                
                //高圧縮ZIPファイル（ZipBomb）の場合はパスワード無しとして返る [v2.2.5d]
                if(ZipUtil.isZipBomb(file.getPath(), log, itemHelper))
                    return false;
                    
                if (!ZipUtil.isZipFile(file.getPath())) {
                    //正常なzipファイルではないのでパスワード無しと判定する
                    rc = false;
                }else if (ZipUtil.isEncrypted(file.getPath())) {
                    //PW付ZIP
                    rc = true;
                }else if (isRestoration) {
                    //ZIP内ファイルのチェックをする
                    ZipFile zipFile = new ZipFile(file.getPath());

                    //ZIP内ファイルリスト取得
                    List<FileHeader> lst = zipFile.getFileHeaders();

//                    //TODO :::UT:::Start v2.1.13_A004 例外発生
//                    if(file.getName().contains("#UT#checkPw Exception#"))
//                        throw new RuntimeException("#UT# ZIPパスワードチェック処理で例外");    //例外発生
//                    //TODO :::UT:::End                        

                    for(FileHeader fh: lst){
                        if(fh.isDirectory())
                            continue;
                        String fname = fh.getFileName();
                        //対象拡張子かどうかの判定
                        if(!SUFFIXLST.contains(getSuffix(fname).toLowerCase())){
                            //対象外拡張子
                            continue;
                        }
                        
                        //大容量ファイルチェック（PDF,OFFICEファイル） [v2.2.6]
                        long fsize = fh.getUncompressedSize();  //解凍後ファイルサイズ
                        if(checkIsFileSizeOver(fname, fsize, true, null, log, itemHelper)==true){
                            //サイズ上限チェックでサイズオーバーと判定された場合はパスワードチェックせず、パスワード無しの判定とする
                            log.warn("#パスワードチェック ZIP内ファイルサイズオーバー（fname:{}, fsize:{}）", fname, fsize);
                            continue;
                        }
                        
                        //zip内ファイルのInputStreamを取得してパスワードチェックする
                        try(ZipInputStream zis =  zipFile.getInputStream(fh)){
                            if( PasswordUnlockUtil.isPwdProtectedInMem(fname, zis)){
                                rc = true;
                                break;  //パスワードファイルが一つでも見つかれば抜ける
                            }
                        } catch (IOException ex) {
                        }
                    }
                }
            } else if (SUFFIXLST.contains(suffix)) {
                //------------------------------------
                //対象拡張子の場合の処理(zip以外）
                //------------------------------------
                
                //大容量ファイルチェック（PDF,OFFICEファイル） [v2.2.6]
                if(checkIsFileSizeOver(file.getName(), file.length(), false, null, log, itemHelper)==true){
                    //サイズ上限チェックでサイズオーバーと判定された場合はパスワードチェックせず、パスワード無しの判定とする
                    log.warn("#パスワードチェック ファイルサイズオーバー（fname:{}, fsize:{}）", file.getName(), file.length());
                    rc = false;
                }
                //パスワード付ファイルの判定
                else if (PasswordUnlockUtil.isPwdProtected(file.getPath())) {  //PW付ファイル
                    rc = true;
                }
            }
            return rc;            
        } catch (Exception e) {
            if(log != null){
                log.error("#!パスワードチェック処理で例外が発生しました。 File:" + file.getPath() + ", Message:" + e.getMessage(), e);
            }
            //この処理での例外は無視し、パスワードなしとする
            return false;
        }
    }

    /**
     * ファイルサイズ上限チェック [v2.2.6]
     * PDF,OFFICEファイル（Word,Excel,PowerPoint）のサイズ上限をチェック
     * 必要であれば上限オーバーに対する表示用メッセージも返す
     * @param fName         ファイル名
     * @param fSize         ファイルサイズ
     * @param flgInZip      ZIP内ファイルかどうか（ファイル送信画面表示用。それ以外の用途では意味なし）
     * @param errMsg        上限超えた場合のメッセージをセットして返す（ファイル送信画面表示用。メッセージ不要の場合はnullを指定）
     * @param LOG
     * @param itemHelper
     * @return サイズオーバーかどうか（true:サイズオーバー）
     */
    public static boolean checkIsFileSizeOver(String fName, long fSize, boolean flgInZip, StringBuilder errMsg,  Logger LOG, ItemHelper itemHelper){
        try {            
            boolean bRet = false;        
            Item configItem = null;
            Item.ErrMsgItemKey msgItemKey = null;
            Item.ErrMsgItemKey msgItemKeyInZip = null;
            int filesSizeLimit = -1;
            
            //拡張子
            String suffix = FileUtil.getSuffix(fName).toLowerCase();
            
            // 拡張子の対象チェック
            if (!FileUtil.SUFFIXLST.contains(suffix)) {
                return false;                 // 対象外
            }
            
            //拡張子から対象ファイル種類を判別してconfigの対象キーを判定
            if (suffix.equalsIgnoreCase(FileUtil.PDFSUFFIX)) {
                //PDF
                configItem = Item.PDF_FILE_SIZE_LIMIT;
                msgItemKey = Item.ErrMsgItemKey.ERR_FILE_SIZEOVER_PDF;
                msgItemKeyInZip = Item.ErrMsgItemKey.ERR_FILE_INZIP_SIZEOVER_PDF;
            }else{
                //PDF以外
                switch (suffix.substring(0, 2)) {
                    case "xl":      // Excel
                        configItem = Item.EXCEL_FILE_SIZE_LIMIT;
                        msgItemKey = Item.ErrMsgItemKey.ERR_FILE_SIZEOVER_EXCEL;
                        msgItemKeyInZip = Item.ErrMsgItemKey.ERR_FILE_INZIP_SIZEOVER_EXCEL;
                        break;
                    case "do":      // Word
                        configItem = Item.WORD_FILE_SIZE_LIMIT;
                        msgItemKey = Item.ErrMsgItemKey.ERR_FILE_SIZEOVER_WORD;
                        msgItemKeyInZip = Item.ErrMsgItemKey.ERR_FILE_INZIP_SIZEOVER_WORD;
                        break;
                    case "pp":      // PowerPoint
                    case "po":
                        configItem = Item.POWER_POINT_FILE_SIZE_LIMIT;
                        msgItemKey = Item.ErrMsgItemKey.ERR_FILE_SIZEOVER_POWERPOINT;
                        msgItemKeyInZip = Item.ErrMsgItemKey.ERR_FILE_INZIP_SIZEOVER_POWERPOINT;
                        break;
                    default:
                        // それ以外は対象外
                        bRet = false;
                }
            }
            if(configItem != null){
                //チェック対象ファイルの場合（PDF,Word,Excel,PowerPoint)
                //configからサイズ上限を取得
                filesSizeLimit = itemHelper.findIntWithDefault(configItem, Item.FUNC_COMMON, -1);
                if( filesSizeLimit == -1 ){
                    //未設定または-1がセットされていた場合は対象外
                    bRet = false;
                }else{
                    //指定されたファイルサイズがconfigから取得した上限値より大きいか判定            
                    bRet = (fSize > filesSizeLimit * 1024 * 1024);
                    //サイズオーバーの場合、メッセージをセットする
                    if(bRet && errMsg != null){
                        if(flgInZip){
                            //ZIP内ファイル用メッセージ
                            errMsg.append(itemHelper.findDispMessageStr(msgItemKeyInZip, Item.FUNC_COMMON, filesSizeLimit));
                        }else{
                            //ZIP内ファイル以外用のメッセージ
                            errMsg.append(itemHelper.findDispMessageStr(msgItemKey, Item.FUNC_COMMON, filesSizeLimit));
                        }
                    }
                }
            }

            VerifyUtil.outputUtLog(LOG, "#v2.2.6#", false, "ファイルサイズ上限チェック  fName:%s, fSize:%d, sizeList:%d, return:%b", fName, fSize, filesSizeLimit, bRet);   //ログ（UTモードのみ）            
            return bRet;
        } catch (Exception e) {
            LOG.warn("#ファイルサイズ上限チェックエラー! fName:{}, fSize:{}, errMsg:{}", fName, fSize, e.toString());
            return false;
        }
    }
    
    /**
     * ファイルサイズ上限チェック（ZIP内ファイル） [v2.2.6]
     * PDF,OFFICEファイル（Word,Excel,PowerPoint）のサイズ上限をチェック
     * @param zipFileName   ZIPファイル名
     * @param errMsg        上限超えた場合のメッセージをセットして返す（ファイル送信画面表示用。それ以外の用途ではnullを指定）
     * @param LOG
     * @param itemHelper
     * @return サイズオーバーかどうか（true:サイズオーバー）
     */
    public static boolean checkIsFileSizeOverZip(String zipFileName, StringBuilder errMsg, Logger LOG, ItemHelper itemHelper){
        try {
            //ZIPファイルか判定
            if(!ZipUtil.isZipFile(zipFileName)){
                //ZIPファイルではないので対象外としてFalseを返す
                return false;
            }
            //ZipFile型に変換
            ZipFile zipFile = new ZipFile(zipFileName);
            //Zipヘッダーからサイズ判定をする
            List<FileHeader> lst = zipFile.getFileHeaders();
            for(FileHeader fh: lst){
                if(fh != null && fh.getFileName() != null){
                     if(checkIsFileSizeOver(fh.getFileName(), fh.getUncompressedSize(), true, errMsg, LOG, itemHelper))
                         return true;
                }
            }
            return false;
        } catch (Exception e) {
            LOG.warn("#ファイルサイズ上限チェック（ZIP内ファイル） 例外発生.  fileName:{}, errMsg:{}", zipFileName, e.toString());
            return false;
        }
    }
    
    /**
     * ZIPファイルに指定拡張子ファイルが含まれるか返す
     *
     * @return 指定拡張子のファイル有無
     */
    private static boolean hasTargetFileInZip(String zipPath) {
        List<String> fLst;
        try {
            fLst = ZipUtil.getFileNameList(zipPath);                            //ファイル名リスト
            //指定拡張子リストに含まれるかチェックし結果を返す
            return fLst.stream().anyMatch(f -> SUFFIXLST.contains(getSuffix(f).toLowerCase()));
        } catch (ZipException ex) {
            return false;
        }
    }

    /**
     * 指定フォルダ内ファイルのパスワード有無を判定します。<br>
     * zip内ファイルのパスワードZIPファイルは、パスワード有無の判定をしません。<br>
     * 子階層のファイルは対象外です。
     *
     * @param targetPath
     * @param log   ロガー
     * @param itemHelper            ItemHelper (config設定値取得用）
     * @see FileUtil#checkPw(String)
     * @return true:パスワードあり、false:パスワードなし
     */
    public static boolean checkPw(String targetPath, Logger log, ItemHelper itemHelper) {
        File dir = new File(targetPath);
        File[] files = dir.listFiles();
        if(files == null)
            return false;
        for (File file : files) {
            if (!file.isDirectory()) {  // 子階層は対象外のためフォルダは無視
                if (FileUtil.checkPw(file, true, log, itemHelper)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * ファイル名から拡張子を返す
     *
     * @param fileName ファイル名
     * @return ファイルの拡張子
     */
    public static String getSuffix(String fileName) {
        if (fileName == null) {
            return null;
        }
        String suffix = fileName;
        int point = fileName.lastIndexOf(".");
        if (point != -1) {
            suffix = fileName.substring(point + 1);
        }
        if(suffix.length() > MAX_SUFFIX_LEN){
            suffix = getSubString(suffix, FileUtil.MAX_SUFFIX_LEN);
        }
        return suffix;
    }

    /**
     * ファイル名から拡張子抜き名称を返す
     *
     * @param fileName ファイル名
     * @return ファイル拡張子なし名称
     */
    public static String getFNameWithoutSuffix(String fileName) {
        if (fileName == null) {
            return null;
        }
        int point = fileName.lastIndexOf(".");
        if (point != -1) {
            return fileName.substring(0, point);
        }
        return fileName;
    }

    /**
     * フォルダコピー（階層なし）
     *
     * @param from コピー元フォルダ名
     * @param to コピー元フォルダ名
     */
    public static void copyFolder(String from, String to) {
        copyFolder(from, to, null);
    }

    //例外発生でログ出力するためにオーバーロード追加
    /**
     * フォルダコピー（階層なし）
     * @param from
     * @param to
     * @param log   //Logger（nullの場合はログ出力なし）
     * @return true:正常、false：異常
     */
    public static boolean copyFolder(String from, String to, Logger log) {
        boolean bRet = true;
        try{
            File dirFrom = new File(from);
            File dirTo = new File(to);

            deleteFiles(dirTo, false);
            dirTo.mkdirs();

            for (File file : dirFrom.listFiles()) {
                File dstFile = new File(dirTo, file.getName());
                try {
                    java.nio.file.Files.copy(file.toPath(), dstFile.toPath());
                } catch (IOException e) {
                    bRet = false;   //1つでも失敗すれば結果はFalse
                    if(log != null)
                        log.error("#! Failed to Copy File.  from:" + file.getPath() + " to:" + dstFile.getPath(),  e);
                }
            }
            return bRet;
        }catch(Exception e){
            if(log != null)
                log.error("#! Failed to Copy Folder.  from:" + from + " to:" + to,  e);
            return false;
        }
    }    
    
    /**
     * フォルダ削除（再帰）
     *
     * @param dir フォルダ
     */
    public static void deleteFolder(String dir) {
        deleteFiles(new File(dir), true);
    }

    /**
     * フォルダ削除（再帰）
     * 例外発生時はログを出力して例外をスローせず結果(True/False)を返す
     *
     * @param dir フォルダ
     * @param log ログ出力先
     * @return true:正常終了、　false:例外発生
     */
    public static boolean deleteFolder(String dir, Logger log) {
        try{
            deleteFolder(dir);
            return true;
        }catch(Exception e){
            if(log != null){
                log.error("#! Failed to delete folder.  name:" + dir, e);
            }
            return false;
        }
    }
    
    /**
     * ファイル削除（再帰）
     *
     * @param file 削除対象ファイル（フォルダの場合はその階層下ファイル）
     * @param delFolderFlg 指定したものがフォルダの場合にそのフォルダを削除するかどうか
     *
     */
    public static void deleteFiles(File file, boolean delFolderFlg) {
        if(!file.exists())
            return;
        if (file.isDirectory()) {
            //フォルダ
            for (File f : file.listFiles()) {
                deleteFiles(f, true);
            }

            if (delFolderFlg) {
                file.delete();
            }
        } else {
            //ファイル
            file.delete();
        }
    }

    /**
     * 表示用ファイルサイズを返します。<br>
     * マイナス値の場合、"－ "を返します。
     *
     * @param size ファイルサイズ(B)
     * @return 表示用ファイルサイズ
     */
    public static String getSizeText(long size) {

        String sizeText;

        if (size < 0) {
            return "－ ";
        }

        //単位
        int calc_unit = 0;
        double calc_size = size;
        for (int tmp = 0; tmp < 4; tmp++) {
            ///表記基準を1000とする（実際は1024で変換するのだが）
            int tmp_unit = (int) (calc_size / 1000.0);
            if (tmp_unit < 1) {
                break;
            }
            calc_unit++;
            calc_size = calc_size / 1024.0;
        }
        String unit = "";
        switch (calc_unit) {
            case 1:
                unit = "KB";
                break;
            case 2:
                unit = "MB";
                break;
            case 3:
                unit = "GB";
                break;
            case 4:
                unit = "TB";
                break;
            default:
                break;
        }

        //サイズ変換：切上
        if (calc_unit > 0) {
            BigDecimal bd = new BigDecimal(size / pow(1024.0, calc_unit));
            ///小数点以下第1位まで(切上)
            bd = bd.setScale(1, RoundingMode.CEILING);
            sizeText = String.valueOf(bd) + " " + unit;
        } else {
            sizeText = String.format("%1$,3d B", size);
        }
        return sizeText;
    }

    /**
     * 指定フォルダのファイル一覧を取得します。<br>
     * 配下フォルダ全て確認します。
     *
     * @param files
     * @return ファイル一覧
     */
    public static File[] getFileFolder(File file) {
        return getFileFolder(new File[]{file});
    }

    /**
     * ファイル一覧内のファイル一覧を取得します。<br>
     * 配下フォルダ全て確認します。
     *
     * @param files
     * @return ファイル一覧
     */
    public static File[] getFileFolder(File[] files) {
        List<File> retFile = new ArrayList<>();
        File[] filea;
        for (File file : files) {
            if (file.isDirectory()) {
                File[] rets = getFileFolder(file.listFiles());
                for (File ret : rets) {
                    retFile.add(ret);
                }
            } else {
                retFile.add(file);
            }
        }
        filea = retFile.toArray(new File[0]);
        return filea;
    }

    public static List<String> getFolderFilesStrTree(String folderStr) {
        return getFolderFilesStr(folderStr, "", true);
    }

    private static List<String> getFolderFilesStr(String folderStr, String suffixFilter, boolean isDirectory) {
        File folder = new File(folderStr);
        File[] files = folder.listFiles();
        List<String> fileList = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && isDirectory) {
                    List<String> ret = getFolderFilesStr(file.getPath(), suffixFilter, isDirectory);
                    if (!ret.isEmpty()) {
                        fileList.addAll(ret);
                    }
                } else {
                    String suffix = FileUtil.getSuffix(file.getPath());
                    if (!suffixFilter.isEmpty() && suffix.equalsIgnoreCase(suffixFilter)) {
                        fileList.add(file.getPath());
                    } else {
                        fileList.add(file.getPath());
                    }
                }
            }
        }
        return fileList;
    }

    /**
     * CSV出力用文字列を取得
     *
     * @param str
     *
     * @return CSV出力用文字列
     */
    public static String getCsvString(String str) {

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
     * フォルダ移動（再帰）
     * @param srcDir 移動元フォルダ
     * @param dstDir 移動先フォルダ
     * @param log ロガー
     * @return 全ファイルが移動できたかどうか
     */
    public static boolean moveFolder(String srcDir, String dstDir, Logger log) {
        return moveFiles(new File(srcDir), new File(dstDir), log);
    }

    /**
     * ファイル移動（再帰）
     * @param srcFile 移動元ファイル（フォルダの場合はその階層下のサブフォルダ、ファイルも再帰的に移動する）
     * @param dstFile 移動先ファイル
     * @param log ロガー
     * @return 全ファイルが移動できたかどうか
     */
    public static boolean moveFiles(File srcFile, File dstFile, Logger log) {
        boolean bRet = true;    //結果
        if(!srcFile.exists()){
            //移動元ファイルがない
            return false;
        }
        if(dstFile.exists()){
            //移動先(ファイル、フォルダ）を削除しておく
            if(!deleteFolder(dstFile.getPath(), log)){
                //移動先削除で例外発生の場合falseを返す
                return false;
            }
        }
        
        if (srcFile.isDirectory()) {
            //フォルダ
            for (File f : srcFile.listFiles()) {
                if(moveFiles(f, new File(dstFile, f.getName()), log) == false){
                    //ひとつでも移動できなかったファイルがあれば失敗とする
                    bRet = false;                    
                }
            }
        } else {
            //ファイル
            bRet = moveFile(srcFile.getPath(), dstFile.getPath(), log);
        }
        return bRet;
    }

    /**
     * ファイル移動（フォルダは対象外）
     * @param srcfile
     * @param dstfile
     * @return 
     */
    public static boolean moveFile(String srcfile, String dstfile, Logger log){
        boolean flgMkDir = false;
        Path srcPath = Paths.get(srcfile);
        Path dstPath = Paths.get(dstfile);
        try {           
            if(!Files.exists(srcPath)){
                //移動元ファイルがない
                return false;
            }
            if(Files.isDirectory(srcPath)){
                //ディレクトリは対象外
                return false;
            }
            //ウィルスでブロックされている場合はisReadableでの判定ができない
//            if(!Files.isReadable(srcPath)){
//                //読み込めない（ブロックされている）
//                if(log != null)
//                    log.error("#! Failed to move file. (can not read.)  FileName:" + srcfile);
//                return false;
//            }
            //移動先のフォルダを生成
            if(!Files.exists(dstPath.getParent())){
                Files.createDirectories(dstPath.getParent());
                flgMkDir = true;
            }
//            //ファイル移動
//            Files.move(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING);
            //ファイルコピー＆削除でファイル移動を実現（同一ファイルシステム内ファイルの場合、ブロックされていてもmoveできてしまうため）
            Files.copy(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING);  //コピー
            Files.delete(srcPath); //元ファイルを削除            
            return true;
        } catch (Exception ex) {
            if(log != null)
                log.error("#! Failed to move file.  from:" + srcfile + ", to:" + dstfile, ex);
            return false;
        }finally{
            if(flgMkDir){
                //元々出力先フォルダが存在していなかった場合は削除して元に戻す。
                try{
                    Files.deleteIfExists(dstPath.getParent());
                }catch(Exception e){}
            }
        }
    }
    
    /**
     * ファイルの読込み可否チェック
     * フォルダの場合はフォルダ内ファイルおよびサブフォルダを再帰的にチェックする
     * @param chkFile
     * @return 
     */
    public static boolean chkFileReadable(String chkFile) {
        return chkFileReadable(new File(chkFile));
    }
    
    /**
     * ファイルの読込み可否チェック
     * フォルダの場合はフォルダ内ファイルおよびサブフォルダを再帰的にチェックする
     * @param chkFile
     * @return 
     */
    public static boolean chkFileReadable(File chkFile) {
        if(!chkFile.exists())
            return false;
        if(!Files.isReadable(chkFile.toPath()))
            return false;
        if (chkFile.isDirectory()){
            //フォルダ
            for (File f : chkFile.listFiles()) {
                if(!chkFileReadable(f))
                    return false;
            }
        }
        return true;
    }

    /**
     * 相対パス取得
     * @param srcFilePath   ファイル名（フルパス）
     * @param rootDir        相対パスに対するルートディレクトリ
     * @return 
     */
    public static String getRelativePath(String srcFilePath, String rootDir){
        try{
            java.nio.file.Path pathRootDir = Paths.get(rootDir);
            java.nio.file.Path pathSrcFile = Paths.get(srcFilePath);
            return pathSrcFile.subpath(pathRootDir.getNameCount(), pathSrcFile.getNameCount()).toString();            
        }catch(Exception e){
            return srcFilePath;
        }
    }

    /**
     * zipファイルかどうか（拡張子のみでの判定）
     * @param file
     * @return 
     */
    public static boolean isZipFile(File file){
        return isZipFile(file.getName());
    }
    
    /**
     * zipファイルかどうか（拡張子のみでの判定）
     * @param fname
     * @return 
     */
    public static boolean isZipFile(String fname){
        return getSuffix(fname).equalsIgnoreCase(FileUtil.ZIPSUFFIX);
    }

    /**
     * 256UT用 フォルダ内ファイルｎファイルの模擬ウィルス化（256_NFS対応）
     * 指定フォルダ内でファイル名に特定文字列を含む場合にブロック（読み取り不可）とする。または削除する。
     * ファイル名に以下文字列を含むファイルを対象とする。
     * "#UT256#":ブロック
     * "#UT256#PW#":パスワード解除されている場合にブロック
     * "#UT256#D#"：削除
     * "#UT256#PW#D#":パスワード解除されている場合に削除
     * @param rootDir
     * @param subDir 
     * @param log 
     */
    public static void ut256(String rootDir, String subDir, Logger log){
        try{
            File dir = new File(rootDir);
            if(!StringUtils.isEmpty(subDir))
                dir = new File(dir,subDir);
            File[] fLst = getFileFolder(dir);
            if(log != null){
                String methodName = new Throwable().getStackTrace()[1].getMethodName();
                log.debug("##UT256## method:{}  rootDir:{}  subdir:{}", methodName, rootDir, subDir);
            }        
            for(File f: fLst){
                if(f.isDirectory())
                    continue;
                ut256ex(f.getPath(), false, log);
            }            
        }catch(Exception e){
            if(log!=null)
                log.error("##UT256## Error! msg:" + e.getMessage(), e);
        }
    }

    /**
     * 256UT用 ファイルの模擬ウィルス化（256_NFS対応）
     * @param fpath         ファイル名（フルパス）
     * @param flgException  該当ファイルの場合に例外とするか
     * @param log
     * @throws FileSystemException 
     */
    public static void ut256ex(String fpath, boolean flgException, Logger log) throws FileSystemException{
        File f = new File(fpath);
        if(!f.getName().contains("#UT256#")){
            return;
        }

        if(f.getName().contains("#UT256#PW#")){
            //#PW#の場合はパスワード解除済みの場合のみ対象
            if(checkPw(f, false, log, null) || !f.exists())
                return;
        }
        if(flgException){
            if(log!=null)
                log.debug("##UT256## Exception! File:{}", f.getPath());
            throw new FileSystemException("UT255 許可されていない操作です。");
        }
        if(f.getName().contains("#UT256#D#") || f.getName().contains("#UT256#PW#D#")){
            //#D#の場合は削除
            f.delete();
            if(log!=null)
                log.debug("##UT256## DeleteFile:{}  isExists:{}", f.getPath(), Files.exists(f.toPath()));
        }else{
            //それ以外はブロック
            f.setReadable(false);
            if(log!=null)
                log.debug("##UT256## BlockFile:{}  isReadable:{}", f.getPath(), Files.isReadable(Paths.get(f.getPath())));
        }     
    }

    //[v2.2.3]
    /**
     * 指定ファイルがアーカイブファイルか？
     * @param fileName 指定ファイル
     * @return アーカイブファイルの場合、trueを返す
     */
    public static boolean isArchiveFile(String fileName) {
        String suffix = FileUtil.getSuffix(fileName);
        boolean ret = false;
        //アーカイブファイルの判定
        for (String archSuffix : ARCHIVE_SUFFIXLST) {
            if (archSuffix.equalsIgnoreCase(suffix)) {
                ret = true;
                break;
            }
        }

        return ret;
    }

}
