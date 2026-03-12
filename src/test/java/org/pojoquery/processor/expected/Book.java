package org.pojoquery.processor.expected;

import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

@Table("book")
public class Book {
	@Id
	public Long id;
	public String title;
	public Person author;
}
