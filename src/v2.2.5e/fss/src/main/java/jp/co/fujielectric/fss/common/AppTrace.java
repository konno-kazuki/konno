package jp.co.fujielectric.fss.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.enterprise.util.Nonbinding;
import javax.interceptor.InterceptorBinding;

/**
 * トレース出力用のアノテーション
 */
@InterceptorBinding
@Target(value = {ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface AppTrace {
    public enum LoggingLevel { NONE, TRACE, DEBUG, INFO, WARN, ERROR; }

    LoggingLevel loggingLevel() default LoggingLevel.TRACE;     // ログ出力のレベル

    @Nonbinding
    String prefixStarted() default "[Started]";                 // 開始時のプレフィックス

    @Nonbinding
    String prefixCompleted() default "[Completed]";             // 終了時のプレフィックス

    @Nonbinding
    String prefixSuspended() default "[Suspended]";             // 例外時のプレフィックス
}
