package org.voltdb.tatp.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.voltdb.VoltTable;
import org.voltdb.client.Client;
import org.voltdb.client.ClientResponse;
import org.voltdb.client.ClientResponseWithPartitionKey;

public class GenericUpdateOneRowInAllPartitionsCallback extends BaseMPCallback {

  /*
   * This file is part of VoltDB. Copyright (C) 2008-2019 VoltDB Inc.
   *
   * Permission is hereby granted, free of charge, to any person obtaining a
   * copy of this software and associated documentation files (the "Software"),
   * to deal in the Software without restriction, including without limitation
   * the rights to use, copy, modify, merge, publish, distribute, sublicense,
   * and/or sell copies of the Software, and to permit persons to whom the
   * Software is furnished to do so, subject to the following conditions:
   *
   * The above copyright notice and this permission notice shall be included in
   * all copies or substantial portions of the Software.
   *
   * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   * AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
   * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
   * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
   */

  private static Logger logger = LoggerFactory.getLogger(GenericUpdateOneRowInAllPartitionsCallback.class);

  public GenericUpdateOneRowInAllPartitionsCallback(long startTime, long startTimeNanos, int sid,
      String callbackStatsCategory, Client c) {
    super(startTime, startTimeNanos, sid, callbackStatsCategory, c, false);

  }

  @Override
  public void clientCallback(ClientResponseWithPartitionKey[] response) {

    try {
      super.clientCallback(response);
    } catch (Exception e) {
      logger.error(e.getMessage());
    }

    // See how many records we updated. Answer ought to be 1.
    long updated = 0;

    for (int i = 0; i < response.length; i++) {
      updated += getUpdateCount(response[i].response);
      
      if (updated < 0 || updated > 2) {
        System.out.println("foo");
      }
    }

    if (updated == 1) {
      histCache.incCounter(callbackStatsCategory + "_WORKED");
    } else {
      histCache.incCounter(callbackStatsCategory + "_FAILED");
      logger.error("Error: " + callbackStatsCategory + ": Didn't update 1 and only 1 record for sid " 
          + sid +". got " + updated + " instead'");
    }
  }

  public static long getUpdateCount(ClientResponse response) {

    long count = 0;

    if (response.getStatus() == ClientResponse.SUCCESS) {

      VoltTable[] resultTables = response.getResults();

      //Assume we're looking for the last SQL statement result....
      if (resultTables[resultTables.length-1].advanceRow()) {
        count =  resultTables[resultTables.length-1].getLong(0);
      }

    } else {
      logger.error("Error: got error message " + response.getStatusString());
      return -1;
    }

    return count;

  }
}
