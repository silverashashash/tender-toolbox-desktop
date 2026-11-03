package local.tender.tools.award;

import local.tender.core.*;import local.tender.io.*;import org.apache.poi.ss.usermodel.*;
import java.io.IOException;import java.math.BigDecimal;import java.nio.file.*;import java.util.*;import java.util.regex.Pattern;

/** Offline award simulation; source rankings are authoritative inputs, never recomputed here. */
public final class AwardTool implements ToolHandler {
 public static final List<String> MODES=List.of("无限授","一轮一包","一轮多包");
 public static final List<String> INPUT_HEADERS=List.of("分标编号","分标名称","包名称","投标人","排序","投标总价");
 public static final List<String> GROUP_HEADERS=List.of("限授组","分标编号","包名称","最大中包数","授标顺序");
 private record Key(String section,String pack){}
 private record Candidate(String bidder,int rank,BigDecimal price,String source){}
 private static final class Pack {final Key key;final String name;final int order;final List<Candidate> candidates=new ArrayList<>();String group="",note="",status="",winner="",source="";int priority=1,limit=0,round=0;Pack(Key k,String n,int o){key=k;name=n;order=o;}}
 private record Choice(Pack pack,Candidate candidate){}
 public ToolId id(){return ToolId.AWARD;}
 public Outcome<ToolResult> execute(ToolRequest request,TaskContext context)throws Exception {
  try {
   var files=request.inputs().get("files");if(files==null||files.isEmpty())throw new IOException("请选择技术经济排序表");
   String mode=request.parameters().getOrDefault("mode","无限授").trim();if(!MODES.contains(mode))throw new IOException("授标方式应为：无限授、一轮一包或一轮多包");
   var packs=new LinkedHashMap<Key,Pack>();for(Path f:files){context.checkCancelled();read(f,packs,context);}
   if(packs.isEmpty())throw new IOException("没有可处理的包");
   for(Pack p:packs.values()){p.candidates.sort(Comparator.comparingInt(Candidate::rank));Set<Integer> ranks=new HashSet<>();for(var c:p.candidates)if(!ranks.add(c.rank))throw new IOException(p.key+" 排序并列，请先确认唯一排序后授标");}
   if(!mode.equals("无限授"))configure(request,packs);
   for(Pack p:packs.values())if(mode.equals("无限授")||p.group.isEmpty()) {if(p.candidates.isEmpty())p.status="无候选";else award(new Choice(p,p.candidates.get(0)),1);}
   if(!mode.equals("无限授")){var groups=new LinkedHashMap<String,List<Pack>>();for(Pack p:packs.values())if(!p.group.isEmpty())groups.computeIfAbsent(p.group,k->new ArrayList<>()).add(p);for(var group:groups.values())allocate(group,mode,context);}
   var rows=new ArrayList<List<String>>();for(Pack p:packs.values())rows.add(List.of(p.key.section,p.name,p.key.pack,p.group,p.winner,p.status,p.round==0?"":Integer.toString(p.round),mode,p.note,p.source));
   return WorkbookOutput.publish(request,context,"授标结果.xlsx","授标结果",List.of("分标编号","分标名称","包名称","限授组","拟授标人","状态","轮次","授标方式","说明","来源"),rows,List.of());
  }catch(IOException|IllegalArgumentException|ArithmeticException e){return Outcome.fail("INPUT_INVALID",e.getMessage());}
 }
 private static void configure(ToolRequest request,Map<Key,Pack> packs)throws IOException {
  var files=request.inputs().get("groups");if(files==null||files.size()!=1)throw new IOException("限授模式需要一份限授组配置表");
  Map<String,List<String>> headers=new LinkedHashMap<>();for(String h:GROUP_HEADERS)headers.put(h,List.of(h));var seen=new HashSet<Key>();var limits=new HashMap<String,Integer>();
  for(var table:WorkbookTable.readAll(files.get(0),headers).values())for(var row:table.rows()){
   Key key=new Key(required(row,"分标编号"),required(row,"包名称"));Pack p=packs.get(key);if(p==null)throw new IOException("配置中的包不存在："+key);if(!seen.add(key))throw new IOException("同一包重复配置限授组："+key);
   p.group=required(row,"限授组");p.note="请确认组内报价单位及口径一致；金额与折扣率不可混合比较";p.limit=positive(WorkbookTable.numericText(row,"最大中包数"));p.priority=positive(WorkbookTable.numericText(row,"授标顺序"));Integer prior=limits.putIfAbsent(p.group,p.limit);if(prior!=null&&prior!=p.limit)throw new IOException("同一限授组最大中包数不一致："+p.group);
  }
  if(seen.isEmpty())throw new IOException("限授配置为空；如不需要限授，请选择无限授");
 }
 private static void allocate(List<Pack> packs,String mode,TaskContext context){
  Map<String,Integer> counts=new HashMap<>();int round=1;
  while(true){context.checkCancelled();var choices=new ArrayList<Choice>();for(Pack p:packs)if(p.status.isEmpty()){Candidate c=p.candidates.stream().filter(x->counts.getOrDefault(x.bidder,0)<p.limit).findFirst().orElse(null);if(c!=null)choices.add(new Choice(p,c));else p.status=p.candidates.isEmpty()?"无候选":"限授后无可授候选";}
   if(choices.isEmpty())break;
   if(mode.equals("一轮一包")){choices.sort(order());markTies(choices);Choice selected=choices.get(0);award(selected,round++);counts.merge(selected.candidate.bidder,1,Integer::sum);}
   else {var byBidder=new LinkedHashMap<String,List<Choice>>();for(Choice c:choices)byBidder.computeIfAbsent(c.candidate.bidder,k->new ArrayList<>()).add(c);for(var group:byBidder.values()){group.sort(order());markTies(group);int available=group.get(0).pack.limit-counts.getOrDefault(group.get(0).candidate.bidder,0);for(int i=0;i<Math.min(available,group.size());i++){Choice c=group.get(i);award(c,round);counts.merge(c.candidate.bidder,1,Integer::sum);}}round++;}
  }
 }
 private static Comparator<Choice> order(){return Comparator.comparing((Choice c)->c.candidate.price,Comparator.reverseOrder()).thenComparingInt(c->c.pack.priority).thenComparingInt(c->c.pack.order);}
 private static void markTies(List<Choice> choices){for(int i=1;i<choices.size();i++){Choice a=choices.get(i-1),b=choices.get(i);if(a.candidate.price.compareTo(b.candidate.price)==0&&a.pack.priority==b.pack.priority){String warning="；报价及授标顺序相同，按输入文件/工作表顺序处理，请复核";if(!a.pack.note.contains(warning))a.pack.note+=warning;if(!b.pack.note.contains(warning))b.pack.note+=warning;}}}
 private static void award(Choice c,int round){c.pack.winner=c.candidate.bidder;c.pack.status="拟授标";c.pack.round=round;c.pack.source=c.candidate.source;}
 private static String required(Map<String,String> row,String key)throws IOException{String s=row.getOrDefault(key,"").trim();if(s.isEmpty())throw new IOException("第"+row.get("__row")+"行缺少"+key);return s;}
 private static int positive(String text){int n=new BigDecimal(text.trim()).intValueExact();if(n<1)throw new IllegalArgumentException("排序、限授数量和授标顺序必须为正整数");return n;}
 private static void add(Map<Key,Pack> packs,String code,String name,String pack,String supplier,String rank,String price,String source)throws IOException{
  if(code.isBlank()||name.isBlank()||pack.isBlank())throw new IOException(source+" 缺少分标编号、分标名称或包名称");Key k=new Key(code.trim(),pack.trim());Pack p=packs.computeIfAbsent(k,key->new Pack(key,name.trim(),packs.size()));if(!p.name.equals(name.trim()))throw new IOException(source+" 同一分标编号的名称不一致");
  if(supplier.isBlank()){if(!rank.isBlank()||!price.isBlank())throw new IOException(source+" 缺少投标人");return;}
  if(p.candidates.stream().anyMatch(c->c.bidder.equals(supplier.trim())))throw new IOException(source+" 同一包投标人重复");BigDecimal amount=new BigDecimal(price.trim().replace(",",""));if(amount.signum()<0)throw new IOException(source+" 投标总价不能为负数");p.candidates.add(new Candidate(supplier.trim(),positive(rank),amount,source));
 }
 private static void read(Path file,Map<Key,Pack> packs,TaskContext context)throws IOException{
  try(var in=Files.newInputStream(file);var wb=WorkbookFactory.create(in)){
   var fmt=new DataFormatter(Locale.ROOT);var ev=wb.getCreationHelper().createFormulaEvaluator();
   boolean original=wb.getNumberOfSheets()>0&&value(wb.getSheetAt(0),5,4,fmt,ev).equals("投标人");
   if(!original){var headers=new LinkedHashMap<String,List<String>>();for(String h:INPUT_HEADERS)headers.put(h,List.of(h));for(var table:WorkbookTable.readAll(file,headers).entrySet())for(var row:table.getValue().rows())add(packs,required(row,"分标编号"),required(row,"分标名称"),required(row,"包名称"),row.get("投标人"),WorkbookTable.numericText(row,"排序"),WorkbookTable.numericText(row,"投标总价"),file.getFileName()+" / "+table.getKey()+" 第"+row.get("__row")+"行");return;}
   var localKeys=new HashSet<Key>();for(Sheet sheet:wb){context.checkCancelled();String location=file.getFileName()+" / "+sheet.getSheetName(),meta=value(sheet,3,0,fmt,ev);var code=Pattern.compile("分标编号\\s*[:：]\\s*(.*?)\\s*价格单位",Pattern.DOTALL).matcher(meta);var name=Pattern.compile("(?:设备/材料名称|分标名称)\\s*[:：]\\s*(.*?)\\s*日期",Pattern.DOTALL).matcher(meta);if(!code.find()||!name.find())throw new IOException(location+" 第4行缺少分标编号/设备材料名称");String section=code.group(1).trim(),sectionName=name.group(1).trim();
    boolean full=value(sheet,5,9,fmt,ev).equals("排序")&&value(sheet,5,11,fmt,ev).contains("投标总价");boolean compact=value(sheet,5,6,fmt,ev).equals("排序")&&value(sheet,5,8,fmt,ev).contains("投标总价");if(!value(sheet,5,4,fmt,ev).equals("投标人")||(!full&&!compact))throw new IOException(location+" 第6行不是支持的经济排序表");
    String pack=value(sheet,6,0,fmt,ev).trim();if(pack.isBlank()||List.of("无","无数据").contains(pack))pack=sheet.getSheetName();Key key=new Key(section,pack);if(packs.containsKey(key)||!localKeys.add(key))throw new IOException(location+" 重复包："+key);add(packs,section,sectionName,pack,"","","",location);
    for(int r=6;r<=sheet.getLastRowNum();r++){String supplier=value(sheet,r,4,fmt,ev);String rank=value(sheet,r,full?9:6,fmt,ev),price=numeric(sheet,r,full?11:8,fmt,ev);if(supplier.isBlank()&&rank.isBlank()&&price.isBlank())continue;add(packs,section,sectionName,pack,supplier,rank,price,location+" 第"+(r+1)+"行");}
   }
  }
 }
 private static String value(Sheet s,int r,int c,DataFormatter f,FormulaEvaluator e)throws IOException{Row row=s.getRow(r);return CellValues.read(row==null?null:row.getCell(c),f,e).display().trim();}
 private static String numeric(Sheet s,int r,int c,DataFormatter f,FormulaEvaluator e)throws IOException{Row row=s.getRow(r);return CellValues.read(row==null?null:row.getCell(c),f,e).numericText();}
}
