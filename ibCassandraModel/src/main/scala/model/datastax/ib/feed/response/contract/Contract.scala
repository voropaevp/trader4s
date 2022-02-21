package model.datastax.ib.feed.response.contract

import com.datastax.oss.driver.api.mapper.annotations.{Computed, Entity, PartitionKey, Transient}
import model.datastax.ib.feed.ast.{Exchange, SecurityType}
import com.ib.client.{TagValue, Contract => IbContract, ContractDetails => IbContractDetails}
import model.datastax.ib.feed.response.Response

import java.time.Instant
import java.util.{Optional, List => JList}
import scala.annotation.meta.field
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters.RichOptional

@Entity
case class Contract(
  //The unique IB contractReq identifier.
  @(PartitionKey @field) contId: Int,
  //The underlying's asset symbol.
  symbol: String,
  //The security's type: STK - stock (or ETF) OPT - option FUT - future IND - index FOP -
  // futures option CASH - forex pair BAG - combo WAR - warrant BOND- bond CMDTY- commodity
  // NEWS- news FUND- mutual fund.
  secType: SecurityType,
  // The contractReq's last trading day or contractReq month (for Options and Futures). Strings with
  // format YYYYMM will be interpreted as the Contract Month whereas YYYYMMDD will be
  // interpreted as Last Trading Day.
  lastTradeDateOrContractMonth: Optional[String],
  //  The option's strike price.
  strike: Double,
  //  Either Put or Call (i.e. Options). Valid values are P, PUT, C, CALL.
  right: Optional[String],
  //  The instrument's multiplier (i.e. options, futures).
  multiplier: Optional[String],
  //  The destination exchange.
  exchange: Exchange,
  //  The underlying's currency.
  currency: Optional[String],
  //  The contractReq's symbol within its primary exchange For options, this will be the OCC symbol.
  localSymbol: Optional[String],
  //  The contractReq's primary exchange. For smart routed contracts, used to define contractReq in
  //  case of ambiguity. Should be defined as native exchange of contractReq, e.g. ISLAND for MSFT
  //  For exchanges which contain a period in name, will only be part of exchange name prior to
  //  period, i.e. ENEXT for ENEXT.BE.
  primaryExch: Optional[Exchange],
  //  The trading class name for this contractReq. Available in TWS contractReq description window as
  //  well. For example, GBL Dec '13 future's trading class is "FGBL".
  tradingClass: Optional[String],
  //  If set to true, contractReq details request and historical data queries can be performed
  //  to expired futures contracts. Expired options or other instrument types are not available.
  includeExpired: Boolean,
  //  Security's identifier when querying contractReq's details or placing orders ISIN - example:
  //  apple: US0378331005 CUSIP - example: apple: 037833100.
  secId: Optional[String],
  //  Identifier of the security type.
  secIdType: Optional[String],
  //  Description of the combo legs.
  comboLegsDescription: Optional[String],
  //  The legs of a combined contractReq definition.
  comboLegs: JList[ComboLeg],
  //  Delta and underlying price for Delta-Neutral combo orders. Underlying (STK or FUT),
  //  and underlying price goes into this attribute.
  deltaNeutralContract: DeltaNeutralContract,
  //The market name for this product.
  marketName: Optional[String],
  //The minimum allowed price variation. Note that many securities vary their minimum tick size according to their
  // price. This value will only show the smallest of the different minimum tick sizes regardless of the product's
  // price.
  // Full information about the minimum increment price structure can be obtained with the reqMarketRule function or
  // the IB Contract and Security Search site.
  minTick: Double,
  // Allows execution and strike prices to be reported consistently with market data, historical data and the order
  // price, i.e. Z on LIFFE is reported in Index points and not GBP. In TWS versions prior to 972, the price
  // is used in defining future option strike prices (e.g. in the API the strike is specified in dollars, but in TWS
  // it is specified in cents). In TWS versions 972 and higher, the price magnifier is not used in defining futures
  // option strike prices so they are consistent in TWS and the API.

  priceMagnifier: Int,
  //Supported order types for this product.
  orderTypes: Optional[String],
  //Valid exchange fields when placing an order for this contractReq.
  //The list of exchanges will is provided in the same order as the corresponding MarketRuleIds list.
  validExchanges: Optional[String],
  // For derivatives, the contractReq ID (conID) of the underlying instrument.
  underConId: Int,
  // Descriptive name of the product.
  longName: Optional[String],
  //    Typically the contractReq month of the underlying for a Future contractReq.
  contractMonth: Optional[String],
  //    The industry classification of the underlying/product. For example, Financial.
  industry: Optional[String],
  //    The industry category of the underlying. For example, InvestmentSvcd.contractReq.
  category: Optional[String],
  //    The industry subcategory of the underlying. For example, Brokerage.
  subcategory: Optional[String],
  //    The time zone for the trading hours of the product. For example, EST.
  timeZoneId: Optional[String],
  //    The trading hours of the product. This value will contain the trading hours of the current day as well as
  //    the next's. For example, 20090507:0700-1830,1830-2330;20090508:CLOSED. In TWS versions 965+ there is an option
  //    in the Global Configuration API settings to return 1 month of trading hours. In TWS version 970+, the format
  //    includes the date of the closing time to clarify potential ambiguity,
  //    ex: 20180323:0400-20180323:2000;20180326:0400-20180326:2000 The trading hours will correspond to the hours for
  //    the product on the associated exchange. The same instrument can have different hours on different exchanges.
  tradingHours: Optional[String],
  //    The liquid hours of the product. This value will contain the liquid hours (regular trading hours) of the
  //    contractReq on the specified exchange. Format for TWS versions until
  //    969: 20090507:0700-1830,1830-2330;20090508:CLOSED. In TWS versions 965+ there is an option in
  //    the Global Configuration API settings to return 1 month of trading hours. In TWS v970 and above, the format
  //    includes the date of the closing time to clarify potential ambiguity,
  //    e.g. 20180323:0930-20180323:1600;20180326:0930-20180326:1600.
  liquidHours: Optional[String],
  //    Contains the Economic Value Rule name and the respective optional argument. The two values
  //    should be separated by a colon. For example, aussieBond:YearsToExpiration=3. When the optional argument
  //    is not present, the first value will be followed by a colon.
  evRule: Optional[String],
  //    Tells you approximately how much the market value of a contractReq would change if the price were to change by 1.
  //    It cannot be used to get market value by multiplying the price by the approximate multiplier.
  evMultiplier: Double,
  mdSizeMultiplier: Int,
  //    Aggregated group Indicates the smart-routing group to which a contractReq belongs.
  //    contracts which cannot be smart-routed have aggGroup = -1.
  aggGroup: Int,
  //  A list of contractReq identifiers that the customer is allowed to view. CUSIP/ISIN/etcd.contractReq. For US stocks,
  //  receiving the ISIN requires the CUSIP market data subscription. For Bonds, the CUSIP or ISIN is input
  //  directly into the symbol field of the Contract class.
  secIdList: JList[SecId],
  //    For derivatives, the symbol of the underlying contractReq.
  underSymbol: Optional[String],
  //    For derivatives, returns the underlying security type.
  underSecType: Optional[String],
  //    The list of market rule IDs separated by comma Market rule IDs can be used to determine the minimum
  //    price increment at a given price.
  marketRuleIds: Optional[String],
  //    Real expiration date. Requires TWS 968+ and API v973.04+. Python API specifically requires API v973.06+.
  realExpirationDate: Optional[String],
  //    Last trade time.
  lastTradeTime: Optional[String],
  //    Stock type.
  stockType: Optional[String],
  //    The nine-character bond CUSIP. For Bonds only. Receiving CUSIPs requires a CUSIP market data subscription.
  cusip: Optional[String],
  //    Identifies the credit rating of the issuer. This field is not currently available from the TWS API. For Bonds
  //    only. A higher credit rating generally indicates a less risky investment. Bond ratings are from Moody's and S&P
  //    respectively. Not currently implemented due to bond market data restrictions.
  ratings: Optional[String],
  //    A description string containing further descriptive information about the bond. For Bonds only.
  descAppend: Optional[String],
  //    The type of bond, such as "CORP.".
  bondType: Optional[String],
  //    The type of bond coupon. This field is currently not available from the TWS API. For Bonds only.
  couponType: Optional[String],
  //    If true, the bond can be called by the issuer under certain conditions. This field is currently not available
  //    from the TWS API. For Bonds only.
  callable: Boolean,
  //  Values are True or False. If true, the bond can be sold back to the issuer under certain conditions. This field
  //  is currently not available from the TWS API. For Bonds only.
  putable: Boolean,
  //  The interest rate used to calculate the amount you will receive in interest payments over the course of the year.
  //  This field is currently not available from the TWS API. For Bonds only.
  coupon: Double,
  //  Values are True or False. If true, the bond can be converted to stock under certain conditions. This field is
  //  currently not available from the TWS API. For Bonds only.
  convertible: Boolean,
  //  he date on which the issuer must repay the face value of the bond. This field is currently not available from
  //  the TWS API. For Bonds only. Not currently implemented due to bond market data restrictions.
  maturity: Optional[String],
  //    The date the bond was issued. This field is currently not available from the TWS API. For Bonds only. Not
  //    currently implemented due to bond market data restrictions.
  issueDate: Optional[String],
  //    Only if bond has embedded options. This field is currently not available from the TWS API. Refers to callable
  //    bonds and puttable bonds. Available in TWS description window for bonds.
  nextOptionDate: Optional[String],
  //    Type of embedded option. This field is currently not available from the TWS API. Only if bond has embedded
  //    options.
  nextOptionType: Optional[String],
  //    Only if bond has embedded options. This field is currently not available from the TWS API. For Bonds only.
  nextOptionPartial: Boolean,
  //  If populated for the bond in IB's database. For Bonds only.
  notes: Optional[String]
  //
  // Note: 3 following props mentioned in documentation but no implemented in java API
  //
  //    Order's minimal size.
  //  minSize: Optional[BigDecimal],
  //    Order's size increment.
  //  sizeIncrement: Optional[BigDecimal],
  //    Order's suggested size increment.
  //  suggestedSizeIncrement: Optional[BigDecimal]
) extends Response {

  @Transient
  lazy val asEntry: ContractEntry = ContractEntry(
    symbol                       = symbol,
    secType                      = secType,
    lastTradeDateOrContractMonth = lastTradeDateOrContractMonth.toScala,
    strike                       = Option.when((strike - .0d).abs > .000001d)(strike),
    right                        = right.toScala,
    multiplier                   = multiplier.toScala,
    exchange                     = exchange,
    currency                     = currency.toScala,
    localSymbol                  = localSymbol.toScala,
    tradingClass                 = tradingClass.toScala,
    includeExpired               = Option.when(includeExpired)(true),
    primaryExch                  = primaryExch.toScala,
    secIdType                    = secIdType.toScala,
    secId                        = secId.toScala,
    comboLegsDescription         = comboLegsDescription.toScala,
    comboLegs                    = Option.when(!comboLegs.isEmpty)(comboLegs.asScala.toList),
    deltaNeutralContract         = Option(deltaNeutralContract)
  )

  @Transient
  val entryHash: Int = asEntry.hashCode()

  @Transient
  lazy val ibContract: IbContract = {
    val c = new IbContract(
      this.contId,
      this.symbol,
      this.secType.toString,
      this.lastTradeDateOrContractMonth.toScala.orNull,
      this.strike,
      this.right.toScala.orNull,
      this.multiplier.toScala.orNull,
      this.exchange.name,
      this.currency.toScala.orNull,
      this.localSymbol.toScala.orNull,
      this.tradingClass.toScala.orNull,
      this.comboLegs.asScala.map(_.asIb).asJava,
      this.primaryExch.toScala.orNull.toString,
      this.includeExpired,
      this.secIdType.toScala.orNull,
      this.secId.toScala.orNull
    )
    c.deltaNeutralContract(if (this.deltaNeutralContract == null) null else this.deltaNeutralContract.toIb)
    c
  }

  @Transient lazy val ibContractDetails: IbContractDetails = {
    val cd = new IbContractDetails(
      ibContract,
      this.marketName.toScala.orNull,
      this.minTick,
      this.orderTypes.toScala.orNull,
      this.validExchanges.toScala.orNull,
      this.underConId,
      this.longName.toScala.orNull,
      this.contractMonth.toScala.orNull,
      this.industry.toScala.orNull,
      this.category.toScala.orNull,
      this.subcategory.toScala.orNull,
      this.timeZoneId.toScala.orNull,
      this.tradingHours.toScala.orNull,
      this.liquidHours.toScala.orNull,
      this.evRule.toScala.orNull,
      this.evMultiplier,
      this.mdSizeMultiplier,
      this.aggGroup,
      this.underSymbol.toScala.orNull,
      this.underSecType.toScala.orNull,
      this.marketRuleIds.toScala.orNull,
      this.realExpirationDate.toScala.orNull,
      this.lastTradeTime.toScala.orNull,
      this.stockType.toScala.orNull
    )

    cd.priceMagnifier(this.priceMagnifier)
    cd.secIdList(this.secIdList.asScala.map(p => new TagValue(p.m_tag, p.m_value)).asJava)
    cd.cusip(this.cusip.toScala.orNull)
    cd.ratings(this.ratings.toScala.orNull)
    cd.descAppend(this.descAppend.toScala.orNull)
    cd.bondType(this.bondType.toScala.orNull)
    cd.couponType(this.couponType.toScala.orNull)
    cd.callable(this.callable)
    cd.putable(this.putable)
    cd.coupon(this.coupon)
    cd.convertible(this.convertible)
    cd.maturity(this.maturity.toScala.orNull)
    cd.issueDate(this.issueDate.toScala.orNull)
    cd.nextOptionDate(this.nextOptionDate.toScala.orNull)
    cd.nextOptionType(this.nextOptionType.toScala.orNull)
    cd.nextOptionPartial(this.nextOptionPartial)
    cd.notes(this.notes.toScala.orNull)
    cd
  }
}

object Contract {

  def apply(cd: IbContractDetails): Contract = new Contract(
    contId                       = cd.contract.conid,
    symbol                       = cd.contract.symbol,
    secType                      = SecurityType(cd.contract.getSecType),
    lastTradeDateOrContractMonth = Optional.ofNullable(cd.contract.lastTradeDateOrContractMonth),
    strike                       = cd.contract.strike,
    right                        = Optional.ofNullable(cd.contract.getRight),
    multiplier                   = Optional.ofNullable(cd.contract.multiplier),
    exchange                     = Exchange(cd.contract.exchange),
    currency                     = Optional.ofNullable(cd.contract.currency),
    localSymbol                  = Optional.ofNullable(cd.contract.localSymbol),
    primaryExch                  = Optional.ofNullable(Exchange(cd.contract.primaryExch)),
    tradingClass                 = Optional.ofNullable(cd.contract.tradingClass),
    includeExpired               = cd.contract.includeExpired,
    secIdType                    = Optional.ofNullable(cd.contract.getSecIdType),
    secId                        = Optional.ofNullable(cd.contract.secId),
    comboLegsDescription         = Optional.ofNullable(cd.contract.comboLegsDescrip),
    comboLegs                    = cd.contract.comboLegs.asScala.toList.map(ComboLeg.apply).asJava,
    deltaNeutralContract =
      if (cd.contract.deltaNeutralContract == null)
        null
      else
        DeltaNeutralContract(cd.contract.deltaNeutralContract),
    marketName         = Optional.ofNullable(cd.marketName),
    minTick            = cd.minTick,
    priceMagnifier     = cd.priceMagnifier,
    orderTypes         = Optional.ofNullable(cd.orderTypes),
    validExchanges     = Optional.ofNullable(cd.validExchanges),
    underConId         = cd.underConid,
    longName           = Optional.ofNullable(cd.longName),
    contractMonth      = Optional.ofNullable(cd.contractMonth),
    industry           = Optional.ofNullable(cd.industry),
    category           = Optional.ofNullable(cd.category),
    subcategory        = Optional.ofNullable(cd.subcategory),
    timeZoneId         = Optional.ofNullable(cd.timeZoneId),
    tradingHours       = Optional.ofNullable(cd.tradingHours),
    liquidHours        = Optional.ofNullable(cd.liquidHours),
    evRule             = Optional.ofNullable(cd.evRule),
    evMultiplier       = cd.evMultiplier,
    mdSizeMultiplier   = cd.mdSizeMultiplier,
    aggGroup           = cd.aggGroup,
    secIdList          = cd.secIdList.asScala.toList.map(t => SecId(t.m_tag, t.m_value)).asJava,
    underSymbol        = Optional.ofNullable(cd.underSymbol),
    underSecType       = Optional.ofNullable(cd.underSecType),
    marketRuleIds      = Optional.ofNullable(cd.marketRuleIds),
    realExpirationDate = Optional.ofNullable(cd.realExpirationDate),
    lastTradeTime      = Optional.ofNullable(cd.lastTradeTime),
    stockType          = Optional.ofNullable(cd.stockType),
    cusip              = Optional.ofNullable(cd.cusip),
    ratings            = Optional.ofNullable(cd.ratings),
    descAppend         = Optional.ofNullable(cd.descAppend),
    bondType           = Optional.ofNullable(cd.bondType),
    couponType         = Optional.ofNullable(cd.couponType),
    callable           = cd.callable,
    putable            = cd.putable,
    coupon             = cd.coupon,
    convertible        = cd.convertible,
    maturity           = Optional.ofNullable(cd.maturity),
    issueDate          = Optional.ofNullable(cd.issueDate),
    nextOptionDate     = Optional.ofNullable(cd.nextOptionDate),
    nextOptionType     = Optional.ofNullable(cd.nextOptionType),
    nextOptionPartial  = cd.nextOptionPartial,
    notes              = Optional.ofNullable(cd.notes)
  )
}
