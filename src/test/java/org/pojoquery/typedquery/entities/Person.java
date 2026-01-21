package org.pojoquery.typedquery.entities;

import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

@Table("person")
@GenerateQuery
public class Person {
    @Id
    public Long id;
    public String name;
    public String email;
}
