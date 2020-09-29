package jp.co.fujielectric.fss.logic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import jp.co.fujielectric.fss.data.CommonEnum;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.data.Item.DeleteReasonItemKey;
import jp.co.fujielectric.fss.data.Tuple;
import jp.co.fujielectric.fss.data.VotReportInfo;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.entity.UploadFileInfo;
import jp.co.fujielectric.fss.entity.UploadGroupInfo;
import jp.co.fujielectric.fss.exception.FssException;
import jp.co.fujielectric.fss.util.CommonUtil;
import org.apache.commons.lang3.StringUtils;

/**
 * 削除理由ファイル、Votiroレポートファイル関連処理
 */
@RequestScoped
public class DeleteReasonFileLogic {
    /**
     *  削除理由ファイル名（メール添付時用）
     */
    public static final String DELETEREASON_FILENAME = "無害化処理による削除理由.txt";
    
    @Inject
    protected ItemHelper itemHelper;
    
    /**
     * 削除理由ファイル作成
     * @param recvInfo
     * @param vriLst
     * @param uploadFileInfoList
     * @return
     * @throws IOException
     */
    public File createDeleteReasonFileRecv(ReceiveInfo recvInfo, List<VotReportInfo> vriLst, List<UploadFileInfo> uploadFileInfoList) throws IOException {       
        //ファイル名とメッセージのペアのリスト
        List<Tuple<String, String>> msgItemLst = new ArrayList<>();
        
        //------------------------
        //ブロックファイル
        //------------------------
        //エラーメッセージ
        DeleteReasonItemKey itemKey_Blocked;
        if (recvInfo.isAttachmentMailFlg()) {
            itemKey_Blocked = DeleteReasonItemKey.MAIL_BLOCK;   // "mail_block"
        } else {
            itemKey_Blocked = DeleteReasonItemKey.FILE_BLOCK;   //"file_block";
        }
        String blockedErrMsg = itemHelper.findMailMessage(itemKey_Blocked.getItemKey(), Item.FUNC_DELETEREASON).getValue();
        //出力フォーマット（ポリシー定義によるブロック用）
        String txtPolicyErr = itemHelper.findMailMessage("mail_sanitized_policy_err_txt", Item.FUNC_DELETEREASON).getValue();
        //出力フォーマット（ポリシー定義によるブロック以外）
        String txtErr = itemHelper.findMailMessage("mail_sanitized_err_txt", Item.FUNC_DELETEREASON).getValue();
        for (VotReportInfo vri : vriLst) {
            //ファイル名
            String fileName = vri.getFileName();
            if(StringUtils.isEmpty(fileName)){
                //ファイル名が設定されていない（メール本文）の場合
                continue;
            }
            if(!StringUtils.isEmpty(vri.getChildFileName())){
                //アーカイブファイルの場合
                fileName += "/" + vri.getChildFileName();
            }
            String txt = "";            //出力文字列
            String errDetailsMsg = "";  //エラー詳細
            String errFileType = "";    //ファイルタイプ
            switch (vri.getId()) {                
                case 10050100:  //ポリシー定義によるブロック
                    //削除理由定型メッセージ
                    txt = txtPolicyErr; //ポリシー定義用
                    //エラー詳細
                    errDetailsMsg = vri.getDetails();
                    //ファイルタイプ
                    errFileType = vri.getType();
                    break;
                case 10050200:  //ウィルス検知によるブロック
                    //削除理由定型メッセージ
                    txt = txtErr;
                    //エラー詳細
                    errDetailsMsg = "ウィルスが検知されました。";
                    break;
                case 10050500:  //無害化エンジンでのエラー
                    //削除理由定型メッセージ
                    txt = txtErr;
                    //エラー詳細
                    errDetailsMsg = "無害化エンジンでエラーが発生しました。";
                    break;
                default:    //デフォルト
                    break;
            }
            // メール解析時の削除理由を作成
            txt = txt.replace("$file;", fileName);
            txt = txt.replace("$err;", blockedErrMsg);
            txt = txt.replace("$errdetails;", errDetailsMsg);
            txt = txt.replace("$filetype;", errFileType);
            msgItemLst.add(new Tuple<>(fileName,txt));
        }

        //-----------------------------------
        //アップロード、ダウンロードの失敗
        //-----------------------------------
        DeleteReasonItemKey itemKey_Upload;
        DeleteReasonItemKey itemKey_Download;
        if (recvInfo.isAttachmentMailFlg()) {
            itemKey_Upload = DeleteReasonItemKey.MAIL_UPLOAD_ERR;       //"mail_upload_err"
            itemKey_Download = DeleteReasonItemKey.MAIL_DOWNLOAD_ERR;   //"mail_download_err"
        } else {
            itemKey_Upload = DeleteReasonItemKey.FILE_UPLOAD_ERR;       //"file_upload_err"
            itemKey_Download = DeleteReasonItemKey.FILE_DOWNLOAD_ERR;   //"file_download_err"
        }
        String uploadErrMsg = itemHelper.findMailMessage(itemKey_Upload.getItemKey(), Item.FUNC_DELETEREASON).getValue();
        String downloadErrMsg = itemHelper.findMailMessage(itemKey_Download.getItemKey(), Item.FUNC_DELETEREASON).getValue();
        for (UploadFileInfo ufi : uploadFileInfoList) {
            //ファイル名
            String fileName = ufi.getFileNameOrg();
            if(StringUtils.isEmpty(fileName)){
                //ファイル名が設定されていない（メール本文）の場合
                continue;
            }

            String errMsg = "";
            if (ufi.getErrInfo() == CommonEnum.ProcResultKbn.UPLOAD_ERROR.value) {
                //Votiroへのファイルアップロード時の異常
                errMsg = uploadErrMsg;
            }else if (ufi.getErrInfo() == CommonEnum.ProcResultKbn.DOWNLOAD_ERROR.value) {
                //Votiroの監視、ファイルダウンロード時の異常
                errMsg = downloadErrMsg;
            }else{
                //それ以外は対象外
                continue;
            }
            //エラー詳細　（uploadFileInfo.errDetailsにレスポンスコードがセットされている）
            String errDetailsMsg = "無害化エンジンレスポンスコード=" + ufi.getErrDetails();

            // メール解析時の削除理由を作成
            String txt = itemHelper.findMailMessage("mail_sanitized_err_txt", Item.FUNC_DELETEREASON).getValue();
            txt = txt.replace("$file;", fileName);
            txt = txt.replace("$err;", errMsg);
            txt = txt.replace("$errdetails;", errDetailsMsg);
            txt = txt.replace("$filetype;", "");

            msgItemLst.add(new Tuple<>(fileName,txt));
        }

        //---------------------------------------
        // 削除理由テキストファイルの作成
        // ※メールにはファイル名「無害化処理による削除理由.txt」で添付されるが
        // フォルダにはIDを使ったファイル名で保存する
        //---------------------------------------
        File file = null;
        if(msgItemLst.size() > 0){
            //削除理由メッセージ
            StringBuilder msg = new StringBuilder();
            //ファイル名でソートしてから削除理由メッセージを結合（受信履歴詳細画面に合わせてファイル名降順とする）
            Collections.sort(msgItemLst, (Tuple<String, String> obj1, Tuple<String, String> obj2)
                -> -(obj1.getValue1().compareTo(obj2.getValue1())));
            msgItemLst.forEach((item) -> {
                msg.append(item.getValue2());
            });
            // 削除理由ファイルパス取得
            file = getDeleteReasonFileRecv(recvInfo);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            //削除理由ファイル書込み
            try (FileWriter filewrite = new FileWriter(file)) {
                // 削除理由ファイル書き出し
                filewrite.write(msg.toString().replaceAll("\\r\\n|\\r|\\n", "\r\n"));   //改行コードをCRLFに統一[v2.2.4d]
                filewrite.close();
            }            
        }


        return file;
    }

     /**
     * 削除理由ファイル作成
     * @param sendInfo
     * @param errCode
     * @return 
     * @throws IOException
     */
    public File createDeleteReasonFileSend(SendInfo sendInfo, int errCode) throws IOException {

        // エラーメッセージの取得
        String errMsg = itemHelper.findMailMessage("mail_analysis_err", Item.FUNC_DELETEREASON).getValue();
        // エラー詳細メッセージの取得
        String errDetailsMsg = "";
        switch(CommonEnum.MailAnalyzeResultKbn.getMailAnalyzeResultKbn(errCode)) {
            case ORGMAIL_READ_ERROR:
                errDetailsMsg = "メール読込み時異常発生";
                break;
            case HEADER_OTHER_ERROR:
                errDetailsMsg = "メールヘッダ部分の構造異常";
                break;
            case BODY_ERROR:
                errDetailsMsg = "メール本文部分の構造異常";
                break;
            case ATTACHMENT_ERROR:
                errDetailsMsg = "メール添付ファイル部分の構造異常";
                break;
        }

        // メール解析時の削除理由を作成
        String txt = itemHelper.findMailMessage("mail_analysis_err_txt", Item.FUNC_DELETEREASON).getValue();
        txt = txt.replace("$err;", errMsg);
        txt = txt.replace("$errdetails;", errDetailsMsg);        
        
        //---------------------------------------
        // 削除理由テキストファイルの作成
        // ※メールにはファイル名「無害化処理による削除理由.txt」で添付されるが
        // フォルダにはIDを使ったファイル名で保存する
        //---------------------------------------
        File file = getDeleteReasonFileSend(sendInfo);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        try (FileWriter filewrite = new FileWriter(file)) {
            // 削除理由ファイル書き出し
            filewrite.write(txt.replaceAll("\\r\\n|\\r|\\n", "\r\n"));   //改行コードをCRLFに統一[v2.2.4d]            
            filewrite.close();
        }

        return file;
    }   
    
    
    /**
     * 削除理由テキストファイルの保存時のファイルパスを取得する(Recev用)
     * @param recvInfo
     * @return 
     */
    public static File getDeleteReasonFileRecv(ReceiveInfo recvInfo)
    {
        //{ReceiveDir}/{日付}/{ReceiveID}/{ReceiveInfoID}.txt
        String savePath = CommonUtil.getFolderReceive(recvInfo, false, false);   //Receiveフォルダ
        return new File(savePath, recvInfo.getId() + ".txt");
    }

    /**
     * 削除理由テキストファイルの保存時のファイルパスを取得する(Send用)
     * @param sendInfo
     * @return 
     */
    public static File getDeleteReasonFileSend(SendInfo sendInfo)
    {
        //{SendDir}/{日付}/{ReceiveID}/{ReceiveInfoID}.txt
        String savePath = CommonUtil.getFolderSend(sendInfo, false, false);   //Receiveフォルダ
        return new File(savePath, sendInfo.getId() + ".txt");
    }
    
    /**
     * Votiroレポートファイルを取得
     * @param uploadGroupInfo
     * @param uploadFileInfo
     * @return 
     */
    public static File getReportFile(UploadGroupInfo uploadGroupInfo, UploadFileInfo uploadFileInfo)
    {
        //{VotiroReportDir}/{日付}/{ReceiveID}/{UploadFileInfoID}.txt
        String savePath = CommonUtil.getFolderVotiroReport(uploadGroupInfo);        //VotiroReportフォルダ
        return new File(savePath, uploadFileInfo.getSendFileId()+ "_rpt.txt");      //レポートファイル
    }
    
    /**
     * アーカイブファイルのレポート情報を取得する
     * （最初に検出されたBlockedファイルの情報のみ返す）
     * @param filePath
     * @param fileName
     * @return
     * @throws IOException
     * @throws FssException
     */
    public static List<VotReportInfo> getArchiveFileReportInfo(String filePath, String fileName) throws IOException, FssException {
        List<VotReportInfo> vriLst = new ArrayList<>();

        if(StringUtils.isBlank(filePath)){
            throw new FssException("レポートファイルパスがUploadFileInfoにセットされていません。");            
        }
        File file = new File(filePath);
        if(!file.exists()){
            throw new FssException("レポートファイルが存在しません。");
        }
        
        //レポートファイルの情報を展開
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);
        //1階層目からブロックされたファイル情報を取得する
        for (JsonNode child : root.get("Children")) {
            for (JsonNode event : child.get("Events")) {
                VotReportInfo vri = getNodeFromEvent(event);
                if (vri != null) {
                    //親ファイル名
                    vri.setFileName(fileName);
                    //子ファイル名
                    String childFileName = child.get("FileName").textValue();
                    int lastIndex = childFileName.lastIndexOf("_blocked.pdf");
                    if(lastIndex > 0){
                        vri.setChildFileName(childFileName.substring(0, lastIndex));
                    }else{
                        vri.setChildFileName(childFileName);
                    }
                    //子ファイルのファイルタイプ
                    vri.setType(child.get("FileType").get("Type").textValue());
                    //返却用リストに追加
                    vriLst.add(vri);
                    break;
                }
            }            
        }

        return vriLst;
    }

    /**
     * ステータスがブロック又はエラーであったファイルのレポート情報を取得する
     * @param filePath
     * @param fileName
     * @return
     * @throws IOException 
     * @throws FssException 
     */
    public static VotReportInfo getFileReportInfo(String filePath, String fileName) throws IOException, FssException {
        VotReportInfo vri = new VotReportInfo();
        
        if(StringUtils.isBlank(filePath)){
            throw new FssException("レポートファイルパスがUploadFileInfoにセットされていません。");            
        }
        File file = new File(filePath);
        if(!file.exists()){
            throw new FssException("レポートファイルが存在しません。");
        }

        //レポートファイルの情報を展開
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);
        //ブロックされた理由を取得する
        for (JsonNode event : root.get("Events")) {
            vri = getNodeFromEvent(event);
            if (vri != null) {
                //ブロックされたファイル名
                vri.setFileName(fileName);
                //ブロックされたファイルのタイプ
                vri.setType(root.get("FileType").get("Type").textValue());
                break;
            }
        }
        return vri;
    }

    /**
     * イベント要素から情報を取得する
     * @param event
     * @return
     */
    private static VotReportInfo getNodeFromEvent(JsonNode event) {
        VotReportInfo vri = null;
        try {
            int id = event.get("Id").intValue();            
            if (id == 10050100 ||
                id == 10050200 ||
                id == 10050500) {

                //VotReportInfoのインスタンスを生成
                vri = new VotReportInfo();

                //ID（ブロック理由）
                vri.setId(id);
                //ID=10050100の場合、Detailsの[]内を出力文字列として抽出
                if (id == 10050100) {
                    Pattern pattern = Pattern.compile("\\[(.+)\\]");
                    Matcher matcher = pattern.matcher(event.get("Details").textValue());
                    while(matcher.find()) {
                        vri.setDetails(matcher.group(1));
                    }
                }else{
                    vri.setDetails(String.valueOf(id));
                }
            }
            return vri;
        } catch (Exception e) {
            return null;
        }
    }
}
