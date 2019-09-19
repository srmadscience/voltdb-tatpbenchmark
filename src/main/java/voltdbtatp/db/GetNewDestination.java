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


public class GetNewDestination extends VoltProcedure {

	public static final SQLStmt getNewDestSelect = new SQLStmt("SELECT addCallForwarding.numberx "
         + " FROM Special_Facility AS sf, Call_Forwarding AS addCallForwarding "
         + "WHERE sf.s_id = ? "
         + "AND sf.sf_type = ? "
         + "AND sf.is_active = 1 "
         + "AND addCallForwarding.s_id = sf.s_id "
         + "AND addCallForwarding.sf_type = sf.sf_type "
         + "AND addCallForwarding.start_time <= ? "
         + "AND ? < addCallForwarding.end_time;");
     
  public VoltTable[] run(long subscriberId, long sfType, long st, long ed) throws VoltAbortException {

    voltQueueSQL(getNewDestSelect, subscriberId, sfType, st, ed);

    return voltExecuteSQL(true);
  }

}
