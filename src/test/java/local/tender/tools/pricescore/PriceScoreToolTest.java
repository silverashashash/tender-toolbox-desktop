package local.tender.tools.pricescore;
import local.tender.core.*;import local.tender.io.*;import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import java.nio.file.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class PriceScoreToolTest {
 @TempDir Path root;
 Outcome<ToolResult> run(String method,List<List<String>> rows,Map<String,String> params)throws Exception{
  Path f=root.resolve(UUID.randomUUID()+".xlsx"),r=root.resolve(UUID.randomUUID()+".xlsx");
  WorkbookTable.write(f,"报价",PriceScoreTool.HEADERS,rows);
  WorkbookTable.write(r,"参数",PriceScoreTool.RULE_HEADERS,List.of(List.of("标",method,"100","-0.15","0.1","1","0.5",method.equals("10013004")?"0.1":"0","0.8","1.2","-0.2",method.equals("10013004")?"-0.2":"0.2","0.1")));
  return new PriceScoreTool().execute(new ToolRequest(ToolId.PRICE_SCORE,Map.of("files",List.of(f),"rules",List.of(r)),params,root.resolve(UUID.randomUUID().toString()),Optional.empty()),new TaskContext(root));
 }
 List<String> row(String bidder,String round,String total,String discount){return List.of("标","包1",round,bidder,total,discount,"");}
 List<Map<String,String>> output(Outcome<ToolResult> result)throws Exception{var s=assertInstanceOf(Outcome.Success.class,result);return WorkbookTable.read(((ToolResult)s.value()).outputs().get(0),Map.of("分",List.of("价格分"),"基准",List.of("基准值"),"口径",List.of("报价口径"))).rows();}
 @Test void allSixFormulasProduceFullMarksAtIdenticalPositivePrices()throws Exception{for(int i=1;i<=6;i++)assertEquals("100",output(run("1001300"+i,List.of(row("甲","1","10",""),row("乙","1","10","")),Map.of())).get(0).get("分"));}
 @Test void secondFormulaAndDiscountUnitAreExplicit()throws Exception{var r=output(run("10013002",List.of(row("甲","1","","80%"),row("乙","1","","100%")),Map.of()));assertEquals("90",r.get(0).get("基准"));assertEquals("折扣率（%）",r.get(0).get("口径"));assertEquals("94.44",r.get(0).get("分"));assertEquals("88.89",r.get(1).get("分"));}
 @Test void ambiguousRoundsAndMixedUnitsFail()throws Exception{
  assertInstanceOf(Outcome.Failure.class,run("10013002",List.of(row("甲","1","10",""),row("甲","2","9","")),Map.of()));
  assertEquals(1,output(run("10013002",List.of(row("甲","1","10",""),row("甲","2","9","")),Map.of("round","2"))).size());
  assertInstanceOf(Outcome.Failure.class,run("10013002",List.of(row("甲","1","10",""),row("乙","1","","90%")),Map.of()));
 }
 @Test void zeroAndDuplicateSupplierFail()throws Exception{
  assertInstanceOf(Outcome.Failure.class,run("10013002",List.of(row("甲","1","0","")),Map.of()));
  assertInstanceOf(Outcome.Failure.class,run("10013002",List.of(row("甲","1","10",""),row("甲","1","10","")),Map.of()));
 }
 @Test void trimsExtremesAndRefusesAmbiguousMinimumFallback()throws Exception{
  var bids=new ArrayList<List<String>>();for(int i=0;i<6;i++)bids.add(row("人"+i,"1",i==0?"1":i==5?"100":"10",""));
  assertEquals("10",output(run("10013001",bids,Map.of())).get(0).get("基准"));
  assertInstanceOf(Outcome.Failure.class,run("10013004",List.of(row("甲","1","1",""),row("乙","1","1",""),row("丙","1","100","")),Map.of()));
 }
 @Test void excludedQuotesDoNotChangeMean()throws Exception{
  var excluded=new ArrayList<>(row("排除者","1","1000",""));excluded.set(6,"否");
  var rows=output(run("10013002",List.of(row("甲","1","10",""),excluded),Map.of()));assertEquals(1,rows.size());assertEquals("10",rows.get(0).get("基准"));
 }
 @Test void curvesAndLowPriceFilterHaveDistinctResults()throws Exception{
  var bids=List.of(row("甲","1","80",""),row("乙","1","100",""));
  var curve=output(run("10013003",bids,Map.of()));assertEquals("94.28",curve.get(0).get("分"));assertEquals("90",curve.get(1).get("分"));
  var filtered=output(run("10013005",bids,Map.of()));assertEquals("100",filtered.get(0).get("基准"));assertEquals("76",filtered.get(0).get("分"));
 }
}
