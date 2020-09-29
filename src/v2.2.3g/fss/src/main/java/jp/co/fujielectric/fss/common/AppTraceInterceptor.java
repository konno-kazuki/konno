package jp.co.fujielectric.fss.common;

import java.io.Serializable;
import javax.annotation.Priority;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import jp.co.fujielectric.fss.common.AppTrace.LoggingLevel;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

/**
 * トレース出力用のインターセプタ
 */
@Interceptor
@Dependent
@AppTrace
@Priority(Interceptor.Priority.APPLICATION)
public class AppTraceInterceptor implements Serializable {
    @Inject
    private Logger LOG;

    @AroundInvoke
    public Object invoke(final InvocationContext ic) throws Throwable {
        Object rc = null;

        AppTrace appTrace = (ic.getMethod().isAnnotationPresent(AppTrace.class))
                ? ic.getMethod().getAnnotation(AppTrace.class)
                : ic.getTarget().getClass().getSuperclass().getAnnotation(AppTrace.class);

        if (appTrace == null || !isLoggerEnabled(appTrace.loggingLevel())) {
            rc = ic.proceed();
        } else {
            //クラス名取得
            String className = (ic.getMethod().isAnnotationPresent(AppTrace.class))
                    ? ic.getTarget().getClass().getSimpleName()
                    : ClassUtils.getShortClassName(ic.getTarget().getClass().getSuperclass());
            //メソッド名取得
            String methodName = ic.getMethod().getName();
            //パラメータ展開
            String args = (ic.getParameters() != null && 0 < ic.getParameters().length)
                    ? StringUtils.join(ic.getParameters(), ',') : StringUtils.EMPTY;

            log(appTrace.loggingLevel(), "%s::%s%s(%s)", className, methodName, appTrace.prefixStarted(), args);

            Throwable suspened = null;

            try {
                rc = ic.proceed();
            } catch (Throwable t) {
                suspened = t;
                throw t;
            } finally {
                if (suspened == null) {
                    log(appTrace.loggingLevel(), "%s::%s%s(%s)", className, methodName, appTrace.prefixCompleted(), args);
                } else {
                    log(appTrace.loggingLevel(), "%s::%s%s(%s)", className, methodName, appTrace.prefixSuspended(), args);
                }
            }
        }

        return rc;
    }

    protected boolean isLoggerEnabled(LoggingLevel loggingLevel) {
        return !loggingLevel.equals(LoggingLevel.NONE);
    }

    protected void log(LoggingLevel loggingLevel, String format, Object... args) {
        switch (loggingLevel) {
        case TRACE:
            LOG.trace(String.format(format, args));
            break;
        case DEBUG:
            LOG.debug(String.format(format, args));
            break;
        case INFO:
            LOG.info(String.format(format, args));
            break;
        case WARN:
            LOG.warn(String.format(format, args));
            break;
        case ERROR:
            LOG.error(String.format(format, args));
            break;
        case NONE:
        default:
        }
    }
}
