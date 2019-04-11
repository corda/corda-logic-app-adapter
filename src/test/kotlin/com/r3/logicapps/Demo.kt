package com.r3.logicapps

import com.r3.logicapps.RPCRequest.InvokeFlowWithoutInputStates
import com.r3.logicapps.RPCResponse.FlowOutput
import com.r3.logicapps.rpc.RPCInvoker
import com.r3.logicapps.servicebus.ServicebusMessage
import com.r3.logicapps.servicebus.consumer.ServicebusConsumer
import com.r3.logicapps.servicebus.producer.ServicebusProducer
import com.r3.logicapps.workbench.WorkbenchAdapter
import net.corda.core.contracts.UniqueIdentifier
import java.util.*

object Demo {
    val dummyAdapter = object : WorkbenchAdapter {
        override fun transformIngress(message: ServicebusMessage): RPCRequest {
            // determine type and convert to RPCRequest object
            return InvokeFlowWithoutInputStates("id", "name", mapOf("par" to "ams"))
        }

        override fun transformEgress(message: RPCResponse): ServicebusMessage {
            // get message and translate to Workbench JSON
            return "response from corda"
        }
    }

    val dummyRPCInvoker = object : RPCInvoker {
        override fun invoke(message: RPCRequest): RPCResponse? {
            // call corda with request
            // transform reponse to message format
            val transformed = dummyAdapter.transformEgress(
                FlowOutput(
                    "id",
                    UniqueIdentifier.fromString(UUID.randomUUID().toString()),
                    mapOf(),
                    true
                )
            )
            // put response on bus
            dummyProducer.handleMessage(transformed)
            return null
        }
    }

    val dummyConsumer = object : ServicebusConsumer {
        override fun handleMessage(message: String) {
            val transformed = dummyAdapter.transformIngress(message)
            dummyRPCInvoker.invoke(transformed)
        }
    }

    val dummyProducer = object : ServicebusProducer {
        override fun handleMessage(string: String) {
            println("put onto bus")
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        // end to end
        dummyConsumer.handleMessage("whatever")
    }
}