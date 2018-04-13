package vbds.server.controllers

import akka.http.scaladsl.model.StatusCodes
import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import vbds.server.models.{JsonSupport, StreamInfo}

class StreamAdminSpec
    extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with JsonSupport {

  val route = new AdminRoute(new LocalAdminData(system)).route

  "The service" should {

    "return initial empty list of streams" in {
      Get("/vbds/admin/streams") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Set[StreamInfo]] shouldEqual Set.empty
      }
    }

    "allow adding a stream" in {
      Post("/vbds/admin/streams/firstStream") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[StreamInfo] shouldEqual StreamInfo("firstStream")
      }
      Post("/vbds/admin/streams/secondStream") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[StreamInfo] shouldEqual StreamInfo("secondStream")
      }
      Post("/vbds/admin/streams/secondStream") ~> route ~> check {
        status shouldEqual StatusCodes.Conflict
        responseAs[String] shouldEqual "The stream secondStream already exists"
      }
    }

    "return list of streams" in {
      Get("/vbds/admin/streams") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Set[StreamInfo]] shouldEqual Set(StreamInfo("firstStream"),
                                                    StreamInfo("secondStream"))
      }
    }

    "delete a stream" in {
      Delete("/vbds/admin/streams/firstStream") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[StreamInfo] shouldEqual StreamInfo("firstStream")
      }
      Delete("/vbds/admin/streams/firstStream") ~> route ~> check {
        status shouldEqual StatusCodes.NotFound
        responseAs[String] shouldEqual "The stream firstStream does not exists"
      }
      Get("/vbds/admin/streams") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Set[StreamInfo]] shouldEqual Set(StreamInfo("secondStream"))
      }
    }
  }
}
