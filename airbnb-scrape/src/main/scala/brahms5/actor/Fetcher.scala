package brahms5.actor

import akka.actor.{ActorRef, Props, Actor}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.ImplicitMaterializer
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging

object Fetcher {
  def props(address: String,
            terminator: ActorRef,
            parser: ActorRef) =
    Props(new Fetcher(address, terminator, parser))
}

class Fetcher(address: String,
              terminator: ActorRef,
              parser: ActorRef) extends Actor
  with ImplicitMaterializer
  with LazyLogging {

  val encodedAddress = address.replace(" ", "-")
  val url =  s"https://www.airbnb.com/s/$encodedAddress";
  val http = Http(context.system)

  import akka.pattern.pipe
  import context.dispatcher

  override def preStart(): Unit = {
    logger.info(s"Fetching $url")
    http.singleRequest(HttpRequest(uri = url))
      .pipeTo(self)
  }

  override def receive: Receive = {
    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      for (body <- entity.dataBytes.runFold(ByteString(""))((oldBs, newBs) => oldBs.concat(newBs))) {
        val bodyAsString = body.decodeString("UTF-8")
        logger.info(s"Got response body ${bodyAsString.length}")
        parser ! ListingsParser.Parse(address, bodyAsString)
      }
    case HttpResponse(code, _, _, _) =>
      logger.warn(s"Request failed for $url, response code is $code")
      terminator ! SystemTerminator.WorkFailed(address)
  }
}
