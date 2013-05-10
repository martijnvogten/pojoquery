package examples.blog;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;

@Table("user")
public class User {
	@Id
	public Long id;
	public String firstName;
	public String lastName;
	public String email;
	
	public String getFullName() {
		return (firstName + " " + lastName).trim();
	}
}
