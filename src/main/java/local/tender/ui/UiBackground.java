package local.tender.ui;
import javax.swing.SwingWorker;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
final class UiBackground {
 private UiBackground() {}
 static <T> void run(Callable<T> work, Consumer<T> success, Consumer<Throwable> failure) {
  new SwingWorker<T,Void>() {
   protected T doInBackground() throws Exception { return work.call(); }
   protected void done() {
    try { success.accept(get()); }
    catch (ExecutionException e) { failure.accept(e.getCause()); }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); failure.accept(e); }
    catch (RuntimeException e) { failure.accept(e); }
   }
  }.execute();
 }
}
