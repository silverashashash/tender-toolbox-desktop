package local.tender.tools.warningform;
import local.tender.core.*;import local.tender.io.*;import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import java.nio.file.*;import java.util.*;import java.util.zip.*;import org.apache.poi.xwpf.usermodel.XWPFDocument;import static org.junit.jupiter.api.Assertions.*;
class WarningFormToolTest{
 @TempDir Path root;
 @Test void generatesBothGroupsUsingOriginalTemplateAndMergesPackages()throws Exception{
  Path risk=root.resolve("风险.xlsx"),groups=root.resolve("分组.xlsx"),bids=root.resolve("开标.xlsx");var rs=new ArrayList<List<String>>();var gs=new ArrayList<List<String>>();var bs=new ArrayList<List<String>>();
  for(String pack:List.of("包2","包1")){gs.add(List.of("标",pack,"技术1","商务1"));for(String supplier:List.of("甲","乙")){rs.add(List.of("标",pack,supplier,"地址","电话","共同邮箱","否","否","是"));bs.add(List.of("项目","001","标","0001",pack,supplier));}}
  WorkbookTable.write(risk,"风险",WarningFormTool.RISK_HEADERS,rs);WorkbookTable.write(groups,"分组",WarningFormTool.GROUP_HEADERS,gs);WorkbookTable.write(bids,"开标",WarningFormTool.BID_HEADERS,bs);
  var result=new WarningFormTool().execute(new ToolRequest(ToolId.WARNING_FORM,Map.of("risk",List.of(risk),"groups",List.of(groups),"bids",List.of(bids)),Map.of(),root.resolve("结果"),Optional.empty()),new TaskContext(root));var success=assertInstanceOf(Outcome.Success.class,result);
  try(var zip=new ZipFile(((ToolResult)success.value()).outputs().get(0).toFile())){
   var entries=zip.stream().filter(e->e.getName().endsWith(".docx")).toList();assertEquals(2,entries.size());
   assertEquals(Set.of("技术1/预警事项确认单(邮箱相同)-标-包1、包2-乙、甲-001技术1组.docx","商务1/预警事项确认单(邮箱相同)-标-包1、包2-乙、甲-001商务1组.docx"),entries.stream().map(ZipEntry::getName).collect(java.util.stream.Collectors.toSet()));
   for(var entry:entries)try(var doc=new XWPFDocument(zip.getInputStream(entry))){String text=String.join("\n",doc.getParagraphs().stream().map(p->p.getText()).toList());assertTrue(text.contains("包1、包2"));assertTrue(text.contains("共同邮箱"));assertTrue(text.contains("乙、甲"));assertTrue(text.contains("1.同一标包中，未发现该两家应答人应答文件异常一致。"));assertFalse(text.contains("{{"));assertTrue(text.contains("经评委会核查，“标”标段（分标编号0001）包1、包2"));assertTrue(text.contains("项目（001"));
    try(var original=new XWPFDocument(WarningFormTool.class.getResourceAsStream("/templates/预警事项确认单.docx"))){assertEquals(original.getParagraphs().size(),doc.getParagraphs().size());assertEquals(original.getDocument().getBody().getSectPr().xmlText(),doc.getDocument().getBody().getSectPr().xmlText());for(int i=0;i<doc.getParagraphs().size();i++){var expected=original.getParagraphs().get(i).getCTP().getPPr();var actual=doc.getParagraphs().get(i).getCTP().getPPr();assertEquals(expected==null?null:expected.xmlText(),actual==null?null:actual.xmlText());}}}
  }
 }
 @Test void combinesThreeSuppliersAndKeepsDifferentPackageCohortsSeparate()throws Exception{
  Path risk=root.resolve("risk.xlsx"),groups=root.resolve("groups.xlsx"),bids=root.resolve("bids.xlsx");
  var rs=new ArrayList<List<String>>();var gs=new ArrayList<List<String>>();var bs=new ArrayList<List<String>>();
  for(String pack:List.of("包1","包2","包3")){
   gs.add(List.of("标",pack,"技术1","商务1"));
   for(String supplier:pack.equals("包3")?List.of("甲","乙"):List.of("甲","乙","丙")){
    var row=List.of("标",pack,supplier,supplier+"独立地址",supplier+"独立电话","same@example.com","否","否","是");rs.add(row);rs.add(row);
    bs.add(List.of("项目","001","标","0001",pack,supplier));
   }
  }
  WorkbookTable.write(risk,"风险",WarningFormTool.RISK_HEADERS,rs);WorkbookTable.write(groups,"分组",WarningFormTool.GROUP_HEADERS,gs);WorkbookTable.write(bids,"开标",WarningFormTool.BID_HEADERS,bs);
  var result=new WarningFormTool().execute(new ToolRequest(ToolId.WARNING_FORM,Map.of("risk",List.of(risk),"groups",List.of(groups),"bids",List.of(bids)),Map.of(),root.resolve("out"),Optional.empty()),new TaskContext(root));
  var success=assertInstanceOf(Outcome.Success.class,result);
  try(var zip=new ZipFile(((ToolResult)success.value()).outputs().get(0).toFile())){
   var entries=zip.stream().filter(e->e.getName().endsWith(".docx")).toList();assertEquals(4,entries.size());int triples=0,pairs=0;
   for(var entry:entries)try(var doc=new XWPFDocument(zip.getInputStream(entry))){String text=String.join("\n",doc.getParagraphs().stream().map(p->p.getText()).toList());
    if(text.contains("3家投标人")){triples++;assertTrue(text.contains("包1、包2"));assertFalse(text.contains("包3"));assertTrue(text.contains("该3家应答人"));assertFalse(text.contains("两家"));assertTrue(text.contains("丙、乙、甲"));}
    else{pairs++;assertTrue(text.contains("2家投标人"));assertTrue(text.contains("包3"));assertFalse(text.contains("丙"));}
   }assertEquals(2,triples);assertEquals(2,pairs);
  }
 }
 @Test void acceptsActualTwoRowMergedRiskHeaders()throws Exception{
  Path risk=root.resolve("merged.xlsx"),groups=root.resolve("groups.xlsx"),bids=root.resolve("bids.xlsx");
  try(var wb=new org.apache.poi.xssf.usermodel.XSSFWorkbook()){
   var sheet=wb.createSheet("围串标风险提示表");var top=sheet.createRow(0);var lower=sheet.createRow(1);
   var headers=List.of("分标名称","分包编号","投标厂商名称","注册地址","企业联系方式","邮箱");
   for(int c=0;c<6;c++){top.createCell(c).setCellValue(headers.get(c));sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0,1,c,c));}
   top.createCell(6).setCellValue("围串标风险项(三级)");sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0,0,6,8));
   for(int c=6;c<9;c++)lower.createCell(c).setCellValue(WarningFormTool.RISK_HEADERS.get(c));
   for(int i=0;i<2;i++){var row=sheet.createRow(i+2);var values=List.of("标","包1",i==0?"甲":"乙","地址","电话","共同邮箱","否","否","是");for(int c=0;c<9;c++)row.createCell(c).setCellValue(values.get(c));}
   try(var out=Files.newOutputStream(risk)){wb.write(out);}
  }
  WorkbookTable.write(groups,"组",WarningFormTool.GROUP_HEADERS,List.of(List.of("标","包1","技术1","商务1")));
  // Real bid sheets contain both columns: 分包编号 must NOT become a global alias for 分包名称.
  var bh=new ArrayList<>(WarningFormTool.BID_HEADERS);bh.add("分包编号");
  WorkbookTable.write(bids,"开标",bh,List.of(List.of("项目","1","标","01","包1","甲","100"),List.of("项目","1","标","01","包1","乙","100")));
  var result=new WarningFormTool().execute(new ToolRequest(ToolId.WARNING_FORM,Map.of("risk",List.of(risk),"groups",List.of(groups),"bids",List.of(bids)),Map.of(),root.resolve("结果"),Optional.empty()),new TaskContext(root));
  var success=assertInstanceOf(Outcome.Success.class,result);
  try(var zip=new ZipFile(((ToolResult)success.value()).outputs().get(0).toFile())){assertEquals(2,zip.stream().filter(e->e.getName().endsWith(".docx")).count());}
 }
 @Test void missingGroupIsReportedRatherThanSilentlyDroppingWarnings()throws Exception{
  Path risk=root.resolve("r.xlsx"),groups=root.resolve("g.xlsx"),bids=root.resolve("b.xlsx");
  WorkbookTable.write(risk,"风险",WarningFormTool.RISK_HEADERS,List.of(List.of("标","包1","甲","/","/","same","否","否","是"),List.of("标","包1","乙","/","/","same","否","否","是")));
  WorkbookTable.write(groups,"分组",WarningFormTool.GROUP_HEADERS,List.of(List.of("标","包2","技术1","商务1")));
  WorkbookTable.write(bids,"开标",WarningFormTool.BID_HEADERS,List.of(List.of("项目","1","标","01","包1","甲"),List.of("项目","1","标","01","包1","乙")));
  assertInstanceOf(Outcome.Failure.class,new WarningFormTool().execute(new ToolRequest(ToolId.WARNING_FORM,Map.of("risk",List.of(risk),"groups",List.of(groups),"bids",List.of(bids)),Map.of(),root.resolve("结果"),Optional.empty()),new TaskContext(root)));
 }
}
