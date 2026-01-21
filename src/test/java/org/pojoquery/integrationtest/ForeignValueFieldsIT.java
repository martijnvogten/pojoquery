package org.pojoquery.integrationtest;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.JoinCondition;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

public class ForeignValueFieldsIT {
	
	@Table("poule")
	static class Poule {

		@Id 
		Long id;	
		
		@Link(linktable="poule_weightclass", fetchColumn="weightclass")
		Integer[] weightClasses;
	}

	@Table("poule_weightclass")
	static class PouleWeightClass {
		@Id
		Long poule_id;
		@Id
		Integer weightclass;
	}

	@Table("poule")
	static class PouleWithHeavyWeights {

		@Id 
		Long id;	
		
		@Link(linktable="poule_weightclass", fetchColumn="weightclass")
		@JoinCondition("{this}.id = {linktable}.poule_id AND {linktable}.weightclass > -32")
		Integer[] weightClasses;
	}

	@Test
	public void testUpdates() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, Poule.class, PouleWeightClass.class);
		
		DB.withConnection(db, (Connection c) -> {
			insertTestData(c);
			
			PojoQuery<Poule> query = PojoQuery.build(Poule.class)
					.addWhere("id = ? ", 1);
			
			List<Poule> result = query.execute(c);
			Assertions.assertEquals(1, result.size());
			Assertions.assertEquals(3, result.get(0).weightClasses.length);
		});
	}
	
	@Test
	public void testJoinCondition() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, Poule.class, PouleWeightClass.class);
		
		DB.withConnection(db, (Connection c) -> {
			insertTestData(c);
			
			PojoQuery<PouleWithHeavyWeights> query = PojoQuery.build(PouleWithHeavyWeights.class)
					.addWhere("id = ? ", 1);
			
			List<PouleWithHeavyWeights> result = query.execute(c);
			Assertions.assertEquals(1, result.get(0).weightClasses.length);
		});
	}

	private void insertTestData(Connection c) {
		Poule p = new Poule();
		PojoQuery.insert(c, p);
		Assertions.assertEquals((Long)1L, p.id);
		
		DB.insert(c, "poule_weightclass", Map.of("poule_id", 1, "weightclass", -30));
		DB.insert(c, "poule_weightclass", Map.of("poule_id", 1, "weightclass", -32));
		DB.insert(c, "poule_weightclass", Map.of("poule_id", 1, "weightclass", -34));
	}
	
}