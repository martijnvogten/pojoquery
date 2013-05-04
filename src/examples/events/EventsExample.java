package events;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import system.db.DB;
import system.sql.Query;
import system.sql.annotations.Id;
import system.sql.annotations.Link;
import system.sql.annotations.Table;

public class Main {
	
	@Table("event")
	public static class EventWithPersons extends Event {
		@Link(linktable="event_person", resultClass=Person.class)
		private List<Person> persons;

		public List<Person> getPersons() {
			return persons;
		}

		public void setPersons(List<Person> persons) {
			this.persons = persons;
		}

		@Override
		public String toString() {
			return "EventWithPersons [persons=" + persons + ", getId()="
					+ getId() + ", getDate()=" + getDate() + ", getTitle()="
					+ getTitle() + "]";
		}
	}
	
	@Table("person")
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
		DataSource db = DB.getDataSource("jdbc:mysql://localhost/events", "root", "");
		DB.executeDDL(db, "DELETE FROM event");
		DB.executeDDL(db, "DELETE FROM person");
		DB.executeDDL(db, "DELETE FROM event_person");
		DB.executeDDL(db, "DELETE FROM emailaddress");
		
		Event e = new Event();
		e.setDate(new Date());
		e.setTitle("My Event");
		Long eventId = Query.insertOrUpdate(db, e);
		
		Event concert = new Event();
		concert.setDate(new Date());
		concert.setTitle("The concert");
		Long concertId = Query.insertOrUpdate(db, concert);
		
		PersonRecord p = new PersonRecord();
		p.setFirstname("John");
		p.setLastname("Ewbank");
		p.setAge(38);
		Long personId = Query.insertOrUpdate(db, p);
		
		EmailAddress em = new EmailAddress();
		em.setPerson_id(personId);
		em.setName("John Ewbank");
		em.setEmail("john.ewbank@endemol.nl");
		Query.insertOrUpdate(db, em);
		
		DB.insertOrUpdate(db, "event_person", map("event_id", eventId, "person_id", personId));
		DB.insertOrUpdate(db, "event_person", map("event_id", concertId, "person_id", personId));
		
//		Person marco = new Person();
//		marco.setFirstname("Marco");
//		marco.setLastname("Borsato");
//		marco.setAge(38);
//		Long marcoId = Query.insertOrUpdate(db, marco);
//		DB.insertOrUpdate(db, "event_person", map("event_id", eventId, "person_id", marcoId));
		
		Query<EventPersonLink> links = Query.buildQuery(EventPersonLink.class);
		System.out.println(links.toSql());
		for(EventPersonLink epl : links.execute(db)) {
			System.out.println(epl.event.getTitle() + " " + epl.person);
		}
		
		Query<EventWithPersons> q = Query.buildQuery(EventWithPersons.class)
					.addWhere("persons.firstname=?", "John");
		
		System.out.println(q.toSql());
		for(EventWithPersons event : q.execute(db)) {
			System.out.println(event.getPersons().get(0).getEmailAddresses().get(0));
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

	private static <K,V> Map<K,V> map(K k1, V v1, K k2, V v2) {
		Map<K,V> result = new HashMap<K,V>();
		result.put(k1, v1);
		result.put(k2, v2);
		return result;
	}
}
