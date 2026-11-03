package local.tender.ui;
import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
class UiBackgroundTest {
 @Test void blockedFileWorkDoesNotBlockEventThreadAndCompletionReturnsToEventThread()throws Exception{
  var started=new CountDownLatch(1);var release=new CountDownLatch(1);var done=new CountDownLatch(1);var heartbeat=new CountDownLatch(1);var callbackOnEdt=new AtomicBoolean();
  SwingUtilities.invokeAndWait(()->UiBackground.run(()->{started.countDown();release.await(5,TimeUnit.SECONDS);return "done";},result->{callbackOnEdt.set(SwingUtilities.isEventDispatchThread());done.countDown();},error->done.countDown()));
  assertTrue(started.await(3,TimeUnit.SECONDS));SwingUtilities.invokeLater(heartbeat::countDown);
  try{assertTrue(heartbeat.await(1,TimeUnit.SECONDS));}finally{release.countDown();}
  assertTrue(done.await(3,TimeUnit.SECONDS));assertTrue(callbackOnEdt.get());
 }
}
