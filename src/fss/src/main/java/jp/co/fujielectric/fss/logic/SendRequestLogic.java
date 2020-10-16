/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.logic;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.entity.SendRequestInfo;
import jp.co.fujielectric.fss.service.SendRequestInfoService;
import org.apache.logging.log4j.Logger;

/**
 * 送信依頼処理用ロジック
 */
@RequestScoped
public class SendRequestLogic {

    @Inject
    protected Logger LOG;

    @Inject
    private SendRequestInfoService service;

    @Inject
    private MailManager mailManager;

    @Transactional
    public void execSendRequest(SendRequestInfo sendRequestInfo) {
        try {
            // 送信依頼情報を登録
            service.create(sendRequestInfo);

            // メール送信
            mailManager.sendMailRequest(sendRequestInfo);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("送信依頼処理失敗", e);
        }
    }
}
