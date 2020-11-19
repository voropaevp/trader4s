package model.datastax.ib.feed.response.contract

import com.datastax.oss.driver.api.mapper.annotations.{Entity, PartitionKey, Transient}
import model.datastax.ib.feed.ast.{Exchange, SecurityType}
import com.ib.client.{TagValue, Contract => IbContract, ContractDetails => IbContractDetails}
import model.datastax.ib.feed.response.Response

import scala.annotation.meta.field
import scala.jdk.CollectionConverters._

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
  lastTradeDateOrContractMonth: Option[String],
  //  The option's strike price.
  strike: Double,
  //  Either Put or Call (i.e. Options). Valid values are P, PUT, C, CALL.
  right: Option[String],
  //  The instrument's multiplier (i.e. options, futures).
  multiplier: Option[String],
  //  The destination exchange.
  exchange: Exchange,
  //  The underlying's currency.
  currency: Option[String],
  //  The contractReq's symbol within its primary exchange For options, this will be the OCC symbol.
  localSymbol: Option[String],
  //  The contractReq's primary exchange. For smart routed contracts, used to define contractReq in
  //  case of ambiguity. Should be defined as native exchange of contractReq, e.g. ISLAND for MSFT
  //  For exchanges which contain a period in name, will only be part of exchange name prior to
  //  period, i.e. ENEXT for ENEXT.BE.
  primaryExch: Option[Exchange],
  //  The trading class name for this contractReq. Available in TWS contractReq description window as
  //  well. For example, GBL Dec '13 future's trading class is "FGBL".
  tradingClass: Option[String],
  //  If set to true, contractReq details request and historical data queries can be performed
  //  to expired futures contracts. Expired options or other instrument types are not available.
  includeExpired: Boolean,
  //  Security's identifier when querying contractReq's details or placing orders ISIN - example:
  //  apple: US0378331005 CUSIP - example: apple: 037833100.
  secId: Option[String],
  //  Identifier of the security type.
  secIdType: Option[String],
  //  Description of the combo legs.
  comboLegsDescription: Option[String],
  //  The legs of a combined contractReq definition.
  comboLegs: List[ComboLeg],
  //  Delta and underlying price for Delta-Neutral combo orders. Underlying (STK or FUT),
  //  and underlying price goes into this attribute.
  deltaNeutralContract: Option[DeltaNeutralContract],
  //The market name for this product.
  marketName: Option[String],
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
  orderTypes: Option[String],
  //Valid exchange fields when placing an order for this contractReq.
  //The list of exchanges will is provided in the same order as the corresponding MarketRuleIds list.
  validExchanges: Option[String],
  // For derivatives, the contractReq ID (conID) of the underlying instrument.
  underConId: Int,
  // Descriptive name of the product.
  longName: Option[String],
  //    Typically the contractReq month of the underlying for a Future contractReq.
  contractMonth: Option[String],
  //    The industry classification of the underlying/product. For example, Financial.
  industry: Option[String],
  //    The industry category of the underlying. For example, InvestmentSvcd.contractReq.
  category: Option[String],
  //    The industry subcategory of the underlying. For example, Brokerage.
  subcategory: Option[String],
  //    The time zone for the trading hours of the product. For example, EST.
  timeZoneId: Option[String],
  //    The trading hours of the product. This value will contain the trading hours of the current day as well as
  //    the next's. For example, 20090507:0700-1830,1830-2330;20090508:CLOSED. In TWS versions 965+ there is an option
  //    in the Global Configuration API settings to return 1 month of trading hours. In TWS version 970+, the format
  //    includes the date of the closing time to clarify potential ambiguity,
  //    ex: 20180323:0400-20180323:2000;20180326:0400-20180326:2000 The trading hours will correspond to the hours for
  //    the product on the associated exchange. The same instrument can have different hours on different exchanges.
  tradingHours: Option[String],
  //    The liquid hours of the product. This value will contain the liquid hours (regular trading hours) of the
  //    contractReq on the specified exchange. Format for TWS versions until
  //    969: 20090507:0700-1830,1830-2330;20090508:CLOSED. In TWS versions 965+ there is an option in
  //    the Global Configuration API settings to return 1 month of trading hours. In TWS v970 and above, the format
  //    includes the date of the closing time to clarify potential ambiguity,
  //    e.g. 20180323:0930-20180323:1600;20180326:0930-20180326:1600.
  liquidHours: Option[String],
  //    Contains the Economic Value Rule name and the respective optional argument. The two values
  //    should be separated by a colon. For example, aussieBond:YearsToExpiration=3. When the optional argument
  //    is not present, the first value will be followed by a colon.
  evRule: Option[String],
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
  secIdList: List[(String, String)],
  //    For derivatives, the symbol of the underlying contractReq.
  underSymbol: Option[String],
  //    For derivatives, returns the underlying security type.
  underSecType: Option[String],
  //    The list of market rule IDs separated by comma Market rule IDs can be used to determine the minimum
  //    price increment at a given price.
  marketRuleIds: Option[String],
  //    Real expiration date. Requires TWS 968+ and API v973.04+. Python API specifically requires API v973.06+.
  realExpirationDate: Option[String],
  //    Last trade time.
  lastTradeTime: Option[String],
  //    Stock type.
  stockType: Option[String],
  //    The nine-character bond CUSIP. For Bonds only. Receiving CUSIPs requires a CUSIP market data subscription.
  cusip: Option[String],
  //    Identifies the credit rating of the issuer. This field is not currently available from the TWS API. For Bonds
  //    only. A higher credit rating generally indicates a less risky investment. Bond ratings are from Moody's and S&P
  //    respectively. Not currently implemented due to bond market data restrictions.
  ratings: Option[String],
  //    A description string containing further descriptive information about the bond. For Bonds only.
  descAppend: Option[String],
  //    The type of bond, such as "CORP.".
  bondType: Option[String],
  //    The type of bond coupon. This field is currently not available from the TWS API. For Bonds only.
  couponType: Option[String],
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
  maturity: Option[String],
  //    The date the bond was issued. This field is currently not available from the TWS API. For Bonds only. Not
  //    currently implemented due to bond market data restrictions.
  issueDate: Option[String],
  //    Only if bond has embedded options. This field is currently not available from the TWS API. Refers to callable
  //    bonds and puttable bonds. Available in TWS description window for bonds.
  nextOptionDate: Option[String],
  //    Type of embedded option. This field is currently not available from the TWS API. Only if bond has embedded
  //    options.
  nextOptionType: Option[String],
  //    Only if bond has embedded options. This field is currently not available from the TWS API. For Bonds only.
  nextOptionPartial: Boolean,
  //  If populated for the bond in IB's database. For Bonds only.
  notes: Option[String],
  //
  // Note: 3 following props mentioned in documentation but no implemented in java API
  //
  //    Order's minimal size.
  //  minSize: Option[BigDecimal],
  //    Order's size increment.
  //  sizeIncrement: Option[BigDecimal],
  //    Order's suggested size increment.
  //  suggestedSizeIncrement: Option[BigDecimal]
  @Transient needsSyncWithIb: Boolean = false
) extends Response {

  @Transient lazy val ibContract: IbContract = {
    val c = new IbContract(
      this.contId,
      this.symbol,
      this.secType.toString,
      this.lastTradeDateOrContractMonth.orNull,
      this.strike,
      this.right.orNull,
      this.multiplier.orNull,
      this.exchange.name,
      this.currency.orNull,
      this.localSymbol.orNull,
      this.tradingClass.orNull,
      this.comboLegs.map(_.asIb).asJava,
      this.primaryExch.orNull.toString,
      this.includeExpired,
      this.secIdType.orNull,
      this.secId.orNull
    )
    c.deltaNeutralContract(this.deltaNeutralContract.orNull.toIb)
    c
  }

  @Transient lazy val ibContractDetails: IbContractDetails = {
    val cd = new IbContractDetails(
      ibContract,
      this.marketName.orNull,
      this.minTick,
      this.orderTypes.orNull,
      this.validExchanges.orNull,
      this.underConId,
      this.longName.orNull,
      this.contractMonth.orNull,
      this.industry.orNull,
      this.category.orNull,
      this.subcategory.orNull,
      this.timeZoneId.orNull,
      this.tradingHours.orNull,
      this.liquidHours.orNull,
      this.evRule.orNull,
      this.evMultiplier,
      this.mdSizeMultiplier,
      this.aggGroup,
      this.underSymbol.orNull,
      this.underSecType.orNull,
      this.marketRuleIds.orNull,
      this.realExpirationDate.orNull,
      this.lastTradeTime.orNull,
      this.stockType.orNull
    )

    cd.priceMagnifier(this.priceMagnifier)
    cd.secIdList(this.secIdList.map(p => new TagValue(p._1, p._1)).asJava)
    cd.cusip(this.cusip.orNull)
    cd.ratings(this.ratings.orNull)
    cd.descAppend(this.descAppend.orNull)
    cd.bondType(this.bondType.orNull)
    cd.couponType(this.couponType.orNull)
    cd.callable(this.callable)
    cd.putable(this.putable)
    cd.coupon(this.coupon)
    cd.convertible(this.convertible)
    cd.maturity(this.maturity.orNull)
    cd.issueDate(this.issueDate.orNull)
    cd.nextOptionDate(this.nextOptionDate.orNull)
    cd.nextOptionType(this.nextOptionType.orNull)
    cd.nextOptionPartial(this.nextOptionPartial)
    cd.notes(this.notes.orNull)
    cd
  }
}

object Contract {

  def apply(cd: IbContractDetails): Contract = new Contract(
    contId  = cd.contract.conid,
    symbol  = cd.contract.symbol,
    secType = SecurityType(cd.contract.getSecType),
    lastTradeDateOrContractMonth =
      Option.when(cd.contract.lastTradeDateOrContractMonth != null)(cd.contract.lastTradeDateOrContractMonth),
    strike               = cd.contract.strike,
    right                = Option.when(cd.contract.getRight != null)(cd.contract.getRight),
    multiplier           = Option.when(cd.contract.multiplier != null)(cd.contract.multiplier),
    exchange             = Exchange(cd.contract.exchange),
    currency             = Option.when(cd.contract.currency != null)(cd.contract.currency),
    localSymbol          = Option.when(cd.contract.localSymbol != null)(cd.contract.localSymbol),
    primaryExch          = Option.when(Exchange(cd.contract.primaryExch) != null)(Exchange(cd.contract.primaryExch)),
    tradingClass         = Option.when(cd.contract.tradingClass != null)(cd.contract.tradingClass),
    includeExpired       = cd.contract.includeExpired,
    secIdType            = Option.when(cd.contract.getSecIdType != null)(cd.contract.getSecIdType),
    secId                = Option.when(cd.contract.secId != null)(cd.contract.secId),
    comboLegsDescription = Option.when(cd.contract.comboLegsDescrip != null)(cd.contract.comboLegsDescrip),
    comboLegs            = cd.contract.comboLegs.asScala.toList.map(l => ComboLeg(l)),
    deltaNeutralContract =
      Option.when(cd.contract.deltaNeutralContract != null)(DeltaNeutralContract(cd.contract.deltaNeutralContract)),
    marketName         = Option.when(cd.marketName != null)(cd.marketName),
    minTick            = cd.minTick,
    priceMagnifier     = cd.priceMagnifier,
    orderTypes         = Option.when(cd.orderTypes != null)(cd.orderTypes),
    validExchanges     = Option.when(cd.validExchanges != null)(cd.validExchanges),
    underConId         = cd.underConid,
    longName           = Option.when(cd.longName != null)(cd.longName),
    contractMonth      = Option.when(cd.contractMonth != null)(cd.contractMonth),
    industry           = Option.when(cd.industry != null)(cd.industry),
    category           = Option.when(cd.category != null)(cd.category),
    subcategory        = Option.when(cd.subcategory != null)(cd.subcategory),
    timeZoneId         = Option.when(cd.timeZoneId != null)(cd.timeZoneId),
    tradingHours       = Option.when(cd.tradingHours != null)(cd.tradingHours),
    liquidHours        = Option.when(cd.liquidHours != null)(cd.liquidHours),
    evRule             = Option.when(cd.evRule != null)(cd.evRule),
    evMultiplier       = cd.evMultiplier,
    mdSizeMultiplier   = cd.mdSizeMultiplier,
    aggGroup           = cd.aggGroup,
    secIdList          = cd.secIdList.asScala.toList.map(t => (t.m_tag, t.m_value)),
    underSymbol        = Option.when(cd.underSymbol != null)(cd.underSymbol),
    underSecType       = Option.when(cd.underSecType != null)(cd.underSecType),
    marketRuleIds      = Option.when(cd.marketRuleIds != null)(cd.marketRuleIds),
    realExpirationDate = Option.when(cd.realExpirationDate != null)(cd.realExpirationDate),
    lastTradeTime      = Option.when(cd.lastTradeTime != null)(cd.lastTradeTime),
    stockType          = Option.when(cd.stockType != null)(cd.stockType),
    cusip              = Option.when(cd.cusip != null)(cd.cusip),
    ratings            = Option.when(cd.ratings != null)(cd.ratings),
    descAppend         = Option.when(cd.descAppend != null)(cd.descAppend),
    bondType           = Option.when(cd.bondType != null)(cd.bondType),
    couponType         = Option.when(cd.couponType != null)(cd.couponType),
    callable           = cd.callable,
    putable            = cd.putable,
    coupon             = cd.coupon,
    convertible        = cd.convertible,
    maturity           = Option.when(cd.maturity != null)(cd.maturity),
    issueDate          = Option.when(cd.issueDate != null)(cd.issueDate),
    nextOptionDate     = Option.when(cd.nextOptionDate != null)(cd.nextOptionDate),
    nextOptionType     = Option.when(cd.nextOptionType != null)(cd.nextOptionType),
    nextOptionPartial  = cd.nextOptionPartial,
    notes              = Option.when(cd.notes != null)(cd.notes)
  )
}
