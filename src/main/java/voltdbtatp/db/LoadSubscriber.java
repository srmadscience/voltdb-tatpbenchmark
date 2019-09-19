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

import java.util.Random;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;

public class LoadSubscriber extends VoltProcedure {

	public static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	public static final String DIGITS = "0123456789";

	public static final SQLStmt x = new SQLStmt("INSERT INTO subscriber  " + "(s_id , sub_nbr , "
			+ "bit_1 , bit_2 , bit_3 , bit_4 , bit_5 ,  " + "bit_6 , bit_7 , bit_8 , bit_9 , bit_10 ,  "
			+ "hex_1 , hex_2 , hex_3 , hex_4 , hex_5 ,  " + "hex_6 , hex_7 , hex_8 , hex_9 , hex_10 ,  "
			+ "byte2_1 , byte2_2 , byte2_3 , byte2_4 , byte2_5 ,  "
			+ "byte2_6 , byte2_7 , byte2_8 , byte2_9 , byte2_10 ,  " + "msc_location , vlr_location ) "
			+ "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);");

	public static final SQLStmt y = new SQLStmt(
			"INSERT INTO access_info (s_id,ai_type,data1,data2,data3,data4 ) VALUES (?,?,?,?,?,?);");

	public static final SQLStmt sqlSpecialFacility = new SQLStmt(
			"INSERT INTO special_facility (s_id,sf_type,is_active,error_cntrl,data_a,data_b ) VALUES (?,?,?,?,?,?);");

	public static final SQLStmt sqlCallForwarding = new SQLStmt(
			"INSERT INTO call_forwarding (s_id,sf_type,start_time,end_time,numberx ) VALUES (?,?,?,?,?);");

	/*
	 * access_info (s_id BIGINT NOT NULL, ai_type TINYINT NOT NULL, data1
	 * SMALLINT, data2 SMALLINT, data3 VARCHAR(3), data4 VARCHAR(5), PRIMARY KEY
	 * (s_id, ai_type),
	 * 
	 * s_id BIGINT NOT NULL, sf_type TINYINT NOT NULL, is_active TINYINT NOT
	 * NULL, error_cntrl SMALLINT, data_a SMALLINT, data_b VARCHAR(5)
	 * 
	 * 
	 * (s_id BIGINT NOT NULL, sf_type TINYINT NOT NULL, start_time TINYINT NOT
	 * NULL, end_time TINYINT, numberx VARCHAR(15),
	 * 
	 */

	public VoltTable[] run(long s_id, String sub_nbr, byte bit_1, byte bit_2, byte bit_3, byte bit_4, byte bit_5,
			byte bit_6, byte bit_7, byte bit_8, byte bit_9, byte bit_10, byte hex_1, byte hex_2, byte hex_3, byte hex_4,
			byte hex_5, byte hex_6, byte hex_7, byte hex_8, byte hex_9, byte hex_10, byte byte2_1, byte byte2_2,
			byte byte2_3, byte byte2_4, byte byte2_5, byte byte2_6, byte byte2_7, byte byte2_8, byte byte2_9,
			byte byte2_10, long msc_location, long vlr_location, long randomSeed) throws VoltAbortException {

		voltQueueSQL(x, s_id, sub_nbr, bit_1, bit_2, bit_3, bit_4, bit_5, bit_6, bit_7, bit_8, bit_9, bit_10, hex_1,
				hex_2, hex_3, hex_4, hex_5, hex_6, hex_7, hex_8, hex_9, hex_10, 2_1, 2_2, 2_3, 2_4, 2_5, 2_6, 2_7, 2_8,
				2_9, 2_10, msc_location, vlr_location);

		Random r = new Random(randomSeed);

		for (int i = 0; i < 4; i++) {

			int rnd25pct = r.nextInt(4);

			if (rnd25pct == 0) {

				voltQueueSQL(y, s_id, i, r.nextInt(256), r.nextInt(256), makeRandString(ALPHABET,r, 3),
						makeRandString(ALPHABET,r, 5));

			}

		}

		for (int i = 0; i < 4; i++) {

			int rnd25pct = r.nextInt(4);

			if (rnd25pct == 0) {

				int isActive = 0;

				if (r.nextInt(100) < 85) {
					isActive = 1;
				}

				voltQueueSQL(sqlSpecialFacility, s_id, i, isActive, r.nextInt(256), r.nextInt(256), makeRandString(ALPHABET, r, 5));

				int rnd35pct_2 = r.nextInt(3);

				for (int j = 0; j < rnd35pct_2; j++) {

					int startTime = 0;

					if (j == 1) {
						startTime = 8;
					} else if (j == 2) {
						startTime = 16;
					}

					int endTime = startTime + 1 + r.nextInt(8);

					voltQueueSQL(sqlCallForwarding, s_id, i, startTime, endTime , makeRandString(DIGITS, r, 15));

				}
			}

		}

		return voltExecuteSQL(true);
	}

	static String  makeRandString(String listofchars, Random r, int length) {
		char[] text = new char[length];
		for (int i = 0; i < length; i++) {
			text[i] = listofchars.charAt(r.nextInt(listofchars.length()));
		}
		return new String(text);
	}

	

}
