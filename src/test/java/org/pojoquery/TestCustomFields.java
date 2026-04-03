package org.pojoquery;

import static org.pojoquery.TestUtils.norm;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Other;
import org.pojoquery.annotations.Table;
import org.pojoquery.pipeline.AQTTransformer;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;

public class TestCustomFields {

	@BeforeEach
	public void setup() {
		DbContext.setDefault(DbContext.forDialect(Dialect.MYSQL));
	}
	

	@Table("user")
	static class User {
		Long id;
		String email;
		
		@Other
		Map<String,Object> customFields;
		
		public Object getCustomValue(String field) {
			return customFields.get(field);
		}
	}
	
	@Test
	public void testBasics() {
		RootNode tree = AQTTransformer.buildQueryTreeForType(User.class);
		

		PojoQuery<User> q = PojoQuery.build(User.class);
		q.addField(new SqlExpression("{" + tree.alias() + "}.custom_linkedInUrl"), tree.alias() + ".linkedInUrl");


		Assert.assertEquals(norm("""
			SELECT
			 `user`.`id` AS `user.id`,
			 `user`.`email` AS `user.email`,
			 `user`.custom_linkedInUrl AS `user.linkedInUrl` 
			FROM `user` AS `user`
			"""), norm(q.getQuery().toStatement().getSql()));
		
		List<Map<String,Object>> resultSet = TestUtils.resultSet(new String[] 
				{"user.id", "user.email",     "user.linkedInUrl"}, 
				  1L,       "john@ewbank.nl", "http://www.linkedin.com/123456");
		
		List<User> users = q.processRows(resultSet);
		Assert.assertEquals("http://www.linkedin.com/123456", users.get(0).getCustomValue("linkedInUrl"));
	}

}
