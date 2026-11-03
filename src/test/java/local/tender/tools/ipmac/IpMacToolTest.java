package local.tender.tools.ipmac;
import local.tender.core.*;import local.tender.io.*;import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import java.nio.file.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class IpMacToolTest {
 @TempDir Path root;
 List<Map<String,String>> run(List<List<String>> rows)throws Exception{
  Path f=root.resolve(UUID.randomUUID()+".xlsx");WorkbookTable.write(f,"数据",List.of("分标名称","包名","轮次","投标人","技术文件IP","技术文件MAC","技术文件CPU","技术文件硬盘","商务文件MAC","商务文件CPU","商务文件硬盘"),rows);
  var outcome=new IpMacTool().execute(new ToolRequest(ToolId.IP_MAC,Map.of("files",List.of(f)),Map.of(),root.resolve(UUID.randomUUID().toString()),Optional.empty()),new TaskContext(root));
  var success=assertInstanceOf(Outcome.Success.class,outcome);
  return WorkbookTable.read(((ToolResult)success.value()).outputs().get(0),Map.of("status",List.of("核验结果"),"a",List.of("投标人甲"),"b",List.of("投标人乙"),"source",List.of("甲方文件及来源"))).rows();
 }
 List<String> row(String supplier,String mac,String cpu,String disk){return List.of("标","包1","1",supplier,"IP相同",mac,cpu,disk,"/","/","/");}
 @Test void requiresJointHardwareEqualityNotIndividualMatches()throws Exception{
  assertTrue(run(List.of(row("甲","M1","CPU","D1"),row("乙","M2","CPU","D2"),row("丙","M1","OTHER","D1"))).isEmpty());
  assertTrue(run(List.of(row("甲","M1","CPU","D1"),row("乙","M2","CPU","D1"))).isEmpty());
 }
 @Test void comparesAcrossCategoriesAndRoundsWithoutMixingHardwareFromDifferentCategories()throws Exception{
  var b=List.of("标","包1","2","乙","IP相同","OTHER","CPU","OTHER","M","CPU","D");
  var result=run(List.of(row("甲","M","CPU","D"),b));assertEquals(1,result.size());assertEquals("硬件组合一致",result.get(0).get("status"));
  assertTrue(run(List.of(row("甲","M","CPU","D"),List.of("标","包1","2","乙","IP相同","M","CPU","OTHER","OTHER","OTHER","D"))).isEmpty());
 }
 @Test void excludesSameSupplierAndOtherPackages()throws Exception{
  var other=new ArrayList<>(row("乙","M","C","D"));other.set(1,"包2");
  assertTrue(run(List.of(row("甲","M","C","D"),row("甲","M","C","D"),other)).isEmpty());
 }
 @Test void handlesMissingMacAndMissingRequiredHardwareExplicitly()throws Exception{
  assertEquals("硬件组合一致（双方无MAC）",run(List.of(row("甲","/","C","D"),row("乙","","C","D"))).get(0).get("status"));
  assertEquals("待人工核实（单方缺MAC）",run(List.of(row("甲","M","C","D"),row("乙","/","C","D"))).get(0).get("status"));
  assertEquals("资料不足，未完成核验",run(List.of(row("甲","M","C","/"))).get(0).get("status"));
 }
 @Test void readsOriginal23ColumnFormat()throws Exception{
  Path f=root.resolve("original.xlsx");var h=new ArrayList<>(List.of("序号","分标名称","分标编号","包名","轮次","投标人","下载IP"));for(int i=0;i<16;i++)h.add("原始字段"+i);
  var rows=new ArrayList<List<String>>();for(String supplier:List.of("甲","乙")){var r=new ArrayList<>(List.of("1","标","001","包1","1",supplier,"下载IP"));for(int i=0;i<16;i++)r.add(i==4?"M":i==8?"C":i==12?"D":"/");rows.add(r);}WorkbookTable.write(f,"原表",h,rows);
  var result=new IpMacTool().execute(new ToolRequest(ToolId.IP_MAC,Map.of("files",List.of(f)),Map.of(),root.resolve("结果"),Optional.empty()),new TaskContext(root));
  var success=assertInstanceOf(Outcome.Success.class,result);var output=WorkbookTable.read(((ToolResult)success.value()).outputs().get(0),Map.of("status",List.of("核验结果"))).rows();assertEquals(1,output.size());assertEquals("硬件组合一致",output.get(0).get("status"));
 }
 @Test void unrelatedHeadersCannotProduceFalseCleanReport()throws Exception{
  Path f=root.resolve("wrong.xlsx");WorkbookTable.write(f,"数据",List.of("分标名称","包名","轮次","投标人"),List.of(List.of("甲","包1","1","甲")));
  assertInstanceOf(Outcome.Failure.class,new IpMacTool().execute(new ToolRequest(ToolId.IP_MAC,Map.of("files",List.of(f)),Map.of(),root.resolve("结果"),Optional.empty()),new TaskContext(root)));
 }
}
