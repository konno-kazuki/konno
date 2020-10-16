package jp.co.fujielectric.fss.servlet;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.fujielectric.fss.logic.MailEntranceLogic;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.logging.log4j.Logger;

/**
 * メール
 */
@Named
@RequestScoped
@WebServlet("/MailErrorServlet")
public class MailErrorServlet extends HttpServlet {

    @Inject
    private Logger LOG;

    @Inject
    MailEntranceLogic mailEntranceLogic;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException {
        LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "BEGIN", "mailId:" + req.getParameter("mailId")));
        try {
            //異常終了に対応するため、あらかじめResponceにエラーをセットしておく
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String mailId = req.getParameter("mailId");                             // リクエスト情報からメールＩＤを取得
            String mailDate = req.getParameter("mailDate");         // リクエスト情報からメール日付を取得
            LOG.debug("## MailErrorServlet.doPost Start (mailId:{} mailDate:{}) ##", mailId, mailDate);

            // [2017/03/17]処理中に不測のエラーが発生した場合、最低限の情報を取得してエラーメールを送付する。
            mailEntranceLogic.execMailEntranceError(mailId, mailDate);

            //エラーメール送信時も、原本取得可能な状況であれば正常完了とみなす。
            res.reset();
            res.setStatus(HttpServletResponse.SC_OK);
            LOG.debug("### MailErrorServlet.doPost Error ###");
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("  ---MailErrorServlet Exception!! msg:" + ex.getMessage(), ex);
            throw new ServletException();
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "mailId:" + req.getParameter("mailId")));
        }
    }

}
