package org.hawkular.alerts.api.doc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface DocModel {
    String description() default "";
    Class[] subTypes() default  {};
}
