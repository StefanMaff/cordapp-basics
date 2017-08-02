package com.template.contract

/**
 * Created by stese on 01/08/2017.
 */

import com.template.state.Commands
import com.template.state.CommercialPaperState
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TransactionForContract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.crypto.SecureHash

//Implement the Contract interface or subclass an abstract class like OnLedgerAsset

class CommercialPaperContract : Contract {
    /**
     * Unparsed reference to the natural language contract that this code is supposed to express (usually a hash of
     * the contract's contents).
     */
    //Supposed to be an hash of a document that describes the legal contract and may take precedence over the code, in case of dispute
    // get() = is the same of setting an = after SecureHash
    override val legalContractReference: SecureHash
        get() = SecureHash.sha256("https://en.wikipedia.org/wiki/Commercial_paper");


    //Clause = reusable chunks of contract code, reusable chunks of verification logic, introduced later
    //Each transaction can have multiple input and output states of different types.
    //The set of contracts to run is decided by taking the code references inside each state, each contract is run only ONCE.
    // Ex: a contract that includes 2 cashState and 1 commercialPaperState as input and has output 1 cashState and 1 commercialPaperState will run two contracts
    // CashContract and CommercialPaperContract, one time each.
    override fun verify(tx: TransactionForContract) {

        // Group by everything except owner: any modification to the CommercialPaper at all is considered changing it fundamentally.
        // groupStates take a type and a function.
        // State grouping is a way of ensuring that your contract can handle multiple unrelated states of the same type in the same tx, which is needed for splitting/merging of assets
        // atomic swaps and so on

        // States are fungible if they are treated identically by the recipient, despite the fact that they are not identical.
        // Ex: Dollar bills are fungible because ten $1 bills are the same as one $10 bill, and $1 bill from five months ago is the same as now, however 10$ and 10£ are not fungible
        // if you try to pay something that cost 20£ with 10$ + 10£ the trade will not be accepted
        // To make it easy, the contract API provides a notion of groups. A group is a set of input states and output states that should be checked for validity together
        // Transaction example: Alice input: 12$, 3$ . Bob input : 10£ . Alice output: 10£, Bob output: 15$
        // In this transaction Alice and Bob are trading 15$ for 10£, Alice has her money in form of two different inputs because she received two different payments, the input
        // and output amounts do balance correctly but the smart contract must consider the pounds and dollars separately because they are not fungible, se we have two groups!

        // This groupStates method handle this scenario for us, firstly it selects only the states of the given type (as the transaction may include other type of state, such as
        // states representing bond ownership etc, or a multi sig state) and then it takes a function that maps a state to a grouping key. All staes that share the same key are grouped
        // together, in the example above the key would be the currency

        //In our example ContractPaper are NOT FUNGIBLE, merging and splitting is not allowed. So we need a copy of the state minus the owner field as the grouping key
        val groups = tx.groupStates(CommercialPaperState::withoutOwner)

        //Example of  grouping a cash state through currency, refer to tutorial "Writing a Contract"

/*        // Type of groups is List<InOutGroup<State, Pair<PartyReference, Currency>>>
        val groups = tx.groupStates() { it: Cash.State -> Pair(it.deposit, it.amount.currency) }
        for ((inputs, outputs, key) in groups) {
            // Either inputs or outputs could be empty.
            val (deposit, currency) = key

            ...
        }



        * The groupStates call uses the groupStates function to calculate a groupingKey(). All states that have the same grouping key are placed in the same group
        * A grouping key can be anything that implements equals and hashCode but it's always an aggregate of fields that shouldn't change between input and ouput
        * In the above example we picked the fields that shouldn't change and pack them into a Pair
        *
        *
        **/



        //Remember that we defined our interface Commands in the CommercialPaperState file, so we need to import it.
        // There are two possible things that can be done with a CommercialPaper, one is trading it, the other is redeeming it for cash on or after the maturity date

        // This function does this: it searches for a command object that inherits from our Command interface supertype and either returns it or throws an exception if there are
        // zero or more commands
        val command = tx.commands.requireSingleCommand<Commands>()


    }

}