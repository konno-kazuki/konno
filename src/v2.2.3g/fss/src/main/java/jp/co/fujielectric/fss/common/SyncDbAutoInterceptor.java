package jp.co.fujielectric.fss.common;

import java.io.Serializable;
import javax.annotation.Priority;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import jp.co.fujielectric.fss.logic.RestManager;
import jp.co.fujielectric.fss.util.CommonUtil;
import org.apache.commons.lang3.ClassUtils;

/**
 * ＤＢ同期用のインターセプタ
 */
@Interceptor
@Dependent
@SyncDbAuto
@Priority(Interceptor.Priority.APPLICATION)
public class SyncDbAutoInterceptor implements Serializable {
    @Inject
    RestManager restManager;

    @AroundInvoke
    public Object invoke(final InvocationContext ic) throws Throwable {
        Object rc = null;

        SyncDbAuto syncDbAuto = (ic.getMethod().isAnnotationPresent(SyncDbAuto.class))
                ? ic.getMethod().getAnnotation(SyncDbAuto.class)
                : ic.getTarget().getClass().getSuperclass().getAnnotation(SyncDbAuto.class);

        if (syncDbAuto == null) {
            rc = ic.proceed();
        } else {
            boolean syncDB = Boolean.valueOf(CommonUtil.getSetting("sync_db"));
            if (syncDB && ic.getParameters() != null && ic.getParameters().length > 0) {
                //クラス名取得
                String className = (ic.getMethod().isAnnotationPresent(SyncDbAuto.class))
                    ? ic.getTarget().getClass().getSimpleName()
                    : ClassUtils.getShortClassName(ic.getTarget().getClass().getSuperclass());
//                String entityName = ClassUtils.getShortClassName(ic.getParameters()[0].getClass()).toLowerCase();
                String entityName = className.toLowerCase().replace("service", "");     // TODO: remove時は引数がStringになる。entityNameの取得方法を再検討
                String methodName = ic.getMethod().getName();

                switch (methodName.toLowerCase()) {
                case "create":
                    restManager.create(entityName, ic.getParameters()[0]);
                    break;
                case "edit":
                    restManager.edit(entityName, ic.getParameters()[0]);
                    break;
                case "remove":
                    restManager.remove(entityName, ic.getParameters()[0].toString());
                    break;
                }
            }

            try {
                rc = ic.proceed();
            } catch (Throwable t) {
                throw t;
            } finally {
            }
        }
        return rc;
    }
}
