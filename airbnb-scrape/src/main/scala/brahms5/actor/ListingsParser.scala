package brahms5.actor

import akka.actor._
import akka.routing.RoundRobinGroup
import com.typesafe.scalalogging.LazyLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}

import scala.collection.JavaConverters._
import scala.concurrent.Future

object ListingsParser {
  def props(terminator: SystemTerminator,
           reverseGeocoder: ReverseGeocoder) =
    TypedProps(classOf[ListingsParser],
    new ListingsParserImpl(terminator, reverseGeocoder))

  /**
    * Creates a router version of ListingsParser, with total routees behind it
    * @param system
    * @param props
    * @param total
    * @return
    */
  def router[T <: ListingsParser](system: TypedActorExtension,
             props: TypedProps[T],
             total: Int) = {
    val routees = List.fill(total) { system.typedActorOf(props) }
    val routeePaths = routees.map( r => {
      system.getActorRefFor(r).path.toStringWithoutAddress
    })
    val router: ActorRef = system.system.actorOf(RoundRobinGroup(routeePaths).props())
    val typedRouter: ListingsParser =
      system.typedActorOf(TypedProps[ListingsParser](), actorRef = router)
    typedRouter
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
}
trait ListingsParser {
  def parse(address: String, body: String)
}
class ListingsParserImpl(terminator: SystemTerminator,
                        reverseGeocoder: ReverseGeocoder) extends ListingsParser
  with LazyLogging {
  val context = TypedActor.context
  import context.dispatcher
  override def parse(address: String, body: String): Unit = {
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

    val listingsWithGeocodesF: Future[List[ListingsParser.Listing]] = Future.sequence(listings.map(l => {
      reverseGeocoder.reverseGeocode(l.lat, l.lng)
        .map((result: ReverseGeocoder.Result) => {
          l.withAddress(result.address)
        }).recover({case throwable: Throwable => l})
    }))

    listingsWithGeocodesF.map({case listings =>
      for (listing <- listings) {
        logger.info(s"With geocoded info: $listing")
      }
      terminator.workFinished(address)
    })
  }
}
