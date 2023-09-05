package io.novafoundation.nova.feature_staking_impl.domain.staking.start.setupStakingType

import io.novafoundation.nova.common.utils.combine
import io.novafoundation.nova.feature_staking_api.domain.model.Validator
import io.novafoundation.nova.feature_staking_impl.domain.nominationPools.model.NominationPool
import io.novafoundation.nova.feature_staking_impl.domain.staking.start.common.selection.RecommendableMultiStakingSelection
import io.novafoundation.nova.feature_staking_impl.domain.staking.start.common.selection.SelectionTypeSource
import io.novafoundation.nova.feature_staking_impl.domain.staking.start.common.selection.StartMultiStakingSelection
import io.novafoundation.nova.feature_staking_impl.domain.staking.start.common.selection.store.StartMultiStakingSelectionStoreProvider
import io.novafoundation.nova.feature_staking_impl.domain.staking.start.common.selection.store.currentSelectionFlow
import io.novafoundation.nova.feature_staking_impl.domain.staking.start.common.types.CompoundStakingTypeDetailsProvidersFactory
import io.novafoundation.nova.feature_staking_impl.domain.staking.start.common.types.StakingTypeDetailsProvider
import io.novafoundation.nova.feature_staking_impl.domain.staking.start.setupAmount.direct.DirectStakingSelection
import io.novafoundation.nova.feature_staking_impl.domain.staking.start.setupAmount.pools.NominationPoolSelection
import io.novafoundation.nova.feature_staking_impl.domain.staking.start.setupStakingType.direct.EditingStakingTypeValidationSystem
import io.novafoundation.nova.feature_staking_impl.domain.staking.start.setupStakingType.model.ValidatedStakingTypeDetails
import io.novafoundation.nova.runtime.ext.StakingTypeGroup
import io.novafoundation.nova.runtime.ext.group
import io.novafoundation.nova.runtime.multiNetwork.ChainWithAsset
import io.novafoundation.nova.runtime.multiNetwork.chain.model.Chain
import java.math.BigInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class EditingStakingTypeSelectionMixinFactory(
    private val currentSelectionStoreProvider: StartMultiStakingSelectionStoreProvider,
    private val editableSelectionStoreProvider: StartMultiStakingSelectionStoreProvider,
    private val compoundStakingTypeDetailsProvidersFactory: CompoundStakingTypeDetailsProvidersFactory
) {

    suspend fun create(
        scope: CoroutineScope,
        chainWithAsset: ChainWithAsset,
        availableStakingTypes: List<Chain.Asset.StakingType>
    ): EditingStakingTypeSelectionMixin {
        return EditingStakingTypeSelectionMixin(
            currentSelectionStoreProvider,
            editableSelectionStoreProvider,
            compoundStakingTypeDetailsProvidersFactory.create(scope, chainWithAsset, availableStakingTypes),
            scope
        )
    }
}

class EditingStakingTypeSelectionMixin(
    private val currentSelectionStoreProvider: StartMultiStakingSelectionStoreProvider,
    private val editableSelectionStoreProvider: StartMultiStakingSelectionStoreProvider,
    private val stakingTypeDetailsProviders: List<StakingTypeDetailsProvider>,
    private val scope: CoroutineScope
) {

    val editableSelectionFlow = editableSelectionStoreProvider.currentSelectionFlow(scope)
        .filterNotNull()

    val currentSelectionFlow = currentSelectionStoreProvider.currentSelectionFlow(scope)
        .filterNotNull()

    val availableToRewriteData = combine(
        currentSelectionFlow,
        editableSelectionFlow
    ) { current, editable ->
        !current.selection.isSettingsEquals(editable.selection)
    }

    fun getValidationSystem(stakingType: Chain.Asset.StakingType): EditingStakingTypeValidationSystem? {
        val stakingTypeDetailsProvider = stakingTypeDetailsProviders.firstOrNull { it.stakingType == stakingType } ?: return null
        return stakingTypeDetailsProvider.getValidationSystem()
    }

    fun getEditableStakingTypes(): Flow<List<ValidatedStakingTypeDetails>> {
        val comparator = getEditableStakingTypeComparator()
        return stakingTypeDetailsProviders.map { it.stakingTypeDetails }
            .combine()
            .map { it.sortedWith(comparator) }
    }

    suspend fun enteredAmount(): BigInteger? {
        return currentSelectionStoreProvider.getSelectionStore(scope).currentSelection?.selection?.stake ?: return null
    }

    suspend fun setRecommendedSelection(stakingType: Chain.Asset.StakingType) {
        val currentStake = enteredAmount() ?: return

        val stakingTypeDetailsProvider = stakingTypeDetailsProviders.firstOrNull { it.stakingType == stakingType } ?: return
        val recommendedSelection = stakingTypeDetailsProvider.recommendationProvider
            .recommendedSelection(currentStake)

        val recommendableMultiStakingSelection = RecommendableMultiStakingSelection(
            source = SelectionTypeSource.Manual(contentRecommended = true),
            selection = recommendedSelection
        )

        editableSelectionStoreProvider.getSelectionStore(scope)
            .updateSelection(recommendableMultiStakingSelection)
    }

    suspend fun apply() {
        val recommendableSelection = editableSelectionStoreProvider.getSelectionStore(scope).currentSelection ?: return
        currentSelectionStoreProvider.getSelectionStore(scope)
            .updateSelection(recommendableSelection)
    }

    suspend fun selectNominationPoolAndApply(pool: NominationPool) {
        val editingSelection = editableSelectionFlow.first().selection as? NominationPoolSelection ?: return
        val newSelection = editingSelection.copy(pool = pool)
        setSelectionAndApply(newSelection)
        apply()
    }

    suspend fun selectValidatorsAndApply(validators: List<Validator>) {
        val editingSelection = editableSelectionFlow.first().selection as? DirectStakingSelection ?: return
        val newSelection = editingSelection.copy(validators = validators)
        setSelectionAndApply(newSelection)
    }

    private suspend fun setSelectionAndApply(selection: StartMultiStakingSelection) {
        currentSelectionStoreProvider.getSelectionStore(scope)
            .updateSelection(
                RecommendableMultiStakingSelection(
                    SelectionTypeSource.Manual(contentRecommended = false),
                    selection
                )
            )
    }

    private fun getEditableStakingTypeComparator(): Comparator<ValidatedStakingTypeDetails> {
        return compareBy {
            when (it.stakingTypeDetails.stakingType.group()) {
                StakingTypeGroup.NOMINATION_POOL -> 0
                StakingTypeGroup.RELAYCHAIN -> 1
                else -> 3
            }
        }
    }
}
