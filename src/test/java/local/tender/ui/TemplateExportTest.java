package local.tender.ui;
import local.tender.core.ToolId;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;import static org.junit.jupiter.api.Assertions.*;
class TemplateExportTest {
 @TempDir Path root;
 @Test void exportsIntoNewDirectoryAndKeepsExistingTemplates()throws Exception{
  Path folder=root.resolve("new-folder");var form=ToolForm.forTool(ToolId.LIMIT_PRICE);
  var first=TemplateExport.save(form,folder);var second=TemplateExport.save(form,folder);
  assertEquals(2,first.size());assertEquals(2,second.size());
  for(Path p:first)assertTrue(Files.size(p)>0);
  assertTrue(first.stream().noneMatch(second::contains));
 }
 @Test void priceTemplatesPrefillSelectedFormulaAndOnlyItsParameters()throws Exception{
  var methods=local.tender.tools.pricescore.PriceScoreTool.METHODS;
  var expected=java.util.List.of("分标名称,价格公式,qz,w1,w2,n1,n2,c","分标名称,价格公式,qz,n1,n2,c","分标名称,价格公式,qz,n1,n2,c,F1,F2","分标名称,价格公式,qz,n1,n2,c,b","分标名称,价格公式,qz,c,F1,F2,m","分标名称,价格公式,qz,n1,n2,c,a,b");
  for(int i=0;i<methods.size();i++){
   var paths=TemplateExport.save(ToolForm.forTool(ToolId.PRICE_SCORE),root.resolve("method"+i),methods.get(i));
   try(var in=Files.newInputStream(paths.get(1));var book=org.apache.poi.ss.usermodel.WorkbookFactory.create(in)){
    var sheet=book.getSheetAt(0);var headers=new java.util.ArrayList<String>();for(var cell:sheet.getRow(0))headers.add(cell.getStringCellValue());
    assertEquals(expected.get(i),String.join(",",headers));assertEquals(methods.get(i),sheet.getRow(1).getCell(1).getStringCellValue());
    assertEquals("",sheet.getRow(1).getCell(0).getStringCellValue());assertEquals("",sheet.getRow(1).getCell(2).getStringCellValue());
   }
  }
 }
 @Test void awardTemplatesFollowModeAndShareLimitedHeaders()throws Exception{
  for(String mode:local.tender.tools.award.AwardTool.MODES){
   var paths=TemplateExport.save(ToolForm.forTool(ToolId.AWARD),root.resolve(mode),"",mode);
   assertEquals(mode.equals("无限授")?1:2,paths.size());
   if(paths.size()==2)try(var in=Files.newInputStream(paths.get(1));var book=org.apache.poi.ss.usermodel.WorkbookFactory.create(in)){
    var headers=new java.util.ArrayList<String>();for(var cell:book.getSheetAt(0).getRow(0))headers.add(cell.getStringCellValue());assertEquals(local.tender.tools.award.AwardTool.GROUP_HEADERS,headers);
   }
  }
 }
 @Test void ipMacTemplateHasPortableNameAndCanBeReadBack()throws Exception{
  var paths=TemplateExport.save(ToolForm.forTool(ToolId.IP_MAC),root);
  assertEquals(1,paths.size());assertEquals(root.toAbsolutePath(),paths.get(0).getParent());
  try(var in=Files.newInputStream(paths.get(0));var book=org.apache.poi.ss.usermodel.WorkbookFactory.create(in)){
   assertEquals(20,book.getSheetAt(0).getRow(0).getLastCellNum());
  }
 }
}
