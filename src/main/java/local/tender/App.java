package local.tender;
import javax.swing.*;
import java.nio.file.*;
import java.util.*;
import local.tender.core.*;
import local.tender.ui.*;
public final class App {
 public static void main(String[] args)throws Exception {
  if(args.length!=0){
   if(args.length!=2||!args[0].equals("--verify-demo"))throw new IllegalArgumentException("用法：--verify-demo 验证输出目录");
   var registry=ToolRegistry.standard();
   for(var entry:registry.catalog())if(registry.find(entry.id()) instanceof Outcome.Success<ToolHandler> found){
    var demo=ToolDemos.create(entry.id(),Path.of(args[1]));var request=new ToolRequest(entry.id(),demo.inputs(),demo.parameters(),demo.output(),Optional.empty());
    var result=found.value().execute(request,new TaskContext(demo.output()));if(result instanceof Outcome.Failure<?> failure)throw new IllegalStateException(failure.problems().toString());
    System.out.println(entry.name()+" 演示输出："+demo.output());
   }return;
  }
  SwingUtilities.invokeLater(()->{try{UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());}catch(Exception ignored){}new MainWindow(ToolRegistry.standard()).setVisible(true);});
 }
}
