package brahms5.actor

import akka.actor.{Actor, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.ImplicitMaterializer
import akka.util.ByteString
import brahms5.actor.HttpRequestor.RequestString
import com.typesafe.scalalogging.LazyLogging

/**
  * A simple Http Request object that returns the entire body back to you
  */

object HttpRequestor {
  def props() = Props[HttpRequestor]( new HttpRequestor)
  case class Response[T](code: StatusCode,
                         headers: Seq[HttpHeader] = Nil,
                         body: T,
                         protocol: HttpProtocol = HttpProtocols.`HTTP/1.1`)
  case class RequestString(request: HttpRequest)
}
class HttpRequestor extends Actor
  with LazyLogging
  with ImplicitMaterializer {
  // used by the http library to materialize the actors to build the http request actor graph
  val http = Http(context.system)
  import context.dispatcher
  override def receive: Receive = {
    case RequestString(request) =>
      val replyTo = sender()
      http.singleRequest(request).flatMap({
        case HttpResponse(code, headers, entity, protocol) =>
          entity.dataBytes.runFold(ByteString(""))((oldBs, newBs) => oldBs.concat(newBs)).map(bs => {
            val bodyAsString = bs.decodeString("UTF-8")
            logger.info(s"Got response status code ${code}")
            logger.info(s"Got response body ${bodyAsString}")
            replyTo ! HttpRequestor.Response(code = code,
              headers = headers,
              body = bodyAsString,
              protocol = protocol)
          })
      })
  }
}
