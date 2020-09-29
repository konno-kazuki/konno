/*
 * パスワード解除関連ロジック
 * [256対応]SendTransferLogic, PasswordUnlockREST, SendTransferPasswordUnlockRESTの処理を共通化しここに移設
 */
package jp.co.fujielectric.fss.logic;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import jp.co.fujielectric.fss.data.CommonEnum.DecryptKbn;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.data.PasswordUnlockBean;
import jp.co.fujielectric.fss.entity.DecryptFile;
import jp.co.fujielectric.fss.entity.ReceiveFile;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.service.DecryptFileService;
import jp.co.fujielectric.fss.service.ReceiveFileService;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.FileUtil;
import jp.co.fujielectric.fss.util.IdUtil;
import jp.co.fujielectric.fss.util.PasswordUnlockUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import jp.co.fujielectric.fss.util.ZipUtil;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

/**
 * パスワード付ファイル関連処理
 */
@RequestScoped
public class DecryptFileLogic {
    @Inject
    private Logger LOG;
    @Inject
    private ItemHelper itemHelper;
    @Inject
    private DecryptFileService decryptFileService;
    @Inject
    private ReceiveInfoService receiveInfoService;
    @Inject
    private SanitizeHelper sanitizeHelper;
    @Inject
    private EntityManager em;
    @Inject
    private ReceiveFileService receiveFileService;
    
    /**
     * ReceiveFileの出力（パスワード解除有り用）
     * @param receiveInfo 
     */
    public void createReceiveFilesForPassowrd( ReceiveInfo receiveInfo){
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "BEGIN"));       
        //送信用フォルダ(NFS)
        File nfsSendDir = new File(CommonUtil.getFolderSend(receiveInfo.getSendInfo(), false, false));
        //パスワード解除用テンポラリフォルダ(Local)
        File localDecryptDir = new File(CommonUtil.getFolderDecrypt(receiveInfo, true));
        //パスワード解除用フォルダ(NFS)
        File nfsDecryptDir = new File(CommonUtil.getFolderDecrypt(receiveInfo, false));

        boolean passwordReEncryptFlg = getPasswordReEncryptFlg(receiveInfo);    //パスワード再付与フラグ
        try{
            // 送信ファイルをパスワード解除用フォルダにコピー
            FileUtil.copyFolder(nfsSendDir.getPath(), nfsDecryptDir.getPath());
            //ReceiveFileの生成     [v2.2.1]共通化
            List<ReceiveFile> receiveFileLst = receiveFileService.createReceiveFiles(receiveInfo, receiveInfo.getSendInfo().getSendFiles(), false);            
            //DecryptFileの生成
            for(ReceiveFile receiveFile: receiveFileLst){
                //DecryptFile(パスワード解除ファイル情報)をDB登録
                File sFile = new File(nfsDecryptDir, receiveFile.getFileName());                   //decryptDir上のファイル
                createDecryptFiles(receiveFile, sFile, null, passwordReEncryptFlg, localDecryptDir, nfsDecryptDir);   // パスワード解除ファイル情報の作成                
            }
        }finally{
            if(localDecryptDir.compareTo(nfsDecryptDir) != 0){
                //テンポラリフォルダを削除
                FileUtil.deleteFolder(localDecryptDir.getPath(), LOG);
            }
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "END"));        
        }
    }    
    
    /**
     * パスワード解除処理
     * @param receiveInfoId
     * @param password
     * @return 
     */
    public PasswordUnlockBean unlockFiles(String receiveInfoId, String password) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "BEGIN"));

        PasswordUnlockBean passwordUnlockBean = new PasswordUnlockBean();       // パスワード解除データ生成
        ReceiveInfo receiveInfo = receiveInfoService.findWithRelationTables(receiveInfoId);
        passwordUnlockBean.setReceiveInfo(receiveInfo);

        boolean passwordReEncryptFlg = getPasswordReEncryptFlg(receiveInfo);    //パスワード再付与フラグ
        
        //DecryptDir(ローカル）
//        File localDecryptDir = new File(CommonUtil.getSetting("local_decryptdir") + receiveInfo.getId());
        File localDecryptDir = new File(CommonUtil.getFolderDecrypt(receiveInfo, true));
        //DecryptDir(NFS)
//        File nfsDecryptDir = new File(CommonUtil.getSetting("decryptdir") + receiveInfo.getId());
        File nfsDecryptDir = new File(CommonUtil.getFolderDecrypt(receiveInfo, false));
        //一時フォルダを使用しているか
        boolean flgUseTmpDir = (localDecryptDir.compareTo(nfsDecryptDir) != 0);
        
        boolean unlocked = false;
        for (ReceiveFile receiveFile : receiveInfo.getReceiveFiles()) {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "パスワード解除[Start]", "fileName:" + receiveFile.getFileName(), "password:" + password));
            if (FileUtil.isZipFile(receiveFile.getFileName())) { // Zipファイル判定
                // 親Zipの検索
                DecryptFile decryptFileParent = null;
                for (DecryptFile _decryptFile : receiveFile.getDecryptFiles()) {
                    if (StringUtils.isEmpty(_decryptFile.getParentId())) {
                        decryptFileParent = _decryptFile;
                        break;
                    }
                }
                if (decryptFileParent == null) {
                    continue;
                }

                // 親Zipが保護されているなら、パスワード解除＋子ファイル再構築
                if (decryptFileParent.isPasswordFlg() && !decryptFileParent.isDecryptFlg()) {
                    // 再構築時はパスワード解除データを初期化
                    for (DecryptFile decryptFile : receiveFile.getDecryptFiles()) {
                        decryptFileService.remove(decryptFile.getId());
                    }
                    receiveFile.getDecryptFiles().clear();
//                    if (createDecryptFiles(receiveFile, new File(decryptFileParent.getFilePath()), password, receiveInfo.isAttachmentMailFlg())) {
//                        unlocked = true;
//                    }
//                    for (DecryptFile _decryptFile : receiveFile.getDecryptFiles()) {
//                        decryptFileService.create(_decryptFile);
//                    }
                    if(createDecryptFiles(receiveFile, new File(decryptFileParent.getFilePath()),
                            password, passwordReEncryptFlg, localDecryptDir, nfsDecryptDir)){
                        unlocked = true;
                    }
                }
            }
            
            // Zip本体以外
            for (DecryptFile decryptFile : receiveFile.getDecryptFiles()) {
                if (decryptFile.isPasswordFlg() && !decryptFile.isDecryptFlg()
                        && (!FileUtil.isZipFile(receiveFile.getFileName()) || !decryptFile.getParentId().isEmpty())){
                    
                    File tmpOutputPath;
                    if(flgUseTmpDir){
                        //出力先一時ファイル名取得 (localDecryptDir + "/" + 実ファイルのDecrypttDirからの相対パス）
                        tmpOutputPath = new File(localDecryptDir.getPath(), 
                            FileUtil.getRelativePath(decryptFile.getFilePath(), nfsDecryptDir.getPath()));
                    }else{
                        tmpOutputPath = new File(decryptFile.getFilePath());
                    }
                    // パスワード解除
                    if (PasswordUnlockUtil.unlockPassword(decryptFile.getFilePath(), password, tmpOutputPath.getPath())) {
//                        //TODO :::UT:::Start v2.1.12
//                        FileUtil.ut256(tmpOutputPath.getPath(),null,LOG);  //模擬ウィルス隔離
//                        //TODO :::UT:::End                        
                        boolean bret = true;
                        //パスワード解除が成功した場合
                        if(flgUseTmpDir){
                            //一時フォルダを使用している場合
                            //パスワード解除済みファイル（一時ファイル）を元のファイルに移動する                            
                            bret = FileUtil.moveFile(tmpOutputPath.getPath(), decryptFile.getFilePath(), LOG);
                        }
                        if(bret){
                            decryptFile.setFilePassword(password);
                            decryptFile.setDecryptFlg(true);
                            decryptFile.resetDate();  //更新日付の更新
                            decryptFileService.edit(decryptFile);
                            unlocked = true;
                        }else{
                            LOG.error("#! Failed to move decryptFile from localDecryptDir to decryptDir. fileName:" + tmpOutputPath.getName());
                        }
                    }
                }                
            }
        }

        // パスワード解除結果を設定
        passwordUnlockBean.setUnlocked(unlocked);

        if(flgUseTmpDir){
            //テンポラリフォルダを削除
            FileUtil.deleteFolder(localDecryptDir.getPath(), LOG);
        }
        em.flush(); //DB更新（DB更新エラー検知。コミットではない）
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "END"));        
        return passwordUnlockBean;
    }

    /**
     * 無害化開始処理
     * @param receiveInfoId
     * @return 
     */
    public PasswordUnlockBean execSanitize(String receiveInfoId) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "BEGIN"));

        PasswordUnlockBean passwordUnlockBean = new PasswordUnlockBean();       // パスワード解除データ生成
        ReceiveInfo receiveInfo = receiveInfoService.findWithRelationTables(receiveInfoId);
        passwordUnlockBean.setReceiveInfo(receiveInfo);

        // 全フォルダをZip圧縮
        for (ReceiveFile receiveFile : receiveInfo.getReceiveFiles()) {
            if (FileUtil.isZipFile(receiveFile.getFileName())) { // Zipファイル判定
                LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "ZIP圧縮[Start]", "fileName:" + receiveFile.getFileName()));
                // 親Zipの検索
                DecryptFile decryptFileParent = null;
                for (DecryptFile _decryptFile : receiveFile.getDecryptFiles()) {
                    if (StringUtils.isEmpty(_decryptFile.getParentId())) {
                        decryptFileParent = _decryptFile;
                        break;
                    }
                }
                if (decryptFileParent == null) {
                    continue;
                }

                // フォルダをZipファイルに圧縮
                String zipDir = FileUtil.getFNameWithoutSuffix(decryptFileParent.getFilePath());
                File _zipDir = new File(zipDir);
                if (_zipDir.exists()) {
                    // Zip解凍フォルダがある場合はZip圧縮を実行
                    // ※元のZipファイルは削除・上書きされる
                    try {
                        ZipUtil.createZip(zipDir, decryptFileParent.getFilePath());
                    } catch (ZipException ex) {
                        LOG.error("#! Failed to create zipFile.  Directory:" + zipDir + ", ZipFile:" + decryptFileParent.getFilePath(), ex);
                    }
                }

                // Zipフォルダを削除
                // ※再度、パスワード解除を実行する際に必要のため削除しない
//                FileUtil.deleteFolder(zipDir);
                LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "ZIP圧縮[Complete]", "fileName:" + receiveFile.getFileName()));
            }
        }

        //------------------------------------
        // 無害化ステップへ
        //------------------------------------
        // 無害化処理の呼び出し
        sanitizeHelper.startForPassword(receiveInfo);

        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "END"));
        return passwordUnlockBean;
    }
    
    //[256対応]
    //・ファイル送信、パスワード解除(ファイル交換）、パスワード解除（メール）を共通化。
    //・展開先フォルダ引数を追加(ローカル、NFS）
    /**
     * パスワード解除情報の作成
     * @param receiveFile 受信ファイル情報
     * @param file ファイル
     * @param password zip用パスワード（解除しない場合はnullを指定）
     * @param passwordReEncryptFlg パスワード再付与フラグ
     * @param tmpDir    zip展開先一時フォルダ（ローカル）
     * @param nfsDir    zip展開先恒久フォルダ（NFS）
     * @return パスワード解除したかどうか
     */
    private boolean createDecryptFiles(
            ReceiveFile receiveFile, File file, String password, boolean passwordReEncryptFlg,File tmpDir, File nfsDir) {
        boolean unlocked = false;
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "BEGIN"));

        //一時フォルダを使用しているか
        boolean flgUseTmpDir = (tmpDir.compareTo(nfsDir) != 0);        
        
        boolean zipPwChkFlg = (password == null);          //パスワードチェックをするかどうか(パスワード解除する場合はチェックしない）
        boolean defaultPasswordFlg = (password != null);    //パスワードフラグ(パスワード解除する場合はパスワード付）

        // deccryptFileを追加
        DecryptFile decryptFile = createDecryptFile(receiveFile, file, "", passwordReEncryptFlg, zipPwChkFlg, defaultPasswordFlg);
        
        // Zipファイルの場合は中のファイル分も作成
        if ( FileUtil.isZipFile(file) ) {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "ZIP解凍[Start]", "fileName:" + file.getName(), "password:" + password));
            boolean unziped = false;
            String parentId = receiveFile.getId();  //zip子ファイルの親ID（receiveFileId)
            //フォルダ（テンポラリ）
            String unzipDir = Paths.get(tmpDir.getPath(), FileUtil.getFNameWithoutSuffix(file.getName())).toString();                
            //フォルダ（NFS）
            String nfsDecryptDir = Paths.get(nfsDir.getPath(), FileUtil.getFNameWithoutSuffix(file.getName())).toString();
            //フォルダを削除する
            FileUtil.deleteFolder(unzipDir, LOG);
            FileUtil.deleteFolder(nfsDecryptDir, LOG);
            try{
                //テンポラリフォルダに解凍する（既存の場合はカウンタを付加したフォルダ名）
                try {
                    //ZIP展開 (zip内ファイル名のファイル長オーバー対応）【v2.1.13】
                    //ZipUtil.unzipAll(file.getPath(), unzipDir, password);
                    unzipAllWithRename(receiveFile.getReceiveInfo(), file.getPath(), unzipDir, password, null, LOG);
                    
                    unziped = true; //解凍成功
                    if (decryptFile.isPasswordFlg()) {  // PW付の場合、パスワード解除済みに更新
                        unlocked = true;
                        decryptFile.setFilePassword(Optional.ofNullable(password).orElse(""));
                        decryptFile.setDecryptFlg(true);
                    }
//                    //TODO :::UT:::Start v2.1.12
//                    FileUtil.ut256(unzipDir,null,LOG);  //模擬ウィルス隔離
//                    //TODO :::UT:::End                                                
                    if(flgUseTmpDir){
                        //テンポラリフォルダに解凍したファイルをnfsフォルダに移動させる
                        if(!FileUtil.moveFolder(unzipDir, nfsDecryptDir, LOG)){
                            //移動できなかったファイルがあった場合、エラーとする
                            unziped = false;
                            LOG.error("#! Failed to move decryptFile from localDecryptDir to decryptDir.");
                        }
                    }                    
                } catch (Exception ex) {
    //                    // 失敗時にフォルダだけ作成される場合があるので削除
    //                    FileUtil.deleteFolder(unzipDir);
                    if (!decryptFile.isPasswordFlg()) {
                        //パスワード無しZIPが解凍できなかった場合はエラーとする
                        LOG.error("#! Failed to unzipAll. file:"+file.getPath() + " Msg:" + ex.getMessage(), ex);
                    }
                }
                if(unziped){
                    //解凍成功した場合
                    // Zip内のファイル情報を作成（解凍済みファイルから）
                    List<String> filePaths = FileUtil.getFolderFilesStrTree(nfsDecryptDir);
                    for (String filePath : filePaths) {
                        // Zip内のZipはパスワード解除対象外
                        decryptFile = createDecryptFile(receiveFile, Paths.get(filePath).toFile(), 
                                parentId, passwordReEncryptFlg, false, false);
                    }
                }
                if(!unziped){
                    //Zip内のファイル名でファイル情報を作成
                    List<String> zipInFileNameList = ZipUtil.getFileNameList(file.getPath());
                    for (String fileName : zipInFileNameList) {
                        decryptFile = createDecryptFile(receiveFile, null, 
                                parentId, passwordReEncryptFlg, false, false);
                        decryptFile.setFileName(fileName);
//TODO ファイル名長対応用                        decryptFile.setFileName(new File(fileName).getName());
                        decryptFile.setFilePath(Paths.get(nfsDecryptDir, fileName).toString());
                        decryptFile.setFileFormat(FileUtil.getSuffix(fileName));
                    }
                }
            }catch( Exception e){
                LOG.error("#! Failed to create DecryptFiles. errMsg:"+e.getMessage(), e);
                unziped = false;
            }
            if(!unziped){
                //------------------------
                //解凍失敗の場合
                //------------------------                
                unlocked = false;
                //フォルダを削除する
                FileUtil.deleteFolder(unzipDir, LOG);
                FileUtil.deleteFolder(nfsDecryptDir, LOG);
            }
        }
        //DBに登録
        for (DecryptFile dFile : receiveFile.getDecryptFiles()) {
            dFile.resetDate();  //作成日付・更新日付のセット
            decryptFileService.create(dFile);
        }
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "END"));
        return unlocked;
    }
    
    /**
     * ファイル情報からDecryptFileを生成します。
     *
     * @param receiveFile 受信ファイル情報
     * @param file ファイル（zipファイルから直接ファイル名を取得した場合は実ファイルが無いのでnull）
     * @param parentId  親ID（zip内ファイルには親zipファイルのIDを指定する）
     * @param passwordReEncryptFlg パスワード再付与フラグ
     * @param zipPwChkFlg  ZIP内パスワードチェックフラグ
     * @param defaultPasswordFlg パスワードチェックしない場合のパスワードフラグ初期値
     * @return DecryptFile
     */
    private DecryptFile createDecryptFile(
            ReceiveFile receiveFile, File file, String parentId, boolean passwordReEncryptFlg, boolean zipPwChkFlg, boolean defaultPasswordFlg) {
        DecryptFile decryptFile = new DecryptFile();
        String id = IdUtil.createUUID();
        decryptFile.setId(id);
        decryptFile.setReceiveInfoId(receiveFile.getReceiveInfo().getId());
        decryptFile.setReceiveFileId(receiveFile.getId());
        decryptFile.setParentId(parentId);                          // 初期設定なし
        decryptFile.setTargetFlg(true);                             // TODO: 現時点では全ファイルを無害化対象とする
        if (file != null) {     // 実ファイルがある場合の設定
            decryptFile.setFileName(file.getName());
            decryptFile.setFilePath(file.getPath());
            decryptFile.setFileFormat(FileUtil.getSuffix(file.getName()));
            decryptFile.setFileSize(file.length());            
            if (!zipPwChkFlg && FileUtil.isZipFile(file)) {
                // zipPwChkFlg(ZIP内パスワードチェックフラグ）がFalseの場合は、ZIPファイルのパスワードチェックをしない
                decryptFile.setPasswordFlg(defaultPasswordFlg);                  
            }else{
                decryptFile.setPasswordFlg(FileUtil.checkPw(file, false, LOG));
            }            
        } else {                
            //実ファイル指定がない場合の設定
            decryptFile.setFileName("");
            decryptFile.setFilePath("");
            decryptFile.setFileFormat("");
            decryptFile.setFileSize(-1);
            decryptFile.setPasswordFlg(defaultPasswordFlg);
        }
        decryptFile.setFilePassword("");
        decryptFile.setDecryptFlg(false);
        decryptFile.setEncryptFlg(passwordReEncryptFlg && decryptFile.isPasswordFlg());     // パスワード再付与時でかつパスワード付きの場合trueを設定
        decryptFile.setEncryptedFlg(false);                                     // パスワード再付与済みフラグはfalseで初期化
        receiveFile.getDecryptFiles().add(decryptFile);
        return decryptFile;
    }
    
    /**
     * パスワード再付与フラグ取得
     * @param recvInfo
     * @return 
     */
    private boolean getPasswordReEncryptFlg(ReceiveInfo recvInfo){
        // パスワード再付与フラグ取得
        Item item;
        if(recvInfo.isAttachmentMailFlg()){
            //メール無害化の場合 (INNER,OUTERの区別あり）
            item = itemHelper.find(Item.PASSWORD_RE_ENCRYPT_FLG_INNER, Item.FUNC_COMMON, recvInfo);      //[248対応（簡易版）]            
        }else{
            //ファイル交換の場合 (INNER,OUTERの区別なし）
            item = itemHelper.find(Item.PASSWORD_RE_ENCRYPT_FLG_INNER, "sendTransfer", "");
        }
        return item.getValue().equalsIgnoreCase("true");
    }
    
    //【v2.1.13】
    /**
     * ZIPファイルのファイル名文字数チェック＆リネーム展開呼出
     * @param receiveInfo   受信情報
     * @param zipPath       zipファイル名（フルパス）
     * @param unzipDir      展開先フォルダ
     * @param password      パスワード
     * @param charset       文字コード（nullの場合は自動判別）
     * @param log           ロガー
     * @return              リネーム有無
     * @throws ZipException 
     */
    public static boolean unzipAllWithRename(ReceiveInfo receiveInfo, String zipPath, String unzipDir, String password, String charset, Logger log ) throws ZipException
    {
        try {
            boolean hasRename = false;  //リネーム有無（戻り値用）
            //文字数チェック＆リネーム処理用のzip解凍
            Map<File,File> resMap = ZipUtil.unzipAllWithRenameByMaxLen(
                    zipPath, unzipDir, password, FileUtil.MAX_FILENAME_LEN, charset, log);
            if(resMap != null && resMap.size() > 0){
                hasRename = true;
                //リネームがあった場合はログ出力
                for (Map.Entry<File,File> entry : resMap.entrySet()) {
                    //ログ出力
                    //"##### FileName  change.　ZIPファイル内のファイル名が長すぎるためリネームしました。
                    // （MailID:{メールID(SendInfoID)}, SendMailAddress:{送信メールアドレス}, ReceiveMailAddress:{受信メールアドレス},
                    //  ZipFileName:{zipファイル名}, BeforeFileName:{リネーム前ファイル名}, AfterFileName:{リネーム後ファイル名}）"
                    log.warn("##### FileName  change.　ZIPファイル内のファイル名が長すぎるためリネームしました。"
                            +"（MailID:{}, SendMailAddress:{}, ReceiveMailAddress:{}, ZipFileName:{}, BeforeFileName:{}, AfterFileName:{}）",
                            receiveInfo.getSendInfoId(),    //メールID
                            receiveInfo.getSendInfo().getSendMailAddress(),    //送信メールアドレス
                            receiveInfo.getReceiveMailAddress(),    //受信メールアドレス
                            zipPath,      //zipファイル名
                            entry.getValue().getPath(),     //リネーム前ファイル名
                            entry.getKey().getPath()        //リネーム後ファイル名
                    );
                }
            }
            return hasRename;            
        } catch (ZipException e) {
            //このメソッドでの例外発生はスローして呼出元で対応する
            throw e;            
        }
    }
    
    /**
     * ReceiveFileのパスワード解除区分（decryptKbn）の取得
     * @param receiveFile
     * @return パスワード解除区分
     */
    public static DecryptKbn getDecryptKbn(ReceiveFile receiveFile){
        boolean hasPasswordFile = false;    //パスワードファイル有無
        boolean hasEncryptFile = false;     //パスワード未解除有無
        boolean hasPwIncludeZip = false;    //パスワードファイルを含むzipか        
        
        for(DecryptFile df: receiveFile.getDecryptFiles()){
            if(df.isPasswordFlg()){
                //パスワード付きファイル
                hasPasswordFile = true; //パスワードファイル有り
                if(!df.isDecryptFlg()){
                    //パスワード未解除あり
                    hasEncryptFile = true;
                }
                if(!StringUtils.isEmpty(df.getParentId())){
                    //パスワードつきのZIP内ファイルが存在する
                    hasPwIncludeZip = true;
                }
            }
        }
        DecryptKbn kbn = DecryptKbn.NONE;
        if(hasPasswordFile){
            if(hasPwIncludeZip){
                //パスワードつきのZIP内ファイル有りの場合
                if(hasEncryptFile){
                    //パスワード付きファイル入りZIPファイル　パスワード未解除あり
                    kbn = DecryptKbn.ENCRYPTZIP;
                }else{
                    //パスワード付きファイル入りZIPファイル　全ファイル解除済み
                    kbn = DecryptKbn.DECRYPTEDZIP;
                }
            }else{
                if(hasEncryptFile){
                    //パスワード付きファイル　パスワード未解除
                    kbn = DecryptKbn.ENCTYPT;
                }else{
                    //パスワード付きファイル　パスワード解除済み
                    kbn = DecryptKbn.DECRYPTED;
                }                
            }
        }
        return kbn;
    }
}
