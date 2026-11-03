package local.tender.tools.rejection;
import local.tender.core.*;
import local.tender.io.*;
import org.apache.poi.xwpf.usermodel.*;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
public final class RejectionTool implements ToolHandler {
 public ToolId id(){return ToolId.REJECTION_NOTICE;}
 public Outcome<ToolResult> execute(ToolRequest request,TaskContext context)throws Exception{
  try {
   Path source=one(request,"excluded"),ordering=one(request,"sectionOrder");
   var records=WorkbookTable.read(source,Map.of("分标名称",List.of("分标名称"),"投标人",List.of("投标人"),"否决模板",List.of("否决模板")));
   var orders=WorkbookTable.read(ordering,Map.of("序号",List.of("序号"),"分标名称",List.of("分标名称")));
   var orderMap=new LinkedHashMap<String,Integer>();for(var row:orders.rows()){
    String name=row.get("分标名称");if(name.isBlank())throw new IOException("分标顺序表存在空分标名称");
    int value=new java.math.BigDecimal(row.get("序号")).intValueExact();if(value<1)throw new IOException("分标序号必须是正整数");
    Integer previous=orderMap.putIfAbsent(name,value);if(previous!=null&&previous!=value)throw new IOException("同一分标存在不同序号："+name);
   }
   var groups=new LinkedHashMap<String,LinkedHashMap<String,List<String>>>();
   for(var row:records.rows()){
    context.checkCancelled();for(String key:List.of("分标名称","投标人","否决模板"))if(row.get(key).isBlank())throw new IOException("否决表第"+row.get("__row")+"行缺少"+key);
    groups.computeIfAbsent(row.get("分标名称"),k->new LinkedHashMap<>()).computeIfAbsent(row.get("否决模板"),k->new ArrayList<>()).add(row.get("投标人"));
   }
   if(groups.isEmpty())throw new IOException("否决表没有数据");
   var sections=new ArrayList<>(groups.keySet());sections.sort(Comparator.comparingInt(s->orderMap.getOrDefault(s,Integer.MAX_VALUE)));
   var warnings=new ArrayList<Problem>();for(String section:sections)if(!orderMap.containsKey(section))warnings.add(new Problem("SECTION_ORDER_MISSING","未提供顺序的分标排在末尾："+section,null));
   return ArtifactOutput.publish(request,context,"否决事项公示.docx",warnings,path->{
    try(var doc=new XWPFDocument()){
     var sect=doc.getDocument().getBody().addNewSectPr();var size=sect.addNewPgSz();size.setW(BigInteger.valueOf(11906));size.setH(BigInteger.valueOf(16838));var margin=sect.addNewPgMar();margin.setLeft(BigInteger.valueOf(1440));margin.setRight(BigInteger.valueOf(1440));margin.setTop(BigInteger.valueOf(1440));margin.setBottom(BigInteger.valueOf(1440));
     var title=doc.createParagraph();title.setStyle("Title");title.setAlignment(ParagraphAlignment.CENTER);text(title,"否决事项公示",16,true);
     int index=0;for(String section:sections){
      context.checkCancelled();var heading=doc.createParagraph();heading.getCTP().addNewPPr();heading.setKeepNext(true);text(heading,++index+". "+section,12,true);
      var reasons=groups.get(section);var table=doc.createTable(reasons.size()+1,4);table.setWidth(9000);table.getRow(0).setRepeatHeader(true);
      String[] headers={"序号","未进入详评原因","未实质响应的投标人数量","未实质响应的投标文件份数"};int[] widths={700,4500,1900,1900};
      for(int c=0;c<4;c++)cell(table.getRow(0).getCell(c),headers[c],widths[c],true);
      int row=0;for(var reason:reasons.entrySet()){
       var r=table.getRow(++row);String[] values={Integer.toString(row),reason.getKey(),Long.toString(reason.getValue().stream().distinct().count()),Integer.toString(reason.getValue().size())};
       for(int c=0;c<4;c++)cell(r.getCell(c),values[c],widths[c],false);
      }
      doc.createParagraph();
     }
     try(var out=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW)){doc.write(out);}
    }
   });
  }catch(IOException|IllegalArgumentException|ArithmeticException e){return Outcome.fail("INPUT_INVALID",e.getMessage());}
 }
 private static void text(XWPFParagraph p,String value,int size,boolean bold){var run=p.createRun();run.setFontFamily("宋体");run.setFontSize(size);run.setBold(bold);run.setText(value);p.setSpacingAfter(120);}
 private static void cell(XWPFTableCell cell,String value,int width,boolean bold){cell.setWidth(Integer.toString(width));cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);var p=cell.getParagraphs().get(0);p.setAlignment(ParagraphAlignment.CENTER);text(p,value,11,bold);}
 private static Path one(ToolRequest r,String key){var paths=r.inputs().get(key);if(paths==null||paths.size()!=1)throw new IllegalArgumentException("请选择一份文件："+key);return paths.get(0);}
}
