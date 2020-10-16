/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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
@Target(value = {ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ViewTrace {

    public enum LoggingLevel {
        NONE, TRACE, DEBUG, INFO, WARN, ERROR;
    }

    @Nonbinding
    LoggingLevel loggingLevel() default LoggingLevel.DEBUG;     // ログ出力のレベル

    @Nonbinding
    String prefixStarted() default "[Started]";                 // 開始時のプレフィックス

    @Nonbinding
    String prefixCompleted() default "[Completed]";             // 終了時のプレフィックス

    @Nonbinding
    String prefixSuspended() default "[Suspended]";             // 例外時のプレフィックス
}
