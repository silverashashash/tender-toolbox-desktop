package local.tender.tools.contactwarning;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;import java.nio.file.*;import java.util.*;
import local.tender.core.*;import static org.junit.jupiter.api.Assertions.*;
class ContactWarningToolTest {
 @TempDir Path root;
 Path workbook(String filename,String[][] data)throws Exception{
  Path file=root.resolve(filename);try(var w=new XSSFWorkbook()){var s=w.createSheet("Sheet1");for(int i=0;i<data.length;i++){var r=s.createRow(i);for(int j=0;j<data[i].length;j++)r.createCell(j).setCellValue(data[i][j]);}try(var out=Files.newOutputStream(file)){w.write(out);}}return file;
 }
 @Test void exportsIndependentFaxWarningFromRealFiles()throws Exception{
  Path bids=workbook("开标.xlsx",new String[][]{{"分标名称","包名称","投标人名称"},{"分标一","包01","甲"},{"分标一","包01","乙"}});
  Path contacts=workbook("联系人.xlsx",new String[][]{{"投标人名称","投标人联系人姓名","手机号码","邮政地址","固定电话","电子邮件","传真号码"},{"甲","张一","111","甲市","001","a@example.test","0123"},{"乙","张二","222","乙市","002","b@example.test","0123"}});
  Path temp=Files.createDirectory(root.resolve("临时"));
  var req=new ToolRequest(ToolId.CONTACT_WARNING,Map.of("bidList",List.of(bids),"contacts",List.of(contacts)),Map.of(),root,Optional.empty());
  var result=new ContactWarningTool().execute(req,new TaskContext(temp));assertInstanceOf(Outcome.Success.class,result);
  var output=((Outcome.Success<ToolResult>)result).value().outputs().get(0);
  try(var in=Files.newInputStream(output);var w=new XSSFWorkbook(in)){
   var s=w.getSheetAt(0);assertEquals(1,s.getLastRowNum());assertEquals("包01",s.getRow(1).getCell(1).getStringCellValue());assertEquals("传真号码相同",s.getRow(1).getCell(4).getStringCellValue());assertEquals("0123",s.getRow(1).getCell(5).getStringCellValue());
  }
 }
 @Test void missingHeadersFailWithoutOutput()throws Exception{
  Path f=workbook("错误.xlsx",new String[][]{{"无关列"},{"数据"}});Path out=Files.createDirectory(root.resolve("output"));
  var req=new ToolRequest(ToolId.CONTACT_WARNING,Map.of("bidList",List.of(f),"contacts",List.of(f)),Map.of(),out,Optional.empty());
  assertInstanceOf(Outcome.Failure.class,new ContactWarningTool().execute(req,new TaskContext(root.resolve("temp"))));
  try(var list=Files.list(out)){assertEquals(0,list.count());}
 }
}
