package model.datastax.ib.feed.codec
import scala.reflect._

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.codec.{TypeCodec, TypeCodecs}
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import com.datastax.oss.driver.api.core.`type`.{DataType => DxDataType, DataTypes}
import model.datastax.ib.feed.ast._

import java.nio.ByteBuffer

case class CqlStringToAstCodec[T: ClassTag](
  encT: String => T
) extends TypeCodec[T] {

  override def getJavaType: GenericType[T] = GenericType.of(classTag[T].runtimeClass.asInstanceOf[Class[T]])

  override def getCqlType: DxDataType = DataTypes.TEXT

  override def encode(value: T, protocolVersion: ProtocolVersion): ByteBuffer =
    TypeCodecs.TEXT.encode(value.toString, protocolVersion)

  def decode(bytes: ByteBuffer, protocolVersion: ProtocolVersion): T =
    encT(TypeCodecs.TEXT.decode(bytes, protocolVersion))

  override def format(value: T): String = TypeCodecs.TEXT.format(value.toString)

  override def parse(value: String): T = encT(TypeCodecs.TEXT.parse(value))
}

object CqlStringToAstCodec {
  lazy val DataTypeCodec: CqlStringToAstCodec[DataType]   = CqlStringToAstCodec(DataType.apply)
  lazy val ReqStateCodec: CqlStringToAstCodec[RequestState]   = CqlStringToAstCodec(RequestState.apply)
  lazy val ReqTypeCodec: CqlStringToAstCodec[RequestType] = CqlStringToAstCodec(RequestType.apply)

  lazy val astEncoders = Seq(
    CqlStringToAstCodec(Exchange.apply),
    DataTypeCodec,
    ReqStateCodec,
    ReqTypeCodec,
    CqlStringToAstCodec(BarSize.apply),
    CqlStringToAstCodec(SecurityType.apply)
  )
}
