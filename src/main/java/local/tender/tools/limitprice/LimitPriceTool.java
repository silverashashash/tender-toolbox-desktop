package local.tender.tools.limitprice;
import local.tender.core.*;
import local.tender.io.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
public final class LimitPriceTool implements ToolHandler {
 public static final List<String> QUOTE_HEADERS=List.of("轮次","包名","投标人","采购申请号","含税单价（万元）","物资描述","增值税率（%）","含税总价（万元）","折扣率（%）");
 public static final List<String> LIMIT_HEADERS=List.of("分标名称","采购申请标识","单价限价（元）");
 public static final List<String> SERVICE_LIMIT_HEADERS=List.of("分标名称","采购申请标识","限价值（元/%)","报价方式");
 private record LimitKey(String section,String item){}
 private record Limit(BigDecimal value,String basis){}
 private record Quote(String section,String pack,String supplier,String item,String limitItem,String round,BigDecimal price,String priceText,String basis,String tax,String material,String location){}
 public ToolId id(){return ToolId.LIMIT_PRICE;}
 public Outcome<ToolResult> execute(ToolRequest request,TaskContext context)throws Exception{
  try{
   Path quotesFile=one(request,"quotes"),limitsFile=one(request,"limits");var tables=WorkbookTable.readAll(quotesFile,quoteColumns(),Map.of("含税单价（万元）",List.of("含税单价（万元）"),"含税总价（万元）",List.of("含税总价（万元）"),"折扣率（%）",List.of("折扣率（%）")));
   String projectType=request.parameters().getOrDefault("projectType","物资");
   if(!Set.of("服务","物资").contains(projectType))throw new IllegalArgumentException("项目类型请填写：服务 或 物资");
   boolean service=projectType.equals("服务");
   var allowedTaxes=taxRates(request.parameters().getOrDefault("allowedTaxes","自动"),service?"0,1,3,5,6,9,13":"13",false);
   var proofTaxes=taxRates(request.parameters().getOrDefault("proofTaxes","0,1"),"0,1",true);
   String limitBasis=service?"总价":request.parameters().getOrDefault("limitBasis","单价");
   if(!Set.of("单价","总价").contains(limitBasis))throw new IllegalArgumentException("限价口径请填写：单价 或 总价");
   var limits=WorkbookTable.read(limitsFile,limitColumns(service));
   var limitMap=new LinkedHashMap<LimitKey,Limit>();for(var row:limits.rows()){
    String item=row.get("采购申请标识");if(item.isBlank())throw new IOException("限价表采购申请标识为空");
    String section=row.get("分标名称");if(section.isBlank())throw new IOException("限价表 第"+row.get("__row")+"行分标名称为空");
    String basis=service?row.get("报价方式").trim().replace("报价",""):limitBasis;
    if(limitMap.putIfAbsent(new LimitKey(section,item),new Limit(number(WorkbookTable.numericText(row,"单价限价（元）")),basis))!=null)throw new IOException("限价表同一分标的采购申请标识重复："+section+" / "+item);
   }
   var quotes=new ArrayList<Quote>();var seen=new HashSet<List<String>>();
   for(var entry:tables.entrySet())for(var row:entry.getValue().rows()){
    context.checkCancelled();for(String key:List.of("轮次","包名","投标人","采购申请号"))if(row.get(key).isBlank())throw new IOException(entry.getKey()+" 第"+row.get("__row")+"行缺少"+key);
    String round=row.get("轮次").replace("第一轮","第1轮").replace("第二轮","第2轮");
    var key=List.of(entry.getKey(),row.get("包名"),row.get("投标人"),row.get("采购申请号"),round);
    if(!seen.add(key))throw new IOException("同一供应商、物料和轮次存在重复报价："+key);
    String priceText;String basis;
    if(service){
     String total=WorkbookTable.numericText(row,"含税总价（万元）"),discount=WorkbookTable.percentText(row,"折扣率（%）");
     if(!isMissing(total)&&!isMissing(discount))throw new IOException(entry.getKey()+" 第"+row.get("__row")+"行同时填写总价和折扣率，无法确定报价方式");
     basis=!isMissing(discount)?"折扣率":"总价";priceText=basis.equals("折扣率")?discount:total;
    }else{
     priceText=WorkbookTable.numericText(row,"含税单价（万元）");basis="单价";
     if(isMissing(priceText)){String total=WorkbookTable.numericText(row,"含税总价（万元）");if(!isMissing(total)){priceText=total;basis="总价";}}
    }
    if(priceText==null)priceText="";
    quotes.add(new Quote(entry.getKey(),row.get("包名"),row.get("投标人"),row.get("采购申请号"),limitIdentifier(entry.getKey(),row.get("采购申请号"),limitMap),round,optionalNumber(priceText,entry.getKey()+" 第"+row.get("__row")+"行 含税"+basis+"（万元）"),priceText,basis,WorkbookTable.percentText(row,"增值税率（%）"),row.get("物资描述"),entry.getKey()+" 第"+row.get("__row")+"行"));
   }
   if(quotes.isEmpty())throw new IOException("报价表没有记录");
   var result=new ArrayList<List<String>>();var warnings=new ArrayList<Problem>();var samePrices=new LinkedHashMap<List<String>,List<Quote>>();var rounds=new LinkedHashMap<List<String>,Map<String,Quote>>();
   for(var q:quotes){
    context.checkCancelled();Limit matched=limitMap.get(new LimitKey(q.section(),q.limitItem()));boolean discount=q.basis().equals("折扣率");
    boolean sameBasis=matched!=null&&matched.basis().equals(q.basis());
    BigDecimal limit=!sameBasis?null:discount?matched.value().movePointRight(2):matched.value().movePointLeft(4);
    if(matched==null){String message="未找到限价，未完成限价核验："+q.section()+" / "+q.item();add(result,"缺少限价",q,"","",message);}
    if(q.price()==null){add(result,"报价缺失",q,limit==null?"":limit.toPlainString(),"",q.location()+"：适用的报价字段为空或占位符；未完成报价核验");}
    else if(service&&matched!=null&&!Set.of("总价","折扣率").contains(matched.basis())){add(result,"报价方式缺失",q,"","","限价表报价方式待确认："+q.limitItem()+"；未进行限价比较");}
    else if(matched!=null&&!sameBasis){add(result,"口径不一致",q,"","","报价表为"+q.basis()+"，限价表报价方式为"+matched.basis()+"，未进行限价比较；限价原值："+matched.value().toPlainString());}
    else if(limit!=null){int comparison=q.price().compareTo(limit);if(comparison>=0)add(result,comparison>0?"超过限价":"等于限价",q,limit.toPlainString(),"",q.material());}
    String tax=q.tax().trim();if(tax.endsWith("%")||tax.endsWith("％"))tax=tax.substring(0,tax.length()-1);
    BigDecimal taxValue=optionalNumber(tax,q.location()+" 增值税率");
    if(taxValue==null)add(result,"税率缺失",q,limit==null?"":limit.toPlainString(),"",q.location()+"：未完成税率核验");
    else{
     if(!allowedTaxes.contains(taxValue))add(result,"税率异常",q,limit==null?"":limit.toPlainString(),"","不在本次允许税率中："+allowedTaxes+"%；请人工复核");
     if(proofTaxes.contains(taxValue))add(result,"税率证明待核实",q,limit==null?"":limit.toPlainString(),"","命中证明核查税率："+proofTaxes+"%；请核查投标文件中的相应税率证明，不据此认定纳税人身份");
    }
    if(q.price()==null)continue;
    samePrices.computeIfAbsent(List.of(q.section(),q.pack(),q.round(),q.item(),q.basis(),q.price().stripTrailingZeros().toPlainString()),k->new ArrayList<>()).add(q);
    rounds.computeIfAbsent(List.of(q.section(),q.pack(),q.item(),q.supplier(),q.basis()),k->new LinkedHashMap<>()).put(q.round(),q);
   }
   long missingLimits=result.stream().filter(r->r.get(0).equals("缺少限价")).count();
   if(missingLimits>0)warnings.add(new Problem("LIMIT_MISSING",missingLimits+"条记录未匹配限价；详见结果中的缺少限价行",null));
   long missingPrices=quotes.stream().filter(q->q.price()==null).count();
   if(missingPrices>0)warnings.add(new Problem("PRICE_MISSING",missingPrices+"条记录缺少适用报价（服务类为总价或折扣率），报价核验未完成；详见结果中的报价缺失行",null));
   long unknownTypes=result.stream().filter(r->r.get(0).equals("报价方式缺失")).count();
   if(unknownTypes>0)warnings.add(new Problem("LIMIT_TYPE_UNKNOWN",unknownTypes+"条记录的限价报价方式未确认",null));
   long mismatches=result.stream().filter(r->r.get(0).equals("口径不一致")).count();
   if(mismatches>0)warnings.add(new Problem("BASIS_MISMATCH",mismatches+"条记录的报价与限价口径不同，未作限价比较",null));
   long missingTaxes=result.stream().filter(r->r.get(0).equals("税率缺失")).count();
   if(missingTaxes>0)warnings.add(new Problem("TAX_MISSING",missingTaxes+"条记录未完成税率核验",null));
   for(var group:samePrices.values())if(group.size()>1){var q=group.get(0);String suppliers=String.join("、",group.stream().map(Quote::supplier).toList());add(result,"相同报价",q,"",suppliers,"同一物料、轮次有"+group.size()+"家供应商报价相同");}
   for(var group:rounds.values()){
    Quote first=group.get("第1轮"),second=group.get("第2轮");if(first!=null&&second!=null&&second.price().compareTo(first.price())>0)add(result,"二轮报价上涨",second,"","","第一轮："+first.price().toPlainString()+(second.basis().equals("折扣率")?"%":"万元")+"；第二轮："+second.price().toPlainString()+(second.basis().equals("折扣率")?"%":"万元"));
   }
   return WorkbookOutput.publish(request,context,"报价核验.xlsx","核验结果",List.of("类别","分标名称","包名","投标人","采购申请号","限价匹配标识","轮次","含税单价（万元）","含税总价（万元）","折扣率（%）","采用口径","限价（万元）","折扣率上限（%）","增值税率（%）","说明"),result,warnings);
  }catch(IOException|IllegalArgumentException e){return Outcome.fail("INPUT_INVALID",e.getMessage());}
 }
 private static void add(List<List<String>> rows,String type,Quote q,String limit,String suppliers,String note){rows.add(List.of(type,q.section(),q.pack(),suppliers.isEmpty()?q.supplier():suppliers,q.item(),q.limitItem(),q.round(),q.basis().equals("单价")?q.priceText():"",q.basis().equals("总价")?q.priceText():"",q.basis().equals("折扣率")?q.priceText():"",q.basis(),q.basis().equals("折扣率")?"":limit,q.basis().equals("折扣率")?limit:"",q.tax(),note));}
 private static String limitIdentifier(String section,String original,Map<LimitKey,Limit> limits){
  if(limits.containsKey(new LimitKey(section,original)))return original;
  return original.length()>5?original.substring(0,original.length()-5):original;
 }
 private static SortedSet<BigDecimal> taxRates(String setting,String defaults,boolean mayDisable){
  String text=setting.trim();if(text.equals("自动")||(!mayDisable&&text.isBlank()))text=defaults;
  var rates=new TreeSet<BigDecimal>();if(mayDisable&&text.equals("无"))return rates;
  if(text.isBlank())throw new IllegalArgumentException("税率选项不能为空；允许税率可填自动，证明核查可填无");
  for(String token:text.split("[,，、;；\\s]+")){
   String value=token.replace("%","").replace("％","");BigDecimal rate=number(value);
   if(rate.compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException("税率须在0至100之间："+token);rates.add(rate);
  }
  return rates;
 }
 private static boolean isMissing(String text){return text==null||text.isBlank()||Set.of("/","／","-","—").contains(text.trim());}
 private static BigDecimal optionalNumber(String text,String location){
  if(isMissing(text))return null;
  try{return number(text);}catch(IllegalArgumentException e){throw new IllegalArgumentException(location+"："+e.getMessage());}
 }
 private static BigDecimal number(String text){String s=text.trim();if(!s.matches("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?"))throw new IllegalArgumentException("金额或税率不是有效非负数："+text);return new BigDecimal(s.replace(",",""));}
 private static Map<String,List<String>> limitColumns(boolean service){var map=columns(LIMIT_HEADERS);map.put("单价限价（元）",List.of("单价限价（元）","总价限价（元）","限价值","限价值（元/%)"));if(service)map.put("报价方式",List.of("报价方式"));return map;}
 public static Map<String,List<String>> quoteColumns(){var map=columns(QUOTE_HEADERS);map.remove("含税单价（万元）");map.remove("含税总价（万元）");map.remove("折扣率（%）");map.put("增值税率（%）",List.of("增值税率（%）","增值税率或征收率（%）"));return map;}
 private static Map<String,List<String>> columns(List<String> names){var map=new LinkedHashMap<String,List<String>>();for(String name:names)map.put(name,List.of(name));return map;}
 private static Path one(ToolRequest r,String key){var paths=r.inputs().get(key);if(paths==null||paths.size()!=1)throw new IllegalArgumentException("请选择一份文件："+key);return paths.get(0);}
}
