package events;

import java.util.Date;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;


@Table("event")
public class Event {
	@Id
	private Long id;
	
	private String title;
	private Date date;

	public Event() {
	}

	public Long getId() {
		return id;
	}

	@SuppressWarnings("unused")
	private void setId(Long id) {
		this.id = id;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	@Override
	public String toString() {
		return "Event [id=" + id + ", title=" + title + ", date=" + date + "]";
	}
}