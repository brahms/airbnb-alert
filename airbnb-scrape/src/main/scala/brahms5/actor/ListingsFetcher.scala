package brahms5.actor

import akka.actor._
import akka.http.scaladsl.model.{StatusCodes, HttpRequest}
import akka.routing.RoundRobinGroup
import brahms5.actor.ListingsFetcher.Listing
import com.typesafe.scalalogging.LazyLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}

import scala.collection.JavaConverters._
import scala.concurrent.Future

object ListingsFetcher {
  def props(httpRequestor: ActorRef, reverseGeocoder: ActorRef) =
    Props[ListingsFetcher](new ListingsFetcher(httpRequestor, reverseGeocoder))

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
                      addresses: Seq[String] = Seq()) {
    def withAddresses(addresses: Seq[String]) = this.copy(addresses = addresses ++ addresses)
  }

  case class ListingMsg(address: String)
  case class ListingRsp(listings: Seq[Listing])
}
class ListingsFetcher(httpRequester: ActorRef, reverseGeocoder: ActorRef) extends Actor
  with LazyLogging
  with Implicit30MinuteTimeout {


  import akka.pattern.ask
  import context.dispatcher

  def findSameListing(address: String, listings: Seq[Listing]): Option[Listing] = {
    listings.find(listing => {
      listing.addresses.contains(address)
    })
  }

  override def receive: Receive = {
    case ListingsFetcher.ListingMsg(address) =>
      val replyTo = sender()
      val addresWithSpaces = address.replace(" ", "-")
      val url = s"https://www.airbnb.com/s/$addresWithSpaces"
      val request = HttpRequestor.RequestString(HttpRequest(uri = url))
      val htmlFuture = (httpRequester ? request)
        .asInstanceOf[Future[HttpRequestor.Response[String]]]
      htmlFuture.map({
        case HttpRequestor.Response(StatusCodes.OK, headers, body, _) =>
          val document: Document =  Jsoup.parse(body)
          logger.info(s"Parsing body for $address")
          val listings =  document.select(".listing").asScala.map( (el: Element) => {
            ListingsFetcher.Listing(
              lat = el.attr("data-lat").toDouble,
              lng = el.attr("data-lng").toDouble,
              name = el.attr("data-name"),
              url = el.attr("data-url"),
              user= el.attr("data-user"),
              id = el.attr("data-id"),
              price = el.attr("data-price"))
          }).slice(0, 1).toList

          val listingsFuture: Future[Seq[ListingsFetcher.Listing]] = Future.sequence(listings.map(listing => {
            (reverseGeocoder ? ReverseGeocoder.ReverseGeocodeMsg(listing.lat, listing.lng))
              .asInstanceOf[Future[Either[Throwable, ReverseGeocoder.ReverseGeoCodeRsp]]]
              .map( {
                case Right(res: ReverseGeocoder.ReverseGeoCodeRsp) => listing.withAddresses(res.addresses)
                case Left(ex) => listing
              })
          }))
          listingsFuture.map(listings => {
            logger.info(s"Got listings: $listings")
            val empty = listings.filter(_.addresses.isEmpty)
            if (empty.nonEmpty) {
              logger.warn(s"Some listings couldn't get rgeocoded: $empty")
            }
            val nonEmpty = listings.filter(_.addresses.nonEmpty)
            val listing: Option[ListingsFetcher.Listing] = findSameListing(address, nonEmpty)
            replyTo ! Right(listing)
          })
        case HttpRequestor.Response(code, headers, body, _) =>
          val warn = s"Couldn't retrieve html: $code, $body"
          logger.warn(warn)
          replyTo ! Left(new Exception(warn))
      })
  }
}
