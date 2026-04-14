package org.pojoquery;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.pojoquery.annotations.Aggregate;
import org.pojoquery.annotations.Table;

@ExtendWith(org.pojoquery.integrationtest.DbContextExtension.class)
public class TestAutoGroupBy {

	@BeforeEach
	public void setUp() {
		DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.MYSQL));
	}

	@Table("wordindex")
	static class WordCount {
		String word;
		
		@Aggregate("COUNT(*)")
		Long wordCount;
	}
	
	@Test
	public void testAutoGroupByWithAggregate() {
		PojoQuery<WordCount> q = PojoQuery.build(WordCount.class);
		
		Assertions.assertEquals(TestUtils.norm("""
			SELECT
			 `wordindex`.`word` AS `wordindex.word`,
			 COUNT(*) AS `wordindex.wordCount`
			FROM `wordindex` AS `wordindex`
			GROUP BY `wordindex`.`word`
			"""), TestUtils.norm(q.toStatement().getSql()));
	}

	@Table("order_item")
	static class OrderSummary {
		Long orderId;
		
		@Aggregate("COUNT(*)")
		Long itemCount;
		
		@Aggregate("SUM({order_item.price})")
		java.math.BigDecimal totalPrice;
	}
	
	@Test
	public void testAutoGroupByMultipleAggregates() {
		PojoQuery<OrderSummary> q = PojoQuery.build(OrderSummary.class);
		
		Assertions.assertEquals(TestUtils.norm("""
			SELECT
			 `order_item`.`orderId` AS `order_item.orderId`,
			 COUNT(*) AS `order_item.itemCount`,
			 SUM(`order_item`.`price`) AS `order_item.totalPrice`
			FROM `order_item` AS `order_item`
			GROUP BY `order_item`.`orderId`
			"""), TestUtils.norm(q.toStatement().getSql()));
	}
}
