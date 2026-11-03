package local.tender.tools.rejection;
import local.tender.core.*;
import local.tender.io.WorkbookTable;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RejectionToolTest {
 @TempDir Path root;
 Outcome<ToolResult> run(String reason)throws Exception{
  Path a=root.resolve("否决.xlsx"),b=root.resolve("顺序.xlsx");
  WorkbookTable.write(a,"否决",List.of("分标名称","投标人","否决模板"),List.of(List.of("乙标","甲公司",reason),List.of("乙标","甲公司",reason),List.of("乙标","乙公司",reason),List.of("甲标","丙公司","资料缺失")));
  WorkbookTable.write(b,"顺序",List.of("序号","分标名称"),List.of(List.of("1","甲标"),List.of("2","乙标")));
  return new RejectionTool().execute(new ToolRequest(ToolId.REJECTION_NOTICE,Map.of("excluded",List.of(a),"sectionOrder",List.of(b)),Map.of(),root.resolve("结果"),Optional.empty()),new TaskContext(root));
 }
 @Test void ordersSectionsCountsDistinctSuppliersAndCountsEveryDocument()throws Exception{
  var result=run("未提交必要证明文件");assertInstanceOf(Outcome.Success.class,result);
  Path file=((Outcome.Success<ToolResult>)result).value().outputs().get(0);
  try(var in=Files.newInputStream(file);var doc=new XWPFDocument(in)){
   assertEquals(2,doc.getTables().size());var texts=doc.getParagraphs().stream().map(p->p.getText()).toList();assertTrue(texts.indexOf("1. 甲标")<texts.indexOf("2. 乙标"));
   var row=doc.getTables().get(1).getRow(1);assertEquals("2",row.getCell(2).getText());assertEquals("3",row.getCell(3).getText());
  }
 }
 @Test void emptyReasonFailsWithoutPublishing()throws Exception{
  assertInstanceOf(Outcome.Failure.class,run(""));assertFalse(Files.exists(root.resolve("结果/否决事项公示.docx")));
 }
}
