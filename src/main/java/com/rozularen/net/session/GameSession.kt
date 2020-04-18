package com.rozularen.net.session

import com.rozularen.entity.location.Location
import com.rozularen.entity.meta.PlayerProfile
import com.rozularen.entity.player.GamePlayer
import com.rozularen.net.MainServer
import com.rozularen.net.game.GameServer
import com.rozularen.net.message.AsyncGameMessage
import com.rozularen.net.message.GameMessage
import com.rozularen.net.message.login.LoginSuccessMessage
import com.rozularen.net.protocol.ProtocolProvider
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.handler.codec.CodecException
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

class GameSession(server: MainServer,
                  channel: Channel,
                  gameServer: GameServer) : BaseSession(server, ProtocolProvider.HANDSHAKE, channel, gameServer) {

    var version: Int = 0
    var virtualHost: InetSocketAddress? = null
    private var player: GamePlayer? = null
    private val messageQueue: Queue<GameMessage> = ConcurrentLinkedDeque<GameMessage>()

    var online: Boolean = false
    var disconnected: Boolean = false

    override fun messageReceived(message: GameMessage) {
        if (message is AsyncGameMessage && message.isAsync()) {
            super.messageReceived(message)
        } else {
            messageQueue.add(message)
        }
    }

    override fun pulse() {
        var message: GameMessage
        while (messageQueue.poll().also { message = it } != null) {
            if (disconnected) { // disconnected, we are just seeing extra messages now
                break
            }
            super.messageReceived(message)
        }

        if (disconnected) {
            gameServer.sessionInactivated(this)

            player?.remove()

            player = null
        }
    }

    fun setPlayer(profile: PlayerProfile) {
        val playerLocation = getInitialLocation()
        player = GamePlayer(this, profile, playerLocation)
        finalizeLogin(profile)

        if (!isActive()) {
            onDisconnect()
            return
        }

        player!!.join(this)
        player!!.world.server.rawPlayers.add(player!!)
        online = true
    }

    private fun getInitialLocation(): Location {
        val world = server.worldEntries[0].world
        return Location(world, 1.0, 1.0, 1.0, 0F, 0F)
    }

    override fun finalizeLogin(profile: PlayerProfile) {
        send(LoginSuccessMessage(profile.uuid, profile.name))
        this.protocol = ProtocolProvider.PLAY
    }

    override fun getProcessor() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun send(message: GameMessage) {
        sendWithFuture(message)
    }

    override fun sendWithFuture(message: GameMessage): ChannelFuture? {
        if (!this.channel.isActive) {
            throw Exception("Trying to send when session is inactive")
        } else {
            return this.channel.writeAndFlush(message).addListener {
                if (it.cause() != null) {
                    it.cause().printStackTrace()
                    this.onOutboundThrowable(it.cause())
                }
            }
        }
    }

    override fun sendAll(messages: List<GameMessage>) {
        messages.forEach {
            send(it)
        }
    }

    override fun disconnect(reason: String) {
        System.err.println("${player!!.name} kicked $reason")
        channel.close()
    }

    private fun onOutboundThrowable(cause: Throwable) {
        if (cause is CodecException) {
            System.err.println("Error in network output")
        } else {
            disconnect("write error: ${cause.message}")
        }
    }


    override fun onReady() {
        println("Session is ready")
    }

    override fun onDisconnect() {
        disconnected = true
    }


}