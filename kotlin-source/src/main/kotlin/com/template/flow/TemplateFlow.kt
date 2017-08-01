package com.template.flow

import co.paralleluniverse.fibers.Suspendable
import com.template.contract.IOUContract
import com.template.state.IOUState
import net.corda.core.contracts.Command
import net.corda.core.contracts.Requirements.using
import net.corda.core.identity.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.flows.CollectSignaturesFlow
import net.corda.flows.FinalityFlow
import net.corda.flows.SignTransactionFlow

/**
 * A flow describes the sequence of steps necessary to agree on a specific ledger update. By installing new flows on our nodes we can handle more business processes
 * We will have to define two flow to issue an IOUState on the ledger
 * 1) One to be run by the node initiating the creation of the IOU
 * 2) One to be run by the node responding to the creation
 */
object TemplateFlow {
    /**
     * You can add a constructor to each FlowLogic subclass to pass objects into the flow.
     */
    @InitiatingFlow
    class Initiator: FlowLogic<Unit>() {
        /**
         * Define the initiator's flow logic here.
         */
        @Suspendable
        override fun call() {

        }
    }


    //Both classes are overriding flow.call()
    //FlowLogic.call has a return type that matches the type
    @InitiatedBy(Initiator::class)
    class Acceptor(val counterparty: Party) : FlowLogic<Unit>() {
        /**
         * Define the acceptor's flow logic here.
         */
        @Suspendable
        override fun call() {

        }
    }
}


 // Each flow implement a FlowLogic subclass. You define the steps taken by the flow overriding flowLogic.call()
// We will define two FlowLogic communication in pairs, the first will be called INITIATOR, the other ACCEPTOR and will be run by the recipient
// We group them together using a Singleton to show that they are  conceptually related

/*
*  Now we have created our FlowLogic.class(), what are the next steps to allow the creation of an IOU on the ledger?
*  On the Iniator side:
*  1) Creating a valid transaction proposal for the creation of an IOU
*  2) Verify the transaction
*  3) Sign the transaction
*  4) Gather the acceptor signature
*  5) Optionally, get the transaction notarised to:
*         - protect against double spends for transaction with inputs
*         - Timestamp transactions that have a timeWindows
*  6) Record the transaction in our vault
*  7) Send the transaction to the acceptor so they can record it too
*
*  On the Acceptor side:
*  1) Receive the partially signed transaction from the initiator
*  2) Verify its content and signatures
*  3) Append our signature and send it back to the initiator
*  4) Wait to receive back the transaction from the initiator
*  5) Record the transaction in our vault
*
*  These are a lot of things, we can simplify something with the creation of subflows, a subflow is a flow that is invoked in the context of a larger flow to handle a repeatable task
*  In our initiator flow we can automate step 4) with SignTransactionFlow, and step 5,6,7) with FinalityFlow
*  In the acceptor we can automate everything with CollectSignatureFlow
*  Here, all we need to do is to write the steps to handle the initiator to create a transaction, verify and sign it
* */

object IOUFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(val iouValue: Int, val otherParty: Party) : FlowLogic<SignedTransaction>() {

        //ProgressTracker provides checkpoints indicating the progress of the flow to observers
        override val progressTracker: ProgressTracker? = ProgressTracker()
        /**
         * This is where you fill out your business logic.
         * The flow logic is encapsulated within the call() method.
         */

        //To start building the proposed transaction we need a TransactionBuilder. This is a mutable transaction class to which we can add inputs, outputs commands etc

        @Suspendable
        override fun call(): SignedTransaction {

            //Create a transactionBuilder
            val txBuilder = TransactionBuilder()
            //We want our transaction to have a notary, retrieve it from serviceHub
            val notaryIdentity = serviceHub.networkMapCache.getAnyNotary()
            txBuilder.notary = notaryIdentity
            //We can see that the notary identity is being retrieved from the node's serviceHub. Whenever we need information within a flow, about our node,
            //its contents, the rest of the network, etc, we use the node serviceHub
            //In particular, ServiceHub.networkMapCache provides information about all the nodes in the network and the services that they offer

            //Now that we have our txBuilder we need to build its component
            // VERY USEFUL: Draw a picture or a diagram of your transaction

            //so we need: 1) a create command with both signer and receiver
            //2) the IOU output state


            //Create transaction components, retrieve our identity
            val ourIdentity = serviceHub.myInfo.legalIdentity
            // Build the IOUState from otherParty and iouValue are taken from the constructor
            val iou = IOUState(ourIdentity, otherParty, iouValue )
            // We also create a command which pairs the IOUContract.Create() command with the public keys of ourselves and the counterparty
            // if this command is inserted in the transaction, both ourselves and the counterparty will be required to sign
            val txCommand = Command(IOUContract.Create(), iou.participants.map { it.owningKey })

            //Adding items to the transaction
            txBuilder.withItems(iou, txCommand)
            // This takes in input a vararg of: ContractState objects, which are added to the builder as output states
            // StateRef objects (references to the outputs of previous transactions ), which are added to the builder as input state references
            // Command objects, which are added to the transaction builder as commands
            // It will modify the transaction in place and add these components to it

            //Verifying the transaction
            txBuilder.toWireTransaction().toLedgerTransaction(serviceHub).verify()
            // to verify the transaction we must, convert the builder into a WireTransaction
            // convert the wireTransaction into ledgerTransaction using the serviceHub, this step resolve the transaction input reference and state references into actual states
            // and attachments (in case their content are needed to verify the transaction).
            // if the verification fails, we have built an invalid transaction, our flow will end throwing a TransactionVerificationException

            // Signin the transaction. In addition signInitialTransaction returns a SignedTransaction object that pairs the transaction itself with a list of signatures
            // We can now send the builder to our counterParty, if he/she tries to modify it, the transaction hash will change, our digital signature will no longer be valid, and
            // the transaction will not be accepted as a ledger update
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering counterparty signatures, the final step in order to create a valid transaction proposal is to collect the counterparty's signature
            val signedTx = subFlow( CollectSignaturesFlow(partSignedTx, CollectSignaturesFlow.tracker()))
            // CollectSignaturesFlow gathers signatures from every participant  listed on the transaction, and returns a SignedTransaction with all the required signatures

            //Finalizing the transaction
            // Now we have a transaction signed by all parties, what is left is to have it notarised and recorded by all relevant parties, from now on it will became a permanent
            // part of the ledger , we use a built-in subflow called FinalityFlow

            return subFlow(FinalityFlow(signedTx)).single()
            // Finality flow completely automates the flow of
            // 1) Notarising the transaction
            // 2) Recording in on the vault
            // 3) Sending it to the couterparty for the to record as well
            // Finality flow also returns a list of the notarised transactions. We extract the single item from the list and return it

        }


    }

    // We have to notice some things:
    // 1) FlowLogic.call() has a return type that matches the type parameters passed to FlowLogic, this is the return type of the current flow
    // 2) The FlowLogic subclasses can have constructor parameters, which can be used as arguments to FlowLogic.call()
    // 3) FlowLogic.call() in annotated as @Suspendable, this mean that the flow  can be checkpointed and saved to disk when it encounters a long-run operation
    //    allowing the node to move on running flows, every call must be annotated as suspendable
    // 4) There are also other annotations on the FlowLogic subclasses,
    // @InitiatingFlow means that this flow can be started directly by the node
    // @StartableByRPC allow the owner to start the flow through an RPC call
    // @InitiatedBy(myClass :: Class) means that this flow will start only in response to a message sent from another node running the myClass Flow


    // Writing acceptor flow , which is much simpler
    /*
    * 1) Receive the signed tx from the counterparty
    * 2) Verify the transaction
    * 3) Sign the transaction
    * 4) Send the updated transaction back to counterparty
    *
    * As we just saw, the process of building and finalising the transaction will be handled by the initator flow
    * */

    //We can automate all four steps of the acceptor's flow by invoking SignTransactionFlow, a flow registered by default on every node to respond to messages from
    // CollectSignauresFlow (invoked by the initiator flow)

    // SignTransactionFlow is an abstract class, we have to subclass it and override .checkTransaction
    @InitiatedBy(Initiator::class)
    class Acceptor (val otherParty : Party) : FlowLogic<Unit>() {
        /**
         * This is where you fill out your business logic.
         */
        @Suspendable
        override fun call() {
            //Stage one - verify and sign transaction
            // SignTransactionFlow already checks the transaction signatures and whether the transaction is contractually valid

            subFlow(object : SignTransactionFlow(otherParty, tracker()) {
                /**
                 * The [checkTransaction] method allows the caller of this flow to provide some additional checks over the proposed
                 * transaction received from the counter-party. For example:
                 *
                 * - Ensuring that the transaction you are receiving is the transaction you *EXPECT* to receive. I.e. is has the
                 *   expected type and number of inputs and outputs
                 * - Checking that the properties of the outputs are as you would expect. Linking into any reference data sources
                 *   might be appropriate here
                 * - Checking that the transaction is not incorrectly spending (perhaps maliciously) one of your asset states, as
                 *   potentially the transaction creator has access to some of your state references
                 *
                 * **WARNING**: If appropriate checks, such as the ones listed above, are not defined then it is likely that your
                 * node will sign any transaction if it conforms to the contract code in the transaction's referenced contracts.
                 *
                 * [IllegalArgumentException], [IllegalStateException] and [AssertionError] will be caught and rethrown as flow
                 * exceptions i.e. the other side will be given information about what exact check failed.
                 *
                 * @param stx a partially signed transaction received from your counter-party.
                 * @throws FlowException if the proposed transaction fails the checks.
                 */
                //The purpose of checkTransaction is to define additional verification, for example:    IOU value not too large, check that the transaction contains an IOUState
                override fun checkTransaction(stx: SignedTransaction) {

                    val output = stx.tx.outputs.single().data
                    "This must be an IOU transaction" using (output is IOUState)

                    val iou = output as IOUState
                    "The IOU value is too high" using (iou.IOUvalue < 100)
                }


            })
        }




    }


}