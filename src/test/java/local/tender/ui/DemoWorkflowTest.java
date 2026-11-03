package local.tender.ui;
import local.tender.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DemoWorkflowTest {
 @TempDir Path root;
 @Test void everyEnabledDesktopFormRunsItsSyntheticFilesWithExpectedContent()throws Exception{
  var registry=ToolRegistry.standard();
  for(ToolId id:List.of(ToolId.WARNING_FORM,ToolId.PRICE_SCORE,ToolId.AWARD,ToolId.SPLIT_PACK,ToolId.IP_MAC,ToolId.EXPERT_OBJECTIVE,ToolId.INITIAL_REPORT,ToolId.LIMIT_PRICE,ToolId.CONTACT_WARNING,ToolId.ECONOMIC_RANK,ToolId.PERFORMANCE_SCORE,ToolId.EXPERT_TOTAL,ToolId.REJECTION_NOTICE)){
   var demo=ToolDemos.create(id,root);var form=ToolForm.forTool(id);var state=new ToolForm.State(form,root);
   demo.inputs().forEach(state::select);demo.parameters().forEach(state::parameter);state.output(demo.output());
   var handler=((Outcome.Success<ToolHandler>)registry.find(id)).value();var result=handler.execute(state.request(false),new TaskContext(root));assertInstanceOf(Outcome.Success.class,result,id.toString());
   Path file=((Outcome.Success<ToolResult>)result).value().outputs().get(0);assertEquals(form.outputName(),file.getFileName().toString());
   try(var in=Files.newInputStream(file)){
    if(id==ToolId.WARNING_FORM){try(var zip=new java.util.zip.ZipFile(file.toFile())){assertEquals(2,zip.stream().filter(e->e.getName().endsWith(".docx")).count());}}
    else if(id==ToolId.INITIAL_REPORT){try(var doc=new XWPFDocument(in)){assertTrue(String.join("",doc.getParagraphs().stream().map(p->p.getText()).toList()).contains("1个标段、2个包"));}}
    else if(id==ToolId.REJECTION_NOTICE){try(var doc=new XWPFDocument(in)){assertEquals("2",doc.getTables().get(0).getRow(1).getCell(2).getText());assertEquals("3",doc.getTables().get(0).getRow(1).getCell(3).getText());}}
    else try(var w=new XSSFWorkbook(in)){var sheet=w.getSheetAt(0);switch(id){
     case PRICE_SCORE -> {assertEquals(2,sheet.getLastRowNum());assertEquals("94.44",sheet.getRow(1).getCell(8).getStringCellValue());}
     case AWARD -> {assertEquals(2,sheet.getLastRowNum());assertEquals("乙",sheet.getRow(1).getCell(4).getStringCellValue());assertEquals("甲",sheet.getRow(2).getCell(4).getStringCellValue());}
     case SPLIT_PACK -> {assertEquals(2,w.getSheetAt(1).getLastRowNum());assertEquals("5",w.getSheetAt(1).getRow(1).getCell(7).getStringCellValue());}
     case IP_MAC -> assertEquals(1,sheet.getLastRowNum());
     case EXPERT_OBJECTIVE -> assertEquals(2,sheet.getLastRowNum());
     case LIMIT_PRICE -> assertEquals(6,sheet.getLastRowNum());
     case CONTACT_WARNING -> {assertEquals(1,sheet.getLastRowNum());assertEquals("012345",sheet.getRow(1).getCell(5).getStringCellValue());}
     case ECONOMIC_RANK -> assertEquals(2,sheet.getLastRowNum());
     case PERFORMANCE_SCORE -> {assertEquals("5",sheet.getRow(1).getCell(10).getStringCellValue());assertEquals("4",sheet.getRow(2).getCell(10).getStringCellValue());}
     case EXPERT_TOTAL -> {assertEquals(1,sheet.getLastRowNum());assertTrue(sheet.getRow(1).getCell(4).getStringCellValue().contains("88"));}
     default -> fail();
    }}
   }
  }
 }
}
