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
import jp.co.fujielectric.fss.entity.SendFile;
import jp.co.fujielectric.fss.util.CommonUtil;

/**
 * [RESTful] sendfile
 */
@Stateless
@Path("sendfile")
public class SendFileFacadeREST extends AbstractFacade<SendFile> {
    @Inject
    private EntityManager em;

    public SendFileFacadeREST() {
        super(SendFile.class);
    }

    @POST
    @Override
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public void create(SendFile entity) {
        super.create(entity);
    }

    @PUT
    @Override
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public void edit(SendFile entity) {
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
