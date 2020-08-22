Kafka REST Proxy
================

The Kafka REST Proxy provides a RESTful interface to a Kafka cluster. It makes
it easy to produce and consume messages, view the state of the cluster, and
perform administrative actions without using the native Kafka protocol or
clients. Examples of use cases include reporting data to Kafka from any
frontend app built in any language, ingesting messages into a stream processing
framework that doesn't yet support Kafka, and scripting administrative actions.

Quickstart
----------

The following assumes you have Kafka, the schema registry, and an instance of
the REST Proxy running using the default settings and some topics already created.

    # Get a list of topics
    
    $ curl "http://localhost:8080/topics"
    
      [{"name":"test","num_partitions":3},{"name":"test2","num_partitions":1}]
    
    # Get info about one topic
    
    $ curl "http://localhost:8080/topics/?topic=test"
    
      {"name":"test","num_partitions":3}

    # Get partition info about one topic
    
    $ curl "http://localhost:8080/partitions/?topic=test" 
    
      [
        "{"partition":0,"leader":0,"replicas":[{"broker":0,"leader":true,"inSync":true}]}",
        "{"partition":5,"leader":0,"replicas":[{"broker":0,"leader":true,"inSync":true}]}",
        "{"partition":1,"leader":0,"replicas":[{"broker":0,"leader":true,"inSync":true}]}",
        "{"partition":6,"leader":0,"replicas":[{"broker":0,"leader":true,"inSync":true}]}",
        "{"partition":2,"leader":0,"replicas":[{"broker":0,"leader":true,"inSync":true}]}",
        "{"partition":7,"leader":0,"replicas":[{"broker":0,"leader":true,"inSync":true}]}",
        "{"partition":3,"leader":0,"replicas":[{"broker":0,"leader":true,"inSync":true}]}",
        "{"partition":4,"leader":0,"replicas":[{"broker":0,"leader":true,"inSync":true}]}"
      ]
      
    # Get info about one partition in a specific topic
    
    $ curl "http://localhost:8080/partitions/?topic=test&partition=0"
    
      {"partition":0,"leader":0,"replicas":[{"broker":0,"leader":true,"inSync":true}]}

    # Clients send a POST request to rest proxy for sending one or multiple messages.
      # We specify the type of messages in request header 'Content-Type': 
      	'application/vnd.kafka.binary.v1+json' for binary message;
      	'application/vnd.kafka.json.v1+json' for json message.
      
      # We specify the type of producer in request header 'Producer-Type': 
      	If not specified, we goes to default, which is for high throughput, ACK = '1';
      	'Producer-Type: reliable' for high reliable, ACK = 'all'

      # There are two modes to the send request. It's specified by using header 'Request-Type'.

      	# If the client specifies header with 'Request-Type: sync', the client will not receive an ack to the send request from Kafka REST until Kafka REST receives an ack from the destination Kafka cluster. This avoids data loss upon proxy server failures.
      	
      	$ curl -X  POST -H "Request-Type: sync" -H "Content-Type: application/vnd.kafka.binary.v1+json" -d '{ "records": [{"value":"Kafka"}]}' http://localhost:8080/topics/test
      	
      	{ version : 1, Status : SENT, message : {keySchemaId : null, valueSchemaId : null, offsets : [PartitionOffset{partition=7, offset=2, errorCode=null, error='null'}]}} 

      	# If the client specifies header with 'Request-Type: async', or not specified, the client will receive an ack from Kafka REST before the destination Kafka cluster responds. There might be loss of data in this case but will have lower latency.
      	
      	$ curl -X  POST -H "Producer-Type: reliable" -H "Content-Type: application/vnd.kafka.binary.v1+json" -d '{ "records": [{"value":"Kafka"}]}' http://localhost:8080/topics/test
      	
      	{ version : 1, Status : SENT, message : {}}


Installation
------------

To install from source, follow the instructions in the Development section.


Deployment
----------

The REST proxy includes a built-in Jetty server. The wrapper scripts
``bin/kafka-rest-start`` and ``bin/kafka-rest-stop`` are the recommended method of
starting and stopping the service.

Development
-----------

To build a development version, you may need a development versions of
[common](https://github.com/confluentinc/common),
[rest-utils](https://github.com/confluentinc/rest-utils), and
[schema-registry](https://github.com/confluentinc/schema-registry).  After
installing these, you can build the Kafka REST Proxy
with Maven. All the standard lifecycle phases work.

License
-------

The project is licensed under the Apache 2 license.
