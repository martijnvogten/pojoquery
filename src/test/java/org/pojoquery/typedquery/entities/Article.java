package org.pojoquery.typedquery.entities;

import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

@Table("article")
@GenerateQuery
public class Article {
    @Id
    @FieldName("article_id")
    public Long id;
    @FieldName("article_title")
    public String title;
    public String content;
    public Person author;
}
