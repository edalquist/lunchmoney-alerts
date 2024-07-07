package org.dalquist.lunchmoney.alerts

interface Notifier {
  suspend fun send(subject: String, body: String)
}