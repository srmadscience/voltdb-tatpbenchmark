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


public class GetSubscriberData extends VoltProcedure {

	public static final SQLStmt firstSelect = new SQLStmt("SELECT s_id, sub_nbr, "
     +" bit_1, bit_2, bit_3, bit_4, bit_5, bit_6, bit_7, "
     +"bit_8, bit_9, bit_10, "
     +"hex_1, hex_2, hex_3, hex_4, hex_5, hex_6, hex_7, "
     +"hex_8, hex_9, hex_10, "
     +"byte2_1, byte2_2, byte2_3, byte2_4, byte2_5, "
     +"byte2_6, byte2_7, byte2_8, byte2_9, byte2_10, "
     +"msc_location, vlr_location "
     +"FROM Subscriber "
     +"WHERE s_id = ?;");
     

	public VoltTable[] run(long subscriberId) throws VoltAbortException {

		voltQueueSQL(firstSelect, subscriberId);

		// Return control - 'true' tells the C++ core this is our last
		// Interaction
		return voltExecuteSQL(true);
	}

}
