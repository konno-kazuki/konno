package jp.co.fujielectric.fss.common;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import jp.co.fujielectric.fss.data.CommonBean;
import org.apache.logging.log4j.Logger;

/**
 * EntityManagerプロデューサー
 */
@Named
@Dependent
public class EntityManagerProducer {
    @Inject
    Logger LOG;

    @Inject
    CommonBean commonBean;

    @Produces
    @RequestScoped
    public EntityManager getEntityManager() {
        LOG.debug("EntityManagerProducer:commonBean.regionId=" + commonBean.getRegionId());
        EntityManager em = getEntityManager(commonBean.getRegionId());
        if(em == null) {
            LOG.warn("EntityManagerProducer:Lookup unitName Nothing");
        }
        return em;
    }

    @RequestScoped
    public static EntityManager getEntityManager(String regionId) {
        try {
//            System.out.println("EntityManagerProducer:commonBean.regionId=" + regionId);
            // commonBeanから対象のユニット名を取得する
            String unitName = regionId;
            Context initCtx = new InitialContext();
            return (EntityManager)initCtx.lookup("java:comp/env/persistence/EntityManager/" + unitName);
        }catch(NamingException e){
        }
        return null;
    }
}
