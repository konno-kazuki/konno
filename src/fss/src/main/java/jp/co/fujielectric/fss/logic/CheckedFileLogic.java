package jp.co.fujielectric.fss.logic;

import java.util.List;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.entity.CheckedFile;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.UploadFileInfo;
import jp.co.fujielectric.fss.service.CheckedFileService;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.logging.log4j.Logger;

/**
 * CheckedFileロジック
 */
@RequestScoped
public class CheckedFileLogic {
    @Inject
    private Logger LOG;

    @Inject
    private ItemHelper itemHelper;

    @Inject
    private CheckedFileService checkedFileService;
    
    //[V2.2.1]引数変更
    /**
     * ふるまい検知情報更新
     * @param receiveInfo
     * @param fileInfoList 
     */
    @Transactional
    public void execUpdateCheckedFile(ReceiveInfo receiveInfo, List<UploadFileInfo> fileInfoList) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "receiveInfoId:" + receiveInfo.getId()));
        try {
            // [2017/04/25] ふるまい検知フラグがOFFの場合は処理しない
            if (!receiveInfo.getSendInfo().isSendFileCheckFlg()) 
                return;

            //ふるまい検知NGの場合にセットするメッセージを取得する
            String msgFileCheckError = itemHelper.findDispMessageStr("dspFileCheckError", Item.FUNC_COMMON, "");

            // [2017/04/11] ふるまい検知ファイル情報を更新
            List<CheckedFile> checkedFiles = checkedFileService.findReceiveInfoId(receiveInfo.getId());
            if( checkedFiles == null || checkedFiles.isEmpty()) {
                return;
            }
            for (CheckedFile checkedFile : checkedFiles) {
                boolean isCheckOK = false;
                String fname = checkedFile.getFileName();
                //UploadFileInfoのリストから該当するレコードを見つけて結果を取得
                for (UploadFileInfo ufi : fileInfoList) {
                    if( fname.equals(ufi.getFileNameOrg())) {
                        isCheckOK = ufi.isCheckedFileFlg();   //ふるまい検知結果を取得
                        break;
                    }
                }
                checkedFile.setCheckedFlg(true);                            // ふるまい検知済に更新
                if (!isCheckOK){
                    //ふるまい検知がOKでない場合に、ふるまい検知NGのメッセージをセットする。
                    checkedFile.setFileMessage(msgFileCheckError);    // メッセージ更新
                }
                checkedFileService.edit(checkedFile);
            }
        } catch (Exception ex) {
            LOG.error("  ---CheckedFileLogic.execUpdateCheckedFile Exception!! msg:" + ex.getMessage(), ex);
            throw new RuntimeException(ex);
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }    

    /**
     * ふるまい検知使用有無のチェック
     * @return True:使用, False:不使用
     */
    public boolean checkUseCheckedFile(){
        boolean sendFileCheckFlg = false;
        try {
            // [2017/04/11] ふるまい検知フラグをconfigから取得し設定
            Item item = itemHelper.find(Item.SENDIFLE_CHECK_FLG, Item.FUNC_COMMON); // 機能ＩＤは共通
            sendFileCheckFlg = (item.getValue().equalsIgnoreCase("true"));
        } catch (Exception e) {
            //"sendFileCheckFlg"の未設定は不使用扱いとする
        }
        return sendFileCheckFlg;
        
//        try {
//            // SandBlast区分によりふるまい検知の有無を判定 [v2.2.1]
//            CommonEnum.SandBlastKbn sandBlstKbn = sanitizeHelper.getSandBlastKbn(false);
//            if(sandBlstKbn == CommonEnum.SandBlastKbn.NONE){
//                //TODO SandBlast不使用の場合はふるまい検知をしない
//                sendFileCheckFlg = false;
//            }
//            return sendFileCheckFlg;            
//        } catch (Exception e) {
//            //SandBlast区分が取得できない場合は例外をスローして呼出元でエラー対応を行う。
//            throw new RuntimeException("SandBlast対応区分取得に失敗しました。");
//        }
    }

}
