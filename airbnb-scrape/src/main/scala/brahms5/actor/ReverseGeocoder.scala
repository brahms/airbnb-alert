package brahms5.actor

import akka.actor.{TypedActor, TypedProps}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.util.ByteString
import brahms5.actor.ReverseGeocoder.Result
import com.typesafe.scalalogging.LazyLogging
import spray.json.JsonParser

import scala.concurrent.Future

object ReverseGeocoder {
  case class Result(address: String = "")

  def props(apiKey: String) =
    TypedProps(classOf[ReverseGeocoder], new ReverseGeocoderImpl(apiKey))
}
trait ReverseGeocoder {
  def reverseGeocode(lat: Double, lng: Double): Future[ReverseGeocoder.Result]
}
class ReverseGeocoderImpl(apiKey: String) extends ReverseGeocoder
  with LazyLogging {

  val context = TypedActor.context
  import context.dispatcher
  implicit val materializer = ActorMaterializer(None)(context)

  val http = Http(context.system)

  override def reverseGeocode(lat: Double, lng: Double): Future[ReverseGeocoder.Result] = {
    val url = s"https://maps.googleapis.com" +
      s"/maps/api/place/nearbysearch/json" +
      s"?key=$apiKey" +
      s"&location=$lat,$lng" +
      s"&radius=100"
    http.singleRequest(HttpRequest(uri = url)).flatMap({
      case HttpResponse(StatusCodes.OK, headers, entity, _) =>
        entity.dataBytes.runFold(ByteString(""))((oldBs, newBs) => oldBs.concat(newBs)).map(bs => {
          val bodyAsString = bs.decodeString("UTF-8")
          logger.info(s"Got response body ${bodyAsString}")
          ReverseGeocoder.Result("")
        })
      case HttpResponse(code, _, _ , _) =>
        logger.warn(s"Request failed for $url, code: $code")
        Future.failed[ReverseGeocoder.Result](new Exception(s"Request failed for $url, code: $code"))
    })
  }
}
