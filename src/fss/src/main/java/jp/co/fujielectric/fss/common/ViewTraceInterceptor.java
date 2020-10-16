package jp.co.fujielectric.fss.common;

import java.io.Serializable;
import java.util.Date;
import javax.annotation.Priority;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import jp.co.fujielectric.fss.common.ViewTrace.LoggingLevel;
import jp.co.fujielectric.fss.entity.ViewLog;
import jp.co.fujielectric.fss.data.CommonBean;
import jp.co.fujielectric.fss.service.ViewLogService;
import jp.co.fujielectric.fss.util.DateUtil;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

/**
 * トレース出力用のインターセプタ
 */
@Interceptor
@Dependent
@ViewTrace
@Priority(Interceptor.Priority.APPLICATION)
public class ViewTraceInterceptor implements Serializable {

    @Inject
    private Logger LOG;

    @Inject
    private CommonBean common;

    @Inject
    private ViewLogService viewLogService;

    @AroundInvoke
    public Object invoke(final InvocationContext ic) throws Throwable {

        Object rc = null;

        ViewTrace viewTrace = (ic.getMethod().isAnnotationPresent(ViewTrace.class))
                ? ic.getMethod().getAnnotation(ViewTrace.class)
                : ic.getTarget().getClass().getSuperclass().getAnnotation(ViewTrace.class);

        if (viewTrace == null || !isLoggerEnabled(viewTrace.loggingLevel())) {
            //メソッド実行
            rc = ic.proceed();
        } else {
            //ログインID取得
            String userId = (common.getUserId() != null) ? common.getUserId() : "";
            //ワンタイムID取得
            String onceId = (common.getOnetimeId() != null) ? common.getOnetimeId() : "";
            //クラス名取得
            String className = (ic.getMethod().isAnnotationPresent(SqlTrace.class))
                    ? ic.getTarget().getClass().getSimpleName()
                    : ClassUtils.getShortClassName(ic.getTarget().getClass().getSuperclass());
            //メソッド名取得
            String methodName = ic.getMethod().getName();
            //パラメータ展開
            String args = (ic.getParameters() != null && 0 < ic.getParameters().length)
                    ? StringUtils.abbreviate(StringUtils.join(ic.getParameters(), ','), 255) : StringUtils.EMPTY;
            //戻り値
            String ret = "";

//            log(viewTrace.loggingLevel(), "uId=%s onceId=%s::%s::%s%s(%s)::%s", userId, onceId, className, methodName, viewTrace.prefixStarted(), args, ret, DateUtil.getSysDate());
            Throwable suspened = null;

            try {
                //メソッド実行
                rc = ic.proceed();
            } catch (Throwable t) {
                suspened = t;
                throw t;
            } finally {

                if (rc != null) {
                    ret = StringUtils.abbreviate(rc.toString(), 255);
                }

                if (suspened == null) {
                    log(viewTrace.loggingLevel(), "uId=%s onceId=%s::%s::%s%s(%s)::%s", userId, onceId, className, methodName, viewTrace.prefixCompleted(), args, ret, DateUtil.getSysDate());
                } else {
                    log(viewTrace.loggingLevel(), "uId=%s onceId=%s::%s::%s%s(%s)::%s", userId, onceId, className, methodName, viewTrace.prefixSuspended(), args, ret, DateUtil.getSysDate());
                }
            }
        }

        return rc;
    }

    protected boolean isLoggerEnabled(LoggingLevel loggingLevel) {
        return !loggingLevel.equals(LoggingLevel.NONE);
    }

    protected void log(LoggingLevel loggingLevel, String format, Object... args) {
        // Debugレベルで出力
        LOG.debug(String.format(format, args));

        //ログデータを登録
        ViewLog viewLog = new ViewLog();
        viewLog.setUId(args[0].toString());
        viewLog.setOnceId(args[1].toString());
        viewLog.setClsName(args[2].toString());
        viewLog.setMethodName(args[3].toString());
        viewLog.setStatus(args[4].toString());
        viewLog.setParam(args[5].toString());
        viewLog.setRet(args[6].toString());
        viewLog.setTStamp((Date) args[7]);
        try {
            // [2017/08/04]DB負荷を低減するため、ログ登録を除外
//            viewLogService.create(viewLog);
        } catch (Exception e) {
            LOG.debug("ViewTrace Error: " + e.getMessage());
        }

    }
}
