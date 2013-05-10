package examples.events;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import examples.util.MysqlDatabases;

import nl.pojoquery.DB;
import nl.pojoquery.PojoQuery;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Link;
import nl.pojoquery.annotations.Table;


public class EventsExample {
	
	public static class EventWithPersons extends Event {
		@Link(linktable="event_person", resultClass=Person.class)
		public List<Person> persons;

		@Override
		public String toString() {
			return "EventWithPersons [persons=" + persons + ", getId()="
					+ getId() + ", getDate()=" + getDate() + ", getTitle()="
					+ getTitle() + "]";
		}
	}
	
	public static class PersonWithEvents extends Person {
		@Link(linktable="event_person", resultClass=Event.class)
		private List<Event> events;
		
		public List<Event> getEvents() {
			return events;
		}

		public void setEvents(List<Event> events) {
			this.events = events;
		}

		public String toString() {
			return "PersonWithEvents [events=" + events + ", getId()="
					+ getId() + ", getAge()=" + getAge() + ", getFirstname()="
					+ getFirstname() + ", getLastname()=" + getLastname() + "]";
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

		@Override
		public String toString() {
			return "EventPersonLink [event_id=" + event_id + ", person_id="
					+ person_id + "]";
		}
	}
	
	public static void main(String[] args) {
		DataSource db = MysqlDatabases.createDatabase("localhost", "pojoquery_events", "root", "");
		createTables(db);
		
		Event e = new Event();
		e.setDate(new Date());
		e.setTitle("My Event");
		Long eventId = PojoQuery.insertOrUpdate(db, e);
		
		Event concert = new Event();
		concert.setDate(new Date());
		concert.setTitle("The concert");
		Long concertId = PojoQuery.insertOrUpdate(db, concert);
		
		PersonRecord p = new PersonRecord();
		p.setFirstname("John");
		p.setLastname("Ewbank");
		p.setAge(38);
		Long personId = PojoQuery.insertOrUpdate(db, p);
		
		EmailAddress em = new EmailAddress();
		em.setPerson_id(personId);
		em.setName("John Ewbank");
		em.setEmail("john.ewbank@endemol.nl");
		PojoQuery.insertOrUpdate(db, em);
		
		DB.insertOrUpdate(db, "event_person", map("event_id", eventId, "person_id", personId));
		DB.insertOrUpdate(db, "event_person", map("event_id", concertId, "person_id", personId));
		
//		Person marco = new Person();
//		marco.setFirstname("Marco");
//		marco.setLastname("Borsato");
//		marco.setAge(38);
//		Long marcoId = Query.insertOrUpdate(db, marco);
//		DB.insertOrUpdate(db, "event_person", map("event_id", eventId, "person_id", marcoId));
		

		long start = System.currentTimeMillis();
		PojoQuery<EventPersonLink> links = PojoQuery.build(EventPersonLink.class);
		System.out.println("Finished in " + (System.currentTimeMillis() - start) + " ms");
		System.out.println(links.toSql());
		for(EventPersonLink epl : links.execute(db)) {
			System.out.println(epl.event.getTitle() + " " + epl.person);
		}
		
		PojoQuery<EventWithPersons> q = PojoQuery.build(EventWithPersons.class)
					.addWhere("persons.firstname=?", "John");
		
		System.out.println(q.toSql());
		for(EventWithPersons event : q.execute(db)) {
			System.out.println(event.persons.get(0).getEmailAddresses().get(0));
		}
//		
//		for(PersonWithEvents person : Query.buildQuery(PersonWithEvents.class).execute(db)) {
//			System.out.println(person.getEvents());
//		}
//		
//		for(Event event : Query.buildQuery(Event.class).execute(db)) {
//			System.out.println(event);
//		}
	}

	private static void createTables(DataSource db) {
		DB.executeDDL(db, "CREATE TABLE event  (id BIGINT NOT NULL AUTO_INCREMENT, title TEXT, `date` DATETIME, PRIMARY KEY(id))");
		DB.executeDDL(db, "CREATE TABLE person (id BIGINT NOT NULL AUTO_INCREMENT, age INT, firstName VARCHAR(255), lastName VARCHAR(255), PRIMARY KEY(id))");
		DB.executeDDL(db, "CREATE TABLE event_person (person_id BIGINT NOT NULL, event_id BIGINT NOT NULL, PRIMARY KEY(person_id, event_id))");
		DB.executeDDL(db, "CREATE TABLE emailaddress (person_id BIGINT NOT NULL, name VARCHAR(128), email VARCHAR(128) NOT NULL, PRIMARY KEY(person_id, name, email))");
	}
	
	private static <K,V> Map<K,V> map(K k1, V v1, K k2, V v2) {
		Map<K,V> result = new HashMap<K,V>();
		result.put(k1, v1);
		result.put(k2, v2);
		return result;
	}
}
