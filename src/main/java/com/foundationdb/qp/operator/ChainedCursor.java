/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
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

package com.foundationdb.qp.operator;

import com.foundationdb.qp.row.Row;
import com.foundationdb.server.api.dml.ColumnSelector;

/**
 * The first of the three complete implementations of the CursorBase
 * interface. 
 * 
 * The ChainedCursor works in the middle of the operator tree, taking
 * input from the input operator and passing along rows to the caller to next().
 * 
 * The ChainedCursor assumes nothing about the QueryBindings, 
 * in general does not use them, and only passes them along to either
 * parent or child. 
 * 
 * @See LeafCursor
 * @see DualChainedCursor
 *
 */
public class ChainedCursor extends OperatorCursor
{
    protected final Cursor input;
    protected QueryBindings bindings;
    protected CursorLifecycle.CursorState state = CursorLifecycle.CursorState.CLOSED;
    
    protected ChainedCursor(QueryContext context, Cursor input) {
        super(context);
        this.input = input;
    }

    public Cursor getInput() {
        return input;
    }

    @Override
    public void open() {
        CursorLifecycle.checkClosed(input);
        input.open();
        state = CursorLifecycle.CursorState.ACTIVE;
    }

    @Override
    public void jump(Row row, ColumnSelector columnSelector)
    {
        input.jump(row, columnSelector);
    }

    @Override
    public void close() {
        if (CURSOR_LIFECYCLE_ENABLED) {
            CursorLifecycle.checkIdleOrActive(input);
        }
        input.close();
        state = CursorLifecycle.CursorState.CLOSED;
    }

    @Override
    public boolean isIdle()
    {
        return state == CursorLifecycle.CursorState.IDLE;
    }

    @Override
    public boolean isActive()
    {
        return state == CursorLifecycle.CursorState.ACTIVE;
    }

    @Override
    public boolean isClosed()
    {
        return state == CursorLifecycle.CursorState.CLOSED;
    }

    @Override
    public void openBindings() {
        input.openBindings();
    }

    @Override
    public QueryBindings nextBindings() {
        bindings = input.nextBindings();
        return bindings;
    }

    @Override
    public void closeBindings() {
        input.closeBindings();
    }

    protected void setIdle() {
        state = CursorLifecycle.CursorState.IDLE;
    }
    @Override
    public void cancelBindings(QueryBindings ancestor) {
        close();                // In case override maintains some additional state.
        input.cancelBindings(ancestor);
    }
}
