package model.datastax.ib.feed.codec
import scala.reflect._
import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.codec.registry.MutableCodecRegistry
import com.datastax.oss.driver.api.core.`type`.codec.{ExtraTypeCodecs, TypeCodec, TypeCodecs}
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import com.datastax.oss.driver.api.core.`type`.{DataTypes, DataType => DxDataType}
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

  def registerAll(reg: MutableCodecRegistry): Unit =
    astEncoders.foreach(reg.register)

  lazy val DataTypeCodec: TypeCodec[DataType]     =  CqlStringToAstCodec(DataType.apply).asInstanceOf[TypeCodec[DataType]]
  lazy val ReqStateCodec: TypeCodec[RequestState] =  CqlStringToAstCodec(RequestState.apply).asInstanceOf[TypeCodec[RequestState]]
  lazy val ReqTypeCodec: TypeCodec[RequestType]   =  CqlStringToAstCodec(RequestType.apply).asInstanceOf[TypeCodec[RequestType]]
  lazy val BarSizeCodec: TypeCodec[BarSize]       =  CqlStringToAstCodec(BarSize.apply).asInstanceOf[TypeCodec[BarSize]]

  lazy val astEncoders = Seq(
    CqlStringToAstCodec(Exchange.apply),
    ExtraTypeCodecs.optionalOf(TypeCodecs.INT),
    ExtraTypeCodecs.optionalOf(TypeCodecs.ASCII),
    ExtraTypeCodecs.optionalOf(TypeCodecs.DOUBLE),
    ExtraTypeCodecs.optionalOf(TypeCodecs.TEXT),
    BarSizeCodec,
    DataTypeCodec,
    ReqStateCodec,
    ReqTypeCodec,
    CqlStringToAstCodec(BarSize.apply),
    CqlStringToAstCodec(SecurityType.apply)
  )
}
