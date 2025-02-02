package io.novafoundation.nova.runtime.multiNetwork.connection.autobalance.strategy

import io.novafoundation.nova.common.utils.cycle
import io.novafoundation.nova.runtime.multiNetwork.chain.model.Chain

class RoundRobinStrategy : AutoBalanceStrategy {

    override fun generateNodeSequence(defaultNodes: List<Chain.Node>): Sequence<Chain.Node> {
        return defaultNodes.cycle()
    }
}
