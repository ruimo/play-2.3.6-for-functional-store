/*
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package play.it.http

import play.api.test._
import play.api.libs.ws.WSResponse
import play.libs.EventSource
import play.libs.EventSource.Event
import play.mvc.Results
import play.mvc.Results.Chunks

object JavaResultsHandlingSpec extends PlaySpecification with WsTestClient {

  "Java results handling" should {
    def makeRequest[T](controller: MockController)(block: WSResponse => T) = {
      implicit val port = testServerPort
      running(TestServer(port, FakeApplication(
        withRoutes = {
          case _ => JAction(controller)
        }
      ))) {
        val response = await(wsUrl("/").get())
        block(response)
      }
    }

    "treat headers case insensitively" in makeRequest(new MockController {
      def action = {
        response.setHeader("content-type", "text/plain")
        response.setHeader("Content-type", "text/html")
        Results.ok("Hello world")
      }
    }) { response =>
      response.header(CONTENT_TYPE) must beSome("text/html")
      response.body must_== "Hello world"
    }

    "buffer results with no content length" in makeRequest(new MockController {
      def action = Results.ok("Hello world")
    }) { response =>
      response.header(CONTENT_LENGTH) must beSome("11")
      response.body must_== "Hello world"
    }

    "send results as is with a content length" in makeRequest(new MockController {
      def action = {
        response.setHeader(CONTENT_LENGTH, "5")
        Results.ok("Hello world")
      }
    }) { response =>
      response.header(CONTENT_LENGTH) must beSome("5")
      response.body must_== "Hello"
    }

    "chunk results that are streamed" in makeRequest(new MockController {
      def action = {
        Results.ok(new Results.StringChunks() {
          def onReady(out: Chunks.Out[String]) {
            out.write("a")
            out.write("b")
            out.write("c")
            out.close()
          }
        })
      }
    }) { response =>
      response.header(TRANSFER_ENCODING) must beSome("chunked")
      response.header(CONTENT_LENGTH) must beNone
      response.body must_== "abc"
    }

    "chunk event source results" in makeRequest(new MockController {
      def action = {
        Results.ok(new EventSource() {
          def onConnected(): Unit = {
            send(Event.event("a"))
            send(Event.event("b"))
            close()
          }
        })
      }
    }) { response =>
      response.header(CONTENT_TYPE) must beSome("text/event-stream; charset=utf-8")
      response.header(TRANSFER_ENCODING) must beSome("chunked")
      response.header(CONTENT_LENGTH) must beNone
      response.body must_== "data: a\n\ndata: b\n\n"
    }
  }
}
