package com.template.contract

/**
 * Created by stese on 01/08/2017.
 */

import com.template.state.Commands
import com.template.state.CommercialPaperState
import net.corda.contracts.asset.sumCashBy
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.VaultService
import net.corda.core.transactions.TransactionBuilder
import java.time.Instant
import java.util.*

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
    // Contract class MUST provide a verify function but they may also provide HELPER functions to simplify their usage. A simple class of functions most contracts provide are
    // generation functions, which either create or modify  a transaction to perform a certain actions (usually an action is mappable 1:1 to a command but not always)

    // Generation may involve complex logic, for example the cash contract has a generateSpend method that is given a set of cash states and choose a way to combine them together
    // to satisfy the amount of money that is being sent. In the immutable state model that we are using, ledger entries (states) can only be created and deleted and never modified,
    // therefore to spend 12$ when we have 9$ and 5$ we need to combine both outputs together, creating a 12$ to our creditor and 2$ back to ourselves
    // In our commercial paper contract the things that can be done with it are pretty simple

    //Lets start with a method to wrap up the issuance process:

    // we take a reference that points to the issuing party (the caller) and which can contain any internal bookkeeping or reference number that we may require.
    // The reference field is the ideal place to put (for example) a join key. Then the face value of the paper and the maturity date
    // It returns a transaction builder, that is one of the few mutable classes that the platform provides. It allows you to add inputs,ouputs and commands to it
    // and is designed to be passed around, potentially between multiple contracts

    fun generateIssue(issuance: PartyAndReference, faceValue : Amount<Issued<Currency>>, maturityDate : Instant, notary : Party) : TransactionBuilder {

        //The function we define create a CommercialPaperState object that mostly just use the arguments we are were given, but it fills out the owner field of the state
        // with the same public key of the issuing party
        val state = CommercialPaperState(issuance, issuance.party, faceValue, maturityDate)

        // The returned partial transaction has a Command object as a parameter. This is a container for any objects that implements the commandData interface, along with a list
        // of keys that are expected to sign this transaction. In this case issuance requires that the issuing party sign, so we put the key of the party here
        // The transactionBuilder has a convenience withItems method that take a variable argument list. You can pass in any StateAndRef(input), ContractState(output) or commandObject
        // and it will build the transaction for you
        // In addition we ask the caller to select a notary that controls this state and prevents it from being double spent
        return TransactionBuilder(notary = notary).withItems(state, Command(Commands.Issue(), issuance.party.owningKey))


        // NB: Generation methods should ideally be written to compose with each other, i.e. they should take a TransactionBuilder as an argument instead of returning one
        // Unless you are sure it doesn't make sense to combine this type of transaction with others.
        // In this case, issuing a CP at the same time doing other things would just introduce complexity that isn't like to be worth it, so we return a fresh object each time

    }

    // Here the generateMove method takes a pre-existing TransactionBuilder and adds to it. This is correct because typically you will want to combine a sale of CP with the
    // movements of some other asset, such as cash. So both generate methods should operate on the same transaction (an example of this is done in the Unit Test later)

    //The paper is passed as a StateAndRef, a small object that has a copy  of a state object, and also the (txhash, index) that indicates the location of this state on the ledger
    // We adde the existing paper as input, the same paper with the adjusted owner field as output, and finally a move command that has the old owner's public key: this is
    // what forces the current ower's signature to be present on the transaction, and this is what is checked by the contrac
    fun generateMove( tx: TransactionBuilder, paper: StateAndRef<CommercialPaperState>, newOwner : AbstractParty) {

        tx.addInputState(paper)
        tx.addOutputState(paper.state.data.withNewOwner(newOwner).second)
        tx.addCommand(Command(Commands.Move(), paper.state.data.owner.owningKey))

    }

    @Throws (InsufficientBalanceException::class)
    fun generateRedeem(tx: TransactionBuilder, paper: StateAndRef<CommercialPaperState>, vault: VaultService) {
        // Add the cash movements using the states in our vault
        // The vault is a concept that may be familiar from Bitcoin and Ethereum. It is a set of states (such as cash) that are owned by the caller
        // Here we use the vault to update the partial transaction we are handed with a movement of cash from the issuer of the CP to the current owner
        // If we don't have enough cash in our vault an exception is throw

        vault.generateSpend(tx, paper.state.data.faceValue.withoutIssuer(), paper.state.data.owner)
        // Here we see an example of composing contracts together. When an owner wishes to redeem the commercial paper, the issuer ( the caller ) must gather cash from its vault
        // and send the face value to the owner of the paper

        // Then we add the paper itself as an input
        tx.addInputState(paper)
        //Finally we add a redeem command that has to be signed by the owner of the commercial paper
        tx.addCommand(Command(Commands.Redeem(), paper.state.data.owner.owningKey))

    }

    // A transaction builder is not by itself ready to be used anywhere, so first, we must convert it to something that is recognised by the network.
    // The most important next step is for the participating entities to sign it
    // Typically, an initiating flow will create an initial partially signed SignedTransaction by calling the serviceHub.signInitialTransaction method.
    // Then the frozen SignedTransaction can be passed to other nodes by the flow, these can sign using serviceHub.createSignature and distribute.
    // The CollectSignaturesFlow provides a generic implementation of this process that can be used as a subFlow.

    /*
    * Non-Asset-Oriented SmartContracts:
    * It is proper to think of states as represeting of useful facts about the world, and code contracts as imposing logical relations on how facts combine
    * to produce new facts. When writing a contract that handles deal-like entities rather than asset-like entries is better to refer to Interest-Rate-Swaps code
    *
    *
    * Making Things Happen at a particular time:
    * Ex: automatically redeem your commercial paper as soon as it matures. Corda provides a way for states to advertise scheduled events that should occur in the future.
    * Learn more in the Event Scheduling tutorial
    *
    * ENCUMBRANCES:
    *
    * The encumbrance state, if present, forces additional controls over the encumbered state, since the encumbrance state contract will also be verified during
    * the execution of the transaction.
    * For example, a contract state could be encumbered with a time-lock contract state;
    * the state is then only processable in a transaction that verifies that the time specified in the encumbrance time-lock has passed.
    * The encumbered state refers to its encumbrance by index, and the referred encumbrance state is an output state in a particular position on the same transaction that c
    * reated the encumbered state. Note that an encumbered state that is being consumed must have its encumbrance consumed in the same transaction,
    * otherwise the transaction is not valid.
    *
    * CLAUSES:
    *
    * It is common for slightly different contracts to have lots of common logic that can be shared. For example, the concept of being issued, being exited and being upgraded
    * are all usually required by any contract. Corda calls these frequently needed chunks of logic "clauses"
    *
    * */

}