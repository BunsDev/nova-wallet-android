package io.novafoundation.nova.common.data.network.ethereum

import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.base.RpcRequest
import org.web3j.protocol.ObjectMapperFactory
import org.web3j.protocol.Web3jService
import org.web3j.protocol.core.BatchRequest
import org.web3j.protocol.core.BatchResponse
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.protocol.websocket.WebSocketService
import org.web3j.protocol.websocket.events.Notification
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class WebSocketWeb3jService(
    private val socketService: SocketService,
    private val jsonMapper: ObjectMapper = ObjectMapperFactory.getObjectMapper()
) : Web3jService {

    /**
     * Implementation based on [WebSocketService.send]
     */
    override fun <T : Response<*>?> send(request: Request<*, out Response<*>>, responseType: Class<T>): T {
        return try {
            sendAsync(request, responseType).get()
        } catch (e: InterruptedException) {
            Thread.interrupted()
            throw IOException("Interrupted WebSocket request", e)
        } catch (e: ExecutionException) {
            if (e.cause is IOException) {
                throw e.cause as IOException
            }
            throw RuntimeException("Unexpected exception", e.cause)
        }
    }

    override fun <T : Response<*>?> sendAsync(request: Request<*, out Response<*>>, responseType: Class<T>): CompletableFuture<T> {
        val rpcRequest = request.toRpcRequest()

        return socketService.executeRequestAsFuture(rpcRequest).thenApply {
            jsonMapper.convertValue(it, responseType)
        }
    }

    override fun <T : Notification<*>?> subscribe(
        request: Request<*, out Response<*>>,
        unsubscribeMethod: String,
        responseType: Class<T>
    ): Flowable<T> {
        val rpcRequest = request.toRpcRequest()

        return socketService.subscribeAsObservable(rpcRequest, unsubscribeMethod).map {
            jsonMapper.convertValue(it, responseType)
        }.toFlowable(BackpressureStrategy.LATEST)
    }

    override fun sendBatch(batchRequest: BatchRequest): BatchResponse {
        TODO("Batches not yet supported")
    }

    override fun sendBatchAsync(batchRequest: BatchRequest): CompletableFuture<BatchResponse> {
        TODO("Batches not yet supported")
    }

    override fun close() {
        // other components handle lifecycle of socketService
    }

    private fun Request<*, *>.toRpcRequest(): RpcRequest {
        val raw = jsonMapper.writeValueAsString(this)

        return RpcRequest.Raw(raw, id.toInt())
    }
}
