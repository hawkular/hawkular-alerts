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
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface DocModelProperty {
    String description();
    boolean required() default false;
    int position() default 0;
    String allowableValues() default "";
    String name() default "";
    String defaultValue() default "";
}
