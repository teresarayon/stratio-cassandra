/*
 * Copyright 2014, Stratio.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stratio.cassandra.index.query;

import com.stratio.cassandra.index.schema.*;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static com.stratio.cassandra.index.query.builder.SearchBuilders.prefix;
import static com.stratio.cassandra.index.query.builder.SearchBuilders.query;

/**
 * @author Andres de la Pena <adelapena@stratio.com>
 */
public class PrefixConditionTest extends AbstractConditionTest
{

    @Test
    public void testString()
    {

        Map<String, ColumnMapper<?>> map = new HashMap<>();
        map.put("name", new ColumnMapperString());
        Schema mappers = new Schema(EnglishAnalyzer.class.getName(), map);

        PrefixCondition prefixCondition = new PrefixCondition(0.5f, "name", "tr");
        Query query = prefixCondition.query(mappers);

        Assert.assertNotNull(query);
        Assert.assertEquals(PrefixQuery.class, query.getClass());
        PrefixQuery luceneQuery = (PrefixQuery) query;
        Assert.assertEquals("name", luceneQuery.getField());
        Assert.assertEquals("tr", luceneQuery.getPrefix().text());
        Assert.assertEquals(0.5f, query.getBoost(), 0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testInteger()
    {

        Map<String, ColumnMapper<?>> map = new HashMap<>();
        map.put("name", new ColumnMapperInteger(1f));
        Schema mappers = new Schema(EnglishAnalyzer.class.getName(), map);

        PrefixCondition prefixCondition = new PrefixCondition(0.5f, "name", "2*");
        prefixCondition.query(mappers);
    }

    @Test
    public void testInetV4()
    {

        Map<String, ColumnMapper<?>> map = new HashMap<>();
        map.put("name", new ColumnMapperInet());
        Schema mappers = new Schema(EnglishAnalyzer.class.getName(), map);

        PrefixCondition wildcardCondition = new PrefixCondition(0.5f, "name", "192.168.");
        Query query = wildcardCondition.query(mappers);

        Assert.assertNotNull(query);
        Assert.assertEquals(PrefixQuery.class, query.getClass());
        PrefixQuery luceneQuery = (PrefixQuery) query;
        Assert.assertEquals("name", luceneQuery.getField());
        Assert.assertEquals("192.168.", luceneQuery.getPrefix().text());
        Assert.assertEquals(0.5f, query.getBoost(), 0);
    }

    @Test
    public void testInetV6()
    {

        Map<String, ColumnMapper<?>> map = new HashMap<>();
        map.put("name", new ColumnMapperInet());
        Schema mappers = new Schema(EnglishAnalyzer.class.getName(), map);

        PrefixCondition wildcardCondition = new PrefixCondition(0.5f, "name", "2001:db8:2de:0:0:0:0:e");
        Query query = wildcardCondition.query(mappers);

        Assert.assertNotNull(query);
        Assert.assertEquals(PrefixQuery.class, query.getClass());
        PrefixQuery luceneQuery = (PrefixQuery) query;
        Assert.assertEquals("name", luceneQuery.getField());
        Assert.assertEquals("2001:db8:2de:0:0:0:0:e", luceneQuery.getPrefix().text());
        Assert.assertEquals(0.5f, query.getBoost(), 0);
    }

    @Test
    public void testJson()
    {

        testJsonCondition(query(prefix("name", "aaa").boost(0.5f)));
    }

}
