package tatp_server;

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


public class UpdateSubscriberData extends VoltProcedure {

	public static final SQLStmt firstSelect = new SQLStmt("UPDATE Subscriber SET bit_1 = ? WHERE s_id = ?;");
	public static final SQLStmt x = new SQLStmt("UPDATE Special_Facility SET data_a = ? WHERE s_id = ? AND sf_type = ?;");
     

	public VoltTable[] run(long subscriberId, long bit1, long dataA, long sfType) throws VoltAbortException {

		voltQueueSQL(firstSelect, bit1, subscriberId);
		voltQueueSQL(x, dataA, subscriberId, sfType);

		// Return control - 'true' tells the C++ core this is our last
		// interqction
		return voltExecuteSQL(true);
	}

}
