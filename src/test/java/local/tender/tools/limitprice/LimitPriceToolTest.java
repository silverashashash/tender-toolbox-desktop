package local.tender.tools.limitprice;
import local.tender.core.*;
import local.tender.io.WorkbookTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LimitPriceToolTest {
 @TempDir Path root;
 Outcome<ToolResult> run(List<List<String>> quotes)throws Exception{
  Path q=root.resolve("报价.xlsx"),l=root.resolve("限价.xlsx");
  WorkbookTable.write(q,"分标甲",List.of("轮次","包名","投标人","采购申请号","含税单价（万元）","物资描述","增值税率（%）"),quotes);
  WorkbookTable.write(l,"限价",List.of("分标名称","采购申请标识","单价限价（元）"),List.of(List.of("分标甲","0001","10000")));
  return new LimitPriceTool().execute(new ToolRequest(ToolId.LIMIT_PRICE,Map.of("quotes",List.of(q),"limits",List.of(l)),Map.of(),root.resolve("结果"),Optional.empty()),new TaskContext(root));
 }
 @Test void allFiveCategoriesAndCurrencyConversion()throws Exception{
  var result=run(List.of(List.of("第一轮","包01","甲","0001","1","材料","13%"),List.of("第一轮","包01","乙","0001","1","材料","13"),List.of("第二轮","包01","甲","0001","1.2","材料","9")));
  assertInstanceOf(Outcome.Success.class,result);
  var file=((Outcome.Success<ToolResult>)result).value().outputs().get(0);var rows=WorkbookTable.read(file,Map.of("type",List.of("类别"),"id",List.of("采购申请号"))).rows();
  assertEquals(Set.of("超过限价","等于限价","税率异常","相同报价","二轮报价上涨"),new HashSet<>(rows.stream().map(r->r.get("type")).toList()));
  assertTrue(rows.stream().allMatch(r->r.get("id").equals("0001")));
  assertEquals(1,rows.stream().filter(r->r.get("type").equals("税率异常")).count());
 }
 @Test void missingLimitIsReportedAndCannotSilentlyPass()throws Exception{
  var result=run(List.of(List.of("第一轮","包01","甲","unknown","1","材料","13")));
  assertInstanceOf(Outcome.Success.class,result);assertFalse(((Outcome.Success<ToolResult>)result).warnings().isEmpty());
 }
 @Test void placeholderPriceIsIncompleteNotZeroAndTaxStillChecked()throws Exception{
  var result=run(List.of(List.of("第一轮","包01","甲","0001","/","材料","9")));
  assertInstanceOf(Outcome.Success.class,result);
  var success=(Outcome.Success<ToolResult>)result;assertFalse(success.warnings().isEmpty());
  var rows=WorkbookTable.read(success.value().outputs().get(0),Map.of("type",List.of("类别"),"price",List.of("含税单价（万元）"))).rows();
  assertEquals(Set.of("报价缺失","税率异常"),new HashSet<>(rows.stream().map(r->r.get("type")).toList()));
  assertTrue(rows.stream().allMatch(r->r.get("price").equals("/")));
 }
 @Test void invalidNumberIncludesSheetRowAndColumn()throws Exception{
  var result=run(List.of(List.of("第一轮","包01","甲","0001","abc","材料","13")));
  var failure=assertInstanceOf(Outcome.Failure.class,result);
  assertTrue(failure.toString().contains("分标甲 第2行 含税单价"));
 }
 @Test void missingPricesDoNotProduceSamePriceOrRoundIncrease()throws Exception{
  var result=run(List.of(List.of("第一轮","包01","甲","0001","/","材料","13"),List.of("第一轮","包01","乙","0001","/","材料","13"),List.of("第二轮","包01","甲","0001","1","材料","13")));
  var success=assertInstanceOf(Outcome.Success.class,result);
  var rows=WorkbookTable.read(((ToolResult)success.value()).outputs().get(0),Map.of("type",List.of("类别"))).rows();
  assertFalse(rows.stream().anyMatch(r->Set.of("相同报价","二轮报价上涨").contains(r.get("type"))));
 }
 @Test void totalFallbackMatchesConfirmedFiveDigitSuffixAndPreservesSource()throws Exception{
  Path q=root.resolve("total.xlsx"),l=root.resolve("limits.xlsx");
  WorkbookTable.write(q,"分标甲",LimitPriceTool.QUOTE_HEADERS,List.of(
   List.of("第一轮","包01","甲","0001","/","材料","13","1.2"),
   List.of("第一轮","包01","乙","000100010","/","材料","13","1.2"),
   List.of("第一轮","包01","丙","0001","0.5","材料","13","1.2")));
  WorkbookTable.write(l,"限价",List.of("分标名称","采购申请标识","总价限价（元）"),List.of(List.of("分标甲","0001","10000")));
  var result=new LimitPriceTool().execute(new ToolRequest(ToolId.LIMIT_PRICE,Map.of("quotes",List.of(q),"limits",List.of(l)),Map.of("limitBasis","总价"),root.resolve("结果"),Optional.empty()),new TaskContext(root));
  var success=assertInstanceOf(Outcome.Success.class,result);
  var rows=WorkbookTable.read(((ToolResult)success.value()).outputs().get(0),Map.of("type",List.of("类别"),"supplier",List.of("投标人"),"basis",List.of("采用口径"),"total",List.of("含税总价（万元）"),"id",List.of("采购申请号"),"matched",List.of("限价匹配标识"))).rows();
  assertTrue(rows.stream().anyMatch(r->r.get("supplier").equals("甲")&&r.get("type").equals("超过限价")&&r.get("basis").equals("总价")&&r.get("total").equals("1.2")));
  assertTrue(rows.stream().anyMatch(r->r.get("supplier").equals("乙")&&r.get("type").equals("超过限价")&&r.get("id").equals("000100010")&&r.get("matched").equals("0001")));
  assertTrue(rows.stream().anyMatch(r->r.get("supplier").equals("丙")&&r.get("type").equals("口径不一致")));
 }
 @Test void totalOnlyWorkbookWithoutUnitColumnIsSupported()throws Exception{
  Path q=root.resolve("total.xlsx"),l=root.resolve("limits.xlsx");
  WorkbookTable.write(q,"分标甲",List.of("轮次","包名","投标人","采购申请号","含税总价（万元）","物资描述","增值税率（%）"),List.of(List.of("第一轮","包01","甲","0001","1.2","材料","13")));
  WorkbookTable.write(l,"限价",LimitPriceTool.LIMIT_HEADERS,List.of(List.of("分标甲","0001","10000")));
  var result=new LimitPriceTool().execute(new ToolRequest(ToolId.LIMIT_PRICE,Map.of("quotes",List.of(q),"limits",List.of(l)),Map.of("limitBasis","总价"),root.resolve("结果"),Optional.empty()),new TaskContext(root));assertInstanceOf(Outcome.Success.class,result);
 }
 @Test void serviceDiscountAndTotalAreSeparateAndUnitIsIgnored()throws Exception{
  Path q=root.resolve("service.xlsx"),l=root.resolve("limits.xlsx");
  WorkbookTable.write(q,"分标甲",List.of("轮次","包名","投标人","采购申请号","含税单价（万元）","物资描述","增值税率（%）","含税总价（万元）","折扣率（%）"),List.of(
   List.of("第一轮","包01","甲","0001","99","材料","13","/","88"),
   List.of("第二轮","包01","甲","0001","/","材料","13","/","101"),
   List.of("第一轮","包01","乙","0001","/","材料","13","/","88%"),
   List.of("第一轮","包02","丙","0002","99","材料","13","1.2","/")));
  WorkbookTable.write(l,"限价",LimitPriceTool.SERVICE_LIMIT_HEADERS,List.of(List.of("分标甲","0001","1","折扣率"),List.of("分标甲","0002","10000","总价")));
  var result=new LimitPriceTool().execute(new ToolRequest(ToolId.LIMIT_PRICE,Map.of("quotes",List.of(q),"limits",List.of(l)),Map.of("projectType","服务","discountLimit","比例"),root.resolve("结果"),Optional.empty()),new TaskContext(root));
  var success=assertInstanceOf(Outcome.Success.class,result);assertTrue(success.warnings().isEmpty());
  var rows=WorkbookTable.read(((ToolResult)success.value()).outputs().get(0),Map.of("type",List.of("类别"),"basis",List.of("采用口径"),"discount",List.of("折扣率（%）"),"limit",List.of("折扣率上限（%）"),"note",List.of("说明"))).rows();
  assertEquals(2,rows.stream().filter(r->r.get("type").equals("超过限价")).count());
  assertTrue(rows.stream().anyMatch(r->r.get("discount").equals("101")&&r.get("limit").equals("100")));
  assertTrue(rows.stream().anyMatch(r->r.get("type").equals("相同报价")&&r.get("basis").equals("折扣率")));
  assertTrue(rows.stream().anyMatch(r->r.get("type").equals("二轮报价上涨")&&r.get("note").contains("%")&&!r.get("note").contains("万元")));
 }
 @Test void serviceLimitTypeIsReadFromColumnNotInferredFromOne()throws Exception{
  Path q=root.resolve("q.xlsx"),l=root.resolve("l.xlsx");
  WorkbookTable.write(q,"分标",LimitPriceTool.QUOTE_HEADERS,List.of(
   List.of("第一轮","包1","甲","A","/","项目","13","0.0002","/"),
   List.of("第一轮","包1","乙","B","/","项目","13","/","88"),
   List.of("第一轮","包1","丙","C","/","项目","13","/","88")));
  WorkbookTable.write(l,"限价",LimitPriceTool.SERVICE_LIMIT_HEADERS,List.of(List.of("分标","A","1","总价"),List.of("分标","B","1","总价"),List.of("分标","C","1","待确认"),List.of("分标","unused","1","待确认")));
  var result=new LimitPriceTool().execute(new ToolRequest(ToolId.LIMIT_PRICE,Map.of("quotes",List.of(q),"limits",List.of(l)),Map.of("projectType","服务"),root.resolve("结果"),Optional.empty()),new TaskContext(root));
  var success=assertInstanceOf(Outcome.Success.class,result);
  var rows=WorkbookTable.read(((ToolResult)success.value()).outputs().get(0),Map.of("type",List.of("类别"),"supplier",List.of("投标人"))).rows();
  assertTrue(rows.stream().anyMatch(r->r.get("supplier").equals("甲")&&r.get("type").equals("超过限价")));
  assertTrue(rows.stream().anyMatch(r->r.get("supplier").equals("乙")&&r.get("type").equals("口径不一致")));
  assertTrue(rows.stream().anyMatch(r->r.get("supplier").equals("丙")&&r.get("type").equals("报价方式缺失")));
 }
 @Test void sectionScopedLimitsAndConfigurableTaxRules()throws Exception{
  Path q=root.resolve("q.xlsx"),l=root.resolve("l.xlsx");
  WorkbookTable.write(q,"分标甲",LimitPriceTool.QUOTE_HEADERS,List.of(
   List.of("第一轮","包1","甲","A00010","/","项目","0","0.5","/"),
   List.of("第一轮","包1","乙","A00010","/","项目","1.0","0.6","/"),
   List.of("第一轮","包1","丙","A00010","/","项目","6","0.7","/"),
   List.of("第一轮","包1","丁","A00010","/","项目","13","0.8","/")));
  WorkbookTable.write(l,"限价",LimitPriceTool.SERVICE_LIMIT_HEADERS,List.of(List.of("分标乙","A","1","总价"),List.of("分标甲","A","10000","总价")));
  var inputs=Map.of("quotes",List.of(q),"limits",List.of(l));var tool=new LimitPriceTool();
  var result=tool.execute(new ToolRequest(ToolId.LIMIT_PRICE,inputs,Map.of("projectType","服务"),root.resolve("自动"),Optional.empty()),new TaskContext(root));
  var success=assertInstanceOf(Outcome.Success.class,result);
  var rows=WorkbookTable.read(((ToolResult)success.value()).outputs().get(0),Map.of("type",List.of("类别"),"supplier",List.of("投标人"))).rows();
  assertEquals(2,rows.size());assertEquals(2,rows.stream().filter(r->r.get("type").equals("税率证明待核实")).count());
  assertFalse(rows.stream().anyMatch(r->r.get("supplier").equals("丁")&&r.get("type").equals("税率异常")));
  var custom=tool.execute(new ToolRequest(ToolId.LIMIT_PRICE,inputs,Map.of("projectType","服务","allowedTaxes","0，1.0,6%,13","proofTaxes","无"),root.resolve("自选"),Optional.empty()),new TaskContext(root));
  var cs=assertInstanceOf(Outcome.Success.class,custom);
  assertEquals(0,WorkbookTable.read(((ToolResult)cs.value()).outputs().get(0),Map.of("type",List.of("类别"))).rows().size());
 }
 @Test void duplicateSameSupplierRoundAndItemFails()throws Exception{
  var row=List.of("第一轮","包01","甲","0001","1","材料","13");assertInstanceOf(Outcome.Failure.class,run(List.of(row,row)));
 }
}
