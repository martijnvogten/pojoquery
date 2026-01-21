package org.pojoquery.typedquery.entities;

import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

@Table("article")
@GenerateQuery
public class Article {
    @Id
    public Long id;
    public String title;
    public String content;
    public Person author;
}
