package jp.co.fujielectric.fss.logic;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.data.CommonBean;
import jp.co.fujielectric.fss.util.CommonUtil;
import jp.co.fujielectric.fss.util.JsonUtil;

/**
 * Restマネージャー
 */
@AppTrace
@ApplicationScoped
public class RestManager {
    @Inject
    private CommonBean commonBean;

    public void create(String entityName, Object entity) {
        Client client = null;
        Response response = null;
        try {
            client = ClientBuilder.newClient();
            WebTarget target = client.target(CommonUtil.createLocalUrl(commonBean.getRegionId(), !CommonUtil.isSectionLgwan()) + "webresources/" + entityName);
            Entity<String> postEntity = Entity.entity(JsonUtil.fromObject(entity), MediaType.APPLICATION_JSON_TYPE);
            response = target.request(MediaType.APPLICATION_JSON_TYPE).post(postEntity);
            if (response.getStatus() >= 400) {
                throw new RuntimeException("restful_create[" + entityName + "] status:" + response.getStatus());
            }            
        } finally {
            if(response != null)
                response.close();
            if(client != null)
                client.close();            
        }
    }

    public void edit(String entityName, Object entity) {
        Client client = null;
        Response response = null;
        try {
            client = ClientBuilder.newClient();
            WebTarget target = client.target(CommonUtil.createLocalUrl(commonBean.getRegionId(), !CommonUtil.isSectionLgwan()) + "webresources/" + entityName);
            Entity<String> putEntity = Entity.entity(JsonUtil.fromObject(entity), MediaType.APPLICATION_JSON_TYPE);
            response = target.request(MediaType.APPLICATION_JSON_TYPE).put(putEntity);
            if (response.getStatus() >= 400) {
                throw new RuntimeException("restful_edit[" + entityName + "] status:" + response.getStatus());
            }
        } finally {
            if(response != null)
                response.close();
            if(client != null)
                client.close();            
        }
    }

    public void remove(String entityName, String id) {
        Client client = null;
        Response response = null;
        try {
            client = ClientBuilder.newClient();
            WebTarget target = client.target(CommonUtil.createLocalUrl(commonBean.getRegionId(), !CommonUtil.isSectionLgwan()) + "webresources/" + entityName)
                    .path("{id}")
                    .resolveTemplate("id", CommonUtil.encodeBase64(id));
            response = target.request(MediaType.APPLICATION_JSON_TYPE).delete();
            if (response.getStatus() >= 400) {
                throw new RuntimeException("restful_remove[" + entityName + "] status:" + response.getStatus());
            }
        } finally {
            if(response != null)
                response.close();
            if(client != null)
                client.close();            
        }
    }
}
