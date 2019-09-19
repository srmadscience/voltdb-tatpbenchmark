package org.voltdb.tatp.client;

/* This file is part of VoltDB.
 * Copyright (C) 2008-2019 VoltDB Inc.
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

import org.voltdb.VoltTable;
import org.voltdb.client.Client;
import org.voltdb.client.ClientResponse;
import org.voltdb.voltutil.stats.SafeHistogramCache;

public abstract class AbstactCallback  {

	final int MILLISECOND_STATS_SIZE = 10000;
	final int MICROSECOND_STATS_SIZE = 1000000;

	long startTime = 0;
	long startTimeNanos = 0;
	int sid = 0;
	SafeHistogramCache h = null;
	String callbackStatsCategory = null;
	Client theClient = null;

	public AbstactCallback(long startTime, long startTimeNanos, int sid, SafeHistogramCache h,
			String callbackStatsCategory, Client theClient) {
		this.startTime = startTime;
		this.startTimeNanos = startTimeNanos;
		this.sid = sid;
		this.h = h;
		this.callbackStatsCategory = callbackStatsCategory;
		this.theClient = theClient;

	}

	public static int getSid(ClientResponse response) {

		int sid = -1;

		if (response.getStatus() == ClientResponse.SUCCESS) {

			VoltTable[] resultTables = response.getResults();

			if (resultTables[0].advanceRow()) {
				sid = (int) resultTables[0].getLong("S_ID");
			}

		} 

		return sid;

	}
}
