package local.tender.tools.objective;
import local.tender.core.*;import local.tender.io.*;
import org.apache.poi.ss.usermodel.*;
import java.nio.file.*;import java.util.*;import java.math.BigDecimal;import java.io.IOException;import java.util.regex.Pattern;
public final class ObjectiveTool implements ToolHandler {
 public static final List<String> RULE_HEADERS=List.of("客观评分细则","分值步长（可空）");
 public record Criterion(String name,List<String> projects){public Criterion{projects=List.copyOf(projects);}}
 public static final List<String> SCOPED_RULE_HEADERS=List.of("客观评分细则","分值步长（可空）","评分办法标识");
 public record ScoringMethod(String id,List<Criterion> criteria,List<String> packages){public ScoringMethod{criteria=List.copyOf(criteria);packages=List.copyOf(packages);}}
 public record Selection(String methodId,String criterion){}
 public static List<ScoringMethod> discoverMethods(List<Path> files)throws IOException{
  if(files.isEmpty())throw new IOException("请先选择专家评分明细表");
  var definitions=new LinkedHashMap<String,List<Criterion>>();var packages=new LinkedHashMap<String,LinkedHashSet<String>>();
  for(Path file:files)try(var in=Files.newInputStream(file);var workbook=WorkbookFactory.create(in)){
   var cells=new Cells(workbook);
   for(Sheet sheet:workbook){var items=methodCriteria(sheet,cells);String id=methodId(items);definitions.putIfAbsent(id,items);
    String section=sectionName(cells.text(sheet,1,0));if(section.isEmpty())section=file.getFileName().toString();
    packages.computeIfAbsent(id,k->new LinkedHashSet<>()).add(section+" / "+sheet.getSheetName());
   }
  }
  if(definitions.isEmpty())throw new IOException("没有读取到评分细则");
  return definitions.entrySet().stream().map(e->new ScoringMethod(e.getKey(),e.getValue(),List.copyOf(packages.get(e.getKey())))).toList();
 }
 private static List<Criterion> methodCriteria(Sheet sheet,Cells cells)throws IOException{
  List<Criterion> definition=null;
  for(int r=0;r<=sheet.getLastRowNum();r++)if(isCriteriaHeader(cells.text(sheet,r,1))){
   var page=new ArrayList<Criterion>();boolean total=false;
   for(r+=2;r<=sheet.getLastRowNum();r++){
    if(cells.text(sheet,r,0).equals("合计")||cells.text(sheet,r,1).equals("合计")){total=true;break;}
    String item=cells.text(sheet,r,1).trim();if(item.isEmpty())continue;
    int projectRow=r;for(var range:sheet.getMergedRegions())if(range.isInRange(r,0)){projectRow=range.getFirstRow();break;}
    String project=cells.text(sheet,projectRow,0).trim();page.add(new Criterion(item,List.of(project.isEmpty()?"未填写项目名称":project)));
   }
   if(!total)throw new IOException(sheet.getSheetName()+" 缺少合计行");
   if(definition==null)definition=List.copyOf(page);else if(!definition.equals(page))throw new IOException(sheet.getSheetName()+" 同一工作表分页评分办法不一致");
  }
  if(definition==null||definition.isEmpty())throw new IOException(sheet.getSheetName()+" 未找到评分细则");return definition;
 }
 private static String methodId(List<Criterion> items){
  var data=new StringBuilder();for(var item:items){for(String part:List.of(item.projects().get(0),item.name()))data.append(part.length()).append(':').append(part);}
  try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 private static String sectionName(String text){var match=Pattern.compile("材料名称[:：]\\s*(.*?)\\s*(?:设备/材料名称|材料名称|分包编号|项目单位|工程名称|日期)[:：]",Pattern.DOTALL).matcher(text);return match.find()?match.group(1).trim():"";}
 private static boolean isCriteriaHeader(String text){return Set.of("商务评分细则","技术评分细则").contains(text);}
 private record Key(String section,String pack,String supplier,String criterion){}
 private record Scope(String section,String pack){}
 private record Score(BigDecimal value,String source){}
 public ToolId id(){return ToolId.EXPERT_OBJECTIVE;}
 public Outcome<ToolResult> execute(ToolRequest request,TaskContext context)throws Exception{
  try{
   var files=request.inputs().get("files");var ruleFiles=request.inputs().get("rules");if(files==null||files.isEmpty()||ruleFiles==null||ruleFiles.size()!=1)throw new IOException("请选择专家评分工作簿和客观项规则表");
   var ruleSets=new LinkedHashMap<String,Map<String,BigDecimal>>();
   for(var row:WorkbookTable.readAll(ruleFiles.get(0),Map.of("item",List.of("客观评分细则"),"step",List.of("分值步长（可空）")),Map.of("method",List.of("评分办法标识"))).values().iterator().next().rows()){
    var rules=ruleSets.computeIfAbsent(row.getOrDefault("method","").trim(),k->new LinkedHashMap<>());
    String item=row.get("item").trim(),step=WorkbookTable.numericText(row,"step").trim();if(item.isEmpty())throw new IOException("规则表客观评分细则不能为空");if(rules.containsKey(item))throw new IOException("规则表细则重复："+item);
    BigDecimal multiple=step.isEmpty()?null:new BigDecimal(step);if(multiple!=null&&multiple.signum()<=0)throw new IOException("分值步长必须大于0："+item);rules.put(item,multiple);
   }
   if(ruleSets.isEmpty())throw new IOException("规则表至少选择一项客观评分细则");
   var matchedMethods=new HashSet<String>();var scopeMethods=new HashMap<Scope,String>();var scopeRules=new HashMap<Scope,Map<String,BigDecimal>>();
   var scores=new LinkedHashMap<Key,Map<String,Score>>();var experts=new LinkedHashMap<Scope,Set<String>>();
   var outputs=new ArrayList<List<String>>();var warnings=new ArrayList<Problem>();
   for(Path file:files){
    context.progress("读取客观评分："+file.getFileName(),0,OptionalLong.empty());
    String stem=file.getFileName().toString().replaceFirst("(?i)\\.xlsx?$","");String[] parts=stem.split("_",3);if(parts.length<3||parts[1].isBlank()||parts[2].isBlank())throw new IOException("文件名须为：评分表_专家编号_专家姓名.xlsx："+file.getFileName());String expert=parts[1]+"_"+parts[2];
    try(var in=Files.newInputStream(file);var w=WorkbookFactory.create(in)){
     var cells=new Cells(w);
     for(Sheet sheet:w){context.checkCancelled();String section=request.parameters().getOrDefault("section","").trim();
      if(section.isEmpty()){section=sectionName(cells.text(sheet,1,0));if(section.isEmpty())throw new IOException(file.getFileName()+" / "+sheet.getSheetName()+" 无法提取分标名称，请填写界面分标名称");}
      String method=methodId(methodCriteria(sheet,cells));matchedMethods.add(method);
      var rules=new LinkedHashMap<String,BigDecimal>(ruleSets.getOrDefault("",Map.of()));
      for(var rule:ruleSets.getOrDefault(method,Map.of()).entrySet()){if(rules.containsKey(rule.getKey()))throw new IOException("同一细则同时配置全局与评分办法规则："+rule.getKey());rules.put(rule.getKey(),rule.getValue());}
      Scope scope=new Scope(section,sheet.getSheetName());
      String previousMethod=scopeMethods.putIfAbsent(scope,method);if(previousMethod!=null&&!previousMethod.equals(method))throw new IOException("同标包不同专家的评分办法不一致："+scope);
      if(rules.isEmpty())continue;scopeRules.put(scope,rules);if(!experts.computeIfAbsent(scope,k->new LinkedHashSet<>()).add(expert))throw new IOException("同标包专家重复导入："+expert+" / "+scope);
      int count=0;for(int r=4;r<=sheet.getLastRowNum();r++){if(cells.text(sheet,r,0).equals("合计")||cells.text(sheet,r,1).equals("合计")){count=r-4;break;}}
      if(count<1)throw new IOException(file.getFileName()+" / "+sheet.getSheetName()+" 未找到评分项后的合计行");
      // Some site exports append a page-number row after the original footer.
      int stride=count+8;
      if(cells.text(sheet,stride,0).matches("第\\s*\\d+\\s*页\\s*[/／]\\s*共\\s*\\d+\\s*页"))stride++;
      var foundItems=new HashSet<String>();boolean anyPage=false;
      for(int base=0;base<=sheet.getLastRowNum();base+=stride){
       context.checkCancelled();Row names=sheet.getRow(base+3);if(names==null){boolean meaningful=false;for(int r=base;r<=sheet.getLastRowNum();r++){Row tail=sheet.getRow(r);if(tail!=null)for(Cell c:tail)if(!cells.value(c).display().isBlank())meaningful=true;}if(meaningful)throw new IOException("分页不完整："+file.getFileName()+" / "+sheet.getSheetName()+" 第"+(base+1)+"行");continue;}
       if(!isCriteriaHeader(cells.text(sheet,base+2,1)))throw new IOException("评分细则表头不符："+file.getFileName()+" / "+sheet.getSheetName()+" 第"+(base+3)+"行B列");
       if(!cells.text(sheet,base+count+4,0).equals("合计")&&!cells.text(sheet,base+count+4,1).equals("合计"))throw new IOException("分页评分项数不一致："+file.getFileName()+" / "+sheet.getSheetName());
       anyPage=true;
       for(int r=base+4;r<base+count+4;r++){
        String criterion=cells.text(sheet,r,1).trim();if(!rules.containsKey(criterion))continue;foundItems.add(criterion);
        Row row=sheet.getRow(r);int last=Math.max(names.getLastCellNum(),row==null?0:row.getLastCellNum());
        for(int col=3;col<last;col++){
         String supplier=cells.text(sheet,base+3,col).trim(),number=cells.numeric(sheet,r,col).trim();String location=file.getFileName()+" / "+sheet.getSheetName()+" / "+(r+1)+"行"+(col+1)+"列";
         if(supplier.isEmpty()){if(!number.isEmpty())throw new IOException(location+" 有分数但缺少供应商名称");continue;}
         if(number.isEmpty())throw new IOException(location+" 缺少客观分");BigDecimal score;try{score=new BigDecimal(number);}catch(NumberFormatException e){throw new IOException(location+" 客观分无效："+number);}
         Key key=new Key(section,sheet.getSheetName(),supplier,criterion);if(scores.computeIfAbsent(key,k->new LinkedHashMap<>()).putIfAbsent(expert,new Score(score.stripTrailingZeros(),location))!=null)throw new IOException(location+" 同一专家/供应商/细则重复，不能覆盖");
        }
       }
      }
      if(!anyPage)throw new IOException("没有完整评分页："+file.getFileName());
      for(String item:rules.keySet())if(!foundItems.contains(item)){outputs.add(List.of(section,sheet.getSheetName(),"",item,"细则未找到",expert+"：未找到完全一致的细则名称",file.getFileName().toString()));warnings.add(new Problem("CRITERION_MISSING",file.getFileName()+" / "+sheet.getSheetName()+" 未找到客观项："+item,null));}
     }
    }
   }
   for(String method:ruleSets.keySet())if(!method.isEmpty()&&!matchedMethods.contains(method))throw new IOException("所选评分办法未匹配当前文件，请重新读取评分项并勾选");
   for(var entry:scores.entrySet()){
    context.checkCancelled();var key=entry.getKey();var values=entry.getValue();String detail=String.join("\n",values.entrySet().stream().map(e->e.getKey()+"："+e.getValue().value().toPlainString()+"分").toList());String sources=String.join("\n",values.values().stream().map(Score::source).toList());
    var expected=experts.get(new Scope(key.section(),key.pack()));var missing=new LinkedHashSet<>(expected);missing.removeAll(values.keySet());if(!missing.isEmpty())add(outputs,key,"专家记录缺失","未找到："+String.join("、",missing),sources);
    if(values.size()<2)add(outputs,key,"专家数量不足","仅1位专家有此项评分，无法核验一致性；"+detail,sources);
    if(values.values().stream().map(Score::value).distinct().count()>1)add(outputs,key,"分数不一致",detail,sources);
    BigDecimal step=scopeRules.get(new Scope(key.section(),key.pack())).get(key.criterion());if(step!=null)for(var value:values.entrySet())if(value.getValue().value().remainder(step).signum()!=0)add(outputs,key,"分值步长不符",value.getKey()+"："+value.getValue().value().toPlainString()+"分，不是"+step.toPlainString()+"的倍数",value.getValue().source());
   }
   if(scores.isEmpty())warnings.add(new Problem("NO_OBJECTIVE_SCORES","没有找到可核验的客观项评分，请核对规则表",null));
   return WorkbookOutput.publish(request,context,"专家客观分核验.xlsx","核验结果",List.of("分标名称","包名称","投标人","客观评分细则","问题类型","说明","来源位置"),outputs,warnings);
  }catch(IOException|IllegalArgumentException e){return Outcome.fail("INPUT_INVALID",e.getMessage());}
 }
 private static void add(List<List<String>> rows,Key k,String type,String detail,String source){rows.add(List.of(k.section(),k.pack(),k.supplier(),k.criterion(),type,detail,source));}
 private static final class Cells{
  final DataFormatter format=new DataFormatter(Locale.ROOT);final FormulaEvaluator evaluator;
  Cells(Workbook w){evaluator=w.getCreationHelper().createFormulaEvaluator();}
  CellValues.Value value(Cell cell)throws IOException{return CellValues.read(cell,format,evaluator);}
  Cell cell(Sheet s,int r,int c){Row row=s.getRow(r);return row==null?null:row.getCell(c);}
  String text(Sheet s,int r,int c)throws IOException{return value(cell(s,r,c)).display();}
  String numeric(Sheet s,int r,int c)throws IOException{return value(cell(s,r,c)).numericText();}
 }
}
