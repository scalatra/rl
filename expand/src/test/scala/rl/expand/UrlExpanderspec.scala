package rl
package expand

import org.jboss.netty.channel.{ChannelFutureListener, ChannelHandlerContext}
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpHeaders.Names
import org.jboss.netty.buffer.ChannelBuffers
import org.specs2.time.NoTimeConversions
import scala.concurrent.duration._
import scala.concurrent.Await

class UrlExpanderspec extends org.specs2.mutable.Specification with NoTimeConversions  {
//  def nettyContext: NettyHttpServerContext = new NettyHttpServerContext {
//    def handleRequest(ctx: ChannelHandlerContext, req: HttpRequest) {
//
//    }
//  }

//  override def intToRichLong(v: Int)   = super.intToRichLong(v)
//  override def longToRichLong(v: Long) = super.longToRichLong(v)

  sequential


  "A UrlExpander" should {
    "expand urls that redirect with a 302 status" in {
      var count = 0
      val server = new NettyHttpServerContext {
        def handleRequest(ctx: ChannelHandlerContext, req: HttpRequest) {
          if (count < 3) {
            count += 1
            val resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND)
            resp.setHeader(Names.CONTENT_TYPE, "text/plain")
            resp.setHeader(HttpHeaders.Names.LOCATION, "http://127.0.0.1:"+port+"/"+count)
            resp.setContent(ChannelBuffers.wrappedBuffer("".getBytes("UTF-8")))
            val future = ctx.getChannel.write(resp)
            future addListener ChannelFutureListener.CLOSE
          } else {
            writeResponse(ctx, "done")
          }
        }
      }
      server.start
      val expand = UrlExpander()
      try {
        Await.result(expand(Uri("http://127.0.0.1:"+server.port+"/")), 5 seconds) must_== "http://127.0.0.1:"+server.port+"/3"
        count must be_==(3)
      } finally {
        server.stop
        expand.stop()
      }
    }

    "expand urls that redirect with a 301 status" in {
      var count = 0
      val server = new NettyHttpServerContext {
        def handleRequest(ctx: ChannelHandlerContext, req: HttpRequest) {
          if (count < 3) {
            count += 1
            val resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.MOVED_PERMANENTLY)
            resp.setHeader(Names.CONTENT_TYPE, "text/plain")
            resp.setHeader(HttpHeaders.Names.LOCATION, "http://127.0.0.1:"+port+"/"+count)
            resp.setContent(ChannelBuffers.wrappedBuffer("".getBytes("UTF-8")))
            val future = ctx.getChannel.write(resp)
            future addListener ChannelFutureListener.CLOSE
          } else {
            writeResponse(ctx, "done")
          }
        }
      }
      server.start
      val expand = UrlExpander()
      try {
        Await.result(expand(Uri("http://127.0.0.1:"+server.port+"/")), 5 seconds) must_== "http://127.0.0.1:"+server.port+"/3"
        count must be_==(3)
      } finally {
        server.stop
        expand.stop()
      }
    }

    "throw an error when the max redirects are done" in {
      var count = 0
      val server = new NettyHttpServerContext {
        def handleRequest(ctx: ChannelHandlerContext, req: HttpRequest) {
          if (count < 3) {
            count += 1
            val resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.MOVED_PERMANENTLY)
            resp.setHeader(Names.CONTENT_TYPE, "text/plain")
            resp.setHeader(HttpHeaders.Names.LOCATION, "http://127.0.0.1:"+port+"/"+count)
            resp.setContent(ChannelBuffers.wrappedBuffer("".getBytes("UTF-8")))
            val future = ctx.getChannel.write(resp)
            future addListener ChannelFutureListener.CLOSE
          } else {
            writeResponse(ctx, "done")
          }
        }
      }
      server.start
      val expand = UrlExpander(ExpanderConfig(maximumResolveSteps = 1))
      try {
        Await.result(expand(Uri("http://127.0.0.1:"+server.port+"/")), 5 seconds) must throwA[UrlExpander.RedirectsExhausted]
        count must_== 2
      } finally {
        server.stop
        expand.stop()
      }
    }

    "not expand urls that return a 200" in {
      val server = new NettyHttpServerContext {
        def handleRequest(ctx: ChannelHandlerContext, req: HttpRequest) {
          writeResponse(ctx, "done")
        }
      }
      server.start
      val expand = UrlExpander()
      try {
        Await.result(expand(Uri("http://127.0.0.1:"+server.port+"/")), 5 seconds) must_== "http://127.0.0.1:"+server.port
      } finally {
        server.stop
        expand.stop()
      }
    }
//
//    "add the http scheme if no scheme provided" in {
//       val expand = UrlExpander()
//      try {
//        Await.result(expand(Uri("www.dressaday.com/2012/11/01/autumn-9929/")), 5 seconds) must_== Uri("http://www.dressaday.com/2012/11/01/autumn-9929/")
//      } finally {
//        expand.stop()
//      }
//    }
//
//    "expand urls that have invalid chars in them" in {
//      val expand = UrlExpander()
//      try {
//        Await.result(expand(Uri("http://bit.ly/ZvTH4o")), 5 seconds) must_== "http://theweek.com/article/index/242212%20/why-the-associated-press-is-dropping-il%20legal-immigrant-from-its-lexicon"
//      } finally {
//        expand.stop()
//      }
//    }
//
//    "not expand dressaday.com urls that return a 200" in {
//      val expand = UrlExpander()
//      try {
//        Await.result(expand(Uri("https://www.dressaday.com/2012/11/01/autumn-9929/")), 5 seconds) must_== "http://dressaday.com/2012/11/01/autumn-9929/"
//      } finally {
//        expand.stop()
//      }
//    }
  }
}