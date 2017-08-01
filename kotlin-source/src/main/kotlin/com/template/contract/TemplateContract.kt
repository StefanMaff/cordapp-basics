package com.template.contract

import com.template.state.IOUState
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash



/**
 * In Corda the ledger is updated through transactions, transactions consume input states and create output states
 * Each transaction is a proposal to mark zero or more existing state as historic (inputs) while creating new states (outputs)
 * Most CorDapps will want to impose some constraints on how the states will evolve over time, for example:
 * A cash CorDapp will not allow users to generate money out of thin air, without the involvement of a Central Bank at least
 * A loan CorDapp might not allow users to create negative valued loans
 * An asset-trading CorDapp would not want to allow users to finalise a trade without the agreement of the counterparty
 *
 * Just every State, every Contract has to implement the Contract interface
 *
 * interface Contract {
// Implements the contract constraints in code.
@Throws(IllegalArgumentException::class)
fun verify(tx: TransactionForContract)

// Expresses the contract constraints as legal prose.
val legalContractReference: SecureHash
}

 *
 * A contract expresses its constraints in two ways:
 * 1) In legal prose, through a hash referencing a legal contract that expresses the legal constraints in legal prose
 * 2) In code, through a verify function that takes a transaction as input and:
 *      - Throw an IllegalArgumentException if it rejects the transaction proposal
 *      - Return silently ( Unit) if it accepts the transaction
 */

/*  Controlling IOU evolution
*
* What would a good contract for an IOU looks like? There is no right or wrong answer, it depends on how you want your CorDapp to behave
* For this example we will impose the constraint that we only allow the creation of IOUs, we don't want nodes to transfer them (maybe yes)
* or redeem them for cash SO:
*
* 1) A transaction involving IOUs must consume zero inputs and create of output of type IOUState
* 2) The transaction should include a create command, indicating the transaction intent
* 3) For the output IOUState: its value must be non negative, its sender and recipient cannot be the same identity, all the participants must sign the transaction
*
* */

/* Commands
*
* The first thing our contract needs are commands. Commands serve two purposes:
* 1) They indicate the transaction intent, allowing us to perform different verifications given the situation
*    - For example, a transaction proposing the creation of an IOU could have to satisfy different  constraints with respect to one redeeming an IOU
* 2) They allow us to define the required signers of the transaction
*     - For example the creation of an IOU might require signatures from both the sender and the recipient, whereas the transfer of an IOU might require only the sender recipient
*
*
* */

open class TemplateContract : Contract {
    /**
     * The verify() function of the contract of each of the transaction's input and output states must not throw an
     * exception for a transaction to be considered valid.
     */
    override fun verify(tx: TransactionForContract) {}

    /** A reference to the underlying legal contract template and associated parameters. */
    override val legalContractReference: SecureHash = SecureHash.sha256("Prose contract.")
}




open class IOUContract : Contract {
    /**
     * Takes an object that represents a state transition, and ensures the inputs/outputs/commands make sense.
     * Must throw an exception if there's a problem that should prevent state transition. Takes a single object
     * rather than an argument so that additional data can be added without breaking binary compatibility with
     * existing contract code
     */

    // Verify function can only access the content of the transaction
    override fun verify(tx: TransactionForContract) {


/*        // What we will need to access
        tx.inputs
        tx.outputs
        tx.commands // list commands and their authorized signers*/

        // Attachments, timeWindows, notary and hash are also available from tx.

        //Create your constrain and then write the verify function associated to
        //So our verify will have to reject transaction if
        // - Has input - Has more than one output or zero outputs - The IOU is invalid - Transaction doesn't include a create command
        // - the transaction doesn't require (Doesn't have?) signatures from both the sender and the recipient

        // Test presence of the "create" command in our Contrac , we can use requireSingleCommand
        val command = tx.commands.requireSingleCommand<Create>()
        //  requireSingleCommand performs a dual purpose
        // it's asserting that there is exactly a single "Create" command in the transaction
        // it EXTRACT the command and RETURN it
        // if the "create" command isn't present or if the transaction has multiple create commands te verify will fail

        // Requirements on the body of the transaction
        requireThat {
            //Constraints specific on the shape of the transaction
            "No inputs should be consumed when creating an IOU" using (tx.inputs.isEmpty()) // like assert true
            "Only one output state should be created" using (tx.outputs.size == 1) // if is false, throw an exception with IllegalArgumentException

            // IOU-specific constraints
            val out = tx.outputs.single() as IOUState // take the iou output
            "The IOU value should be non-negative and greater than zero" using (out.IOUvalue > 0)
            "The sender and the recipient must be different" using (out.IOUsender != out.IOUrecipient)

            // Constrain on signers
            "All the participants must be signers" using(command.signers.toSet() == out.participants.map {it.owningKey}.toSet())

        }

        // Here we have defined all the logic or our contract, it means that every transaction that involve IOUState will have to fulfill these strict constraints to became valid
        // ledger updates
        // However IOUState must point to IOUContract

    }

    // We can test our contract logic by using ledgerDSL transaction testing framework, this will allow to test without setting up nodes


    //All  commands must implement the CommandData interface
    class Create : CommandData

    /**
     * Unparsed reference to the natural language contract that this code is supposed to express (usually a hash of
     * the contract's contents).
     */

    // Will not write a legal prose of IOU for the court, instead we will focus on the code
    override val legalContractReference: SecureHash = SecureHash.sha256("Legal prose of the contract")

      // get() = SecureHash.sha256("Legal prose of the contract"), probably get() is the same thing as = etc
        //To change initializer of created properties use File | Settings | File Templates.


    //So until now we have assured that an IOUState can only be created and not transferred
    //Creating an IOUState requires no input, one output, two signatures and a positive value
    //Final step create a flow
}



