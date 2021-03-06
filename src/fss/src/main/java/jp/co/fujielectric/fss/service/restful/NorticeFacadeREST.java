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
import jp.co.fujielectric.fss.entity.Nortice;
import jp.co.fujielectric.fss.util.CommonUtil;

/**
 * [RESTful] nortice
 */
@Stateless
@Path("nortice")
public class NorticeFacadeREST extends AbstractFacade<Nortice> {

    @Inject
    private EntityManager em;

    public NorticeFacadeREST() {
        super(Nortice.class);
    }

    @POST
    @Override
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public void create(Nortice entity) {
        super.create(entity);
    }

    @PUT
    @Override
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public void edit(Nortice entity) {
        super.edit(entity);
    }

    @DELETE
    @Path("{id}")
    @AppTrace
    @SqlTrace
    public void remove(@PathParam("id") String id) {
        id = CommonUtil.decodeBase64(id);
        super.remove(super.find(Long.parseLong(id)));
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }
}
