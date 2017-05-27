package com.verizon.bda.apisvcs.security
import com.verizon.bda.apisvcs.utils.HttpServicesConstants._
import com.verizon.bda.commons.serviceapis.security.BDAAuthorizationService
import com.verizon.logger.BDALoggerFactory

/**
  * Created by chundch on 5/9/17.
  */
class FederatedAuthorizerSvc extends ApiAuthorizationService {

  private val logger = BDALoggerFactory.getLogger(this.getClass)

  override def authorizeApiClient(authData: Map[String, String]): (Boolean, Any) = {

    logger.info("authorization data map size : " + (if (authData != null) authData.size else 0  ))

    // @TODO add support for external authenticators

    (true, "")

  }

  override def authorizationDataAccessKeys(): List[String] = {

    val keysList = List(VZ_DATE_HEADER_KEY, VZ_AUTHORIZATION_HEADER_KEY)
    logger.info("authorization data access keys size : " + keysList.size)

    keysList

  }

}
