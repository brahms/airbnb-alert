package brahms5.actor

import akka.actor._
import akka.routing.RoundRobinGroup
import com.typesafe.scalalogging.LazyLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}

import scala.collection.JavaConverters._
import scala.concurrent.Future

object ListingsParser {
  def props(terminator: ActorRef,
           reverseGeocoder: ActorRef) =
    Props[ListingsParser](new ListingsParser(terminator, reverseGeocoder))

  /**
    * Creates a router version of ListingsParser, with total routees behind it
    * @param system
    * @param props
    * @param total
    * @return
    */
  def router(system: ActorSystem,
             props: Props,
             total: Int) = {
    val routees = List.fill(total) { system.actorOf(props) }
    val routeePaths = routees.map( r => {
      r.path.toStringWithoutAddress
    })
    system.actorOf(RoundRobinGroup(routeePaths).props())
  }
  case class Listing( lat: Double,
                      lng: Double,
                      name: String,
                      url: String,
                      user: String,
                      id: String,
                      price: String,
                      address: Option[String] = None) {
    def withAddress(addr: String) = this.copy(address = Some(addr))
  }

  case class Parse(address: String, body: String)
}
class ListingsParser(terminator: ActorRef,
                     reverseGeocoder: ActorRef) extends Actor
  with LazyLogging
  with Implicit30MinuteTimeout {


  import akka.pattern.ask
  import context.dispatcher

  override def receive: Receive = {
    case ListingsParser.Parse(address, body) =>
      val document: Document =  Jsoup.parse(body)
      logger.info(s"Parsing body for $address")

      val listings =  document.select(".listing").asScala.map( (el: Element) => {
        ListingsParser.Listing(
          lat = el.attr("data-lat").toDouble,
          lng = el.attr("data-lng").toDouble,
          name = el.attr("data-name"),
          url = el.attr("data-url"),
          user= el.attr("data-user"),
          id = el.attr("data-id"),
          price = el.attr("data-price"))
      }).slice(0, 1).toList
      Future.sequence(listings.map(listing => {
        (reverseGeocoder ? ReverseGeocoder.ReverseGeocodeMsg(listing.lat, listing.lng))
          .asInstanceOf[Future[Either[ReverseGeocoder.ReverseGeoCodeRsp, Throwable]]]
          .map( {
            case Left(res: ReverseGeocoder.ReverseGeoCodeRsp) => listing.withAddress(res.address)
            case Right(ex) => listing
          })
      })).map({
        case listingsWithAddress =>
          for (listing <- listingsWithAddress) {
            logger.info(s"With geocoded info: $listing")
          }
          terminator ! SystemTerminator.WorkFinished(address)
      })
  }
}
