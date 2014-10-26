package com.mobindustry.cursormapper;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Defines field name in case when class field name differs from database column name.
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface Column {
  String value();
}
