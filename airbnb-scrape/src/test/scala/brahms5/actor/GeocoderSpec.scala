package brahms5.actor

import java.net.URLEncoder

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.testkit.{TestActorRef, TestProbe, ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.{Future, Await}
import scala.concurrent.duration.Duration
import scala.io.Source

class GeocoderSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll
  with Implicit30MinuteTimeout {

  def this() = this(ActorSystem("GeocoderSpec"))

  val geocodeExampleJson = Source.fromInputStream(
    getClass.getResourceAsStream("/geocode-example.json")).mkString

  override def afterAll: Unit = {
    system.terminate()
    Await.result(system.whenTerminated, Duration.Inf)
  }

  "A Geocoder" should "be able to parse a google maps response" in {
    val httpRequestor = TestProbe("httpRequestor")
    val geocoder = TestActorRef(Geocoder.props(apiKey = "invalid",
      httpRequestor = httpRequestor.ref))
    val req = Geocoder.GeocodeMsg(address = "whatever with spaces")

    import akka.pattern.ask

    val response = (geocoder ? req).asInstanceOf[Future[Either[Throwable, Geocoder.GeocodeRsp]]]
    httpRequestor.expectMsgType[HttpRequestor.RequestString].request.uri.toString() should
      (include ("invalid") and include (URLEncoder.encode(req.address, "UTF-8")) )
    httpRequestor.reply(HttpRequestor.Response(code = StatusCodes.OK, body = geocodeExampleJson))
    response shouldBe 'isCompleted
    response.value.get.get should matchPattern {
      case Right(Geocoder.GeocodeRsp(req.address, 37.4224764d, -122.0842499d)) =>
    }

  }
}
