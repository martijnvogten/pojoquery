package examples.events;

import java.util.Date;
import java.util.HashMap;
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
	
	@Table("event_person")
	public static class EventPersonLink {
		@Id
		public Long event_id;
		
		@Id
		public Long person_id;
		
		public Person person;
		public Event event;

	}
	
	public static void main(String[] args) {
		DataSource db = TestDatabase.dropAndRecreate();
		createTables(db);
		insertData(db);

		PojoQuery<EventPersonLink> links = PojoQuery.build(EventPersonLink.class);
		for(EventPersonLink epl : links.execute(db)) {
			System.out.println(epl.event.getTitle() + " is visited by " + epl.person.getFullName());
		}
		
		PojoQuery<EventWithPersons> q = PojoQuery.build(EventWithPersons.class)
					.addWhere("persons.firstname=?", "John");
		
		for(EventWithPersons event : q.execute(db)) {
			System.out.println(event.persons.get(0).getEmailAddresses().get(0));
		}
	}

	private static void insertData(DataSource db) {
		Event e = new Event();
		e.setDate(new Date());
		e.setTitle("My Event");
		Long eventId = PojoQuery.insert(db, e);
		
		Event concert = new Event();
		concert.setDate(new Date());
		concert.setTitle("The concert");
		Long concertId = PojoQuery.insert(db, concert);
		
		PersonRecord p = new PersonRecord();
		p.setFirstname("John");
		p.setLastname("Ewbank");
		p.setAge(38);
		Long personId = PojoQuery.insert(db, p);
		
		EmailAddress em = new EmailAddress();
		em.setPerson_id(personId);
		em.setName("John Ewbank");
		em.setEmail("john.ewbank@endemol.nl");
		PojoQuery.insert(db, em);
		
		DB.insert(db, "event_person", map("event_id", eventId, "person_id", personId));
		DB.insert(db, "event_person", map("event_id", concertId, "person_id", personId));
		
//		Person marco = new Person();
//		marco.setFirstname("Marco");
//		marco.setLastname("Borsato");
//		marco.setAge(38);
//		Long marcoId = Query.insert(db, marco);
//		DB.insert(db, "event_person", map("event_id", eventId, "person_id", marcoId));
	}

	private static void createTables(DataSource db) {
		SchemaGenerator.createTables(db, Event.class, PersonRecord.class, EventPersonLink.class, EmailAddress.class);
	}
	
	private static <K,V> Map<K,V> map(K k1, V v1, K k2, V v2) {
		Map<K,V> result = new HashMap<K,V>();
		result.put(k1, v1);
		result.put(k2, v2);
		return result;
	}
}
