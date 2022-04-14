DROP PROCEDURE Reset IF EXISTS;
DROP PROCEDURE DeleteCallForwarding IF EXISTS;
DROP PROCEDURE GetAccessData IF EXISTS;
DROP PROCEDURE GetNewDestination IF EXISTS;
DROP PROCEDURE GetSubscriberData IF EXISTS;
DROP PROCEDURE UpdateSubscriberData IF EXISTS;
DROP PROCEDURE InsertCallForwarding IF EXISTS;
DROP PROCEDURE LoadSubscriber IF EXISTS;
DROP PROCEDURE UpdateLocation IF EXISTS;
DROP PROCEDURE UpdateLocationCompound IF EXISTS;
DROP PROCEDURE InsertCallForwardingCompound IF EXISTS;
DROP PROCEDURE DeleteCallForwardingCompound IF EXISTS;
DROP PROCEDURE MapSubStringToNumberAllPartitions IF EXISTS; 
DROP PROCEDURE MapSubStringToNumberNoView IF EXISTS;
DROP PROCEDURE UpdateLocationMultiPartition IF EXISTS;
DROP PROCEDURE InsertCallForwardingMultiPartition IF EXISTS;
DROP PROCEDURE DeleteCallForwardingMultiPartition IF EXISTS;
DROP PROCEDURE UpdateSubNbr IF EXISTS;

DROP TABLE subscriber IF EXISTS;
DROP TABLE subscriber_nbr_map IF EXISTS;
DROP TABLE access_info IF EXISTS;
DROP TABLE special_facility IF EXISTS;
DROP TABLE call_forwarding IF EXISTS;

