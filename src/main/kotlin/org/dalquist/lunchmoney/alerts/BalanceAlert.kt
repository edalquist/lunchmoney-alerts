package org.dalquist.lunchmoney.alerts

import com.google.cloud.functions.HttpFunction
import com.google.cloud.functions.HttpRequest
import com.google.cloud.functions.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.dalquist.lunchmoney.api.LunchMoney
import org.dalquist.lunchmoney.api.LunchMoneyImpl
import org.dalquist.lunchmoney.model.PlaidAccount
import java.io.BufferedWriter
import java.io.IOException
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.Properties

class BalanceAlert : HttpFunction {
  private val clock: Clock = Clock.System
  private val lunchMoney: LunchMoney
  private val primaryAccount: String
  private val CF = DecimalFormat("$#,###,###,##0.00")

  init {
    val properties = Properties()
    val file = this::class.java.classLoader.getResourceAsStream("config.properties")
    properties.load(file)

    val apiKey = properties.getProperty("api_key")
    lunchMoney = LunchMoneyImpl(apiKey)

    primaryAccount = properties.getProperty("primary_account")
  }

  // Simple function to return "Hello World"
  @Throws(IOException::class)
  override fun service(request: HttpRequest, response: HttpResponse) {
    response.setContentType("text/html")
    response.writer.write("<html><body>")

    runBlocking {
      launch {
        doAlert(response.writer)
      }
    }

    response.writer.write("</body></html>")
  }

  private suspend fun doAlert(writer: BufferedWriter) {
    val plaidAccounts = lunchMoney.getPlaidAccounts()

    val accountsByType = plaidAccounts.plaidAccounts.groupBy { account -> account.type }

    val creditBalance = getTotalBalance(accountsByType["credit"]).negate()

    val depositoryBalance = getTotalBalance(accountsByType["depository"])
    val depositoryDiff = depositoryBalance.plus(creditBalance.multiply(BigDecimal(4))).negate().max(BigDecimal.ZERO)

    val primaryBalance =
      accountsByType["depository"]?.find { a -> primaryAccount == a.displayName }?.balance ?: BigDecimal.ZERO
    val primaryDiff = primaryBalance.plus(creditBalance.multiply(BigDecimal(2))).negate().max(BigDecimal.ZERO)

    write(writer, "Credit Balance: ${CF.format(creditBalance)}<br>")

    write(writer, "Deposit Balance: ${CF.format(depositoryBalance)}<br>")
    if (depositoryDiff.toDouble() > 0) {
      write(writer, "ALERT: Increase Total Deposits by ${CF.format(depositoryDiff)}<br>")
    }

    write(writer, "Primary Balance: ${CF.format(primaryBalance)}<br>")
    if (primaryDiff.toDouble() > 0) {
      write(writer, "ALERT: Increase Primary Deposits by ${CF.format(primaryDiff)}<br>")
    }
    write(writer, "<br>")

    write(writer, "Accounts<ul>\n")
    for ((type, accounts) in accountsByType) {
      write(writer, "<li>$type\n")
      write(writer, "<ul>\n")
      for (account in accounts.sortedBy { a -> a.balance }) {
        val sinceLastUpdate = clock.now().minus(account.balanceLastUpdate)
        write(
          writer,
          "<li>${account.displayName} with balance ${CF.format(account.balance)} last updated $sinceLastUpdate ago\n"
        )
      }
      write(writer, "</ul>\n")
    }
    write(writer, "</ul>\n")
  }

  private fun getTotalBalance(accounts: List<PlaidAccount>?): BigDecimal {
    if (accounts == null) {
      return BigDecimal.ZERO
    }

    return accounts
      .map { a -> a.balance }
      .reduce { acc, b -> acc.add(b) }
  }

  private suspend fun write(writer: BufferedWriter, str: String) {
    withContext(Dispatchers.IO) {
      writer.write(str)
    }
  }
}
