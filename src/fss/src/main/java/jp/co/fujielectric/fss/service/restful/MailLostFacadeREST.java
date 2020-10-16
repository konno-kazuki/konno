/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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
import jp.co.fujielectric.fss.entity.MailLost;

/**
 * [RESTful] mailLost
 */
@Stateless
@Path("maillost")
public class MailLostFacadeREST extends AbstractFacade<MailLost> {

    @Inject
    private EntityManager em;

    public MailLostFacadeREST() {
        super(MailLost.class);
    }

    @POST
    @Override
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public void create(MailLost entity) {
        super.create(entity);
    }

    @PUT
    @Override
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public void edit(MailLost entity) {
        super.edit(entity);
    }

    @DELETE
    @Path("{id}")
    @AppTrace
    @SqlTrace
    public void remove(@PathParam("id") String id) {
        super.remove(super.find(id));
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

}
