package model.datastax.ib.feed.response.contract

import com.datastax.oss.driver.api.mapper.annotations.{Entity, Transient}
import com.ib.client.{ComboLeg => IbComboLeg}

@Entity
case class ComboLeg(
  //The Contract's IB's unique id.
  contId: Int,
  //Select the relative number of contracts for the leg you are constructing. To help determine the ratio for a specific combination order, refer to the Interactive Analytics section of the User's Guide.
  ratio: Int,
  //The DataType (buy or sell) of the leg:
  action: String,
  //  The destination exchange to which the order will be routed.
  exchange: String,
  //  Specifies whether an order is an open or closing order. For instituational customers to determine if this order is to open or close a position. 0 - Same as the parent security. This is the only option for retail customers.
  //  1 - Open. This value is only valid for institutional customers.
  //  2 - Close. This value is only valid for institutional customers.
  //  3 - Unknown.
  openClose: Int,
  //  For stock legs when doing short selling. Set to 1 = clearing broker, 2 = third party.
  shortSaleSlot: Int,
  //  When ShortSaleSlot is 2, this field shall contain the designated location.
  designatedLocation: String,
  exemptCode: Int
) {
  @Transient lazy val asIb: IbComboLeg = new IbComboLeg(
    this.contId,
    this.ratio,
    this.action,
    this.exchange,
    this.openClose,
    this.shortSaleSlot,
    this.designatedLocation,
    this.exemptCode
  )
}

object ComboLeg {
  def apply(l: IbComboLeg): ComboLeg = new ComboLeg(
    contId             = l.conid,
    ratio              = l.ratio,
    action             = l.getAction,
    exchange           = l.exchange,
    openClose          = l.getOpenClose,
    shortSaleSlot      = l.shortSaleSlot,
    designatedLocation = l.designatedLocation,
    exemptCode         = l.exemptCode
  )
}
