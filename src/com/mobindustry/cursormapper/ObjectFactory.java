package com.mobindustry.cursormapper;

/**
 * Interface that will handle creation of new objects of type {@code <T>}
 * @param <T> objects of this type will be created.
 */
public interface ObjectFactory<T> {
  T newInstance();
}
