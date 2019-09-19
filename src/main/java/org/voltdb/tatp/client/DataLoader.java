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
import java.util.Random;

import org.voltdb.VoltTable;
import org.voltdb.VoltType;
import org.voltdb.client.Client;
import org.voltdb.client.ClientConfig;
import org.voltdb.client.ClientFactory;
import org.voltdb.client.NoConnectionsException;
import org.voltdb.client.ProcCallException;

public class DataLoader {

	public static void main(String[] args) {

		VoltProcedureUpdateCallBack theCallback = new VoltProcedureUpdateCallBack();

		Client client = null;
		try {
			final int LIMIT = Integer.parseInt(args[0]);

			try {

				Random r = new Random(42);

				client = connectVoltDB();

				int recordCount = getRecordCount(client);
				System.out.println(recordCount + " rows seen before Reset");

				client.callProcedure(theCallback, "Reset");

				recordCount = getRecordCount(client);
				System.out.println(recordCount + " rows seen after Reset");

				long subId = 0;
				String subIdString;

				byte[] bitArray = new byte[10];
				byte[] hexArray = new byte[10];
				byte[] byteArray = new byte[10];

				long mscLocation = 0;
				long vlrLocation = 0;

				int lineCount = 0;
				long start = System.currentTimeMillis();

				for (int i = 0; i < LIMIT; i++) {
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
					
					client.callProcedure(theCallback, "LoadSubscriber", subId, subIdString, bitArray[0], bitArray[1],
							bitArray[2], bitArray[3], bitArray[4], bitArray[5], bitArray[6], bitArray[7], bitArray[8],
							bitArray[9], hexArray[0], hexArray[1], hexArray[2], hexArray[3], hexArray[4], hexArray[5],
							hexArray[6], hexArray[7], hexArray[8], hexArray[9], byteArray[0], byteArray[1],
							byteArray[2], byteArray[3], byteArray[4], byteArray[5], byteArray[6], byteArray[7],
							byteArray[8], byteArray[9], mscLocation, vlrLocation,i);

				}
				
				recordCount = getRecordCount(client);
				System.out.println(recordCount + " rows seen after insert");

				System.out.println(lineCount + " rows processed in " + (System.currentTimeMillis() - start) + "ms");

				start = System.currentTimeMillis();
				client.drain();
				System.out.println(theCallback.getDupCount() + " dup seen");
				theCallback.setDupCount(0);

				

			} catch (Exception e) {
				e.printStackTrace();
				throw new Exception(e.getMessage(), e);
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {

			/* Client clear */
			try {
				if (client != null) {
					client.drain();
					client.close();
				}

			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (NoConnectionsException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

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
	 * @return
	 * @throws Exception
	 */
	public static Client connectVoltDB() throws Exception {
		Client client = null;
		ClientConfig config = null;
		try {
			config = new ClientConfig(); //"admin","idontknow");
			config.setMaxTransactionsPerSecond(200000);
			config.setTopologyChangeAware(true);
			config.setReconnectOnConnectionLoss(true);

			client = ClientFactory.createClient(config);
			client.createConnection("127.0.0.1" /*"172.31.23.43"*/);

		} catch (Exception e) {
			e.printStackTrace();
			throw new Exception("VoltDB connection failed.." + e.getMessage(), e);
		}

		return client;

	}
}
