package org.dalquist.lunchmoney.api

import org.dalquist.lunchmoney.model.PlaidAccounts

interface LunchMoney {
  suspend fun getPlaidAccounts(): PlaidAccounts
}