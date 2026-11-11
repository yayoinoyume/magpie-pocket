package com.magpie.pocket.core.data

import com.magpie.pocket.core.model.Account
import com.magpie.pocket.core.model.AccountQuota
import com.magpie.pocket.core.model.UsageBreakdownItem

/**
 * Routing accounts come from three answers, strongest first. The deployed /api/groups leaves
 * memberInfo[].account out, so the group list usually only names providers; the usage
 * breakdown names the accounts that carried traffic and the quota list names the accounts the
 * server can price. The group answer stays authoritative: when two sources name the same id,
 * the group's provider and availability survive.
 *
 * Rows are keyed by account id and keep the first source that names them. Usage rows carry no
 * availability and quota rows carry no alias beyond the id, so both say UNKNOWN instead of
 * claiming a state the server never reported. A quota answer that names no account adds no row;
 * the provider it belongs to is a provider, not an account.
 */
internal fun mergeAccountSources(
    groups: List<Account>,
    usage: List<UsageBreakdownItem>,
    quotas: List<AccountQuota>
): List<Account> {
    val byId = LinkedHashMap<String, Account>()
    groups.forEach { byId[it.id] = it }
    usage.forEach { row ->
        val id = row.id ?: row.label ?: return@forEach
        if (byId.containsKey(id)) return@forEach
        byId[id] = Account(
            id = id,
            providerId = row.provider ?: "unknown",
            alias = row.label ?: id,
            availability = "UNKNOWN",
            fromUsage = true
        )
    }
    quotas.forEach { quota ->
        // Most quota answers name no account; the few that do are real accounts.
        val id = quota.accountId ?: return@forEach
        if (byId.containsKey(id)) return@forEach
        byId[id] = Account(
            id = id,
            providerId = quota.providerId,
            alias = id,
            availability = "UNKNOWN"
        )
    }
    return byId.values.toList()
}
