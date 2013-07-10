/**
 * Copyright (C) 2009-2013 Akiban Technologies, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.akiban.qp.operator;

import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.util.ValueSourceHasher;
import com.akiban.server.collation.AkCollator;
import com.akiban.server.explain.*;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types3.pvalue.PValueSource;
import com.akiban.server.types3.pvalue.PValueSources;
import com.akiban.util.ArgumentValidation;
import com.akiban.util.BloomFilter;
import com.akiban.util.tap.InOutTap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * <h1>Overview</h1>
 * <p/>
 * Using_BloomFilter loads a bloom filter for use by Select_BloomFilter.
 * <p/>
 * <h1>Arguments</h1>
 * <p/>
 * <li><b>Operator filterInput:</b></li> Stream of rows used to load the filter
 * <li><b>long estimatedRowCount,:</b></li> Estimated count of rows from filterInput
 * <li><b>int filterBindingPosition,:</b></li> Position in the query context that will contain the bloom filter
 * <li><b>Operator streamInput: </b></li> Stream of rows to be filtered
 * <p/>
 * <h1>Behavior</h1>
 * <p/>
 * When a Using_BloomFilter cursor is opened, all rows from the filterInput operator will be consumed and used to
 * load a bloom filter. The bloom filter will be set up to accomodate up to estimatedRowCount rows. If this number
 * is exceeded, the filter will be grown and the filterInput will be scanned a second time.
 * <p/>
 * Besides loading the bloom filter, all operations on a Using_BloomFilter cursor are delegated to the streamInput's
 * cursor.
 * <p/>
 * <h1>Output</h1>
 * <p/>
 * Output from the streamInput cursor is passed on.
 * <p/>
 * <h1>Assumptions</h1>
 * <p/>
 * None.
 * <p/>
 * <h1>Performance</h1>
 * <p/>
 * The filterInput stream will be consumed completely each time this operator's cursor is opened. It may be consumed
 * twice if estimatedRowCount is too low.
 * <p/>
 * <h1>Memory Requirements</h1>
 * <p/>
 * The bloom filter uses memory proportional to the number of rows scanned from filterInput, typically 2-4 bytes.
 */

class Using_BloomFilter extends Operator
{
    // Object interface

    @Override
    public String toString()
    {
        return getClass().getSimpleName();
    }

    // Operator interface


    @Override
    public void findDerivedTypes(Set<RowType> derivedTypes)
    {
        filterInput.findDerivedTypes(derivedTypes);
        streamInput.findDerivedTypes(derivedTypes);
    }

    @Override
    protected Cursor cursor(QueryContext context, QueryBindings bindings)
    {
        return new Execution(context, bindings, streamInput.cursor(context, bindings));
    }

    @Override
    public List<Operator> getInputOperators()
    {
        return Arrays.asList(filterInput, streamInput);
    }

    @Override
    public String describePlan()
    {
        return String.format("%s\n%s", describePlan(filterInput), describePlan(streamInput));
    }

    // Using_BloomFilter interface

    public Using_BloomFilter(Operator filterInput,
                             RowType filterRowType,
                             long estimatedRowCount,
                             int filterBindingPosition,
                             Operator streamInput,
                             List<AkCollator> collators,
                             boolean usePValues)
    {
        ArgumentValidation.notNull("filterInput", filterInput);
        ArgumentValidation.notNull("filterRowType", filterRowType);
        ArgumentValidation.isGTE("estimatedRowCount", estimatedRowCount, 0);
        ArgumentValidation.isGTE("filterBindingPosition", filterBindingPosition, 0);
        ArgumentValidation.notNull("streamInput", streamInput);
        if (collators != null)
            ArgumentValidation.isEQ("collators length", collators.size(), filterRowType.nFields());
        this.filterInput = filterInput;
        this.filterRowType = filterRowType;
        this.estimatedRowCount = estimatedRowCount;
        this.filterBindingPosition = filterBindingPosition;
        this.streamInput = streamInput;
        this.collators = collators;
        this.usePValues = usePValues;
    }

    // For use by this class

    private AkCollator collator(int f)
    {
        return collators == null ? null : collators.get(f);
    }

    // Class state

    private static final InOutTap TAP_OPEN = OPERATOR_TAP.createSubsidiaryTap("operator: Using_BloomFilter open");
    private static final InOutTap TAP_NEXT = OPERATOR_TAP.createSubsidiaryTap("operator: Using_BloomFilter next");
    private static final Logger LOG = LoggerFactory.getLogger(Using_BloomFilter.class);
    private static final double ERROR_RATE = 0.0001; // Bloom filter will use 19.17 bits per key

    // Object state

    private final Operator filterInput;
    private final RowType filterRowType;
    private final long estimatedRowCount;
    private final int filterBindingPosition;
    private final Operator streamInput;
    private final List<AkCollator> collators;
    private final boolean usePValues;

    @Override
    public CompoundExplainer getExplainer(ExplainContext context) {
        Attributes atts = new Attributes();
        atts.put(Label.NAME, PrimitiveExplainer.getInstance(getName()));
        atts.put(Label.BINDING_POSITION, PrimitiveExplainer.getInstance(filterBindingPosition));
        atts.put(Label.INPUT_OPERATOR, filterInput.getExplainer(context));
        atts.put(Label.INPUT_OPERATOR, streamInput.getExplainer(context));
        return new CompoundExplainer(Type.BLOOM_FILTER, atts);
    }

    // Inner classes

    private class Execution extends OperatorExecutionBase implements Cursor
    {
        // Cursor interface

        @Override
        public void open()
        {
            TAP_OPEN.in();
            try {
                BloomFilter filter = loadBloomFilter();
                bindings.setBloomFilter(filterBindingPosition, filter);
                input.open();
            } finally {
                TAP_OPEN.out();
            }
        }

        @Override
        public Row next()
        {
            if (TAP_NEXT_ENABLED) {
                TAP_NEXT.in();
            }
            try {
                Row output = input.next();
                if (LOG_EXECUTION) {
                    LOG.debug("Using_BloomFilter: yield {}", output);
                }
                return output;
            } finally {
                if (TAP_NEXT_ENABLED) {
                    TAP_NEXT.out();
                }
            }
        }

        @Override
        public void close()
        {
            input.close();
        }

        @Override
        public void destroy()
        {
            close();
            input.destroy();
            bindings.setBloomFilter(filterBindingPosition, null);
        }

        @Override
        public boolean isIdle()
        {
            return input.isIdle();
        }

        @Override
        public boolean isActive()
        {
            return input.isActive();
        }

        @Override
        public boolean isDestroyed()
        {
            return input.isDestroyed();
        }

        // Execution interface

        Execution(QueryContext context, QueryBindings bindings, Cursor input)
        {
            super(context, bindings);
            this.input = input;
        }

        // For use by this class

        private BloomFilter loadBloomFilter()
        {
            BloomFilter filter = new BloomFilter(estimatedRowCount, ERROR_RATE);
            long rows = loadBloomFilter(filter);
            if (rows > estimatedRowCount) {
                // Do it again, but size the filter based on the actual row count
                filter = new BloomFilter(rows, ERROR_RATE);
                loadBloomFilter(filter);
            }
            return filter;
        }

        private long loadBloomFilter(BloomFilter filter)
        {
            int fields = filterRowType.nFields();
            int rows = 0;
            Cursor loadCursor = filterInput.cursor(context, bindings);
            loadCursor.open();
            Row row;
            while ((row = loadCursor.next()) != null) {
                int h = 0;
                for (int f = 0; f < fields; f++) {
                    if (usePValues) {
                        PValueSource valueSource = row.pvalue(f);
                        h = h ^ PValueSources.hash(valueSource, collator(f));
                    }
                    else {
                        ValueSource valueSource = row.eval(f);
                        h = h ^ ValueSourceHasher.hash(adapter(), valueSource, collator(f));
                    }
                }
                filter.add(h);
                rows++;
            }
            loadCursor.destroy();
            return rows;
        }

        // Object state

        private final Cursor input;
    }
}
