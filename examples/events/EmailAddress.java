package events;

import nl.pojoquery.annotations.Table;

@Table("emailaddress")
public class EmailAddress {
	private Long person_id;
	private String email;
	private String name;
	
	public Long getPerson_id() {
		return person_id;
	}

	public void setPerson_id(Long person_id) {
		this.person_id = person_id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return "EmailAddress [person_id=" + person_id + ", email=" + email
				+ ", name=" + name + "]";
	}

}
