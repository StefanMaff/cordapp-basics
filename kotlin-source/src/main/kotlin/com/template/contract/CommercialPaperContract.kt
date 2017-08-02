package com.template.contract

/**
 * Created by stese on 01/08/2017.
 */

import com.template.state.Commands
import com.template.state.CommercialPaperState
import net.corda.contracts.asset.sumCashBy
import net.corda.core.contracts.*
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

        // In this example we use the class itself as an aggregator. We just blank out fields that are allowed to change, making the grouping key "everything that is not that"
        // or also "everything that can't change?"
        val groups = tx.groupStates(CommercialPaperState::withoutOwner)

        // For large states with many fields that must remain constant and only one or two that can be mutable it's often easier to do things this way than to name each field to
        // remain the same. The without owner function return a copy of the object but with the PublicKey set to all zeros. It's invalid and useless but it's ok because all we are
        // doing is preventing the field from mattering in the hashCode

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
        * It returns a list of InOutGroup which is an holder for inputs, outputs and the key that was used to define the group, here in Kotlin we use destructuring to get access
        * to the inputs, the outputs, the deposit data and the  currency
        * So the rules can be now applied to inputs and outputs as they were a single transaction. A group can have zero inputs or zero outputs, this could happen when issuing
        * assets on the ledger or removing them
        *
        *
        **/



        //Remember that we defined our interface Commands in the CommercialPaperState file, so we need to import it.
        // There are two possible things that can be done with a CommercialPaper, one is trading it, the other is redeeming it for cash on or after the maturity date
        // This function does this: it searches for a command object that inherits from our Command interface supertype and either returns it or throws an exception if there are
        // zero or more commands
        val command = tx.commands.requireSingleCommand<Commands>()

        //  get the timestamp out of the transaction, for now timestamp is optional (but in future may be mandatory), we check for null later
        val timeWindow : TimeWindow? = tx.timeWindow

        //This is the core of the verify function and of the contract itself
        for ((inputs,outputs,key) in groups  ) {

            when (command.value) {

                // If the command is a move we simply verify that the output State is PRESENT, move can't delete the cp from ledgeer
                // the grouping logic already ensured that all attributes were the same except the public key of the owner
                is Commands.Move -> {
                    //Impose that there must be a single piece of commercial paper in this group, don't allow multiple units of CP to be split or merged even if they are owned
                    // by the same owne. The single method is an extension method defined by the standard library of Kotlin, given a list it throws an exception if the size is not 1
                    // otherwise it returns the item in the list
                    val input = inputs.single()
                    requireThat {
                        // The platform has already verified all the digital signatures before the contract begins execution, all we have to do is verify that the owner's public key
                        // was one of the key that signed the transaction
                        "The transaction is signed by the owner of the Commercial Paper " using (input.owner.owningKey in command.signers)
                        "The state is propagated" using (outputs.size == 1)
                        // Don't need to check anything else, because if the output is equal to one, then everything is equal between input and output
                        // except the owner field due to grouping
                    }
                }

                /*
                * If the command is a redeem it is a bit more complex:
                * 1) we want to see that the face value of the CP is being moved as cash claim against some party, that is the issuer of the CP is really
                * paying back the face value
                * 2) The transaction must appear after maturity date
                * 3) The commercial paper MUST NOT be propagated this time, it must be deleted
                *
                * To calculate how much cash is moving we use SumCashBy utility function, in Kotlin it appears as an extension function of List<Cash.State>
                *     This method returns an amount object containing the sum of all cash states in the transaction output that are owned by that given public key
                *     throw an exception if there were no such states or if there were different currencies in the output
                *  So there is a little limitation here, you can't exchange currencies in a redemption transaction that aren't involved in the CP
                * */
                is Commands.Redeem -> {
                    // redemption of the paper requires movement of on-ledger cash
                    val input = inputs.single()
                    val received = tx.outputs.sumCashBy(input.owner)
                    val time = timeWindow?.fromTime ?: throw IllegalArgumentException("Redemption must be timestamped")
                    requireThat {
                        "The transaction is signed by the owner of the Commercial Paper" using (input.owner.owningKey in command.signers)
                        "The paper must have been matured" using (time >= input.maturityDate)
                        "The receiver amount equals the face value" using (input.faceValue == received)
                        "The paper must be destroyed" using (outputs.isEmpty())
                    }
                }

            /**
             * Last we need the issuance command
             *
             */

                is Commands.Issue -> {
                    val output = outputs.single()
                    val time = timeWindow?.untilTime ?: throw IllegalArgumentException ("Issuance must be timestamped")
                    requireThat {
                        // Don't allow people to issue Commercial Papers under other identities
                        "Output Commercial paper is issued by the command signer " using (output.issuance.party.owningKey in command.signers)
                        "Output value sums to more than the inputs" using (output.faceValue.quantity > 0)
                        "The maturity date is not in the past" using (time < output.maturityDate) //always time first
                        // Don't allow an existing CP state to be replaced by this issuance
                        "Can't re-issue an existing state" using (inputs.isEmpty()) // by inputs.isEmpty() in the tutorial?
                    }
                }

                else -> throw IllegalArgumentException ("Unsupported command ")
            }

        }


    }


    // Adding a Generation API to the contract

}