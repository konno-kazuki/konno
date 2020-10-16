package jp.co.fujielectric.fss.servlet;

import java.util.List;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.fujielectric.fss.entity.UploadFileInfo;
import jp.co.fujielectric.fss.entity.UploadGroupInfo;
import jp.co.fujielectric.fss.logic.VotiroCompleteLogic;
import jp.co.fujielectric.fss.service.UploadFileInfoService;
import jp.co.fujielectric.fss.util.JsonUtil;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.logging.log4j.Logger;

/**
 * VotiroCompleteServlet
 */
@Named
@WebServlet("/VotiroCompleteServlet")
@RequestScoped
public class VotiroCompleteServlet extends HttpServlet {

    @Inject
    private Logger LOG;

    @Inject
    private VotiroCompleteLogic votiroCompleteLogic;

    @Inject
    private UploadFileInfoService uploadFileInfoService;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN"));
        LOG.trace("### VotiroCompleteServlet.doPost START ###");
        try {
            //異常終了に対応するため、あらかじめResponceにエラーをセットしておく
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            //実処理をLogicに移動。（トランザクションがうまく効かないため）
            UploadGroupInfo uploadGroupInfo = JsonUtil.toObject(req.getParameter("uploadGroupInfo"), UploadGroupInfo.class);

            List<UploadFileInfo> fileInfoList = uploadFileInfoService.findUploadFileInfoByGroup(uploadGroupInfo.getId());

            int result = votiroCompleteLogic.execVotiroComplete(uploadGroupInfo, fileInfoList);

            //正常終了ステータスをセット
            res.reset();
            res.setStatus(result);
            switch (result) {
                case HttpServletResponse.SC_OK:
                    LOG.trace("### VotiroCompleteServlet.doPost END ###");
                    break;
                default:
                    LOG.error("### VotiroCompleteServlet.doPost error (RC:" + result + ") ###");
                    throw new ServletException();
            }
        } catch (Exception ex) {
            LOG.error("### VotiroCompleteServlet.doPost error (msg:" + ex.getMessage() + ") ###", ex);
            throw new ServletException();
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END"));
        }
    }
}
