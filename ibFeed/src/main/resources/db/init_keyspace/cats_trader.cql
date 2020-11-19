CREATE KEYSPACE IF NOT EXISTS cats_trader
with replication = {
        'class': 'org.apache.cassandra.locator.SimpleStrategy',
        'replication_factor': '2'
        };
