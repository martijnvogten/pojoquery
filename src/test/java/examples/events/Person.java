package examples.events;

import java.util.List;


public class Person extends PersonRecord {
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