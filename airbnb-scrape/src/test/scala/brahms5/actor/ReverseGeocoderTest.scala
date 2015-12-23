package brahms5.actor

import akka.actor.{Props, ActorSystem}
import akka.http.scaladsl.model.{StatusCodes, HttpRequest}
import akka.testkit.{TestProbe, TestActorRef, TestKit, ImplicitSender}
import brahms5.actor
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.{Future, Await}
import scala.concurrent.duration.Duration
import scala.io.Source

import akka.pattern.ask

class ReverseGeocoderSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll
  with Implicit30MinuteTimeout {

  def this() = this(ActorSystem("ReverseGeocoderSpec"))

  val reverseGeocodeExampleJson = Source.fromInputStream(
    getClass.getResourceAsStream("/reverse-geocode-example.json")).mkString

  override def afterAll: Unit = {
    system.terminate()
    Await.result(system.whenTerminated, Duration.Inf)
  }
  "A ReverseGeocoder" should "be able to parse a google maps response" in {
    val httpRequestor = TestProbe("httpRequestor")
    val rgeocoder = TestActorRef(ReverseGeocoder.props(
        apiKey = "invalid",
        httpRequestor = httpRequestor.ref
    ))
    val req =  ReverseGeocoder.ReverseGeocodeMsg(
        lat = 38.83779201970851d,
        lng = -77.3675359802915d)
    val response = (rgeocoder ? req).asInstanceOf[Future[Either[ReverseGeocoder.ReverseGeoCodeRsp, Throwable]]]
    httpRequestor.expectMsgType[HttpRequestor.RequestString].request.uri.toString should (include (s"${req.lat},${req.lng}"))
    httpRequestor.reply(HttpRequestor.Response[String](code = StatusCodes.OK, body = reverseGeocodeExampleJson))
    response shouldBe 'isCompleted
    response.value.get.get should matchPattern {
      case Left(ReverseGeocoder.ReverseGeoCodeRsp("5091 Brentwood Farm Dr, Fairfax, VA 22030, USA")) =>
    }
  }
}
