package jp.co.fujielectric.fss.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import javax.annotation.Priority;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import jp.co.fujielectric.fss.common.SqlTrace.LoggingLevel;
import jp.co.fujielectric.fss.entity.SqlLog;
import jp.co.fujielectric.fss.data.CommonBean;
import jp.co.fujielectric.fss.service.SqlLogService;
import jp.co.fujielectric.fss.util.DateUtil;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

/**
 * トレース出力用のインターセプタ
 */
@Interceptor
@Dependent
@SqlTrace
@Priority(Interceptor.Priority.APPLICATION)
public class SqlTraceInterceptor implements Serializable {

    @Inject
    private Logger LOG;

    @Inject
    private CommonBean common;

    @Inject
    private SqlLogService sqlLogService;

    @AroundInvoke
    public Object invoke(final InvocationContext ic) throws Throwable {
//        LOG.info("SqlTraceInterceptor Start");

        Object rc = null;

        SqlTrace sqlTrace = (ic.getMethod().isAnnotationPresent(SqlTrace.class))
                ? ic.getMethod().getAnnotation(SqlTrace.class)
                : ic.getTarget().getClass().getSuperclass().getAnnotation(SqlTrace.class);

        if (sqlTrace == null || !isLoggerEnabled(sqlTrace.loggingLevel())) {
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

//            log(sqlTrace.loggingLevel(), "uId=%s onceId=%s::%s::%s%s(%s)::%s::%s", userId, onceId, className, methodName, sqlTrace.prefixStarted(), args, "", DateUtil.getSysDate());
            Throwable suspened = null;

            try {
                //メソッド実行
                rc = ic.proceed();
            } catch (Throwable t) {
                suspened = t;
                throw t;
            } finally {

                int size = 0;
                if (rc != null) {
                    if (rc instanceof ArrayList) {
                        size = ((ArrayList) rc).size();
                    } else {
                        size = 1;
                    }
                }

                if (suspened == null) {
                    log(sqlTrace.loggingLevel(), "uId=%s onceId=%s::%s::%s%s(%s)::%s::%s", userId, onceId, className, methodName, sqlTrace.prefixCompleted(), args, (rc != null) ? Integer.toString(size) : "", DateUtil.getSysDate());
                } else {
                    log(sqlTrace.loggingLevel(), "uId=%s onceId=%s::%s::%s%s(%s)::%s::%s", userId, onceId, className, methodName, sqlTrace.prefixSuspended(), args, (rc != null) ? Integer.toString(size) : "", DateUtil.getSysDate());
                }
            }
        }

//        LOG.info("SqlTraceInterceptor End");
        return rc;
    }

    protected boolean isLoggerEnabled(LoggingLevel loggingLevel) {
        return !loggingLevel.equals(LoggingLevel.NONE);
    }

    protected void log(LoggingLevel loggingLevel, String format, Object... args) {
        // infoレベルで出力
        LOG.debug(String.format(format, args));

        //ログデータを登録
        SqlLog sqlLog = new SqlLog();
        sqlLog.setUId(args[0].toString());
        sqlLog.setOnceId(args[1].toString());
        sqlLog.setClsName(args[2].toString());
        sqlLog.setMethodName(args[3].toString());
        sqlLog.setStatus(args[4].toString());
        sqlLog.setParam(args[5].toString());
        sqlLog.setLstSize(args[6].toString());
        sqlLog.setTStamp((Date) args[7]);
        try {
            // [2017/08/04]DB負荷を低減するため、ログ登録を停止
//            sqlLogService.create(sqlLog);
        } catch (Exception e) {
            LOG.debug("SQLTrace Error: " + e.getMessage());
        }

    }
}
