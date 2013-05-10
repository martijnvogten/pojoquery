package examples.blog;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;

@Table("article")
public class Article {
	@Id
	public Long id;
	public String title;
	public String content;
}
