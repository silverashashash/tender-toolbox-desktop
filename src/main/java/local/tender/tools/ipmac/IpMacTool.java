package local.tender.tools.ipmac;
import local.tender.core.*;import local.tender.io.*;
import org.apache.poi.ss.usermodel.*;
import java.nio.file.*;import java.util.*;import java.io.IOException;
public final class IpMacTool implements ToolHandler {
 public static final List<String> FIELDS;
 public static final List<String> HEADERS;
 static{var fields=new ArrayList<String>();for(String kind:List.of("IP","MAC","CPU","硬盘"))for(String type:List.of("技术文件","商务文件","价格文件","资质业绩"))fields.add(type+kind);FIELDS=List.copyOf(fields);var h=new ArrayList<>(List.of("分标名称","包名","轮次","投标人"));h.addAll(fields);HEADERS=List.copyOf(h);}
 private record Key(String section,String pack,String cpu,String disk){}
 private record Device(String supplier,String mac){}
 private static String hardware(String value){String v=value==null?"":value.trim();return Set.of("/","／","-","—").contains(v)?"":v;}
 private static final List<String> TYPES=List.of("技术文件","商务文件","价格文件","资质业绩");
 public ToolId id(){return ToolId.IP_MAC;}
 public Outcome<ToolResult> execute(ToolRequest request,TaskContext context)throws Exception{
  try{
   var paths=request.inputs().get("files");if(paths==null||paths.isEmpty())throw new IOException("请选择IP/MAC对比工作簿");
   var required=new LinkedHashMap<String,List<String>>();required.put("分标名称",List.of("分标名称","标段名称"));required.put("包名",List.of("包名","包名称","分包名称"));required.put("轮次",List.of("轮次"));required.put("投标人",List.of("投标人","投标人名称"));
   var optional=new LinkedHashMap<String,List<String>>();for(String f:FIELDS){
    var aliases=new ArrayList<String>();aliases.add(f);
    if(f.endsWith("IP"))aliases.add(f.substring(0,f.length()-2).replace("资质业绩","资格业绩文件")+"提交IP地址");
    optional.put(f,List.copyOf(aliases));
   }
   var groups=new LinkedHashMap<Key,Map<Device,LinkedHashSet<String>>>();int records=0,values=0;var warnings=new ArrayList<Problem>();var rows=new ArrayList<List<String>>();
   for(Path path:paths){context.progress("读取IP/MAC："+path.getFileName(),0,OptionalLong.empty());
    var original=readOriginal(path);
    for(var entry:(original==null?WorkbookTable.readAll(path,required,optional):original).entrySet()){
     var sheetRows=entry.getValue().rows();
     if(!sheetRows.isEmpty()){
      var missing=FIELDS.stream().filter(f->!sheetRows.get(0).containsKey(f)).toList();
      if(!missing.isEmpty())warnings.add(new Problem("PARTIAL_DEVICE_COVERAGE",path.getFileName()+" / "+entry.getKey()+"：仅核验实际提供的字段；未提供、未核验："+String.join("、",missing),null));
     }
     for(var row:sheetRows){
     context.checkCancelled();String location=path.getFileName()+" / "+entry.getKey()+" 第"+row.get("__row")+"行";
     for(String key:required.keySet())if(row.get(key).isBlank())throw new IOException(location+" 缺少"+key);
     if(FIELDS.stream().noneMatch(row::containsKey))throw new IOException(location+" 未识别设备信息表头，请使用导出的填写模板");records++;
     for(String type:TYPES){
      String cpu=hardware(row.get(type+"CPU")),disk=hardware(row.get(type+"硬盘")),mac=hardware(row.get(type+"MAC")),ip=hardware(row.get(type+"IP"));
      if(cpu.isEmpty()&&disk.isEmpty()&&mac.isEmpty()&&ip.isEmpty())continue;
      String source=type+" / 轮次"+row.get("轮次")+" / "+location;
      if(cpu.isEmpty()||disk.isEmpty()){
       rows.add(List.of(row.get("分标名称"),row.get("包名"),"资料不足，未完成核验",row.get("投标人"),"",cpu,disk,mac,"",source,"","CPU或硬盘信息缺失；不能据此认定无异常"));continue;
      }
      values++;
      var key=new Key(row.get("分标名称"),row.get("包名"),cpu,disk);
      groups.computeIfAbsent(key,k->new LinkedHashMap<>()).computeIfAbsent(new Device(row.get("投标人"),mac),d->new LinkedHashSet<>()).add(source);
     }
    }
    }
   }
   if(records==0)throw new IOException("没有可核验的记录");
   for(var entry:groups.entrySet()){
    var devices=new ArrayList<>(entry.getValue().entrySet());var k=entry.getKey();
    for(int i=0;i<devices.size();i++)for(int j=i+1;j<devices.size();j++){
     context.checkCancelled();var a=devices.get(i);var b=devices.get(j);var x=a.getKey();var y=b.getKey();
     if(x.supplier().equals(y.supplier()))continue;
     if(!x.mac().isEmpty()&&!y.mac().isEmpty()&&!x.mac().equals(y.mac()))continue;
     String status=x.mac().isEmpty()&&y.mac().isEmpty()?"硬件组合一致（双方无MAC）":x.mac().isEmpty()||y.mac().isEmpty()?"待人工核实（单方缺MAC）":"硬件组合一致";
     rows.add(List.of(k.section(),k.pack(),status,x.supplier(),y.supplier(),k.cpu(),k.disk(),x.mac(),y.mac(),String.join("\n",a.getValue()),String.join("\n",b.getValue()),"CPU、硬盘按整格同时相同；跨文件类别/轮次比较；结合原始投标文件复核"));
    }
   }
   if(values==0)warnings.add(new Problem("NO_COMPLETE_HARDWARE","没有CPU及硬盘均完整的记录，未完成硬件组合核验",null));
   return WorkbookOutput.publish(request,context,"IP_MAC核验.xlsx","联合核验",List.of("分标名称","包名","核验结果","投标人甲","投标人乙","相同CPU序列号","相同硬盘序列号","甲方MAC","乙方MAC","甲方文件及来源","乙方文件及来源","说明"),rows,warnings);
  }catch(IOException|IllegalArgumentException e){return Outcome.fail("INPUT_INVALID",e.getMessage());}
 }
 private static Map<String,WorkbookTable.Table> readOriginal(Path path)throws IOException{
  try(var in=Files.newInputStream(path);var workbook=WorkbookFactory.create(in)){
   if(workbook.getNumberOfSheets()==0)throw new IOException("工作簿没有工作表");
   var formatter=new DataFormatter(Locale.ROOT);var evaluator=workbook.getCreationHelper().createFormulaEvaluator();
   var head=workbook.getSheetAt(0).getRow(0);
   if(head==null||head.getLastCellNum()<23||!formatter.formatCellValue(head.getCell(1)).contains("分标")||!formatter.formatCellValue(head.getCell(3)).contains("包")||!formatter.formatCellValue(head.getCell(5)).contains("投标人"))return null;
   var result=new LinkedHashMap<String,WorkbookTable.Table>();
   for(Sheet sheet:workbook){
    Row header=sheet.getRow(0);if(header==null||header.getLastCellNum()<23||!formatter.formatCellValue(header.getCell(1)).contains("分标")||!formatter.formatCellValue(header.getCell(5)).contains("投标人"))throw new IOException(sheet.getSheetName()+" 原版23列表头不符");
    var rows=new ArrayList<Map<String,String>>();for(int i=1;i<=sheet.getLastRowNum();i++){
     Row row=sheet.getRow(i);if(row==null)continue;var values=new LinkedHashMap<String,String>();
     for(int j=0;j<4;j++){int col=new int[]{1,3,4,5}[j];values.put(HEADERS.get(j),CellValues.read(row.getCell(col),formatter,evaluator).display());}
     for(int j=0;j<FIELDS.size();j++)values.put(FIELDS.get(j),CellValues.read(row.getCell(j+7),formatter,evaluator).display());
     if(values.values().stream().allMatch(String::isBlank))continue;values.put("__row",Integer.toString(i+1));rows.add(values);
    }result.put(sheet.getSheetName(),new WorkbookTable.Table(rows,1));
   }return result;
  }
 }

}
