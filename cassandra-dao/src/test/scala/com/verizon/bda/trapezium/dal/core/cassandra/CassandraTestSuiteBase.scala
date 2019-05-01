/**
* Copyright (C) 2016 Verizon. All Rights Reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.verizon.bda.trapezium.dal.core.cassandra

import java.net.ServerSocket

import com.datastax.driver.core.Cluster
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import org.slf4j.LoggerFactory

/**
  * Created by v468328 on 5/26/16.
  * @deprecated
  */
trait CassandraTestSuiteBase extends FunSuite with BeforeAndAfterAll {
  override def beforeAll() {
    super.beforeAll()

    var socket = new ServerSocket(0)
    val nativeTransportPort = socket.getLocalPort
    // closing the socket
    socket.close()
    System.setProperty("cassandra.native_transport_port", nativeTransportPort.toString)

    socket = new ServerSocket(0)
    val storagePort = socket.getLocalPort
    // closing the socket
    socket.close()
    System.setProperty("cassandra.storage_port", storagePort.toString)

    socket = new ServerSocket(0)
    val rpcPort = socket.getLocalPort
    // closing the socket
    socket.close()
    System.setProperty("cassandra.rpc_port", rpcPort.toString)

    EmbeddedCassandraServerHelper.startEmbeddedCassandra("another-cassandra.yaml");
  }

  override def afterAll(): Unit = {
    super.afterAll()
    SessionManager.shutdown()
    EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
  }

  def start(): Unit = {
    EmbeddedCassandraServerHelper.startEmbeddedCassandra();
  }

  def stop(): Unit = {
    SessionManager.shutdown()
    EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
  }

  def executeSetupScript(script: String): Unit = {
    val logger = LoggerFactory.getLogger(this.getClass)
    val port = System.getProperty("cassandra.native_transport_port")
    try {
      val cluster: Cluster = Cluster.builder()
        .addContactPoint("localhost")
        .withPort(port.toInt)
        .build()
      cluster.connect().execute(script)
      logger.info(s"Executing cassandra ddl $script")
    }
    catch {
      case e: Exception => {
        logger.error("exception we got is " , e.getMessage)
      }
      case err: Throwable => {
        logger.error("exception we got is " , err.getMessage)
      }
    }
  }
}
