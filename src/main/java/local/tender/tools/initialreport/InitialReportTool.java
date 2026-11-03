package local.tender.tools.initialreport;
import local.tender.core.*;
import local.tender.io.ArtifactOutput;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
public final class InitialReportTool implements ToolHandler {
 public ToolId id(){return ToolId.INITIAL_REPORT;}
 public Outcome<ToolResult> execute(ToolRequest request,TaskContext context)throws Exception{
  try{
   var paths=request.inputs().get("report");if(paths==null||paths.size()!=1)throw new IOException("请选择一份初评工作簿");
   var sections=new LinkedHashMap<String,LinkedHashMap<String,LinkedHashMap<String,LinkedHashSet<String>>>>();
   long packs=0,bidders=0,documents=0;int sectionCount;String dept,leader,members;
   try(var in=Files.newInputStream(paths.get(0));var workbook=WorkbookFactory.create(in)){
    if(workbook.getNumberOfSheets()<3)throw new IOException("初评工作簿需要3张工作表，依次为否决明细、基本情况、组长信息");
    var formatter=new DataFormatter(Locale.ROOT);var evaluator=workbook.getCreationHelper().createFormulaEvaluator();
    Sheet reasons=workbook.getSheetAt(0);for(int r=2;r<=reasons.getLastRowNum();r++){
     context.checkCancelled();String section=text(reasons,r,1,formatter,evaluator),reason=text(reasons,r,2,formatter,evaluator),company=text(reasons,r,3,formatter,evaluator),pack=text(reasons,r,4,formatter,evaluator);
     if(section.isBlank()&&reason.isBlank()&&company.isBlank()&&pack.isBlank())continue;
     if(section.isBlank()||reason.isBlank()||company.isBlank()||pack.isBlank())throw new IOException("否决明细第"+(r+1)+"行B至E列存在空值");
     sections.computeIfAbsent(section,k->new LinkedHashMap<>()).computeIfAbsent(reason,k->new LinkedHashMap<>()).computeIfAbsent(company,k->new LinkedHashSet<>()).add(pack);
    }
    Sheet base=workbook.getSheetAt(1);int start=-1,end=-1;
    for(int r=0;r<=base.getLastRowNum();r++){String value=text(base,r,0,formatter,evaluator).trim();if(value.equals("一、基本情况"))start=r;if(value.equals("二、澄清事项")){end=r;break;}}
    if(start<0||end<start+2)throw new IOException("第2张表缺少或错置“一、基本情况”“二、澄清事项”标记");
    sectionCount=0;
    for(int r=start+2;r<end;r++){
     String code=text(base,r,0,formatter,evaluator).trim(),name=text(base,r,1,formatter,evaluator).trim();
     String packCount=numeric(base,r,2,formatter,evaluator),bidderCount=numeric(base,r,3,formatter,evaluator),documentCount=numeric(base,r,4,formatter,evaluator);
     if(code.isBlank()&&name.isBlank()){
      if(packCount.isBlank()&&bidderCount.isBlank()&&documentCount.isBlank())continue;
      throw new IOException("第2张表第"+(r+1)+"行有数量但缺少分标编号和分标名称");
     }
     sectionCount++;
     packs=Math.addExact(packs,count(packCount));bidders=Math.addExact(bidders,count(bidderCount));documents=Math.addExact(documents,count(documentCount));
    }
    Sheet names=workbook.getSheetAt(2);String group=text(names,1,0,formatter,evaluator);String people=text(names,10,0,formatter,evaluator);
    dept=request.parameters().getOrDefault("department","").trim();if(dept.isEmpty()){if(group.length()<5)throw new IOException("第3张表A2缺少组别说明，请填写组别参数");dept=group.substring(group.length()-5,group.length()-2);}
    int marker=people.indexOf("组长："),separator=people.indexOf("、",Math.max(0,marker));
    if(marker<0||separator<=marker+3||separator==people.length()-1)throw new IOException("第3张表A11应包含“组长：姓名、姓名”");leader=people.substring(marker+3,separator).trim();members=people.substring(separator+1).trim();
   }
   if(!dept.endsWith("组"))dept+="组";
   var paragraphs=new ArrayList<String>();paragraphs.add("各位领导好，我们是"+dept+"的组长，我是"+leader+"，这位是"+members+"。下面由我向评委会汇报"+dept+"初评情况。");
   paragraphs.add("本组所评共"+sectionCount+"个标段、"+packs+"个包，在投标截止时间前共"+bidders+"个投标人成功递交了"+documents+"份投标文件，各标段基本情况见输入表格。");
   String clarification=request.parameters().getOrDefault("clarification","").trim();var warnings=new ArrayList<Problem>();
   if(clarification.isEmpty()){clarification="【待核实填写】";warnings.add(new Problem("CLARIFICATION_REQUIRED","原模板固定写“无”，新版未替你判断澄清事项；请填写后使用正式报告",null));}
   paragraphs.add("澄清事项："+clarification+"。");paragraphs.add("建议否决投标事项：");
   if(sections.isEmpty())paragraphs.add("输入明细中未列出否决事项。");
   boolean simplify=Boolean.parseBoolean(request.parameters().getOrDefault("simplifyNames","true"));
   var suffixes=InitialReportFormatting.suffixes(request.parameters().getOrDefault("companySuffixes",InitialReportFormatting.DEFAULT_SUFFIXES));
   var allCompanies=new LinkedHashSet<String>();for(var section:sections.values())for(var reason:section.values())allCompanies.addAll(reason.keySet());
   var displayNames=InitialReportFormatting.names(allCompanies,simplify?suffixes:List.of());
   for(var section:sections.entrySet())for(var reason:section.getValue().entrySet()){
    var parts=new ArrayList<String>();for(var company:reason.getValue().entrySet())parts.add(displayNames.get(company.getKey())+"的"+InitialReportFormatting.packs(company.getValue()));
    paragraphs.add(section.getKey()+"标段，因"+reason.getKey()+"而建议否决的有："+String.join("；",parts)+"。");
   }
   return ArtifactOutput.publish(request,context,"初评汇报单.docx",warnings,path->{
    try(var doc=new XWPFDocument()){
     var title=doc.createParagraph();title.setAlignment(ParagraphAlignment.CENTER);var run=title.createRun();run.setText("初评汇报单");run.setBold(true);run.setFontSize(18);run.setFontFamily("宋体");
     for(String paragraph:paragraphs){context.checkCancelled();var p=doc.createParagraph();p.setIndentationFirstLine(560);p.setSpacingAfter(160);var r=p.createRun();r.setFontFamily("宋体");r.setFontSize(14);r.setText(paragraph);}
     try(var out=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW)){doc.write(out);}
    }
   });
  }catch(IOException|IllegalArgumentException|ArithmeticException e){return Outcome.fail("INPUT_INVALID",e.getMessage());}
 }
 private static long count(String value)throws IOException{if(value.isBlank())return 0;try{long n=new BigDecimal(value).longValueExact();if(n<0)throw new ArithmeticException();return n;}catch(NumberFormatException|ArithmeticException e){throw new IOException("数量应为非负整数："+value);}}
 private static String numeric(Sheet sheet,int r,int c,DataFormatter formatter,FormulaEvaluator evaluator)throws IOException{Row row=sheet.getRow(r);return local.tender.io.CellValues.read(row==null?null:row.getCell(c),formatter,evaluator).numericText();}
 private static String text(Sheet sheet,int r,int c,DataFormatter formatter,FormulaEvaluator evaluator)throws IOException{Row row=sheet.getRow(r);Cell cell=row==null?null:row.getCell(c);if(cell==null)return "";if(cell.getCellType()==CellType.ERROR||(cell.getCellType()==CellType.FORMULA&&evaluator.evaluate(cell).getCellType()==CellType.ERROR))throw new IOException(sheet.getSheetName()+"!"+cell.getAddress()+" 为错误单元格");return formatter.formatCellValue(cell,evaluator);}
}
