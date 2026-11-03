package local.tender.tools.performance;
import local.tender.core.*;
import local.tender.io.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
public final class PerformanceTool implements ToolHandler {
 public static final List<String> BID_HEADERS=List.of("采购项目编号","分标名称","分标编号","分包名称","分包编号","项目单位","投标人名称","单位名称","项目归口管理部门");
 public static final List<String> SCORE_HEADERS=List.of("单位名称","关联类型","所属部门","服务类型/物料名称","供应商名称","综合评价","备注");
 public ToolId id(){return ToolId.PERFORMANCE_SCORE;}
 private record Rating(int score,String note,boolean applies){}
 public Outcome<ToolResult> execute(ToolRequest request,TaskContext context)throws Exception{
  try {
   Path a=one(request,"fileA"),b=one(request,"fileB");var bids=WorkbookTable.read(a,columns(BID_HEADERS));var evaluations=WorkbookTable.read(b,columns(SCORE_HEADERS));
   if(bids.rows().isEmpty())throw new IOException("开标表没有数据");
   for(var row:evaluations.rows()){
    String type=row.get("关联类型");if(!Set.of("供应商","供应商+标段","供应商+所属部门").contains(type))throw new IOException("履约评价第"+row.get("__row")+"行关联类型不支持："+type);
    for(String key:List.of("单位名称","供应商名称"))if(row.get(key).isBlank())throw new IOException("履约评价第"+row.get("__row")+"行缺少"+key);
    if(type.equals("供应商+标段")&&row.get("服务类型/物料名称").isBlank())throw new IOException("按标段关联时必须填写服务类型/物料名称");
    if(type.equals("供应商+所属部门")&&row.get("所属部门").isBlank())throw new IOException("按部门关联时必须填写所属部门");
   }
   var output=new ArrayList<List<String>>();var warnings=new ArrayList<Problem>();
   for(var bid:bids.rows()){
    context.checkCancelled();for(String key:List.of("单位名称","投标人名称","分标名称"))if(bid.get(key).isBlank())throw new IOException("开标表第"+bid.get("__row")+"行缺少"+key);
    Map<String,String> match=null;int matches=0;Rating rating=null;var skipped=new ArrayList<String>();boolean validRating=false;
    for(var row:evaluations.rows()){
     if(!row.get("单位名称").equals(bid.get("单位名称"))||!row.get("供应商名称").equals(bid.get("投标人名称")))continue;
     if(row.get("关联类型").equals("供应商+标段")&&!row.get("服务类型/物料名称").equals(bid.get("分标名称")))continue;
     if(row.get("关联类型").equals("供应商+所属部门")&&!row.get("所属部门").equals(bid.get("项目归口管理部门")))continue;
     matches++;Rating candidate=rate(WorkbookTable.numericText(row,"综合评价"));
     if(candidate.applies()){match=row;rating=candidate;validRating=true;}
     else{skipped.add("评价“"+row.get("综合评价")+"”未被原版分段覆盖");if(!validRating){match=row;rating=candidate;}}

    }
    if(rating==null)rating=new Rating(4,"未匹配评价，按原版默认4分",false);
    String note=rating.note();if(validRating&&!skipped.isEmpty())note=note+"；"+String.join("；",skipped)+"；保留末条有效评价，请复核";if(matches>1)note="匹配"+matches+"条，采用末条有效评价（无有效评价时默认4）；"+note;
    if(!note.isEmpty())warnings.add(new Problem("PERFORMANCE_REVIEW","第"+bid.get("__row")+"行 "+bid.get("投标人名称")+"："+note,new SourceLocation(a,null,Integer.parseInt(bid.get("__row")),null)));
    var cells=new ArrayList<String>();for(String header:BID_HEADERS)cells.add(bid.get(header));
    cells.add(match==null?"":match.get("综合评价"));cells.add(Integer.toString(rating.score()));cells.add(match==null?"":match.get("备注"));cells.add(note.isEmpty()?"已匹配":note);output.add(cells);
   }
   var headers=new ArrayList<>(BID_HEADERS);headers.addAll(List.of("综合评价","履约分结果","备注","匹配说明"));
   return WorkbookOutput.publish(request,context,"履约分.xlsx","履约分",headers,output,warnings);
  }catch(IOException|IllegalArgumentException e){return Outcome.fail("INPUT_INVALID",e.getMessage());}
 }
 private static Rating rate(String text){
  if(text.isEmpty()||text.equals("/")||text.equals("0"))return new Rating(4,"评价为原版未知标记（空、/或0），按默认4分；请复核",true);
  Integer named=Map.of("优秀",5,"A",5,"良好",4,"B",4,"一般",3,"C",3,"及格",2,"D",2,"不及格",1,"E",1).get(text);
  if(named!=null)return new Rating(named,"",true);
  try{
   var number=new BigDecimal(text.trim());if(number.signum()<0)throw new IllegalArgumentException("履约评价不能为负数");
   if(number.compareTo(new BigDecimal("90"))>=0)return new Rating(5,"",true);
   if(number.compareTo(new BigDecimal("80"))>0)return new Rating(4,"",true);
   if(number.compareTo(new BigDecimal("60"))>0)return new Rating(3,"",true);
   if(number.compareTo(new BigDecimal("30"))>0)return new Rating(2,"",true);
   if(number.compareTo(new BigDecimal("30"))<0)return new Rating(1,"",true);
   return new Rating(4,"评价恰好30，原版未覆盖该边界，沿用默认4分；请复核",false);
  }catch(NumberFormatException e){return new Rating(4,"评价无法识别，沿用原版默认4分；请复核",false);}
 }
 private static Map<String,List<String>> columns(List<String> names){var map=new LinkedHashMap<String,List<String>>();for(String name:names)map.put(name,List.of(name));return map;}
 private static Path one(ToolRequest r,String key){var paths=r.inputs().get(key);if(paths==null||paths.size()!=1)throw new IllegalArgumentException("请选择一份文件："+key);return paths.get(0);}
}
