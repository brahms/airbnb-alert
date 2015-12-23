package brahms5.actor

import akka.actor.ActorSystem
import akka.testkit.{TestProbe, TestActorRef, ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.{Future, Await}
import scala.concurrent.duration.Duration
import scala.io.Source

import akka.pattern.ask

class ListingsFetcherSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll
  with Implicit30MinuteTimeout {


  def this() = this(ActorSystem("ListingsParserSpec"))

  val airbnbSearchBody = Source.fromInputStream(
    getClass.getResourceAsStream("/airbnb-example.html")
  ).mkString


  override def afterAll: Unit = {
    system.terminate()
    Await.result(system.whenTerminated, Duration.Inf)
  }

  "A ListingsFetcher" should "be able to parse an airbnb html body" in {
    val httpRequestor = TestProbe("httpRequestor")
    val rgeocoder = TestProbe("reverseGeocoder")
    val lparser = TestActorRef(ListingsFetcher.props(httpRequestor.ref, rgeocoder.ref)
    val response = (lparser ? ListingsFetcher.ListingMsg("invalid address"))
      .asInstanceOf[Future[Either[Throwable, ListingsFetcher.ListingRsp]]]
    httpRequestor.expectMsgType[HttpRequestor.RequestString]
      .request.uri.toString should include "invalid-address"

  }
}
