package domain.broker

import java.net.InetAddress

case class Broker(
  brokerType: String,
  gatewayIp: InetAddress,
  port: Int
)
