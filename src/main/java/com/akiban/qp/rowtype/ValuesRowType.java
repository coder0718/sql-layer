/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.qp.rowtype;

import com.akiban.server.types.AkType;
import com.akiban.server.types3.TInstance;
import com.akiban.server.types3.pvalue.PValueSources;

import java.util.Arrays;

public class ValuesRowType extends DerivedRowType
{
    // Object interface

    @Override
    public String toString()
    {
        return "values(" + Arrays.toString(types == null ? tInstances : types) + ')';
    }

    // RowType interface

    @Override
    public int nFields()
    {
        return (types == null) ? ((tInstances == null) ? 0 : tInstances.length) : types.length;
    }

    @Override
    public AkType typeAt(int index) {
        return types[index];
    }

    @Override
    public TInstance typeInstanceAt(int index) {
        // Hopefully we were created in a types3 way and therefore have tInstances. If not, we have no idea what
        // our nullability is, so we have to be pessimistic and assume everything is nullable.
        return tInstances != null ? tInstances[index] : PValueSources.fromAkType(types[index], true);
    }

    // ValuesRowType interface

    public ValuesRowType(DerivedTypesSchema schema, int typeId, AkType... types)
    {
        super(schema, typeId);
        this.types = types;
        this.tInstances = null;
    }

    public ValuesRowType(DerivedTypesSchema schema, int typeId, TInstance... fields) {
        super(schema, typeId);
        this.types = null;
        this.tInstances = fields;
        assert fields != null : "must have tInstance fields for the ValuesRowType";
    }

    // Object state

    private final AkType[] types;
    private TInstance[] tInstances;
}
