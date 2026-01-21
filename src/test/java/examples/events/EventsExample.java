package examples.events;

import java.sql.Connection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabase;
import org.pojoquery.schema.SchemaGenerator;


public class EventsExample {
	
	// tag::many-to-many[]
	public static class EventWithPersons extends Event {
		@Link(linktable="event_person")
		public List<Person> persons;

	}
	
	public static class PersonWithEvents extends Person {
		@Link(linktable="event_person")
		private List<Event> events;
		
		public List<Event> getEvents() {
			return events;
		}

		public void setEvents(List<Event> events) {
			this.events = events;
		}

	}
	// end::many-to-many[]
	
	// tag::link-entity[]
	@Table("event_person")
	public static class EventPersonLink {
		@Id
		public Long event_id;
		
		@Id
		public Long person_id;
		
		public Person person;
		public Event event;
	}
	// end::link-entity[]
	
	public static void main(String[] args) {
		DataSource db = TestDatabase.dropAndRecreate();
		createTables(db);
		insertData(db);

		DB.withConnection(db, c -> {
			// tag::query-links[]
			PojoQuery<EventPersonLink> links = PojoQuery.build(EventPersonLink.class);
			for(EventPersonLink epl : links.execute(c)) {
				System.out.println(epl.event.getTitle() + " is visited by " + epl.person.getFullName());
			}
			// end::query-links[]
			
			// tag::query-with-filter[]
			PojoQuery<EventWithPersons> q = PojoQuery.build(EventWithPersons.class)
						.addWhere("{persons}.firstname=?", "John");
			
			for(EventWithPersons event : q.execute(c)) {
				System.out.println(event.persons.get(0).getEmailAddresses().get(0));
			}
			// end::query-with-filter[]
		});

	}

	private static void insertData(DataSource db) {
		DB.withConnection(db, (Connection c) -> {
			Event e = new Event();
			e.setDate(new Date());
			e.setTitle("My Event");
			Long eventId = PojoQuery.insert(c, e);
			
			Event concert = new Event();
			concert.setDate(new Date());
			concert.setTitle("The concert");
			Long concertId = PojoQuery.insert(c, concert);
			
			PersonRecord p = new PersonRecord();
			p.setFirstname("John");
			p.setLastname("Ewbank");
			p.setAge(38);
			Long personId = PojoQuery.insert(c, p);
			
			EmailAddress em = new EmailAddress();
			em.setPerson_id(personId);
			em.setName("John Ewbank");
			em.setEmail("john.ewbank@endemol.nl");
			PojoQuery.insert(c, em);
			
			DB.insert(c, "event_person", Map.of("event_id", eventId, "person_id", personId));
			DB.insert(c, "event_person", Map.of("event_id", concertId, "person_id", personId));
		});
	}

	private static void createTables(DataSource db) {
		SchemaGenerator.createTables(db, Event.class, PersonRecord.class, EventPersonLink.class, EmailAddress.class);
	}
	
}

