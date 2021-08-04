package voltdbtatp.db;

/* This file is part of VoltDB.
 * Copyright (C) 2008-2018 VoltDB Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;

public class Reset extends VoltProcedure {

    public static final SQLStmt d1 = new SQLStmt("DELETE FROM special_facility;");
    public static final SQLStmt d2 = new SQLStmt("DELETE FROM access_info;");
    public static final SQLStmt d3 = new SQLStmt("DELETE FROM subscriber;");
    public static final SQLStmt d4 = new SQLStmt("DELETE FROM call_forwarding ;");

    public VoltTable[] run() throws VoltAbortException {

        voltQueueSQL(d1);
        voltQueueSQL(d2);
        voltQueueSQL(d3);
        voltQueueSQL(d4);

        return voltExecuteSQL(true);
    }

}
