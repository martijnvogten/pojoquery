package nl.pojoquery.integrationtest;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import javax.sql.DataSource;

import org.junit.Assert;
import org.junit.Test;

import nl.pojoquery.DB;
import nl.pojoquery.PojoQuery;
import nl.pojoquery.TestUtils;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.JoinCondition;
import nl.pojoquery.annotations.Link;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.integrationtest.db.TestDatabase;

public class TestJoinConditions {

	@Table("person")
	static class Person {
		@Id
		Long personID;
		String firstname;
		String lastname;		
	}
	
	@Table("event")
	static class Event {
		@Id
		Long eventID;
		String title;
		String location;
		Date date;
	}
	
	static class EventWithFestival extends Event {
		@Link(linkfield="festivalID")
		Festival festival;
	}

	public static class EventWithVisitorsAndOrganizers extends Event {
		@Link(linktable="event_person", linkfield="eventID", foreignlinkfield="personID")
		@JoinCondition("{this}.eventID={linktable}.eventID AND {linktable}.role='visitor'")
		public List<Person> visitors;
		
		@Link(linktable="event_person", linkfield="eventID", foreignlinkfield="personID")
		@JoinCondition("{this}.eventID={linktable}.eventID AND {linktable}.role='organizer'")
		public List<Person> organizers;
	}
	
	@Table("festival")
	static class Festival {
		@Id
		Long festivalId;
		String name;
		@Link(foreignlinkfield="festivalID")
		List<EventWithVisitorsAndOrganizers> events;
	}
	
	@Table("festival")
	static class FestivalWithJoinConditionOnEvents {
		@Id
		Long festivalId;
		String name;
		@JoinCondition("{events}.festivalId = {this}.festivalId")
		List<Event> events;
	}
	
	@Table("employee")
	static class Employee {
		@Id
		Long id;
		
		@JoinCondition("{this}.department_id = {department}.id")
		Department department;
	}
	
	@Table("department")
	static class Department {
		@Id
		Long id;
		
		String name;
	}
	
	@Test
	public void testSimpleDepartmentEmployeeJoinCondition() {
		PojoQuery<Employee> q = PojoQuery.build(Employee.class);
		String sql = q.toSql();
		Assert.assertEquals("SELECT\n" + 
				" `employee`.id AS `employee.id`,\n" + 
				" `department`.id AS `department.id`,\n" + 
				" `department`.name AS `department.name` \n" + 
				"FROM `employee` \n" + 
				" LEFT JOIN `department` AS `department` ON `employee`.department_id = `department`.id", sql.trim());
	}
	
	
	@Test
	public void testBasic() {
		DataSource db = initDatabase();
		
		PojoQuery<EventWithVisitorsAndOrganizers> q = PojoQuery.build(EventWithVisitorsAndOrganizers.class);
		List<EventWithVisitorsAndOrganizers> events = q.execute(db);
		Assert.assertEquals(0, events.size());
		
		insertTestData(db);
		
		List<EventWithVisitorsAndOrganizers> eventList = q.execute(db);
		Assert.assertEquals(1, eventList.get(0).visitors.size());
		Assert.assertEquals("Jane", eventList.get(0).visitors.get(0).firstname);
		
		Assert.assertEquals(1, eventList.get(0).organizers.size());
		Assert.assertEquals("Stella", eventList.get(0).organizers.get(0).firstname);
	}
	
	@Test
	public void FestivalWithJoinConditionOnEvents() {
		DataSource db = initDatabase();
		PojoQuery.build(FestivalWithJoinConditionOnEvents.class).execute(db);
	}

	private void insertTestData(DataSource db) {
		Person jane = new Person();
		jane.firstname = "Jane";
		jane.lastname = "Doe";
		PojoQuery.insert(db, jane);
		
		Person stella = new Person();
		stella.firstname = "Stella";
		stella.lastname = "Smith";
		PojoQuery.insert(db, stella);
		
		Festival communic8 = new Festival();
		communic8.name = "Communic8";
		PojoQuery.insert(db, communic8);
		
		// Event on programming
		EventWithFestival conference = new EventWithFestival();
		conference.date = new GregorianCalendar(2020, 4, 15).getTime();
		conference.location = "Las Vegas";
		conference.festival = communic8;
		PojoQuery.insert(db, conference);
		
		// Jane is a visitor, Stella is the organizer
		DB.insert(db, "event_person", TestUtils.map("eventID", conference.eventID, "personID", jane.personID, "role", "visitor"));
		DB.insert(db, "event_person", TestUtils.map("eventID", conference.eventID, "personID", stella.personID, "role", "organizer"));
	}
	
	@Test
	public void testDeeper() {
		DataSource db = initDatabase();
		insertTestData(db);
		
		PojoQuery<Festival> q = PojoQuery.build(Festival.class);
		System.out.println(q.toSql());
		List<Festival> festivals = q.execute(db);
		
		List<EventWithVisitorsAndOrganizers> events = festivals.get(0).events;
		Assert.assertEquals(1, events.size());
		
	}
	
	private static DataSource initDatabase() {
		DataSource db = TestDatabase.dropAndRecreate();
		DB.executeDDL(db, "CREATE TABLE person (personID BIGINT NOT NULL AUTO_INCREMENT, firstname VARCHAR(1023), lastname VARCHAR(1023), PRIMARY KEY (personID))");
		DB.executeDDL(db, "CREATE TABLE event (eventID BIGINT NOT NULL AUTO_INCREMENT, festivalID BIGINT, title VARCHAR(1023), date DATE, location VARCHAR(1023), PRIMARY KEY (eventID))");
		DB.executeDDL(db, "CREATE TABLE festival (festivalID BIGINT NOT NULL AUTO_INCREMENT, name VARCHAR(1023), PRIMARY KEY (festivalID))");
		DB.executeDDL(db, "CREATE TABLE event_person (eventID BIGINT NOT NULL, personID BIGINT NOT NULL, role VARCHAR(1023), PRIMARY KEY (eventID, personID))");
		return db;
	}

}
