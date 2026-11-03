package local.tender.io;
import local.tender.core.Outcome;
import java.nio.file.*;
import java.io.IOException;
public final class AtomicOutput {
 private AtomicOutput(){}
 public static Outcome<Path> publish(Path staged,Path destination,boolean overwrite){
  try {
   if(Files.isSymbolicLink(destination))return Outcome.fail("OUTPUT_LINK","输出位置不能是符号链接");
   if(staged.toAbsolutePath().normalize().equals(destination.toAbsolutePath().normalize()))return Outcome.fail("SAME_PATH","临时文件不能与结果文件相同");
   // No-replace move must not use ATOMIC_MOVE: provider may replace an existing target.
   if(overwrite) Files.move(staged,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
   else Files.move(staged,destination);
   return Outcome.ok(destination);
  }catch(FileAlreadyExistsException e){return Outcome.fail("OUTPUT_EXISTS","结果已存在，请选择新文件名或明确覆盖");}
  catch(IOException e){return Outcome.fail("OUTPUT_IO","保存结果失败："+e.getMessage());}
 }
}
