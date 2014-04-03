package org.apache.cassandra.db.index.stratio.query;

import java.util.HashMap;
import java.util.Map;

import org.apache.cassandra.db.index.stratio.schema.CellMapper;
import org.apache.cassandra.db.index.stratio.schema.CellMapperDouble;
import org.apache.cassandra.db.index.stratio.schema.CellMapperFloat;
import org.apache.cassandra.db.index.stratio.schema.CellMapperInteger;
import org.apache.cassandra.db.index.stratio.schema.CellMapperLong;
import org.apache.cassandra.db.index.stratio.schema.CellMapperString;
import org.apache.cassandra.db.index.stratio.schema.CellsMapper;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.junit.Assert;
import org.junit.Test;

public class MatchQueryTest {

	@Test
	public void testString() {

		Map<String, CellMapper<?>> map = new HashMap<>();
		map.put("name", new CellMapperString());
		CellsMapper mappers = new CellsMapper(EnglishAnalyzer.class.getName(), map);

		MatchCondition matchCondition = new MatchCondition(0.5f, "name", "casa");
		Query query = matchCondition.query(mappers);

		Assert.assertNotNull(query);
		Assert.assertEquals(TermQuery.class, query.getClass());
		Assert.assertEquals("casa", ((TermQuery) query).getTerm().bytes().utf8ToString());
		Assert.assertEquals(0.5f, query.getBoost(), 0);
	}

	@Test
	public void testInteger() {

		Map<String, CellMapper<?>> map = new HashMap<>();
		map.put("name", new CellMapperInteger(1f));
		CellsMapper mappers = new CellsMapper(EnglishAnalyzer.class.getName(), map);

		MatchCondition matchCondition = new MatchCondition(0.5f, "name", 42);
		Query query = matchCondition.query(mappers);

		Assert.assertNotNull(query);
		Assert.assertEquals(NumericRangeQuery.class, query.getClass());
		Assert.assertEquals(42, ((NumericRangeQuery<?>) query).getMin());
		Assert.assertEquals(42, ((NumericRangeQuery<?>) query).getMax());
		Assert.assertEquals(true, ((NumericRangeQuery<?>) query).includesMin());
		Assert.assertEquals(true, ((NumericRangeQuery<?>) query).includesMax());
		Assert.assertEquals(0.5f, query.getBoost(), 0);
	}

	@Test
	public void testLong() {

		Map<String, CellMapper<?>> map = new HashMap<>();
		map.put("name", new CellMapperLong(1f));
		CellsMapper mappers = new CellsMapper(EnglishAnalyzer.class.getName(), map);

		MatchCondition matchCondition = new MatchCondition(0.5f, "name", 42L);
		Query query = matchCondition.query(mappers);

		Assert.assertNotNull(query);
		Assert.assertEquals(NumericRangeQuery.class, query.getClass());
		Assert.assertEquals(42L, ((NumericRangeQuery<?>) query).getMin());
		Assert.assertEquals(42L, ((NumericRangeQuery<?>) query).getMax());
		Assert.assertEquals(true, ((NumericRangeQuery<?>) query).includesMin());
		Assert.assertEquals(true, ((NumericRangeQuery<?>) query).includesMax());
		Assert.assertEquals(0.5f, query.getBoost(), 0);
	}

	@Test
	public void testFloat() {

		Map<String, CellMapper<?>> map = new HashMap<>();
		map.put("name", new CellMapperFloat(1f));
		CellsMapper mappers = new CellsMapper(EnglishAnalyzer.class.getName(), map);

		MatchCondition matchCondition = new MatchCondition(0.5f, "name", 42.42F);
		Query query = matchCondition.query(mappers);

		Assert.assertNotNull(query);
		Assert.assertEquals(NumericRangeQuery.class, query.getClass());
		Assert.assertEquals(42.42F, ((NumericRangeQuery<?>) query).getMin());
		Assert.assertEquals(42.42F, ((NumericRangeQuery<?>) query).getMax());
		Assert.assertEquals(true, ((NumericRangeQuery<?>) query).includesMin());
		Assert.assertEquals(true, ((NumericRangeQuery<?>) query).includesMax());
		Assert.assertEquals(0.5f, query.getBoost(), 0);
	}

	@Test
	public void testDouble() {

		Map<String, CellMapper<?>> map = new HashMap<>();
		map.put("name", new CellMapperDouble(1f));
		CellsMapper mappers = new CellsMapper(EnglishAnalyzer.class.getName(), map);

		MatchCondition matchCondition = new MatchCondition(0.5f, "name", 42.42D);
		Query query = matchCondition.query(mappers);

		Assert.assertNotNull(query);
		Assert.assertEquals(NumericRangeQuery.class, query.getClass());
		Assert.assertEquals(42.42D, ((NumericRangeQuery<?>) query).getMin());
		Assert.assertEquals(42.42D, ((NumericRangeQuery<?>) query).getMax());
		Assert.assertEquals(true, ((NumericRangeQuery<?>) query).includesMin());
		Assert.assertEquals(true, ((NumericRangeQuery<?>) query).includesMax());
		Assert.assertEquals(0.5f, query.getBoost(), 0);
	}

}