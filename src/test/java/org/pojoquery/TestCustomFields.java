package org.pojoquery;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Table;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.QueryTreeBuilder;

public class TestCustomFields {

	@BeforeEach
	public void setup() {
		DbContext.setDefault(DbContext.forDialect(Dialect.MYSQL));
	}
	

	@Table("user")
	static class User {
		Long id;
		String email;
		
		transient Map<String,Object> customFields;
		
		public Object getCustomValue(String field) {
			return customFields.get(field);
		}
	}
	
	@Test
	@Disabled("refactor to query tree")
	public void testBasics() {
		QueryTree q = QueryTreeBuilder.from(User.class);
		
		// for(Alias a : q.getAliases().values()) {
		// 	if (User.class.equals(a.getResultClass())) {
		// 		q.getQuery().addField(new SqlExpression("{" + a.getAlias() + "}.custom_linkedInUrl"), a.getAlias() + ".linkedInUrl");
		// 		q.getFieldMappings().put(a.getAlias() + ".linkedInUrl", new FieldMapping() {
		// 			@Override
		// 			public void apply(Object targetEntity, Object value) {
		// 				User u = (User)targetEntity;
		// 				if (u.customFields == null) {
		// 					u.customFields = new HashMap<>();
		// 				}
		// 				u.customFields.put("linkedInUrl", value);
		// 			}});
		// 	}
		// }
		
		// Assertions.assertEquals(norm("""
		// 	SELECT
		// 	 `user`.`id` AS `user.id`,
		// 	 `user`.`email` AS `user.email`,
		// 	 `user`.custom_linkedInUrl AS `user.linkedInUrl` 
		// 	FROM `user` AS `user`
		// 	"""), norm(q.getQuery().toStatement().getSql()));
		
		// List<Map<String,Object>> resultSet = TestUtils.resultSet(new String[] 
		// 		{"user.id", "user.email",     "user.linkedInUrl"}, 
		// 		  1L,       "john@ewbank.nl", "http://www.linkedin.com/123456");
		
		// List<User> users = q.processRows(resultSet);
		// Assertions.assertEquals("http://www.linkedin.com/123456", users.get(0).getCustomValue("linkedInUrl"));
	}

}
