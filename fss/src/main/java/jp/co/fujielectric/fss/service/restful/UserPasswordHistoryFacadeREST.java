package jp.co.fujielectric.fss.service.restful;

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
import jp.co.fujielectric.fss.entity.UserPasswordHistory;
import jp.co.fujielectric.fss.util.CommonUtil;

/**
 *
 * [RESTful] userpasswordhistory
 */
@Stateless
@Path("userpasswordhistory")
public class UserPasswordHistoryFacadeREST extends AbstractFacade<UserPasswordHistory> {

    @Inject
    private EntityManager em;

    public UserPasswordHistoryFacadeREST() {
        super(UserPasswordHistory.class);
    }

    @POST
    @Override
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public void create(UserPasswordHistory entity) {
        super.create(entity);
    }

    @PUT
    @Override
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public void edit(UserPasswordHistory entity) {
        super.edit(entity);
    }

    @DELETE
    @Path("{id}")
    @AppTrace
    @SqlTrace
    public void remove(@PathParam("id") String id) {
        try{
            id = CommonUtil.decodeBase64(id);
            super.remove(super.find(id));            
        }catch(Exception ex){
            //削除連動の失敗は無視する
        }
    }
    
    @Override
    protected EntityManager getEntityManager() {
        return em;
    }
    
}
