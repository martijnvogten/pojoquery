package events;

import java.util.List;

import system.sql.annotations.Link;
import system.sql.annotations.Table;

@Table("person")
public class Person extends PersonRecord {
	@Link(resultClass = EmailAddress.class)
	private List<EmailAddress> emailAddresses;

	public List<EmailAddress> getEmailAddresses() {
		return emailAddresses;
	}

	public void setEmailAddresses(List<EmailAddress> emailAddresses) {
		this.emailAddresses = emailAddresses;
	}

}