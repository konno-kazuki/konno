package jp.co.fujielectric.fss.logic;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.OptimisticLockException;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.data.CommonBean;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.service.SendInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;

@RequestScoped
public class SampleLogic {
    @Inject
    private CommonBean commonBean;
    @Inject
    private SendInfoService sendInfoService;

    @Transactional
    public void updateSendInfo(String sendInfoId) throws Exception {
        SendInfo sendInfo = sendInfoService.findLocking(sendInfoId);
        sendInfo.setCancelFlg(true);
        sendInfo.setContent("LoginName=" + commonBean.getLoginName() + ": isLgwan=" + CommonUtil.isSectionLgwan());
        sendInfo.setApprovalsDoneCount(sendInfo.getApprovalsDoneCount() + 1);
        try{ Thread.sleep(3000); } catch (Exception ex){ }
        sendInfoService.edit(sendInfo);
    }
}
