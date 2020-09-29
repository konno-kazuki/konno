package jp.co.fujielectric.fss.ejb;

import com.ocpsoft.pretty.faces.util.StringUtils;
import java.io.File;
import java.nio.file.Files;
import java.util.Date;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.inject.Inject;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.CommonUtil.FolderKbn;
import jp.co.fujielectric.fss.util.DateUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.logging.log4j.Logger;

@Singleton
public class FileCleaning {

    @Inject
    Logger LOG;

    //setting.propertiesの該当項目名
    private final String[][] propInfoList = {
        {FolderKbn.MAIL.key, "maildir_term"},       //maildir
        {FolderKbn.SEND.key, "senddir_term"},       //senddir
        {FolderKbn.DECRYPT.key, "decryptdir_term"}, //decryptdir
        {FolderKbn.RECEIVE.key, "receivedir_term"}, //receivedir
        {FolderKbn.TEMP.key, "tempdir_term"},       //tempdir
        {FolderKbn.SANDBLAST.key, "sandblastdir_term"},  //sandblastdir [v2.2.1]
        {FolderKbn.VOTIRO.key, "votirodir_term"},   //votirodir [v2.2.1]
        {FolderKbn.VOTIRO_REPORT.key, "votiroReportdir_term"},   //votirodir [v2.2.3]
    };

    private boolean isFileCleanEnabled = false;

    public FileCleaning() {
        try {
            isFileCleanEnabled = "true".equalsIgnoreCase(CommonUtil.getSetting("enable_fileclean"));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
    }

    /**
     * 期限切れフォルダの削除処理
     */
    @Schedule(persistent = false, hour = "1", minute = "0", second = "0", info = "FileCleaning.timerProcess")
    public void timerProcess() {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        // TODO: TimerServiceを利用して停止することが出来るらしいが、Injection方法が不明。簡易的な停止判定にて実装。
        if (!isFileCleanEnabled) {
            return;
        }

        LOG.info("<-- FileCleaning " + new Date().toString());

        try {
            Date sysDate = DateUtil.getSysDate(); //現在日付

            for (String[] propInfo : propInfoList) {
                //setting.propertiesの登録情報取得
                String folder = CommonUtil.getSetting(propInfo[0]); //フォルダ
                String term = CommonUtil.getSetting(propInfo[1]);   //期限

                if(StringUtils.isBlank(folder)){
                    //フォルダが未設定
                    LOG.warn("#! FileCleaning. 未設定:{}", propInfo[0]);
                    continue;
                }else if(StringUtils.isBlank(term)){
                    //期限が未設定
                    LOG.warn("#! FileCleaning. 未設定:{}", propInfo[0]);
                    continue;
                }
                
                //削除対象日付取得
                Date termDate = DateUtil.addDays(sysDate, "-" + term);

                //削除対象日付より古いファイルを削除する
                int cnt = deleteOldFiles(new File(folder), termDate, false);
                if(cnt > 0){
                    LOG.warn("# FileCleaning. ファイルを削除しました。 フォルダ：{},  削除数：{}", folder, cnt);
                }
            }
        } catch (Exception e) {
            LOG.error("#! FileCleaning Error. msg:{}", e.getMessage(), e);
            e.printStackTrace();
        }

        LOG.info("-->");
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
    }
    
    /**
     * 更新日付が指定日付より古いファイルを削除する（ファイルを含まないフォルダも削除する）
     * @param f
     * @param termDate
     * @param flgDelFolder  ファイルを含まないフォルダを削除するか
     * @return 
     */
    private int deleteOldFiles(File f, Date termDate, boolean flgDelFolder){
        int cnt = 0;
        try {
            if (f.isDirectory()) {
                //フォルダの場合
                //フォルダ内のファイル/サブフォルダに対して再帰的に削除処理を行う
                File[] files = f.listFiles();
                if(files != null){
                    for (File subFile : files) {
                        cnt += deleteOldFiles(subFile, termDate, true);
                    }
                }
                if(flgDelFolder){
                    //処理後の当該フォルダ内ファイル数を確認
                    File[] filesAfter = f.listFiles();
                    if(filesAfter == null || filesAfter.length == 0){
                        //フォルダ内にファイルがなくなっていたらフォルダを削除する
                        Files.delete(f.toPath());
                        cnt++;
                        LOG.trace("削除対象フォルダ:{}", f.getPath());
                    }
                }
            } else if(f.exists()) {
                //ファイルの場合、最終更新日付取得
                Date updtDate = new Date(f.lastModified());
                if (updtDate.compareTo(termDate) < 0) {
                    //指定日付より古い場合は削除する。
                    Files.delete(f.toPath());
                    cnt++;
                    LOG.trace("削除対象ファイル:{}",f.getPath());
                }
            }
        } catch (Exception e) {
            LOG.warn("#! FileCleaning ファイル削除エラー [{}]", e.toString());
        }
        return cnt;
    }    
}
