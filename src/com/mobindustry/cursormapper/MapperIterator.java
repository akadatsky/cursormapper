package com.mobindustry.cursormapper;

import android.database.Cursor;

import java.io.IOException;
import java.util.NoSuchElementException;

/** Iterator for getting mapped objects from a Cursor. */
class MapperIterator<T> implements CloseableIterator<T> {

  /** The mapper which instantiates objects of type T. */
  private final CursorMapper<T> cursorMapper;
  /** The cursor backing up this iterator. */
  private final Cursor cursor;
  /** An array which holds the column index for each field of T. */
  private final int[] columnIndexes;

  public MapperIterator(CursorMapper<T> cursorMapper, Cursor cursor) {
    this.cursorMapper = cursorMapper;
    this.cursor = cursor;
    cursor.moveToFirst();
    this.columnIndexes = cursorMapper.loadColumnIndeces(cursor);
  }

  @Override
  public boolean hasNext() {
    if (cursor.isClosed()) {
      return false;
    }
    if (cursor.isAfterLast()) {
      cursor.close();
      return false;
    }
    return true;
  }

  @Override
  public T next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    T instance = cursorMapper.createInstance(cursor, columnIndexes);
    cursor.moveToNext();
    return instance;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void close() throws IOException {
    cursor.close();
  }

}