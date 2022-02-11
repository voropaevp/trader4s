package model.datastax.ib.feed.codec
import scala.reflect._
import com.datastax.oss.driver.api.core.{CqlSession, ProtocolVersion}
import com.datastax.oss.driver.api.core.`type`.codec.registry.MutableCodecRegistry
import com.datastax.oss.driver.api.core.`type`.codec.{ExtraTypeCodecs, TypeCodec, TypeCodecs}
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import com.datastax.oss.driver.api.core.`type`.{DataTypes, UserDefinedType, DataType => DxDataType}

import scala.jdk.OptionConverters._
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

  def registerDerivedCodecs(session: CqlSession, ks: String): Unit = {
    val reg = session.getContext.getCodecRegistry
    val seqIdUdt: UserDefinedType =
      session.getMetadata.getKeyspace(ks).flatMap(_.getUserDefinedType("seq_id")).get
//    val seqIdCodec: TypeCodec[UserDefinedType]           = reg.codecFor(seqIdUdt)
//    reg.asInstanceOf[MutableCodecRegistry].register(TypeCodecs.listOf(seqIdCodec))
  }

  lazy val DataTypeCodec: TypeCodec[DataType]         = CqlStringToAstCodec(DataType.apply)
  lazy val ReqStateCodec: TypeCodec[RequestState]     = CqlStringToAstCodec(RequestState.apply)
  lazy val ReqTypeCodec: TypeCodec[RequestType]       = CqlStringToAstCodec(RequestType.apply)
  lazy val BarSizeCodec: TypeCodec[BarSize]           = CqlStringToAstCodec(BarSize.apply)
  lazy val ExchangeCodec: TypeCodec[Exchange]         = CqlStringToAstCodec(Exchange.apply)
  lazy val SecurityTypeCodec: TypeCodec[SecurityType] = CqlStringToAstCodec(SecurityType.apply)

  lazy val astEncoders = Seq(
    ExchangeCodec,
    BarSizeCodec,
    DataTypeCodec,
    ReqStateCodec,
    ReqTypeCodec,
    SecurityTypeCodec,
    ExtraTypeCodecs.optionalOf(ExchangeCodec),
    ExtraTypeCodecs.optionalOf(TypeCodecs.TEXT)
  )
}
