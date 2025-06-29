/* This file is part of VoltDB.
 * Copyright (C) 2022 VoltDB Inc.
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
package voltdbtatp.db;

import org.voltcore.logging.VoltLogger;
import org.voltdb.VoltCompoundProcedure;
import org.voltdb.client.ClientResponse;

public class InsertCallForwardingCompound extends VoltCompoundProcedure {
    
    static VoltLogger LOG = new VoltLogger("InsertCallForwardingCompound");

    String fkString = null;
    int sid;
    long bit1;
    long dataA;
    long sfType;
    boolean errorsReported;

    public long run(String fkString, long bit1, long dataA, long sfType) {

        // Save inputs
        this.fkString = fkString;
        
        this.bit1 = bit1;
        this.dataA = dataA;
        this.sfType = sfType;

        // Build stages
        newStageList(this::doLookups)
        .then(this::doUpdates)
        .then(this::finish)
        .build();
        return 0L;
    }

    // Invoke first stage procedures, lookups on different partitioning keys
    private void doLookups(ClientResponse[] unused) {
   
        queueProcedureCall("SUBSCRIBER_NBR_MAP.select", fkString);
    }

    // Process results of first stage, i.e. lookups, and perform updates
    private void doUpdates(ClientResponse[] resp) {
        
        boolean allGood = true;

        // Process response 0 = username
        ClientResponse resp0 = resp[0];
        if (resp0.getStatus() != ClientResponse.SUCCESS) {
            
            abortProcedure(String.format("InsertCallForwardingCompound returned: %d", resp0.getStatus()));
        }
        else if (resp0.getResults().length > 0 && resp0.getResults()[0].advanceRow()) {
            sid = (int) resp0.getResults()[0].getLong("S_ID"); 
        }
        else {
            reportError(String.format("No user match found for fkString %d", fkString));
            allGood = false;
        }

        // Did we get all we need?
        if (allGood) {
            queueProcedureCall("InsertCallForwarding", sid,  bit1,  dataA,  sfType);
        }
        
    }

    private void finish(ClientResponse[] resp) {

        if (resp[0].getStatus() != ClientResponse.SUCCESS) {

            abortProcedure(String.format("InsertCallForwardingCompound returned: %d", resp[0].getStatus()));
        }

        completeProcedure(0L);
    }

   

    // Complete the procedure after reporting errors: check if we succeeded logging them
    private void completeWithErrors(ClientResponse[] resp) {
        for (ClientResponse r : resp) {
            if (r.getStatus() != ClientResponse.SUCCESS) {
                abortProcedure(String.format("Failed reporting errors: %s", r.getStatusString()));
            }
        }
        completeProcedure(-1L);
    }

    // Report execution errors to special topic. We:
    // 1.  Change the stage list so as to abandon all incomplete stages
    //     and set up a new final stage
    // 2.  Queue up a request, to be executed after the
    //     current stage, to update the special topic
    private void reportError(String message) {
        if (!errorsReported) {
            newStageList(this::completeWithErrors)
                          .build();
            errorsReported = true;
        }
       // queueProcedureCall("COOKIE_ERRORS.insert", cookieId, urlStr, message);
    }
}
