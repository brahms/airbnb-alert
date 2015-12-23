package brahms5.actor

import akka.actor.{Actor, Props}
import akka.http.javadsl.Http
import akka.stream.scaladsl.ImplicitMaterializer
import brahms5.actor.SystemTerminator.{WorkFailed, WorkFinished}
import com.typesafe.scalalogging.LazyLogging

object SystemTerminator {
  def props(count: Int) = Props[SystemTerminator](new SystemTerminator(count))
  case class WorkFinished(address: String)
  case class WorkFailed(address: String)
}
class SystemTerminator(var count: Int)
  extends Actor
  with LazyLogging
  with ImplicitMaterializer {
  val http = Http(context.system)
  import context.dispatcher
  override def receive: Receive = {
    case WorkFinished(address) =>
      count = count - 1
      logger.info(s"Worked finished for $address, count is: $count")
      handleEnd()
    case WorkFailed(address) =>
      count = count - 1
      logger.warn(s"Worked failed for $address, count is: $count")
      handleEnd()
  }
  private def handleEnd(): Unit = {
    if (count == 0) {
      logger.info("Hasta la vista, baby")
      logger.info("Shutting down http pools")
      http.shutdownAllConnectionPools().map({
        case _ =>
          logger.info("Shutting Actor system")
          context.system.terminate()
      }).andThen({
        case _ =>
          logger.info("Actor system shutdown")
      })
    }
  }
}
