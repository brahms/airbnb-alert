package brahms5.actor

import java.net.URLEncoder

import akka.actor.{Props, ActorRef, Actor}
import akka.http.scaladsl.model.{StatusCodes, HttpRequest}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future


object Geocoder {
  def props(apiKey: String, httpRequestor: ActorRef) =
    Props[Geocoder](new Geocoder(apiKey = apiKey,
      httpRequestor = httpRequestor) )

  case class GeocodeMsg(address: String)
  case class GeocodeRsp(address: String, lat: Double, lng: Double)

}

class Geocoder(apiKey: String, httpRequestor: ActorRef) extends Actor
  with LazyLogging
  with Implicit30MinuteTimeout {

  import brahms5.googleapi.GMapGeocodeModel._
  import spray.json._
  import Geocoder._
  import GeocodeProtocol._
  import context.dispatcher
  override def receive: Receive = {
    case GeocodeMsg(address) =>
      val replyTo = sender()
      val addressFormatted = URLEncoder.encode(address, "UTF-8")
      val url = s"https://maps.googleapis.com/maps/api/geocode/json?address=$addressFormatted&key=$apiKey"
      logger.info(s"Requesting: $url")
      import akka.pattern.ask
      (httpRequestor ? HttpRequestor.RequestString(HttpRequest(uri = url)))
        .asInstanceOf[Future[HttpRequestor.Response[String]]]
        .map({
          case HttpRequestor.Response(StatusCodes.OK, headers, body, _) =>
            val response = body.parseJson.convertTo[GeocodeResponse]
            val geometry = response.results.head.geometry.get
            replyTo ! Right(GeocodeRsp(address, geometry.location.lat, geometry.location.lng))
          case HttpRequestor.Response(code, headers, body, _) =>
            val warn = s"Bad status code: $code, body: $body";
            logger.warn(warn)
            replyTo ! Left(new Exception(warn))
        }).onFailure({
          case t: Throwable =>
            logger.warn("Exception parsing gmap response", t)
            replyTo ! Left(t)
      })
  }
}
