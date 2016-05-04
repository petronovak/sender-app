package com.senderapp.sources.http

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult._
import akka.http.scaladsl.server.directives.Credentials
import akka.persistence.PersistentActor
import akka.stream.ActorMaterializer
import com.senderapp.Global
import com.senderapp.model.{ Events, JsonMessageData, Message }
import spray.json._

import scala.concurrent.Future

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit object JsonMessageDataFormat extends RootJsonFormat[JsonMessageData] {
    def write(c: JsonMessageData) = ??? // don't care
    def read(value: JsValue) = {
      JsonMessageData(value.asJsObject)
    }
  }
}

sealed case class HttpCmd(user: String, data: JsonMessageData)

class HttpReaderActor extends PersistentActor with ActorLogging {

  import Global._

  override def persistenceId = "http-actor"

  var processor: ActorSelection = _

  var serverOpt: Option[HttpServer] = None

  var meta: Map[String, String] = _

  override def preStart() {
    super.preStart()
    processor = context.actorSelection("/user/messages-router")
  }

  override def receiveCommand = {
    case cmd: HttpCmd =>
      persistAsync(cmd) { cmd =>
        log.info(s"Received cmd: $cmd")
        // TODO: add headers to the meta
        processor ! Message("trash", meta + ("user" -> cmd.user), cmd.data)
      }

    case Events.Configure(name, config) =>

      val host = config.getString("host")
      val port = config.getInt("port")

      log.info(s"Starting http-receiver on $host:$port")

      if (!serverOpt.map(_.host).contains(host) ||
        !serverOpt.map(_.port).contains(port)) {

        stopReceiver.onComplete { _ =>
          startReceiver(host, port)
        }
      }

      meta = Map("source" -> name)
    case msg =>
      log.error(s"Unknown message $msg")
  }

  override def receiveRecover = {
    case _ =>
  }

  def startReceiver(host: String, port: Int): Unit = {
    serverOpt = Some(HttpServer(host, port, context, materializer, self))
  }

  def stopReceiver: Future[Unit] = {
    serverOpt.map(_.stop).getOrElse(Future.successful(Unit))
  }
}

case class HttpServer(host: String, port: Int,
    context: ActorContext,
    mat: ActorMaterializer,
    processor: ActorRef) extends JsonSupport {

  // TODO: implement authentication mechanism
  def authenticator: Authenticator[String] = {
    case p: Credentials.Provided =>
      Some(p.identifier)
    case Credentials.Missing =>
      None
  }

  implicit val actorSystem = context.system
  implicit val materializer = mat
  implicit val executionContext = context.dispatcher

  val OK = JsObject("status" -> JsString("OK"))

  val route = {
    pathSingleSlash {
      authenticateBasic(realm = "restricted", authenticator) { user =>
        post {
          decodeRequest {
            entity(as[JsonMessageData]) { msgData =>
              processor ! HttpCmd(user, msgData)
              complete(OK)
            } // entity
          } // decode
        } // post
      } // auth
    } // path
  }

  val bindResult = Http().bindAndHandle(route2HandlerFlow(route), host, port)

  def stop: Future[Unit] = {
    bindResult.flatMap(_.unbind())
  }
}
