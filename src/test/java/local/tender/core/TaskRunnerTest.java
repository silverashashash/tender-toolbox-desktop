package local.tender.core;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class TaskRunnerTest {
 @TempDir Path root;
 ToolRequest req(){return new ToolRequest(ToolId.CONTACT_WARNING,Map.of(),Map.of(),root,Optional.empty());}
 @Test void cancelWaitsForWorkerAndCleansOnlyItsFiles() throws Exception {
  Path marker=Files.writeString(root.resolve("unrelated"),"keep");var started=new CountDownLatch(1);
  try(var runner=new TaskRunner(root)){
   var handle=runner.submit(new ToolHandler(){public ToolId id(){return ToolId.CONTACT_WARNING;}public Outcome<ToolResult> execute(ToolRequest r,TaskContext c)throws Exception{
    Files.writeString(c.temporaryDirectory().resolve("partial"),"partial");started.countDown();
    for(;;){c.checkCancelled();Thread.sleep(10);}
   }},req());
   assertTrue(started.await(3,TimeUnit.SECONDS));handle.cancel();
   assertTrue(handle.result().get(3,TimeUnit.SECONDS) instanceof Outcome.Failure<?>);
   assertEquals("keep",Files.readString(marker));try(var s=Files.list(root)){assertEquals(1,s.count());}
  }
 }
 @Test void parallelTasksHaveDistinctTempDirectories()throws Exception{
  var seen=ConcurrentHashMap.<Path>newKeySet();var ready=new CountDownLatch(2);
  try(var runner=new TaskRunner(root)){
   ToolHandler handler=new ToolHandler(){public ToolId id(){return ToolId.CONTACT_WARNING;}public Outcome<ToolResult> execute(ToolRequest r,TaskContext c)throws Exception{
    seen.add(c.temporaryDirectory());ready.countDown();if(!ready.await(3,TimeUnit.SECONDS))throw new Exception("timeout");return Outcome.ok(new ToolResult(List.of(),List.of()));}};
   var a=runner.submit(handler,req());var b=runner.submit(handler,req());
   assertTrue(a.result().get(4,TimeUnit.SECONDS) instanceof Outcome.Success<?>);assertTrue(b.result().get(4,TimeUnit.SECONDS) instanceof Outcome.Success<?>);assertEquals(2,seen.size());
  }
 }
}
