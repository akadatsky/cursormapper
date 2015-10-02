package com.mobindustry.cursormapper;

import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

/**
 * This class will handle all job with cursor from your database.
 * Use static method {@link com.mobindustry.cursormapper.CursorMapper#create(Class)} to invoke.<br>
 * To get objects with data call one of following methods:
 * {@link com.mobindustry.cursormapper.CursorMapper#map(android.database.Cursor)},
 * {@link com.mobindustry.cursormapper.CursorMapper#mapInto(android.database.Cursor, java.util.Collection)},
 * {@link com.mobindustry.cursormapper.CursorMapper#mapSingle(android.database.Cursor)},
 * {@link com.mobindustry.cursormapper.CursorMapper#mapRow(android.database.Cursor)}.
 *
 * @param <T> class definition for future collection of objects with data from cursor.
 */
public class CursorMapper<T> {

    /**
     * you have to implement this interface in your code
     */
    public interface HowToParseClass {
        String VALUE_PARAM = "VALUE_PARAM";

        /**
         * @param classX          - your class
         * @param cursorFieldType - Cursor.FIELD_TYPE_* value, expected value from database
         * @param value           - Bundle with value in cursor/database, you have to convert this value to your class object, use VALUE_PARAM to extract it from Bundle
         *                        if value is null - use correct CursorFieldType param (see {@link #getValue(Class, Cursor, int)})
         * @return instance of your class
         */
        Object getValueByFieldType(Class classX, int cursorFieldType, Bundle value);
    }

    protected final ObjectFactory<T> factory;
    protected Field[] fields;
    protected Method[] postMapHooks;
    protected HashMap<Class, HowToParseClass> valueCustomTypesMap;

    /**
     * If you want to parse some your data from cursor/database and get your class by parsed data
     * then you can call method addHowToParseForCustomClass
     *
     * @param aClass          - your custom class which you want to get
     * @param howToParseClass - interface which explain how to parse database data in your class
     */
    public void addHowToParseForCustomClass(Class aClass, HowToParseClass howToParseClass) {
        valueCustomTypesMap.put(aClass, howToParseClass);
    }

    public CursorMapper(Class<T> recordClass, ObjectFactory<T> factory) {
    this.valueCustomTypesMap = new HashMap<>();
    this.factory = factory;
    List<Field> fields = new ArrayList<Field>();
    getFields(recordClass, fields);
    this.fields = fields.toArray(new Field[fields.size()]);
    List<Method> postMapHooks = new ArrayList<Method>();
    for (Method method : recordClass.getDeclaredMethods()) {
      for (Annotation annotation : method.getDeclaredAnnotations()) {
        if (annotation instanceof PostMap) {
          method.setAccessible(true);
          postMapHooks.add(method);
          break;
        }
      }
    }
        this.postMapHooks = postMapHooks.toArray(new Method[postMapHooks.size()]);
  }

  protected void getFields(Class<?> recordClass, List<Field> fields) {
    if (recordClass.equals(Object.class)) {
      return;
    }
    for (Field field : recordClass.getDeclaredFields()) {
      field.setAccessible(true);
      fields.add(field);
    }
    getFields(recordClass.getSuperclass(), fields);
  }

    /**
     * Static initializer.
     * @param recordClass data type class definition.
     * @param <T> type of objects to work with.
     * @return new {@link com.mobindustry.cursormapper.CursorMapper} for objects of given type.
     */
  public static <T> CursorMapper<T> create(final Class<T> recordClass) {

    return new CursorMapper<T>(recordClass, new ObjectFactory<T>() {
      @Override
      public T newInstance() {
        final UnsafeAllocator unsafeAllocator = UnsafeAllocator.create();
        T instance;
        try {
          instance = recordClass.newInstance();
        } catch (Exception e) {
          try {
            instance = unsafeAllocator.newInstance(recordClass);
          } catch (Exception e1) {
            throw new RuntimeException(e1);
          }
        }
        return instance;
      }
    });
  }

  public static <T> CursorMapper<T> create(Class<T> recordClass, ObjectFactory<T> factory) {
    return new CursorMapper<T>(recordClass, factory);
  }

  /**
   * Maps all rows. Closes cursor when done.
   */
  public List<T> map(Cursor cursor) {
    return mapInto(cursor, new ArrayList<T>(cursor.getCount()));
  }

  /**
   * Maps all rows into the provided collection. Closes cursor when done.
   */
  public <C extends Collection<T>> C mapInto(Cursor cursor, C result) {
    try {
      int[] columnIndexes = loadColumnIndexes(cursor);
      cursor.moveToFirst();
      while (!cursor.isAfterLast()) {
        T instance = factory.newInstance();
        for (int i = 0; i < fields.length; i++) {
          Field field = fields[i];
          if (columnIndexes[i] != -1) {
            field.set(instance, getValue(field.getType(), cursor, columnIndexes[i]));
          }
        }
        runPostMapHooks(instance);
        result.add(instance);
        cursor.moveToNext();
      }
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } finally {
      tryClosing(cursor);
    }
    return result;
  }

  private void tryClosing(Cursor cursor) {
    try {
      cursor.close();
    } catch (SQLiteException e) {
      // Nothing to do.
      Log.w("CursorMapper", "Failed to close cursor", e);
    }
  }

    /** Get an array which holds the column index for each field of T. */
  protected int[] loadColumnIndexes(Cursor cursor) {
    int[] result = new int[fields.length];
    for (int i = 0; i < fields.length; i++) {
      Field field = fields[i];
      String columnName = field.getName();
      if (field.getAnnotation(Column.class) != null) {
        columnName = field.getAnnotation(Column.class).value();
      }
      result[i] = cursor.getColumnIndex(columnName);
    }
    return result;
  }

    /**
     * Determine type of field with given index and get it's value.
     * @param type type of field.
     * @param cursor data obtained from database.
     * @param columnIndex current column's index.
     * @return value of field at given index.
     */
    protected Object getValue(Class<?> type, Cursor cursor, int columnIndex) {
        if (valueCustomTypesMap.containsKey(type)) {
            HowToParseClass howToParseClass = valueCustomTypesMap.get(type);
            int typeOfColumn = cursor.getType(columnIndex);
            Bundle bundle = new Bundle();
            if (typeOfColumn == Cursor.FIELD_TYPE_FLOAT) {
                bundle.putDouble(HowToParseClass.VALUE_PARAM, cursor.getDouble(columnIndex));
            } else if (typeOfColumn == Cursor.FIELD_TYPE_INTEGER) {
                bundle.putInt(HowToParseClass.VALUE_PARAM, cursor.getInt(columnIndex));
            } else if (typeOfColumn == Cursor.FIELD_TYPE_STRING) {
                bundle.putString(HowToParseClass.VALUE_PARAM, cursor.getString(columnIndex));
            } else {
                bundle = null;
            }
            return howToParseClass.getValueByFieldType(type,typeOfColumn, bundle);
    }
    else if (type == String.class) {
      return cursor.getString(columnIndex);
    } else if (type == int.class || type == Integer.class) {
      if (TextUtils.isEmpty(cursor.getString(columnIndex))) {
        return 0;
      } else {
        return cursor.getInt(columnIndex);
      }
    } else if (type == long.class || type == Long.class) {
      if (TextUtils.isEmpty(cursor.getString(columnIndex))) {
        return 0;
      } else {
        return cursor.getLong(columnIndex);
      }
    } else if (type == double.class || type == Double.class) {
      if (TextUtils.isEmpty(cursor.getString(columnIndex))) {
        return 0;
      } else {
        return cursor.getDouble(columnIndex);
      }
    } else if (type == float.class || type == Float.class) {
      if (TextUtils.isEmpty(cursor.getString(columnIndex))) {
        return 0;
      } else {
        return cursor.getFloat(columnIndex);
      }
    } else if (type == boolean.class || type == Boolean.class) {
      if (TextUtils.isEmpty(cursor.getString(columnIndex))) {
        return false;
      } else {
        return cursor.getInt(columnIndex) != 0;
      }
    } else if (type == Currency.class) {
      final String currencyCode = cursor.getString(columnIndex);
      try {
        return Currency.getInstance(currencyCode);
      } catch (IllegalArgumentException e) {
        Log.w("CursorMapper", "No such currency: " + currencyCode);
        return null;
      }
    } else if (type == TimeZone.class) {
      final String tz = cursor.getString(columnIndex);
      if (tz == null) {
        return null;
      }
      try {
        return TimeZone.getTimeZone(tz);
      } catch (IllegalArgumentException e) {
        Log.w("CursorMapper", "No such timezone: " + tz);
        return null;
      }
    } else if (type == Date.class) {
      String value = cursor.getString(columnIndex);
      if (TextUtils.isEmpty(value)) {
        return null;
      } else {
        // The data could be String or Numeric.
        // We do not cache the dateFormat because it has threading problems.
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        dateFormat.setLenient(false);
        try {
          return dateFormat.parse(value);
        } catch (ParseException e) {
          return new Date(cursor.getLong(columnIndex));
        }
      }
    } else {
      throw new RuntimeException("Mapping to type not implemented yet: " + type);
    }
  }

  /**
   * Map a cursor assuming there is a single row in it. Closes cursor afterwards.
   */
  public T mapSingle(Cursor cursor) {
    try {
      cursor.moveToFirst();
      if (cursor.isAfterLast()) {
        return null;
      }
      return mapRow(cursor);
    } finally {
      tryClosing(cursor);
    }
  }

  /**
   * Map the current row in the cursor. Keeps the cursor open.
   */
  public T mapRow(Cursor cursor) {
    try {
      T instance = factory.newInstance();
      int[] columnIndeces = loadColumnIndexes(cursor);
      for (int i = 0; i < fields.length; i++) {
        Field field = fields[i];
        if (columnIndeces[i] != -1) {
          field.set(instance, getValue(field.getType(), cursor, columnIndeces[i]));
        }
      }
      runPostMapHooks(instance);
      return instance;
    } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
    }
  }

  protected void runPostMapHooks(T instance) {
    for (Method postMapHook : postMapHooks) {
      try {
        postMapHook.invoke(instance);
      } catch (Exception e) {
        throw new RuntimeException("Failure while running @After hook.", e);
      }
    }
  }

  public CloseableIterator<T> iterator(Cursor cursor) {
    return new MapperIterator<T>(this, cursor);
  }

  public T createInstance(Cursor cursor, int[] columnIndexes) {
    final T instance = factory.newInstance();
    for (int i = 0; i < fields.length; i++) {
      final Field field = fields[i];
      if (columnIndexes[i] != -1) {
        try {
          field.set(instance, getValue(field.getType(), cursor, columnIndexes[i]));
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }
    }
    runPostMapHooks(instance);
    return instance;
  }
}
