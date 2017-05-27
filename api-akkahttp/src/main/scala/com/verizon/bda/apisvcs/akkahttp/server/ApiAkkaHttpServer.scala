package com.verizon.bda.apisvcs.akkahttp.server
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigObject}
import com.verizon.bda.apisvcs.ApiHttpServices
import com.verizon.bda.apisvcs.routing.{AkkaHttpRouteHelper}
import com.verizon.logger.BDALoggerFactory

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import java.util

import com.verizon.bda.apisvcs.utils.HttpServicesUtils._
import com.verizon.bda.apisvcs.utils.HttpServicesConstants._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by chundch on 5/18/17.
  */

sealed trait ApiHttpServerInterface {

  private val logger = BDALoggerFactory.getLogger(this.getClass)

  private var bindPort: Int = _

  def getBindPort(): Int = {
    bindPort
  }

  def setBindPort (port: Int): Unit = {

    if (bindPort == 0) {

      logger.info(s"bind port set to $bindPort")
      bindPort = port
    } else {
      logger.warn(s"You can't set bind port again.")

    }
  }

  def init(httpRoutes : util.HashMap[String, ApiHttpServices])


  def start(host : String, bindingPort : Int)

  def stop()


}

class ApiAkkaHttpServer extends ApiHttpServerInterface with AkkaHttpRouteHelper {

  private val logger = BDALoggerFactory.getLogger(this.getClass)
  implicit lazy val actorSystem = ActorSystem("httpsvcs-akkaactorsystem")
  implicit lazy val materializer = ActorMaterializer()

  val routes: ListBuffer[Route] = ListBuffer()

  var bindingFuture: Future[Http.ServerBinding] = _

  override def init(httpRoutes : util.HashMap[String, ApiHttpServices]): Unit = {

   import scala.collection.JavaConverters._
    httpRoutes.asScala.foreach( f => {
      logger.info("serviceing endpoint : " + f._1)
      routes += buildRoute(f._1, f._2)
      }
    )

  }

  def buildRoutesToPublish(routes: ListBuffer[Route]) : Route = {
    routes.reduce((r1, r2) => r1 ~ r2)
  }

  override def start(host : String, bindingPort : Int): Unit = {

    logger.info(s"REST interface start with host : " + host + " binding to port : " + bindingPort)

    var bindinghost = ""
      if(host != null) {
        bindinghost = host
      } else {
        bindinghost = getHostName
      }

    var svcsport : Int = 0

    if( bindingPort > 0 ) {
      svcsport = bindingPort
    } else {
      svcsport = HTTP_SERVICES_BINDING_PORT
    }

    val publishRoute: Route = buildRoutesToPublish(routes)
    bindingFuture =
    Http().bindAndHandle(handler = publishRoute, interface = bindinghost,
      port = svcsport)

    bindingFuture map {
      binding => {
        logger.info(s"REST interface bound to ${binding.localAddress}")
      }
    } recover {
      case ex =>
      logger.error("Failed to bind to $bindinghost:$svcsport", ex)
    }

  }

  override def stop(): Unit = {
    bindingFuture.flatMap(_.unbind())
  }
}



