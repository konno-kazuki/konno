package jp.co.fujielectric.fss.service.restful;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.data.PasswordUnlockBean;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.logic.DecryptFileLogic;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.logging.log4j.Logger;

/**
 * [RESTful] パスワード解除サービス
 */
@Stateless
@Path("sendtransferpasswordunlock")
public class SendTransferPasswordUnlockREST {

    @Inject
    private Logger LOG;

    @Inject
    private ReceiveInfoService receiveInfoService;

    @Inject 
    private DecryptFileLogic decryptFileLogic;
    
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
