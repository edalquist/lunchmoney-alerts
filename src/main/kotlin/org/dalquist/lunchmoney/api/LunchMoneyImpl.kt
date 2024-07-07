package org.dalquist.lunchmoney.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.errors.IOException
import kotlinx.serialization.json.Json
import org.dalquist.lunchmoney.model.PlaidAccounts

class LunchMoneyImpl(private val apiKey: String) : LunchMoney {
  private val httpClient = HttpClient(CIO) {
    install(Logging) {
      logger = Logger.DEFAULT
      level = LogLevel.NONE
    }
    defaultRequest {
      header(HttpHeaders.Authorization, "Bearer $apiKey")
    }
    install(ContentEncoding) {
      deflate(1.0F)
      gzip(0.9F)
    }
    install(ContentNegotiation) {
      json(
        json = Json {
          coerceInputValues = true
        }
      )
    }
  }

  override suspend fun getPlaidAccounts(): PlaidAccounts {
    val response: HttpResponse = httpClient.get("https://dev.lunchmoney.app/v1/plaid_accounts")

    if (response.status != HttpStatusCode.OK) {
      throw IOException("Response not OK: ${response.status}")
    }

    return response.body<PlaidAccounts>()
  }
}