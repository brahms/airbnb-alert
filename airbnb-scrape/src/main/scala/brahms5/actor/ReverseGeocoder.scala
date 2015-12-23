package brahms5.actor

import akka.actor._
import akka.http.scaladsl.model._
import com.typesafe.scalalogging.LazyLogging
import spray.json._

import scala.concurrent.Future

object ReverseGeocoder {
  case class ReverseGeocodeMsg(lat: Double, lng: Double)
  case class ReverseGeoCodeRsp(addresses: Seq[String])
  def props(apiKey: String, httpRequestor: ActorRef) =
    Props[ReverseGeocoder](new ReverseGeocoder(apiKey, httpRequestor))
}

class ReverseGeocoder(apiKey: String, httpRequestor: ActorRef) extends Actor
  with LazyLogging
  with Implicit30MinuteTimeout {
  import ReverseGeocoder._
  import brahms5.googleapi.GMapGeocodeModel._
  import GeocodeProtocol._
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
          val response = body.parseJson.convertTo[GeocodeResponse]
          replyTo ! Right(ReverseGeoCodeRsp(response.results.map(_.formattedAddress).take(3)))
        case HttpRequestor.Response(code, _, _ , _) =>
          logger.warn(s"Request failed for $url, code: $code")
          replyTo ! Left(new Exception(s"Request failed for $url, code: $code"))
      }).onFailure({
        case t: Throwable =>
          logger.warn("JSON exception", t)
          replyTo ! Left(Status.Failure(t))
      })
  }
}