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
@Target({ElementType.METHOD})
public @interface DocResponse {
    int code();
    String message();
    Class<?> response() default NULL.class;
    String responseContainer() default "";

    class NULL {}
}
