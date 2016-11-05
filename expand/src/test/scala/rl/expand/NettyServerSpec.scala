package rl
package expand

import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import java.util.concurrent.Executors

import org.jboss.netty.channel.group.DefaultChannelGroup
import org.jboss.netty.handler.codec.http.HttpVersion._
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.util.CharsetUtil
import org.jboss.netty.handler.codec.http.HttpHeaders.Names
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.channel._
import java.net.{InetSocketAddress, ServerSocket}

import org.specs2.Specification
import org.specs2.specification.Step
import org.specs2.specification.core.Fragments

trait NettyHttpServerContext {

  val bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()))
  bootstrap setPipelineFactory channelFactory
  val allChannels = new DefaultChannelGroup()

  def channelFactory = new ChannelPipelineFactory {
    def getPipeline = {
      val pipe = Channels.pipeline()
      pipe.addLast("decoder", new HttpRequestDecoder)
      pipe.addLast("aggregator", new HttpChunkAggregator(8912))
      pipe.addLast("encoder", new HttpResponseEncoder)
      pipe.addLast("handler", httpMessageHandler)
      pipe
    }
  }

  def httpMessageHandler: ChannelUpstreamHandler = new SimpleChannelUpstreamHandler {

    override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
      cleanup()
    }

    override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
      allChannels add e.getChannel
    }

    override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
      val req = e.getMessage.asInstanceOf[HttpRequest]
      handleRequest(ctx, req)
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
      //      e.getCause.printStackTrace()
      try { e.getChannel.close() } catch { case _: Throwable ⇒ }
    }
  }

  def sendError(ctx: ChannelHandlerContext, status: HttpResponseStatus) {
    val response: HttpResponse = new DefaultHttpResponse(HTTP_1_1, status)
    response.setHeader(CONTENT_TYPE, "text/plain; charset=UTF-8")
    response.setContent(ChannelBuffers.copiedBuffer("Failure: "+status.toString+"\r\n", CharsetUtil.UTF_8))
    ctx.getChannel.write(response).addListener(ChannelFutureListener.CLOSE)
  }

  def writeResponse(ctx: ChannelHandlerContext, body: String, contentType: String = "text/html") {
    val resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    resp.setHeader(Names.CONTENT_TYPE, contentType)
    resp.setContent(ChannelBuffers.wrappedBuffer(body.getBytes("UTF-8")))
    val future = ctx.getChannel.write(resp)
    future addListener ChannelFutureListener.CLOSE
  }

  def handleRequest(ctx: ChannelHandlerContext, req: HttpRequest)
  def cleanup() {}

  def start = synchronized {
    bootstrap.bind(new InetSocketAddress(port))
  }

  val port = {
    val s = new ServerSocket(0)
    try { s.getLocalPort } finally { s.close() }
  }

  def stop = synchronized {
    allChannels.close().awaitUninterruptibly()
    val thread = new Thread {
      override def run = {
        if (bootstrap != null) {
          bootstrap.releaseExternalResources()
        }
      }
    }
    thread.setDaemon(false)
    thread.start()
    thread.join()
  }
}

trait NettyServerSpec extends Specification {

  def nettyContext: NettyHttpServerContext

  override def map(fs: ⇒ Fragments) = Step(nettyContext.start) ^ super.map(fs) ^ Step(nettyContext.stop)
}
