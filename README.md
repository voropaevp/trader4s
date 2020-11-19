trader4s
====

Project goal is to provide reliable execution and very fast market data access. For the development of the next generation
algorithmic trading software, that requires access to large data sets, i.e. AI.  

Project is still in active development. Expect breaking changes. 

To achieve performance necessary for the machine learning algorithms and vast amounts of financial data,
database of choice is `Apache Cassandra`. Functional scala streaming `fs2` and datastax driver 4.0 for cassandra have 
little overhead. It is not hard tuned, but native driver and lean code are enough to saturate `IBKR` gateway. 

`Interactive Brokers` is the largest online broker, that provides good quality, high resolution financial data and 
analytics. This is the only broker that had been implemented.

Broker integration is working though the [IBC](https://github.com/IbcAlpha/IBC) project packaged with
[ibgateway](https://github.com/okinta/ibgateway). I advise on forking and building your own docker image to minimize 
security risks. 


Roadmap
--

- **IbFeed** release automatic sinking `ibkr` historic and realtime bar data into `cassandra`. You just provide list of 
  contracts to get the data and feed will do the rest.  
- [Ta4j](https://github.com/ta4j/ta4j) integration for inline technical analysis.
- Docker packaging and build scripts
- Python bindings for cassandra tables 
- **IbBroker** release with back testing
- [javacpp](https://github.com/bytedeco/javacpp-presets/) integration for using `Tensorflow` models
- [insync](https://github.com/erdewit/ib_insync) for python models. 
- Docs and examples.
      

Deployment
--

Software is not ready for deployment at the moment. 

- Download the [TwsApi.jar](https://interactivebrokers.github.io/) and fill out the path to it in your `build.sbt`
- Build the ibgateway image:
- Fill out the `docker-compose-tmlt.yaml` template and run:
`docker-compose up -d` 

Configuration
--
TBD

Back Testing
---
TBD

Strategies
---
TBD



