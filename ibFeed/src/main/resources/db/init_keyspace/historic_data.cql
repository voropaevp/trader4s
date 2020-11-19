CREATE KEYSPACE IF NOT EXISTS historic_data
    with replication = {
        'class': 'org.apache.cassandra.locator.SimpleStrategy',
        'replication_factor': '2'
        };
