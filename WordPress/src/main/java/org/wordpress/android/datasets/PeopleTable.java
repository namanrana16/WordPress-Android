package org.wordpress.android.datasets;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.wordpress.android.WordPress;
import org.wordpress.android.models.Person;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.SqlUtils;

import java.util.ArrayList;
import java.util.List;

public class PeopleTable {
    public static final String PEOPLE_TABLE = "people";

    private static SQLiteDatabase getReadableDb() {
        return WordPress.wpDB.getDatabase();
    }
    private static SQLiteDatabase getWritableDb() {
        return WordPress.wpDB.getDatabase();
    }

    public static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + PEOPLE_TABLE + " ("
                + "person_id               INTEGER DEFAULT 0,"
                + "blog_id                 TEXT,"
                + "local_blog_id           INTEGER DEFAULT 0,"
                + "user_name               TEXT,"
                + "first_name              TEXT,"
                + "last_name               TEXT,"
                + "display_name            TEXT,"
                + "avatar_url              TEXT,"
                + "role                    TEXT,"
                + "PRIMARY KEY (person_id, local_blog_id)"
                + ");");
    }

    private static void dropTables(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + PEOPLE_TABLE);
    }

    public static void reset(SQLiteDatabase db) {
        AppLog.i(AppLog.T.PEOPLE, "resetting people table");
        dropTables(db);
        createTables(db);
    }

    public static void save(Person person) {
        save(person, getWritableDb());
    }

    public static void save(Person person, SQLiteDatabase database) {
        ContentValues values = new ContentValues();
        values.put("person_id", person.getPersonID());
        values.put("blog_id", person.getBlogId());
        values.put("local_blog_id", person.getLocalTableBlogId());
        values.put("user_name", person.getUsername());
        values.put("first_name", person.getFirstName());
        values.put("last_name", person.getLastName());
        values.put("display_name", person.getDisplayName());
        values.put("avatar_url", person.getAvatarUrl());
        values.put("role", person.getRole());
        database.insertWithOnConflict(PEOPLE_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static void savePeople(List<Person> peopleList, int localTableBlogId) {
        getWritableDb().beginTransaction();
        try {
            //We have a fresh list, remove the previous list of people in case it was deleted on remote
            PeopleTable.deletePeopleForBlog(localTableBlogId);

            for (Person person : peopleList) {
                PeopleTable.save(person);
            }
            getWritableDb().setTransactionSuccessful();
        } finally {
            getWritableDb().endTransaction();
        }
    }

    public static int deletePeopleForBlog(int localTableBlogId) {
        String[] args = new String[]{Integer.toString(localTableBlogId)};
        return getWritableDb().delete(PEOPLE_TABLE, "local_blog_id=?", args);
    }

    public static List<Person> getPeople(int localTableBlogId) {
        List<Person> people = new ArrayList<>();
        String[] args = { Integer.toString(localTableBlogId) };
        Cursor c = getReadableDb().rawQuery("SELECT * FROM " + PEOPLE_TABLE +
                " WHERE local_blog_id=? ORDER BY display_name, user_name", args);

        try {
            while (c.moveToNext()) {
                Person person = getPersonFromCursor(c, localTableBlogId);
                people.add(person);
            }

            return people;
        } finally {
            SqlUtils.closeCursor(c);
        }
    }

    /**
     * retrieve a single person
     * @param personId - id of a person in a particular blog
     * @param localTableBlogId - the local blog id the user belongs to
     * @return Person if found, null otherwise
     */
    public static Person getPerson(long personId, int localTableBlogId) {
        String[] args = { Long.toString(personId), Integer.toString(localTableBlogId) };
        Cursor c = getReadableDb().rawQuery("SELECT * FROM " + PEOPLE_TABLE + " WHERE person_id=? AND local_blog_id=?", args);
        try {
            if (!c.moveToFirst()) {
                return null;
            }
            return getPersonFromCursor(c, localTableBlogId);
        } finally {
            SqlUtils.closeCursor(c);
        }
    }

    private static Person getPersonFromCursor(Cursor c, int localTableBlogId) {
        long personId = c.getInt(c.getColumnIndex("person_id"));
        String blogId = c.getString(c.getColumnIndex("blog_id"));
        String username = c.getString(c.getColumnIndex("user_name"));
        String firstName = c.getString(c.getColumnIndex("first_name"));
        String lastName = c.getString(c.getColumnIndex("last_name"));
        String displayName = c.getString(c.getColumnIndex("display_name"));
        String avatarUrl = c.getString(c.getColumnIndex("avatar_url"));
        String role = c.getString(c.getColumnIndex("role"));

        return new Person(personId, blogId, localTableBlogId, username, firstName, lastName, displayName, avatarUrl, role);
    }
}
