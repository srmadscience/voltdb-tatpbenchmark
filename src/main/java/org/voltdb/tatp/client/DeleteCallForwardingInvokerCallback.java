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

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.voltdb.client.Client;
import org.voltdb.client.ClientResponse;

public class DeleteCallForwardingInvokerCallback extends BaseCallback {

    private static Logger logger = LoggerFactory.getLogger(DeleteCallForwardingInvokerCallback.class);

    long randomStartTime = 0;
    int randomSfType = 0;

    public DeleteCallForwardingInvokerCallback(long startTime, long startTimeNanos, int sid,
            String callbackStatsCategory, Client c, long randomStartTime, int randomSfType) {

        super(startTime, startTimeNanos, sid, callbackStatsCategory, c, false);
        this.randomStartTime = randomStartTime;
        this.randomSfType = randomSfType;

    }

    @Override
    public void clientCallback(ClientResponse response) {

        super.clientCallback(response);

        int sid = getSid(response);
        BaseCallback c2 = new BaseCallback(startTime, startTimeNanos, sid, callbackStatsCategory + "_2", theClient,
                true);

        try {
            theClient.callProcedure(c2, "DeleteCallForwarding", sid, randomSfType, randomStartTime);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }

    }

}
