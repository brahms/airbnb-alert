package brahms5.actor

import akka.actor.{TypedActor, TypedProps}
import akka.http.javadsl.Http
import com.typesafe.scalalogging.LazyLogging

object SystemTerminator {
  def props(count: Int) = TypedProps(classOf[SystemTerminator], new SystemTerminatorImpl(count))
}
trait SystemTerminator {
  def workFinished(address: String) : Unit
  def workFailed(address: String) : Unit
}
class SystemTerminatorImpl(var count: Int)
  extends SystemTerminator
  with LazyLogging {
  val http = Http(TypedActor.context.system)
  val context = TypedActor.context
  import context.dispatcher
  override def workFinished(address: String): Unit = {
    count = count - 1
    logger.info(s"Worked finished for $address, count is: $count")
    handleEnd()
  }

  override def workFailed(address: String): Unit = {
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
