package ru.darkkeks.vkmirror.autoreg;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PARAMETER})
@com.google.inject.BindingAnnotation
public @interface AutoRegClient {
}
