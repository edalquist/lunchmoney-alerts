package org.dalquist.lunchmoney.alerts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.mail.HtmlEmail

class SmtpNotifier(
  private val user: String,
  private val pass: String,
  private val server: String,
  private val to: String,
  private val from: String,
  private val port: Int = 587,
) : Notifier {

  override suspend fun send(subject: String, body: String) {
    val email: HtmlEmail = HtmlEmail()
    email.hostName = server
    email.setSmtpPort(port)
    email.setAuthentication(user, pass)
    email.isSSLOnConnect = true
    email.setFrom(from)
    email.addTo(to)
    email.subject = subject
    email.setMsg(body)

    withContext(Dispatchers.IO) {
      email.send()
    }
  }
}