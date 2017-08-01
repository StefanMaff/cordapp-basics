package com.template.contract

/**
 * Created by stese on 01/08/2017.
 */

import net.corda.core.contracts.Contract
import net.corda.core.contracts.TransactionForContract
import net.corda.core.crypto.SecureHash

//Implement the Contract interface or subclass an abstract class like OnLedgerAsset

class CommercialPaperContract : Contract {
    /**
     * Unparsed reference to the natural language contract that this code is supposed to express (usually a hash of
     * the contract's contents).
     */
    //Supposed to be an hash of a document that describes the legal contract and may take precedence over the code, in case of dispute
    override val legalContractReference: SecureHash
        get() = SecureHash.sha256("https://en.wikipedia.org/wiki/Commercial_paper");

    /**
     * Takes an object that represents a state transition, and ensures the inputs/outputs/commands make sense.
     * Must throw an exception if there's a problem that should prevent state transition. Takes a single object
     * rather than an argument so that additional data can be added without breaking binary compatibility with
     * existing contract code.
     */
    //Clause = reusable chunks of contract code, reusable chunks of verification logic, introduced later
    override fun verify(tx: TransactionForContract) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}