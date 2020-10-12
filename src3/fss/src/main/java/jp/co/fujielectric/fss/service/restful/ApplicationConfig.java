package jp.co.fujielectric.fss.service.restful;

import java.util.Set;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

/**
 * RESTfulアプリケーション設定クラス
 */
@ApplicationPath("webresources")
public class ApplicationConfig extends Application {
    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> resources = new java.util.HashSet<>();
        addRestResourceClasses(resources);
        return resources;
    }

    /**
     * Do not modify addRestResourceClasses() method.
     * It is automatically populated with
     * all resources defined in the project.
     * If required, comment out calling this method in getClasses().
     */
    private void addRestResourceClasses(Set<Class<?>> resources) {
        resources.add(jp.co.fujielectric.fss.service.restful.ApproveInfoFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.ApproveTransferREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.BasicUserFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.CheckedFileFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.ConfigFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.DecryptFileFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.DefineFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.DefineImageFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.DestAddressBookFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.DispMessageFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.FuncFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.MailLostFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.MailMessageFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.MenuFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.NorticeFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.OnceUserFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.PasswordUnlockREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.ReceiveFileFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.ReceiveInfoFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.SampleContentFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.SampleFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.SampleSubjectFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.SendFileFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.SendInfoFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.SendRequestInfoFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.SendRequestToFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.SendTransferPasswordUnlockREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.SendTransferREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.UserPasswordHistoryFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.UserTypeFacadeREST.class);
        resources.add(jp.co.fujielectric.fss.service.restful.UserTypePermissionFacadeREST.class);
    }
}
