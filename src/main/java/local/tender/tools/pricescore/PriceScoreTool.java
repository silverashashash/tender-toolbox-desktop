package local.tender.tools.pricescore;

import local.tender.core.*;
import local.tender.io.*;
import java.nio.file.*;
import java.util.*;
import java.math.*;
import java.io.IOException;

/** Local reconstruction of the six shipped price formulas; input ambiguity is rejected. */
public final class PriceScoreTool implements ToolHandler {
 public static final List<String> HEADERS=List.of("分标名称","分包名称","轮次","投标人名称","投标价格（万元）","折扣率","是否参与计算");
 public static final List<String> RULE_HEADERS=List.of("分标名称","价格公式","qz","w1","w2","n1","n2","c","F1","F2","a","b","m");
 public static final List<String> METHODS=List.of("区间平均价浮动法","算术平均值下浮法","区间平均下浮双边曲线算法","区间复合平均价法","算数平均值浮动法","区间复合平均价浮动法");
 private record Group(String section,String pack){}
 private record Bid(Group group,String round,String supplier,BigDecimal value,boolean discount,String source){}
 private record Rule(int method,Map<String,BigDecimal> values){BigDecimal p(String key){return values.get(key);}}
 public ToolId id(){return ToolId.PRICE_SCORE;}
 private static LinkedHashMap<String,List<String>> columns(List<String> names){var m=new LinkedHashMap<String,List<String>>();for(String n:names)m.put(n,List.of(n));return m;}
 private static String[] parameterNames(int method){return switch(method){case 1->new String[]{"w1","w2","n1","n2","c","qz"};case 2->new String[]{"c","n1","n2","qz"};case 3->new String[]{"F1","F2","n1","n2","c","qz"};case 4->new String[]{"b","c","n1","n2","qz"};case 5->new String[]{"F1","F2","m","c","qz"};default->new String[]{"a","b","n1","n2","c","qz"};};}
 public static List<String> templateHeaders(String method){
  int index=METHODS.indexOf(method);if(index<0)throw new IllegalArgumentException("请选择有效价格公式");
  var used=new HashSet<>(Arrays.asList(parameterNames(index+1)));used.add("分标名称");used.add("价格公式");
  return RULE_HEADERS.stream().filter(used::contains).toList();
 }
 public record Configuration(String section,String method,String weight){}
 public static List<Configuration> configurations(Path file)throws IOException{
  return readRules(file).entrySet().stream().map(e->new Configuration(e.getKey(),METHODS.get(e.getValue().method()-1),text(e.getValue().p("qz"))+"%")).toList();
 }
 private static Map<String,Rule> readRules(Path file)throws IOException{
   var rules=new LinkedHashMap<String,Rule>();
   var optional=columns(RULE_HEADERS.subList(2,RULE_HEADERS.size()));
   for(var table:WorkbookTable.readAll(file,columns(RULE_HEADERS.subList(0,2)),optional).values())for(var row:table.rows()){
    String section=required(row,"分标名称"),name=required(row,"价格公式");int method=METHODS.indexOf(name)+1;
    if(method==0&&name.matches("1001300[1-6]"))method=Integer.parseInt(name.substring(7));
    if(method<1||method>6)throw new IOException("未知价格公式："+name);
    String[] fields=parameterNames(method);
    var params=new LinkedHashMap<String,BigDecimal>();for(String f:fields)params.put(f,number(WorkbookTable.numericText(row,f),"参数"+f));
    Rule rule=new Rule(method,Map.copyOf(params));validate(rule);if(rules.putIfAbsent(section,rule)!=null)throw new IOException("同一分标配置了多条价格公式："+section);
   }
   if(rules.isEmpty())throw new IOException("参数表没有分标配置");
   return rules;
 }
 public Outcome<ToolResult> execute(ToolRequest request,TaskContext context)throws Exception{
  try{
   var files=request.inputs().get("files");var configs=request.inputs().get("rules");
   if(files==null||files.isEmpty()||configs==null||configs.size()!=1)throw new IOException("请选择报价文件及一份价格公式参数表");
   var rules=readRules(configs.get(0));
   var groups=new LinkedHashMap<Group,List<Bid>>();String round=request.parameters().getOrDefault("round","").trim();
   var required=columns(HEADERS.subList(0,4));required.put("分包名称",List.of("分包名称","包名","包名称"));required.put("投标人名称",List.of("投标人名称","投标人"));
   var quoteColumns=columns(HEADERS.subList(4,7));
   for(Path file:files)for(var entry:WorkbookTable.readAll(file,required,quoteColumns).entrySet())for(var row:entry.getValue().rows()){
    context.checkCancelled();String participation=row.getOrDefault("是否参与计算","").trim();if(participation.equals("否"))continue;if(!participation.isEmpty()&&!participation.equals("是"))throw new IOException("是否参与计算只能填写是或否");String r=required(row,"轮次");if(!round.isEmpty()&&!round.equals(r))continue;
    String source=file.getFileName()+" / "+entry.getKey()+" 第"+row.get("__row")+"行";
    String total=missing(WorkbookTable.numericText(row,"投标价格（万元）")),discount=missing(WorkbookTable.numericText(row,"折扣率"));
    if(total.isEmpty()==discount.isEmpty())throw new IOException(source+"：投标价格与折扣率必须恰好填写一项");
    boolean isDiscount=total.isEmpty();BigDecimal value;
    if(isDiscount){String display=row.getOrDefault("折扣率","");value=number(discount.replace("%","").replace("％",""),source);if(display.endsWith("%")||display.endsWith("％")){if(row.containsKey("__numeric:折扣率"))value=value.multiply(BigDecimal.valueOf(100));}else value=value.multiply(BigDecimal.valueOf(100));}
    else value=number(total,source);
    if(value.signum()<=0)throw new IOException(source+"：报价必须大于0");
    Group g=new Group(required(row,"分标名称"),required(row,"分包名称"));groups.computeIfAbsent(g,k->new ArrayList<>()).add(new Bid(g,r,required(row,"投标人名称"),value,isDiscount,source));
   }
   if(groups.isEmpty())throw new IOException("所选轮次没有可计算的报价");
   var rows=new ArrayList<List<String>>();var warnings=new ArrayList<Problem>();
   for(var entry:groups.entrySet()){
    context.checkCancelled();Group g=entry.getKey();List<Bid> bids=entry.getValue();Rule rule=rules.get(g.section());if(rule==null)throw new IOException("分标缺少公式参数："+g.section());
    if(bids.stream().map(Bid::round).distinct().count()!=1)throw new IOException(g.section()+" / "+g.pack()+" 存在多个轮次，请填写要计算的轮次或仅选单一轮次文件");
    if(bids.stream().map(Bid::discount).distinct().count()!=1)throw new IOException(g.section()+" / "+g.pack()+" 混有总价和折扣率，不可混算");
    if(bids.stream().map(Bid::supplier).distinct().count()!=bids.size())throw new IOException(g.section()+" / "+g.pack()+" 同一投标人有重复记录，请先确认唯一报价");
    BigDecimal base=base(rule,bids.stream().map(Bid::value).toList());if(base.signum()<=0)throw new IOException("基准值必须大于0");
    for(Bid bid:bids){BigDecimal score=score(rule,bid.value(),base),weighted=score.divide(BigDecimal.valueOf(100)).multiply(rule.p("qz")).setScale(2,RoundingMode.HALF_UP);
     rows.add(List.of(g.section(),g.pack(),bid.round(),bid.supplier(),bid.discount()?"折扣率（%）":"总价（万元）",text(bid.value()),METHODS.get(rule.method()-1),text(base.setScale(6,RoundingMode.HALF_UP)),text(score),text(weighted),bid.source()));}
   }
   warnings.add(new Problem("ELIGIBLE_QUOTES_ONLY","参与计算的报价应为已确认有效、进入详评的报价；可在是否参与计算列填否排除，空白按参与计算处理；本工具不会自动判定否决。参数须按本批采购文件填写。",null));
   return WorkbookOutput.publish(request,context,"价格分计算.xlsx","价格分",List.of("分标名称","分包名称","轮次","投标人名称","报价口径","报价","价格公式","基准值","价格分","加权价格分","来源"),rows,warnings);
  }catch(IOException|IllegalArgumentException|ArithmeticException e){return Outcome.fail("INPUT_INVALID",e.getMessage());}
 }
 private static String required(Map<String,String> row,String key)throws IOException{String s=row.getOrDefault(key,"").trim();if(s.isEmpty())throw new IOException("缺少"+key);return s;}
 private static String missing(String v){if(v==null)return "";v=v.trim();return Set.of("/","／","—","-").contains(v)?"":v;}
 private static BigDecimal number(String s,String label)throws IOException{try{return new BigDecimal(s.replace(",","").trim());}catch(Exception e){throw new IOException(label+"不是有效数字："+s);}}
 private static String text(BigDecimal n){return n.stripTrailingZeros().toPlainString();}
 private static void range(Rule r,String key,double low,double high,boolean closedLow,boolean closedHigh)throws IOException{
  BigDecimal n=r.p(key);int l=n.compareTo(BigDecimal.valueOf(low)),h=n.compareTo(BigDecimal.valueOf(high));if(l<0||(!closedLow&&l==0)||h>0||(!closedHigh&&h==0))throw new IOException("参数"+key+"超出范围："+(closedLow?"[":"(")+low+","+high+(closedHigh?"]":")"));
 }
 private static void validate(Rule r)throws IOException{
  range(r,"qz",0,100,true,true);
  for(String n:List.of("n1","n2"))if(r.values.containsKey(n)&&r.p(n).signum()<=0)throw new IOException(n+"必须大于0");
  if(r.method==4){range(r,"b",-1,0,false,false);range(r,"c",0,1,false,false);}else range(r,"c",-.1,.1,false,false);
  if(r.method==1){range(r,"w1",-1,0,false,false);range(r,"w2",0,1,false,false);}
  if(r.method==3){range(r,"F1",0,1,false,false);range(r,"F2",1,2,false,false);}
  if(r.method==5){range(r,"m",0,1,true,false);if(r.p("F1").signum()<=0||r.p("F2").signum()<=0)throw new IOException("F1、F2必须大于0");}
  if(r.method==6){range(r,"a",-1,0,false,false);range(r,"b",0,1,false,false);}
 }
 private static BigDecimal avg(List<BigDecimal> p){return p.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(p.size()),18,RoundingMode.HALF_UP);}
 private static List<BigDecimal> interval(List<BigDecimal> p,BigDecimal a,BigDecimal b,boolean open){return p.stream().filter(v->open?v.compareTo(a)>0&&v.compareTo(b)<0:v.compareTo(a)>=0&&v.compareTo(b)<=0).toList();}
 static BigDecimal base(Rule r,List<BigDecimal> p)throws IOException{
  BigDecimal a=avg(p),one=BigDecimal.ONE;List<BigDecimal> selected;switch(r.method){
   case 1:{var sorted=new ArrayList<>(p);sorted.sort(Comparator.naturalOrder());int n=p.size(),low=n<=5?0:n<=20?1:n<=30?2:3,high=n<=5?0:n<=10?1:n<=20?2:n<=30?3:4;List<BigDecimal> remaining=sorted.subList(low,n-high);a=avg(remaining);selected=interval(remaining,a.multiply(one.add(r.p("w1"))),a.multiply(one.add(r.p("w2"))),false);return(selected.isEmpty()?a:avg(selected)).multiply(one.subtract(r.p("c")));}
   case 2:return a.multiply(one.subtract(r.p("c")));
   case 3:selected=interval(p,a.multiply(r.p("F1")),a.multiply(r.p("F2")),false);return(selected.isEmpty()?a:avg(selected)).multiply(one.add(r.p("c")));
   case 4:selected=interval(p,a.multiply(one.add(r.p("b"))),a.multiply(one.add(r.p("c"))),false);if(!selected.isEmpty())return avg(selected);BigDecimal min=p.stream().min(Comparator.naturalOrder()).orElseThrow();if(p.stream().filter(v->v.compareTo(min)==0).count()>1)throw new IOException("区间复合平均价法筛选为空且最低价并列：原版以并列最低价之和计算，口径有歧义，请确认采购公式后再处理");return a.add(min).divide(BigDecimal.valueOf(2),6,RoundingMode.HALF_UP);
   case 5:BigDecimal cutoff=a.multiply(one.subtract(r.p("m")));selected=p.stream().filter(v->v.compareTo(cutoff)>=0).toList();return avg(selected).multiply(one.add(r.p("c")));
   default:if(p.size()<=5)return a.multiply(one.add(r.p("c")));selected=interval(p,a.multiply(one.add(r.p("a"))),a.multiply(one.add(r.p("b"))),true);return(selected.isEmpty()?a:avg(selected)).multiply(one.add(r.p("c")));
  }
 }
 private static BigDecimal score(Rule r,BigDecimal price,BigDecimal base){
  boolean above=price.compareTo(base)>=0;BigDecimal n=r.p(r.method==5?(above?"F1":"F2"):(above?"n1":"n2")),hundred=BigDecimal.valueOf(100),result;
  if(r.method==3){BigDecimal ratio=(above?base:price).divide(above?price:base,6,RoundingMode.HALF_UP);result=hundred.multiply(BigDecimal.valueOf(Math.pow(ratio.doubleValue(),n.doubleValue())));}
  else result=hundred.subtract(hundred.multiply(n).multiply(price.subtract(base).abs()).divide(base,6,RoundingMode.HALF_UP)).max(BigDecimal.ZERO);
  return result.setScale(2,RoundingMode.HALF_UP);
 }
}
