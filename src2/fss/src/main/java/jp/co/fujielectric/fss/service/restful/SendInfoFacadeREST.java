package jp.co.fujielectric.fss.service.restful;

import java.util.List;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.service.ReceiveInfoService;
import jp.co.fujielectric.fss.util.CommonUtil;
import org.apache.logging.log4j.Logger;

/**
 * [RESTful] sendinfo
 */
@Stateless
@Path("sendinfo")
public class SendInfoFacadeREST extends AbstractFacade<SendInfo> {

    @Inject
    protected Logger LOG;
    
    @Inject
    private EntityManager em;

    @Inject
    private ReceiveInfoService receiveInfoService;
    
    public SendInfoFacadeREST() {
        super(SendInfo.class);
    }

    //[v2.2.2] DB同期エラー後のリトライ対応として、既存レコードがあった場合にレコード追加の前に削除するように修正
    @POST
    @Override
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public void create(SendInfo entity) {
        try {
            //既存レコードを取得
            SendInfo entityOld = super.find(entity.getId());
            if(entityOld != null){
                //既存レコードがある場合・・・
                LOG.debug("# DBSync SendInfo. remove existing records. SendInfoID:{}", entity.getId());
                //関連テーブル(ReceiveInfo,ReceiveFile)のレコードを削除する
                try {
                    List<ReceiveInfo> rcvInfoLst = receiveInfoService.findForSendInfoId(entity.getId());
                    for(ReceiveInfo ri : rcvInfoLst){
                        em.remove(ri);
                    }                
                } catch (Exception e) {
                    //関連レコードの削除で例外発生した場合は処理を続ける。
                    LOG.warn("#! DBSync SendInfo remove existing receiveInfo record error.", e);
                }            
                //既存レコード(SendInfo,SendFile)を削除する。
                try {
                    em.remove(entityOld);
                } catch (Exception e) {
                    //既存レコードの削除で例外発生した場合は処理を続ける。
                    LOG.warn("#! DBSync SendInfo remove existing record error.", e);
                }
            }
            //新規レコード追加
            super.create(entity);
        } catch (Exception e) {
            LOG.error("#! DBSync SendInfo create error.", e);
            throw e;
        }
    }

    @PUT
    @Override
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public void edit(SendInfo entity) {
        super.edit(entity);
    }

    @DELETE
    @Path("{id}")
    @AppTrace
    @SqlTrace
    public void remove(@PathParam("id") String id) {
        id = CommonUtil.decodeBase64(id);
        super.remove(super.find(id));
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }
}
