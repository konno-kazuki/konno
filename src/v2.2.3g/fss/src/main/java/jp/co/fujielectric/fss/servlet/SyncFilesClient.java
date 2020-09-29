package jp.co.fujielectric.fss.servlet;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import lombok.Getter;
import org.apache.logging.log4j.Logger;

@ClientEndpoint
public class SyncFilesClient {
    @Inject
    private Logger LOG;

    @Getter
    private static final Map<Session, Boolean> sessions = new HashMap<>();

    @Getter
    private static final Object lock = new Object();

    public static final String SESSION_PROP_CLOSEREASON = "closeReason";
    
    @OnMessage
    public void OnMessage(Session session, String message) {
        try {
            LOG.debug("SyncFilesClient:OnMessage (session.id:{}, message:{})"
                    , session.getId(), message);
            if (message.equals(SyncFilesServer.SESSION_MESSAGE_COMPLETE)) {                         // 正常完了状態に更新
                synchronized (lock) { sessions.put(session, true); }                // 排他制御
            }
        } catch (Throwable e) {
            LOG.error("#! SyncFilesClient. OnMessage. Exception:{}", e.toString());
            throw e;
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        try {        
            LOG.debug("SyncFilesClient:onOpen (session.id:{})", session.getId());
            synchronized (lock) {
                session.getUserProperties().put(SESSION_PROP_CLOSEREASON, -1);
                sessions.put(session, false); 
            }                   // 排他制御
        } catch (Throwable e) {
            LOG.error("#! SyncFilesClient. onOpen. Exception:{}", e.toString());
            throw e;
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        LOG.warn("SyncFilesClient:onError (session.id:{}, throwable:{})"
                , session.getId(), throwable.toString());            
    }
   
    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        try {
            if(closeReason.getCloseCode() != CloseReason.CloseCodes.NORMAL_CLOSURE){
                //正常Close以外の場合
                LOG.warn("SyncFilesClient:onClose (session.id:{}, closeReason:{}, closeReason.Code:{})",
                        session.getId(), closeReason.toString(), closeReason.getCloseCode().toString());
            }else{
                //正常Close
                LOG.debug("SyncFilesClient:onClose (session.id:{}, closeReason:{}, closeReason.Code:{})",
                        session.getId(), closeReason.toString(), closeReason.getCloseCode().toString());
            }
            synchronized (lock) {
                //closeReason.CloseCodeをSessionのUserPropertiesにセットする(タイムアウトかどうかの判定のため)
                session.getUserProperties().put(SESSION_PROP_CLOSEREASON, closeReason.getCloseCode().getCode());                
            }
    //        synchronized (lock) { sessions.remove(session); }                       // 排他制御            
        } catch (Throwable e) {
            LOG.error("#! SyncFilesClient. onClose. Exception:{}", e.toString());
            throw e;
        }
    }
}
