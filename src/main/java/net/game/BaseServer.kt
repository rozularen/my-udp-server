package net.game

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.EventLoopGroup
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollDatagramChannel
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.kqueue.KQueue
import io.netty.channel.kqueue.KQueueDatagramChannel
import io.netty.channel.kqueue.KQueueEventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.nio.NioDatagramChannel
import net.MainServer
import net.protocol.ProtocolProvider
import net.session.BaseSession
import net.session.GameSession
import net.session.SessionRegistry
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch

abstract class BaseServer(var server: MainServer,
                          var protocolProvider: ProtocolProvider,
                          var latch: CountDownLatch) {

    private val EPOLL_AVAILABLE = Epoll.isAvailable()
    private val KQUEUE_AVAILABLE = KQueue.isAvailable()

    private var group: EventLoopGroup

    protected var bootstrap: Bootstrap
    protected val sessions: SessionRegistry = SessionRegistry()

    private lateinit var channel: Channel

    init {
        group = createBestEventLoopGroup()

        bootstrap = Bootstrap()
        bootstrap
                .group(group)
                .channel(bestDatagramChannel())
//        option(ChannelOption.SO_RCVBUF, int bytes)
    }

    open fun bind(address: InetSocketAddress) {
        val channelFuture = this.bootstrap.bind(address).addListener {
            if (it.isSuccess) {
                onBindSuccess(address)
            } else {
                onBindFailure(address, it.cause())
            }
        }
        channel = channelFuture.channel()
    }

    open fun onBindSuccess(address: InetSocketAddress) {
        latch.countDown()
    }

    abstract fun newSession(channel: Channel): GameSession

    abstract fun removeSession(baseSession: BaseSession)

    abstract fun onBindFailure(address: InetSocketAddress, t: Throwable)

    private fun createBestEventLoopGroup(): EventLoopGroup {
        return when {
            EPOLL_AVAILABLE -> {
                EpollEventLoopGroup()
            }
            KQUEUE_AVAILABLE -> {
                KQueueEventLoopGroup()
            }
            else -> {
                NioEventLoopGroup()
            }
        }
    }

    /**
     * Gets the "best" server socket channel available.
     *
     *
     * Epoll and KQueue are favoured and will be returned if available, followed by NIO.
     *
     * @return the "best" server socket channel available
     */
    private fun bestDatagramChannel(): Class<out DatagramChannel?>? {
        return when {
            EPOLL_AVAILABLE -> {
                println("EPOLL DATAGRAM CHANNEL")
                EpollDatagramChannel::class.java
            }
            KQUEUE_AVAILABLE -> {
                println("KQUEUE DATAGRAM CHANNEL")
                KQueueDatagramChannel::class.java
            }
            else -> {
                println("NIO DATAGRAM CHANNEL")
                NioDatagramChannel::class.java
            }
        }
    }

    fun shutdown() {
        channel.close()
        bootstrap.config().group().shutdownGracefully()

        try {
            bootstrap.config().group().terminationFuture().sync()
        } catch (e: InterruptedException) {
            System.err.println("Datagram server shutdown process interrupted!")
        }
    }

}

