package nl.pojoquery.integrationtest;

import java.util.List;

import javax.sql.DataSource;

import org.junit.Assert;
import org.junit.Test;

import nl.pojoquery.DB;
import nl.pojoquery.PojoQuery;
import nl.pojoquery.TestUtils;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Link;
import nl.pojoquery.annotations.Table;

public class TestForeignValueFields {
	
	@Table("poule")
	static class Poule {

		@Id 
		Long id;	
		
		@Link(linktable="poule_weightclass", fetchColumn="weightclass")
		Integer[] weightClasses;
	}

	@Test
	public void testUpdates() {
		DataSource db = MysqlDatabases.createDatabase("localhost", "pojoquery_integrationtest", "root", "");
		DB.executeDDL(db, "CREATE TABLE poule (id BIGINT NOT NULL AUTO_INCREMENT, PRIMARY KEY (id));");
		DB.executeDDL(db, "CREATE TABLE poule_weightclass (poule_id BIGINT NOT NULL, weightClass INT, PRIMARY KEY (poule_id, weightClass));");

		Poule p = new Poule();
		PojoQuery.insert(db, p);
		Assert.assertEquals((Long)1L, p.id);
		
		DB.insert(db, "poule_weightclass", TestUtils.map("poule_id", 1, "weightClass", -30));
		DB.insert(db, "poule_weightclass", TestUtils.map("poule_id", 1, "weightClass", -32));
		DB.insert(db, "poule_weightclass", TestUtils.map("poule_id", 1, "weightClass", -34));
		
		PojoQuery<Poule> query = PojoQuery.build(Poule.class)
				.addWhere("id = ? ", 1);
		
		List<Poule> result = query.execute(db);
		Assert.assertEquals(1, result.size());
		Assert.assertEquals(3, result.get(0).weightClasses.length);
	}
	
}