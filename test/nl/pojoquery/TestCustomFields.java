package nl.pojoquery;

import static nl.pojoquery.TestUtils.norm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.pojoquery.annotations.Table;
import nl.pojoquery.pipeline.Alias;
import nl.pojoquery.pipeline.QueryBuilder;

import org.junit.Assert;
import org.junit.Test;

public class TestCustomFields {

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
	public void testBasics() {
		QueryBuilder<User> p = QueryBuilder.from(User.class);
		
		for(Alias a : p.getAliases().values()) {
			if (User.class.equals(a.getResultClass())) {
				p.getQuery().addField(new SqlExpression("{" + a.getAlias() + "}.custom_linkedInUrl"), a.getAlias() + ".linkedInUrl");
				p.getFieldMappings().put(a.getAlias() + ".linkedInUrl", new FieldMapping() {
					@Override
					public void apply(Object targetEntity, Object value) {
						User u = (User)targetEntity;
						if (u.customFields == null) {
							u.customFields = new HashMap<>();
						}
						u.customFields.put("linkedInUrl", value);
					}});
			}
		}
		
		Assert.assertEquals(norm("SELECT" +
				" `user`.id AS `user.id`," +
				" `user`.email AS `user.email`," +
				" `user`.custom_linkedInUrl AS `user.linkedInUrl` " +
				"FROM `user` AS `user`"), norm(p.getQuery().toStatement().getSql()));
		
		List<Map<String,Object>> resultSet = TestUtils.resultSet(new String[] 
				{"user.id", "user.email",     "user.linkedInUrl"}, 
				  1L,       "john@ewbank.nl", "http://www.linkedin.com/123456");
		
		List<User> users = p.processRows(resultSet);
		Assert.assertEquals("http://www.linkedin.com/123456", users.get(0).getCustomValue("linkedInUrl"));
	}

}
