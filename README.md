## cursormapper

A ready-to-use library for android to map your cursor into set of objects.

### Usage

Create a data model class with fields to hold data from database columns,
initiate a CursorMapper object parameterized with that type and feed it with cursor after queriyng database.
Have a collection of objects representing a result of your query.

### Examples

Say you have a database with table DOG:

_id | name | is_happy
:-- | :--- | :-------
001 | Barker | true
002 | Lassie | true
003 | Jerry Lee | true
004 | Saddy | false

First, create a class to represent one dog's dataset:

```java
public class DogModel {
	@Column("_id")
	private int id;
    private String name;
    @Column("is_happy")
    private boolean isHappy;
    
    public int getId() {
    	return id;
    }
    
    public int getName() {
    	return name;
    }
    
    public int getHappiness() {
    	return isHappy;
    }
    
    public void setName(String name) {
    	this.name = name;
    }
    
    public void setHappiness(boolean isHappy) {
    	this.isHappy = isHappy;
    }
}
```

Second, in your class which wraps database job (the one that `extends SQLiteOpenHelper`) - add this:

```java
private static final CursorMapper<DogModel> dogMapper = CursorMapper.create(DogModel.class);
```

The last step - use one of CursorMapper's methods map, mapInto, mapSingle, mapRow:

```java
public DogModel getDog(String id) {
  	Cursor cursor = getReadableDatabase().query(
        "DOG",
        new String[]{"_id"},
        "_id = ?",
        new String[]{id},
        null /* group by */,
        null /* having */,
        null /* order by */);
  	return dogMapper.mapSingle(cursor);
}
  
  public List<DogModel> getAllDogs() {
  	Cursor cursor = getReadableDatabase().rawQuery("SELECT * FROM " + "DOG", null);
  return dogMapper.map(cursor);
}
```

### Sample project

There's a sample project that can give you a bigger picture on how to use cursormapper. For details - see [UserDatabaseExample](https://github.com/akadatsky/UserDatabaseExample).