package com.ice.ktuice.al.services.scrapers.base.ktuScraperService.handlers

import com.ice.ktuice.al.logger.IceLog
import com.ice.ktuice.al.services.scrapers.base.exceptions.ServerErrorException
import com.ice.ktuice.models.LoginModel
import com.ice.ktuice.models.YearModel
import com.ice.ktuice.models.responses.LoginResponseModel
import io.realm.RealmList
import org.jsoup.Connection
import org.jsoup.Jsoup

class LoginHandler: BaseHandler(), IceLog {

    fun getAuthCookies(username: String, password: String): LoginResponseModel {
        val aisTracker = getTracker()
        Thread.sleep(28)
        val autoLogin = getAutoLogin( aisTracker )
        val postLogin = postLogin(username, password, autoLogin)
        Thread.sleep(182)
        if (postLogin.cookies != null) {
            val agreeLogin = getAgree(postLogin)
            Thread.sleep(100)
            val postContinue = postContinue(agreeLogin, aisTracker)
            Thread.sleep(200)
            return getInfo(postContinue, username, password)
        }

        // if there are no cookies returned from postLogin,
        // assume not authorized!
        return LoginResponseModel(null, 401)
    }

    private val userAgentCode = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36"

    private class AutoLoginResponse(
            val authState: String,
            val cookies: Map<String, String>,
            val responseCode: Int
    )

    private class PostLoginResponse(
            val stateId: String?,
            val cookies: Map<String, String>?,
            val responseCode: Int
    )
    private class AgreeResponse(
            val samlResponse: String,
            val relayState: String,
            val responseCode: Int
    )
    private class AuthResponse(
            val authCookies: Map<String, String>,
            val responseCode: Int
    )

    /**
     * This is the default login page, where if
     * the connecting client has appropriate shib session cookies,
     * it gives a STUDCOOKIE, both of which are needed to authenticate a user
     * @return AuthState and cookies
     */
    private fun getTracker() : AutoLoginResponse {
        val url = "https://uais.cr.ktu.lt/ktuis/stp_prisijungimas"
        val request = Jsoup.connect(url)
                .method(Connection.Method.GET)
                .userAgent(userAgentCode)
                .execute()
        return AutoLoginResponse("", request.cookies(),0)
    }

    private fun getAutoLogin( trackerResponse : AutoLoginResponse): AutoLoginResponse {
        val url = "https://uais.cr.ktu.lt/ktuis/studautologin"
        val request = Jsoup.connect(url)
                .method(Connection.Method.GET)
                .cookies(trackerResponse.cookies)
                .userAgent(userAgentCode)
                .execute()
        val parse = request.parse()
        val select = parse.select("input[name=\"AuthState\"]")
        val attr = select[0].attr("value")

        return AutoLoginResponse(attr, request.cookies() + trackerResponse.cookies, request.statusCode())
    }

    private fun postLogin(
            username: String,
            password: String,
            autoLoginResponse: AutoLoginResponse): PostLoginResponse {

        val url = "https://login.ktu.lt/simplesaml/module.php/core/loginuserpass.php"
        val request = Jsoup.connect(url)
                .cookies(autoLoginResponse.cookies)
                .data(mapOf(
                        "username" to username,
                        "password" to password,
                        "AuthState" to autoLoginResponse.authState
                ))
                .method(Connection.Method.POST)
                .userAgent(userAgentCode)
                .execute()
        val parse = request.parse()
        val stateId = parse.baseUri().substring(79).split('&')[0]
        //val r = request.cookies() + autoLoginResponse.cookies
        return PostLoginResponse(stateId, request.cookies() + autoLoginResponse.cookies, request.statusCode())
    }

    private fun getAgree(postLoginResponse: PostLoginResponse, retries: Int = 0): AgreeResponse {
        val url = "https://login.ktu.lt/simplesaml/module.php/consentAleph/getconsent.php?" +
                "StateId=${postLoginResponse.stateId}&" +
                "yes=Yes%2C%20continue%0D%0A&" +
                "saveconsent=1"
        val request = Jsoup.connect(url)
                .method(Connection.Method.GET)
                .cookies(postLoginResponse.cookies)
                .followRedirects(true)
                .userAgent(userAgentCode)
                .execute()
        val parse = request.parse()
        val inputList = parse.select("input")
        try {
            val StateId = inputList.first { it.attr("name") == "StateId" }.attr("value")
            if (StateId != postLoginResponse.stateId) {
                // retry on StateId mismatch
                val newCookies = postLoginResponse.cookies!!.toMutableMap()
                if(request.hasCookie("SimpleSAMLSessionID")) {
                    newCookies["SimpleSAMLSessionID"] = request.cookie("SimpleSAMLSessionID")
                }
                if(retries < 5) {
                    return getAgree(PostLoginResponse(StateId, newCookies, request.statusCode()), retries + 1)
                }else{
                    throw ServerErrorException("Log in could not complete successfully!")
                }
            }
        }catch (e: NoSuchElementException){
            // if the correct no-js version is fetched
        }
        val samlResponse = inputList.first { it.attr("name") == "SAMLResponse" }.attr("value")
        val relayState = inputList.first { it.attr("name") == "RelayState" }.attr("value")
        return AgreeResponse(samlResponse, relayState, request.statusCode())
    }
    private fun postContinue(agreeResponse: AgreeResponse, trackerResponse: AutoLoginResponse): AuthResponse {
        val url = "https://uais.cr.ktu.lt/shibboleth/SAML2/POST"
        val trackerCookie =trackerResponse.cookies["aistrack"]
        val request = Jsoup.connect(url)
                .method(Connection.Method.POST)
                .data(mapOf(
                        "SAMLResponse" to agreeResponse.samlResponse,
                        "RelayState" to agreeResponse.relayState,
                        "aistrack" to trackerCookie
                ))
                .userAgent(userAgentCode)
                .execute()

        return AuthResponse(request.cookies() + trackerResponse.cookies, request.statusCode())
    }

    private fun getInfo(authResponse: AuthResponse, username: String, password: String): LoginResponseModel {
        val url = "https://uais.cr.ktu.lt/ktuis/vs.ind_planas"
        val request = Jsoup.connect(url)
                .cookies(authResponse.authCookies)
                .method(Connection.Method.GET)
                .userAgent(userAgentCode)
                .execute()

        request.charset("windows-1257")
        val parse = request.parse()

        val nameItemText = parse.select("#ais_lang_link_lt").parents().first().text()
        val studentId = nameItemText.split(' ')[0].trim()
        val studentName = nameItemText.split(' ')[1].trim()
        val studyList = mutableListOf<YearModel>().apply {
            val studyYears = parse.select(".ind-lst.unstyled > li > a")
            val yearRegex = "plano_metai=([0-9]+)".toRegex()
            val idRegex = "p_stud_id=([0-9]+)".toRegex()
            studyYears.forEach { yearHtml ->
                val link = yearHtml.attr("href")
                val id = idRegex.find(link)!!.groups[1]!!.value
                val year = yearRegex.find(link)!!.groups[1]!!.value
                add(YearModel(id, year))
            }
        }
        val loginModel = LoginModel(
                studentName = studentName,
                studentId = studentId,
                studentSemesters = RealmList<YearModel>().apply { addAll(studyList) },
                username = username,
                password = password
        )
        loginModel.setCookieMap(authResponse.authCookies)
        return LoginResponseModel(loginModel, request.statusCode())
    }

}