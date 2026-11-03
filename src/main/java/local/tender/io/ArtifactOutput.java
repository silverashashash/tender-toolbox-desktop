package local.tender.io;
import local.tender.core.*;
import java.nio.file.*;
import java.util.*;
public final class ArtifactOutput {
 @FunctionalInterface public interface Writer {void write(Path destination)throws Exception;}
 private ArtifactOutput(){}
 public static Outcome<ToolResult> publish(ToolRequest request,TaskContext context,String filename,List<Problem> warnings,Writer writer)throws Exception {
  context.checkCancelled();
  Path destination=request.outputDirectory().resolve(filename).toAbsolutePath().normalize();
  for(var paths:request.inputs().values())for(var input:paths){
   if(input.toAbsolutePath().normalize().equals(destination)||(Files.exists(destination)&&Files.isSameFile(input,destination)))return Outcome.fail("OUTPUT_IS_INPUT","输出路径不能覆盖输入文件");
  }
  Files.createDirectories(destination.getParent());
  Path stageDirectory=Files.createTempDirectory(destination.getParent(),".toolbox-");
  Path staged=stageDirectory.resolve(filename);
  try {
   writer.write(staged);context.checkCancelled();
   var published=AtomicOutput.publish(staged,destination,Boolean.parseBoolean(request.parameters().getOrDefault("overwrite","false")));
   if(published instanceof Outcome.Failure<Path> failure)return new Outcome.Failure<>(failure.problems());
   return new Outcome.Success<>(new ToolResult(List.of(destination),warnings),warnings);
  }finally{Files.deleteIfExists(staged);Files.deleteIfExists(stageDirectory);}
 }
}
