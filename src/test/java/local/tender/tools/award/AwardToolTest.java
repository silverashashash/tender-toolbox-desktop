package local.tender.tools.award;
import local.tender.core.*;import local.tender.io.*;import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import java.nio.file.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class AwardToolTest {
 @TempDir Path root;
 List<Map<String,String>> run(String mode,List<List<String>> rows,List<List<String>> limits)throws Exception{
  Path f=root.resolve(UUID.randomUUID()+".xlsx");WorkbookTable.write(f,"候选",AwardTool.INPUT_HEADERS,rows);
  var inputs=new HashMap<String,List<Path>>();inputs.put("files",List.of(f));
  if(limits!=null){Path g=root.resolve(UUID.randomUUID()+".xlsx");WorkbookTable.write(g,"限授",AwardTool.GROUP_HEADERS,limits);inputs.put("groups",List.of(g));}
  var out=new AwardTool().execute(new ToolRequest(ToolId.AWARD,inputs,Map.of("mode",mode),root.resolve(UUID.randomUUID().toString()),Optional.empty()),new TaskContext(root));
  var s=assertInstanceOf(Outcome.Success.class,out);return WorkbookTable.read(((ToolResult)s.value()).outputs().get(0),Map.of("pack",List.of("包名称"),"bidder",List.of("拟授标人"),"status",List.of("状态"),"round",List.of("轮次"))).rows();
 }
 List<String> c(String pack,String bidder,String rank,String price){return List.of("001","标",pack,bidder,rank,price);}
 List<String> g(String pack){return List.of("组1","001",pack,"1","1");}
 @Test void unlimitedUsesRankAndRetainsEmptyPack()throws Exception{var r=run("无限授",List.of(c("包1","乙","2","500"),c("包1","甲","1","100"),c("包2","","","")),null);assertEquals("甲",r.get(0).get("bidder"));assertEquals("无候选",r.get(1).get("status"));}
 @Test void singleRoundReselectsAndReportsExhaustion()throws Exception{var r=run("一轮一包",List.of(c("包1","甲","1","100"),c("包2","甲","1","200"),c("包3","乙","1","300")),List.of(g("包1"),g("包2"),g("包3")));assertEquals("限授后无可授候选",r.get(0).get("status"));assertEquals("甲",r.get(1).get("bidder"));assertEquals("1",r.get(2).get("round"));}
 @Test void multiRoundUsesCurrentCandidatePrice()throws Exception{var r=run("一轮多包",List.of(c("包1","甲","1","1000"),c("包2","甲","1","500"),c("包2","乙","2","20"),c("包3","甲","1","400"),c("包3","乙","2","30")),List.of(g("包1"),g("包2"),g("包3")));assertEquals("乙",r.get(2).get("bidder"));assertEquals("2",r.get(2).get("round"));assertEquals("限授后无可授候选",r.get(1).get("status"));}
 @Test void rejectsDuplicateRankAndOverlappingGroups()throws Exception{
  Path f=root.resolve("bad.xlsx");WorkbookTable.write(f,"候选",AwardTool.INPUT_HEADERS,List.of(c("包1","甲","1","10"),c("包1","乙","1","20")));
  assertInstanceOf(Outcome.Failure.class,new AwardTool().execute(new ToolRequest(ToolId.AWARD,Map.of("files",List.of(f)),Map.of(),root.resolve("badout"),Optional.empty()),new TaskContext(root)));
  f=root.resolve("valid.xlsx");WorkbookTable.write(f,"候选",AwardTool.INPUT_HEADERS,List.of(c("包1","甲","1","10")));
  Path g=root.resolve("groups.xlsx");WorkbookTable.write(g,"组",AwardTool.GROUP_HEADERS,List.of(g("包1"),g("包1")));
  assertInstanceOf(Outcome.Failure.class,new AwardTool().execute(new ToolRequest(ToolId.AWARD,Map.of("files",List.of(f),"groups",List.of(g)),Map.of("mode","一轮一包"),root.resolve("badout"),Optional.empty()),new TaskContext(root)));
 }
 @Test void readsOriginalRankingSheetAndFormulaPrice()throws Exception{
  Path f=root.resolve("original.xlsx");try(var wb=new org.apache.poi.xssf.usermodel.XSSFWorkbook()){
   var s=wb.createSheet("包1");s.createRow(3).createCell(0).setCellValue("分标编号: 001  价格单位: 万元  设备/材料名称: 标  日期: 2026-10-23");var h=s.createRow(5);h.createCell(4).setCellValue("投标人");h.createCell(9).setCellValue("排序");h.createCell(11).setCellValue("投标总价(万元)");var r=s.createRow(6);r.createCell(0).setCellValue("包1");r.createCell(4).setCellValue("甲");r.createCell(9).setCellValue(1);r.createCell(11).setCellFormula("10+20");try(var out=Files.newOutputStream(f)){wb.write(out);}
  }
  var result=new AwardTool().execute(new ToolRequest(ToolId.AWARD,Map.of("files",List.of(f)),Map.of(),root.resolve("originalout"),Optional.empty()),new TaskContext(root));assertInstanceOf(Outcome.Success.class,result);
 }
}
