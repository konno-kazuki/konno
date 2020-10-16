package jp.co.fujielectric.fss.servlet;

import java.io.IOException;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.fujielectric.fss.data.CommonEnum.MailAnalyzeResultKbn;
import jp.co.fujielectric.fss.exception.FssException;
import jp.co.fujielectric.fss.logic.MailEntranceLogic;
import jp.co.fujielectric.fss.util.VerifyUtil;
import org.apache.logging.log4j.Logger;

/**
 * メール
 */
@Named
@RequestScoped
@WebServlet("/MailEntranceServlet")
public class MailEntranceServlet extends HttpServlet {

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
            String mailId = req.getParameter("mailId");         // リクエスト情報からメールＩＤを取得
            String mailDate = req.getParameter("mailDate");         // リクエスト情報からメール日付を取得
            LOG.debug("## MailEntranceServlet.doPost Start (mailId:{}  mailDate:{}) ##", mailId, mailDate);

            //実処理をLogicに移動。（トランザクションがうまく効かないため）
            mailEntranceLogic.execMailEntrance(mailId, mailDate);

            //正常終了ステータスをセット
            res.reset();
            res.setStatus(HttpServletResponse.SC_OK);
            res.setIntHeader("Result", 0);  //Resultヘッダに0をセット（成功）
            LOG.debug("### MailEntranceServlet.doPost END ###");
        } catch (Throwable ex) {
            if (ex instanceof FssException && ((FssException) ex).getCode() != MailAnalyzeResultKbn.SUCCESS.value) {
                //解析例外の場合、リトライしても同じ結果となるので、Pollingに戻す前にエラーメール送信処理を行う
                try {
                    String mailId = req.getParameter("mailId");         // リクエスト情報からメールＩＤを取得
                    String mailDate = req.getParameter("mailDate");     // リクエスト情報からメール日付を取得
                    int errCode = ((FssException) ex).getCode();        // FSS固有例外からエラー詳細コードを取得

                    // [v2.2.1]メール解析で異常を検出した場合、削除理由テキストを添付してエラーメールを送付する。
                    mailEntranceLogic.execMailEntranceAnalyzeError(mailId, mailDate, errCode);

                    //正常終了ステータスをセット
                    res.reset();
                    res.setStatus(HttpServletResponse.SC_OK);
                    res.setIntHeader("Result", -1);  //Resultヘッダに-1をセット（解析例外によりエラーメール送信済み）
                    LOG.error("### MailEntranceServlet FssException!! code:" + errCode + " But, doPost is success. END ###");

                    return;
                } catch (Exception e) {
                    LOG.error("  ---MailEntranceServlet execMailEntranceAnalyzeError Exception!! msg:" + e.getMessage(), e);
                    throw new ServletException();
                }
            }
            
            LOG.error("  ---MailEntranceServlet Exception!! msg:" + ex.getMessage(), ex);
            throw new ServletException();
        } finally {
            LOG.debug(VerifyUtil.setLoggingMsg(VerifyUtil.VERIFY_CPU, "END", "mailId:" + req.getParameter("mailId")));
        }
    }

}
