package jp.co.fujielectric.fss.service.restful;

import java.util.ArrayList;
import java.util.List;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.logic.SendTransferLogic;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.logging.log4j.Logger;

/**
 * [RESTful] ファイル送信サービス
 */
@Stateless
@Path("sendtransfer")
public class SendTransferREST {

    @Inject
    private Logger LOG;

    @Inject
    private SendTransferLogic sendTransferLogic;
   
    //[v2.1.14c2
    /**
     * 非同期処理中のSendInfoIDのリスト（不要なリトライを防ぐため）
     * （スレッド、インスタンスを跨いでチェックする必要があるのでstaticとする）
     */
    private static List<String> asyncIdList = new ArrayList<>();
    
    /**
     * ファイル送信実行
     * @param sendInfoId    SendInfoID
     * @param onetimeId     ワンタイムID
     * @param sendRequestId 送信依頼ID
     * @return 
     */
    @GET
    @Path("sendtransfer/{sendInfoId}/{onetimeId}/{sendRequestId}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    @Transactional(Transactional.TxType.NOT_SUPPORTED)    
    public Integer execSendTransfer(
            @PathParam("sendInfoId") String sendInfoId,
            @PathParam("onetimeId") String onetimeId,
            @PathParam("sendRequestId") String sendRequestId) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", 
                "sendInfoId:" + sendInfoId, "onetimeId:" + onetimeId, "sendRequestId:" + sendRequestId, "asyncIdList.count:" + asyncIdList.size()));

        //[v2.1.14c2]
        //非同期処理中Idリストに追加。（リトライ検知）
        if(!updateAsyncId(sendInfoId, true)){
            //既に非同期処理中IDリストに含まれる為、不要なリトライと判断し何もせず抜ける
            LOG.debug("# 非同期処理リトライを検知しました。 sendInfoId:", sendInfoId);
            return -2;
        }
        try{
            onetimeId = CommonUtil.decodeBase64(onetimeId);
            sendRequestId = CommonUtil.decodeBase64(sendRequestId);
            sendTransferLogic.execSendTransfer(sendInfoId, onetimeId);
            return 0;
        }catch(Exception e){
            LOG.error("#!ファイル送信非同期処理で例外発生。 msg:" + e.getMessage(), e);
            //エラー時処理
            sendTransferLogic.setSendTransferExecError(sendInfoId, onetimeId, sendRequestId, false);
            return -1;
        }finally{
            //[v2.1.14c2]
            //非同期処理中Idリストから除去。（リストに残ったままとならないよう、finallyで）
            updateAsyncId(sendInfoId, false);
            
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "sendInfoId:" + sendInfoId));
        }
    }
    
    /**
     * 承認依頼実行
     * @param sendInfoId    SendInfoID
     * @param mailAddressApprovals 承認者メールアドレス
     * @return 
     */
    @GET
    @Path("approvalRequest/{sendInfoId}/{mailAddressApprovals}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    @Transactional(Transactional.TxType.NOT_SUPPORTED)    
    public Integer execApprovalRequest(
            @PathParam("sendInfoId") String sendInfoId,
            @PathParam("mailAddressApprovals") String mailAddressApprovals) {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN",
                "sendInfoId:" + sendInfoId, "mailAddressApprovals:" + mailAddressApprovals, "asyncIdList.count:" + asyncIdList.size()));

        //[v2.1.14c2]
        //非同期処理中Idリストに追加。（リトライ検知）
        if(!updateAsyncId(sendInfoId, true)){
            //既に非同期処理中IDリストに含まれる為、不要なリトライと判断し何もせず抜ける
            LOG.debug("# 非同期処理リトライを検知しました。 sendInfoId:", sendInfoId);
            return -2;
        }
        try{
            mailAddressApprovals = CommonUtil.decodeBase64(mailAddressApprovals);
            sendTransferLogic.execApprovalRequest(sendInfoId, mailAddressApprovals);
            return 0;
        }catch(Exception e){
            //エラー時処理
            LOG.error("#!ファイル送信非同期処理（承認依頼）で例外発生。 msg:" + e.getMessage(), e);
            sendTransferLogic.setSendTransferExecError(sendInfoId, "", "", sendTransferLogic.isFlgApproval());
            return -1;
        }finally{
            //[v2.1.14c2]
            //非同期処理中Idリストから除去。（リストに残ったままとならないよう、finallyで）
            updateAsyncId(sendInfoId, false);

            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "sendInfoId:" + sendInfoId));
        }
    }    

    //[v2.1.14c2]
    /**
     * 非同期処理中Idリストの更新、及びリトライ検知
     * ※staticのListに対する処理なので念の為に排他処理(static synchronized)とする
     * @param asyncId   対象ID
     * @param flgAdd    リストへの追加かどうか（true:追加、false:削除）
     * @return true:OK, false:リトライ検知
     */
    private static synchronized boolean updateAsyncId(String asyncId, boolean flgAdd){
        try {
            if(flgAdd){
                //追加
                if( asyncIdList.contains(asyncId)){
                    //既にリストに存在するため排他する。（リトライ検知）
                    return (false);
                }
                asyncIdList.add(asyncId);
            }else{
                //削除
                asyncIdList.remove(asyncId);        
            }
            return true;            
        } catch (Exception e) {
            //例外発生時は処理を継続
            return true;
        }
    }

    //今後、同様のテストをする可能性を考慮して残しておく
    //[v2.1.14c2]
    //非同期処理スレッド対応テスト用スタブ
    @GET
    @Path("testAsyncS/{sleep}/{counter}/{memo}/{asyncId}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    @Transactional(Transactional.TxType.NOT_SUPPORTED)    
    public String execRestTest(
            @PathParam("sleep") int sleep,
            @PathParam("counter") int counter,
            @PathParam("memo") String memo,
            @PathParam("asyncId") String asyncId
            ) {
        asyncId = CommonUtil.decodeBase64(asyncId);
        memo = CommonUtil.decodeBase64(memo);
        LOG.debug("##[UT]RESTFUL TEST## REST Start. Sleep:{} Memo:{} ExId:{} Counter:{}",
                sleep, memo, asyncId, counter);
        
        if(!updateAsyncId(asyncId, true)){
            LOG.debug("##[UT]RESTFUL TEST## REST Exclusion.  Memo:{} Exid:{} Counter:{}", memo, asyncId, counter);
            return ( memo + "(" + counter + ") Exclusion.");
        }
        LOG.debug("##[UT]RESTFUL TEST## REST ExclusionCheckOK.  Memo:{} Exid:{} Counter:{}", memo, asyncId, counter);
        try{           
            Thread.sleep(sleep);                
            
            LOG.debug("##[UT]RESTFUL TEST## REST End.  Memo:{} Counter:{}", memo, counter);
            return ( memo + "(" + counter + ")");
        }catch(Exception e){
            LOG.error("###[UT]RESTFUL TEST## REST !例外発生。 msg:" + e.getMessage(), e);
            return ("ERR:" + memo + "(" + counter + ")");
        }finally{
            updateAsyncId(asyncId, false);
        }
    }
}
