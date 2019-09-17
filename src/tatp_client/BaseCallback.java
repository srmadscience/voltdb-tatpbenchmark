package tatp_client;

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

import org.voltdb.client.Client;
import org.voltdb.client.ClientResponse;
import org.voltdb.client.ProcedureCallback;
import org.voltdb.voltutil.stats.SafeHistogramCache;

public class BaseCallback extends AbstactCallback implements ProcedureCallback {


    boolean lastCallInChain = true;
    SafeHistogramCache histCache = SafeHistogramCache.getInstance();

	public BaseCallback(long startTime, long startTimeNanos, int sid, SafeHistogramCache h,
			String callbackStatsCategory, Client theClient, boolean lastCallInChain) {
		super(startTime, startTimeNanos, sid, h, callbackStatsCategory, theClient);
		this.lastCallInChain = lastCallInChain;
		
	}

	@Override
	public void clientCallback(ClientResponse response) {

		// Make sure the procedure succeeded.
		if (response.getStatus() != ClientResponse.SUCCESS) {

			//System.out.println("VoltDB Asynchronous stored procedure failed. Res: " + response.getStatus() + " "
			//		+ response.getStatusString());

			h.reportLatency(callbackStatsCategory + "_FAIL_MS", startTime, response.getStatusString(),
					MILLISECOND_STATS_SIZE);
			 histCache.incCounter("ERROR");
		} else {
		    if (lastCallInChain) {
	            h.reportLatency(callbackStatsCategory + "_WALL_MILLIS", startTime, response.getStatusString(),
	                    MILLISECOND_STATS_SIZE);		        
		    }

			if (startTimeNanos > Long.MIN_VALUE) {
				long micros = System.nanoTime() - startTimeNanos;
				micros = micros / 1000;

				if (micros >= MICROSECOND_STATS_SIZE) {
					h.reportLatency(callbackStatsCategory + "_LATE_MS", startTime, response.getStatusString(),
							MILLISECOND_STATS_SIZE);
				} else {
					h.report(callbackStatsCategory + "_JVM_MICROS", (int) micros, response.getStatusString(),
							MICROSECOND_STATS_SIZE);
				}
			}

			h.report(callbackStatsCategory + "_VOLT_CLIENT_MS", response.getClientRoundtrip(), null,
					MILLISECOND_STATS_SIZE);
			
			long clientMicros = response.getClientRoundtripNanos();
			clientMicros = clientMicros / 1000;

			h.report(callbackStatsCategory + "_VOLT_CLIENT_MICROS", (int)clientMicros, null,
					MICROSECOND_STATS_SIZE);

			h.report(callbackStatsCategory + "_VOLT_CLUSTER_MS", response.getClusterRoundtrip(), null,
					MILLISECOND_STATS_SIZE);

		}

	}

	
}
