package org.pojoquery.typedquery.entities;

import org.pojoquery.annotations.GenerateFluentQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

@Table("article")
@GenerateFluentQuery
public class Article {
    @Id
    public Long id;
    public String title;
    public String content;
    public Person author;
}
