/**
  * Copyright (C) 2016 Verizon. All Rights Reserved.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package com.verizon.bda.trapezium.dal.exceptions

/**
  * @author pramod.lakshminarasimha
  *         Dec 15, 2016 debasish83 LuceneDAO exception
  */
case class HiveDAOException(ex: String) extends Exception(ex)

case class LuceneDAOException(ex: String, t: Throwable=null) extends Exception(ex,t)

case class SolrOpsException(ex: String, e: Exception = null) extends Exception(ex, e)
