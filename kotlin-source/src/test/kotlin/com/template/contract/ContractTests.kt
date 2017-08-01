package com.template.contract

import com.template.state.IOUState
import net.corda.testing.*
import org.junit.Test


class ContractTests

class IOUContractTest {

    //Indeed create a Test for every verify part?

    @Test // Testing function
    fun transactionMustIncludeCreateCommand() {

        // Create a testing ledger
        ledger {
            // Create a fake transaction
            transaction {
                //You can create output, input or commands
                output { IOUState( MINI_CORP, MEGA_CORP, 10 ) } // create a transaction with an output but with no command
                fails() //Assert that the transaction built so far is either contractually invalid, until here
                command(MINI_CORP_PUBKEY, MEGA_CORP_PUBKEY) {IOUContract.Create()} // add the create command
                verifies() // Or contractually valid here
            }
        }

    }

    @Test
    fun transactionMustHaveNoInputs() {

        ledger {
            transaction {
                input{ IOUState(MEGA_CORP, MINI_CORP, 10)}
                output { IOUState(MEGA_CORP, MINI_CORP, 15)}
                fails()
                command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) {IOUContract.Create()}
                `fails with`("No inputs should be consumed when creating an IOU")

            }
        }
    }

    @Test
    fun transactionMushHaveOneOutput() {
        ledger {
            transaction {
                output{IOUState(MEGA_CORP, MINI_CORP, 100)}
                fails()
                output{(IOUState(MEGA_CORP, MINI_CORP, 200))}
                command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) {IOUContract.Create()}
                `fails with`("Only one output state should be created")

            }
        }
    }

    @Test
    fun senderMustSignTransaction() {
        ledger {
            transaction {
                output{IOUState(MINI_CORP, MEGA_CORP, 10)}
                command(MEGA_CORP_PUBKEY) {IOUContract.Create()}
                `fails with`("All the participants must be signers")
            }
        }
    }

    @Test
    fun recipientMustSignTransaction() {
        ledger {
            transaction {
                output{IOUState(MINI_CORP, MEGA_CORP, 10)}
                command(MINI_CORP_PUBKEY) {IOUContract.Create()}
                `fails with`("All the participants must be signers")
            }
        }
    }

    @Test
    fun senderIsNotRecipient() {
        ledger{
            transaction{
                output{IOUState(MEGA_CORP, MEGA_CORP, 10)}
                command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) {IOUContract.Create()}
                `fails with`("The sender and the recipient must be different")
            }
        }
    }



    @Test
    fun cannotCreateNegativeValueIOU() {
        ledger{
            transaction{
                output{IOUState(MEGA_CORP, MINI_CORP, 0)}
                command(MINI_CORP_PUBKEY, MEGA_CORP_PUBKEY) {IOUContract.Create()}
                `fails with`("The IOU value should be non-negative and greater than zero")
            }
        }
    }

}