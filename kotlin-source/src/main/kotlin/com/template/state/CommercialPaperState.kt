package com.template.state

import com.template.contract.CommercialPaperContract
import net.corda.contracts.CommercialPaper
import net.corda.core.contracts.*
import net.corda.core.crypto.NullPublicKey
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import java.time.Instant
import java.util.*

/**
 * Created by stese on 01/08/2017.
 */

//States are necessaries, a state is a class that is checked by the contract
// Best practice: Create/Draw an example of your state before coding it

//Implements OwnableState , check their implementation with CTRL+B

data class commercialPaperState(
        val issuance: PartyAndReference,
        override val owner: AbstractParty,
        val faceValue: Amount<Issued<Currency>>,
        val maturityDate: Instant
) : OwnableState {
    override val contract: Contract = CommercialPaperContract()

    override val participants: List<AbstractParty> = listOf(owner)


    fun withoutOwner() = copy(owner = AnonymousParty(NullPublicKey))

    override fun withNewOwner(newOwner: AbstractParty): Pair<CommandData, OwnableState>  = Pair(CommercialPaper.Commands.Move(), copy(owner = newOwner))


}