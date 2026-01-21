package org.pojoquery.typedquery.entities;

import org.pojoquery.annotations.GenerateFluentQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

@Table("person")
@GenerateFluentQuery
public class Person {
    @Id
    public Long id;
    public String name;
    public String email;
}
