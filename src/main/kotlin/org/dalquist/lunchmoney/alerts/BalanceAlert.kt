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
  private val currFormat = DecimalFormat("$#,###,###,##0.00")
  private val ratioFormat = DecimalFormat("0.00")
  private val clock: Clock = Clock.System
  private val lunchMoney: LunchMoney
  private val notifier: Notifier
  private val primaryAccount: String
  private val totalRatio: Double
  private val primaryRatio: Double

  init {
    val conf = Properties()
    val file = this::class.java.classLoader.getResourceAsStream("config.properties")
    conf.load(file)

    primaryAccount = conf.getProperty("primary_account")
    lunchMoney = LunchMoneyImpl(conf.getProperty("api_key"))

    val smtpUser = conf.getProperty("smtp_user")
    notifier = SmtpNotifier(
      user = smtpUser,
      pass = conf.getProperty("smtp_pass"),
      server = conf.getProperty("smtp_server"),
      to = conf.getProperty("smtp_to"),
      from = conf.getProperty("smtp_from", smtpUser),
      port = conf.getProperty("smtp_port", "587").toInt()
    )

    totalRatio = conf.getProperty("total_ratio", "4").toDouble()
    primaryRatio = conf.getProperty("primary_ratio", "1.5").toDouble()
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
    val depositoryDiff =
      depositoryBalance.plus(creditBalance.multiply(BigDecimal(totalRatio))).negate().max(BigDecimal.ZERO)

    val primaryBalance =
      accountsByType["depository"]?.find { a -> primaryAccount == a.displayName }?.balance ?: BigDecimal.ZERO
    val primaryDiff =
      primaryBalance.plus(creditBalance.multiply(BigDecimal(primaryRatio))).negate().max(BigDecimal.ZERO)

    var msg = ""
    if (depositoryDiff.toDouble() > 0) {
      val ratio = depositoryBalance.div(creditBalance.negate())
      msg += "\n\n" + """
        **ALERT**: Total Deposits are low at ${currFormat.format(depositoryBalance)}
          * Ratio is ${ratioFormat.format(ratio)}x and should be at least ${totalRatio}x.
          * Increase by ${currFormat.format(depositoryDiff)}
      """.trimIndent()
    }
    if (primaryDiff.toDouble() > 0) {
      val ratio = primaryBalance.div(creditBalance.negate())
      msg += "\n\n" + """
        **ALERT**: ${primaryAccount} is low at ${currFormat.format(primaryBalance)}
          * Ratio is ${ratioFormat.format(ratio)}x and should be at least ${primaryRatio}x.
          * Increase by ${currFormat.format(primaryDiff)}
      """.trimIndent()
    }

    if (msg.isNotBlank()) {
      msg = """
        Current Credit Balance: ${currFormat.format(creditBalance)}
      """.trimIndent() + msg
      notifier.send("Balances Low", msg)

      write(writer, "<pre>${msg}</pre>")
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
          "<li>${account.displayName} with balance ${currFormat.format(account.balance)} last updated $sinceLastUpdate ago\n"
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
