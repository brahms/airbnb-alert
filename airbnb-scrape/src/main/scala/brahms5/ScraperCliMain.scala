package brahms5

import akka.actor.ActorSystem
import brahms5.actor.{ReverseGeocoder, ListingsParser, Fetcher, SystemTerminator}
import com.typesafe.scalalogging._
import akka.actor.TypedActor

case class ScraperCliArgs(addresses: List[String] = List(),
                         apiKey: String = null)
object ScraperCliMain extends App with StrictLogging {

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
      // Create the  actor system
      val system = TypedActor(ActorSystem("airbnb-scrape"))
      val reverseGeocoder = system.typedActorOf(ReverseGeocoder.props(apiKey = ""))
      val terminator = system.typedActorOf(SystemTerminator.props(args.addresses.size))
      val listingsParser = ListingsParser.router(system,
        ListingsParser.props(terminator,reverseGeocoder),
        Runtime.getRuntime.availableProcessors())

      for (address <- args.addresses) {
        system.system.actorOf(Fetcher.props(address, terminator, listingsParser))
      }

      system.system.registerOnTermination({
        logger.info("App is shutting down")
      })
  }

}
