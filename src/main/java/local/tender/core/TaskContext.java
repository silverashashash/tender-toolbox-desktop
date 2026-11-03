package local.tender.core;
import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
public final class TaskContext {
 private final Path temp;private final AtomicBoolean cancelled=new AtomicBoolean();
 private final Consumer<Progress> observer;
 public record Progress(String stage,long completed,OptionalLong total){}
 public TaskContext(Path temp){this(temp,p->{});}
 public TaskContext(Path temp,Consumer<Progress> observer){this.temp=temp;this.observer=observer;}
 public Path temporaryDirectory(){return temp;}
 public void cancel(){cancelled.set(true);}
 public void checkCancelled(){if(cancelled.get()||Thread.currentThread().isInterrupted())throw new CancellationException("任务已取消");}
 public void progress(String stage,long completed,OptionalLong total){checkCancelled();observer.accept(new Progress(stage,completed,total));}
}
