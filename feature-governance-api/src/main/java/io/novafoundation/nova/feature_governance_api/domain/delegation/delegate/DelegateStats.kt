package io.novafoundation.nova.feature_governance_api.domain.delegation.delegate

import io.novafoundation.nova.feature_wallet_api.data.network.blockhain.types.Balance
import kotlin.time.Duration

class DelegateStats(val delegationsCount: Int, val delegatedVotes: Balance, val recentVotes: RecentVotes) {

    class RecentVotes(val numberOfVotes: Int, val period: Duration)
}
