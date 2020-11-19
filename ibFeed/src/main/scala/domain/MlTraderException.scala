package domain

class MlTraderError extends Exception

class FeedError extends MlTraderError
class BrokerError extends MlTraderError
class FeedConnectError extends MlTraderError

