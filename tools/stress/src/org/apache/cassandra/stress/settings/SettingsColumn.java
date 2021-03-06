package org.apache.cassandra.stress.settings;
/*
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 */


import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.cassandra.db.marshal.*;
import org.apache.cassandra.stress.generate.*;
import org.apache.cassandra.utils.ByteBufferUtil;

/**
 * For parsing column options
 */
public class SettingsColumn implements Serializable
{

    public final int maxColumnsPerKey;
    public transient final List<ByteBuffer> names;
    public final List<String> namestrs;
    public final String comparator;
    public final boolean variableColumnCount;
    public final boolean slice;
    public final DistributionFactory sizeDistribution;
    public final DistributionFactory countDistribution;

    public SettingsColumn(GroupedOptions options)
    {
        this((Options) options,
                options instanceof NameOptions ? (NameOptions) options : null,
                options instanceof CountOptions ? (CountOptions) options : null
        );
    }

    public SettingsColumn(Options options, NameOptions name, CountOptions count)
    {
        sizeDistribution = options.size.get();
        {
            comparator = options.comparator.value();
            AbstractType parsed = null;

            try
            {
                parsed = TypeParser.parse(comparator);
            }
            catch (Exception e)
            {
                System.err.println(e.getMessage());
                System.exit(1);
            }

            if (!(parsed instanceof TimeUUIDType || parsed instanceof AsciiType || parsed instanceof UTF8Type))
            {
                System.err.println("Currently supported types are: TimeUUIDType, AsciiType, UTF8Type.");
                System.exit(1);
            }
        }
        if (name != null)
        {
            assert count == null;

            AbstractType comparator;
            try
            {
                comparator = TypeParser.parse(this.comparator);
            } catch (Exception e)
            {
                throw new IllegalArgumentException(this.comparator + " is not a valid type");
            }

            final String[] names = name.name.value().split(",");
            this.names = new ArrayList<>(names.length);

            for (String columnName : names)
                this.names.add(comparator.fromString(columnName));
            Collections.sort(this.names, BytesType.instance);
            this.namestrs = new ArrayList<>();
            for (ByteBuffer columnName : this.names)
                this.namestrs.add(comparator.getString(columnName));

            final int nameCount = this.names.size();
            countDistribution = new DistributionFactory()
            {
                @Override
                public Distribution get()
                {
                    return new DistributionFixed(nameCount);
                }
            };
        }
        else
        {
            this.countDistribution = count.count.get();
            ByteBuffer[] names = new ByteBuffer[(int) countDistribution.get().maxValue()];
            String[] namestrs = new String[(int) countDistribution.get().maxValue()];
            for (int i = 0 ; i < names.length ; i++)
                names[i] = ByteBufferUtil.bytes("C" + i);
            Arrays.sort(names, BytesType.instance);
            try
            {
                for (int i = 0 ; i < names.length ; i++)
                    namestrs[i] = ByteBufferUtil.string(names[i]);
            }
            catch (CharacterCodingException e)
            {
                throw new RuntimeException(e);
            }

            this.names = Arrays.asList(names);
            this.namestrs = Arrays.asList(namestrs);
        }
        maxColumnsPerKey = (int) countDistribution.get().maxValue();
        variableColumnCount = countDistribution.get().minValue() < maxColumnsPerKey;
        slice = options.slice.setByUser();
    }

    // Option Declarations

    private static abstract class Options extends GroupedOptions
    {
        final OptionSimple superColumns = new OptionSimple("super=", "[0-9]+", "0", "Number of super columns to use (no super columns used if not specified)", false);
        final OptionSimple comparator = new OptionSimple("comparator=", "TimeUUIDType|AsciiType|UTF8Type", "AsciiType", "Column Comparator to use", false);
        final OptionSimple slice = new OptionSimple("slice", "", null, "If set, range slices will be used for reads, otherwise a names query will be", false);
        final OptionDistribution size = new OptionDistribution("size=", "FIXED(34)", "Cell size distribution");
    }

    private static final class NameOptions extends Options
    {
        final OptionSimple name = new OptionSimple("names=", ".*", null, "Column names", true);

        @Override
        public List<? extends Option> options()
        {
            return Arrays.asList(name, slice, superColumns, comparator, size);
        }
    }

    private static final class CountOptions extends Options
    {
        final OptionDistribution count = new OptionDistribution("n=", "FIXED(5)", "Cell count distribution, per operation");

        @Override
        public List<? extends Option> options()
        {
            return Arrays.asList(count, slice, superColumns, comparator, size);
        }
    }

    // CLI Utility Methods

    static SettingsColumn get(Map<String, String[]> clArgs)
    {
        String[] params = clArgs.remove("-col");
        if (params == null)
            return new SettingsColumn(new CountOptions());

        GroupedOptions options = GroupedOptions.select(params, new NameOptions(), new CountOptions());
        if (options == null)
        {
            printHelp();
            System.out.println("Invalid -col options provided, see output for valid options");
            System.exit(1);
        }
        return new SettingsColumn(options);
    }

    static void printHelp()
    {
        GroupedOptions.printOptions(System.out, "-col", new NameOptions(), new CountOptions());
    }

    static Runnable helpPrinter()
    {
        return new Runnable()
        {
            @Override
            public void run()
            {
                printHelp();
            }
        };
    }
}
