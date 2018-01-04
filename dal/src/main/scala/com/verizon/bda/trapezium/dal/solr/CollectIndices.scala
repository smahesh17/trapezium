package com.verizon.bda.trapezium.dal.solr

import java.io.{File, InputStream}
import java.net.URI

import com.jcraft.jsch.{ChannelExec, JSch, Session}
import com.verizon.bda.trapezium.dal.exceptions.SolrOpsException
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, FileUtil, Path}
import org.apache.log4j.Logger

import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, Map => MMap}
import scala.collection.parallel.ForkJoinTaskSupport
import scala.collection.parallel.mutable.ParArray
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
  * Created by venkatesh on 7/10/17.
  */

class CollectIndices {
  val log = Logger.getLogger(classOf[CollectIndices])
  var session: Session = _

  def initSession(host: String, user: String, privateKey: String = "~/.ssh/id_rsa") {
    val jsch = new JSch
    jsch.addIdentity(privateKey)
    session = jsch.getSession(user, host, 22)
    session.setConfig("StrictHostKeyChecking", "no")
    session.setTimeout(1000000)
    log.info(s"making ssh session with ${user}@${host}")

    session.connect()
  }

  def disconnectSession() {
    session.disconnect
  }

  def runCommand(command: String, retry: Boolean, retryCount: Int = 5): Int = {
    var code = -1
    var retries = retryCount

    try {
      do {
        val channel: ChannelExec = getConnectedChannel(command)
        if (channel == null) {
          throw new SolrOpsException(s"could not execute command: $command on ${session.getHost}")
        }
        log.info(s"running command : ${command} in ${session.getHost}" +
          s" with user ${session.getUserName}")
        val in: InputStream = channel.getInputStream
        val out = printResult(in, channel)
        code = out._2
        log.info(s" command : ${command} \n completed on ${session.getHost}" +
          s" with user ${session.getUserName} with exit code:$code on retry with log:\n${out._1}")
        if (code == 0 && retries != retryCount) {
          log.info(s" command : ${command} \n completed on ${session.getHost}" +
            s" with user ${session.getUserName} with exit code:$code on retry with log:\n${out._1}")
        }
        retries = retries - 1
      }
      while (code != 0 && retries > 0)
      if (code != 0) {
        throw new SolrOpsException(s"could not execute $command has" +
          s" returned $code ${session.getHost}")
      }
    } catch {
      case e: Exception => {
        log.warn(s"Has problem running the command :$command", e)
        throw new SolrOpsException(s"could not execute $command has" +
          s" returned $code ${session.getHost}")
        return code
      }
    }
    code
  }

  def getConnectedChannel(command: String, retry: Int = 5): ChannelExec = {
    if (retry > 0) {
      try {
        val channel: ChannelExec = session.openChannel("exec").asInstanceOf[ChannelExec]
        channel.setInputStream(null)
        channel.setCommand(command)
        channel.connect
        return channel
      } catch {
        case e: Exception => {
          log.warn(s"Has problem opening the channel for command :$command \n" +
            s"and retrying to open channel on ${session.getHost}", e)
          Thread.sleep(10000)

          getConnectedChannel(command, retry - 1)
        }
      }
    }
    else {
      null
    }

  }

  def printResult(in: InputStream, channel: ChannelExec): (String, Int) = {
    val tmp = new Array[Byte](1024)
    val strBuilder = new StringBuilder
    var continueLoop = true
    while (continueLoop) {
      while (in.available > 0) {
        val i = in.read(tmp, 0, 1024)
        if (i < 0) continueLoop = false
        strBuilder.append(new String(tmp, 0, i))
      }
      if (continueLoop && channel.isClosed) {
        log.info("exit-status:" + channel.getExitStatus)
        log.info("with error stream as " + channel.getErrStream.toString)
        continueLoop = false
      }
    }
    (strBuilder.toString, channel.getExitStatus)
  }


}

object CollectIndices {
  val log = Logger.getLogger(classOf[CollectIndices])
  val machineMap: MMap[String, CollectIndices] = new mutable.HashMap[String, CollectIndices]()

  def getMachine(host: String, solrNodeUser: String, machinePrivateKey: String): CollectIndices = {
    if (machineMap.contains(host)) {
      machineMap(host)
    } else {
      val scpHost = new CollectIndices
      if (machinePrivateKey == null) {
        scpHost.initSession(host, solrNodeUser)
      } else {
        scpHost.initSession(host, solrNodeUser, machinePrivateKey)
      }
      machineMap(host) = scpHost
      scpHost
    }
  }


  def moveFilesFromHdfsToLocal(solrMap: Map[String, String],
                               hdfsIndexFilePath: String,
                               indexLocationInRoot: String, coreMap: Map[String, String])
  : Map[String, ListBuffer[(String, String)]] = {
    log.info("inside move files")
    val solrNodeUser = solrMap("solrUser")
    val machinePrivateKey = solrMap.getOrElse("machinePrivateKey", null)
    val solrNodes = new ListBuffer[CollectIndices]

    val shards = coreMap.keySet.toArray
    var partFileMap = MMap[(String, String), ListBuffer[String]]()
    var outMap = MMap[String, ListBuffer[(String, String)]]()
    val rootDirs = solrMap("rootDirs").split(",")
    var rootMap = MMap[String, Int]()
    for ((shardId, host) <- coreMap) {
      val tmp = shardId.split("_")
      val folderPrefix = solrMap("folderPrefix").stripSuffix("/")
      val partFile = folderPrefix + (tmp(tmp.length - 2).substring(5).toInt - 1)

      val fileName = indexLocationInRoot.stripSuffix("/") + partFile

      if (rootMap.contains(host)) {
        val root = rootDirs(rootMap(host))
        rootMap(host) = (rootMap(host) + 1) % rootDirs.length
        val partFilePath = root + fileName
        if (partFileMap.contains((host, root))) {
          partFileMap((host, root)).append((partFile))
        } else {
          partFileMap((host, root)) = new ListBuffer[String]
          partFileMap((host, root)).append((partFile))
        }
        outMap(host).append((partFilePath, shardId))
      } else {
        rootMap(host) = 0

        val root = rootDirs(rootMap(host))
        if (partFileMap.contains((host, root))) {
          partFileMap((host, root)).append((partFile))
        } else {
          partFileMap((host, root)) = new ListBuffer[String]
          partFileMap((host, root)).append((partFile))
        }

        val partFilePath = rootDirs(rootMap(host)) + fileName
        rootMap(host) = (rootMap(host) + 1) % rootDirs.length
        outMap(host) = new ListBuffer[(String, String)]
        outMap(host).append((partFilePath, shardId))
      }
    }
    var array: ListBuffer[(CollectIndices, String)] = new ListBuffer[(CollectIndices, String)]
    for (((host: String, root: String), partFileList: ListBuffer[String]) <- partFileMap) {

      val machine: CollectIndices = getMachine(host.split(":")(0), solrNodeUser, machinePrivateKey)
      val command = s"partFiles=(" + partFileList.mkString("\t") + ")" +
        ";for partFile in ${partFiles[@]};" +
        " do " +
        "hdfs dfs -copyToLocal " + hdfsIndexFilePath + "$partFile  " +
        root + indexLocationInRoot + " ;" +
        " done ;chmod  -R 777  " + root + indexLocationInRoot +
        s" ;du -h -s ${root + indexLocationInRoot}"
      array.append((machine, command))

    }
    createMovingDirectory(indexLocationInRoot, rootDirs)

    def deploySolrShards(collectIndices: CollectIndices, command: String): Future[Int] = Future {
      collectIndices.runCommand(command, true)
    }

    val start = System.currentTimeMillis()
    val futureList = array.map(p => {
      val f: Future[Int] = deploySolrShards(p._1, p._2)
      f.onComplete {
        case Success(code)
        => log.info(s"successfully  ran  command ${p._2}" +
          s" on ${p._1.session.getHost} with code $code")
        case Failure(ex)
        => log.error(s"failed to run ${p._2}", ex)
      }
      f
    }
    )
    var areComplete = false
    do {
      var bool = true
      for (f <- futureList) {
        bool = f.isCompleted & bool
      }
      areComplete = bool

    }
    while (!areComplete)
    val finalTime = System.currentTimeMillis()
    log.info(s"time taken to move data to solr local is ${finalTime - start} in milliseconds  ")
    log.info(outMap.toMap)
    outMap.toMap
  }

  def closeSession(): Unit = {
    machineMap.values.foreach(_.disconnectSession())
    machineMap.clear()
  }

  def createMovingDirectory(movingDirectory: String, rootDirs: Array[String]): Unit = {

    machineMap.values.foreach(p => {
      for (root <- rootDirs) {
        val command = s"mkdir ${
          root + movingDirectory
        }"
        p.runCommand(command, false)
      }
    })
  }

  def deleteDirectory(oldCollectionDirectory: String, rootDirs: Array[String]): Unit = {
    machineMap.values.foreach(p => {
      for (root <- rootDirs) {
        val command = s"rm -rf ${
          root + oldCollectionDirectory
        }"
        p.runCommand(command, false)
      }
    })
  }

  def parallelSshFire(sshSequence: Array[(CollectIndices, String, String, String)],
                      directory: String,
                      coreMap: Map[String, String]): MMap[String, ListBuffer[(String, String)]] = {
    var map = MMap[String, ListBuffer[(String, String)]]()

    val pc1: ParArray[(CollectIndices, String, String, String)] =
      ParArray.createFromCopy(sshSequence)

    pc1.tasksupport = new ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(8))
    pc1.map(p => {
      p._1.runCommand(p._2, true)
    })
    for ((machine, command, partFile, shard) <- sshSequence) {

      val host = coreMap(shard)
      val fileName = partFile
      if (map.contains(host)) {
        map(host).append((s"${
          directory
        }$fileName", shard))
      } else {
        map(host) = new ListBuffer[(String, String)]
        map(host).append((s"${
          directory
        }$fileName", shard))
      }
    }
    map
  }

  def getHdfsList(nameNode: String, folderPrefix: String, indexFilePath: String): Array[String] = {
    val configuration: Configuration = new Configuration()
    configuration.set("fs.hdfs.impl", classOf[org.apache.hadoop.hdfs.DistributedFileSystem].getName)
    // 2. Get the instance of the HDFS
    val hdfs = FileSystem.get(new URI(s"hdfs://${
      nameNode
    }"), configuration)
    // 3. Get the metadata of the desired directory
    val fileStatus = hdfs.listStatus(new Path(s"hdfs://${
      nameNode
    }" + indexFilePath))
    // 4. Using FileUtil, getting the Paths for all the FileStatus
    val paths = FileUtil.stat2Paths(fileStatus)
    paths.map(_.toString).filter(p => p.contains(folderPrefix))
  }

}
