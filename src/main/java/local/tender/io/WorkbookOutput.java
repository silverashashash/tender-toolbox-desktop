package local.tender.io;
import local.tender.core.*;
import java.util.*;
public final class WorkbookOutput {
 private WorkbookOutput(){}
 public static Outcome<ToolResult> publish(ToolRequest request,TaskContext context,String filename,String sheet,List<String> headers,List<List<String>> rows,List<Problem> warnings)throws Exception {
  return ArtifactOutput.publish(request,context,filename,warnings,path->WorkbookTable.write(path,sheet,headers,rows));
 }
}
