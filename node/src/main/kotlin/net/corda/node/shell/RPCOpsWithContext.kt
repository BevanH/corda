package net.corda.node.shell

import net.corda.core.context.InvocationContext
import net.corda.core.messaging.*
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.messaging.CURRENT_RPC_CONTEXT
import net.corda.node.services.messaging.RpcAuthContext
import net.corda.node.services.messaging.RpcPermissions
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

fun makeRPCOpsWithContext(cordaRPCOps: CordaRPCOps, invocationContext:InvocationContext, rpcPermissions: RpcPermissions) : CordaRPCOps {

    class RPCContextRunner<T>(val block:() -> T) : Thread() {
        private var result: CompletableFuture<T> = CompletableFuture()
        override fun run() {
            CURRENT_RPC_CONTEXT.set(RpcAuthContext(invocationContext, rpcPermissions))
            try {
                result.complete(block())
            } catch (e:Throwable) {
                result.completeExceptionally(e)
            }
            CURRENT_RPC_CONTEXT.remove()
        }

        fun get(): Future<T> {
            start()
            join()
            return result
        }
    }
    return Proxy.newProxyInstance(CordaRPCOps::class.java.classLoader, arrayOf(CordaRPCOps::class.java), { proxy, method, args ->
            RPCContextRunner { method.invoke(cordaRPCOps, args) }.get().getOrThrow()
        }) as CordaRPCOps
}