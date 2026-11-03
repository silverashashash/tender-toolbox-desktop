package local.tender.tools.economicrank;
import local.tender.core.*;
import local.tender.io.WorkbookOutput;
import org.apache.poi.ss.usermodel.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Offline reconstruction of EconomicRank.economicRank and its sheet listener. */
public final class EconomicRankTool implements ToolHandler {
 private record Candidate(String supplier,BigDecimal technical,BigDecimal business,BigDecimal priceScore,BigDecimal total,int originalRank,BigDecimal bid){}
 private static final Pattern SECTION=Pattern.compile("设备/材料名称[:：]\\s*(.*?)\\s*日期[:：]");
 public ToolId id(){return ToolId.ECONOMIC_RANK;}
 public Outcome<ToolResult> execute(ToolRequest request,TaskContext context)throws Exception {
  try {
   var files=request.inputs().get("files");if(files==null||files.isEmpty())return Outcome.fail("INPUT_REQUIRED","请选择经济排序表");
   var output=new ArrayList<List<String>>();int examined=0;
   for(Path file:files){
    context.checkCancelled();context.progress("核验："+file.getFileName(),0,OptionalLong.empty());
    try(var in=Files.newInputStream(file);var workbook=WorkbookFactory.create(in)){
     var formatter=new DataFormatter(Locale.ROOT);var evaluator=workbook.getCreationHelper().createFormulaEvaluator();
     for(Sheet sheet:workbook){
      context.checkCancelled();String location=file.getFileName()+" / "+sheet.getSheetName();
      var metadata=SECTION.matcher(value(sheet,3,0,formatter,evaluator));
      if(!metadata.find()||metadata.group(1).isBlank())throw new IOException(location+" 第4行缺少设备/材料名称及日期，无法确认分标");
      String section=metadata.group(1),pack=sheet.getSheetName();
      for(var header:Map.of(4,"投标人",5,"技术",6,"商务",7,"价格",8,"总分",9,"排序").entrySet()){
       if(!header.getValue().equals(value(sheet,5,header.getKey(),formatter,evaluator).trim()))throw new IOException(location+" 第6行表头与经济排序表格式不符："+header.getValue());
      }
      if(!value(sheet,5,11,formatter,evaluator).contains("投标总价"))throw new IOException(location+" 缺少投标总价表头");
      var candidates=new ArrayList<Candidate>();var suppliers=new HashSet<String>();
      for(int r=6;r<=sheet.getLastRowNum();r++){
       context.checkCancelled();if(value(sheet,r,0,formatter,evaluator).equals("无数据"))continue;
       String supplier=value(sheet,r,4,formatter,evaluator);
       boolean blank=true;for(int c=0;c<14;c++)if(!value(sheet,r,c,formatter,evaluator).isBlank())blank=false;
       if(blank)continue;
       if(supplier.isBlank()||!suppliers.add(supplier))throw new IOException(location+" 第"+(r+1)+"行投标人为空或重复，无法确定排序记录");
       BigDecimal tech=number(sheet,r,5,formatter,evaluator),biz=number(sheet,r,6,formatter,evaluator),price=number(sheet,r,7,formatter,evaluator),total=number(sheet,r,8,formatter,evaluator),bid=number(sheet,r,11,formatter,evaluator);
       int rank=number(sheet,r,9,formatter,evaluator).intValueExact();if(rank<1)throw new IOException(location+" 第"+(r+1)+"行排序必须为正整数");
       candidates.add(new Candidate(supplier,tech,biz,price,total,rank,bid));examined++;
      }
      boolean zero=false;
      for(var c:candidates){
       var reasons=new ArrayList<String>();if(c.technical().signum()==0)reasons.add("技术分为0");if(c.business().signum()==0)reasons.add("商务分为0");if(c.priceScore().signum()==0)reasons.add("价格分为0");
       if(!reasons.isEmpty()){zero=true;output.add(List.of(section,pack,c.supplier(),Integer.toString(c.originalRank()),"",String.join("；",reasons)));}
      }
      if(zero)continue;
      candidates.sort(Comparator.comparing(Candidate::total,Comparator.reverseOrder()).thenComparing(Candidate::technical,Comparator.reverseOrder()).thenComparing(Candidate::bid).thenComparing(Candidate::business,Comparator.reverseOrder()));
      for(int i=0;i<candidates.size();i++){var c=candidates.get(i);if(c.originalRank()!=i+1)output.add(List.of(section,pack,c.supplier(),Integer.toString(c.originalRank()),Integer.toString(i+1),"排序不一致"));}
     }
    }
   }
   if(examined==0)return Outcome.fail("INPUT_EMPTY","未找到可核验的供应商排序记录");
   return WorkbookOutput.publish(request,context,"经济排序核验.xlsx","核验结果",List.of("分标名称","包名称","投标人","原排序","核验排序","原因"),output,List.of());
  }catch(IOException|IllegalArgumentException|ArithmeticException e){return Outcome.fail("INPUT_INVALID",e.getMessage());}
 }
 private static String value(Sheet sheet,int row,int column,DataFormatter formatter,FormulaEvaluator evaluator)throws IOException {
  Row r=sheet.getRow(row);Cell cell=r==null?null:r.getCell(column);if(cell==null)return "";
  if(cell.getCellType()==CellType.ERROR||(cell.getCellType()==CellType.FORMULA&&evaluator.evaluate(cell).getCellType()==CellType.ERROR))throw new IOException(sheet.getSheetName()+"!"+cell.getAddress()+" 是错误单元格");
  return formatter.formatCellValue(cell,evaluator);
 }
 private static BigDecimal number(Sheet sheet,int row,int column,DataFormatter formatter,FormulaEvaluator evaluator)throws IOException {
  Row source=sheet.getRow(row);String text=local.tender.io.CellValues.read(source==null?null:source.getCell(column),formatter,evaluator).numericText().trim();
  if(!text.matches("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?%?"))throw new IOException(sheet.getSheetName()+" 第"+(row+1)+"行第"+(column+1)+"列不是有效的非负数："+text);
  return new BigDecimal(text.replace(",","").replace("%",""));
 }
}
