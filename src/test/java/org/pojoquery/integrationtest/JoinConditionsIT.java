package org.pojoquery.integrationtest;

import static org.pojoquery.TestUtils.norm;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.TestUtils;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.JoinCondition;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

public class JoinConditionsIT {

	@BeforeClass
	public static void setupDbContext() {
		// Trigger TestDatabaseProvider static initialization to set DbContext
		TestDatabaseProvider.initDbContext();
	}

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
		LocalDateTime date;
	}
	
	static class EventWithFestival extends Event {
		@Link(linkfield="festivalID")
		Festival festival;
	}

	public static class EventWithVisitorsAndOrganizers extends Event {
		@Link(linktable="event_person", linkfield="eventID", foreignlinkfield="personID")
		@JoinCondition("{this.eventID}={linktable.eventID} AND {linktable.role}='visitor'")
		public List<Person> visitors;
		
		@Link(linktable="event_person", linkfield="eventID", foreignlinkfield="personID")
		@JoinCondition("{this.eventID}={linktable.eventID} AND {linktable.role}='organizer'")
		public List<Person> organizers;
	}
	
	@Table("festival")
	static class Festival {
		@Id
		Long festivalID;
		String name;
		@Link(foreignlinkfield="festivalID")
		List<EventWithVisitorsAndOrganizers> events;
	}
	
	@Table("festival")
	static class FestivalWithJoinConditionOnEvents {
		@Id
		Long festivalID;
		String name;
		@JoinCondition("{events.festivalID} = {this.festivalID}")
		List<Event> events;
	}
	
	@Table("event_person")
	static class EventPerson {
		@Id
		Long eventID;
		@Id
		Long personID;
		String role;
	}
	
	@Table("employee")
	static class Employee {
		@Id
		Long id;
		
		@JoinCondition("{this.department_id} = {department.id}")
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
		Assert.assertEquals(TestUtils.norm("""
			SELECT
			 "employee".id AS "employee.id",
			 "department".id AS "department.id",
			 "department".name AS "department.name"
			FROM employee AS "employee"
			 LEFT JOIN department AS "department" ON "employee".department_id = "department".id
			""".replaceAll("\"", "")), norm(sql.trim().replaceAll("`", "\"").replaceAll("\"", "")));
	}
	
	
	@Test
	public void testBasic() {
		DataSource db = initDatabase();
		
		PojoQuery<EventWithVisitorsAndOrganizers> q = PojoQuery.build(EventWithVisitorsAndOrganizers.class);
		List<EventWithVisitorsAndOrganizers> events = q.execute(db);
		Assert.assertEquals(0, events.size());
		
		DB.runInTransaction(db, (Connection c) -> {
			insertTestData(c);
		});
		
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

	private void insertTestData(Connection c) {
		Person jane = new Person();
		jane.firstname = "Jane";
		jane.lastname = "Doe";
		PojoQuery.insert(c, jane);
		
		Person stella = new Person();
		stella.firstname = "Stella";
		stella.lastname = "Smith";
		PojoQuery.insert(c, stella);
		
		Festival communic8 = new Festival();
		communic8.name = "Communic8";
		PojoQuery.insert(c, communic8);
		
		// Event on programming
		EventWithFestival conference = new EventWithFestival();
		conference.date = LocalDateTime.of(2020, 5, 15, 0, 0);
		conference.location = "Las Vegas";
		conference.festival = communic8;
		PojoQuery.insert(c, conference);
		
		// Jane is a visitor, Stella is the organizer
		DB.insert(c, "event_person", Map.of("eventID", conference.eventID, "personID", jane.personID, "role", "visitor"));
		DB.insert(c, "event_person", Map.of("eventID", conference.eventID, "personID", stella.personID, "role", "organizer"));
	}
	
	@Test
	public void testDeeper() {
		DataSource db = initDatabase();
		DB.runInTransaction(db, (Connection c) -> {
			insertTestData(c);
		});
		
		PojoQuery<Festival> q = PojoQuery.build(Festival.class);
		System.out.println(q.toSql());
		List<Festival> festivals = q.execute(db);
		
		List<EventWithVisitorsAndOrganizers> events = festivals.get(0).events;
		Assert.assertEquals(1, events.size());
		
	}
	
	private static DataSource initDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, Person.class, Event.class, Festival.class, EventPerson.class);
		return db;
	}

}
