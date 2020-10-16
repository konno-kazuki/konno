package jp.co.fujielectric.fss.logic;

import java.util.Date;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.entity.MailLost;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.service.MailLostService;
import jp.co.fujielectric.fss.service.MailLostService.EnmMailLostFunction;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.service.SendInfoService;
import jp.co.fujielectric.fss.util.IdUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

@RequestScoped
public class MailLostLogic {
    @Inject
    private SendInfoService sendInfoService;

    @Inject
    private Logger LOG;
    
    @Inject
    private ReceiveInfoService receiveInfoService;
    
    @Inject
    private MailLostService mailLostService;
    
    @Transactional
    public void addMailLost(EnmMailLostFunction lostFunc, String sendInfoId, String recvInfoId, String upldGrpInfoId) throws Exception {
            LOG.debug("addMailLost (lostFuncKbn:{}, sendInfoId:{}, receiveInfoId:{}, uploadGroupInfoId:{})",
                    lostFunc.name(), sendInfoId, recvInfoId, upldGrpInfoId);
            
            //ロストメール処理区分がMailEntranceの場合は送信単位、それ以外は受信単位の情報を取得する
            String mailQueueId = "";
            ReceiveInfo receiveInfo = null;
            SendInfo sendInfo = null;
            String recvAddress = "";    //宛先
            if(lostFunc == MailLostService.EnmMailLostFunction.MailEntrance){
                //パラメータチェック
                if(StringUtils.isEmpty(sendInfoId)){
                    //SendInfoID(MailID)が指定されていない場合はエラー
                    throw new ServletException("パラメータが不正です。sendInfoIdが指定されていません。 funcKbn:" + lostFunc.name());
                }                
                //SendInfoを取得
                mailQueueId = sendInfoId;
                sendInfo = sendInfoService.find(sendInfoId);
                if(sendInfo == null){
                    LOG.warn("addMailLost SendInfoが見つかりません。 funcKbn:{}, sendInfoId:{}", lostFunc, sendInfoId);
                }else{
                    recvAddress = sendInfo.getReceiveAddresses();  //SendInfoから宛先を取得
                }
            }else{
                mailQueueId = upldGrpInfoId;    //UploadGroupInfoのIDをMailQueueIdとする
                //パラメータチェック
                if(StringUtils.isEmpty(recvInfoId)){
                    if(StringUtils.isEmpty(upldGrpInfoId)){
                        //UploadGroupInfo(MailID)も指定されていない場合はエラー
                        throw new ServletException("パラメータが不正です。recvInfoId,uploadGroupInfoIdが指定されていません。 funcKbn:" + lostFunc.name());
                    }
                    //RecvInfoIDが指定されていない場合、エラー情報登録もできなかったケースとする.
                    upldGrpInfoId = "";     //UploadGroupInfoも登録されていないはず
                }else{
                    //RecvInfoを取得
                    receiveInfo = receiveInfoService.find(recvInfoId);
                    if(receiveInfo == null){
                        throw new ServletException("ReceiveInfoが見つかりません。 recvInfoId:" + recvInfoId);
                    }
                    sendInfoId = receiveInfo.getSendInfoId();   //SendInfoIDを取得
                    //SendInfoを取得
                    sendInfo = sendInfoService.find(sendInfoId);
                    if(sendInfo == null){
                        throw new ServletException("SendInfoが見つかりません。 sendInfoId:" + sendInfoId);
                    }
                    recvAddress = receiveInfo.getReceiveMailAddress();  //ReceiveInfoから宛先を取得
                }
            }

            //MailLostテーブルにレコード追加
            MailLost mailLost = new MailLost();
            mailLost.setId(IdUtil.createUUID());
            mailLost.setFunctionKbn(lostFunc.name());
            if(sendInfo != null){
                mailLost.setSendInfoId(sendInfoId);
                mailLost.setSendMailAddress(sendInfo.getSendMailAddress());
                mailLost.setSendTime(sendInfo.getSendTime());
                mailLost.setSubject(sendInfo.getSubject());
            }
            mailLost.setReceiveInfoId(recvInfoId);
            mailLost.setReceiveAddresses(recvAddress);                
            mailLost.setMailQueueId(mailQueueId);
            mailLost.setUploadGroupInfoId(upldGrpInfoId);
            mailLost.setUpdateDate(new Date());
            mailLostService.edit(mailLost);
    }
}
