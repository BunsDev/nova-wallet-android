package io.novafoundation.nova.feature_governance_impl.presentation.delegation.delegate.detail.main

import androidx.lifecycle.viewModelScope
import io.noties.markwon.Markwon
import io.novafoundation.nova.common.address.AddressIconGenerator
import io.novafoundation.nova.common.address.AddressModel
import io.novafoundation.nova.common.base.BaseViewModel
import io.novafoundation.nova.common.presentation.dataOrNull
import io.novafoundation.nova.common.presentation.mapLoading
import io.novafoundation.nova.common.utils.formatting.format
import io.novafoundation.nova.common.utils.withLoadingResult
import io.novafoundation.nova.feature_account_api.presenatation.account.icon.createAccountAddressModel
import io.novafoundation.nova.feature_account_api.presenatation.actions.ExternalActions
import io.novafoundation.nova.feature_account_api.presenatation.mixin.identity.IdentityMixin
import io.novafoundation.nova.feature_governance_api.domain.delegation.delegate.details.model.DelegateDetails
import io.novafoundation.nova.feature_governance_api.domain.delegation.delegate.details.model.DelegateDetailsInteractor
import io.novafoundation.nova.feature_governance_api.domain.delegation.delegate.details.model.description
import io.novafoundation.nova.feature_governance_impl.data.GovernanceSharedState
import io.novafoundation.nova.feature_governance_impl.presentation.GovernanceRouter
import io.novafoundation.nova.feature_governance_impl.presentation.delegation.delegate.common.DelegateMappers
import io.novafoundation.nova.feature_governance_impl.presentation.delegation.delegate.common.model.RecentVotes
import io.novafoundation.nova.feature_governance_impl.presentation.delegation.delegate.detail.votedReferenda.VotedReferendaPayload
import io.novafoundation.nova.feature_governance_impl.presentation.referenda.details.model.DefaultCharacterLimit
import io.novafoundation.nova.feature_governance_impl.presentation.referenda.details.model.ShortenedTextModel
import io.novafoundation.nova.feature_wallet_api.domain.model.amountFromPlanks
import io.novafoundation.nova.runtime.multiNetwork.chain.model.Chain
import io.novafoundation.nova.runtime.state.chain
import io.novafoundation.nova.runtime.state.chainAndAsset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class DelegateDetailsViewModel(
    private val interactor: DelegateDetailsInteractor,
    private val payload: DelegateDetailsPayload,
    private val iconGenerator: AddressIconGenerator,
    private val externalActions: ExternalActions.Presentation,
    private val identityMixinFactory: IdentityMixin.Factory,
    private val router: GovernanceRouter,
    private val delegateMappers: DelegateMappers,
    private val governanceSharedState: GovernanceSharedState,
    val markwon: Markwon,
) : BaseViewModel(), ExternalActions.Presentation by externalActions {

    val identityMixin = identityMixinFactory.create()

    private val delegateDetailsFlow = flowOf(payload.accountId)
        .withLoadingResult(interactor::getDelegateDetails)
        .shareInBackground()

    val delegateDetailsLoadingState = delegateDetailsFlow.mapLoading { delegateDetails ->
        val (chain, chainAsset) = governanceSharedState.chainAndAsset()

        mapDelegateDetailsToUi(delegateDetails, chain, chainAsset)
    }.shareInBackground()

    init {
        delegateDetailsFlow.onEach {
            identityMixin.setIdentity(it.dataOrNull?.onChainIdentity)
        }.launchIn(viewModelScope)
    }

    fun backClicked() {
        router.back()
    }

    private suspend fun mapDelegateDetailsToUi(
        delegateDetails: DelegateDetails,
        chain: Chain,
        chainAsset: Chain.Asset,
    ): DelegateDetailsModel {

        return DelegateDetailsModel(
            addressModel = createDelegateAddressModel(delegateDetails, chain),
            metadata = createDelegateMetadata(delegateDetails, chain),
            stats = formatDelegationStats(delegateDetails.stats, chainAsset)
        )
    }

    private suspend fun createDelegateAddressModel(delegateDetails: DelegateDetails, chain: Chain): AddressModel {
        val willShowNameOnTop = delegateDetails.metadata != null

        val addressModelName = if (willShowNameOnTop) {
            null
        } else {
            delegateDetails.onChainIdentity?.display
        }

        return iconGenerator.createAccountAddressModel(chain, delegateDetails.accountId, addressModelName)
    }

    private suspend fun createDelegateMetadata(delegateDetails: DelegateDetails, chain: Chain): DelegateDetailsModel.Metadata {
        return DelegateDetailsModel.Metadata(
            name = delegateMappers.formatDelegateName(delegateDetails, chain),
            icon = delegateMappers.mapDelegateIconToUi(delegateDetails),
            accountType = delegateMappers.mapDelegateTypeToUi(delegateDetails.metadata?.accountType),
            description = createDelegateDescription(delegateDetails.metadata)
        )
    }

    private suspend fun formatDelegationStats(stats: DelegateDetails.Stats?, chainAsset: Chain.Asset): DelegateDetailsModel.Stats? {
        if (stats == null) return null

        return DelegateDetailsModel.Stats(
            delegations = stats.delegationsCount.format(),
            delegatedVotes = chainAsset.amountFromPlanks(stats.delegatedVotes).format(),
            recentVotes = RecentVotes(
                label = delegateMappers.formattedRecentVotesPeriod(),
                value = stats.recentVotes.format()
            ),
            allVotes = stats.allVotes.format()
        )
    }

    private fun createDelegateDescription(metadata: DelegateDetails.Metadata?): ShortenedTextModel? {
        val description = metadata?.description ?: return null
        val markdownParsed = markwon.toMarkdown(description)
        return ShortenedTextModel.from(markdownParsed, DefaultCharacterLimit.SHORT_PARAGRAPH)
    }

    fun accountActionsClicked() = launch {
        val address = delegateDetailsLoadingState.first().dataOrNull?.addressModel?.address ?: return@launch
        val chain = governanceSharedState.chain()

        externalActions.showExternalActions(ExternalActions.Type.Address(address), chain)
    }

    fun delegationsClicked() {
        showMessage("TODO - open delegations")
    }

    fun recentVotesClicked() {
        openVotedReferenda(onlyRecentVotes = true)
    }

    fun allVotesClicked() {
        openVotedReferenda(onlyRecentVotes = false)
    }

    private fun openVotedReferenda(onlyRecentVotes: Boolean) {
        val votedReferendaPayload = VotedReferendaPayload(payload.accountId, onlyRecentVotes)
        router.openVotedReferenda(votedReferendaPayload)
    }
}
