package nl.pojoquery;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Link;
import nl.pojoquery.annotations.Table;

public class TestQueryBuilder {
	
	@Table("article")
	static class Article {
		Long id;
	}
	
	@Test
	public void testBasicSql() {
		assertEquals("SELECT `article`.id `article.id` FROM article", TestUtils.norm(PojoQuery.build(Article.class).toSql()));
	}
	
	@Table("order_item")
	static class Item {
		@Id
		Long id;
		Long productId;
	}
	
	@Table("shipping_package")
	static class Package {
		@Id
		Long id;
	}
	
	static class PackageDetail extends Package {
		@Link(linktable="shipping_package_order_item", resultClass=Item.class)
		List<Item> items;
	}
	
	@Test
	public void testJoinTablesWithUnderscores() {
		assertEquals(
				"SELECT "
				+ "`shipping_package`.id `shipping_package.id`, "
				+ "`items`.id `items.id`, "
				+ "`items`.productId `items.productId` "
				+ "FROM shipping_package "
				+ "LEFT JOIN shipping_package_order_item `shipping_package_order_item` "
				+ "ON `shipping_package_order_item`.shipping_package_id=shipping_package.id "
				+ "LEFT JOIN order_item items "
				+ "ON `shipping_package_order_item`.order_item_id=`items`.id", 
				TestUtils.norm(PojoQuery.build(PackageDetail.class).toSql()));
	}
}