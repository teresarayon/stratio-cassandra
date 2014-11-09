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
package com.stratio.cassandra.index;

import com.google.common.collect.Lists;
import org.apache.cassandra.config.ColumnDefinition;
import org.apache.cassandra.db.*;
import org.apache.cassandra.db.composites.CellName;
import org.apache.cassandra.db.filter.ColumnSlice;
import org.apache.cassandra.db.filter.QueryFilter;
import org.apache.cassandra.db.filter.SliceQueryFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FilteredQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * {@link RowService} that manages wide rows.
 *
 * @author Andres de la Pena <adelapena@stratio.com>
 */
public class RowServiceWide extends RowService
{

    private static final Set<String> FIELDS_TO_LOAD;

    static
    {
        FIELDS_TO_LOAD = new HashSet<>();
        FIELDS_TO_LOAD.add(PartitionKeyMapper.FIELD_NAME);
        FIELDS_TO_LOAD.add(ClusteringKeyMapper.FIELD_NAME);
    }

    private final RowMapperWide rowMapper;

    /**
     * Returns a new {@code RowServiceWide} for manage wide rows.
     *
     * @param baseCfs          The base column family store.
     * @param columnDefinition The indexed column definition.
     */
    public RowServiceWide(ColumnFamilyStore baseCfs, ColumnDefinition columnDefinition) throws IOException
    {
        super(baseCfs, columnDefinition);
        this.rowMapper = (RowMapperWide) super.rowMapper;
        luceneIndex.init(rowMapper.sort());
    }

    /**
     * {@inheritDoc}
     * <p/>
     * These fields are the partition and clustering keys.
     */
    @Override
    public Set<String> fieldsToLoad()
    {
        return FIELDS_TO_LOAD;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void indexInner(ByteBuffer key, ColumnFamily columnFamily, long timestamp) throws IOException
    {
        DeletionInfo deletionInfo = columnFamily.deletionInfo();
        DecoratedKey partitionKey = rowMapper.partitionKey(key);

        if (columnFamily.iterator().hasNext())
        {
            List<CellName> clusteringKeys = rowMapper.clusteringKeys(columnFamily);
            Map<CellName, Row> rows = rows(partitionKey, clusteringKeys, timestamp);
            for (Map.Entry<CellName, Row> entry : rows.entrySet())
            {
                CellName clusteringKey = entry.getKey();
                Row row = entry.getValue();
                Document document = rowMapper.document(row);
                Term term = rowMapper.term(partitionKey, clusteringKey);
                luceneIndex.upsert(term, document); // Store document
            }
        }
        else if (deletionInfo != null)
        {
            Iterator<RangeTombstone> iterator = deletionInfo.rangeIterator();
            if (iterator.hasNext())
            {
                while (iterator.hasNext())
                {
                    RangeTombstone rangeTombstone = iterator.next();
                    Query rangeTombstoneQuery = rowMapper.query(rangeTombstone);
                    Query partitionKeyQuery = rowMapper.query(partitionKey);
                    Query query = new FilteredQuery(partitionKeyQuery, new QueryWrapperFilter(rangeTombstoneQuery));
                    luceneIndex.delete(query);
                }
            }
            else
            {
                Term term = rowMapper.term(partitionKey);
                luceneIndex.delete(term);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteInner(DecoratedKey partitionKey) throws IOException
    {
        Term term = rowMapper.term(partitionKey);
        luceneIndex.delete(term);
    }

    /**
     * {@inheritDoc}
     * <p/>
     * The {@link Row} is a logical one.
     */
    @Override
    protected List<Row> rows(List<SearchResult> searchResults, long timestamp)
    {

        // Group key queries by partition keys
        Map<DecoratedKey, Map<CellName, Float>> scores = new LinkedHashMap<>();
        Map<DecoratedKey, List<CellName>> keys = new LinkedHashMap<>();
        Integer count = 0;
        for (SearchResult searchResult : searchResults)
        {
            DecoratedKey partitionKey = searchResult.getPartitionKey();
            CellName clusteringKey = searchResult.getClusteringKey();
            List<CellName> clusteringKeys = keys.get(partitionKey);
            if (clusteringKeys == null)
            {
                clusteringKeys = new ArrayList<>();
                keys.put(partitionKey, clusteringKeys);
            }
            clusteringKeys.add(clusteringKey);

            Float score = searchResult.getScore();
            Map<CellName, Float> scoresByCellName = scores.get(partitionKey);
            if (scoresByCellName == null)
            {
                scoresByCellName = new LinkedHashMap<>();
                scores.put(partitionKey, scoresByCellName);
            }
            scoresByCellName.put(clusteringKey, score);
        }

        // Read rows from C* in fixed-size slices
        List<Row> rows = new ArrayList<>(searchResults.size());
        for (Map.Entry<DecoratedKey, List<CellName>> entry : keys.entrySet())
        {
            DecoratedKey partitionKey = entry.getKey();
            for (List<CellName> clusteringKeys : Lists.partition(entry.getValue(), 1000))
            {
                Map<CellName, Row> partitionRows = rows(partitionKey, clusteringKeys, timestamp);
                for (Map.Entry<CellName, Row> entry1 : partitionRows.entrySet())
                {
                    CellName clusteringKey = entry1.getKey();
                    Row row = entry1.getValue();
                    Float score = scores.get(partitionKey).get(clusteringKey);
                    Row scoredRow = addScoreColumn(row, timestamp, score);
                    rows.add(scoredRow);
                }
            }
        }
        return rows;
    }

    /**
     * Returns the CQL3 {@link Row} identified by the specified key pair, using the specified time stamp to ignore
     * deleted columns. The {@link Row} is retrieved from the storage engine, so it involves IO operations.
     *
     * @param partitionKey   The partition key.
     * @param clusteringKeys The clustering keys.
     * @param timestamp      The time stamp to ignore deleted columns.
     * @return The CQL3 {@link Row} identified by the specified key pair.
     */
    private Map<CellName, Row> rows(DecoratedKey partitionKey, List<CellName> clusteringKeys, long timestamp)
    {
        ColumnSlice[] slices = rowMapper.columnSlices(clusteringKeys);

        if (baseCfs.metadata.hasStaticColumns())
        {
            LinkedList<ColumnSlice> l = new LinkedList<>(Arrays.asList(slices));
            l.addFirst(baseCfs.metadata.comparator.staticPrefix().slice());
            slices = new ColumnSlice[l.size()];
            slices = l.toArray(slices);
        }

        SliceQueryFilter dataFilter = new SliceQueryFilter(slices, false, Integer.MAX_VALUE, baseCfs.metadata.clusteringColumns().size());
        QueryFilter queryFilter = new QueryFilter(partitionKey, baseCfs.name, dataFilter, timestamp);

        ColumnFamily queryColumnFamily = baseCfs.getColumnFamily(queryFilter);

        // Avoid null
        if (queryColumnFamily == null)
        {
            return null;
        }

        // Remove deleted/expired columns
        ColumnFamily cleanQueryColumnFamily = cleanExpired(queryColumnFamily, timestamp);

        // Split CQL3 row column families
        Map<CellName, ColumnFamily> columnFamilies = rowMapper.splitRows(cleanQueryColumnFamily);

        // Build and return rows
        Map<CellName, Row> rows = new HashMap<>(columnFamilies.size());
        for (Map.Entry<CellName, ColumnFamily> entry : columnFamilies.entrySet())
        {
            Row row = new Row(partitionKey, entry.getValue());
            rows.put(entry.getKey(), row);
        }
        return rows;
    }

}
