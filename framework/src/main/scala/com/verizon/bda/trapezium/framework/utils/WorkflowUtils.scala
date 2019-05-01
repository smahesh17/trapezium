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
package com.verizon.bda.trapezium.framework.utils

import com.verizon.bda.trapezium.framework.ApplicationManager
import org.slf4j.LoggerFactory
import scopt.OptionParser

/**
 * Created by Pankaj on 7/6/16.
 */
object WorkflowUtils {
  val logger = LoggerFactory.getLogger(this.getClass)

  // Case class to hold the command line arguments
  private case class Params(configDir: String = null,
                            workFlowName: String = null,
                            workflowTime: String = System.currentTimeMillis.toString,
                            action: String = null)

  /**
   * main method to take workflow name and config directory
   * @param args
   */
  def main (args: Array[String]) {

    val defaultParams = Params()

    val parser = new OptionParser[Params]("Workflow Utils") {

      opt[String]("config")
        .text("local conig directory")
        .optional
        .action((x, c) => c.copy(configDir = x))

      opt[String]("workflow")
        .text("workflow name")
        .required
        .action((x, c) => c.copy(workFlowName = x))

      opt[String]("workflowTime")
        .text("workflow time")
        .optional
        .action((x, c) => c.copy(workflowTime = x))

      opt[String]("action")
        .text("action")
        .required
        .action((x, c) => c.copy(action = x))

    }

    parser.parse(args, defaultParams).map { params =>
      run(params)

    } getOrElse {

      logger.error("Insufficient number of arguments {}", getUsage)
      System.exit(1)
    }
  }

  /**
   * Method to print the usage of this Application
   *
   * @return
   */
  private def getUsage: String = {
    "/opt/bda/spark-1.5.1/bin/spark-submit " +
      "--master yarn " +
      "--class com.verizon.bda.trapezium.framework.utils.WorkflowUtils " +
      "--config <configDir> " +
      "--workflow <workFlow> " +
      "--action <GET|SET>"
  }

  /**
   * Parse workflow and reset workflow time
   */
  private def run(params: Params): Unit = {

    // Initialize config file
    val config = ApplicationManager.getConfig(params.configDir)

    // Initialize workflow config file
    val workflowConfig = ApplicationManager.setWorkflowConfig(params.workFlowName)

    params.action.toUpperCase match {
      case "GET" => {

        val currentWorkflowTime =
          ApplicationUtils.getCurrentWorkflowTime(
          config,
          workflowConfig)

        logger.info(s"${params.workFlowName}.lastSuccessfulAccessTime -> ${currentWorkflowTime}")
      }
      case "SET" => {

        ApplicationUtils.updateCurrentWorkflowTime(
          params.workFlowName,
          params.workflowTime.toLong,
          config.zookeeperList
        )

      }
      case _ => {

        logger.warn("Action not supported.")
      }
    }
  }

  /**
   * API to get value for Application Property
   */
  def getAppProperty (propertyName: String,
                  persistSchema: String = null,
                  uid: String = ""): String = {

    // Initialize config file
    val config = ApplicationManager.getConfig()

    val schemaPrefix: String =
      if (persistSchema == null) {

        // if persistSchema is null, use from current workflow
        config.persistSchema
      } else {

        persistSchema
      }

    val propValue = ApplicationUtils.
      getValFromZk(propertyName, config.zookeeperList, s"${schemaPrefix}${uid}/application")

    logger.info(s"appProperty for ${schemaPrefix}${uid} is $propValue")
    propValue
  }

  /**
   * API to set value for Application Property
   */
  def setAppProperty (propertyName: String,
                      propValue: String,
                  persistSchema: String = null,
                  uid: String = ""): Unit = {

    // Initialize config file
    val config = ApplicationManager.getConfig()

    val schemaPrefix: String =
      if (persistSchema == null) {

        // if persistSchema is null, use from current workflow
        config.persistSchema
      } else {

        persistSchema
      }

    ApplicationUtils.updateZookeeperValue(propertyName,
      propValue,
      config.zookeeperList,
      s"${schemaPrefix}${uid}/application")

    logger.info(s"updated appProperty for ${schemaPrefix}${uid} to $propValue")
  }

  /**
   * Get value for property used by framework
   */
  def getFrameworkProperty (workflowName: String,
                            persistSchema: String = null,
                            uid: String = ""): String = {

    // Initialize config file
    val config = ApplicationManager.getConfig()

    val schemaPrefix: String =
      if (persistSchema == null) {

        // if persistSchema is null, use from current workflow
        config.persistSchema
      } else {

        persistSchema
      }

    val propValue = ApplicationUtils.
      getValFromZk(ApplicationUtils.lastSuccessfulAccess,
        config.zookeeperList, s"${schemaPrefix}${uid}/${workflowName}/")

    logger.info(s"frameworkProperty is $propValue")
    propValue
  }

  def getFrameworkPropertyTime (workflowName: String,
                            persistSchema: String = null,
                            zookeeperList: String,
                            uid: String = ""): String = {

    val propValue = ApplicationUtils.
      getValFromZk(ApplicationUtils.lastSuccessfulAccess,
        zookeeperList, s"${persistSchema}${uid}/${workflowName}/")

    logger.info(s"frameworkProperty is $propValue")
    propValue
  }
}
