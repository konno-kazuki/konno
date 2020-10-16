package jp.co.fujielectric.fss.servlet;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.fujielectric.fss.logic.ItemHelper;
import jp.co.fujielectric.fss.logic.MailLostLogic;
import jp.co.fujielectric.fss.service.MailLostService.EnmMailLostFunction;
import org.apache.logging.log4j.Logger;

/**
 * MailLostServlet
 */
@Named
@WebServlet("/MailLostServlet")
@RequestScoped
public class MailLostServlet extends HttpServlet {
    public static final String PARAM_FUNC_KBN = "funcKbn";
    public static final String PARAM_SENDINFO_ID = "sendInfoId";
    public static final String PARAM_RECVINFO_ID = "recvInfoId";
    public static final String PARAM_UPLDGRPINFO_ID = "upldGrpInfoId";


    @Inject
    private Logger LOG;

    @Inject
    protected ItemHelper itemHelper;

    @Inject
    private MailLostLogic mailLostLogic;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException {
        LOG.trace("### MailLostServlet.doPost START ###");
        try {
            //異常終了に対応するため、あらかじめResponceにエラーをセットしておく
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            //リクエストから対象ID、ロストメール処理区分を取得する
            EnmMailLostFunction lostFunc = EnmMailLostFunction.valueOf(req.getParameter(PARAM_FUNC_KBN)); // リクエスト情報から処理区分を取得
            String sendInfoId = req.getParameter(PARAM_SENDINFO_ID);         // リクエスト情報からsendInfoIDを取得
            String recvInfoId = req.getParameter(PARAM_RECVINFO_ID);         // リクエスト情報からreceiveInfoIDを取得
            String upldGrpInfoId = req.getParameter(PARAM_UPLDGRPINFO_ID);   // リクエスト情報からuploadGroupInfoIDを取得
            LOG.debug("MailLostServlet lostFuncKbn:{}, mailQueueId:{}, receiveInfoId:{}, uploadGroupInfoId:{}",
                    lostFunc.name(), sendInfoId, recvInfoId, upldGrpInfoId);

            //MailLost追加
            mailLostLogic.addMailLost(lostFunc, sendInfoId, recvInfoId, upldGrpInfoId);

            //正常終了ステータスをセット
            res.reset();
            res.setStatus(HttpServletResponse.SC_OK);
            LOG.trace("### MailLostServlet.doPost END ###");
        } catch (Exception ex) {
            LOG.error("### MailLostServlet.doPost error (msg:" + ex.getMessage() + ") ###", ex);
            throw new ServletException();
        }
    }
}
