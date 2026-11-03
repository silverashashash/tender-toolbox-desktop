package local.tender.tools.performance;
import local.tender.core.*;
import local.tender.io.WorkbookTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PerformanceToolTest {
 @TempDir Path root;
 static final List<String> BIDS=List.of("采购项目编号","分标名称","分标编号","分包名称","分包编号","项目单位","投标人名称","单位名称","项目归口管理部门");
 static final List<String> SCORES=List.of("单位名称","关联类型","所属部门","服务类型/物料名称","供应商名称","综合评价","备注");
 Outcome<ToolResult> run(List<List<String>> bids,List<List<String>> scores)throws Exception{
  Path a=root.resolve("开标.xlsx"),b=root.resolve("评价.xlsx");WorkbookTable.write(a,"开标",BIDS,bids);WorkbookTable.write(b,"评价",SCORES,scores);
  return new PerformanceTool().execute(new ToolRequest(ToolId.PERFORMANCE_SCORE,Map.of("fileA",List.of(a),"fileB",List.of(b)),Map.of(),root.resolve("结果"),Optional.empty()),new TaskContext(root));
 }
 List<String> bid(String supplier,String section){return List.of("001",section,"002","包01","003","项目单位",supplier,"单位甲","部门甲");}
 List<Map<String,String>> rows(Outcome<ToolResult> result)throws Exception{
  assertInstanceOf(Outcome.Success.class,result);var output=((Outcome.Success<ToolResult>)result).value().outputs().get(0);
  return WorkbookTable.read(output,Map.of("score",List.of("履约分结果"),"status",List.of("匹配说明"),"project",List.of("采购项目编号"))).rows();
 }
 @Test void scopesDepartmentSectionSupplierAndUnmatchedDefault()throws Exception{
  var result=run(List.of(bid("甲","标甲"),bid("甲","标乙"),bid("乙","标甲"),bid("丙","标甲")),List.of(
   List.of("单位甲","供应商+标段","","标甲","甲","A",""),List.of("单位甲","供应商+所属部门","部门甲","","乙","C","")));
  var rows=rows(result);assertEquals(List.of("5","4","3","4"),rows.stream().map(r->r.get("score")).toList());assertEquals("001",rows.get(0).get("project"));assertTrue(rows.get(1).get("status").contains("未匹配"));
 }
 @Test void numericBoundariesMatchRecoveredBehaviorWithWarningForThirty()throws Exception{
  var bids=new ArrayList<List<String>>();var scores=new ArrayList<List<String>>();String[] values={"90","80","60","30","29.9","80.1"};
  for(String v:values){bids.add(bid(v,"标甲"));scores.add(List.of("单位甲","供应商","","",v,v,""));}
  var r=rows(run(bids,scores));assertEquals(List.of("5","3","2","4","1","4"),r.stream().map(x->x.get("score")).toList());assertTrue(r.get(3).get("status").contains("30"));
 }
 @Test void uncoveredLaterEvaluationDoesNotEraseEarlierValidGrade()throws Exception{
  var result=rows(run(List.of(bid("甲","标甲")),List.of(List.of("单位甲","供应商","","","甲","A",""),List.of("单位甲","供应商","","","甲","30",""))));
  assertEquals("5",result.get(0).get("score"));assertTrue(result.get(0).get("status").contains("30"));
 }
 @Test void originalUnknownMarkersAssignFourIncludingZeroAndOverridePriorRating()throws Exception{
  var result=rows(run(List.of(bid("甲","标甲")),List.of(List.of("单位甲","供应商","","","甲","A",""),List.of("单位甲","供应商","","","甲","0",""))));
  assertEquals("4",result.get(0).get("score"));
 }
 @Test void unknownRelationFailsInsteadOfBroadeningMatch()throws Exception{
  assertInstanceOf(Outcome.Failure.class,run(List.of(bid("甲","标甲")),List.of(List.of("单位甲","未知关联","","","甲","A",""))));
  assertFalse(Files.exists(root.resolve("结果/履约分.xlsx")));
 }
}
