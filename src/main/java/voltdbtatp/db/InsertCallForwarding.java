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

import java.util.Random;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;

public class InsertCallForwarding extends VoltProcedure {

    public static final SQLStmt querySpecialFacility = new SQLStmt(
            "SELECT sf_type FROM Special_Facility WHERE s_id = ? ORDER BY sf_type;");

    public static final SQLStmt delCallForwarding = new SQLStmt(
            "DELETE FROM Call_Forwarding WHERE s_id = ? AND sf_type = ?  AND start_time = ?;");

    public static final SQLStmt insertCallForwarding = new SQLStmt(
            "INSERT INTO call_forwarding (s_id,sf_type,start_time,end_time,numberx ) VALUES (?,?,?,?,?);");

    public VoltTable[] run(long subscriberId, long bit1, long dataA, long sfType) throws VoltAbortException {

        Random r = getSeededRandomNumberGenerator();

        voltQueueSQL(querySpecialFacility, subscriberId);

        VoltTable[] firstOne = voltExecuteSQL();
        if (firstOne[0].advanceRow()) {
            long sftype = firstOne[0].getLong("sf_type");

            int j = r.nextInt(4);

            int startTime = 0;

            if (j == 1) {
                startTime = 8;
            } else if (j == 2) {
                startTime = 16;
            }

            int endTime = startTime + 1 + r.nextInt(24);

            voltQueueSQL(delCallForwarding, subscriberId, sftype, startTime);
            voltQueueSQL(insertCallForwarding, subscriberId, sftype, startTime, endTime,
                    LoadSubscriber.makeRandString(LoadSubscriber.ALPHABET, r, 15));
        }
        return voltExecuteSQL(true);
    }

}
