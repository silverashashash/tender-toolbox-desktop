package local.tender.ui;
import local.tender.core.*;
import local.tender.io.WorkbookTable;
import local.tender.tools.performance.PerformanceTool;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.nio.file.*;
import java.util.*;

public record ToolDemos(Map<String,List<Path>> inputs,Map<String,String> parameters,Path output,String expected) {
 public static ToolDemos create(ToolId id,Path parent)throws Exception{
  Files.createDirectories(parent);Path folder=Files.createTempDirectory(parent,"演示-"+id.name()+"-");Path output=Files.createDirectory(folder.resolve("结果"));
  if(id==ToolId.WARNING_FORM){
   Path r=folder.resolve("风险.xlsx"),g=folder.resolve("分组.xlsx"),b=folder.resolve("开标.xlsx");
   WorkbookTable.write(r,"风险",local.tender.tools.warningform.WarningFormTool.RISK_HEADERS,List.of(List.of("演示分标","包1","示例甲","地址甲","电话甲","共同邮箱","否","否","是"),List.of("演示分标","包1","示例乙","地址乙","电话乙","共同邮箱","否","否","是")));
   WorkbookTable.write(g,"分组",local.tender.tools.warningform.WarningFormTool.GROUP_HEADERS,List.of(List.of("演示分标","包1","技术1","商务1")));
   WorkbookTable.write(b,"开标",local.tender.tools.warningform.WarningFormTool.BID_HEADERS,List.of(List.of("演示采购","001","演示分标","0001","包1","示例甲"),List.of("演示采购","001","演示分标","0001","包1","示例乙")));
   return new ToolDemos(Map.of("risk",List.of(r),"groups",List.of(g),"bids",List.of(b)),Map.of(),output,"ZIP包含技术、商务组各1份邮箱相同确认单，复核意见待填写");
  }
  if(id==ToolId.PRICE_SCORE){
   Path q=folder.resolve("报价.xlsx"),r=folder.resolve("公式参数.xlsx");
   WorkbookTable.write(q,"报价",local.tender.tools.pricescore.PriceScoreTool.HEADERS,List.of(List.of("示例分标","包1","第1轮","甲","80","","是"),List.of("示例分标","包1","第1轮","乙","100","","是")));
   WorkbookTable.write(r,"规则",local.tender.tools.pricescore.PriceScoreTool.RULE_HEADERS,List.of(List.of("示例分标","算术平均值下浮法","60","","","1","0.5","0","","","","","")));
   return new ToolDemos(Map.of("files",List.of(q),"rules",List.of(r)),Map.of(),output,"基准价90万元，甲价格分94.44、乙88.89；仅为虚构演示参数");
  }
  if(id==ToolId.AWARD){
   Path q=folder.resolve("排序.xlsx"),g=folder.resolve("限授.xlsx");
   WorkbookTable.write(q,"排序",local.tender.tools.award.AwardTool.INPUT_HEADERS,List.of(List.of("001","演示分标","包1","甲","1","100"),List.of("001","演示分标","包1","乙","2","90"),List.of("001","演示分标","包2","甲","1","200"),List.of("001","演示分标","包2","乙","2","190")));
   WorkbookTable.write(g,"规则",local.tender.tools.award.AwardTool.GROUP_HEADERS,List.of(List.of("组1","001","包1","1","1"),List.of("组1","001","包2","1","1")));
   return new ToolDemos(Map.of("files",List.of(q),"groups",List.of(g)),Map.of("mode","一轮一包"),output,"包2授甲，包1授乙；同组每家最多1包");
  }
  if(id==ToolId.SPLIT_PACK){
   Path m=folder.resolve("物料.xlsx"),r=folder.resolve("分包.xlsx");
   WorkbookTable.write(m,"物料",local.tender.tools.splitpack.SplitPackTool.MATERIAL_HEADERS,List.of(List.of("1","M1","示例物料","示例","个","T1","标准","10","100","1")));
   WorkbookTable.write(r,"规则",local.tender.tools.splitpack.SplitPackTool.RULE_HEADERS,List.of(List.of("1","1"),List.of("2","1")));
   return new ToolDemos(Map.of("materials",List.of(m),"rules",List.of(r)),Map.of("mode","比例"),output,"两包各5件、金额500；合计10件、1000");
  }
  if(id==ToolId.CONTACT_WARNING){var demo=DemoFiles.create(folder);return new ToolDemos(Map.of("bidList",List.of(demo.bids()),"contacts",List.of(demo.contacts())),Map.of(),demo.output(),"一条传真号码相同预警，内容012345");}
  if(id==ToolId.IP_MAC){
   Path file=folder.resolve("IP_MAC演示.xlsx");WorkbookTable.write(file,"对比表",List.of("分标名称","包名","轮次","投标人","技术文件MAC","技术文件CPU","技术文件硬盘"),List.of(List.of("演示分标","包1","1","示例甲","AA:BB:CC:DD:EE:FF","CPU-DEMO","DISK-DEMO"),List.of("演示分标","包1","2","示例乙","AA:BB:CC:DD:EE:FF","CPU-DEMO","DISK-DEMO")));
   return new ToolDemos(Map.of("files",List.of(file)),Map.of(),output,"一条MAC、CPU、硬盘组合全部相同预警，涉及两家供应商");
  }
  if(id==ToolId.EXPERT_OBJECTIVE){
   var files=new ArrayList<Path>();for(int i=0;i<2;i++){
    Path file=folder.resolve("评分表_编号"+i+"_示例专家"+i+".xlsx");files.add(file);
    try(var w=new XSSFWorkbook()){var s=w.createSheet("包1");s.createRow(1).createCell(0).setCellValue("设备/材料名称: 演示分标 日期:2026-10-22");s.createRow(2).createCell(1).setCellValue("商务评分细则");s.createRow(3).createCell(3).setCellValue("示例供应商");var row=s.createRow(4);row.createCell(1).setCellValue("业绩数量");row.createCell(3).setCellValue(i==0?2:2.3);s.createRow(5).createCell(0).setCellValue("合计");s.createRow(8);try(var out=Files.newOutputStream(file)){w.write(out);}}
   }
   Path rules=folder.resolve("客观项规则.xlsx");WorkbookTable.write(rules,"规则",local.tender.tools.objective.ObjectiveTool.RULE_HEADERS,List.of(List.of("业绩数量","0.5")));
   return new ToolDemos(Map.of("files",files,"rules",List.of(rules)),Map.of(),output,"两条问题：专家分数2与2.3不一致，2.3不是0.5的倍数");
  }
  if(id==ToolId.INITIAL_REPORT){
   Path file=folder.resolve("初评.xlsx");try(var w=new XSSFWorkbook()){
    var reasons=w.createSheet("否决明细");var r=reasons.createRow(2);r.createCell(1).setCellValue("演示分标");r.createCell(2).setCellValue("证明材料缺失");r.createCell(3).setCellValue("完整示例公司名称");r.createCell(4).setCellValue("包01");
    var base=w.createSheet("基本情况");base.createRow(1).createCell(0).setCellValue("一、基本情况");r=base.createRow(3);r.createCell(0).setCellValue("001");r.createCell(1).setCellValue("演示分标");r.createCell(2).setCellValue(2);r.createCell(3).setCellValue(3);r.createCell(4).setCellValue(6);base.createRow(4).createCell(0).setCellValue("二、澄清事项");
    var names=w.createSheet("签名");names.createRow(1).createCell(0).setCellValue("这是技术组汇总");names.createRow(10).createCell(0).setCellValue("组长：示例甲、示例乙");try(var out=Files.newOutputStream(file)){w.write(out);}
   }
   return new ToolDemos(Map.of("report",List.of(file)),Map.of("clarification","无（仅为虚构演示）"),output,"1个标段、2个包、3个投标人、6份文件；保留完整示例公司名称");
  }
  if(id==ToolId.LIMIT_PRICE){
   Path q=folder.resolve("报价.xlsx"),l=folder.resolve("限价.xlsx");
   WorkbookTable.write(q,"演示分标",local.tender.tools.limitprice.LimitPriceTool.QUOTE_HEADERS,List.of(List.of("第一轮","包01","示例甲公司","0001","1","演示材料","13%"),List.of("第一轮","包01","示例乙公司","0001","1","演示材料","13"),List.of("第二轮","包01","示例甲公司","0001","1.2","演示材料","9")));
   WorkbookTable.write(l,"限价",local.tender.tools.limitprice.LimitPriceTool.LIMIT_HEADERS,List.of(List.of("演示分标","0001","10000")));
   return new ToolDemos(Map.of("quotes",List.of(q),"limits",List.of(l)),Map.of("projectType","物资","limitBasis","单价"),output,"共6条：等于限价2条，其余4类各1条");
  }
  if(id==ToolId.PERFORMANCE_SCORE){
   Path a=folder.resolve("开标.xlsx"),b=folder.resolve("评价.xlsx");
   WorkbookTable.write(a,"开标",PerformanceTool.BID_HEADERS,List.of(List.of("001","演示分标","01","包01","001","示例项目单位","示例甲公司","示例单位","示例部门"),List.of("001","演示分标","01","包01","001","示例项目单位","示例乙公司","示例单位","示例部门")));
   WorkbookTable.write(b,"评价",PerformanceTool.SCORE_HEADERS,List.of(List.of("示例单位","供应商","","","示例甲公司","A","虚构演示")));
   return new ToolDemos(Map.of("fileA",List.of(a),"fileB",List.of(b)),Map.of(),output,"示例甲公司5分；示例乙公司未匹配、默认4分并提示");
  }
  if(id==ToolId.REJECTION_NOTICE){
   Path a=folder.resolve("否决.xlsx"),b=folder.resolve("顺序.xlsx");
   WorkbookTable.write(a,"否决",List.of("分标名称","投标人","否决模板"),List.of(List.of("演示分标","示例甲公司","未提交必要证明材料"),List.of("演示分标","示例甲公司","未提交必要证明材料"),List.of("演示分标","示例乙公司","未提交必要证明材料")));
   WorkbookTable.write(b,"顺序",List.of("序号","分标名称"),List.of(List.of("1","演示分标")));
   return new ToolDemos(Map.of("excluded",List.of(a),"sectionOrder",List.of(b)),Map.of(),output,"Word中同一原因汇总为2家投标人、3份投标文件");
  }
  Path file=folder.resolve("演示.xlsx");
  try(var workbook=new XSSFWorkbook()){
   var sheet=workbook.createSheet("包01");
   if(id==ToolId.EXPERT_TOTAL){
    sheet.createRow(1).createCell(0).setCellValue("设备/材料名称: 演示分标 日期:2026-10-22");sheet.createRow(2).createCell(0).setCellValue("专家");sheet.createRow(3).createCell(1).setCellValue("示例甲公司");
    int[] scores={80,81,83,85,88};for(int i=0;i<scores.length;i++){var row=sheet.createRow(i+4);row.createCell(0).setCellValue("专家"+(i+1));row.createCell(1).setCellValue(scores[i]);}sheet.createRow(9).createCell(0).setCellValue("平均分");sheet.createRow(11);
   }else if(id==ToolId.ECONOMIC_RANK){
    sheet.createRow(3).createCell(0).setCellValue("设备/材料名称: 演示分标 日期:2026-10-22");
    String[] headers={"包号/子包号","项目单位","项目名称","招标设备/材料（数量）","投标人","技术","商务","价格","总分","排序","基准价（万元/%）","投标总价（万元/%）"};var row=sheet.createRow(5);for(int i=0;i<headers.length;i++)row.createCell(i).setCellValue(headers[i]);
    String[][] values={{"示例甲公司","30","20","40","90","1","100","110"},{"示例乙公司","30","20","40","90","2","100","100"}};
    for(int i=0;i<values.length;i++){row=sheet.createRow(i+6);row.createCell(0).setCellValue("包01");for(int j=0;j<values[i].length;j++)row.createCell(j+4).setCellValue(values[i][j]);}
   }else throw new IllegalArgumentException("此工具尚无演示样例");
   try(var out=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW)){workbook.write(out);}
  }
  return new ToolDemos(Map.of(id==ToolId.EXPERT_TOTAL?"scores":"files",List.of(file)),id==ToolId.EXPERT_TOTAL?Map.of("expertNum","5","maxScoreDiff","7"):Map.of(),output,id==ToolId.EXPERT_TOTAL?"一条分差预警，高分88、低分80；没有超过半数同分":"两条排序差异：乙公司应为第1，甲公司应为第2");
 }
}
