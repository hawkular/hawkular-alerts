package org.hawkular.alerts.api.doc;

import static org.hawkular.alerts.api.doc.DocConstants.APPLICATION_JSON;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface DocPath {
    String method();
    String path();
    String consumes() default APPLICATION_JSON;
    String produces() default APPLICATION_JSON;
    String name();
    String notes() default "";
}
