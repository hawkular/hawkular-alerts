package org.hawkular.alerts.alerters.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Define an alerters plugin implementation
 * Plugin must have a unique name that will be used at registration phase
 * Plugin must implement AlerterPlugin interface
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Alerter {
    String name();
}
