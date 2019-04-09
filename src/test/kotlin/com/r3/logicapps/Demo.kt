package com.r3.logicapps

import com.r3.logicapps.RPCRequest.InvokeFlowWithoutInputStates
import com.r3.logicapps.RPCResponse.FlowOutput
import com.r3.logicapps.rpc.RPCInvoker
import com.r3.logicapps.servicebus.ServicebusMessage
import com.r3.logicapps.servicebus.consumer.ServicebusConsumer
import com.r3.logicapps.servicebus.producer.ServicebusProducer
import com.r3.logicapps.workbench.WorkbenchAdapter

object Demo {
    val dummyAdapter = object : WorkbenchAdapter {
        override fun transformIngress(message: ServicebusMessage): RPCRequest {
            // determine type and convert to RPCRequest object
            return InvokeFlowWithoutInputStates(mapOf("message from" to "bus"))
        }

        override fun transformEgress(message: RPCResponse): ServicebusMessage {
            // get message and translate to Workbench JSON
            return "response from corda"
        }
    }

    val dummyRPCInvoker = object : RPCInvoker {
        override fun invoke(message: RPCRequest) {
            // call corda with request
            // transform reponse to message format
            val transformed = dummyAdapter.transformEgress(FlowOutput(mapOf("return from" to "corda"), "tx id"))
            // put response on bus
            dummyProducer.handleMessage(transformed)
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