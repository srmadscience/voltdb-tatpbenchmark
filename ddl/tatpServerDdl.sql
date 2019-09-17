//tatp_sql
//
// This is an example target database schema file of the TATP Benchmark Suite.
// All the SQL commands defined in this file are executed against the target 
// database when encountering a 'population' directive (mode 'populate') in a TDF file. 
// The commands are processed before the actual population process starts. 
// The commands are processed in the order they appear in this file.

// The valid TATP schema definition follows 

load classes ../jar/tatp_2019_server.jar;


CREATE TABLE subscriber 
	(s_id BIGINT NOT NULL PRIMARY KEY, sub_nbr VARCHAR(15) NOT NULL , 
	bit_1 TINYINT, bit_2 TINYINT, bit_3 TINYINT, bit_4 TINYINT, bit_5 TINYINT, 
	bit_6 TINYINT, bit_7 TINYINT, bit_8 TINYINT, bit_9 TINYINT, bit_10 TINYINT, 
	hex_1 TINYINT, hex_2 TINYINT, hex_3 TINYINT, hex_4 TINYINT, hex_5 TINYINT, 
	hex_6 TINYINT, hex_7 TINYINT, hex_8 TINYINT, hex_9 TINYINT, hex_10 TINYINT, 
	byte2_1 SMALLINT, byte2_2 SMALLINT, byte2_3 SMALLINT, byte2_4 SMALLINT, byte2_5 SMALLINT, 
	byte2_6 SMALLINT, byte2_7 SMALLINT, byte2_8 SMALLINT, byte2_9 SMALLINT, byte2_10 SMALLINT, 
	msc_location BIGINT, vlr_location BIGINT);

create index sub_idx on subscriber(sub_nbr);


PARTITION TABLE subscriber ON COLUMN s_id;

create view sub_nbr_to_sub_map as 
select s_id, count(*) how_many, min(sub_nbr) sub_nbr
from subscriber  
group by s_id;

create index map_idx on sub_nbr_to_sub_map(sub_nbr);

CREATE TABLE access_info 
	(s_id BIGINT NOT NULL, ai_type TINYINT NOT NULL, 
	data1 SMALLINT, data2 SMALLINT, data3 VARCHAR(3), data4 VARCHAR(5), 
	PRIMARY KEY (s_id, ai_type), 
	FOREIGN KEY (s_id) REFERENCES subscriber (s_id));

	PARTITION TABLE access_info ON COLUMN s_id;

	
CREATE TABLE special_facility 
	(s_id BIGINT NOT NULL, sf_type TINYINT NOT NULL, is_active TINYINT NOT NULL, 
	error_cntrl SMALLINT, data_a SMALLINT, data_b VARCHAR(5), 
	PRIMARY KEY (s_id, sf_type), 
	FOREIGN KEY (s_id) REFERENCES subscriber (s_id));

	PARTITION TABLE special_facility ON COLUMN s_id;

	
CREATE TABLE call_forwarding 
	(s_id BIGINT NOT NULL, sf_type TINYINT NOT NULL, start_time TINYINT NOT NULL, 
	end_time TINYINT, numberx VARCHAR(15), 
	PRIMARY KEY (s_id, sf_type, start_time), 
	FOREIGN KEY (s_id, sf_type) REFERENCES special_facility(s_id, sf_type));

	PARTITION TABLE call_forwarding ON COLUMN s_id;

	

 
CREATE PROCEDURE 
   FROM CLASS tatp_server.Reset;

CREATE PROCEDURE 
   PARTITION ON TABLE subscriber COLUMN s_id
   FROM CLASS tatp_server.DeleteCallForwarding;
   
CREATE PROCEDURE 
   PARTITION ON TABLE subscriber COLUMN s_id
   FROM CLASS tatp_server.GetAccessData;
   
CREATE PROCEDURE 
   PARTITION ON TABLE subscriber COLUMN s_id
   FROM CLASS tatp_server.GetNewDestination;
   
CREATE PROCEDURE 
   PARTITION ON TABLE subscriber COLUMN s_id
   FROM CLASS tatp_server.GetSubscriberData;
   
CREATE PROCEDURE 
   PARTITION ON TABLE subscriber COLUMN s_id
   FROM CLASS tatp_server.InsertCallForwarding;
   
CREATE PROCEDURE 
   PARTITION ON TABLE subscriber COLUMN s_id
   FROM CLASS tatp_server.LoadSubscriber;
   
   CREATE PROCEDURE 
   PARTITION ON TABLE subscriber COLUMN s_id
   FROM CLASS tatp_server.UpdateLocation;
   
   CREATE PROCEDURE 
   PARTITION ON TABLE subscriber COLUMN s_id
   FROM CLASS tatp_server.UpdateSubscriberData;
  
CREATE PROCEDURE 
 FROM CLASS tatp_server.MapSubStringToNumber;
 
  CREATE PROCEDURE 
  PARTITION ON TABLE subscriber COLUMN s_id
 FROM CLASS tatp_server.MapSubStringToNumberAllPartitions; 
 
  CREATE PROCEDURE 
 FROM CLASS tatp_server.MapSubStringToNumberNoView;
   
    CREATE PROCEDURE 
 FROM CLASS tatp_server.MapManySubStringToNumber;
 

 
  CREATE PROCEDURE 
  PARTITION ON TABLE subscriber COLUMN s_id
 FROM CLASS tatp_server.MapManySubStringToNumberNoView;