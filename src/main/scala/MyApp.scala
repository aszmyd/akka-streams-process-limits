
import akka.actor.ActorSystem
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl._
import akka.stream.ClosedShape
import akka.util.ByteString

import java.io.{BufferedInputStream, BufferedOutputStream}
import java.nio.file.Paths
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

object MyApp extends App {

  // When this is changed to something above 15, the graph just stops
  val PROCESSES_COUNT = Integer.parseInt(args(0))

  println(s"Running with ${PROCESSES_COUNT} processes...")

  implicit val system                          = ActorSystem("MyApp")
  implicit val globalContext: ExecutionContext = ExecutionContext.global

  def executeCmdOnStream(cmd: String): Flow[ByteString, ByteString, _] = {
    val convertProcess = new ProcessBuilder(cmd).start
    val pipeIn         = new BufferedOutputStream(convertProcess.getOutputStream)
    val pipeOut        = new BufferedInputStream(convertProcess.getInputStream)
    Flow
      .fromSinkAndSource(StreamConverters.fromOutputStream(() ⇒ pipeIn), StreamConverters.fromInputStream(() ⇒ pipeOut))
  }

  val source = Source(1 to 100)
    .map(element => {
      println(s"--emit: ${element}")
      ByteString(element)
    })

  val sinksList = (1 to PROCESSES_COUNT).map(i => {
    Flow[ByteString]
      .via(executeCmdOnStream("cat"))
      .toMat(FileIO.toPath(Paths.get(s"process-$i.txt")))(Keep.right)
  })

  val graph = GraphDSL.create(sinksList) { implicit builder => sinks =>

    val broadcast = builder.add(Broadcast[ByteString](sinks.size))
    source ~> broadcast.in
    for (i <- broadcast.outlets.indices) {
      broadcast.out(i) ~> sinks(i)
    }
    ClosedShape
  }

  Await.result(Future.sequence(RunnableGraph.fromGraph(graph).run()), Duration.Inf)

}
