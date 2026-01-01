package examples.docs;

import java.util.List;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.JoinCondition;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;

/**
 * Example demonstrating filtered relationships using @JoinCondition.
 * Shows how to create separate lists from the same link table based on a role column.
 */
public class FilteredRelationshipExample {

    // tag::person[]
    @Table("person")
    public static class Person {
        @Id Long id;
        String firstname;
        String lastname;

        public Long getId() { return id; }
        public String getFirstname() { return firstname; }
        public String getLastname() { return lastname; }
        public String getFullName() { return firstname + " " + lastname; }
    }
    // end::person[]

    // tag::event[]
    @Table("event")
    public static class Event {
        @Id Long id;
        String title;

        public Long getId() { return id; }
        public String getTitle() { return title; }
    }
    // end::event[]

    // tag::event-with-participants[]
    @Table("event")
    public static class EventWithParticipants {
        @Id Long id;
        String title;

        @Link(linktable = "event_person", linkfield = "event_id", foreignlinkfield = "person_id")
        @JoinCondition("{this}.id = {linktable}.event_id AND {linktable}.role = 'visitor'")
        List<Person> visitors;

        @Link(linktable = "event_person", linkfield = "event_id", foreignlinkfield = "person_id")
        @JoinCondition("{this}.id = {linktable}.event_id AND {linktable}.role = 'organizer'")
        List<Person> organizers;

        public Long getId() { return id; }
        public String getTitle() { return title; }
        public List<Person> getVisitors() { return visitors; }
        public List<Person> getOrganizers() { return organizers; }
    }
    // end::event-with-participants[]

    public static void main(String[] args) {
        DataSource dataSource = createDatabase();
        createTables(dataSource);
        insertTestData(dataSource);

        // tag::query[]
        EventWithParticipants event = PojoQuery.build(EventWithParticipants.class)
            .addWhere("event.id = ?", 1L)
            .execute(dataSource)
            .stream().findFirst().orElse(null);

        if (event != null) {
            System.out.println("Event: " + event.getTitle());
            System.out.println("Visitors (" + event.getVisitors().size() + "):");
            for (Person visitor : event.getVisitors()) {
                System.out.println("  - " + visitor.getFullName());
            }
            System.out.println("Organizers (" + event.getOrganizers().size() + "):");
            for (Person organizer : event.getOrganizers()) {
                System.out.println("  - " + organizer.getFullName());
            }
        }
        // end::query[]
    }

    // tag::schema[]
    private static void createTables(DataSource db) {
        DB.executeDDL(db, """
            CREATE TABLE event (
                id BIGINT IDENTITY PRIMARY KEY,
                title VARCHAR(255)
            )
            """);
        DB.executeDDL(db, """
            CREATE TABLE person (
                id BIGINT IDENTITY PRIMARY KEY,
                firstname VARCHAR(100),
                lastname VARCHAR(100)
            )
            """);
        DB.executeDDL(db, """
            CREATE TABLE event_person (
                event_id BIGINT,
                person_id BIGINT,
                role VARCHAR(20),
                PRIMARY KEY (event_id, person_id, role)
            )
            """);
    }
    // end::schema[]

    private static void insertTestData(DataSource db) {
        // Event
        DB.executeDDL(db, "INSERT INTO event (id, title) VALUES (1, 'Tech Conference 2025')");
        
        // People
        DB.executeDDL(db, "INSERT INTO person (id, firstname, lastname) VALUES (1, 'Alice', 'Admin')");
        DB.executeDDL(db, "INSERT INTO person (id, firstname, lastname) VALUES (2, 'Bob', 'Builder')");
        DB.executeDDL(db, "INSERT INTO person (id, firstname, lastname) VALUES (3, 'Carol', 'Coder')");
        DB.executeDDL(db, "INSERT INTO person (id, firstname, lastname) VALUES (4, 'Dave', 'Developer')");
        
        // Event-Person links with roles
        DB.executeDDL(db, "INSERT INTO event_person (event_id, person_id, role) VALUES (1, 1, 'organizer')");
        DB.executeDDL(db, "INSERT INTO event_person (event_id, person_id, role) VALUES (1, 2, 'organizer')");
        DB.executeDDL(db, "INSERT INTO event_person (event_id, person_id, role) VALUES (1, 3, 'visitor')");
        DB.executeDDL(db, "INSERT INTO event_person (event_id, person_id, role) VALUES (1, 4, 'visitor')");
    }

    private static DataSource createDatabase() {
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:filtered_relationship");
        ds.setUser("SA");
        ds.setPassword("");
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));
        return ds;
    }
}
