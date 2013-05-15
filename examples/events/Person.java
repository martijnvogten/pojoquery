package events;

import java.util.List;

import nl.pojoquery.annotations.Link;
import nl.pojoquery.annotations.Table;


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

	public String getFullName() {
		return getFirstname() + " " + getLastname();
	}

}