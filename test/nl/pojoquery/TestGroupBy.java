package nl.pojoquery;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import nl.pojoquery.annotations.GroupBy;
import nl.pojoquery.annotations.Select;
import nl.pojoquery.annotations.Table;

public class TestGroupBy {

	@Table("wordindex")
	@GroupBy("wordindex.word")
	static class WordCount {
		String word;
		
		@Select("COUNT(*)")
		Long wordCount;
	}
	
	@Test
	public void testSql() {
		PojoQuery<WordCount> q = PojoQuery.build(WordCount.class);
		Assert.assertEquals(TestUtils.norm("SELECT\n" + 
				" `wordindex`.word AS `wordindex.word`,\n" + 
				" COUNT(*) AS `wordindex.wordCount`\n" + 
				"FROM wordindex\n" + 
				"GROUP BY wordindex.word"), TestUtils.norm(q.toStatement().getSql()));
		
		List<Map<String, Object>> result = TestUtils.resultSet(new String[] {
			"wordindex.word", "wordindex.wordCount" } 
		    ,"world"  	    ,  3L
		    ,"hello"   	    ,  1L       
		     );

		List<WordCount> rows = q.processRows(result);
		Assert.assertEquals(2, rows.size());
	}
}
