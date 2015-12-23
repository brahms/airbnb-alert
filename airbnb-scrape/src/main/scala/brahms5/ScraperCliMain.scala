package brahms5

import akka.actor.ActorSystem
import akka.dispatch.Futures
import brahms5.actor._
import com.typesafe.scalalogging._

import scala.concurrent.Future

object ScraperCliMain extends App with StrictLogging {

  case class ScraperCliArgs(addresses: List[String] = List(),
                            apiKey: String = null)

  private [ScraperCliMain] case class AddressWithResult(
                                                       address: String,
                                                       result: Either[Throwable, Option[ListingsFetcher.Listing]])

  logger.info("Starting up")

  val parser = new scopt.OptionParser[ScraperCliArgs]("airbnb-scrape") {

    head("airbnb-scrape", "0.1")
    opt[String]('a', "apiKey")
      .required()
      .text("Api key for google api")
      .action({(apiKey, args) => {
        args.copy(apiKey = apiKey)
      }})
    arg[String]("<addressess>...")
      .text("Addresses to scrape for")
      .unbounded()
      .required()
      .action({(address, args) => {
        args.copy(addresses = args.addresses :+ address)
    }})

  }

  parser.parse(args, ScraperCliArgs()) match {
    case None => /* nop */
    case Some(args) =>
      logger.info(s"Will scrape air bnb for ${args.addresses.mkString(", ")}")
      val system = ActorSystem("airbnb-scrape")

      // create our actors
      val httpRequestor = system.actorOf(HttpRequestor.props())
      val reverseGeocoder = system.actorOf(ReverseGeocoder.props(apiKey = args.apiKey, httpRequestor = httpRequestor))
      val geocoder = system.actorOf(Geocoder.props(apiKey = args.apiKey, httpRequestor = httpRequestor))
      val terminator = system.actorOf(SystemTerminator.props(args.addresses.size))
      val listingsFetcher = ListingsFetcher.router(system,
        ListingsFetcher.props(terminator, reverseGeocoder),
        Runtime.getRuntime.availableProcessors())
      import akka.pattern.ask
      import scala.concurrent.ExecutionContext.global
      Futures.sequence[AddressWithResult](args.addresses.toIterable.map(address => {
        (listingsFetcher ? ListingsFetcher.ListingMsg(address))
          .asInstanceOf[Future[Either[Throwable, Option[ListingsFetcher.Listing]]]]
          .map({
            case Left(t) => AddressWithResult(address, Left(t))
            case Right(None) => AddressWithResult(address, Right(None)),
            case Right(Some(listing)) => AddressWithResult(address, Right(Some(listing)))
          })
      }), scala.concurrent.ExecutionContext.global)
      system.registerOnTermination({
        logger.info("App is shutting down")
      })
  }

}
