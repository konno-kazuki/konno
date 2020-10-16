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
import jp.co.fujielectric.fss.entity.SampleContent;
import jp.co.fujielectric.fss.util.CommonUtil;

/**
 * [RESTful] samplecontent
 */
@Stateless
@Path("samplecontent")
public class SampleContentFacadeREST extends AbstractFacade<SampleContent> {

    @Inject
    private EntityManager em;

    public SampleContentFacadeREST() {
        super(SampleContent.class);
    }

    @POST
    @Override
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public void create(SampleContent entity) {
        super.create(entity);
    }

    @PUT
    @Override
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public void edit(SampleContent entity) {
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
