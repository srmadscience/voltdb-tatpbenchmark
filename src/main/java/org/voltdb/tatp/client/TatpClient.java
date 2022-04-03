package org.voltdb.tatp.client;

import java.io.File;
import java.io.FileNotFoundException;

import com.jezhumble.javasysmon.CpuTimes;
import com.jezhumble.javasysmon.JavaSysMon;

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
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.voltdb.VoltTable;
import org.voltdb.VoltType;
import org.voltdb.client.Client;
import org.voltdb.client.ClientConfig;
import org.voltdb.client.ClientFactory;
import org.voltdb.client.ClientResponse;
import org.voltdb.client.NoConnectionsException;
import org.voltdb.client.ProcCallException;
import org.voltdb.voltutil.schemabuilder.VoltDBSchemaBuilder;
import org.voltdb.voltutil.stats.SafeHistogramCache;
import org.voltdb.voltutil.stats.StatsHistogram;

public class TatpClient implements Runnable {

    // Different Event types
    public static final int GET_SUBSCRIBER_DATA = 0;
    public static final int GET_NEW_DESTINATION = 1;
    public static final int GET_ACCESS_DATA = 2;
    public static final int UPDATE_SUBSCRIBER_DATA = 3;
    public static final int UPDATE_LOCATION = 4;
    public static final int INSERT_CALL_FORWARDING = 5;
    public static final int DELETE_CALL_FORWARDING = 6;
    public static final int UPDATE_SUBSCRIBER_NBR = 7;

    // How we're handling FK lookups
    public static final int FKMODE_QUERY_ALL_PARTITIONS_FIRST = 0;
    public static final int FKMODE_TASK_ALL_PARTITIONS = 1;
    public static final int FKMODE_MULTI_QUERY_FIRST = 2;
    public static final int FKMODE_CACHED_ANSWER = 3;
    public static final int FKMODE_COMPOUND_PROCS = 4;

    // How long we wait between passes
    private static final long DELAY_SECONDS = 5;

    // Max acceptable client CPU
    private static final int MAX_CPU_PCT = 80;

    /**
     * DDL statements for the VoltDB implementation of TATP. Note that you just run
     * this using SQLCMD, but the make this implementation easier to re-create we do
     * it Programmatically.
     */
    final String[] ddlStatements = { "CREATE TABLE subscriber "
            + "      (s_id BIGINT NOT NULL PRIMARY KEY, sub_nbr VARCHAR(15) NOT NULL , "
            + "      bit_1 TINYINT, bit_2 TINYINT, bit_3 TINYINT, bit_4 TINYINT, bit_5 TINYINT, "
            + "      bit_6 TINYINT, bit_7 TINYINT, bit_8 TINYINT, bit_9 TINYINT, bit_10 TINYINT, "
            + "      hex_1 TINYINT, hex_2 TINYINT, hex_3 TINYINT, hex_4 TINYINT, hex_5 TINYINT, "
            + "      hex_6 TINYINT, hex_7 TINYINT, hex_8 TINYINT, hex_9 TINYINT, hex_10 TINYINT, "
            + "      byte2_1 SMALLINT, byte2_2 SMALLINT, byte2_3 SMALLINT, byte2_4 SMALLINT, byte2_5 SMALLINT, "
            + "      byte2_6 SMALLINT, byte2_7 SMALLINT, byte2_8 SMALLINT, byte2_9 SMALLINT, byte2_10 SMALLINT, "
            + "      msc_location BIGINT, vlr_location BIGINT);"

            , "PARTITION TABLE subscriber ON COLUMN s_id;"

            , "create ASSUMEUNIQUE index sub_idx on subscriber(sub_nbr);"

            , "CREATE TABLE subscriber_nbr_map "
                    + "      ( sub_nbr VARCHAR(15) NOT NULL PRIMARY KEY, s_id BIGINT NOT NULL);"

            , "PARTITION TABLE subscriber_nbr_map ON COLUMN sub_nbr;"

            , "CREATE ASSUMEUNIQUE INDEX subscriber_nbr_map_ix1 ON subscriber_nbr_map (s_id);"

            ,
            "CREATE TABLE access_info " + "      (s_id BIGINT NOT NULL, ai_type TINYINT NOT NULL, "
                    + "      data1 SMALLINT, data2 SMALLINT, data3 VARCHAR(3), data4 VARCHAR(5), "
                    + "      PRIMARY KEY (s_id, ai_type), " + "      FOREIGN KEY (s_id) REFERENCES subscriber (s_id));",
            "PARTITION TABLE access_info ON COLUMN s_id;",
            "CREATE TABLE special_facility "
                    + "      (s_id BIGINT NOT NULL, sf_type TINYINT NOT NULL, is_active TINYINT NOT NULL, "
                    + "      error_cntrl SMALLINT, data_a SMALLINT, data_b VARCHAR(5), "
                    + "      PRIMARY KEY (s_id, sf_type), " + "      FOREIGN KEY (s_id) REFERENCES subscriber (s_id));",
            "PARTITION TABLE special_facility ON COLUMN s_id;",
            "CREATE TABLE call_forwarding "
                    + "      (s_id BIGINT NOT NULL, sf_type TINYINT NOT NULL, start_time TINYINT NOT NULL, "
                    + "      end_time TINYINT, numberx VARCHAR(15), "
                    + "      PRIMARY KEY (s_id, sf_type, start_time), "
                    + "      FOREIGN KEY (s_id, sf_type) REFERENCES special_facility(s_id, sf_type));",
            "PARTITION TABLE call_forwarding ON COLUMN s_id;" };

    /**
     * Procedure statements for the VoltDB implementation of TATP. Note that you
     * just run this using SQLCMD, but the make this implementation easier to
     * re-create we do it Programmatically.
     */
    final String[] procStatements = { "CREATE PROCEDURE FROM CLASS voltdbtatp.db.Reset;"

            ,
            "CREATE PROCEDURE PARTITION ON TABLE subscriber COLUMN s_id FROM CLASS voltdbtatp.db.DeleteCallForwarding;"

            , "CREATE PROCEDURE PARTITION ON TABLE subscriber COLUMN s_id FROM CLASS voltdbtatp.db.GetAccessData;"

            , "CREATE PROCEDURE PARTITION ON TABLE subscriber COLUMN s_id FROM CLASS voltdbtatp.db.GetNewDestination;"

            , "CREATE PROCEDURE PARTITION ON TABLE subscriber COLUMN s_id FROM CLASS voltdbtatp.db.GetSubscriberData;"
            , "CREATE PROCEDURE PARTITION ON TABLE subscriber COLUMN s_id FROM CLASS voltdbtatp.db.UpdateSubscriberData;"
            
            ,
            "CREATE PROCEDURE PARTITION ON TABLE subscriber COLUMN s_id FROM CLASS voltdbtatp.db.InsertCallForwarding;"

            , "CREATE PROCEDURE PARTITION ON TABLE subscriber COLUMN s_id FROM CLASS voltdbtatp.db.LoadSubscriber;"

            , "CREATE PROCEDURE PARTITION ON TABLE subscriber COLUMN s_id FROM CLASS voltdbtatp.db.UpdateLocation;"

            ,
            "CREATE PROCEDURE PARTITION ON TABLE subscriber COLUMN s_id FROM CLASS voltdbtatp.db.UpdateLocationCompound;"
            ,
            "CREATE PROCEDURE PARTITION ON TABLE subscriber COLUMN s_id FROM CLASS voltdbtatp.db.InsertCallForwardingCompound;"
            ,
            "CREATE PROCEDURE PARTITION ON TABLE subscriber COLUMN s_id FROM CLASS voltdbtatp.db.DeleteCallForwardingCompound;"

            , "CREATE PROCEDURE PARTITION ON TABLE subscriber COLUMN s_id "
                    + "FROM CLASS voltdbtatp.db.MapSubStringToNumberAllPartitions; "

            , "CREATE PROCEDURE FROM CLASS voltdbtatp.db.MapSubStringToNumberNoView;"

            ,
            "CREATE PROCEDURE PARTITION ON TABLE subscriber COLUMN s_id FROM CLASS voltdbtatp.db.UpdateLocationMultiPartition;"

            ,
            "CREATE PROCEDURE PARTITION ON TABLE subscriber COLUMN s_id FROM CLASS voltdbtatp.db.InsertCallForwardingMultiPartition;",
            "CREATE PROCEDURE PARTITION ON TABLE subscriber COLUMN s_id FROM CLASS voltdbtatp.db.DeleteCallForwardingMultiPartition;",
            "CREATE PROCEDURE FROM CLASS voltdbtatp.db.UpdateSubNbr;" };

    // We only create the DDL and procedures if a call to testProcName with
    // testParams fails....
    final String testProcName = "GetSubscriberData";
    final Object[] testParams = { new Long(42) };

    // We use three clients. You need a separate callback client
    // to avoid threading issues while interacting with the DB in a
    // callback.
    Client client = null;
    Client callbackClient = null;
    Client statsClient = null;

    // Used to monitor client CPU load.
    JavaSysMon monitor = new JavaSysMon();

    // Stores stats
    SafeHistogramCache h = SafeHistogramCache.getInstance();

    Random r = new Random(42);
    long endTime = 0;
    long tps = 0;
    long txnCount = 0;
    long startTime = 0;
    int size = 0;
    int fkMode = FKMODE_TASK_ALL_PARTITIONS;
    long lastStatsTime = System.currentTimeMillis();
    final int statsIntervalMs = 5000;
    int timeInSeconds;
    boolean doStats = false;
    int clientId;

    private static Logger logger = LoggerFactory.getLogger(TatpClient.class);

    /**
     * Run the TATP benchmark on VoltDB. Note that a default run uses 11 instances.
     * One (-1) has main method and the other 10 are used as worker threads.
     * 
     * @param hostnames  comma delimited list of hostnames
     * @param randomSeed Make behavior pseudo random
     * @param endTime    by default 3 minutes from now
     * @param tps        Transactions Per Second
     * @param size       How many rows of test data
     * @param fkMode     FKMODE_QUERY_ALL_PARTITIONS_FIRST,
     *                   FKMODE_TASK_ALL_PARTITIONS or FKMODE_MULTI_QUERY_FIRST
     * @param clientId   id
     */
    public TatpClient(String hostnames, long randomSeed, Date endTime, long tps, int size, int fkMode, int clientId) {
        super();
        r = new Random(randomSeed);
        this.endTime = endTime.getTime();
        this.tps = tps;
        this.size = size;
        this.fkMode = fkMode;
        this.clientId = clientId;

        try {
            msg(clientId + ": Creating main client");
            client = connectVoltDB(hostnames, fkMode);

            msg(clientId + ": Creating callback client");
            callbackClient = connectVoltDB(hostnames, fkMode);

            msg(clientId + ": Creating stats client");
            statsClient = connectVoltDB(hostnames, fkMode);

        } catch (Exception e) {
            logger.error(e.getMessage());
        }

    }

    /**
     * Get Number of rows in subscriber table.
     * 
     * @param client
     * @return Number of rows in subscriber.
     * @throws IOException
     * @throws NoConnectionsException
     * @throws ProcCallException
     */
    private static int getRecordCount(Client client) throws IOException, NoConnectionsException, ProcCallException {
        String sql = "select count(*) as cnt from subscriber;";
        VoltTable[] selectResults = null;
        selectResults = client.callProcedure("@AdHoc", sql).getResults();
        Object recordCount = null;
        for (int i = 0; i < selectResults.length; i++) {
            VoltTable voltTable = selectResults[i];

            voltTable.resetRowPosition();
            while (voltTable.advanceRow()) {
                recordCount = voltTable.get("cnt", VoltType.BIGINT);
            }

        }
        int recordCountInt = Integer.parseInt(recordCount.toString());
        return recordCountInt;
    }

    /**
     * Called to make sure we have 'rows' rows of test data.
     * 
     * @param rows
     */
    public void loadData(int rows) {

        try {

            VoltProcedureUpdateCallBack subscriberInsertCallback = new VoltProcedureUpdateCallBack();
            VoltProcedureUpdateCallBack subscriberMapInsertCallback = new VoltProcedureUpdateCallBack();

            int recordCount = getRecordCount(client);

            if (recordCount == rows) {
                msg("Correct number of rows exist...not changing them.");
            } else {
                msg(recordCount + " rows seen before Reset");

                client.callProcedure("Reset");

                recordCount = getRecordCount(client);
                msg(recordCount + " rows seen after Reset");

                long subId = 0;
                String subIdString;

                byte[] bitArray = new byte[10];
                byte[] hexArray = new byte[10];
                byte[] byteArray = new byte[10];

                long mscLocation = 0;
                long vlrLocation = 0;

                long start = System.currentTimeMillis();

                msg("Creating " + rows + " rows...");

                for (int i = 0; i < rows; i++) {
                    subId = i;
                    subIdString = "0" + i;

                    for (int j = 0; j < 10; j++) {

                        if (r.nextBoolean()) {
                            bitArray[j] = 1;
                        } else {
                            bitArray[j] = 0;
                        }

                        hexArray[j] = (byte) r.nextInt(16);
                        byteArray[j] = (byte) r.nextInt(256);

                        mscLocation = r.nextLong();
                        vlrLocation = r.nextLong();

                    }

                    // Note that the insert is happening in the background...
                    client.callProcedure(subscriberInsertCallback, "LoadSubscriber", subId, subIdString, bitArray[0],
                            bitArray[1], bitArray[2], bitArray[3], bitArray[4], bitArray[5], bitArray[6], bitArray[7],
                            bitArray[8], bitArray[9], hexArray[0], hexArray[1], hexArray[2], hexArray[3], hexArray[4],
                            hexArray[5], hexArray[6], hexArray[7], hexArray[8], hexArray[9], byteArray[0], byteArray[1],
                            byteArray[2], byteArray[3], byteArray[4], byteArray[5], byteArray[6], byteArray[7],
                            byteArray[8], byteArray[9], mscLocation, vlrLocation, i);

                    client.callProcedure(subscriberMapInsertCallback, "SUBSCRIBER_NBR_MAP.insert", subIdString, subId);

                    // Put in an arbitrary delay to avoid swamping servers and making graphs look
                    // funny...
                    if (i % 100 == 0) {
                        Thread.sleep(1);
                    }

                }

                // Important! Until this statement finishes Inserts are still happening..
                client.drain();

                recordCount = getRecordCount(client);
                msg(recordCount + " rows seen after insert");

                msg(rows + " rows processed in " + (System.currentTimeMillis() - start) + "ms");

                start = System.currentTimeMillis();

                msg("Waiting " + DELAY_SECONDS + "seconds...");
                Thread.sleep(DELAY_SECONDS);
            }

        } catch (Exception e) {
            logger.error(e.getMessage());
        }

    }

    /**
     * Get number of partitions - can influence performance, so we need to record it
     * in file name.
     * 
     * @return number of partitions
     */
    private int getPartitionCount() {
        int pCount = 8;

        ClientResponse statsResponse;
        try {
            statsResponse = statsClient.callProcedure("@GetPartitionKeys", "STRING");
            if (statsResponse.getResults()[0].advanceRow()) {
                pCount = statsResponse.getResults()[0].getRowCount();
            }

        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        return pCount;
    }

    /**
     * Normally we'll have 10 instances of this class each running at the same time.
     * 
     * @return null or a message saying why we can't continue.
     */
    public String runBenchmark() {

        if (doStats) {
            h.reset();
        }

        String ok = null;
        startTime = System.currentTimeMillis();
        timeInSeconds = (int) ((this.endTime - startTime) / 1000);
        lastStatsTime = startTime;
        long txns = 0;
        final long tpPerMs = tps / 1000;
        CpuTimes lastCpu = monitor.cpuTimes();

        while (endTime >= System.currentTimeMillis()) {

            long millisecs = System.currentTimeMillis();
            txns = 0;

            while (millisecs == System.currentTimeMillis()) {
                if (txns++ <= tpPerMs) {
                    launchTransaction();
                } else {
                    try {
                        Thread.sleep(0, 500000);
                    } catch (InterruptedException e) {
                        logger.error(e.getMessage());
                    }
                }
            }

            if (doStats && lastStatsTime + statsIntervalMs < System.currentTimeMillis()) {

                try {
                    ClientResponse statsResponse = statsClient.callProcedure("@Statistics", "LATENCY", 1);
                    while (statsResponse.getResults()[0].advanceRow()) {
                        // msg(statsResponse.getResults()[0].toFormattedString());
                        long hostId = statsResponse.getResults()[0].getLong("HOST_ID");
                        int latency = (int) statsResponse.getResults()[0].getLong("P50");

                        if (latency > 10000) {

                            ok = clientId + ": COD: Latency overload = " + latency;
                            h.incCounter("ERROR");

                        }

                        h.report("LATENCY_" + hostId, latency, null, 1000);
                    }

                    statsResponse = statsClient.callProcedure("@Statistics", "COMMANDLOG", 1);

                    while (statsResponse.getResults()[0].advanceRow()) {
                        long hostId = statsResponse.getResults()[0].getLong("HOST_ID");
                        int outstandingTransactions = (int) statsResponse.getResults()[0].getLong("OUTSTANDING_TXNS");
                        h.report("COMMAND_LOG_BACKLOG_" + hostId, outstandingTransactions, null, 50000);

                        if (outstandingTransactions > 50000) {
                            ok = clientId + ": COD: Command Log Backlog = " + outstandingTransactions;
                            h.incCounter("ERROR");

                        }

                    }

                    statsResponse = statsClient.callProcedure("@Statistics", "PROCEDUREPROFILE", 1);

                    int tranCount = 0;
                    while (statsResponse.getResults()[0].advanceRow()) {
                        tranCount += statsResponse.getResults()[0].getLong("INVOCATIONS");

                    }

                    tranCount = tranCount / (statsIntervalMs / 1000);
                    h.report("TPS", tranCount, null, 500000);

                    lastStatsTime = System.currentTimeMillis();

                    msg(clientId + ": Transactions=" + txnCount);

                    if (txnCount > 500000) {
                        // See if we are > 10ms...
                        if (h.get("GET_SUBSCRIBER_DATA_VOLT_CLIENT_MS").getLatencyAverage() > 10) {
                            msg(clientId + ": COD: Latency reached GET_SUBSCRIBER_DATA_VOLT_CLIENT_MS = "
                                    + h.get("GET_SUBSCRIBER_DATA_VOLT_CLIENT_MS").getLatencyAverage());
                            ok = "COD: Latency reached GET_SUBSCRIBER_DATA_VOLT_CLIENT_MS = "
                                    + h.get("GET_SUBSCRIBER_DATA_VOLT_CLIENT_MS").getLatencyAverage();
                            h.incCounter("ERROR");

                        }

                        if (h.get("UPDATE_LOCATION_2_WALL_MILLIS").getLatencyAverage() > 10) {
                            msg(clientId + ": COD: Latency reached UPDATE_LOCATION_2_WALL_MILLIS = "
                                    + h.get("UPDATE_LOCATION_2_WALL_MILLIS").getLatencyAverage());
                            ok = "COD: Latency reached UPDATE_LOCATION_2_WALL_MILLIS = "
                                    + h.get("UPDATE_LOCATION_2_WALL_MILLIS").getLatencyAverage();
                            h.incCounter("ERROR");

                        }
                    }

                    CpuTimes thisCpu = monitor.cpuTimes();
                    int cpuPct = (int) (thisCpu.getCpuUsage(lastCpu) * 100);

                    if (cpuPct > MAX_CPU_PCT) {
                        msg(clientId + ": COD: Client CPU = " + thisCpu.getCpuUsage(lastCpu));
                        ok = "COD: Client CPU  = " + thisCpu.getCpuUsage(lastCpu);
                        h.report("CLIENT_CPU", cpuPct, ok, 100);
                        h.incCounter("ERROR");

                    } else {
                        h.report("CLIENT_CPU", cpuPct, "", 100);
                    }

                    lastCpu = thisCpu;

                    // See if we have errors...
                    if (ok != null && h.getCounter("ERROR") > 0) {

                        msg(clientId + ": COD: Error count = " + h.getCounter("ERROR"));
                        msg(ok);
                    }

                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
            }

        }

        try {
            client.drain();
            callbackClient.drain();
            statsClient.drain();
        } catch (NoConnectionsException e) {
            logger.error(e.getMessage());
        } catch (InterruptedException e) {
            logger.error(e.getMessage());
        }
        msg("Transactions = " + txnCount);
        msg("runtime = " + timeInSeconds);
        msg("TPS = " + txnCount / timeInSeconds);

        return ok;
    }

    /**
     * Launch a single TATP transaction. Note that this is non-trival! Update
     * Location, Insert Call Forwarding and Delete Call Forwarding all use a 2 stage
     * process where the first callback maps the given ID to a subscriber ID and
     * then creates and launches a second callback that does the actual work.
     */
    private void launchTransaction() {
        txnCount++;

        int txnType = getRandomTransactionType();
        final long START_TIME = System.currentTimeMillis();
        final long START_TIME_NANOS = Long.MIN_VALUE;

        // Get pseudo-random subscriber id
        int sid = getRandomSid();

        BaseCallback theCallback = null;
        BaseMPCallback theMPCallback = null;

        try {

            String fkString = getFkString(sid);

            switch (txnType) {
            case GET_SUBSCRIBER_DATA:

                theCallback = new BaseCallback(START_TIME, START_TIME_NANOS, sid, "GET_SUBSCRIBER_DATA", callbackClient,
                        true);
                client.callProcedure(theCallback, "GetSubscriberData", sid);

                break;

            case GET_NEW_DESTINATION:
                theCallback = new BaseCallback(START_TIME, START_TIME_NANOS, sid, "GET_NEW_DESTINATION", callbackClient,
                        true);

                long st = getStartTime();
                long ed = getEndTime(st);
                client.callProcedure(theCallback, "GetNewDestination", sid, getRandomSfType(), st, ed);

                break;

            case GET_ACCESS_DATA:

                theCallback = new BaseCallback(START_TIME, START_TIME_NANOS, sid, "GET_ACCESS_DATA", callbackClient,
                        true);
                client.callProcedure(theCallback, "GetAccessData", sid, getRandomAiType());

                break;

            case UPDATE_SUBSCRIBER_DATA:

                theCallback = new BaseCallback(START_TIME, START_TIME_NANOS, sid, "UPDATE_SUBSCRIBER_DATA",
                        callbackClient, true);
                client.callProcedure(theCallback, "UpdateSubscriberData", sid, getRandomBit(), getRandomDataA(),
                        getRandomSfType());

                break;

            case UPDATE_LOCATION:

                // Depending on how we are going to handle the FK issue we do different things.
                //
                // FKMODE_QUERY_ALL_PARTITIONS_FIRST sends a request to each partition to try
                // and map the FK id to a
                // subscriber id. The callback listens for responses and reacts to the first
                // (and
                // hopefully *only* valid one.
                //
                // FKMODE_TASK_ALL_PARTITIONS asks all the partitions to do the update, and then
                // checks that one and
                // only one did.
                //
                // FKMODE_MULTI_QUERY_FIRST does a global read to map the FK to an ID and then
                // creates a new callback
                // to do the work when it has a valid ID.
                //
                // FKMODE_CACHED_ANSWER uses a two step process involving a lookup table partitioned on the FK.
                // 
                // FKMODE_COMPOUND_PROCS uses a compound procedure to merge the two steps in FKMODE_CACHED_ANSWER

                if (fkMode == FKMODE_QUERY_ALL_PARTITIONS_FIRST) {
                    theMPCallback = new UpdateLocationInvokerCallbackNoView(START_TIME, START_TIME_NANOS, sid,
                            "UPDATE_LOCATION", getRandomLocation(), callbackClient);

                    client.callAllPartitionProcedure(theMPCallback, "MapSubStringToNumberAllPartitions", fkString);

                } else if (fkMode == FKMODE_TASK_ALL_PARTITIONS) {

                    theMPCallback = new GenericUpdateOneRowInAllPartitionsCallback(START_TIME, START_TIME_NANOS, sid,
                            "UPDATE_LOCATION", callbackClient);

                    client.callAllPartitionProcedure(theMPCallback, "UpdateLocationMultiPartition", fkString,
                            getRandomLocation());

                } else if (fkMode == FKMODE_MULTI_QUERY_FIRST) {

                    theCallback = new UpdateLocationInvokerCallback(START_TIME, START_TIME_NANOS, sid,
                            "UPDATE_LOCATION", getRandomLocation(), callbackClient);

                    client.callProcedure(theCallback, "MapSubStringToNumberNoView", fkString);

                } else if (fkMode == FKMODE_CACHED_ANSWER) {

                    theCallback = new UpdateLocationInvokerCallback(START_TIME, START_TIME_NANOS, sid,
                            "UPDATE_LOCATION", getRandomLocation(), callbackClient);

                    client.callProcedure(theCallback, "SUBSCRIBER_NBR_MAP.select", fkString);

                } else if (fkMode == FKMODE_COMPOUND_PROCS) {

                    theCallback = new BaseCallback(START_TIME, START_TIME_NANOS, sid, "UPDATE_LOCATION", callbackClient,
                            true);
                    client.callProcedure(theCallback, "UpdateLocationCompound", fkString, getRandomLocation());

                    break;

                }

                break;

            case INSERT_CALL_FORWARDING:

                if (fkMode == FKMODE_QUERY_ALL_PARTITIONS_FIRST) {

                    theMPCallback = new InsertCallForwardingInvokerCallbackNoView(START_TIME, START_TIME_NANOS, sid,
                            "INSERT_CALL_FORWARDING", callbackClient, getRandomBit(), getRandomDataA(),
                            getRandomSfType());
                    client.callAllPartitionProcedure(theMPCallback, "MapSubStringToNumberAllPartitions", fkString);

                } else if (fkMode == FKMODE_TASK_ALL_PARTITIONS) {

                    theMPCallback = new GenericUpdateOneRowInAllPartitionsCallback(START_TIME, START_TIME_NANOS, sid,
                            "INSERT_CALL_FORWARDING", callbackClient);

                    client.callAllPartitionProcedure(theMPCallback, "InsertCallForwardingMultiPartition", fkString,
                            getRandomBit(), getRandomDataA(), getRandomSfType());

                } else if (fkMode == FKMODE_MULTI_QUERY_FIRST) {
                    theCallback = new InsertCallForwardingInvokerCallback(START_TIME, START_TIME_NANOS, sid,
                            "INSERT_CALL_FORWARDING", callbackClient, getRandomBit(), getRandomDataA(),
                            getRandomSfType());
                    client.callProcedure(theCallback, "MapSubStringToNumberNoView", fkString);

                } else if (fkMode == FKMODE_CACHED_ANSWER) {
                    theCallback = new InsertCallForwardingInvokerCallback(START_TIME, START_TIME_NANOS, sid,
                            "INSERT_CALL_FORWARDING", callbackClient, getRandomBit(), getRandomDataA(),
                            getRandomSfType());
                    client.callProcedure(theCallback, "SUBSCRIBER_NBR_MAP.select", fkString);

                } else if (fkMode == FKMODE_COMPOUND_PROCS) {
                    
                    theCallback = new BaseCallback(START_TIME, START_TIME_NANOS, sid, "INSERT_CALL_FORWARDING", callbackClient,
                            true);
                    client.callProcedure(theCallback, "InsertCallForwardingCompound", fkString, getRandomBit(), getRandomDataA(),
                            getRandomSfType() );
                    

                }

                break;

            case DELETE_CALL_FORWARDING:

                if (fkMode == FKMODE_QUERY_ALL_PARTITIONS_FIRST) {

                    theMPCallback = new DeleteCallForwardingInvokerCallbackNoView(START_TIME, START_TIME_NANOS, sid,
                            "DELETE_CALL_FORWARDING", callbackClient, getStartTime(), getRandomSfType());
                    client.callAllPartitionProcedure(theMPCallback, "MapSubStringToNumberAllPartitions", fkString);

                } else if (fkMode == FKMODE_TASK_ALL_PARTITIONS) {

                    theMPCallback = new GenericUpdateOneRowInAllPartitionsCallback(START_TIME, START_TIME_NANOS, sid,
                            "DELETE_CALL_FORWARDING", callbackClient);

                    client.callAllPartitionProcedure(theMPCallback, "DeleteCallForwardingMultiPartition", fkString,
                            getStartTime(), getRandomSfType());

                } else if (fkMode == FKMODE_MULTI_QUERY_FIRST) {

                    theCallback = new DeleteCallForwardingInvokerCallback(START_TIME, START_TIME_NANOS, sid,
                            "DELETE_CALL_FORWARDING", callbackClient, getStartTime(), getRandomSfType());
                    client.callProcedure(theCallback, "MapSubStringToNumberNoView", fkString);

                } else if (fkMode == FKMODE_CACHED_ANSWER) {

                    theCallback = new DeleteCallForwardingInvokerCallback(START_TIME, START_TIME_NANOS, sid,
                            "DELETE_CALL_FORWARDING", callbackClient, getStartTime(), getRandomSfType());
                    client.callProcedure(theCallback, "SUBSCRIBER_NBR_MAP.select", fkString);
                    
                } else if (fkMode == FKMODE_COMPOUND_PROCS) {
                    
                    theCallback = new BaseCallback(START_TIME, START_TIME_NANOS, sid, "DELETE_CALL_FORWARDING", callbackClient,
                            true);
                    client.callProcedure(theCallback, "DeleteCallForwardingCompound", fkString,  getRandomSfType(),getStartTime());

                }

                break;

            case UPDATE_SUBSCRIBER_NBR:

                theCallback = new BaseCallback(START_TIME, START_TIME_NANOS, sid, "UPDATE_SUBSCRIBER_NBR",
                        callbackClient, true);
                client.callProcedure(theCallback, "UpdateSubNbr", sid, getRandomlyChangedSubNumber(sid));

                break;

            default:
                break;
            }

        } catch (IOException e) {
            logger.error(e.getMessage());
        } catch (ProcCallException e) {
            logger.error(e.getMessage());
        }

        h.reportLatency(mapTypeToString(txnType) + "_INVOKE_MS", START_TIME, "", 1000);

    }

    private String getRandomlyChangedSubNumber(int sid) {
        return getFkString(sid) + "_" + r.nextInt(10);
    }

    private String getFkString(int sid) {

        return "0" + sid;
    }

    private long getEndTime(long st) {

        return 1 + r.nextInt(24);
    }

    private long getStartTime() {

        long sttime = 0;
        int randnumber = r.nextInt(4);

        switch (randnumber) {
        case 0:
            sttime = 0;
            break;
        case 1:
            sttime = 8;
            break;
        default:
            sttime = 16;

        }

        return sttime;
    }

    private int getRandomSid() {

        return NURand(size, 0, size);
    }

    private int getRandomSfType() {
        return r.nextInt(4) + 1;
    }

    private int getRandomAiType() {
        return r.nextInt(4) + 1;
    }

    private int getRandomBit() {
        return r.nextInt(2);
    }

    private int getRandomDataA() {
        return r.nextInt(256);
    }

    private long getRandomLocation() {
        return r.nextLong();
    }

    /**
     * Pick a transaction type based on the TATP spec.
     * 
     * @return chosen transaction type.
     */
    private int getRandomTransactionType() {
        int choice = r.nextInt(100);

        if (choice == 0 || choice == 1) {
            return DELETE_CALL_FORWARDING;
        }

        if (choice == 2 || choice == 3) {
            return INSERT_CALL_FORWARDING;
        }

        if (choice == 4 || choice == 5) {
            return UPDATE_SUBSCRIBER_DATA;
        }

        if (choice >= 6 && choice <= 20) {
            return UPDATE_LOCATION;
        }

        if (choice >= 21 && choice <= 30) {
            return GET_NEW_DESTINATION;
        }

        if (choice >= 31 && choice <= 36) {
            return GET_ACCESS_DATA;
        }

        return GET_SUBSCRIBER_DATA;
    }

    private static Client connectVoltDB(String hostnames, int fkMode) throws Exception {
        Client client = null;
        ClientConfig config = null;

        try {
            msg("Logging into VoltDB");

            config = new ClientConfig(); // "admin", "idontknow");
            config.setMaxOutstandingTxns(200000);
            config.setMaxTransactionsPerSecond(5000000);
            config.setTopologyChangeAware(true);
            config.setReconnectOnConnectionLoss(true);
            config.setHeavyweight(true);

            client = ClientFactory.createClient(config);
            String[] hostnameArray = hostnames.split(",");

            for (int i = 0; i < hostnameArray.length; i++) {
                msg("Connect to " + hostnameArray[i] + "...");
                try {
                    client.createConnection(hostnameArray[i]);
                } catch (Exception e) {
                    msg(e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new Exception("VoltDB connection failed.." + e.getMessage(), e);
        }

        return client;

    }

    public static void msg(String message) {

        logger.info(message);
    }

    public static String mapTypeToString(int type) {

        switch (type) {
        case 0:
            return "GET_SUBSCRIBER_DATA";

        case 1:
            return "GET_NEW_DESTINATION";

        case 2:
            return "GET_ACCESS_DATA";

        case 3:
            return "UPDATE_SUBSCRIBER_DATA";

        case 4:
            return "UPDATE_LOCATION";

        case 5:
            return "INSERT_CALL_FORWARDING";

        case 6:
            return "DELETE_CALL_FORWARDING";
        default:
            return "UNKNOWN";

        }
    }

    public String getTps() {
        StringBuffer b = new StringBuffer();

        b.append("Requested TPS=");
        b.append(tps);

        long actualTps = txnCount / timeInSeconds;
        b.append(" Actual TPS=");
        b.append(actualTps);

        return b.toString();
    }

    @Override
    public String toString() {

        String[] SUFFIXES = { "_WALL_MILLIS", "_FAIL_MILLIS", "_LATE_MILLIS", "_VOLT_CLIENT_MS", "_VOLT_CLUSTER_MS" };

        String[] CALLBACKS = { "", "_2" };

        StringBuffer b = new StringBuffer();

        b.append("Requested TPS=");
        b.append(tps);

        long actualTps = txnCount / timeInSeconds;
        b.append(" Actual TPS=");
        b.append(actualTps);

        b.append(" Size=");
        b.append(size);

        b.append(" Duration (seconds)=");
        b.append((endTime - startTime) / 1000);
        b.append(System.lineSeparator());

        for (int i = 0; i < 7; i++) {

            for (int s = 0; s < SUFFIXES.length; s++) {
                for (int c = 0; c < CALLBACKS.length; c++) {

                    StatsHistogram theHist = h.get(mapTypeToString(i) + CALLBACKS[c] + SUFFIXES[s]);

                    if (theHist != null && theHist.hasReports()) {
                        b.append(theHist.toStringShort());
                        b.append(System.lineSeparator());
                    }
                }
            }
            b.append(System.lineSeparator());
        }

        StatsHistogram theHist;

        for (int i = 0; i < 64; i++) {
            theHist = h.get("CPU_" + i);

            if (theHist.hasReports()) {
                b.append(System.lineSeparator());
                b.append("Cpu Statistics for Host ");
                b.append(theHist.toStringShort());
                b.append(System.lineSeparator());

                theHist = h.get("LATENCY_" + i);
                b.append(System.lineSeparator());
                b.append("Latency Stats for host ");
                b.append(theHist.toStringShort());
                b.append(System.lineSeparator());

                theHist = h.get("COMMAND_LOG_BACKLOG_" + i);
                b.append(System.lineSeparator());
                b.append("Command log stats for host ");
                b.append(theHist.toStringShort());
                b.append(System.lineSeparator());

                theHist = h.get("CLIENT_CPU");
                b.append(System.lineSeparator());
                b.append("client CPU ");
                b.append(theHist.toStringShort());
                b.append(System.lineSeparator());

            }
        }

        theHist = h.get("TPS");
        b.append(System.lineSeparator());
        b.append("TPS ");
        b.append(theHist.toStringShort());
        b.append(System.lineSeparator());

        theHist = h.get("CLIENT_CPU");
        b.append(System.lineSeparator());
        b.append("CLIENT_CPU ");
        b.append(theHist.toStringShort());
        b.append(System.lineSeparator());

        return b.toString();
    }

    private int NURand(int size, int x, int y) {

        int rvalue = 0;
        int a = 65535;

        if (size > 1000000) {
            a = 1048575;

            if (size > 10000000) {
                a = 2097151;
            }
        }

        rvalue = ((getRandom(0, a) | getRandom(x, y)));
        rvalue = rvalue % ((y - x) + 1);
        rvalue += x;

        return rvalue;
    }

    private int getRandom(int s, int e) {

        return r.nextInt((e - s)) + s;
    }

    public static void main(String[] args) {

        msg("Parameters:" + Arrays.toString(args));

        final String testname = args[0];
        final String hostnames = args[1];
        long startTps = Integer.parseInt(args[2]);
        final long incTps = Integer.parseInt(args[3]);
        final int size = Integer.parseInt(args[4]);
        final int fkMode = Integer.parseInt(args[5]);
        final int mins = Integer.parseInt(args[6]);
        final int threadCount = Integer.parseInt(args[7]);

        String ok = null;
        boolean dataNeeded = true;

        SafeHistogramCache h = SafeHistogramCache.getInstance();

        while (ok == null) {

            try {

                long expectedTransactions = startTps * mins * 60;

                if (dataNeeded) {
                    long endTime = System.currentTimeMillis() + (1000 * 60 * mins);
                    TatpClient ccMakeData = new TatpClient(hostnames, 42, new Date(endTime), startTps, size, fkMode,
                            -1);
                    try {
                        ccMakeData.createSchemaIfNeeded();
                    } catch (Exception e) {
                        logger.error(e.toString());
                        System.exit(1);

                    }
                    ccMakeData.loadData(size);
                    dataNeeded = false;

                    ccMakeData.disconnect();
                    ccMakeData = null;
                    endTime = System.currentTimeMillis() + (1000 * 60 * mins);

                }

                long endTime = System.currentTimeMillis() + (1000 * 60 * mins);
                TatpClient[] testRunners = new TatpClient[threadCount];
                Thread[] testRunnerThreads = new Thread[threadCount];

                for (int i = 0; i < testRunners.length; i++) {
                    testRunners[i] = new TatpClient(hostnames, 43 + i, new Date(endTime), (startTps / threadCount),
                            size, fkMode, i);
                }

                testRunners[0].setDoStats(true);
                final int partCount = testRunners[0].getPartitionCount();

                File theFile = new File("results" + File.separator + testname + "_" + startTps + "_" + size + "_"
                        + mapFkModeToString(fkMode) + "_" + mins + "_" + partCount + "_" + threadCount + ".dat");
                if (theFile.exists()) {
                    theFile.delete();
                }

                msg("Creating " + testRunners.length + " execution threads...");
                for (int i = 0; i < testRunners.length; i++) {

                    testRunnerThreads[i] = new Thread(testRunners[i]);
                    testRunnerThreads[i].start();
                    msg("Test Runner " + i + " started...");

                }

                long txnCount = 0;

                for (int i = 0; i < testRunners.length; i++) {
                    try {
                        synchronized (testRunnerThreads[i]) {
                            msg("Waiting for Test Runner " + i + " to finish");
                            testRunnerThreads[i].join();
                            msg("Test Runner " + i + " stopped...");
                            txnCount += testRunners[i].getTxnCount();
                        }
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                    }
                }

                msg("Writing " + theFile.getAbsolutePath());
                PrintWriter out = new PrintWriter(theFile.getAbsolutePath());
                out.println("Expected transactions/Actual transactions  = " + expectedTransactions + "/" + txnCount);

                if (txnCount < (expectedTransactions * 0.75)) {
                    ok = "COD: Unable to do 75% of requested transactions...";
                    h.incCounter("ERROR");
                }

                if (h.getCounter("ERROR") > 0) {
                    msg("Halting...");
                    if (ok == null) {
                        ok = "COD: Errors..." + h.getCounter("ERROR");
                    }

                } else {
                    msg("test continuing...");
                }

                if (ok != null) {
                    out.println(ok);
                }

                out.println("RUNNER 0:");
                out.println(testRunners[0].toString());
                out.println(testRunners[0].client.toString());

                for (int i = 0; i < testRunners.length; i++) {
                    out.println("Extra runner " + i);
                    out.println(testRunners[i].getTps());
                }

                out.flush();
                out.close();

                Thread.sleep(DELAY_SECONDS);

                startTps += incTps;

                for (int i = 0; i < testRunners.length; i++) {
                    testRunners[i].disconnect();
                    testRunners[i] = null;
                }

                // msg(tatpRunner.toString());

                if (ok == null) {
                    msg("Waiting " + DELAY_SECONDS + " seconds before next pass at " + startTps + " TPS...");
                    Thread.sleep(DELAY_SECONDS);
                }

            } catch (FileNotFoundException e) {
                logger.error(e.getMessage());
            } catch (InterruptedException e) {
                logger.error(e.getMessage());
            }

        }

        msg("Finished");

    }

    private void createSchemaIfNeeded() throws Exception {

        VoltDBSchemaBuilder b = new VoltDBSchemaBuilder(ddlStatements, procStatements, "tatpProcs.jar", client,
                "voltdbtatp.db");

        b.loadClassesAndDDLIfNeeded(testProcName, testParams);

    }

    @Override
    public void run() {
        this.runBenchmark();

    }

    private void disconnect() {
        try {

            client.drain();
            callbackClient.drain();
            statsClient.drain();

            client.close();
            callbackClient.close();
            statsClient.close();

        } catch (InterruptedException e) {
            logger.error(e.getMessage());
        } catch (NoConnectionsException e) {
            logger.error(e.getMessage());
        }

        client = null;
        callbackClient = null;
        statsClient = null;

    }

    /**
     * @return the txnCount
     */
    public long getTxnCount() {
        return txnCount;
    }

    /**
     * @return the doStats
     */
    public boolean isDoStats() {
        return doStats;
    }

    /**
     * @param doStats the doStats to set
     */
    public void setDoStats(boolean doStats) {
        this.doStats = doStats;
    }

    private static String mapFkModeToString(int fkMode) {
        String description = "";

        switch (fkMode) {
        case FKMODE_QUERY_ALL_PARTITIONS_FIRST:
            description = "ALL_PARTITIONS_FIRST";
            break;
        case FKMODE_TASK_ALL_PARTITIONS:
            description = "ALL_PARTITIONS_TASKED";
            break;
        case FKMODE_MULTI_QUERY_FIRST:
            description = "MULTI_QUERY_FIRST";
            break;
        case FKMODE_CACHED_ANSWER:
            description = "FKMODE_CACHED_ANSWER";
            break;
        case FKMODE_COMPOUND_PROCS:
            description = "FKMODE_COMPOUND_PROCS";
            break;

        }

        return description;
    }

}
