package org.pojoquery.integrationtest;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.Assert;
import org.junit.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.JoinCondition;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabase;

public class ForeignValueFieldsIT {
	
	@Table("poule")
	static class Poule {

		@Id 
		Long id;	
		
		@Link(linktable="poule_weightclass", fetchColumn="weightclass")
		Integer[] weightClasses;
	}

	@Table("poule")
	static class PouleWithHeavyWeights {

		@Id 
		Long id;	
		
		@Link(linktable="poule_weightclass", fetchColumn="weightclass")
		@JoinCondition("{this}.id = {linktable}.poule_id AND {linktable}.weightClass > -32")
		Integer[] weightClasses;
	}

	@Test
	public void testUpdates() {
		DataSource db = TestDatabase.dropAndRecreate();
		insertTestData(db);
		
		PojoQuery<Poule> query = PojoQuery.build(Poule.class)
				.addWhere("id = ? ", 1);
		
		List<Poule> result = query.execute(db);
		Assert.assertEquals(1, result.size());
		Assert.assertEquals(3, result.get(0).weightClasses.length);
	}
	
	@Test
	public void testJoinCondition() {
		DataSource db = TestDatabase.dropAndRecreate();
		insertTestData(db);
		
		PojoQuery<PouleWithHeavyWeights> query = PojoQuery.build(PouleWithHeavyWeights.class)
				.addWhere("id = ? ", 1);
		
		List<PouleWithHeavyWeights> result = query.execute(db);
		Assert.assertEquals(1, result.get(0).weightClasses.length);
	}

	private void insertTestData(DataSource db) {
		DB.executeDDL(db, "CREATE TABLE poule (id BIGINT NOT NULL AUTO_INCREMENT, PRIMARY KEY (id));");
		DB.executeDDL(db, "CREATE TABLE poule_weightclass (poule_id BIGINT NOT NULL, weightClass INT, PRIMARY KEY (poule_id, weightClass));");

		Poule p = new Poule();
		PojoQuery.insert(db, p);
		Assert.assertEquals((Long)1L, p.id);
		
		DB.insert(db, "poule_weightclass", Map.of("poule_id", 1, "weightClass", -30));
		DB.insert(db, "poule_weightclass", Map.of("poule_id", 1, "weightClass", -32));
		DB.insert(db, "poule_weightclass", Map.of("poule_id", 1, "weightClass", -34));
	}
	
}