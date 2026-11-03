package local.tender.tools.warningform;
import local.tender.core.*;import local.tender.io.*;import org.apache.poi.xwpf.usermodel.*;
import java.io.*;import java.nio.file.*;import java.time.LocalDate;import java.util.*;import java.util.zip.*;
public final class WarningFormTool implements ToolHandler{
 public static final List<String> RISK_HEADERS=List.of("分标名称","分包名称","投标人","注册地址","企业联系方式","邮箱","注册地址是否相同","企业联系方式是否相同","邮箱是否相同");
 public static final List<String> GROUP_HEADERS=List.of("分标名称","分包名称","技术组","商务组");
 public static final List<String> BID_HEADERS=List.of("采购项目名称","采购项目编号","分标名称","分标编号","分包名称","投标人名称");
 private record Pack(String section,String pack){}
 private record Bid(Pack pack,String supplier){}
 private record Project(String name,String id,String sectionId){}
 private record Risk(String supplier,List<String> values,List<Boolean> flags){}
 private record Signature(List<Boolean> flags,List<String> values){}
 private record Form(Project project,String section,String group,List<String> suppliers,Signature signature){}
 public ToolId id(){return ToolId.WARNING_FORM;}
 public Outcome<ToolResult> execute(ToolRequest request,TaskContext context)throws Exception{
  try{
   var risks=readRisk(one(request,"risk"));var grouping=read(one(request,"groups"),GROUP_HEADERS);var bids=read(one(request,"bids"),BID_HEADERS);
   var projects=new LinkedHashMap<Pack,Project>();var participants=new HashSet<Bid>();var groups=new LinkedHashMap<Pack,List<String>>();
   for(var row:bids.rows()){
    require(row,BID_HEADERS);var pack=new Pack(row.get("分标名称"),row.get("分包名称"));var project=new Project(row.get("采购项目名称"),row.get("采购项目编号"),row.get("分标编号"));
    var old=projects.putIfAbsent(pack,project);if(old!=null&&!old.equals(project))throw new IOException("同标包项目或编号冲突："+pack);participants.add(new Bid(pack,row.get("投标人名称")));
   }
   for(var row:grouping.rows()){
    require(row,GROUP_HEADERS);var pack=new Pack(row.get("分标名称"),row.get("分包名称"));var value=List.copyOf(new LinkedHashSet<>(List.of(row.get("技术组"),row.get("商务组"))));
    var old=groups.putIfAbsent(pack,value);if(old!=null&&!old.equals(value))throw new IOException("同标包评审组冲突："+pack);
   }
   var byPack=new LinkedHashMap<Pack,List<Risk>>();
   for(var row:risks.rows()){
    context.checkCancelled();require(row,List.of("分标名称","分包名称","投标人"));var flags=new ArrayList<Boolean>();var values=new ArrayList<String>();
    for(int i=3;i<6;i++){
     String flag=row.get(RISK_HEADERS.get(i+3));if(!Set.of("是","否").contains(flag))throw new IOException("风险表第"+row.get("__row")+"行：是否相同须填写是或否");boolean enabled=flag.equals("是");String value=row.get(RISK_HEADERS.get(i));
     if(enabled&&(value.isBlank()||Set.of("/","／","-","—").contains(value)))throw new IOException("风险表第"+row.get("__row")+"行已标记相同但缺少"+RISK_HEADERS.get(i));flags.add(enabled);values.add(value);
    }
    if(!flags.contains(true))continue;
    var pack=new Pack(row.get("分标名称"),row.get("分包名称"));String supplier=row.get("投标人");
    if(!participants.contains(new Bid(pack,supplier)))throw new IOException("开标表未找到风险记录对应的投标人："+pack+" / "+supplier);
    if(!groups.containsKey(pack))throw new IOException("缺少标包分组："+pack);
    byPack.computeIfAbsent(pack,k->new ArrayList<>()).add(new Risk(supplier,List.copyOf(values),List.copyOf(flags)));
   }
   var forms=new LinkedHashMap<Form,Set<String>>();
   for(var entry:byPack.entrySet()){
    var pack=entry.getKey();var cohorts=new LinkedHashMap<Signature,SortedSet<String>>();
    for(var risk:entry.getValue()){
     context.checkCancelled();var matchedValues=new ArrayList<String>();
     for(int f=0;f<3;f++)matchedValues.add(risk.flags.get(f)?risk.values.get(f):"");
     var signature=new Signature(risk.flags,List.copyOf(matchedValues));
     cohorts.computeIfAbsent(signature,k->new TreeSet<>()).add(risk.supplier);
    }
    for(var cohort:cohorts.entrySet()){
     if(cohort.getValue().size()<2)continue;
     for(String group:groups.get(pack)){
      var form=new Form(projects.get(pack),pack.section,group,List.copyOf(cohort.getValue()),cohort.getKey());
      forms.computeIfAbsent(form,k->new TreeSet<>(WarningFormTool::comparePacks)).add(pack.pack);
     }
    }
   }
   String date=request.parameters().getOrDefault("date","").trim();if(date.isEmpty())date=LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日"));final String documentDate=date;
   var warnings=new ArrayList<Problem>();if(forms.isEmpty())warnings.add(new Problem("NO_MATCHING_RISK","未找到标记组合一致且具体信息相同的不同投标人；不代表已经人工确认无异常",null));
   return ArtifactOutput.publish(request,context,"预警事项确认单.zip",warnings,target->{
    try(var zip=new ZipOutputStream(Files.newOutputStream(target,StandardOpenOption.CREATE_NEW))){var usedNames=new HashSet<String>();
     for(var entry:forms.entrySet()){
      context.checkCancelled();var f=entry.getKey();String packs=String.join("、",entry.getValue());
      String base=filename(f,packs),path=safe(f.group)+"/"+base;int duplicate=1;
      String name=path+".docx";while(!usedNames.add(name))name=path+"("+(++duplicate)+").docx";
      zip.putNextEntry(new ZipEntry(name));
      try(var doc=document(f,packs,documentDate)){doc.write(zip);}zip.closeEntry();
     }
     zip.putNextEntry(new ZipEntry("使用说明.txt"));zip.write(("共生成"+forms.size()+"份确认单。按风险表中相同的预警标记组合及对应值匹配同标包的不同投标人，同一事项的企业合并为一单，按技术/商务组分别生成；仅企业名单完全一致时归并包号。使用原版Word模板及固定复核文字，签字前请专家核对实际情况。\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
    }
   });
  }catch(IOException|IllegalArgumentException e){return Outcome.fail("INPUT_INVALID",e.getMessage());}
 }
 private static String filename(Form form,String packs){
  var labels=List.of("注册地址相同","联系方式相同","邮箱相同");var reasons=new ArrayList<String>();
  for(int i=0;i<labels.size();i++)if(form.signature.flags.get(i))reasons.add(labels.get(i));
  String group=form.group.endsWith("组")?form.group:form.group+"组";
  return safe("预警事项确认单("+String.join("、",reasons)+")-"+form.section+"-"+packs+"-"+String.join("、",form.suppliers)+"-"+form.project.id+group);
 }
 private static int comparePacks(String a,String b){try{int result=new java.math.BigInteger(a.replaceFirst("^包","")).compareTo(new java.math.BigInteger(b.replaceFirst("^包","")));if(result!=0)return result;}catch(NumberFormatException ignored){}return a.compareTo(b);}
 private static String safe(String value){return value.replaceAll("[\\\\/:*?\"<>|]","_").replace("..","_");}
 private static Path one(ToolRequest r,String key){var paths=r.inputs().get(key);if(paths==null||paths.size()!=1)throw new IllegalArgumentException("请选择文件："+key);return paths.get(0);}
 private static void require(Map<String,String> row,List<String> fields)throws IOException{for(String field:fields)if(row.get(field).isBlank())throw new IOException("第"+row.get("__row")+"行缺少"+field);}
 static WorkbookTable.Table readRisk(Path file)throws IOException{
  var columns=new LinkedHashMap<String,List<String>>();for(String h:RISK_HEADERS)columns.put(h,switch(h){
   case "分包名称"->List.of(h,"包名","包名称","分包编号");
   case "投标人"->List.of(h,"投标人名称","供应商名称","投标厂商名称");
   default->List.of(h);
  });return WorkbookTable.read(file,columns);
 }
 private static WorkbookTable.Table read(Path file,List<String> headers)throws IOException{
  var columns=new LinkedHashMap<String,List<String>>();for(String h:headers)columns.put(h,switch(h){case "分包名称"->List.of(h,"包名","包名称");case "投标人"->List.of(h,"投标人名称","供应商名称");case "技术组"->List.of(h,"技术评审组");case "商务组"->List.of(h,"商务评审组");default->List.of(h);});return WorkbookTable.read(file,columns);
 }
 private static XWPFDocument document(Form form,String packs,String date)throws IOException{
  var reasons=new ArrayList<String>();for(int f=0;f<3;f++)if(form.signature.flags.get(f))reasons.add(RISK_HEADERS.get(f+3)+"相同（"+form.signature.values.get(f)+"）");
  var values=Map.of("prjName",form.project.name,"prjNo",form.project.id,"group",form.group,"lableName",form.section,"labelNo",form.project.sectionId,"packageName",packs,"corpNum",Integer.toString(form.suppliers.size()),"corporateNames",String.join("、",form.suppliers),"warnMatters",String.join("；",reasons),"date",date);
  try(var input=WarningFormTool.class.getResourceAsStream("/templates/预警事项确认单.docx")){
   if(input==null)throw new IOException("缺少原版预警事项确认单模板");
   var doc=new XWPFDocument(input);
   // The bundled original has complete placeholders in individual text nodes.
   // Replace only text, retaining runs, paragraph properties and page layout.
   for(var paragraph:doc.getParagraphs())for(var run:paragraph.getRuns())for(var text:run.getCTR().getTList()){
    var matcher=java.util.regex.Pattern.compile("\\{\\{([A-Za-z]+)\\}\\}").matcher(form.suppliers.size()==2?text.getStringValue():text.getStringValue().replace("该两家应答人","该"+form.suppliers.size()+"家应答人"));
    var rendered=new StringBuffer();while(matcher.find()){
     String value=values.get(matcher.group(1));if(value==null){doc.close();throw new IOException("未知模板字段："+matcher.group(1));}
     matcher.appendReplacement(rendered,java.util.regex.Matcher.quoteReplacement(value));
    }matcher.appendTail(rendered);text.setStringValue(rendered.toString());
   }
   return doc;
  }
 }
}
