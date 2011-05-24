/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.ais.io;

import com.akiban.ais.ddl.SchemaDef;
import com.akiban.ais.ddl.SchemaDefToAis;
import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.Group;
import com.akiban.ais.model.GroupIndex;
import com.akiban.ais.model.IndexColumn;
import com.akiban.ais.model.Table;
import org.junit.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public final class MessageTest {
    private static AkibanInformationSchema coiSchema() throws Exception {
        SchemaDef schemaDef = new SchemaDef();
        schemaDef.parseCreateTable("create table test.c(id int key, name varchar(32)) engine=akibandb;");
        schemaDef.parseCreateTable("create table test.o(id int key, cid int, foo int, "+
                                   "constraint __akiban foreign key(cid) references c(id)) engine=akibandb;");
        schemaDef.parseCreateTable("create table test.i(id int key, oid int, "+
                                   "constraint __akiban foreign key(oid) references o(id)) engine=akibandb;");
        return new SchemaDefToAis(schemaDef, true).getAis();
    }

    private static void serializeAndCompare(AkibanInformationSchema ais) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(1 << 19);
        new Writer(new MessageTarget(buffer)).save(ais);
        buffer.flip();
        AkibanInformationSchema newAis = new Reader(new MessageSource(buffer)).load();
        assertEquals("buffer left", 0, buffer.remaining());
        assertEquals("groups", ais.getGroups().keySet(), newAis.getGroups().keySet());
        assertEquals("user tables", ais.getUserTables().keySet(), newAis.getUserTables().keySet());
        assertEquals("group tables", ais.getGroupTables().keySet(), newAis.getGroupTables().keySet());
        assertEquals("joins", ais.getJoins().keySet(), newAis.getJoins().keySet());
        newAis.checkIntegrity();
    }

    @Test
    public void emptyAIS() throws Exception {
        AkibanInformationSchema ais = new AkibanInformationSchema();
        serializeAndCompare(ais);
    }

    @Test
    public void coiAIS() throws Exception {
        AkibanInformationSchema ais = coiSchema();
        serializeAndCompare(ais);
    }

    @Test
    public void coiAISWithGroupIndex() throws Exception {
        final AkibanInformationSchema ais = coiSchema();
        final Table cTable = ais.getTable("test", "c");
        assertNotNull("c table not null", cTable);
        final Table oTable = ais.getTable("test", "o");
        assertNotNull("o table not null", oTable);
        final Group group = cTable.getGroup();
        assertSame("customer and order group", group, oTable.getGroup());
        final GroupIndex fooIndex = GroupIndex.create(ais, group, "foo", 100, false, "KEY");
        fooIndex.addColumn(new IndexColumn(fooIndex, cTable.getColumn("name"), 0, true, null));
        fooIndex.addColumn(new IndexColumn(fooIndex, oTable.getColumn("foo"), 1, true, null));
        ais.checkIntegrity();
        serializeAndCompare(ais);
    }
}
