package jp.co.fujielectric.fss.service.restful;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.mail.internet.InternetAddress;
import javax.persistence.EntityManager;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.data.CommonEnum;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.data.PasswordUnlockBean;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.logic.DecryptFileLogic;
import jp.co.fujielectric.fss.logic.ItemHelper;
import jp.co.fujielectric.fss.logic.SanitizeHelper;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.service.SendInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.FileUtil;
import jp.co.fujielectric.fss.util.IdUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.logging.log4j.Logger;

/**
 * [RESTful] パスワード解除サービス
 */
@Stateless
@Path("passwordunlock")
public class PasswordUnlockREST {

    @Inject
    private Logger LOG;

    @Inject
    private ItemHelper itemHelper;

    @Inject
    private ReceiveInfoService receiveInfoService;

    @Inject
    private SendInfoService sendInfoService;

    @Inject 
    private DecryptFileLogic decryptFileLogic;
    
    @Inject
    private EntityManager em;
    
    @Inject
    private SanitizeHelper sanitizeHelper;
    
    @GET
    @Path("init/{envelopeTo}/{sendInfoId}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public Response initReceiveFiles(@PathParam("envelopeTo") String envelopeTo, @PathParam("sendInfoId") String sendInfoId) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "BEGIN"));
        try{
            SendInfo sendInfo = sendInfoService.find(sendInfoId);                   // 送信情報の取得

            // メール本体無害化フラグ取得
            boolean mailSanitizeFlg = false;
            if (sendInfo.isAttachmentMailFlg()) {
                Item item = itemHelper.find(Item.MAIL_SANITIZE_FLG, Item.FUNC_COMMON);  // 機能ＩＤは共通
                mailSanitizeFlg = (item.getValue().equalsIgnoreCase("true"));
            }
            PasswordUnlockBean passwordUnlockBean = new PasswordUnlockBean();       // パスワード解除データ生成

            //ReceiveInfo生成　[v2.2.1]共通化
            InternetAddress receiveAddress = new InternetAddress("","");
            try {
                receiveAddress = InternetAddress.parse(CommonUtil.decodeBase64(envelopeTo))[0];
            } catch (Exception ex) {
            }
            
            // SandBlast対応区分取得
            CommonEnum.SandBlastKbn sandBlstKbn = sanitizeHelper.getSandBlastKbn(sendInfo);        
            
            ReceiveInfo receiveInfo = receiveInfoService.createReceiveInfo(sendInfo, receiveAddress, mailSanitizeFlg, true, sandBlstKbn);
            
//            ReceiveInfo receiveInfo = new ReceiveInfo();                            // 受信情報の作成
//            receiveInfo.setId(IdUtil.createUUID());
//            receiveInfo.setSendInfoId(sendInfo.getId());
//            receiveInfo.setSendInfo(sendInfo);
//            receiveInfo.setReceiveUserId("");                                       // 受信者IDは空白
//            try {
//                InternetAddress receiveAddress = InternetAddress.parse(CommonUtil.decodeBase64(envelopeTo))[0];
//                receiveInfo.setReceiveMailAddress(receiveAddress.getAddress());
//                receiveInfo.setReceiveUserName(receiveAddress.getPersonal());
//            } catch (Exception ex) {
//                receiveInfo.setReceiveMailAddress("");
//                receiveInfo.setReceiveUserName("");
//            }
//            receiveInfo.setOriginalReceiveAddress(sendInfo.getOriginalReceiveAddresses());  // [2017/06/21] 元受信アドレスに複数宛先が入るケースはない
//            receiveInfo.setSendTime(sendInfo.getSendTime());
//            receiveInfo.setHistoryDisp(sendInfo.isHistoryDisp());
//            receiveInfo.setAttachmentMailFlg(sendInfo.isAttachmentMailFlg());               // mailEntranceからはメール添付
//            receiveInfo.setMailSanitizeFlg(mailSanitizeFlg);                                // メール本体無害化フラグ設定
//            receiveInfo.setMailSanitizedFlg(false);
//            receiveInfo.setPasswordUnlockWaitFlg(true);                                     // パスワード解除待ちフラグ設定

            passwordUnlockBean.setReceiveInfo(receiveInfo);

            // 送信ファイルをパスワード解除ファイルにコピー
//            FileUtil.copyFolder(CommonUtil.getSetting("senddir") + receiveInfo.getSendInfoId() + "/",
//                    CommonUtil.getSetting("decryptdir") + receiveInfo.getId() + "/");
            FileUtil.copyFolder(CommonUtil.getFolderSend(sendInfo, false, false),
                    CommonUtil.getFolderDecrypt(receiveInfo, false));

            // 受信ファイル情報の作成
            decryptFileLogic.createReceiveFilesForPassowrd(receiveInfo);

            // receiveInfoをＤＢ登録
            receiveInfoService.create(receiveInfo);

            em.flush(); //DB更新（DB更新エラー検知。コミットではない）
            return Response.ok(passwordUnlockBean).build();            
        }catch(Exception e){
            LOG.error("#! initReceiveFiles Error! errMsg:" + e.getMessage(), e);
            return Response.serverError().build();
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "END"));            
        }
    }

    @GET
    @Path("get/{receiveInfoId}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public Response getReceiveInfo(@PathParam("receiveInfoId") String receiveInfoId) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "BEGIN"));
        try{
            PasswordUnlockBean passwordUnlockBean = new PasswordUnlockBean();       // パスワード解除データ生成
            ReceiveInfo receiveInfo = receiveInfoService.findWithRelationTables(receiveInfoId);
            passwordUnlockBean.setReceiveInfo(receiveInfo);
            return Response.ok(passwordUnlockBean).build();
        }catch(Exception e){
            LOG.error("#! getReceiveInfo Error! errMsg:" + e.getMessage(), e);
            return Response.serverError().build();
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "END"));
        }
    }

    //[256対応]decryptFileLogicに処理実態を移設
    @GET
    @Path("unlock/{receiveInfoId}/{password}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public Response unlockFiles(@PathParam("receiveInfoId") String receiveInfoId, @PathParam("password") String password) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "BEGIN"));
        try{
            PasswordUnlockBean passwordUnlockBean = decryptFileLogic.unlockFiles(receiveInfoId, CommonUtil.decodeBase64(password));
            return Response.ok(passwordUnlockBean).build();
        }catch(Exception e){
            LOG.error("#! unlockFiles Error! errMsg:" + e.getMessage(), e);
            return Response.serverError().build();
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "END"));
        }
    }
    
    //[256対応]decryptFileLogicに処理実態を移設
    @GET
    @Path("sanitize/{receiveInfoId}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public Response execSanitize(@PathParam("receiveInfoId") String receiveInfoId) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "BEGIN"));       
        try{
            PasswordUnlockBean passwordUnlockBean = decryptFileLogic.execSanitize(receiveInfoId);
            return Response.ok(passwordUnlockBean).build();
        }catch(Exception e){
            LOG.error("#! execSanitize Error! errMsg:" + e.getMessage(), e);
            return Response.serverError().build();
        }finally{
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU_PWUNLOCK, "END"));
        }
   }

}
