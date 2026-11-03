package local.tender.tools.economicrank;
import local.tender.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class EconomicRankToolTest {
 @TempDir Path root;
 Path input(String[][] scores) throws Exception {
  Path p=root.resolve("排序.xlsx");try(var w=new XSSFWorkbook()){
   var s=w.createSheet("包01");s.createRow(3).createCell(0).setCellValue("设备/材料名称: 测试分标 日期:2026-10-21");
   String[] headers={"包号/子包号","项目单位","项目名称","招标设备/材料（数量）","投标人","技术","商务","价格","总分","排序","基准价（万元/%）","投标总价（万元/%）"};
   var h=s.createRow(5);for(int c=0;c<headers.length;c++)h.createCell(c).setCellValue(headers[c]);
   for(int i=0;i<scores.length;i++){var r=s.createRow(i+6);r.createCell(0).setCellValue("包01");for(int c=0;c<scores[i].length;c++)r.createCell(c+4).setCellValue(scores[i][c]);}
   try(var out=Files.newOutputStream(p)){w.write(out);}
  }return p;
 }
 Outcome<ToolResult> run(Path input)throws Exception {
  return new EconomicRankTool().execute(new ToolRequest(ToolId.ECONOMIC_RANK,Map.of("files",List.of(input)),Map.of(),root.resolve("结果"),Optional.empty()),new TaskContext(root));
 }
 List<List<String>> rows(Outcome<ToolResult> result)throws Exception {
  assertInstanceOf(Outcome.Success.class,result);Path f=((Outcome.Success<ToolResult>)result).value().outputs().get(0);
  try(var in=Files.newInputStream(f);var w=new XSSFWorkbook(in)){var rows=new ArrayList<List<String>>();for(var row:w.getSheetAt(0)){var cells=new ArrayList<String>();for(var c:row)cells.add(c.getStringCellValue());rows.add(cells);}return rows;}
 }
 @Test void ranksByTotalThenTechnicalThenLowerBidThenBusiness()throws Exception {
  var result=rows(run(input(new String[][]{
   {"甲","30","20","40","90","1","100","110"},
   {"乙","30","20","40","90","2","100","100"},
   {"丙","31","19","40","90","3","100","200"},
   {"丁","1","1","1","91","4","100","300"}})));
  assertEquals(5,result.size());assertEquals("丁",result.get(1).get(2));assertEquals("丙",result.get(2).get(2));assertEquals("乙",result.get(3).get(2));assertEquals("甲",result.get(4).get(2));
  assertEquals("1",result.get(1).get(4));assertEquals("包01",result.get(1).get(1));
 }
 @Test void zeroScoreReportsWarningAndDoesNotIssueRanks()throws Exception {
  var result=rows(run(input(new String[][]{{"甲","0.00","20","40","60","2","100","110"},{"乙","30","20","40","90","1","100","100"}})));
  assertEquals(2,result.size());assertEquals("",result.get(1).get(4));assertTrue(result.get(1).get(5).contains("技术分为0"));
 }
 @Test void invalidNumberDoesNotBecomeZeroOrProduceAnOutput()throws Exception {
  assertInstanceOf(Outcome.Failure.class,run(input(new String[][]{{"甲","未评分","20","40","60","1","100","110"}})));
  assertFalse(Files.exists(root.resolve("结果/经济排序核验.xlsx")));
 }
 @Test void exactTiesPreserveSourceOrder()throws Exception {
  assertEquals(1,rows(run(input(new String[][]{{"甲","30","20","40","90","1","100","100"},{"乙","30","20","40","90","2","100","100"}}))).size());
 }
}
