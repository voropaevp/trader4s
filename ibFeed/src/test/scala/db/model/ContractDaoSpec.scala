package db.model

import cats.syntax.all._
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import db.ConnectedDao.ContractDaoConnected
import db.{ConnectedDao, TestDbSpec}
import model.datastax.ib.feed.ast.{Exchange, SecurityType}
import model.datastax.ib.feed.response.contract.{ComboLeg, Contract, ContractEntry, SecId}

import scala.jdk.CollectionConverters._
import java.util.{Optional, List => JList}

class ContractDaoSpec extends TestDbSpec {

  import ContractDaoSpec._

  override def beforeAll(): Unit = flushKeyspace(keyspace)

  override def afterAll(): Unit = flushKeyspace(keyspace)

  "Contract cache" - {
    "Create the meta keyspace" in {
      getFormattedContents(
        keyspace,
        s"""SELECT
           | keyspace_name
           |  FROM system_schema.keyspaces
           |  WHERE keyspace_name = '$testSchemaPrefix$keyspace'
           |  """.stripMargin
      ) should ===(s"""[keyspace_name:'$testSchemaPrefix$keyspace']""")
    }

  "Contract derived contract entry should match the mock contract " in {
    mockContract.asEntry shouldBe mockConfigEntry
  }

  "Be able to insert raw the get it by id and contract entry" in {
    val result = ConnectedDao
      .initWithPrefix[IO](testSchemaPrefix)
      .use {
        case (a, b, c) =>
          implicit val (_a, _b, _c) = (a, b, c)
          ContractDaoConnected[IO].create(mockContract) >>
            (
              ContractDaoConnected[IO].get(0),
              ContractDaoConnected[IO].getByContractEntry(mockConfigEntry),
              ContractDaoConnected[IO].getByContractEntry(mockConfigEntry.copy(primaryExch = None))
            ).parTupled
      }
      .unsafeRunSync()
    result._1 shouldBe Some(mockContract)
    result._2 shouldBe Some(mockContract)
    result._3 shouldBe None
  }
}

object ContractDaoSpec {
  val keyspace = "meta"
  val mockComboLeg = ComboLeg(
    contId = 0,
    //Select the relative number of contracts for the leg you are constructing. To help determine the ratio for a specific combination order, refer to the Interactive Analytics section of the User's Guide.
    ratio = 1,
    //The DataType (buy or sell) of the leg:
    action = "buy",
    //  The destination exchange to which the order will be routed.
    exchange = "NYSE",
    //  Specifies whether an order is an open or closing order. For instituational customers to determine if this order is to open or close a position. 0 - Same as the parent security. This is the only option for retail customers.
    //  1 - Open. This value is only valid for institutional customers.
    //  2 - Close. This value is only valid for institutional customers.
    //  3 - Unknown.
    openClose = 1,
    //  For stock legs when doing short selling. Set to 1 = clearing broker, 2 = third party.
    shortSaleSlot = 1,
    //  When ShortSaleSlot is 2, this field shall contain the designated location.
    designatedLocation = "",
    exemptCode         = 0
  )
  val mockConfigEntry: ContractEntry = ContractEntry(
    exchange                     = Exchange.NYSE,
    symbol                       = "TSLA",
    secType                      = SecurityType.Stock,
    primaryExch                  = Some(Exchange.NYSE),
    lastTradeDateOrContractMonth = None,
    strike                       = None,
    right                        = None,
    multiplier                   = None,
    currency                     = None,
    localSymbol                  = None,
    tradingClass                 = None,
    includeExpired               = None,
    comboLegs                    = List(mockComboLeg).some,
    secIdType                    = None,
    secId                        = None,
    comboLegsDescription         = None,
    deltaNeutralContract         = None
  )
  val mockContract: Contract = new Contract(
    contId = 0,
    //The underlying's asset symbol.
    symbol = "TSLA",
    //The security's type: STK - stock (or ETF) OPT - option FUT - future IND - index FOP -
    // futures option CASH - forex pair BAG - combo WAR - warrant BOND- bond CMDTY- commodity
    // NEWS- news FUND- mutual fund.
    secType = SecurityType.Stock,
    // The contractReq's last trading day or contractReq month (for Options and Futures). Strings with
    // format YYYYMM will be interpreted as the Contract Month whereas YYYYMMDD will be
    // interpreted as Last Trading Day.
    lastTradeDateOrContractMonth = Optional.empty[String](),
    //  The option's strike price.
    strike = .0d,
    //  Either Put or Call (i.e. Options). Valid values are P, PUT, C, CALL.
    right = Optional.empty[String](),
    //  The instrument's multiplier (i.e. options, futures).
    multiplier = Optional.empty[String](),
    //  The destination exchange.
    exchange = Exchange.NYSE,
    //  The underlying's currency.
    currency = Optional.empty[String](),
    //  The contractReq's symbol within its primary exchange For options, this will be the OCC symbol.
    localSymbol = Optional.empty[String](),
    //  The contractReq's primary exchange. For smart routed contracts, used to define contractReq in
    //  case of ambiguity. Should be defined as native exchange of contractReq, e.g. ISLAND for MSFT
    //  For exchanges which contain a period in name, will only be part of exchange name prior to
    //  period, i.e. ENEXT for ENEXT.BE.
    primaryExch = Optional.of(Exchange.NYSE),
    //  The trading class name for this contractReq. Available in TWS contractReq description window as
    //  well. For example, GBL Dec '13 future's trading class is "FGBL".
    tradingClass = Optional.empty[String](),
    //  If set to true, contractReq details request and historical data queries can be performed
    //  to expired futures contracts. Expired options or other instrument types are not available.
    includeExpired = false,
    //  Security's identifier when querying contractReq's details or placing orders ISIN - example:
    //  apple: US0378331005 CUSIP - example: apple: 037833100.
    secId = Optional.empty[String](),
    //  Identifier of the security type.
    secIdType = Optional.empty[String](),
    //  Description of the combo legs.
    comboLegsDescription = Optional.empty[String](),
    //  The legs of a combined contractReq definition.
    comboLegs = JList.of(
      ComboLeg(
        contId = 0,
        //Select the relative number of contracts for the leg you are constructing. To help determine the ratio for a specific combination order, refer to the Interactive Analytics section of the User's Guide.
        ratio = 1,
        //The DataType (buy or sell) of the leg:
        action = "buy",
        //  The destination exchange to which the order will be routed.
        exchange = "NYSE",
        //  Specifies whether an order is an open or closing order. For instituational customers to determine if this order is to open or close a position. 0 - Same as the parent security. This is the only option for retail customers.
        //  1 - Open. This value is only valid for institutional customers.
        //  2 - Close. This value is only valid for institutional customers.
        //  3 - Unknown.
        openClose = 1,
        //  For stock legs when doing short selling. Set to 1 = clearing broker, 2 = third party.
        shortSaleSlot = 1,
        //  When ShortSaleSlot is 2, this field shall contain the designated location.
        designatedLocation = "",
        exemptCode         = 0
      )
    ),
    //  Delta and underlying price for Delta-Neutral combo orders. Underlying (STK or FUT),
    //  and underlying price goes into this attribute.
    deltaNeutralContract = null,
    //The market name for this product.
    marketName = Optional.empty[String](),
    //The minimum allowed price variation. Note that many securities vary their minimum tick size according to their
    // price. This value will only show the smallest of the different minimum tick sizes regardless of the product's
    // price.
    // Full information about the minimum increment price structure can be obtained with the reqMarketRule function or
    // the IB Contract and Security Search site.
    minTick = 0d,
    // Allows execution and strike prices to be reported consistently with market data, historical data and the order
    // price, i.e. Z on LIFFE is reported in Index points and not GBP. In TWS versions prior to 972, the price
    // is used in defining future option strike prices (e.g. in the API the strike is specified in dollars, but in TWS
    // it is specified in cents). In TWS versions 972 and higher, the price magnifier is not used in defining futures
    // option strike prices so they are consistent in TWS and the API.
    priceMagnifier = 0,
    //Supported order types for this product.
    orderTypes = Optional.empty[String](),
    //Valid exchange fields when placing an order for this contractReq.
    //The list of exchanges will is provided in the same order as the corresponding MarketRuleIds list.
    validExchanges = Optional.empty[String](),
    // For derivatives, the contractReq ID (conID) of the underlying instrument.
    underConId = 0,
    // Descriptive name of the product.
    longName = Optional.empty[String](),
    //    Typically the contractReq month of the underlying for a Future contractReq.
    contractMonth = Optional.empty[String](),
    //    The industry classification of the underlying/product. For example, Financial.
    industry = Optional.empty[String](),
    //    The industry category of the underlying. For example, InvestmentSvcd.contractReq.
    category = Optional.empty[String](),
    //    The industry subcategory of the underlying. For example, Brokerage.
    subcategory = Optional.empty[String](),
    //    The time zone for the trading hours of the product. For example, EST.
    timeZoneId = Optional.empty[String](),
    //    The trading hours of the product. This value will contain the trading hours of the current day as well as
    //    the next's. For example, 20090507:0700-1830,1830-2330;20090508:CLOSED. In TWS versions 965+ there is an option
    //    in the Global Configuration API settings to return 1 month of trading hours. In TWS version 970+, the format
    //    includes the date of the closing time to clarify potential ambiguity,
    //    ex: 20180323:0400-20180323:2000;20180326:0400-20180326:2000 The trading hours will correspond to the hours for
    //    the product on the associated exchange. The same instrument can have different hours on different exchanges.
    tradingHours = Optional.empty[String],
    //    The liquid hours of the product. This value will contain the liquid hours (regular trading hours) of the
    //    contractReq on the specified exchange. Format for TWS versions until
    //    969: 20090507:0700-1830,1830-2330;20090508:CLOSED. In TWS versions 965+ there is an option in
    //    the Global Configuration API settings to return 1 month of trading hours. In TWS v970 and above, the format
    //    includes the date of the closing time to clarify potential ambiguity,
    //    e.g. 20180323:0930-20180323:1600;20180326:0930-20180326:1600.
    liquidHours = Optional.empty[String],
    //    Contains the Economic Value Rule name and the respective optional argument. The two values
    //    should be separated by a colon. For example, aussieBond:YearsToExpiration=3. When the optional argument
    //    is not present, the first value will be followed by a colon.
    evRule = Optional.empty[String],
    //    Tells you approximately how much the market value of a contractReq would change if the price were to change by 1.
    //    It cannot be used to get market value by multiplying the price by the approximate multiplier.
    evMultiplier     = 0d,
    mdSizeMultiplier = 0,
    //    Aggregated group Indicates the smart-routing group to which a contractReq belongs.
    //    contracts which cannot be smart-routed have aggGroup = -1.
    aggGroup = 0,
    //  A list of contractReq identifiers that the customer is allowed to view. CUSIP/ISIN/etcd.contractReq. For US stocks,
    //  receiving the ISIN requires the CUSIP market data subscription. For Bonds, the CUSIP or ISIN is input
    //  directly into the symbol field of the Contract class.
    secIdList = JList.of(SecId("tag", "val")),
    //    For derivatives, the symbol of the underlying contractReq.
    underSymbol = Optional.empty[String],
    //    For derivatives, returns the underlying security type.
    underSecType = Optional.empty[String],
    //    The list of market rule IDs separated by comma Market rule IDs can be used to determine the minimum
    //    price increment at a given price.
    marketRuleIds = Optional.empty[String],
    //    Real expiration date. Requires TWS 968+ and API v973.04+. Python API specifically requires API v973.06+.
    realExpirationDate = Optional.empty[String],
    //    Last trade time.
    lastTradeTime = Optional.empty[String],
    //    Stock type.
    stockType = Optional.empty[String],
    //    The nine-character bond CUSIP. For Bonds only. Receiving CUSIPs requires a CUSIP market data subscription.
    cusip = Optional.empty[String],
    //    Identifies the credit rating of the issuer. This field is not currently available from the TWS API. For Bonds
    //    only. A higher credit rating generally indicates a less risky investment. Bond ratings are from Moody's and S&P
    //    respectively. Not currently implemented due to bond market data restrictions.
    ratings = Optional.empty[String],
    //    A description string containing further descriptive information about the bond. For Bonds only.
    descAppend = Optional.empty[String],
    //    The type of bond, such as "CORP.".
    bondType = Optional.empty[String],
    //    The type of bond coupon. This field is currently not available from the TWS API. For Bonds only.
    couponType = Optional.empty[String],
    //    If true, the bond can be called by the issuer under certain conditions. This field is currently not available
    //    from the TWS API. For Bonds only.
    callable = false,
    //  Values are True or False. If true, the bond can be sold back to the issuer under certain conditions. This field
    //  is currently not available from the TWS API. For Bonds only.
    putable = false,
    //  The interest rate used to calculate the amount you will receive in interest payments over the course of the year.
    //  This field is currently not available from the TWS API. For Bonds only.
    coupon = 0d,
    //  Values are True or False. If true, the bond can be converted to stock under certain conditions. This field is
    //  currently not available from the TWS API. For Bonds only.
    convertible = false,
    //  he date on which the issuer must repay the face value of the bond. This field is currently not available from
    //  the TWS API. For Bonds only. Not currently implemented due to bond market data restrictions.
    maturity = Optional.empty[String],
    //    The date the bond was issued. This field is currently not available from the TWS API. For Bonds only. Not
    //    currently implemented due to bond market data restrictions.
    issueDate = Optional.empty[String],
    //    Only if bond has embedded options. This field is currently not available from the TWS API. Refers to callable
    //    bonds and puttable bonds. Available in TWS description window for bonds.
    nextOptionDate = Optional.empty[String],
    //    Type of embedded option. This field is currently not available from the TWS API. Only if bond has embedded
    //    options.
    nextOptionType = Optional.empty[String],
    //    Only if bond has embedded options. This field is currently not available from the TWS API. For Bonds only.
    nextOptionPartial = false,
    //  If populated for the bond in IB's database. For Bonds only.
    notes = Optional.empty[String]
  )
}
