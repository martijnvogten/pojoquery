package nl.pojoquery;

import static nl.pojoquery.TestUtils.norm;

import java.util.Date;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.JoinCondition;
import nl.pojoquery.annotations.Link;
import nl.pojoquery.annotations.Table;

public class TestManyToManyWithCondition {

	@Table("person")
	static class Person {
		@Id
		Long personID;
		int age;
		String firstname;
		String lastname;		
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
		@JoinCondition("role='visitor'")
		public List<Person> persons;

	}
		
	@Test
	public void testWhere() {
		PojoQuery<EventWithPersons> q = PojoQuery.build(EventWithPersons.class);
		
		Assert.assertEquals(
			norm(
				"SELECT\n" + 
				" `event`.eventID AS `event.eventID`,\n" + 
				" `event`.title AS `event.title`,\n" + 
				" `event`.date AS `event.date`,\n" + 
				" `persons`.personID AS `persons.personID`,\n" + 
				" `persons`.age AS `persons.age`,\n" + 
				" `persons`.firstname AS `persons.firstname`,\n" + 
				" `persons`.lastname AS `persons.lastname`\n" +
				"FROM `event`\n" + 
				" LEFT JOIN `event_person` AS `event_person` ON `event`.eventID = `event_person`.events_id AND `event_person`.role='visitor'\n" + 
				" LEFT JOIN `person` AS `persons` ON `event_person`.persons_id = `persons`.personID"), 
			norm(q.toSql()));
	}

}
