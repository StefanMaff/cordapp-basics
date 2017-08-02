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

//Implements OwnableState that implements ContractState , check their implementation with CTRL+B

/*
* Fields of our state:
* 1) issuance: a reference to a specific piece of commercial paper issued by some party
* 2) owner: the PUBLIC KEY of the current owner, this is the same as expected in Bitcoin: the public key has NO attached identity and is expected to be ONE-USE-ONLY for privacy
*           However, unlike bitcoin, the ownership is modelled at the level of individual states rather than as a platform-level concept, this because we envisage that many (most)
*           contracts will not represent owner/issuer relationships but only party/party relationships such as a derivative contract (TODO: bepi)
* 3) faceValue: wraps an integer number of pennies and a currency that is specific to some issuer
* 4) maturityDate: an Instant, type from Java8 standard time library, defines a point on a timeline
*
* */

//States are immutables, so the class is defined immutable as well, the data modifier generates the equals/hashCode/toString methods automatically, along with a copy() method
//that can be used to create variants of the original object.

data class CommercialPaperState(
        val issuance: PartyAndReference,
        override val owner: AbstractParty,
        val faceValue: Amount<Issued<Currency>>,
        val maturityDate: Instant
) : OwnableState {
    //The ContractState interface (implemented indirectly) requires us to provide a getContract method that returns an instance of the CommercialPaperContract itself
    // N.B. In future, this may change to support dynamic loading of contracts with versioning and signing constraints
    override val contract: Contract = CommercialPaperContract()

    override val participants: List<AbstractParty> = listOf(owner)

    // The withoutOwner use the auto-generated copy method to return a version of the state with the owner public key blanked out, this can be useful later
    fun withoutOwner() = copy(owner = AnonymousParty(NullPublicKey))

    override fun withNewOwner(newOwner: AbstractParty): Pair<CommandData, OwnableState>  = Pair(CommercialPaper.Commands.Move(), copy(owner = newOwner))
}

// The validation logic of a contract may vary depending on what stage of a state's lifecycle it is automating. So it can be useful to pass additional data to the contract code
// that isn't represented by the state (which exists permanently on the ledger), in order to clarify the INTENT of a transaction.

/*
* For this purpose we have commands, often they don't need to contain any data at all, they just need to exist!
* A command is a piece of data associated with some SIGNATURES
* By the time the contract runs the signatures has already been checked, so, for the contract code perspective, a command is simply a data structure with a list of attached public keys
* Each key had a SIGNATURE proving that the corresponding private key was used to sign. Because of this approach, contracts never actually interact or work with digital signatures directly
*
* */

//Let's define some commands through an interface, this is a grouping interface or static class, this gives us a type that all our commands have in common, then we go ahead
// and create three commands, Move, Redeem, Issue.

interface Commands : CommandData {

    //TypeOnlyCommanddata() is a helpful utility (abstract class) for the case when there's no data inside the command, only the existence matters
    // It defines equals and hashCode such that any istance compare equals and hash to the same value
    class Move: TypeOnlyCommandData(), Commands
    class Redeem: TypeOnlyCommandData(), Commands
    class Issue: TypeOnlyCommandData(), Commands
    // TODO: Clarify what Commands stand for here

}