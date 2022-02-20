package cn.cerc.mis.core;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RUNTIME)
@Repeatable(DataValidates.class)
public @interface DataValidate {
    public static String DefaultErrorMessage = "%s cannot be empty";

    String value();

    String message() default DefaultErrorMessage;

    String name() default "";

    /* sample:$value > 0 */
    String allow() default "";

}
