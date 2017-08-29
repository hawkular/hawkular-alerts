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
public @interface DocParameter {
    String name() default "";
    boolean body() default false;
    boolean path() default false;
    Class<?> type() default String.class;
    String typeContainer() default "";
    boolean required() default false;
    String description() default "";
    String allowableValues() default "";
}
