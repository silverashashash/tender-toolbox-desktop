package local.tender.core;
import java.nio.file.*;import java.io.IOException;import java.util.*;import java.util.concurrent.*;import java.util.function.Consumer;
public final class TaskRunner implements AutoCloseable {
 private final Path root;private final ExecutorService pool=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"toolbox-worker");t.setDaemon(true);return t;});
 private final Set<TaskHandle> active=ConcurrentHashMap.newKeySet();
 public TaskRunner(Path root){this.root=root;}
 public static final class TaskHandle {
  private final TaskContext context;private final CompletableFuture<Outcome<ToolResult>> result=new CompletableFuture<>();
  private TaskHandle(TaskContext context){this.context=context;}
  public void cancel(){context.cancel();}
  public CompletableFuture<Outcome<ToolResult>> result(){return result;}
 }
 public TaskHandle submit(ToolHandler handler,ToolRequest request){return submit(handler,request,p->{});}
 public TaskHandle submit(ToolHandler handler,ToolRequest request,Consumer<TaskContext.Progress> progress){
  final Path temp;
  try{Files.createDirectories(root);temp=Files.createTempDirectory(root,"task-");}catch(IOException e){throw new IllegalStateException("无法创建任务临时目录",e);}
  var context=new TaskContext(temp,progress);var handle=new TaskHandle(context);active.add(handle);
  try{pool.submit(()->{
   Outcome<ToolResult> result;
   try{context.checkCancelled();if(handler.id()!=request.toolId())result=Outcome.fail("TOOL_MISMATCH","工具与请求不匹配");else result=handler.execute(request,context);}
   catch(CancellationException|InterruptedException e){result=Outcome.fail("CANCELLED","任务已取消");}
   catch(Exception e){result=Outcome.fail("TASK_FAILED",e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
   try{cleanup(temp);}catch(IOException e){var warning=new Problem("CLEANUP_FAILED","临时目录需清理："+temp+"："+e.getMessage(),null);
    if(result instanceof Outcome.Success<ToolResult> success){var warnings=new ArrayList<>(success.warnings());warnings.add(warning);result=new Outcome.Success<>(success.value(),warnings);}
    else if(result instanceof Outcome.Failure<ToolResult> failure){var errors=new ArrayList<>(failure.problems());errors.add(warning);result=new Outcome.Failure<>(errors);}
   }
   active.remove(handle);handle.result.complete(result);
  });}catch(RejectedExecutionException e){active.remove(handle);try{cleanup(temp);}catch(IOException failure){e.addSuppressed(failure);}handle.result.complete(Outcome.fail("RUNNER_CLOSED","任务执行器已关闭"));}
  return handle;
 }
 private static void cleanup(Path root)throws IOException{try(var paths=Files.walk(root)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
 public void close(){active.forEach(TaskHandle::cancel);pool.shutdown();}
}
