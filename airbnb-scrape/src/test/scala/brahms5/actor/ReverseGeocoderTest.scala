package brahms5.actor

import akka.actor.ActorSystem
import akka.testkit.{TestKit, ImplicitSender}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ReverseGeocoderSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {


  def this() = this(ActorSystem("ReverseGeocoderSpec"))


  override def afterAll: Unit = {
    system.terminate()
    Await.result(system.whenTerminated, Duration.Inf)
  }

  "A ReverseGeocoder" should "be able to parse a google maps response" in {

  }
}
