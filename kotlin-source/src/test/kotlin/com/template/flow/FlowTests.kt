package com.template.flow


import com.template.state.IOUState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.getOrThrow
import net.corda.testing.node.MockNetwork
import org.junit.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


// Use flow-test DSL to test our flows, the flow-test DSL works by creating a network of lightweight, "mock" node implementations on which we run our flows

// This create an in-memory network with mocked-up components. The network has two nodes, plus network map and notary nodes. We register any responder flow on our nodes as well
// in this case the IOUFlow.Acceptor

class FlowTests {


    lateinit var net: MockNetwork
    lateinit var a: MockNetwork.MockNode
    lateinit var b: MockNetwork.MockNode
    lateinit var c: MockNetwork.MockNode


    @Before
    fun setup() {
        net = MockNetwork()
        val nodes = net.createSomeNodes(2)
        a = nodes.partyNodes[0]
        b = nodes.partyNodes[1]
        b.registerInitiatedFlow(IOUFlow.Acceptor::class.java)
        net.runNetwork()
    }

    // First test will be to check that the network rejects invalid IOUs
    // Here, node A starts the flow , run IOUFlow.Initiator
    // mockNetwork.runNetwork is required to simulate the running of a real network
    // Flows are instrumented by a library called Quasar, it allows the flows to be checkpointed and serialized to disk

    @Test
    fun flowRejectsInvalidIOUs() {

        val flow = IOUFlow.Initiator(-1, b.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()

        //IOU contract specifies that IOUs cannot have negative value
        assertFailsWith<TransactionVerificationException> { future.getOrThrow() }
    }


    @Test
    fun signedTransactionReturnedByFlowIsSignedByInitiator() {

        val flow = IOUFlow.Initiator(1, b.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifySignatures(b.services.legalIdentityKey)
    }

    @Test
    fun signedTransactionReturnedByFlowIsSignedByAcceptor() {
        val flow = IOUFlow.Initiator(1, b.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifySignatures(a.services.legalIdentityKey)
    }

    @Test
    fun flowRecordTransactionInBothVaults() {

        val flow = IOUFlow.Initiator(1,b.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()
        val signedTx = future.getOrThrow()

        for (node in listOf(a,b)) {
            assertEquals(signedTx, node.storage.validatedTransactions.getTransaction(signedTx.id))
        }
    }


    @Test
    fun recordedTransactionHasNoInputAndSingleOutput() {

        val flow = IOUFlow.Initiator(1, b.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()
        val signedTx = future.getOrThrow()

        //Check recorded tx in both vaults
        for (node in listOf(a,b)) {

            val recordedTx = node.storage.validatedTransactions.getTransaction(signedTx.id)
            val txOutputs = recordedTx!!.tx.outputs
            assert(txOutputs.size == 1)

            val recordedState = txOutputs[0].data as IOUState
            assertEquals(recordedState.IOUvalue, 1)
            assertEquals(recordedState.IOUsender, a.info.legalIdentity)
            assertEquals(recordedState.IOUrecipient, b.info.legalIdentity)

        }


    }



    @After
    fun tearDown() {
        net.stopNodes()
    }


}



