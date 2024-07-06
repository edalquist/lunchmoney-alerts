package org.dalquist.lunchmoney.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal


@Serializable
enum class PlaidAccountStatus {
  UNKNOWN,

  @SerialName("active") // Account is active and in good state
  ACTIVE,

  @SerialName("inactive") // Account marked inactive from user. No transactions fetched or balance update for this account.
  INACTIVE,

  @SerialName("relink") // Account needs to be relinked with Plaid.
  RELINK,

  @SerialName("syncing") // Account is awaiting first import of transactions
  SYNCING,

  @SerialName("error") // Account is in error with Plaid
  ERROR,

  @SerialName("not found") // Account is in error with Plaid
  NOT_FOUND,

  @SerialName("not supported") // Account is in error with Plaid
  NOT_SUPPORTED,
}

@Serializable
data class PlaidAccounts(
  @SerialName("plaid_accounts") val plaidAccounts: List<PlaidAccount>,
)

@Serializable
data class PlaidAccount(
  val id: Int,
  @SerialName("date_linked") val dateLinked: LocalDate,
  val name: String,
  @SerialName("display_name") val displayName: String,
  val type: String,
  val subtype: String = "",
  val mask: String,
  @SerialName("institution_name") val institutionName: String,
  val status: PlaidAccountStatus,
  val limit: Int = Int.MIN_VALUE,
  @Serializable(with = BigDecimalSerializer::class)
  val balance: BigDecimal,
  val currency: String,
  @SerialName("balance_last_update") val balanceLastUpdate: Instant,
  @SerialName("import_start_date") val importStartDate: LocalDate = LocalDate.fromEpochDays(0),
  @SerialName("last_import") val lastImport: Instant = Instant.DISTANT_PAST,
  @SerialName("last_fetch") val lastFetch: Instant = Instant.DISTANT_PAST,
  @SerialName("plaid_last_successful_update") val plaidLastSuccessfulUpdate: Instant,
)
