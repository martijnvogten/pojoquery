package org.pojoquery.processor.expected;

import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

@Table("person")
public class Person {
	@Id
	public Long id;
	public String name;
}
