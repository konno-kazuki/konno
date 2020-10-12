package jp.co.fujielectric.fss.common;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Named;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ロガープロデューサー
 */
@Named
@Dependent
public class LoggerProducer {
    @Produces
    public Logger getLogger(InjectionPoint ip){
        return LogManager.getLogger(ip.getMember().getDeclaringClass());
    }
}
