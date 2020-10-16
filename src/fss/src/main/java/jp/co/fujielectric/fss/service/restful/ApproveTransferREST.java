package jp.co.fujielectric.fss.service.restful;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.logic.SendTransferApprovedLogic;

/**
 * [RESTful] 承認関連サービス
 * 
 */
@Stateless
@Path("approvetransfer")
public class ApproveTransferREST {
    @Inject
    protected SendTransferApprovedLogic sendTransferApprovedLogic;

    @GET
    @Path("sendTransferApproved/{sendInfoId}/{approveId}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public Response execSendTransferApproved(
        @PathParam("sendInfoId") String sendInfoId,
        @PathParam("approveId") String approveId) {

        String result = "";
        try {
            sendTransferApprovedLogic.execSendTransferApproved(sendInfoId, approveId, "");
        } catch (Exception e) {
            e.printStackTrace();
            result = e.getMessage();
        }
        return Response.ok(result).build();
    }
    
    @GET
    @Path("sendTransferApproved/{sendInfoId}/{approveId}/{approvedComment}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public Response execSendTransferApproved(
        @PathParam("sendInfoId") String sendInfoId,
        @PathParam("approveId") String approveId,
        @PathParam("approvedComment") String approvedComment) {

        String result = "";
        try {
            sendTransferApprovedLogic.execSendTransferApproved(sendInfoId, approveId, approvedComment);
        } catch (Exception e) {
            e.printStackTrace();
            result = e.getMessage();
        }
        return Response.ok(result).build();
    }

    @GET
    @Path("sendTransferApprovedRejected/{sendInfoId}/{approveId}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public Response execSendTransferApprovedRejected(
        @PathParam("sendInfoId") String sendInfoId,
        @PathParam("approveId") String approveId) {

        String result = "";
        try {
            sendTransferApprovedLogic.execSendTransferApprovedRejected(sendInfoId, approveId, "");
        } catch (Exception e) {
            e.printStackTrace();
            result = e.getMessage();
        }
        return Response.ok(result).build();
    }

    @GET
    @Path("sendTransferApprovedRejected/{sendInfoId}/{approveId}/{approvedComment}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public Response execSendTransferApprovedRejected(
        @PathParam("sendInfoId") String sendInfoId,
        @PathParam("approveId") String approveId,
        @PathParam("approvedComment") String approvedComment) {

        String result = "";
        try {
            sendTransferApprovedLogic.execSendTransferApprovedRejected(sendInfoId, approveId, approvedComment);
        } catch (Exception e) {
            e.printStackTrace();
            result = e.getMessage();
        }
        return Response.ok(result).build();
    }
    
    @GET
    @Path("sendTransferCanceled/{sendInfoId}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @AppTrace
    @SqlTrace
    public Response execSendTransferCanceled(
        @PathParam("sendInfoId") String sendInfoId) {

        String result = "";
        try {
            sendTransferApprovedLogic.execSendTransferCanceled(sendInfoId);
        } catch (Exception e) {
            e.printStackTrace();
            result = e.getMessage();
        }
        return Response.ok(result).build();
    }
}
