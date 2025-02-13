package nl.pojoquery;

import static nl.pojoquery.TestUtils.norm;

import java.util.Date;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Link;
import nl.pojoquery.annotations.Table;

public class TestManyToMany {

	@Table("person")
	static class PersonRecord {
		@Id
		Long personID;
		int age;
		String firstname;
		String lastname;
	}
	
	static class Person extends PersonRecord {
		List<EmailAddress> emailAddresses;
	}
	
	@Table("emailaddress")
	static class EmailAddress {
		Long person_id;
		String email;
		String name;
	}


	@Table("event")
	static class Event {
		@Id
		Long eventID;
		String title;
		Date date;
	}
	
	public static class EventWithPersons extends Event {
		@Link(linktable="event_person", linkfield="events_id", foreignlinkfield="persons_id")
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
		public Person person;
		public Event event;
	}
	
	
	@Test
	public void testWhere() {
		PojoQuery<EventWithPersons> q = PojoQuery.build(EventWithPersons.class)
				.addWhere("persons.firstname=?", "John");
		
		Assert.assertEquals(
			norm(
				"SELECT\n" + 
				" `event`.eventID AS `event.eventID`,\n" + 
				" `event`.title AS `event.title`,\n" + 
				" `event`.date AS `event.date`,\n" + 
				" `persons`.personID AS `persons.personID`,\n" + 
				" `persons`.age AS `persons.age`,\n" + 
				" `persons`.firstname AS `persons.firstname`,\n" + 
				" `persons`.lastname AS `persons.lastname`,\n" + 
				" `persons.emailAddresses`.person_id AS `persons.emailAddresses.person_id`,\n" + 
				" `persons.emailAddresses`.email AS `persons.emailAddresses.email`,\n" + 
				" `persons.emailAddresses`.name AS `persons.emailAddresses.name`\n" + 
				"FROM `event` AS `event`\n" + 
				" LEFT JOIN `event_person` AS `event_persons` ON `event`.eventID = `event_persons`.events_id\n" + 
				" LEFT JOIN `person` AS `persons` ON `event_persons`.persons_id = `persons`.personID\n" + 
				" LEFT JOIN `emailaddress` AS `persons.emailAddresses` ON `persons`.personID = `persons.emailAddresses`.person_id\n" + 
				"WHERE persons.firstname=?"), 
			norm(q.toSql()));
	}

}
