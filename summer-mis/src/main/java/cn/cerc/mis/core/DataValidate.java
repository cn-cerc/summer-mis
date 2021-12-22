package cn.cerc.mis.core;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target( ElementType.METHOD )
@Retention(RUNTIME)
public @interface DataValidate {
    public static String DefaultErrorMessage = "%s cannot be empty";

    String message() default DefaultErrorMessage;

    String[] value();

}