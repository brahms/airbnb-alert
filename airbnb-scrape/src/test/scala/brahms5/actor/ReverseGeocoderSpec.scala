package brahms5.actor

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.io.Source
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
        lat = 33d,
        lng = -100d)

    import akka.pattern.ask

    val response = (rgeocoder ? req).asInstanceOf[Future[Either[Throwable, ReverseGeocoder.ReverseGeoCodeRsp]]]
    httpRequestor.expectMsgType[HttpRequestor.RequestString].request.uri.toString should (include (s"${req.lat},${req.lng}"))
    httpRequestor.reply(HttpRequestor.Response[String](code = StatusCodes.OK, body = reverseGeocodeExampleJson))
    response shouldBe 'isCompleted
    response.value.get.get should matchPattern {
      case Right(ReverseGeocoder.ReverseGeoCodeRsp(
        Seq("277 Bedford Avenue, Brooklyn, NY 11211, USA"))) =>
    }
  }
}
