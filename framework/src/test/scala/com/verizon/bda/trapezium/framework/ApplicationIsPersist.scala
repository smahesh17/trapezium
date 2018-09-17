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
package com.verizon.bda.trapezium.framework

import com.verizon.bda.trapezium.framework.handler.FileCopy
import com.verizon.bda.trapezium.framework.manager.WorkflowConfig
import org.apache.spark.sql.SQLContext
import org.slf4j.LoggerFactory

/**
  * @author hutashan test file split
  */
class ApplicationIsPersist extends ApplicationManagerTestSuite {
  var startTime = System.currentTimeMillis() - 500000

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  ignore("iPersist test") {
    val logger = LoggerFactory.getLogger(this.getClass)
    FileCopy.fileDelete("target/testdata")
    ApplicationManager.updateWorkflowTime(startTime, "isPersistTest")
    val workFlowToRun: WorkflowConfig = ApplicationManager.setWorkflowConfig("isPersistTest")
    ApplicationManager.runBatchWorkFlow(
      workFlowToRun,
      appConfig, maxIters = 1)(sparkSession)
    val sqlContext = sparkSession.sqlContext
    logger.info("file should not present")
    intercept[AssertionError] {
      val dfTestBatchTxn6 = sqlContext.read.parquet(
        "../commons-framework/target/testdata/TestBatchTxn6")
      assert(dfTestBatchTxn6.count() > 1)
    }
    val dfTestBatchTxn7 = sqlContext.read.parquet(
      "../commons-framework/target/testdata/TestBatchTxn7")
    assert(dfTestBatchTxn7.count() > 1)
    val dfTestBatchTxn8 = sqlContext.read.parquet(
      "../commons-framework/target/testdata/TestBatchTxn8")
    assert(dfTestBatchTxn8.count() > 1)
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }
}
