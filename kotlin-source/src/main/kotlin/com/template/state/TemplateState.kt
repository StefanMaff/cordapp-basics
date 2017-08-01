package com.template.state

import com.template.contract.IOUContract
import com.template.contract.TemplateContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

/**
 * Define your state object here.
 *
 *  * interface ContractState {
// The contract that imposes constraints on how this state can evolve over time.
val contract: Contract

// The list of entities considered to have a stake in this state.
val participants: List<AbstractParty>
}

1) We need a class that implements ContractState
2) val is like Java final
3) contract: the contract controlling transactions involving this state
4) participants: the list of entities that have to approve state changes such as changing the state notary or
upgrading the state's contract

Beyond this we can create any property, methods or inner class it requires to accurately represent a given class of shared facts on the ledger
ContractState has also various children interfaces that can be helpful to implement such as LinearState and OwnableState


How should we implement the IOUState? We need to model it, it has to hold the relevant properties and the relevant features of the IOU like:
- Sender - Receiver/Recipient - Value of IOU
We could add much many other properties, like value currency etc , if you want, add them as properties
 */

// This is the template of the State

class TemplateState(override val contract: TemplateContract): ContractState {
    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty>
        get() = listOf()
}

// Our IOUState

class IOUState(val IOUsender: Party, val IOUrecipient: Party, val IOUvalue: Int,
               override val contract: IOUContract = IOUContract()) : ContractState {
    /**
     * A _participant_ is any party that is able to consume this state in a valid transaction.
     *
     * The list of participants is required for certain types of transactions. For example, when changing the notary
     * for this state ([TransactionType.NotaryChange]), every participant has to be involved and approve the transaction
     * so that they receive the updated state, and don't end up in a situation where they can no longer use a state
     * they possess, since someone consumed that state during the notary change process.
     *
     * The participants list should normally be derived from the contents of the state. E.g. for [Cash] the participants
     * list should just contain the owner.
     */

    // We overridden  participants to return a list of Sender and Recipient, this means that a change to the state's contract or a change to the notary will require
    // Approval from both sender and recipient
    override val participants: List<AbstractParty>
        get() = listOf(IOUsender, IOUrecipient) //To change initializer of created properties use File | Settings | File Templates.
    // override val participants get() = listOf(IOUsender, IOUrecipient) //To change initializer of created properties use File | Settings | File Templates.


}