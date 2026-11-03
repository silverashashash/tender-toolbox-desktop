package local.tender.tools.objective;
import local.tender.core.*;import local.tender.io.*;import org.apache.poi.xssf.usermodel.XSSFWorkbook;import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import java.nio.file.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class ObjectiveToolTest{
 @TempDir Path root;
 Path score(String name,String score)throws Exception{
  Path file=root.resolve("评分表_编号"+name+"_"+name+".xlsx");try(var w=new XSSFWorkbook()){
   var s=w.createSheet("包1");s.createRow(1).createCell(0).setCellValue("设备/材料名称: 分标甲 日期:2026-10-22");s.createRow(2).createCell(1).setCellValue("商务评分细则");s.createRow(3).createCell(3).setCellValue("供应商甲");var r=s.createRow(4);r.createCell(1).setCellValue("业绩数量");r.createCell(3).setCellValue(score);s.createRow(5).createCell(0).setCellValue("合计");s.createRow(8);try(var out=Files.newOutputStream(file)){w.write(out);}}return file;
 }
 List<Map<String,String>> run(String a,String b,String step)throws Exception{
  Path rules=root.resolve("rules.xlsx");WorkbookTable.write(rules,"规则",ObjectiveTool.RULE_HEADERS,List.of(List.of("业绩数量",step)));
  var result=new ObjectiveTool().execute(new ToolRequest(ToolId.EXPERT_OBJECTIVE,Map.of("files",List.of(score("甲",a),score("乙",b)),"rules",List.of(rules)),Map.of(),root.resolve("out"),Optional.empty()),new TaskContext(root));var success=assertInstanceOf(Outcome.Success.class,result);return WorkbookTable.read(((ToolResult)success.value()).outputs().get(0),Map.of("type",List.of("问题类型"),"detail",List.of("说明"))).rows();
 }
 @Test void decimalEquivalentScoresAreConsistent()throws Exception{assertTrue(run("2","2.00","0.5").isEmpty());}
 @Test void reportsExpertDifferencesAndDecimalStepViolation()throws Exception{var rows=run("2","2.3","0.5");assertEquals(Set.of("分数不一致","分值步长不符"),new HashSet<>(rows.stream().map(r->r.get("type")).toList()));assertTrue(rows.stream().anyMatch(r->r.get("detail").contains("2.3")));}
 @Test void missingExpertOnPaginatedSupplierIsExplicit()throws Exception{
  Path first=score("甲","2"),second=score("乙","2");
  try(var in=Files.newInputStream(first);var w=new XSSFWorkbook(in)){
   var s=w.getSheetAt(0);s.createRow(11).createCell(1).setCellValue("商务评分细则");s.createRow(12).createCell(3).setCellValue("供应商乙");var row=s.createRow(13);row.createCell(1).setCellValue("业绩数量");row.createCell(3).setCellValue(3);s.createRow(14).createCell(0).setCellValue("合计");s.createRow(17);
   try(var out=Files.newOutputStream(root.resolve("page.xlsx"))){w.write(out);}
  }Files.move(root.resolve("page.xlsx"),first,StandardCopyOption.REPLACE_EXISTING);
  Path rules=root.resolve("rules.xlsx");WorkbookTable.write(rules,"规则",ObjectiveTool.RULE_HEADERS,List.of(List.of("业绩数量","")));
  var result=new ObjectiveTool().execute(new ToolRequest(ToolId.EXPERT_OBJECTIVE,Map.of("files",List.of(first,second),"rules",List.of(rules)),Map.of(),root.resolve("out"),Optional.empty()),new TaskContext(root));var success=assertInstanceOf(Outcome.Success.class,result);
  var rows=WorkbookTable.read(((ToolResult)success.value()).outputs().get(0),Map.of("type",List.of("问题类型"),"supplier",List.of("投标人"))).rows();
  assertEquals(Set.of("专家记录缺失","专家数量不足"),new HashSet<>(rows.stream().map(r->r.get("type")).toList()));assertTrue(rows.stream().allMatch(r->r.get("supplier").equals("供应商乙")));
 }
 @Test void selectedCriterionMustMatchExactlyAndMissingRuleIsReported()throws Exception{
  Path rules=root.resolve("rules.xlsx");WorkbookTable.write(rules,"规则",ObjectiveTool.RULE_HEADERS,List.of(List.of("业绩","")));
  var result=new ObjectiveTool().execute(new ToolRequest(ToolId.EXPERT_OBJECTIVE,Map.of("files",List.of(score("甲","2"),score("乙","2")),"rules",List.of(rules)),Map.of(),root.resolve("out"),Optional.empty()),new TaskContext(root));var success=assertInstanceOf(Outcome.Success.class,result);assertFalse(success.warnings().isEmpty());
  var rows=WorkbookTable.read(((ToolResult)success.value()).outputs().get(0),Map.of("type",List.of("问题类型"))).rows();assertEquals(2,rows.size());assertTrue(rows.stream().allMatch(r->r.get("type").equals("细则未找到")));
 }

 @Test void readsRealMetadataNegativeDeductionsAndPageNumberFooter()throws Exception{
  Path first=score("甲","-2"),second=score("乙","-2.00");
  for(Path file:List.of(first,second))try(var in=Files.newInputStream(file);var w=new XSSFWorkbook(in)){
   var sheet=w.getSheetAt(0);sheet.getRow(1).getCell(0).setCellValue("分标编号: 001  设备/材料名称: 数字化服务-信通公司  设备/材料名称: 数字化服务-信通公司  分包编号: AH100  \n项目单位: 单位  工程名称: 项目  日期: 2025-12-05  \n包号: 包1");
   sheet.createRow(9).createCell(0).setCellValue("第1页/共2页");
   sheet.createRow(10).createCell(0).setCellValue("商务评审打分表");sheet.createRow(12).createCell(1).setCellValue("商务评分细则");sheet.createRow(13).createCell(3).setCellValue("供应商乙");var r=sheet.createRow(14);r.createCell(1).setCellValue("业绩数量");r.createCell(3).setCellValue(file.equals(first)?-1:-2);sheet.createRow(15).createCell(0).setCellValue("合计");sheet.createRow(19).createCell(0).setCellValue("第2页/共2页");
   Path temp=root.resolve("temp.xlsx");try(var out=Files.newOutputStream(temp)){w.write(out);}Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
  }
  Path rules=root.resolve("rules.xlsx");WorkbookTable.write(rules,"规则",ObjectiveTool.RULE_HEADERS,List.of(List.of("业绩数量","1")));
  var result=new ObjectiveTool().execute(new ToolRequest(ToolId.EXPERT_OBJECTIVE,Map.of("files",List.of(first,second),"rules",List.of(rules)),Map.of(),root.resolve("out"),Optional.empty()),new TaskContext(root));
  var success=assertInstanceOf(Outcome.Success.class,result);var rows=WorkbookTable.read(((ToolResult)success.value()).outputs().get(0),Map.of("section",List.of("分标名称"),"type",List.of("问题类型"),"supplier",List.of("投标人"))).rows();
  assertEquals(1,rows.size());assertEquals("数字化服务-信通公司",rows.get(0).get("section"));assertEquals("供应商乙",rows.get(0).get("supplier"));assertEquals("分数不一致",rows.get(0).get("type"));
 }
 @Test void discoversExactCriteriaFromSelectedExpertWorkbooksWithoutDuplicates()throws Exception{
  assertEquals(List.of(new ObjectiveTool.Criterion("业绩数量",List.of("未填写项目名称"))),ObjectiveTool.discoverMethods(List.of(score("甲","2"),score("乙","3"))).get(0).criteria());
 }
 @Test void resolvesMergedProjectCellsWithoutFillingUnmergedBlanks()throws Exception{
  Path file=score("甲","2");
  try(var in=Files.newInputStream(file);var w=new XSSFWorkbook(in)){
   var sheet=w.getSheetAt(0);sheet.getRow(4).createCell(0).setCellValue("绿色低碳评价（12分）");
   sheet.createRow(5).createCell(1).setCellValue("绿色认证");sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(4,5,0,0));
   sheet.createRow(6).createCell(1).setCellValue("未归类项");sheet.createRow(7).createCell(0).setCellValue("合计");
   Path temp=root.resolve("merged.xlsx");try(var out=Files.newOutputStream(temp)){w.write(out);}Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
  }
  var items=ObjectiveTool.discoverMethods(List.of(file)).get(0).criteria();assertEquals(3,items.size());
  assertEquals(List.of("绿色低碳评价（12分）"),items.get(0).projects());assertEquals(items.get(0).projects(),items.get(1).projects());
  assertEquals(List.of("未填写项目名称"),items.get(2).projects());
 }
 @Test void separatesMethodsAndAppliesSelectionsOnlyToMatchingPackages()throws Exception{
  Path a=score("甲","2"),b=score("乙","3");
  for(Path file:List.of(a,b))try(var in=Files.newInputStream(file);var w=new XSSFWorkbook(in)){
   var second=w.cloneSheet(0);w.setSheetName(1,"包2");second.getRow(4).createCell(0).setCellValue("另一评分项目");
   Path temp=root.resolve("tmp.xlsx");try(var out=Files.newOutputStream(temp)){w.write(out);}Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
  }
  var methods=ObjectiveTool.discoverMethods(List.of(a,b));assertEquals(2,methods.size());assertNotEquals(methods.get(0).id(),methods.get(1).id());
  assertTrue(methods.get(0).packages().stream().allMatch(x->x.endsWith(" / 包1")));
  Path rules=root.resolve("rules.xlsx");WorkbookTable.write(rules,"规则",ObjectiveTool.SCOPED_RULE_HEADERS,List.of(List.of("业绩数量","",methods.get(0).id())));
  var result=new ObjectiveTool().execute(new ToolRequest(ToolId.EXPERT_OBJECTIVE,Map.of("files",List.of(a,b),"rules",List.of(rules)),Map.of(),root.resolve("out"),Optional.empty()),new TaskContext(root));
  var success=assertInstanceOf(Outcome.Success.class,result);assertTrue(success.warnings().isEmpty());
  var rows=WorkbookTable.read(((ToolResult)success.value()).outputs().get(0),Map.of("pack",List.of("包名称"),"type",List.of("问题类型"))).rows();
  assertEquals(1,rows.size());assertEquals("包1",rows.get(0).get("pack"));assertEquals("分数不一致",rows.get(0).get("type"));
 }
}
