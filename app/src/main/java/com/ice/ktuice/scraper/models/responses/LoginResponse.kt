package com.ice.ktuice.scraper.models.responses

import com.ice.ktuice.scraper.models.LoginModel

/**
 * Created by Andrius on 1/23/2018.
 * Contains both the login model and response code from the http request
 */
class LoginResponse(val loginModel: LoginModel?, val statusCode:Int)