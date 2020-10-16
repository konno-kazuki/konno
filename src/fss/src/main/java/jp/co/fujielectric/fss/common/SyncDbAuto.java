package jp.co.fujielectric.fss.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.interceptor.InterceptorBinding;

/**
 * ＤＢ同期用のアノテーション
 */
@InterceptorBinding
@Target(value = {ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface SyncDbAuto {
}
