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
import org.pojoquery.schema.SchemaGenerator;

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

        public Person() {}

        public Person(String firstname, String lastname) {
            this.firstname = firstname;
            this.lastname = lastname;
        }

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

        public Event() {}

        public Event(String title) {
            this.title = title;
        }

        public Long getId() { return id; }
        public String getTitle() { return title; }
    }
    // end::event[]

    @Table("event_person")
    public static class RoleInEvent {
        @Id Long event_id;
        @Id Long person_id;
        String role;

        public RoleInEvent() {}

        public RoleInEvent(Long event_id, Long person_id, String role) {
            this.event_id = event_id;
            this.person_id = person_id;
            this.role = role;
        }

        public Long getEventId() { return event_id; }
        public Long getPersonId() { return person_id; }
        public String getRole() { return role; }
    }

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
        SchemaGenerator.createTables(dataSource, Event.class, Person.class, RoleInEvent.class);
        insertTestData(dataSource);

        // tag::query[]
        EventWithParticipants event = PojoQuery.build(EventWithParticipants.class)
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

    private static void insertTestData(DataSource db) {
        DB.runInTransaction(db, conn -> {
            // Event
            Event event = new Event("Tech Conference 2025");
            PojoQuery.insert(conn, event);
            
            // People
            Person alice = new Person("Alice", "Admin");
            Person bob = new Person("Bob", "Builder");
            Person carol = new Person("Carol", "Coder");
            Person dave = new Person("Dave", "Developer");
            PojoQuery.insert(conn, alice);
            PojoQuery.insert(conn, bob);
            PojoQuery.insert(conn, carol);
            PojoQuery.insert(conn, dave);
            
            PojoQuery.insert(conn, new RoleInEvent(event.id, alice.id, "organizer"));
            PojoQuery.insert(conn, new RoleInEvent(event.id, bob.id, "organizer"));
            PojoQuery.insert(conn, new RoleInEvent(event.id, carol.id, "visitor"));
            PojoQuery.insert(conn, new RoleInEvent(event.id, dave.id, "visitor"));
        });
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
