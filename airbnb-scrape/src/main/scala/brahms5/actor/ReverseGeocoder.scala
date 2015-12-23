package brahms5.actor

import akka.actor._
import akka.http.scaladsl.model._
import com.typesafe.scalalogging.LazyLogging
import spray.json._
import brahms5.util.SnakifiedSprayJsonSupport

import scala.concurrent.Future

object ReverseGeocoder {
  case class ReverseGeocodeMsg(lat: Double, lng: Double)
  case class ReverseGeoCodeRsp(address: String)
  def props(apiKey: String, httpRequestor: ActorRef) =
    Props[ReverseGeocoder](new ReverseGeocoder(apiKey, httpRequestor))

  private [ReverseGeocoder] case class GMapAddressComponent(longName: String, shortName: String, types: Seq[String])
  private [ReverseGeocoder] case class GMapResult(addressComponents: Seq[GMapAddressComponent], formattedAddress: String, types: Seq[String])
  private [ReverseGeocoder] case class GMapResponse(results: Seq[GMapResult], status: String)
  private [ReverseGeocoder] object ResponseProtocol extends SnakifiedSprayJsonSupport {
    implicit val addressFormat = jsonFormat3(GMapAddressComponent)
    implicit val resultFormat = jsonFormat3(GMapResult)
    implicit val responseFormat = jsonFormat2(GMapResponse)

  }
}

class ReverseGeocoder(apiKey: String, httpRequestor: ActorRef) extends Actor
  with LazyLogging
  with Implicit30MinuteTimeout {
  import ReverseGeocoder._
  import ResponseProtocol._
  import context.dispatcher
  override def receive: Receive = {
    case ReverseGeocodeMsg(lat, lng) =>
      val replyTo = sender()
      val url = s"https://maps.googleapis.com/maps/api/geocode/json?key=$apiKey&latlng=$lat,$lng"
      import akka.pattern.ask
      logger.info(s"Geocoding $lat, $lng with url $url")
      (httpRequestor ? HttpRequestor.RequestString(HttpRequest(uri = url)))
        .asInstanceOf[Future[HttpRequestor.Response[String]]].map({
        case HttpRequestor.Response(StatusCodes.OK, headers, body, _) =>
          logger.info("Got response")
          val response = body.parseJson.convertTo[GMapResponse]
          replyTo ! Left(ReverseGeoCodeRsp(response.results(0).formattedAddress))
        case HttpRequestor.Response(code, _, _ , _) =>
          logger.warn(s"Request failed for $url, code: $code")
          replyTo ! Right(new Exception(s"Request failed for $url, code: $code"))
      }).onFailure({case t: Throwable =>
          logger.warn("JSON exception", t)
          replyTo ! Right(Status.Failure(t))})
  }
}