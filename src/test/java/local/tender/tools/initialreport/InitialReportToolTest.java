package local.tender.tools.initialreport;
import local.tender.core.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class InitialReportToolTest {
 @TempDir Path root;
 @Test void usesAllThreeSheetsAndRetainsSupplierIdentity()throws Exception{
  Path input=root.resolve("初评.xlsx");try(var w=new XSSFWorkbook()){
   var reasons=w.createSheet("否决明细");var r=reasons.createRow(2);r.createCell(1).setCellValue("演示分标");r.createCell(2).setCellValue("证明材料缺失");r.createCell(3).setCellValue("完整示例集团有限公司");r.createCell(4).setCellValue("包01");
   for(int i=3;i<=4;i++){var extra=reasons.createRow(i);extra.createCell(1).setCellValue("演示分标");extra.createCell(2).setCellValue("证明材料缺失");extra.createCell(3).setCellValue("完整示例集团有限公司");extra.createCell(4).setCellValue(i==3?"包3":"包2,包1");}
   var base=w.createSheet("基本情况");base.createRow(1).createCell(0).setCellValue("一、基本情况");r=base.createRow(3);r.createCell(0).setCellValue("标001");r.createCell(1).setCellValue("演示分标");r.createCell(2).setCellValue(2);r.createCell(3).setCellValue(3);r.createCell(4).setCellValue(6);base.createRow(4);base.createRow(6).createCell(0).setCellValue("二、澄清事项");
   var names=w.createSheet("签名");names.createRow(1).createCell(0).setCellValue("这是技术组汇总");names.createRow(10).createCell(0).setCellValue("组长：张三、李四");try(var out=Files.newOutputStream(input)){w.write(out);}
  }
  var result=new InitialReportTool().execute(new ToolRequest(ToolId.INITIAL_REPORT,Map.of("report",List.of(input)),Map.of(),root.resolve("out"),Optional.empty()),new TaskContext(root));assertInstanceOf(Outcome.Success.class,result);
  try(var in=Files.newInputStream(((Outcome.Success<ToolResult>)result).value().outputs().get(0));var doc=new XWPFDocument(in)){
   String text=String.join("\n",doc.getParagraphs().stream().map(p->p.getText()).toList());assertTrue(text.contains("1个标段、2个包"));assertTrue(text.contains("3个投标人"));assertTrue(text.contains("6份投标文件"));assertTrue(text.contains("完整示例的包1至包3"));assertFalse(text.contains("完整示例集团有限公司"));assertTrue(text.contains("张三"));assertTrue(text.contains("技术组"));assertFalse(text.contains("null"));
  }
  var full=new InitialReportTool().execute(new ToolRequest(ToolId.INITIAL_REPORT,Map.of("report",List.of(input)),Map.of("simplifyNames","false"),root.resolve("full"),Optional.empty()),new TaskContext(root));
  var success=assertInstanceOf(Outcome.Success.class,full);
  try(var in=Files.newInputStream(((ToolResult)success.value()).outputs().get(0));var doc=new XWPFDocument(in)){assertTrue(doc.getParagraphs().stream().anyMatch(p->p.getText().contains("完整示例集团有限公司的包1至包3")));}
 }
 @Test void missingSheetsFailsClearly()throws Exception{
  Path file=root.resolve("缺表.xlsx");try(var w=new XSSFWorkbook()){w.createSheet("仅一张");try(var out=Files.newOutputStream(file)){w.write(out);}}
  var result=new InitialReportTool().execute(new ToolRequest(ToolId.INITIAL_REPORT,Map.of("report",List.of(file)),Map.of(),root.resolve("out"),Optional.empty()),new TaskContext(root));assertInstanceOf(Outcome.Failure.class,result);assertTrue(result.toString().contains("3"));
 }
}
