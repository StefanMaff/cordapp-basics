package com.template.contract

/**
 * Created by Stefano Maffullo on 03/08/2017.
 */

/*
* This example will be focused on a basic implementation of a commercial paper (CP), which is essentially a simpler version of a corporate bond.
* A company issues a commercial paper with a particular face value, say $100 but sells it for less, $90 says, the paper may be redeemed for cash at a given maturity date
* In our example the commercial paper has a 10% interest rate with a single repayment, the full Kotlin code can be found in CommercialPaper.Kt
* */

import com.template.state.CommercialPaperState
import net.corda.contracts.CommercialPaper
import net.corda.core.contracts.*
import net.corda.core.contracts.clauses.AnyOf
import net.corda.core.contracts.clauses.Clause
import net.corda.core.contracts.clauses.GroupClauseVerifier
import net.corda.core.contracts.clauses.verifyClause
import net.corda.core.crypto.SecureHash
import net.corda.core.random63BitValue


/*
* Clauses are essentially a micro-contracts which contain independent verification logic, and can be logically composed to form a complete contract
* Clauses are designed to enable the re-use of common verification parts. For example, issuing state objects is generally the same for all fungible contracts, so a common
* issuance clause can be used for each contract's issue clause. This cuts down on scope for error, and improves consistency of behaviour. By splitting verification logic
* into smaller chunks, these can also be readily tested in isolation
* */

/*
* HOW CLAUSES WORKS?
* There are different type of clauses. The most basic are those that define the verification logic for a single command ( Move, Issue and Redeem in case of commercialPaper)
* Or even run without any commands at all (eg TimeStamp)
*
* These basic clauses can be combined using a CompositeClause. The goal of composite clauses is to determine which single clause need to be matched and verified for a single
* transaction to be considered valid. We refer to a clause as being "matched" when the transaction has the required commands present for the clause in question to trigger.
* Meanwhile we talk about a clause "verifying" when it's verify function returns true.
* For example, if we want a transaction to be valid only when every single of its clauses matches and verifies we WRAP the individual clauses in an:
 * 1) AllOf: composite clause which ensures that a transaction is considered valid only when all of its clauses are both matched and verified
 * 2) AnyOf: composite clause whereby 1 or more clauses may match, every matched clause has to be verified
 * 3) FirstOf: at least one clause must match, and the first such clause must verify
 *
 * In addition composite clauses are themselves clauses and can, for example, be wrapped in the special GroupClauseVerifier grouping clause
 * For Charts see https://docs.corda.net/tutorial-contract-clauses.html and https://docs.corda.net/clauses.html
*
* */



class CommercialPaper_withClauses : Contract {
    override val legalContractReference: SecureHash
        get() = SecureHash.sha256("https://en.wikipedia.org/wiki/Commercial_paper")

    //As we can se we used verifyClause with Clauses.Group() not yet implemented? :| in place of our verification logic, it's an entry point to running clause logic
    // VerifyClause takes the transaction, a clause (usually a composite one) and all the commands the transaction is expected to handle
    // This list of commands is important because verifyClause checks that none of the commands are left unprocessed at the end, raising an error if they were

    override fun verify(tx: TransactionForContract) = verifyClause(tx, Clauses.Group(), tx.commands.select<CommercialPaperCommands>())

    interface CommercialPaperCommands : CommandData {
        data class Move(override val contractHash: SecureHash? = null): FungibleAsset.Commands.Move, CommercialPaperCommands
        class Redeem : TypeOnlyCommandData(), CommercialPaperCommands
        data class Issue (override val nonce: Long = random63BitValue()): IssueCommand, CommercialPaperCommands
    }

    // Let's try to move construct logic in term of clauses. The commercial paper contract has three commands and three behaviours: Issue, Move and Redeem.
    // Each of them has a specific set of requirements that must be satisfied - perfect material for defining clauses.
    // For brevity we will only show the Move clause. The rest is constructed in similar manner and is included in the commercialPaper.kt
    interface Clauses{

        //We took some code from the CommercialPaperContract class (the verify part) and added to this verify function, notice that the Move class must extend the clause abstract class
        // which defines the verify function and the requiredCommands property used in order to determine the conditions under which a clause is triggered

        class Move: Clause<CommercialPaperState, CommercialPaperCommands, Issued<CommercialPaper.Terms>>() {

            // Here it means that the clause will run its verification logic when Commands.Move is present in a transaction

            override val requiredCommands: Set<Class<out CommandData>>
                get() = setOf(CommercialPaperCommands.Move::class.java)

            //Notice that commands refers to all input and output states in a transaction. For a clause to be executed, the transaction has to include all the command from requireCommand

            //The verify function returns a set of commands which it has processed, normally this set is EQUAL to the requiredCommands used to trigger the clause
                // However in some cases the clause may process additional commands which it needs to report that it has handled
            override fun verify(tx: TransactionForContract,
                                inputs: List<CommercialPaperState>,
                                outputs: List<CommercialPaperState>,
                                commands: List<AuthenticatedObject<CommercialPaperCommands>>,
                                groupingKey: Issued<CommercialPaper.Terms>?): Set<CommercialPaperCommands> {

                // Ensures that a transaction has only a single command of that kind, if not throw an exception
                val command = commands.requireSingleCommand<CommercialPaperCommands.Move>()

                // Returns a single element, or throw an exception if the List has more than one element
                val input = inputs.single()

                // Verification takes new parameters. Usually inputs and outputs are subsets of the original transaction entries passed to the clause by other composite or
                // grouping clause, groupingKey is a key to group original states.


                // What do we need when someone want to Move his Commercial Paper?
                requireThat {
                    "The transaction is signed by the owner of the CP" using (input.owner.owningKey in command.signers)
                    "The state is propagated" using (outputs.size == 1)
                    // No need to check anything else, as if output==1 then the output is equal to the input ignoring the owner field
                }

                return setOf(command.value)
            }
        }

        class Issue: Clause<CommercialPaperState, CommercialPaperCommands, Issued<CommercialPaper.Terms>>() {
            override fun verify(tx: TransactionForContract,
                                inputs: List<CommercialPaperState>,
                                outputs: List<CommercialPaperState>,
                                commands: List<AuthenticatedObject<CommercialPaperCommands>>,
                                groupingKey: Issued<CommercialPaper.Terms>?): Set<CommercialPaperCommands> {

                // Look at CommercialPaper.kt for the implementation


            }


        }

        class Redeem: Clause<CommercialPaperState, CommercialPaperCommands, Issued<CommercialPaper.Terms>> () {
            override fun verify(tx: TransactionForContract,
                                inputs: List<CommercialPaperState>,
                                outputs: List<CommercialPaperState>,
                                commands: List<AuthenticatedObject<CommercialPaperCommands>>,
                                groupingKey: Issued<CommercialPaper.Terms>?): Set<CommercialPaperCommands> {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.

            }

        }





        /*
    * Let's imagine a simple scenario, three input states:
    * 1: 1000 GBP issued by Bank of England
    * 2: 500 GBP issued by Bank of England
    * 3: 1000 GBP issued by Bank of Scotland
    * We will group states by the issuer, this means that we will have inputs 1 and 2 in one group and input 3 in another group, the keys will be
    * GBP issued by BOE, GBP issued by BOS
    * How are those states passed to the move clause? Answering this question leads us to the concept of clause Groups
    * */

        /*
        *
        * We may have a transaction with similar but unrelated state evolutions which need to be validated independently.
        * It makes sense to check the Move command on groups of related inputs and outputs (like the scenario above)
        * Thus, we need to collect relevant states together, for this we extend the GroupClauseVerifier and specify how to group input and output states, as well as
        * the top level clause to run on each group. In our example the top level clause is AnyComposition that delegates verification to its subclasses (wrapped move,
        * issue and redeem). AnyComposition means that it will take zero or more clauses that match the transaction command
        * */

        // For the commercialPaperContract, Group is  the main clause of the contract, and is passed directly into verifyClause ( see the code at the top)
        // We also use groupStates here, to recall what it does https://docs.corda.net/tutorial-contract.html#state-ref
        class Group : GroupClauseVerifier<CommercialPaperState, CommercialPaperCommands, Issued<CommercialPaper.Terms>>(
                AnyOf(
                        Redeem(),
                        Move(),
                        Issue())) {
            override fun groupStates(tx: TransactionForContract): List<TransactionForContract.InOutGroup<CommercialPaperState, Issued<CommercialPaper.Terms>>> {

                // Terms is a data class that encapsulate CommercialPaper asset and maturityDAte
                return tx.groupStates<CommercialPaperState, Issued<CommercialPaper.Terms>> { it.token }
            }

        }


    }



    /*

    * Summary
    * In summary, the top level contract CommercialPaperContract specifies a single grouping clause of type CommercialPaper.Clauses.Group
    * which in turn specifies GroupClause implementation for each type of command ( Redeem, Move, Issue). This reflects the verification flow, in order to verify the
    * CommercialPaper we first group states and then we check which commands are specified, and finally we run command-specific verification logic
    *
    * verifyClause -> calls -> Clauses.Group.verify()-> GroupClauseVerifier -> AnyOf.verify() [Move,Redeem,Issue]
    *
    * Debugging:
    * Debugging clauses which have been composed together can be complicated due to the difficulty in knowing which clause has been matched, whether specific clauses failed to match
    * or passed verification etc. There is trace level logging code in the clause verifier which evaluates which clauses will be matched and logs them before actually perform the validation
    * To enable this ensure that trace level logging is enabled on the clause interface
    * */



}

